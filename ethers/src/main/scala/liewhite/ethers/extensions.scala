package liewhite.ethers

import org.apache.commons.codec.binary.Hex


extension (s: String) {
    def toBytes: Array[Byte] = {
        val hex = s.stripPrefix("0x")
        Hex.decodeHex(hex)
    }
}

extension (bs: Array[Byte]) {
    def toBigInt: BigInt = {
        BigInt(bs)
    }

    def toHex: String = {
        Hex.encodeHexString(bs)
    }
}