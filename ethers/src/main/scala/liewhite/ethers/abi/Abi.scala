package liewhite.ethers.abi

import liewhite.json.{*, given}
import zio.schema
import scala.util.Try
import liewhite.ethers.types.Address
import liewhite.json.{*, given}

trait ABIType {
  def toString(): String
  def isDynamic: Boolean = false

  // 头部长度， 大部分都是32
  def headLength: Int = 32

  def encode(params: Any): Array[Byte] // params也应该支持json

  def decode(bs: Array[Byte]): Json // 只返回Json, 方便动态处理， 不然只能到处match

  def encodePacked(params: Any): Array[Byte]
}

// int 对应int256, uint 对应uint256
case class ABITypeUint(size: Int) extends ABIType {

  val MAX: BigInt = BigInt(2).pow(size) - 1
  val MIN: BigInt = 0

  override def toString(): String = s"uint$size"
  def encode(params: Any): Array[Byte] = {
    val bi = params match {
      case i: Int       => BigInt(i)
      case l: Long      => BigInt(l)
      case bi: BigInt   => bi
      case Json.Num(bd) => BigInt(bd.toBigInteger())
    }
    if (bi > MAX || bi < MIN) {
      throw ABIException(s"uint not in range $bi")
    }
    bi.toBytes32
  }
  override def decode(bs: Array[Byte]): Json = {
    val with0 = bs.take(32).prepended(0.toByte)
    Json.Num(BigDecimal(BigInt(with0)))
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
      case i: Int       => BigInt(i)
      case l: Long      => BigInt(l)
      case bi: BigInt   => bi
      case Json.Num(bd) => BigInt(bd.toBigInteger())
    }
    if (bi > MAX || bi < MIN) {
      throw ABIException(s"int not in range $bi")
    }
    bi.toBytes32

  override def encodePacked(params: Any): Array[Byte] =
    encode(params).drop((32 - size / 8))

  override def decode(bs: Array[Byte]): Json =
    Json.Num(BigDecimal(BigInt(bs.take(32))))
}
case object ABITypeBool extends ABIType {

  override def toString(): String = s"bool"
  def encode(params: Any): Array[Byte] =
    params match {
      case true         => 1.toBytes32
      case false        => 0.toBytes32
      case Json.Bool(v) => encode(v)
    }
  override def encodePacked(params: Any): Array[Byte] =
    Array[Byte](encode(params).last)

  override def decode(bs: Array[Byte]): Json = {
    val value = BigInt(bs.take(32))
    if (value == 0) {
      Json.Bool(false)
    } else if (value == 1) {
      Json.Bool(true)
    } else {
      throw ABIException(s"invalid bool value: ${bs.BytesToHex}")
    }
  }
}

case class ABITypeSizedBytes(size: Int) extends ABIType {

  override def encodePacked(params: Any): Array[Byte] = {
    val bs = params match {
      case bs: Array[Byte] => bs
      case str: String     => str.hexToBytes
      case Json.Str(str)   => str.hexToBytes
    }
    if (bs.length > size) {
      throw ABIException(s"bytes length not in range ${bs.length}")
    }
    bs.alignLength(size)
  }

  override def toString(): String      = s"bytes$size"
  def encode(params: Any): Array[Byte] = encodePacked(params).alignLength()
  override def decode(bs: Array[Byte]): Json =
    Json.Str(bs.take(size).BytesToHex)
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
      case Json.Str(str)   => str.hexToBytes
    }
  override def decode(bs: Array[Byte]): Json = {
    val length = ABITypeUint(256).decode(bs.take(32)).asNumber.get.value.toBigInteger().intValue()
    Json.Str(bs.drop(32).take(length).BytesToHex)
  }
}


