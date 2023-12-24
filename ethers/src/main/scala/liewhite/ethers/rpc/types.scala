package liewhite.ethers.rpc

import liewhite.json.{*,given}
import liewhite.ethers.types.HexUint

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
  params: Seq[Json]
) derives Schema

import zio.schema.annotation.noDiscriminator

@noDiscriminator
enum BlockNumber derives Schema {
  case latest
  case Number(n: HexUint)
}