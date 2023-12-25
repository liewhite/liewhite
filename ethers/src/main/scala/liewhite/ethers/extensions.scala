package liewhite.ethers

import org.apache.commons.codec.binary.Hex
import liewhite.ethers.types.Address
import liewhite.json.{*, given}
import zio.json.ast.Json
import zio.json.internal.Lexer
import java.lang.reflect.Modifier
import java.lang.reflect.Field
import java.lang.invoke.MethodHandles

extension (bs: Array[Byte]) {
  // 0扩展， left向左填充， right向右填充
  // address 向左填充， bytes，string向右填充
  def alignLength(lenth: Int = 32, direction: "left" | "right" = "right") = {
    val tailLenth = bs.length % lenth
    if (tailLenth == 0) {
      bs
    } else {
      if (direction == "left") {
        Array.fill[Byte](lenth - tailLenth)(0) ++ bs
      } else {
        bs ++ Array.fill[Byte](lenth - tailLenth)(0)

      }
    }
  }
  def bytesToHex = "0x" + Hex.encodeHexString(bs)

  def bytesToInt  = BigInt(bs)
  def bytesToUint = BigInt(bs.prepended(0.toByte))

}

extension (s: String) {
  def hexToBytes = Hex.decodeHex(s.stripPrefix("0x"))
  def hexToUint  = BigInt(s.stripPrefix("0x"), 16)
}

extension (i: BigInt) {
  def toBytes32: Array[Byte] = {
    var bs = i.toByteArray
    // 如果正数且0开头， 则说明是符号位， 直接drop
    if (i.signum > 0 && bs(0) == 0) {
      bs = bs.drop(1)
    }

    val l = bs.length
    // 大于0, 0扩展
    val baseBytes = if (i.signum >= 0) {
      Array.fill[Byte](32 - l)(0)
    } else {
      // 负数， 符号扩展
      Array.fill[Byte](32 - l)(-1)
    }
    baseBytes ++ bs
  }

  def toHex: String =
    "0x" + i.toString(16)

}

@main def mmm = {
  val x: Either[Int, String] = Right("xx")
  println(x.toJson.asString.fromJson[Either[Int,String]])
}
