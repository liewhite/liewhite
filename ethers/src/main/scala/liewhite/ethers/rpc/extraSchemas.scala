package liewhite.ethers.rpc

import liewhite.json.{*, given}
import liewhite.ethers.*
import zio.json.ast.Json.*

enum transactionOrTransactionHash {
  case TransactionHash(hash: Array[Byte])
  case Transaction(tx: transaction)
}

object transactionOrTransactionHash {
  given Schema[transactionOrTransactionHash] = Schema[Json].transformOrFail(
    j => {
      j match
        case Obj(fields) => j.asType[transaction].map(transactionOrTransactionHash.Transaction(_))
        case Str(value)  => Right(transactionOrTransactionHash.TransactionHash(value.hexToBytes))
    },
    tp => {
      tp match
        case TransactionHash(hash) => Right(Json.Str(hash.bytesToHex))
        case Transaction(tx)       => Right(tx.toJsonAst)
    }
  )
}
