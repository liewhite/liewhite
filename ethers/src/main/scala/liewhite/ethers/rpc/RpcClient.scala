package liewhite.ethers.rpc
import zio.*
import zio.json.ast.Json
import zio.stream.*
import liewhite.json.{*, given}
import java.net.URI
import liewhite.ethers.types.HexUint
import liewhite.ethers.types.Address
import liewhite.ethers.bytesToHex
import liewhite.ethers.hexToBytes
import java.util.concurrent.TimeUnit
import okhttp3.*
import okhttp3.internal.http2.Header
import zio.schema.codec.DecodeError
import liewhite.ethers.abi.ABITypeAddress
import liewhite.ethers.*
import liewhite.ethers.abi.ABIFunction
import liewhite.ethers.abi.ABITypeTuple
import liewhite.ethers.abi.ABITypeUint

class RpcClient(val url: String, id: Ref[Long], proxy: Option[java.net.Proxy] = None) {
  val clientBuilder = okhttp3.OkHttpClient.Builder()
  val client = (proxy match
    case None        => clientBuilder
    case Some(value) => clientBuilder.proxy(value)
  ).build()

  def request[I: Schema, O: Schema](method: String, params: I): ZIO[Any, Throwable, O] =
    for {
      reqId <- id.getAndUpdate(_ + 1)
      req    = JsonRpcRequest(method, params.toJsonAst, reqId, "2.0")
      res <- ZIO.attemptBlocking {
               val urlReq   = okhttp3.Request.Builder().url(url)
               val body     = RequestBody.create(MediaType.parse("application/json"), req.toJson.asString)
               val request  = urlReq.post(body).build()
               val response = client.newCall(request).execute()
               response.body().string()
             }
      parsed <- ZIO.fromEither(res.fromJson[JsonRpcResponse[O]])
      result <- {
        if (parsed.error.isDefined) {
          ZIO.fail(Exception(parsed.error.get.toJson.asString))
        } else {
          ZIO.succeed(parsed.result.get)
        }
      }
    } yield result

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
    hash: Array[Byte],
    withTxs: Boolean = true
  ) =
    request[(Array[Byte], Boolean), Block](
      "eth_getBlockByHash",
      (hash, withTxs)
    )
  def eth_getTransactionReceipt(
    hash: Array[Byte]
  ) =
    request[Seq[Array[Byte]], Receipt](
      "eth_getTransactionReceipt",
      Seq(hash)
    )
}

object RpcClient {
  def apply(url: String): ZIO[Scope, Throwable, RpcClient] =
    (for {
      id <- Ref.make(0L)
    } yield new RpcClient(url, id))
}

class WebsocketClient(val url: String, id: Ref[Long]) {
  val wsClient =
    okhttp3.OkHttpClient.Builder().readTimeout(30, TimeUnit.SECONDS).pingInterval(10, TimeUnit.SECONDS).build()

  def newHeads(): ZStream[Any, Throwable, liewhite.ethers.rpc.Header] =
    val s = "newHeads"
    subscribe[Seq[String], liewhite.ethers.rpc.Header](Seq(s))

  def newPendingTxs(withBody: Boolean): ZStream[Any, Throwable, Transaction] =
    subscribe[(String, Boolean), Transaction](("newPendingTransactions", withBody))

  def newLogs(filter: liewhite.ethers.rpc.LogFilter): ZStream[Any, Throwable, Log] =
    subscribe[(String, liewhite.ethers.rpc.LogFilter), Log](("logs", filter))

  def subscribe[T: Schema, O: Schema](
    params: T
  ): ZStream[Any, Throwable, O] =
    ZStream.unwrap(id.getAndUpdate(_ + 1).map { id =>
      ZStream.asyncScoped[Any, Throwable, O] { cb =>
        val req = new Request.Builder().url(url).build()
        val ws = wsClient.newWebSocket(
          req,
          new WebSocketListener {
            override def onOpen(x: WebSocket, y: Response): Unit =
              x.send(JsonRpcRequest("eth_subscribe", params.toJsonAst, id, "2.0").toJson.asString)

            override def onMessage(s: WebSocket, x: String): Unit =
              x.fromJson[SubscribeStreamItem[O]] match
                // 不是推送消息， 检查是否是订阅结果
                case Left(value) => {
                  val result = x.fromJson[JsonRpcResponse[String]]
                  result match
                    case Left(value) => cb(ZIO.fail(Some(Exception(s"$value unknown ws msg: $x"))))
                    case Right(value) =>
                      if (value.error.isDefined) {
                        cb(ZIO.fail(Some(Exception(s"subscribe failed: $x"))))
                      }
                }
                case Right(value) =>
                  cb(ZIO.succeed(Chunk(value.params.result)))

            override def onClosing(x: WebSocket, y: Int, z: String): Unit =
              cb(
                ZIO.logWarning(
                  s"orderbook market websocket closing:$y, $z"
                ) *> ZIO.fail(Some(Exception(z)))
              )

            override def onFailure(
              s: WebSocket,
              e: Throwable,
              x: Response
            ): Unit = {
              val result = ZIO.logWarning(e.toString()) *> ZIO.succeed(
                e.printStackTrace()
              ) *> ZIO.logInfo(
                "market websocket closed, reloading"
              ) *> ZIO.fail(Some(e))
              cb(result)
            }
          }
        )
        ZIO.acquireRelease(ZIO.succeed(ws))(s => ZIO.succeed(s.close(1001, "release")))
      }

    })
}

object WebsocketClient {
  def apply(url: String): ZIO[Scope, Throwable, WebsocketClient] =
    (for {
      id <- Ref.make(0L)
    } yield new WebsocketClient(url, id))
}

object TestRpc extends ZIOAppDefault {
  override def run: ZIO[Any & (ZIOAppArgs & Scope), Any, Any] =
    val url = "https://eth-hk1.csnodes.com/v1/973eeba6738a7d8c3bd54f91adcbea89"
    val wss = "wss://eth-hk1.csnodes.com/ws/v1/973eeba6738a7d8c3bd54f91adcbea89"

    val balanceOf = ABIFunction("balanceOf", ABITypeTuple(ABITypeAddress), ABITypeUint(256))
    val data = balanceOf.encodeInput(Tuple(Address.fromHex("0x151D6C9C114976cFc0baA8F9B3291AfBea9f3549")))
    
    val e1 = (for {
      cli <- RpcClient(url)
      balance <- cli
                   .eth_call(
                     TransactionCall(
                       "0x151D6C9C114976cFc0baA8F9B3291AfBea9f3549",
                       Some("0xdAC17F958D2ee523a2206206994597C13D831ec7"),
                       data = Some(data)
                     )
                   )
                   .map(balanceOf.decodeOutput(_))
                   .debug
      // wsCli <- WebsocketClient(wss)
      // ws     = wsCli.newLogs(LogFilter(None, Seq.empty))
      // _     <- ws.map(_.toJson.asString).debug.runDrain
      // logs <- ZIO.withParallelism(10)(ZIO.collectAllPar(blks.flatten.map(cli.eth_getTransactionReceipt(_).debug)))
      // _ = ZIO.collectAll(blks.map(b => ZIO.log(s"${b.transactions.length}"))).debug
      // _   <- cli.eth_getBlockByNumber(BlockNumberOrTag.Number(HexUint(10)), false).map(_.toJsonAst).debug
    } yield ())
    e1
}
