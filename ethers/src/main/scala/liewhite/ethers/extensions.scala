package liewhite.ethers

import org.apache.commons.codec.binary.Hex

given [T]: Conversion[T, T *: EmptyTuple] with {
  def apply(x: T): T *: EmptyTuple =
    x *: EmptyTuple
}

extension (s: String) {
  def toBytes: Array[Byte] = {
    val hex = s.stripPrefix("0x")
    Hex.decodeHex(hex)
  }
}

extension (bs: Array[Byte]) {
  def toBigInt: BigInt =
    BigInt(bs)

  def toHex: String =
    "0x" + Hex.encodeHexString(bs)
}

extension (i: BigInt) {
  def toHex: String =
    "0x" + Hex.encodeHexString(i.toByteArray)
}
