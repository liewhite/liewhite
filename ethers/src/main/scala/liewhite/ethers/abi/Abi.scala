package liewhite.ethers.abi

import liewhite.json.{*, given}
import zio.schema
import scala.util.Try

trait ABIType {
  def toString(): String
}

// int 对应int256, uint 对应uint256
case class ABITypeUint(size: Int) extends ABIType {
  override def toString(): String = s"uint$size"
}
case class ABITypeInt(size: Int) extends ABIType {

  override def toString(): String = s"int$size"
}
case object ABITypeBool extends ABIType {

  override def toString(): String = s"bool"
}
case object ABITypeBytes extends ABIType {

  override def toString(): String = s"bytes"
}
case class ABITypeSizedBytes(size: Int) extends ABIType {

  override def toString(): String = s"bytes$size"
}
case object ABITypeAddress extends ABIType {

  override def toString(): String = s"address"
}
case object ABITypeString extends ABIType {

  override def toString(): String = s"string"
}
case class ABITypeSizedArray(elem: ABIType, size: Int) extends ABIType {

  override def toString(): String = s"${elem.toString()}[$size]"
}
case class ABITypeArray(elem: ABIType) extends ABIType {

  override def toString(): String = s"${elem.toString()}[]"
}
case class ABITypeTuple(elems: Seq[ABIType]) extends ABIType {
  override def toString(): String = s"(${elems.map(_.toString()).mkString(",")})"
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
