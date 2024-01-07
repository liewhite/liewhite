package quant.trader.exchange

import zio.*
import liewhite.json.{*, given}
import java.time.ZonedDateTime
import java.time.ZoneId
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import java.util.Base64
import zio.http
import java.time.format.DateTimeFormatter
import quant.trader.Trader
import zio.stream.*
import okhttp3.*
import java.util.concurrent.TimeUnit
import java.net.InetSocketAddress
import java.net.URL
import zio.schema.codec.DecodeError

class Okx(
  val baseToken: String,
  val quoteToken: String,
  val ak: String,
  val skStr: String,
  val secret: String,
  val proxy: Option[java.net.Proxy]
) extends Trader {

  val restUrl = "https://www.okx.com"

  val sk            = skStr.getBytes()
  val clientBuilder = okhttp3.OkHttpClient.Builder()
  val client = (proxy match
    case None        => clientBuilder
    case Some(value) => clientBuilder.proxy(value)
  ).build()

  val wsClient =
    okhttp3.OkHttpClient.Builder().readTimeout(30, TimeUnit.SECONDS).pingInterval(10, TimeUnit.SECONDS).build()

  val factorSymbol = (baseToken + quoteToken).toLowerCase()
  // todo 根据market改变后缀
  val exchangeSymbol = s"$baseToken-$quoteToken-SWAP".toUpperCase()

  def start(): Task[Unit] =
    ZIO.unit

  def symbolInfo(): Task[Trader.SymbolInfo] =
    request[Unit, Seq[Map[String, String]]](
      zio.http.Method.GET,
      s"api/v5/public/instruments",
      Map("instId" -> exchangeSymbol, "instType" -> "SWAP"),
      None,
      false
    ).map { data =>
      val result = data(0)
      Trader.SymbolInfo(1, result("tickSz").toDouble, result("ctVal").toDouble)
    }
  def setLeverage(leverage: Int, tp: Trader.MarginMode): Task[Unit] =
    request[Okx.SetLeverageReq, Json](
      zio.http.Method.POST,
      "api/v5/account/set-leverage",
      Map.empty,
      Some(Okx.SetLeverageReq(exchangeSymbol, leverage.toString(), tp.toString().toLowerCase())),
      true
    ).unit

  def klines(interval: String, limit: Int = 100): Task[Seq[Trader.Kline]] = {
    val result = request[Unit, Seq[Seq[String]]](
      zio.http.Method.GET,
      s"api/v5/market/candles",
      Map("instId" -> exchangeSymbol, "limit" -> limit.toString(), "bar" -> interval),
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
          items(5).toDouble,
          items(8) == "1"
        )
      }.reverse
    }
  }

  def getPosition(): Task[Option[Trader.RestPosition]] =
    request[Unit, Seq[Okx.Position]](
      zio.http.Method.GET,
      "api/v5/account/positions",
      Map(
        "instType" -> "SWAP",
        "instId"   -> exchangeSymbol
      ),
      None,
      true
    ).map { okp =>
      if (okp.isEmpty) {
        None
      } else {
        val p = okp.head
        if (p.pos.toDoubleOption.getOrElse(0.0) == 0) {
          None
        } else {
          Some(
            Trader.RestPosition(
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

  override def positionStream(): ZStream[Any, Throwable, Trader.Position] =
    (stream[Chunk[Okx.Position]](
      "positions",
      exchangeSymbol
    ).flattenChunks.map { item =>
      Trader.Position(
        Trader.MarginMode.parseOkx(item.mgnMode),
        Trader.PositionSide.parseOkx(item.posSide),
        item.pos.toDoubleOption,
        item.avgPx.toDoubleOption,
        item.cTime.toLong,
        item.uTime.toLong
      )
    })

  override def orderStream(): ZStream[Any, Throwable, Trader.Order] =
    stream[Chunk[Okx.Order]](
      "orders",
      exchangeSymbol
    ).flattenChunks.mapZIO { item =>
      ZIO.succeed(
        Trader.Order(
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
    action: Trader.OrderAction,
    orderType: Trader.OrderType,
    quantity: Double,
    clientOrderID: Option[String],
    marginMode: Trader.MarginMode
  ): Task[String] = {
    val path   = "api/v5/trade/order"
    val symbol = exchangeSymbol
    val side   = action.toString().toLowerCase()
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
         }) -> Some(price.toString())
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
      http.Method.POST,
      path,
      Map.empty,
      Some(order)
    ).map(item => item.head.ordId)
  }

  override def getOpenOrders(): Task[Seq[Trader.Order]] = {
    val path = "api/v5/trade/orders-pending"
    val response = request[Unit, Seq[Okx.RestResponse.Order]](
      http.Method.GET,
      path,
      Map(
        "instType" -> "SWAP",
        "instId"   -> exchangeSymbol
      ),
      None
    ).map(os =>
      os.map { i =>
        Trader.Order(
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

  def revokeOrders(orders: Seq[Trader.BatchRevokeOrdersItem]): Task[Unit] =
    ZIO
      .when(orders.nonEmpty) {
        val req = orders.map { item =>
          Okx.RevokeOrderRequest(exchangeSymbol, item.ordId, item.clOrdId)
        }
        request[Seq[Okx.RevokeOrderRequest], Seq[Okx.RevokeOrderResponse]](
          http.Method.POST,
          "api/v5/trade/cancel-batch-orders",
          Map.empty,
          Some(req)
        ) *> ZIO.succeed(())
      }
      .as(())

  override def revokeOrder(
    orderID: Option[String],
    clientOrderID: Option[String]
  ): Task[Unit] = {
    val symbol = exchangeSymbol
    request[Okx.RevokeOrderRequest, Seq[Okx.RevokeOrderResponse]](
      http.Method.POST,
      "api/v5/trade/cancel-order",
      Map.empty,
      Some(Okx.RevokeOrderRequest(symbol, orderID, clientOrderID))
    ) *> ZIO.succeed(())
  }

  override def getOrder(
    orderID: Option[String],
    clientOrderID: Option[String]
  ): Task[Trader.Order] = {
    val path = "api/v5/trade/order"
    val qs = Map(
      "instId" -> exchangeSymbol
    )
    val qsWithId = if (orderID.isDefined) {
      qs + ("ordId" -> orderID.get)
    } else if (clientOrderID.isDefined) {
      qs + ("clOrdId" -> clientOrderID.get)
    } else {
      throw Exception("empty ordId and clOrdId")
    }

    val response = request[Unit, Seq[Okx.RestResponse.Order]](
      http.Method.GET,
      path,
      qsWithId,
      None
    ).map(os =>
      os.map { i =>
        Trader.Order(
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

  def authHeaders(method: http.Method, path: String, body: String) = {
    val bodyStr = body

    val time    = ZonedDateTime.now().withZoneSameInstant(ZoneId.of("UTC"))
    val timeStr = time.format(DateTimeFormatter.ISO_INSTANT).split('.').head + s".${time.getNano() / 1000 / 1000}Z"

    val rawStr        = timeStr + method.name + path + bodyStr
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
    method: http.Method,
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
          method.name,
          okhttp3.RequestBody.create(bodyStr, MediaType.parse("application/json; charset=utf-8"))
        )
    val req = requestWithBody.build()
    (ZIO.attemptBlocking {
      client.newCall(req).execute()
    }.flatMap { res =>
      if (!res.isSuccessful()) {
        ZIO.fail(Exception(s"failed send request: ${res.code()}"))
      } else {
        ZIO.fromEither {
          val body = String(res.body().bytes())
          // println("response" -> body)
          body.fromJson[Okx.RestResponse[OUT]].map(_.data).left.map(e => e.and(DecodeError.EmptyContent(body)))
        }
      }
    }).timed
      .flatMap(i => ZIO.logDebug(s"$path cost ${i._1.getNano() / 1000 / 1000} ms") *> ZIO.succeed(i._2))
  }

  def getBalance(ccy: String) = {
    val path = s"api/v5/account/balance"
    request[Unit, Seq[Okx.RestResponse.Balance]](
      http.Method.GET,
      path,
      Map("ccy" -> ccy.toUpperCase()),
      None
    ).map(res => Trader.Balance(ccy, res.head.details.head.availBal.toDouble))
  }

  override def klineStream(interval: String) = {
    val wssUrl = "wss://ws.okx.com:8443/ws/v5/business"
    publicStream[Seq[String], Trader.Kline](
      wssUrl,
      f"candle$interval",
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

  case class OkOrderBook(ts: Long, asks: Seq[Seq[String]], bids: Seq[Seq[String]]) derives Schema

  override def orderbookStream(depth: Int): ZStream[Any, Throwable, Trader.OrderBook] = {
    val wssUrl = "wss://ws.okx.com:8443/ws/v5/public"
    val channel = if (depth == 1) {
      "bbo-tbt"
    } else {
      throw Exception(s"not supported orderbook depth $depth")
    }
    publicStream[OkOrderBook, Trader.OrderBook](
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
            ok.ts,
            bids,
            asks
          )
        )
      }
    )

  }

  def publicStream[OK: Schema, OUT: Schema](
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
                  exchangeSymbol
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

  case class SetLeverageReq(
    instId: String,
    lever: String,
    mgnMode: String
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
