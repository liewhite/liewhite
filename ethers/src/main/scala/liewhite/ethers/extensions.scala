package liewhite.ethers

import org.apache.commons.codec.binary.Hex
import liewhite.ethers.abi.types.ABIStruct
import org.web3j.abi.datatypes.Type
import scala.jdk.CollectionConverters.*

given [T]: Conversion[T, T *: EmptyTuple] with {
  def apply(x: T): T *: EmptyTuple =
    x *: EmptyTuple
}
given [T <: Tuple](using ev: Tuple.Union[T] <:< Type[_]): Conversion[T, ABIStruct[T]] with {
  def apply(x: T): ABIStruct[T] =
    ABIStruct(x)
}

given [T]: Conversion[Seq[T], java.util.List[T]] with {
  def apply(x: Seq[T]): java.util.List[T] =
    x.asJava
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
