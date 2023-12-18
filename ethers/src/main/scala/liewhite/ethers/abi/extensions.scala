package liewhite.ethers.abi

import org.apache.commons.codec.binary.Hex
import liewhite.ethers.types.Address

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

  // val result = ABITypeTuple(Seq(ABITypeInt(256), ABITypeAddress)).encode((1,Address.fromHex("0x8DF04D551E3f7F5B03a67DE79184BB919A97BbDE")))
  val result = ABITypeTuple(Seq(ABITypeArray(ABITypeInt(256)))).encode( Seq(1, 2, 3) *: EmptyTuple)
  // var result = ABITypeAddress.encode("0x8DF04D551E3f7F5B03a67DE79184BB919A97BbDE")
  // println(result.BytesToHex)
  // result = ABITypeAddress.encodePacked("0x8DF04D551E3f7F5B03a67DE79184BB919A97BbDE")
  // println(result.BytesToHex)
  // println(ABITypeInt(8).encode(-127).BytesToHex)
  // println("0x1234".hexToBytes.alignLength(3).BytesToHex)
  // println(ABITypeSizedBytes(2).encodePacked("0x1234").BytesToHex)
  // println((BigInt(-1) & (BigInt(2).pow(256) - 1)).abs.toBytes32.BytesToHex)
  println(result.BytesToHex)
