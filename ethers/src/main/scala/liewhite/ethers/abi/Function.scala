package liewhite.ethers.abi

import org.web3j.crypto.Hash
import liewhite.ethers.*
import zio.json.ast.Json

class ABIFunction(name: String, input: ABIType, output: ABIType) {
  def selector = Hash.sha3String(name + input.toString()).hexToBytes.take(4)

  def encodeInput(arg: Tuple): Array[Byte] =
    selector ++ input.encode(arg)
  def decodeInput(bs: Array[Byte]): Json = {
    if (!bs.take(4).sameElements(selector)) {
      throw ABIException(s"selector not match, expect ${selector.bytesToHex} got ${bs.take(4).bytesToHex}")
    }
    input.decode(bs.drop(4))
  }

  def encodeOutput(arg: Any): Array[Byte] =
    output.encode(arg)

  def decodeOutput(bs: Array[Byte]): Json = {
    output.decode(bs)
  }
}
