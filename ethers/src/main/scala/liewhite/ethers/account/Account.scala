package liewhite.ethers.account

import org.web3j.crypto.Bip32ECKeyPair
import org.web3j.crypto.Keys
import liewhite.ethers.types.Address

class Account(keypair: Bip32ECKeyPair) {

    def address: Address = {
        Address.fromHex(Keys.getAddress(keypair))
    }
    def privateKey: BigInt = {
        keypair.getPrivateKey()
    }

}