package liewhite.ethers.types

import org.web3j.crypto.Keys
import zio.schema.Schema
import scala.util.Try
import liewhite.ethers.*

case class Address(bs: Array[Byte]) {
  def checkSumAddress: String =
    Keys.toChecksumAddress(bs.BytesToHex)
}

object Address {
  def fromBytes(bs: Array[Byte]): Address =
    if (bs.length == 20) {
      Address(bs)
    } else {
      throw TypeException(f"address length ${bs.length}")
    }
  def fromHex(hex: String): Address =
    fromBytes(hex.hexToBytes)

  given Schema[Address] = Schema[String].transformOrFail(
    str => {
      Try {
        Address.fromHex(str)
      }.toEither.left.map(_.getMessage())
    },
    tp => {
      Right(tp.checkSumAddress)
    }
  )
}
