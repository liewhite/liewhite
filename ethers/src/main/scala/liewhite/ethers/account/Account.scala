package liewhite.ethers.account

import org.web3j.crypto.Bip32ECKeyPair
import org.web3j.crypto.Keys
import liewhite.ethers.types.Address
import org.web3j.crypto.Credentials
import org.web3j.crypto.RawTransaction
import liewhite.ethers.types.TransactionMsg
import liewhite.ethers.rpc.Transaction
import liewhite.ethers.rpc.TransactionCall

class Account(keypair: Bip32ECKeyPair) {

    val credential = Credentials.create(keypair)

    def address: Address = {
        Address.fromHex(Keys.getAddress(keypair))
    }
    def privateKey: BigInt = {
        keypair.getPrivateKey()
    }

    def signTx(tx: TransactionCall): Transaction = ???

}