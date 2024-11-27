package liewhite.quant.trader.exchange

import zio.*
import liewhite.json.{*, given}
import java.time.ZonedDateTime
import java.time.ZoneId
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import java.util.Base64
import java.time.format.DateTimeFormatter
import liewhite.quant.trader.Trader
import zio.stream.*
import java.util.concurrent.TimeUnit
import java.net.InetSocketAddress
import java.net.URL
import zio.schema.codec.DecodeError
import liewhite.quant.trader.Trader.MarginMode
import liewhite.quant.trader.Trader.AggTrade
import okhttp3.*

class Okx(
  val ak: String,
  val skStr: String,
  val secret: String,
) extends Trader {

  val restUrl = "https://www.okx.com"
  val sk      = skStr.getBytes()

  val client= okhttp3.OkHttpClient.Builder().build()

  val wsClient =
    okhttp3.OkHttpClient.Builder().readTimeout(30, TimeUnit.SECONDS).pingInterval(10, TimeUnit.SECONDS).build()

  def start(): Task[Unit] =
    ZIO.unit

  def token2Symbol(token: String): String =
    s"$token-USDT-SWAP".toUpperCase()

  def symbolInfo(symbol: String): Task[Trader.SymbolInfo] =
    request[Unit, Seq[Map[String, String]]](
      "GET",
      s"api/v5/public/instruments",
      Map("instType" -> "SWAP", "instId" -> symbol),
      None,
      false
    ).map { data =>
      data.map(item =>
        Trader.SymbolInfo(
          item("instId"),
          item("settleCcy"),
          1,
          item("tickSz").toDouble,
          item("ctVal").toDouble,
          item("lotSz").toDouble
        )
      ).head
    }

  def getDepth(symbol: String, depth: Int): Task[Trader.OrderBook] =
    request[Unit, Seq[Okx.DepthResponse]](
      "GET",
      "/api/v5/market/books",
      Map("instId" -> symbol, "sz" -> depth.toString()),
      None,
      false
    ).map { data =>
      val item = data.head
      Trader.OrderBook(item.ts.toLong, item.bids.map(i => i.map(_.toDouble)), item.asks.map(i => i.map(_.toDouble)))
    }

  def klines(symbol: String, interval: String, limit: Int = 100): Task[Seq[Trader.Kline]] = {
    val result = request[Unit, Seq[Seq[String]]](
      "GET",
      s"api/v5/market/candles",
      Map("instId" -> symbol, "limit" -> limit.toString(), "bar" -> interval),
      None,
      false
    )
    result.map { data =>
      data.map { items =>
        Trader.Kline(
          items(0).toLong,
          items(1).toDouble,
          items(3).toDouble,
          items(2).toDouble,
          items(4).toDouble,
          items(7).toDouble, //volCcyQuote, usdt计价
          items(8) == "1"
        )
      }.reverse
    }
  }
  def getPositions(mgnMode: Trader.MarginMode): Task[Seq[Trader.RestPosition]] =
    request[Unit, Seq[Okx.Position]](
      "GET",
      "api/v5/account/positions",
      Map(
        "instType" -> "SWAP"
      ),
      None,
      true
    ).map { okp =>
      val po = okp.filter(_.mgnMode == mgnMode.toString().toLowerCase())
      po.flatMap { item =>
        if (item.pos.toDoubleOption.getOrElse(0.0) == 0) {
          Seq.empty
        } else {
          Seq(
            Trader.RestPosition(
              item.instId,
              Trader.MarginMode.parseOkx(item.mgnMode),
              Trader.PositionSide.parseOkx(item.posSide),
              item.pos.toDouble,
              item.avgPx.toDouble,
              item.cTime.toLong,
              item.uTime.toLong
            )
          )
        }
      }
    }

  def getSymbolPosition(symbol: String, mgnMode: Trader.MarginMode): Task[Option[Trader.RestPosition]] =
    request[Unit, Seq[Okx.Position]](
      "GET",
      "api/v5/account/positions",
      Map(
        "instType" -> "SWAP",
        "instId"   -> symbol
      ),
      None,
      true
    ).map { okp =>
      val po = okp.filter(_.mgnMode == mgnMode.toString().toLowerCase())
      if (po.isEmpty) {
        None
      } else {
        val p = po.head
        if (p.pos.toDoubleOption.getOrElse(0.0) == 0) {
          None
        } else {
          Some(
            Trader.RestPosition(
              p.instId,
              Trader.MarginMode.parseOkx(p.mgnMode),
              Trader.PositionSide.parseOkx(p.posSide),
              p.pos.toDouble,
              p.avgPx.toDouble,
              p.cTime.toLong,
              p.uTime.toLong
            )
          )
        }
      }
    }

  override def positionStream(symbol: String): ZStream[Any, Throwable, Trader.Position] =
    (stream[Chunk[Okx.Position]](
      "positions",
      symbol
    ).flattenChunks.map { item =>
      Trader.Position(
        symbol,
        Trader.MarginMode.parseOkx(item.mgnMode),
        Trader.PositionSide.parseOkx(item.posSide),
        item.pos.toDoubleOption.getOrElse(0.0),
        item.avgPx.toDoubleOption.getOrElse(0.0),
        item.cTime.toLong,
        item.uTime.toLong
      )
    })

  override def orderStream(symbol: String): ZStream[Any, Throwable, Trader.Order] =
    stream[Chunk[Okx.Order]](
      "orders",
      symbol
    ).flattenChunks.mapZIO { item =>
      ZIO.succeed(
        Trader.Order(
          item.instId,
          item.ordId,
          item.clOrdId,
          Trader.OrderAction.parse(item.side),
          item.avgPx.toDoubleOption.getOrElse(0.0),
          item.px.toDoubleOption.getOrElse(0.0),
          item.sz.toDoubleOption.getOrElse(0.0),
          item.fillSz.toDoubleOption.getOrElse(0.0),
          Trader.OrderState.parseOkx(item.state),
          Trader.OrderType.parseOkx(item.ordType, item.px),
          item.fee.toDoubleOption.getOrElse(0.0),
          item.cTime.toLong,
          item.uTime.toLong
        )
      )
    }

  override def createOrder(
    symbol: String,
    action: Trader.OrderAction,
    orderType: Trader.OrderType,
    quantity: Double,
    clientOrderID: Option[String],
    marginMode: Trader.MarginMode
  ): Task[String] = {
    val path = "api/v5/trade/order"
    val side = action.toString().toLowerCase()
    val (okOrderType, px) = orderType match {
      case Trader.OrderType.Limit(price, flag) => {
        (if (flag == Trader.LimitOrderFlag.Gtc) {
           "limit"
         } else if (flag == Trader.LimitOrderFlag.Ioc) {
           "ioc"
         } else if (flag == Trader.LimitOrderFlag.Fok) {
           "fok"
         } else if (flag == Trader.LimitOrderFlag.MakerOnly) {
           "post_only"
         } else {
           throw Exception(s"order type $flag is not supported")
         }) -> Some(BigDecimal(price).bigDecimal.toPlainString())
      }
      case Trader.OrderType.Market() => "market" -> None
    }
    val order = Okx.CreateOrderRequest(
      side,
      symbol,
      marginMode.toString().toLowerCase(),
      okOrderType,
      px,
      quantity.toString(),
      clientOrderID
    )

    request[Okx.CreateOrderRequest, Seq[
      Okx.RestResponse.CreateOrderResponse
    ]](
      "POST",
      path,
      Map.empty,
      Some(order)
    ).map(item => item.head.ordId)
  }

  override def getOpenOrders(symbol: Option[String]): Task[Seq[Trader.Order]] = {
    val path = "api/v5/trade/orders-pending"
    val params = symbol match
      case None =>
        Map(
          "instType" -> "SWAP"
        )
      case Some(value) =>
        Map(
          "instType" -> "SWAP",
          "instId"   -> value
        )

    val response = request[Unit, Seq[Okx.RestResponse.Order]](
      "GET",
      path,
      params,
      None
    ).map(os =>
      os.map { i =>
        Trader.Order(
          i.instId,
          i.ordId,
          i.clOrdId,
          Trader.OrderAction.parse(i.side),
          i.avgPx.toDoubleOption.getOrElse(0.0),
          i.px.toDoubleOption.getOrElse(0.0),
          i.sz.toDoubleOption.getOrElse(0.0),
          i.accFillSz.toDoubleOption.getOrElse(0.0),
          Trader.OrderState.parseOkx(i.state),
          Trader.OrderType.parseOkx(i.ordType, i.px),
          i.fee.toDoubleOption.getOrElse(0),
          i.cTime.toLong,
          i.uTime.toLong
        )
      }
    )
    response
  }

  override def revokeAll(symbol: Option[String]): Task[Unit] =
    for {
      orders <- getOpenOrders(symbol)
      _      <- revokeOrders(orders.map(o => Trader.BatchRevokeOrdersItem(o.symbol, Some(o.orderId), None)))
    } yield ()

  def revokeOrders(orders: Seq[Trader.BatchRevokeOrdersItem]): Task[Unit] =
    ZIO
      .when(orders.nonEmpty) {
        val req = orders.map { item =>
          Okx.RevokeOrderRequest(item.symbol, item.ordId, item.clOrdId)
        }
        request[Seq[Okx.RevokeOrderRequest], Seq[Okx.RevokeOrderResponse]](
          "POST",
          "api/v5/trade/cancel-batch-orders",
          Map.empty,
          Some(req)
        ) *> ZIO.succeed(())
      }
      .as(())

  override def revokeOrder(
    symbol: String,
    orderID: Option[String],
    clientOrderID: Option[String]
  ): Task[Unit] =
    request[Okx.RevokeOrderRequest, Seq[Okx.RevokeOrderResponse]](
      "POST",
      "api/v5/trade/cancel-order",
      Map.empty,
      Some(Okx.RevokeOrderRequest(symbol, orderID, clientOrderID))
    ) *> ZIO.succeed(())

  override def getOrder(
    symbol: String,
    orderID: Option[String],
    clientOrderID: Option[String]
  ): Task[Trader.Order] = {
    val path = "api/v5/trade/order"
    val qs   = Map("instId" -> symbol)
    val qsWithId = if (orderID.isDefined) {
      qs + ("ordId" -> orderID.get)
    } else if (clientOrderID.isDefined) {
      qs + ("clOrdId" -> clientOrderID.get)
    } else {
      throw Exception("empty ordId and clOrdId")
    }

    val response = request[Unit, Seq[Okx.RestResponse.Order]](
      "GET",
      path,
      qsWithId,
      None
    ).map(os =>
      os.map { i =>
        Trader.Order(
          i.instId,
          i.ordId,
          i.clOrdId,
          Trader.OrderAction.parse(i.side),
          i.avgPx.toDoubleOption.getOrElse(0),
          i.px.toDouble,
          i.sz.toDouble,
          i.accFillSz.toDouble,
          Trader.OrderState.parseOkx(i.state),
          Trader.OrderType.parseOkx(i.ordType, i.px),
          i.fee.toDoubleOption.getOrElse(0),
          i.cTime.toLong,
          i.uTime.toLong
        )
      }
    )
    response.map(_.head)
  }

  def authHeaders(method: String, path: String, body: String) = {
    val bodyStr = body

    val time    = ZonedDateTime.now().withZoneSameInstant(ZoneId.of("UTC"))
    val timeStr = time.format(DateTimeFormatter.ISO_INSTANT).split('.').head + s".${time.getNano() / 1000 / 1000}Z"

    val rawStr        = timeStr + method + path + bodyStr
    val mac           = Mac.getInstance("HmacSHA256")
    val secretKeySpec = new SecretKeySpec(sk, "SHA256")
    mac.init(secretKeySpec)
    val sig = mac.doFinal(rawStr.getBytes())
    val b64 = Base64.getEncoder().encodeToString(sig)

    Map(
      "OK-ACCESS-KEY"        -> ak,
      "OK-ACCESS-SIGN"       -> b64,
      "OK-ACCESS-TIMESTAMP"  -> timeStr,
      "OK-ACCESS-PASSPHRASE" -> secret,
      "content-type"         -> "application/json"
    )
  }

  def request[IN: Schema, OUT: Schema](
    method: String,
    path: String,
    qs: Map[String, String],
    body: Option[IN],
    auth: Boolean = true
  ): ZIO[Any, Throwable, OUT] = {
    val bodyStr = body match {
      case None        => ""
      case Some(value) => value.toJson.asString
    }
    val uri           = okhttp3.HttpUrl.parse(restUrl).newBuilder().addEncodedPathSegments(path)
    val uriWithParams = qs.foldLeft(uri)((acc, item) => acc.addQueryParameter(item._1, item._2)).build()

    val pathWithQs = uriWithParams.encodedPath() + (if (qs.isEmpty) { "" }
                                                    else { "?" + uriWithParams.encodedQuery() })

    val headers = if (auth) {
      authHeaders(
        method,
        pathWithQs,
        bodyStr
      )
    } else {
      Map(
        "content-type" -> "application/json"
      )
    }
    val urlReq         = okhttp3.Request.Builder().url(uriWithParams)
    val reqWithHeaders = headers.foldLeft(urlReq)((acc, item) => acc.addHeader(item._1, item._2))
    val requestWithBody = body match
      case None => reqWithHeaders.get()
      case Some(value) =>
        reqWithHeaders.method(
          method,
          okhttp3.RequestBody.create(bodyStr, MediaType.parse("application/json; charset=utf-8"))
        )
    val req = requestWithBody.build()
    (ZIO.attemptBlocking {
      client.newCall(req).execute()
    }.flatMap { res =>

      val body = String(res.body().bytes())
      if (!res.isSuccessful()) {
        ZIO.fail(Exception(s"failed send request: ${path} ${res.code()}"))
      } else {
        ZIO.fromEither {
          body.fromJson[Okx.RestResponse[OUT]].map(_.data).left.map(e => e.and(DecodeError.EmptyContent(body)))
        }
      }
    }).timed
      .flatMap(i => ZIO.logDebug(s"$path cost ${i._1.getNano() / 1000 / 1000} ms") *> ZIO.succeed(i._2))
  }

  def getBalance(ccy: String) = {
    val path = s"api/v5/account/balance"
    request[Unit, Seq[Okx.RestResponse.Balance]](
      "GET",
      path,
      Map("ccy" -> ccy.toUpperCase()),
      None
    ).map(res => Trader.Balance(ccy, res.head.details.head.availBal.toDouble))
  }

  override def klineStream(symbol: String, interval: String) = {
    val wssUrl = "wss://ws.okx.com:8443/ws/v5/business"
    publicStream[Seq[String], Trader.Kline](
      symbol,
      wssUrl,
      s"candle$interval",
      k => {
        Right(
          Trader.Kline(
            k(0).toLong,
            k(1).toDouble,
            k(3).toDouble,
            k(2).toDouble,
            k(4).toDouble,
            k(5).toDouble,
            k(8) == "1"
          )
        )
      }
    )
  }

  case class OkOrderBook(ts: String, asks: Seq[Seq[String]], bids: Seq[Seq[String]]) derives Schema

  override def orderbookStream(symbol: String): ZStream[Any, Throwable, Trader.OrderBook] = {
    val wssUrl  = "wss://ws.okx.com:8443/ws/v5/public"
    val channel = "bbo-tbt"
    publicStream[OkOrderBook, Trader.OrderBook](
      symbol,
      wssUrl,
      channel,
      ok => {
        val asks = ok.asks.map { item =>
          Seq(item(0).toDouble, item(1).toDouble) // price, vol
        }
        val bids = ok.bids.map { item =>
          Seq(item(0).toDouble, item(1).toDouble) // price, vol
        }
        Right(
          Trader.OrderBook(
            ok.ts.toLong,
            bids,
            asks
          )
        )
      }
    )
  }

  case class OkTrades(ts: String, px: String, sz: String, side: String) derives Schema

  def aggTradeStream(symbol: String): ZStream[Any, Throwable, AggTrade] = {
    val wssUrl = "wss://ws.okx.com:8443/ws/v5/public"
    publicStream[OkTrades, Trader.AggTrade](
      symbol,
      wssUrl,
      "trades",
      item => {
        Right(
          Trader.AggTrade(
            symbol,
            item.ts.toLong,
            item.px.toDouble,
            item.sz.toDouble,
            Trader.OrderAction.parse(item.side)
          )
        )
      }
    )
  }

  def publicStream[OK: Schema, OUT: Schema](
    symbol: String,
    wss: String,
    channel: String,
    transformer: OK => Either[Throwable, OUT]
  ): ZStream[Any, Throwable, OUT] = {
    val wssUrl = wss
    val s = ZStream.asyncScoped[Any, Throwable, OUT] { cb =>
      val req = new Request.Builder().url(wssUrl).build()
      val ws = wsClient.newWebSocket(
        req,
        new WebSocketListener {
          override def onOpen(x: WebSocket, y: Response): Unit = {
            val req = Okx.WsRequest[Okx.WsSubscribeRequest](
              "subscribe",
              Seq(
                Okx.WsSubscribeRequest(
                  channel,
                  "",
                  symbol
                )
              )
            )
            x.send(req.toJson.asString)
          }

          override def onMessage(s: WebSocket, x: String): Unit =
            if (x.contains("error")) {
              cb(ZIO.fail(Some(Exception(s"$channel subscribe failed $x "))))
            } else if (x.contains("data")) {
              x.fromJson[Okx.WsPush[Seq[OK]]] match
                case Left(value) => {
                  cb(ZIO.fail(Some(Exception(s"$channel Unknown push data $x"))))
                }
                case Right(value) => {
                  val data = value.data
                  if (data.isEmpty) {
                    cb(ZIO.fail(Some(Exception(s"$channel empty $x"))))
                  } else {
                    val k      = data.head
                    val result = transformer(k).map(Chunk(_)).left.map(Some(_): Option[Throwable])
                    cb(ZIO.fromEither(result))
                  }
                }
            }

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
    s.tapError { e =>
      e.printStackTrace()
      ZIO.logWarning(e.toString())
    }.retry(Schedule.fibonacci(3.second))
  }

  private def stream[T: Schema](channel: String, instId: String) = {
    val wssUrl = "wss://ws.okx.com:8443/ws/v5/private"
    // 状态从loginSent -> subscriptionSent -> running
    val s = ZStream.asyncScoped[Any, Throwable, T] { cb =>

      var state = "loginSent"

      val req = new Request.Builder().url(wssUrl).build()
      val ws = wsClient.newWebSocket(
        req,
        new WebSocketListener {
          override def onOpen(x: WebSocket, y: Response): Unit = {
            val now =
              ZonedDateTime.now().toInstant().toEpochMilli().toDouble / 1000
            val mac           = Mac.getInstance("HmacSHA256")
            val secretKeySpec = new SecretKeySpec(sk, "HmacSHA256")
            mac.init(secretKeySpec)
            val sig = mac.doFinal(
              (now.toString() + "GET" + "/users/self/verify").getBytes()
            )
            val b64 = Base64.getEncoder().encodeToString(sig)
            // 登录
            val req = Okx.WsRequest[Okx.WsLoginRequest](
              "login",
              Seq(
                Okx.WsLoginRequest(
                  ak,
                  secret,
                  now.toString(),
                  b64
                )
              )
            )
            println(s"send login to $channel")
            x.send(req.toJson.asString)
          }

          override def onMessage(s: WebSocket, x: String): Unit =
            if (x == "pong") {} else {
              // 收到登录成功的消息就订阅
              if (state == "loginSent") {
                state = "subscriptionSent"
                x.fromJson[Okx.WsLoginResponse] match
                  case Left(value) =>
                    cb(ZIO.fail(Some(Exception(s"Login failed $x"))).debug)
                  case Right(value) => {
                    if (value.code != "0") {
                      cb(ZIO.fail(Some(Exception(s"Login failed $x"))))
                    } else {
                      println(s"login to $channel success, send subscribe")
                      // 订阅
                      s.send(
                        Okx
                          .WsRequest[Okx.WsSubscribeRequest](
                            op = "subscribe",
                            args = Seq(
                              Okx.WsSubscribeRequest(
                                channel = channel,
                                instType = "SWAP",
                                instId = instId
                              )
                            )
                          )
                          .toJson
                          .asString
                      )
                    }
                  }
              } else if (state == "subscriptionSent") {
                // 等待订阅结果
                state = "running"
                x.fromJson[Okx.WsSubscribeResponse] match
                  case Left(value) => {
                    cb(ZIO.fail(Some(Exception(s"Subscribe failed $x"))))
                  }
                  case Right(value) => {
                    println("Subscribe success")
                  }

              } else {
                x.fromJson[Okx.WsPush[T]] match
                  case Left(value) => {
                    cb(ZIO.fail(Some(Exception(s"Unknown push data $x"))))
                  }
                  case Right(value) => {
                    cb(ZIO.succeed(Chunk(value.data)))
                  }
              }

            }

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
    s.tapError { e =>
      e.printStackTrace()
      ZIO.logWarning(e.toString())
    }.retry(Schedule.fibonacci(3.second))
  }
}

object Okx {
  case class RestResponse[T](code: String, data: T) derives Schema
  object RestResponse {
    case class Order(
      instId: String,
      ordId: String,
      px: String,
      clOrdId: String,
      side: String,
      avgPx: String,
      sz: String,
      accFillSz: String,
      state: String,
      ordType: String,
      fee: String,
      uTime: String,
      cTime: String
    ) derives Schema

    case class Balance(
      adjEq: String,
      details: Seq[BalanceDetail]
    ) derives Schema

    case class BalanceDetail(availBal: String) derives Schema

    case class CreateOrderResponse(
      clOrdId: Option[String],
      ordId: String,
      sCode: String,
      sMsg: String
    ) derives Schema
  }
  case class DepthResponse(
    asks: Seq[Seq[String]],
    bids: Seq[Seq[String]],
    ts: String
  ) derives Schema

  case class SetLeverageReq(
    instId: String,
    lever: String,
    mgnMode: String
  ) derives Schema

  case class GetDepthReq(
    instId: String,
    depth: Int
  ) derives Schema

  case class CreateOrderRequest(
    side: String,
    instId: String,
    tdMode: String,
    ordType: String,
    px: Option[String],
    sz: String,
    clOrdId: Option[String]
  ) derives Schema

  case class RevokeOrderRequest(
    instId: String,
    ordId: Option[String],
    clOrdId: Option[String]
  ) derives Schema

  case class RevokeOrderResponse(
    ordId: String,
    clOrdId: String
  ) derives Schema

  // websocket
  case class WsRequest[T](
    op: String,
    args: Seq[T]
  ) derives Schema

  case class WsLoginRequest(
    apiKey: String,
    passphrase: String,
    timestamp: String,
    sign: String
  ) derives Schema

  case class WsLoginResponse(
    event: String,
    code: String
  ) derives Schema

  case class WsSubscribeRequest(
    channel: String,
    instType: String,
    instId: String
  ) derives Schema

  case class WsSubscribeResponse(
    event: String,
    arg: Json
  ) derives Schema

  case class WsPush[T](
    data: T
  ) derives Schema

  case class Position(
    instId: String,
    posId: String,
    posSide: String,  //
    pos: String,      // 持仓数量
    availPos: String, // 可平仓数量
    mgnMode: String,  // 保证金模式
    avgPx: String,    // 开仓均价
    cTime: String,
    uTime: String
  ) derives Schema

  case class Order(
    instId: String,
    ordId: String,
    clOrdId: String,
    accFillSz: String, // 累积成交
    fee: String,       // 累积手续费
    px: String,        // 委托价格
    sz: String,        // 委托数量
    avgPx: String,     // 成交均价
    ordType: String,
    side: String,
    tdMode: String,
    fillPx: String, // 最新成交价
    fillSz: String, // 最新成交数量
    state: String,
    cTime: String,
    uTime: String
  ) derives Schema
}

enum HttpMethod derives Schema {
  case GET
  case POST
}

extension (o: Trader.PositionSide.type) {
  def parseOkx(s: String): Trader.PositionSide =
    if (s == "long") {
      Trader.PositionSide.Long
    } else if (s == "short") {
      Trader.PositionSide.Short
    } else if (s == "net") {
      Trader.PositionSide.Net
    } else {
      throw Exception(s"unknown position side: $s")
    }
}
extension (o: Trader.OrderState.type) {
  def parseOkx(s: String): Trader.OrderState =
    if (s == "live") {
      Trader.OrderState.Submitted
    } else if (s == "partially_filled") {
      Trader.OrderState.PartialFilled
    } else if (s == "canceled") {
      Trader.OrderState.Canceled
    } else if (s == "filled") {
      Trader.OrderState.Filled
    } else {
      throw Exception(s"unknown order state : $s")
    }
}
extension (o: Trader.OrderType.type) {
  def parseOkx(s: String, price: String): Trader.OrderType =
    if (s == "market") {
      Trader.OrderType.Market()
    } else if (s == "limit") {
      Trader.OrderType.Limit(price.toDouble, Trader.LimitOrderFlag.Gtc)
    } else if (s == "post_only") {
      Trader.OrderType.Limit(price.toDouble, Trader.LimitOrderFlag.MakerOnly)
    } else if (s == "fok") {
      Trader.OrderType.Limit(price.toDouble, Trader.LimitOrderFlag.Fok)
    } else if (s == "ioc") {
      Trader.OrderType.Limit(price.toDouble, Trader.LimitOrderFlag.Ioc)
    } else {
      throw Exception(s"unknown order type : $s")
    }
}

extension (o: Trader.MarginMode.type) {
  def parseOkx(s: String): Trader.MarginMode =
    if (s == "isolated") {
      Trader.MarginMode.Isolated
    } else if (s == "cross") {
      Trader.MarginMode.Cross
    } else {
      throw Exception(s"unknown margin mode : $s")
    }
}
