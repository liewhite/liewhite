package liewhite.ethers.abi

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
  def BytesToHex = "0x" + Hex.encodeHexString(bs)
}

extension (s: String) {
  def hexToBytes = Hex.decodeHex(s.stripPrefix("0x"))
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
}

@main def testHex =
  val encoder = ABITypeTuple(
    ABITypeArray(
      ABITypeArray(ABITypeUint(256))
    )
  )
  val data =
    "0x00000000000000000000000000000000000000000000000000000000000000200000000000000000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000000000000000000000000004000000000000000000000000000000000000000000000000000000000000000c000000000000000000000000000000000000000000000000000000000000000030000000000000000000000000000000000000000000000000000000000000001000000000000000000000000000000000000000000000000000000000000000200000000000000000000000000000000000000000000000000000000000000030000000000000000000000000000000000000000000000000000000000000003000000000000000000000000000000000000000000000000000000000000000100000000000000000000000000000000000000000000000000000000000000020000000000000000000000000000000000000000000000000000000000000003"
  val result = encoder
    .decode(data.hexToBytes)
  println(result)
  val cmp = Seq(Seq(1, 2, 3), Seq(1, 2, 3)).toJsonAst
  println(cmp == result)

