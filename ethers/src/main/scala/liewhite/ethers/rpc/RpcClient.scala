package liewhite.ethers.rpc
import zio.*
import zio.json.ast.Json
import zio.http.*
import liewhite.json.{*, given}
import java.net.URI
import liewhite.ethers.types.HexUint
import liewhite.ethers.types.Address
import liewhite.ethers.bytesToHex
import liewhite.ethers.hexToBytes

class RpcClient(val client: Client, id: Ref[Long]) {
  def request[I: Schema, O: Schema](method: String, params: I): ZIO[Any, Throwable, O] =
    ZIO.scoped {
      for {
        reqId  <- id.getAndUpdate(_ + 1)
        req     = JsonRpcRequest(reqId, "2.0", method, params.toJsonAst)
        res    <- client.post("")(Body.fromString(req.toJson.asString))
        body   <- res.body.asString
        parsed <- ZIO.fromEither(body.fromJson[JsonRpcResponse[O]])
        result <- {
          if (parsed.error.isDefined) {
            ZIO.fail(Exception(parsed.error.get.toJson.asString))
          } else {
            ZIO.succeed(parsed.result.get)
          }
        }
      } yield result

    }

  def eth_chainId(): ZIO[Any, Throwable, HexUint] =
    request[Seq[Unit], HexUint](
      "eth_gasPrice",
      Seq.empty[Unit]
    )
  def eth_gasPrice(): ZIO[Any, Throwable, HexUint] =
    request[Seq[Unit], HexUint](
      "eth_gasPrice",
      Seq.empty[Unit]
    )
  def eth_blockNumber(): ZIO[Any, Throwable, HexUint] =
    request[Seq[Unit], HexUint](
      "eth_blockNumber",
      Seq.empty[Unit]
    )
  def eth_getBalance(
    address: Address,
    blockNumber: BlockNumberOrTag = BlockNumberOrTag.Tag(BlockNumberTag.latest)
  ): ZIO[Any, Throwable, HexUint] =
    request[(Address, BlockNumberOrTag), HexUint](
      "eth_getBalance",
      (address, blockNumber)
    )
  def eth_getStorageAt(
    address: Address,
    position: HexUint,
    blockNumber: BlockNumberOrTag = BlockNumberOrTag.Tag(BlockNumberTag.latest)
  ): ZIO[Any, Throwable, Array[Byte]] =
    request[Json, Array[Byte]](
      "eth_getStorageAt",
      Json.Arr(address.toJsonAst, position.toJsonAst, blockNumber.toJsonAst)
    )
  def eth_getTransactionCount(
    address: Address,
    blockNumber: BlockNumberOrTag = BlockNumberOrTag.Tag(BlockNumberTag.latest)
  ) =
    request[(Address, BlockNumberOrTag), HexUint](
      "eth_getTransactionCount",
      (address, blockNumber)
    )

  def eth_getCode(
    address: Address,
    blockNumber: BlockNumberOrTag = BlockNumberOrTag.Tag(BlockNumberTag.latest)
  ) =
    request[(Address, BlockNumberOrTag), Array[Byte]](
      "eth_getCode",
      (address, blockNumber)
    )

  def eth_sendRawTransaction(
    txs: Seq[Array[Byte]]
  ) =
    request[Seq[Array[Byte]], Array[Byte]](
      "eth_sendRawTransaction",
      txs
    )

  def eth_call(
    call: TransactionCall,
    blockNumber: BlockNumberOrTag = BlockNumberOrTag.Tag(BlockNumberTag.latest)
  ) =
    request[(TransactionCall, BlockNumberOrTag), Array[Byte]](
      "eth_call",
      (call, blockNumber)
    )
  def eth_estimateGas(
    call: TransactionCall,
    blockNumber: BlockNumberOrTag = BlockNumberOrTag.Tag(BlockNumberTag.latest)
  ) =
    request[(TransactionCall, BlockNumberOrTag), HexUint](
      "eth_estimateGas",
      (call, blockNumber)
    )

  def eth_getBlockByNumber(
    blockNumber: BlockNumberOrTag,
    withTxs: Boolean = true
  ) =
    request[(BlockNumberOrTag, Boolean), Block](
      "eth_getBlockByNumber",
      (blockNumber, withTxs)
    )
  def eth_getBlockByHash(
    blockNumber: BlockNumberOrTag,
    withTxs: Boolean = true
  ) =
    request[(BlockNumberOrTag, Boolean), Block](
      "eth_getBlockByHash",
      (blockNumber, withTxs)
    )
}

object RpcClient {
  def apply(url: String): ZIO[Scope, Throwable, RpcClient] =
    (for {
      default       <- ZIO.service[Client]
      uri           <- ZIO.fromEither(URL.decode(url))
      cli            = default.url(uri)
      cliWithHeaders = cli.addHeader(Header.ContentType(MediaType.application.json))
      id            <- Ref.make(0L)
    } yield new RpcClient(cliWithHeaders, id))
      .provideLayer(
        Client.default.extendScope
      ) // Client.default是scoped layer， 所以会在provide的那个effect执行完后就release了, 这里取消该layer的scope， 开放给外层
}

object TestRpc extends ZIOAppDefault {

  override def run: ZIO[Any & (ZIOAppArgs & Scope), Any, Any] =
    val url = "https://eth-hk1.csnodes.com/v1/973eeba6738a7d8c3bd54f91adcbea89"
    val cli = RpcClient("https://eth-hk1.csnodes.com/v1/973eeba6738a7d8c3bd54f91adcbea89")
    val e1 = (for {
      cli <- RpcClient(url)
      blk <- cli.eth_getBlockByNumber(BlockNumberOrTag.Tag(BlockNumberTag.latest), false).map(_.transactions.toJsonAst).debug("block: ")
      // _   <- cli.eth_getBlockByNumber(BlockNumberOrTag.Number(HexUint(10)), false).map(_.toJsonAst).debug
    } yield ())
    e1
}