case object ABITypeAddress extends ABIType {

  override def encodePacked(params: Any): Array[Byte] = {
    val bs = params match {
      case bs: Array[Byte] => Address.fromBytes(bs)
      case str: String     => Address.fromHex(str)
      case addr: Address   => addr
      case Json.Str(str)   => Address.fromHex(str)
    }
    bs.bs
  }

  override def toString(): String = s"address"

  def encode(params: Any): Array[Byte] =
    encodePacked(params).alignLength(32, "left")
    
  override def decode(bs: Array[Byte]): Json =
    Json.Str(bs.take(20).BytesToHex)
}

case object ABITypeString extends ABIType {

  override def isDynamic: Boolean = true
  override def encodePacked(params: Any): Array[Byte] =
    params match {
      case s: String     => s.getBytes()
      case Json.Str(str) => str.getBytes()
    }

  override def toString(): String = s"string"

  def encode(params: Any): Array[Byte] =
    val content = encodePacked(params)
    ABITypeUint(256).encode(content.length) ++ content.alignLength()

  override def decode(bs: Array[Byte]): Json = {
    val length = ABITypeUint(256).decode(bs.take(32)).asNumber.get.value.toBigInteger().intValue()
    Json.Str(bs.drop(32).take(length).BytesToHex)
  }
}

case class ABITypeSizedArray(elem: ABIType, size: Int) extends ABIType {
  override def headLength: Int = if(!elem.isDynamic) {
    elem.headLength * size
  }else {
    32
  }

  override def isDynamic: Boolean = elem.isDynamic

  override def encodePacked(params: Any): Array[Byte] = {
    val elems = params match {
      case seq: Seq[Any] => seq
      case Json.Arr(vs)  => vs
    }
    elems.map(elem.encodePacked(_)).reduce(_ ++ _)
  }

  override def toString(): String = s"${elem.toString()}[$size]"
  def encode(params: Any): Array[Byte] =
    val elems = params match {
      case seq: Seq[Any] => seq
      case Json.Arr(vs)  => vs
    }
    ABITypeTuple(List.fill(elems.length)(elem))
      .encode(Tuple.fromArray(elems.toArray))

  override def decode(bs: Array[Byte]): Json = {
    val length = ABITypeUint(256).decode(bs.take(32))
    ???
  }
}

case class ABITypeArray(elem: ABIType) extends ABIType {

  override def isDynamic: Boolean = true

  override def encodePacked(params: Any): Array[Byte] = {
    val elems = params match {
      case seq: Seq[Any] => seq
      case Json.Arr(vs)  => vs
    }
    elems.map(elem.encodePacked(_)).reduce(_ ++ _)
  }

  override def toString(): String = s"${elem.toString()}[]"
  def encode(params: Any): Array[Byte] = {
    val elems = params match {
      case seq: Seq[Any] => seq
      case Json.Arr(vs)  => vs
    }
    ABITypeUint(256).encode(elems.length) ++ ABITypeTuple(List.fill(elems.length)(elem))
      .encode(Tuple.fromArray(elems.toArray))
  }
  override def decode(bs: Array[Byte]): Json = {
    val length = ABITypeUint(256).decode(bs.take(32))
    ???
  }
}

case class ABITypeTuple(elems: Seq[ABIType]) extends ABIType {

  override def headLength: Int = if (isDynamic) {
    32
  } else {
    elems.map(_.headLength).sum
  }

  override def isDynamic: Boolean = elems.exists(_.isDynamic)

  override def encodePacked(params: Any): Array[Byte] = {
    val args = params match {
      case tuple: Tuple => tuple.toList.asInstanceOf[List[Any]]
      case seq: Seq[_]  => seq
      case Json.Arr(vs) => vs
    }
    (elems.zip(args).map{
      case (tp,data) => tp.encodePacked(data)
    }).reduce(_ ++ _)

  }

  override def toString(): String = s"(${elems.map(_.toString()).mkString(",")})"

  def encode(params: Any): Array[Byte] = {
    val args = params match {
      case tuple: Tuple => tuple.toList.asInstanceOf[List[Any]]
      case seq: Seq[_]  => seq
      case Json.Arr(vs) => vs
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
  override def decode(bs: Array[Byte]): Json = {
    val length = ABITypeUint(256).decode(bs.take(32))
    ???
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
