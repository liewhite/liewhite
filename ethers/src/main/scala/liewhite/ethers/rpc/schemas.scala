package liewhite.ethers.rpc
import liewhite.json.{*, given}
import liewhite.ethers.types.*
case class storageProof(key: HexUint, value: HexUint, proof: Seq[Array[Byte]]) derives Schema

enum blockNumberTag derives Schema {
  case earliest
  case latest
  case pending
}
case class Block(
  number: Option[HexUint],
  hash: Option[Array[Byte]],
  parentHash: Array[Byte],
  nonce: Option[HexUint],
  sha3Uncles: Array[Byte],
  logsBloom: Array[Byte],
  transactionsRoot: Array[Byte],
  stateRoot: Array[Byte],
  receiptsRoot: Array[Byte],
  miner: Option[Address],
  difficulty: HexUint,
  totalDifficulty: Option[HexUint],
  extraData: Array[Byte],
  size: HexUint,
  gasLimit: HexUint,
  gasUsed: HexUint,
  timestamp: HexUint,
  transactions: Seq[transactionOrTransactionHash],
  uncles: Seq[Array[Byte]]
) derives Schema

case class log(
  address: Address,
  blockHash: Array[Byte],
  blockNumber: HexUint,
  data: Array[Byte],
  logIndex: HexUint,
  removed: Boolean,
  topics: Seq[Array[Byte]],
  transactionHash: Array[Byte],
  transactionIndex: Option[HexUint]
) derives Schema
case class receipt(
  blockHash: Array[Byte],
  blockNumber: HexUint,
  contractAddress: Option[Address],
  cumulativeGasUsed: HexUint,
  from: Address,
  gasUsed: HexUint,
  logs: Seq[log],
  logsBloom: Array[Byte],
  to: Option[Address],
  transactionHash: Array[Byte],
  transactionIndex: Option[HexUint],
  postTransactionState: Array[Byte],
  status: Boolean
) derives Schema

case class transaction(
  blockHash: Option[Array[Byte]],
  blockNumber: Option[HexUint],
  from: Address,
  gas: HexUint,
  gasPrice: HexUint,
  hash: Array[Byte],
  input: Array[Byte],
  nonce: HexUint,
  to: Option[Address],
  transactionIndex: Option[HexUint],
  value: Array[Byte],
  v: HexUint,
  r: HexUint,
  s: HexUint
) derives Schema
