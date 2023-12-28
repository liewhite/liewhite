package liewhite.ethers.types

import org.web3j.crypto.RawTransaction

trait TransactionMsg {
    def toWeb3J: RawTransaction
}