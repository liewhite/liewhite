package liewhite.ethers.rpc

import liewhite.json.{*, given}
import liewhite.ethers.types.HexUint
import zio.schema.annotation.noDiscriminator
import liewhite.ethers.types.Address
import scala.util.control.Exception.By

case class JsonRpcResponse[T](
  result: Option[T],
  error: Option[Json],
  id: Long,
  jsonrpc: String,
) derives Schema

case class JsonRpcRequest(
  method: String,
  params: Json,
  id: Long,
  jsonrpc: String,
) derives Schema


case class SubscribeStreamItem[T](
  params: SubscribeStreamItemParams[T],
  method: String,
  jsonrpc: String,
) derives Schema

case class SubscribeStreamItemParams[T](
  result: T
) derives Schema

case class LogFilter(
  address: Option[Address],
  topics: Seq[Array[Byte]],
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

case class AccessList(
  address: Address,
  storageKeys: Seq[Array[Byte]]
) derives Schema
case class Transaction(
  `type` : HexUint, 
  blockHash: Option[Array[Byte]],
  blockNumber: Option[HexUint],
  from: Address,
  gas: HexUint,
  gasPrice: HexUint,
  maxFeePerGas: Option[HexUint],
  maxPriorityFeePerGas: Option[HexUint],
  hash: Array[Byte],
  input: Array[Byte],
  nonce : HexUint, 
  to: Option[Address],
  transactionIndex : Option[HexUint], 
  value : HexUint, 
  accessList: Option[Seq[AccessList]],
  chainId: Option[HexUint],
  v: HexUint,
  r: HexUint,
  s: HexUint,
  yParity: Option[HexUint],
) derives Schema

case class Log(
  address: Address,
  topics: Seq[Array[Byte]],
  data: Array[Byte],
  blockNumber: HexUint,
  transactionHash: Array[Byte],
  transactionIndex: HexUint,
  blockHash: Array[Byte],
  logIndex: HexUint,
  removed: Boolean
) derives Schema

case class Receipt(
  blockHash: Array[Byte],
  blockNumber: HexUint,
  transactionHash: Array[Byte],
  transactionIndex: HexUint,
  contractAddress: Option[Address],
  cumulativeGasUsed: HexUint,
  effectiveGasPrice: HexUint,
  from: Address,
  gasUsed:HexUint,
  logs: Seq[Log],
  logsBloom: Array[Byte],
  status: HexUint,
  to: Option[Address],
  `type`: HexUint,
)derives Schema

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

case class Header(
  difficulty: HexUint,
  extraData: Array[Byte],
  gasLimit: HexUint,
  gasUsed: HexUint,
  logsBloom: Array[Byte],
  miner: Address,
  nonce: Option[HexUint],
  number: HexUint,
  parentHash: Array[Byte],
  receiptsRoot: Array[Byte],
  sha3Uncles: Array[Byte],
  stateRoot: Array[Byte],
  timestamp: HexUint,
  transactionsRoot: Array[Byte],
  hash: Array[Byte],

  // size: HexUint,
  // transactions: Seq[Either[Array[Byte], Transaction]],
  // uncles: Seq[Array[Byte]]
) derives Schema

case class TransactionCall(
  from: Option[Address],
  to: Address,
  gas: Option[HexUint],
  gasPrice: Option[HexUint],
  value: Option[HexUint],
  input: Option[Array[Byte]]
) derives Schema
