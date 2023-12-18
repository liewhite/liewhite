package liewhite.ethers.abi

import liewhite.json.{*, given}
import zio.schema
import scala.util.Try
import liewhite.ethers.types.Address

trait ABIType {
  def toString(): String
  def isDynamic: Boolean = false

  // 头部长度， 大部分都是32
  def headLength: Int = 32

  def encode(params: Any): Array[Byte]
  def encodePacked(params: Any): Array[Byte]
}

// int 对应int256, uint 对应uint256
case class ABITypeUint(size: Int) extends ABIType {

  val MAX: BigInt = BigInt(2).pow(size) - 1
  val MIN: BigInt = 0

  override def toString(): String = s"uint$size"
  def encode(params: Any): Array[Byte] = {
    val bi = params match {
      case i: Int     => BigInt(i)
      case l: Long    => BigInt(l)
      case bi: BigInt => bi
    }
    if (bi > MAX || bi < MIN) {
      throw ABIException(s"uint not in range $bi")
    }
    bi.toBytes32
  }

  override def encodePacked(params: Any): Array[Byte] =
    encode(params).drop((32 - size / 8))
}
case class ABITypeInt(size: Int) extends ABIType {
  val MAX: BigInt = BigInt(2).pow(size - 1) - 1
  val MIN: BigInt = -MAX - 1

  override def toString(): String = s"int$size"
  def encode(params: Any): Array[Byte] =
    val bi = params match {
      case i: Int     => BigInt(i)
      case l: Long    => BigInt(l)
      case bi: BigInt => bi
    }
    if (bi > MAX || bi < MIN) {
      throw ABIException(s"int not in range $bi")
    }
    bi.toBytes32

  override def encodePacked(params: Any): Array[Byte] =
    encode(params).drop((32 - size / 8))
}
case object ABITypeBool extends ABIType {

  override def toString(): String = s"bool"
  def encode(params: Any): Array[Byte] =
    params match {
      case true  => 1.toBytes32
      case false => 0.toBytes32
    }
  override def encodePacked(params: Any): Array[Byte] =
    Array[Byte](encode(params).last)
}
case object ABITypeBytes extends ABIType {

  override def isDynamic: Boolean = true

  override def toString(): String = s"bytes"
  def encode(params: Any): Array[Byte] =
    val content = encodePacked(params)
    ABITypeUint(256).encode(content.length) ++ content.alignLength()

  override def encodePacked(params: Any): Array[Byte] =
    params match {
      case bs: Array[Byte] => bs
      case str: String     => str.hexToBytes
    }
}
case class ABITypeSizedBytes(size: Int) extends ABIType {

  override def encodePacked(params: Any): Array[Byte] = {
    val bs = params match {
      case bs: Array[Byte] => bs
      case str: String     => str.hexToBytes
    }
    if (bs.length > size) {
      throw ABIException(s"bytes length not in range ${bs.length}")
    }
    bs.alignLength(size)
  }

  override def toString(): String      = s"bytes$size"
  def encode(params: Any): Array[Byte] = encodePacked(params).alignLength()
}

case object ABITypeAddress extends ABIType {

  override def encodePacked(params: Any): Array[Byte] = {
    val bs = params match {
      case bs: Array[Byte] => Address.fromBytes(bs)
      case str: String     => Address.fromHex(str)
      case addr: Address   => addr
    }
    bs.bs
  }

  override def toString(): String = s"address"

  def encode(params: Any): Array[Byte] =
    encodePacked(params).alignLength(32, "left")
}

case object ABITypeString extends ABIType {

  override def isDynamic: Boolean = true
  override def encodePacked(params: Any): Array[Byte] =
    params match {
      case s: String => s.getBytes()
    }

  override def toString(): String = s"string"

  def encode(params: Any): Array[Byte] = encodePacked(params).alignLength(32)
}

case class ABITypeSizedArray(elem: ABIType, size: Int) extends ABIType {
  override def headLength: Int = elem.headLength * size

  override def isDynamic: Boolean = elem.isDynamic

  override def encodePacked(params: Any): Array[Byte] = ???

  override def toString(): String = s"${elem.toString()}[$size]"
  def encode(params: Any): Array[Byte] =
    val elems = params.asInstanceOf[Seq[Any]]
    ABITypeTuple(List.fill(elems.length)(elem))
      .encode(Tuple.fromArray(elems.toArray))
}

