package liewhite.ethers.rpc
import zio.*
import zio.json.ast.Json
import zio.http.*
import liewhite.json.{*, given}
import java.net.URI

class RpcClient(val client: Client, id: Ref[Long]) {
  def request[O: Schema](method: String, params: Seq[Json]): ZIO[Scope, Throwable, O] =
    ZIO.scoped {
      for {
        reqId  <- id.getAndUpdate(_ + 1)
        req     = JsonRpcRequest(reqId, "2.0", method, params)
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

  def eth_gasPrice() =
    request[Json](
      "eth_gasPrice",
      Seq.empty[Json]
    )

  def eth_getBlockByNumber(
    blockNumber: JsonRpcRequest.BlockNumber,
    withTxs: Boolean = true
  ): ZIO[Scope, Throwable, Block] =
    blockNumber match
      case JsonRpcRequest.BlockNumber.latest => {
        request[Block](
          "eth_getBlockByNumber",
          Seq("latest".toJsonAst, withTxs.toJsonAst)
        )
      }
      case JsonRpcRequest.BlockNumber.Number(n) => {
        request[Block](
          "eth_getBlockByNumber",
          Seq(n.toJsonAst, withTxs.toJsonAst)
        )

      }
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
      blk <- cli.eth_getBlockByNumber(JsonRpcRequest.BlockNumber.latest, false).map(_.toJsonAst).debug
      _ <- cli.eth_getBlockByNumber(JsonRpcRequest.BlockNumber.latest, false).map(_.toJsonAst).debug
    } yield ())
    e1
}

object SimpleClient extends ZIOAppDefault {

  val program = (for {
    client        <- ZIO.service[Client]
    uri           <- ZIO.fromEither(URL.decode("https://eth-hk1.csnodes.com/v1/973eeba6738a7d8c3bd54f91adcbea89"))
    cli            = client.url(uri)
    cliWithHeaders = cli.addHeader(Header.ContentType(MediaType.application.json))
    req            = JsonRpcRequest(0, "2.0", "eth_getBlockByNumber", Seq(Json.Num(1)))
    res <- cliWithHeaders
             .post("")(Body.fromString(req.toJson.asString))
    data <- res.body.asString
    _    <- Console.printLine(data)
  } yield ()).provideSomeLayer(Client.default)

  override def run: ZIO[Any & (ZIOAppArgs & Scope), Any, Any] =
    program
}
