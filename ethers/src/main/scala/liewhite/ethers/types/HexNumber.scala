package liewhite.ethers.types

import liewhite.ethers.abi.*
import org.web3j.crypto.Keys
import zio.schema.Schema
import scala.util.Try
import liewhite.ethers.*
import liewhite.json.{*, given}

// currently only uint
case class HexUint(i: BigInt)

object HexUint {
  def apply(hex: String): HexUint =
    HexUint(hex.hexToUint)

  given Schema[HexUint] = Schema[String].transformOrFail(
    str => {
      Try {
        HexUint(str)
      }.toEither.left.map(_.getMessage() + str)
    },
    tp => {
      Right(tp.i.toHex)
    }
  )
  given Conversion[BigInt, HexUint] with {
    def apply(x: BigInt): HexUint = HexUint(x)
  }
  given Conversion[Int, HexUint] with {
    def apply(x: Int): HexUint = HexUint(x)
  }
  given Conversion[Long, HexUint] with {
    def apply(x: Long): HexUint = HexUint(x)
  }
}