case class ABITypeArray(elem: ABIType) extends ABIType {

  override def isDynamic: Boolean = true

  override def encodePacked(params: Any): Array[Byte] = ???

  override def toString(): String = s"${elem.toString()}[]"
  def encode(params: Any): Array[Byte] = {
    val elems = params.asInstanceOf[Seq[Any]]
    ABITypeUint(256).encode(elems.length) ++ ABITypeTuple(List.fill(elems.length)(elem))
      .encode(Tuple.fromArray(elems.toArray))
  }
}

case class ABITypeTuple(elems: Seq[ABIType]) extends ABIType {

  override def headLength: Int = if (isDynamic) {
    32
  } else {
    elems.map(_.headLength).sum
  }

  override def isDynamic: Boolean = elems.exists(_.isDynamic)

  override def encodePacked(params: Any): Array[Byte] = ???

  override def toString(): String = s"(${elems.map(_.toString()).mkString(",")})"

  def encode(params: Any): Array[Byte] = {
    val args = params match {
      case tuple: Tuple => tuple.toList.asInstanceOf[List[Any]]
      case seq: Seq[_]  => seq
    }

    val headsLength = elems.map(_.headLength).sum
    // heads, tails
    val tail = elems.zip(args).foldLeft(Seq.empty[Array[Byte]]) { (accTail, item) =>
      val value = if (!item._1.isDynamic) {
        Array.emptyByteArray
      } else {
        item._1.encode(item._2)
      }
      accTail.appended(value)
    }
    val tailLength = tail.map(_.length)
    val heads = elems.zip(args).zipWithIndex.map {
      case ((tp, arg), index) => {
        if (!tp.isDynamic) {
          tp.encode(arg)
        } else {
          ABITypeUint(256).encode(headsLength + tailLength.take(index).sum)
        }
      }
    }
    heads.reduce(_ ++ _) ++ tail.reduce(_ ++ _)
  }
}

object ABIType {
  val sugar = Map(
    "int"  -> "int256",
    "uint" -> "uint256"
  )
  def parseType(s: String): ABIType =
    if (s.endsWith("]")) {
      if (s.endsWith("[]")) {
        parseArray(s)
      } else {
        parseSizedArray(s)
      }
    } else if (s.startsWith("(")) {
      parseTuple(s)
    } else {
      // parse atom
      val desugar = sugar.getOrElse(s, s)
      if (desugar == "bytes") {
        ABITypeBytes
      } else if (desugar.startsWith("bytes")) {
        val size = desugar.drop(5).toInt
        ABITypeSizedBytes(size)
      } else if (desugar == "string") {
        ABITypeString
      } else if (desugar == "bool") {
        ABITypeBool
      } else if (desugar == "address") {
        ABITypeAddress
      } else if (desugar.startsWith("int")) {
        parseInt(desugar)
      } else if (desugar.startsWith("uint")) {
        parseUInt(desugar)
      } else {
        throw ABIException(s"unknown type $desugar")
      }
    }

  def parseArray(s: String): ABIType =
    ABITypeArray(parseType(s.dropRight(2)))
  def parseSizedArray(s: String): ABIType = {
    // bytes[] bytes[n] 要单独处理
    val items = s.dropRight(1).split("[")
    val size  = items.last.toInt
    ABITypeSizedArray(parseType(items.head), size)
  }

  def parseTuple(s: String): ABIType = {
    val items = s.drop(1).dropRight(1).split(",").map(parseType(_))
    ABITypeTuple(items)
  }
  def parseInt(s: String): ABIType = {
    val size = s.drop(3).toInt
    ABITypeInt(size)
  }
  def parseUInt(s: String): ABIType = {
    val size = s.drop(4).toInt
    ABITypeUint(size)
  }

  given Schema[ABIType] = Schema[String].transformOrFail(
    str => {
      Try {
        parseType(str)
      }.toEither.left.map(_.getMessage())
    },
    tp => {
      Right(tp.toString())
    }
  )
}

enum ABIItemType derives Schema {
  case function
  case constructor
  case receive
  case fallback
  case event
  case error
}
case class Input(
  name: String,
  `type`: ABIType,
  components: Option[Seq[Input]],
  indexed: Option[Boolean]
) derives Schema

case class ABIItem(
  `type`: ABIItemType,
  name: Option[String], //Constructor, receive, and fallback never have name or outputs
  inputs: Option[Seq[Input]],
  outputs: Option[Seq[Input]],
  anonymous: Option[Boolean]
) derives Schema

trait ABIValue
