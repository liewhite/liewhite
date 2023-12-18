package liewhite.ethers.types

import liewhite.ethers.abi.*
import org.web3j.crypto.Keys

case class Address(bs: Array[Byte]) {
    def checkSumAddress: String = {
        Keys.toChecksumAddress(bs.BytesToHex)
    }
}

object Address {
    def fromBytes(bs: Array[Byte]): Address = {
        if(bs.length == 20) {
            Address(bs)
        }else {
            throw TypeException(f"address length ${bs.length}")
        }
    }
    def fromHex(hex: String): Address = {
        fromBytes(hex.hexToBytes)
    }
}