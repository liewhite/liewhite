package liewhite.ethers.rpc

import liewhite.json.{*, given}
import liewhite.ethers.types.HexUint
import zio.schema.annotation.noDiscriminator
import liewhite.ethers.types.Address
import scala.util.control.Exception.By

case class JsonRpcResponse[T](
  id: Long,
  jsonrpc: String,
  result: Option[T],
  error: Option[Json]
) derives Schema

case class JsonRpcRequest(
  id: Long,
  jsonrpc: String,
  method: String,
  params: Json
) derives Schema

@noDiscriminator()
enum BlockNumberTag derives Schema {
  case latest
  case pending
  case earliest
}

enum BlockNumberOrTag {
  case Number(i: HexUint)
  case Tag(tag: BlockNumberTag)
}
object BlockNumberOrTag {
  given Schema[BlockNumberOrTag] = Schema[String].transformOrFail(
    from => {
      if (from.startsWith("0x")) {
        Right(BlockNumberOrTag.Number(HexUint(from)))
      } else {
        from.fromJson[BlockNumberTag].map(BlockNumberOrTag.Tag(_)).left.map(_.toString())
      }
    },
    to => {
      Right(to match
        case Number(i) => i.toJson.asString
        case Tag(tag)  => tag.toJson.asString
      ).map(_.drop(1).dropRight(1))

    }
  )
}

case class Transaction(
  blockHash: Option[Array[Byte]],
  blockNumber: Option[HexUint],
  from: Address,
  gas: HexUint,
  gasPrice: HexUint,

) derives Schema
case class Block(
  // 暂时不用option
  number: HexUint,
  hash: Array[Byte],
  parentHash: Array[Byte],
  nonce: Option[HexUint],
  sha3Uncles: Array[Byte],
  logsBloom: Array[Byte],
  transactionsRoot: Array[Byte],
  stateRoot: Array[Byte],
  receiptsRoot: Array[Byte],
  miner: Address,
  difficulty: HexUint,
  totalDifficulty: HexUint,
  extraData: Array[Byte],
  size: HexUint,
  gasLimit: HexUint,
  gasUsed: HexUint,
  timestamp: HexUint,
  transactions: Seq[Either[Array[Byte], Transaction]],
  uncles: Seq[Array[Byte]]
) derives Schema

case class TransactionCall(
  from: Option[Address],
  to: Address,
  gas: Option[HexUint],
  gasPrice: Option[HexUint],
  value: Option[HexUint],
  input: Option[Array[Byte]]
) derives Schema
