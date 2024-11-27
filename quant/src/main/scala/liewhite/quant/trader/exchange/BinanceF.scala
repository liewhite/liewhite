// package liewhite.quant.trader.exchange

// import liewhite.quant.trader.Trader
// import zio.*
// import zio.stream.ZStream
// import okhttp3.*
// import liewhite.json.{*, given}
// import java.time.ZonedDateTime
// import javax.crypto.*
// import org.apache.commons.codec.binary.Hex
// import zio.schema.codec.DecodeError
// import java.net.InetSocketAddress
// import java.util.concurrent.TimeUnit
// import scala.util.Try
// import liewhite.quant.trader.Trader.OrderType
// import liewhite.quant.trader.Trader.LimitOrderFlag
// import liewhite.quant.trader.Trader.Position
// import liewhite.quant.trader.Trader.Kline
// import liewhite.quant.trader.Trader.SymbolInfo
// import liewhite.quant.trader.Trader.RestPosition
// import liewhite.quant.trader.Trader.MarginMode
// import liewhite.quant.trader.Trader.OrderBook

// class BinanceF(
//   val apiKey: String,
//   val apiSecret: String,
//   val baseToken: String,
//   val quoteToken: String,
//   val precision: Int, // 精确到 e^precision, 比如-2代表0.01
//   val proxy: Option[java.net.Proxy] = None
// ) extends Trader {

//   def token2Symbol(token: String): String = {
//     f"${token}USDT".toUpperCase()
//   }

//   override def getPositions(mgnMode: MarginMode): Task[Seq[RestPosition]] = ???

//   override def klines(symbol: String, interval: String, limit: Int): Task[Seq[Kline]] = ???

//   override def getSymbolPosition(symbol: String, mgnMode: MarginMode): Task[Option[RestPosition]] = ???

//   override def getDepth(symbol: String, depth: Int): Task[OrderBook] = ???

//   override def revokeAll(symbol: Option[String]): Task[Unit] = ???

//   override def symbolsInfo(): Task[Seq[SymbolInfo]] = ???

//   val restUrl       = "https://fapi.binance.com"
//   val clientBuilder = okhttp3.OkHttpClient.Builder()
//   val client = (proxy match
//     case None        => clientBuilder
//     case Some(value) => clientBuilder.proxy(value)
//   ).build()

//   val wsClient =
//     okhttp3.OkHttpClient.Builder().readTimeout(30, TimeUnit.SECONDS).pingInterval(10, TimeUnit.SECONDS).build()

//   val factorSymbol = (baseToken + quoteToken).toLowerCase()

//   def symbolInfo(): Task[Trader.SymbolInfo] =
//     ???

//   def getPosition(mgnMode: Trader.MarginMode): Task[Option[Trader.RestPosition]] = ???
//   def klines(interval: String, limit: Int): Task[Seq[Trader.Kline]]              = ???

//   def flushListenKey(): Task[String] =
//     request[BinanceF.ListenKeyResponse](
//       http.Method.POST,
//       "fapi/v1/listenKey",
//       Seq.empty
//     ).map(_.listenKey)

//   def wsStream(): ZStream[Any, Throwable, String] =
//     ZStream
//       .fromZIO(flushListenKey())
//       .flatMap { listenKey =>
//         ZStream.asyncScoped[Any, Throwable, String] { cb =>
//           val req = new Request.Builder().url(s"wss://fstream.binance.com/ws/$listenKey").build()
//           val ws = wsClient.newWebSocket(
//             req,
//             new WebSocketListener {
//               override def onOpen(x: WebSocket, y: Response): Unit = {}

//               override def onMessage(s: WebSocket, x: String): Unit =
//                 cb(ZIO.succeed(Chunk(x)))

//               override def onClosing(x: WebSocket, y: Int, z: String): Unit =
//                 cb(
//                   ZIO.logWarning(
//                     s"orderbook market websocket closing:$y, $z"
//                   ) *> ZIO.fail(Some(Exception(z)))
//                 )

//               override def onFailure(
//                 s: WebSocket,
//                 e: Throwable,
//                 x: Response
//               ): Unit = {
//                 val result = ZIO.logWarning(e.toString()) *> ZIO.succeed(
//                   e.printStackTrace()
//                 ) *> ZIO.logInfo(
//                   "market websocket closed, reloading"
//                 ) *> ZIO.fail(Some(e))
//                 cb(result)
//               }
//             }
//           )
//           ZIO.acquireRelease(ZIO.succeed(ws))(s => ZIO.succeed(s.close(1001, "release")))
//         }
//       }

//   override def positionStream(symbol: String): ZStream[Any, Throwable, Trader.Position] =
//     wsStream()
//       .map(item => item.fromJson[BinanceF.AccountUpdatePush])
//       .filter(_.isRight)
//       .map(_.toOption.get)
//       .map { items =>
//         Chunk.fromIterable(items.a.P.map { item =>
//           Trader.Position(
//             symbol,
//             Trader.MarginMode.Isolated, // 应该不会用到
//             Trader.PositionSide.Net,    // 不支持双边开仓模式
//             item.pa.toDouble,
//             item.ep.toDouble,
//             items.T, // 币安position推送没有创建时间
//             items.T
//           )
//         })
//       }
//       .flattenChunks
//       .retry(Schedule.fixed(3.second))

//   override def orderStream(symbol: String): ZStream[Any, Throwable, Trader.Order] =
//     wsStream()
//       .map(item => item.fromJson[BinanceF.OrderPushItem])
//       .filter(_.isRight)
//       .map(_.toOption.get)
//       .map { item =>
//         parseOrder(
//           BinanceF.Order(
//             item.o.i,
//             "",
//             item.o.ap,
//             item.o.q,
//             item.o.z,
//             item.o.p,
//             item.o.S,
//             item.o.X,
//             item.o.s,
//             item.o.T,
//             item.o.f,
//             item.o.o,
//             item.o.T
//           )
//         )
//       }
//       .retry(Schedule.fixed(3.second))

//   override def klineStream(symbol: String, interval: String) =
//     ???

//   override def orderbookStream(symbol: String): ZStream[Any, Throwable, Trader.OrderBook] = ???

//   override def getOrder(symbol: String, orderID: Option[String], clientOrderID: Option[String]): Task[Trader.Order] = {
//     val path   = "fapi/v1/order"
//     val params = Seq(("symbol", symbol))
//     val paramsWithOid = orderID match
//       case None        => params
//       case Some(value) => params.appended(("orderId", value))
//     val paramsWithCid = orderID match
//       case None        => paramsWithOid
//       case Some(value) => paramsWithOid.appended(("origClientOrderId", value))

//     request[BinanceF.Order](http.Method.GET, path, paramsWithCid).map { order =>
//       parseOrder(order)
//     }
//   }

//   override def createOrder(
//     symbol: String,
//     action: Trader.OrderAction,
//     orderType: Trader.OrderType,
//     quantity: Double,
//     clientOrderID: Option[String] = None,
//     marginMode: Trader.MarginMode = Trader.MarginMode.Isolated
//   ): Task[String] = {
//     val path = "fapi/v1/order"
//     val tif = orderType match
//       case OrderType.Limit(price, flag) => flag.binanceTimeInForce
//       case OrderType.Market()           => "GTC"

//     val params = Seq(
//       ("symbol", symbol),
//       ("side", action.toString().toUpperCase()),
//       ("type", orderType.toString().toUpperCase()),
//       ("quantity", withPrecision(quantity, -3).toString()),
//       ("timeInForce", tif)
//     )
//     val paramsWithPrice = orderType match
//       case OrderType.Limit(price, flag) =>
//         params.appended(("price", price.toString()))
//       case OrderType.Market() => params

//     request[BinanceF.CreateOrderResponse](
//       http.Method.POST,
//       path,
//       paramsWithPrice
//     ).map(_.orderId.toString())
//   }

//   override def revokeOrders(orders: Seq[Trader.BatchRevokeOrdersItem]): Task[Unit] = {
//     val path = "fapi/v1/batchOrders"
//     val oids = orders.map(item => item.ordId.get.toLong)
//     request[Seq[Json]](
//       http.Method.DELETE,
//       path,
//       Seq(("orderIdList", oids.toJson.asString))
//     ).as(())
//   }

//   override def getBalance(currency: String): Task[Trader.Balance] = {
//     val path = "fapi/v2/balance"
//     request[Seq[BinanceF.BinanceBalance]](
//       http.Method.GET,
//       path,
//       Seq.empty
//     ).map(item =>
//       item
//         .find(_.asset.toLowerCase() == currency.toLowerCase())
//         .map(i => Trader.Balance(currency, i.availableBalance.toDouble))
//         .getOrElse(Trader.Balance(currency, 0))
//     )
//   }

//   override def revokeOrder(symbol: String, orderID: Option[String], clientOrderID: Option[String]): Task[Unit] = {
//     val path = "fapi/v1/order"
//     val params = Seq(("symbol", symbol))

//     request[Seq[Json]](
//       http.Method.DELETE,
//       path,
//       params,
//     ).as(())
//   }

//   override def start(): Task[Unit] =
//     flushListenKey() *>
//       (flushListenKey()
//         .debug("refresh listen key")
//         .schedule(Schedule.fixed(10.minute))
//         .retry(Schedule.fixed(3.second))
//         .fork
//         .as(()))
//   override def aggTradeStream(symbol: String): ZStream[Any, Throwable, Trader.AggTrade] = ???

//   override def getOpenOrders(symbol: Option[String]): Task[Seq[Trader.Order]] = {
//     val path = "fapi/v1/openOrders"
//     val params = symbol match {
//         case None => Seq.empty
//         case Some(value) => Seq(("symbol", value))
//     }
//     request[Seq[BinanceF.Order]](http.Method.GET, path, params).map { orders =>
//       orders.map(o => parseOrder(o))
//     }
//   }
//   def parseOrder(o: BinanceF.Order) = {
//     val status = o.status match {
//       case "NEW"              => Trader.OrderState.Submitted
//       case "CANCELED"         => Trader.OrderState.Canceled
//       case "PARTIALLY_FILLED" => Trader.OrderState.PartialFilled
//       case "FILLED"           => Trader.OrderState.Filled
//       case "EXPIRED"          => Trader.OrderState.Expired
//     }
//     val orderType = if (o.`type` == "LIMIT") {
//       val flag = o.timeInForce match
//         case "GTC" => Trader.LimitOrderFlag.Gtc
//         case "FOK" => Trader.LimitOrderFlag.Fok
//         case "IOC" => Trader.LimitOrderFlag.Ioc
//         case "GTX" => Trader.LimitOrderFlag.MakerOnly

//       Trader.OrderType.Limit(o.price.toDouble, flag)
//     } else if (o.`type` == "MARKET") {
//       Trader.OrderType.Market()
//     } else {
//       throw Exception(s"unknown order type ${o.`type`}")
//     }
//     Trader.Order(
//       o.symbol,
//       o.orderId.toString(),
//       o.clientOrderId,
//       Trader.OrderAction.parse(o.side),
//       o.avgPrice.toDouble,
//       o.price.toDouble,
//       o.origQty.toDouble,
//       o.executedQty.toDouble,
//       status,
//       orderType,
//       0,
//       o.time,
//       o.updateTime
//     )
//   }

//   def request[OUT: Schema](
//     method: http.Method,
//     path: String,
//     params: Seq[(String, String)]
//   ): ZIO[Any, Throwable, OUT] = {
//     val now                 = ZonedDateTime.now().toInstant.toEpochMilli
//     val paramsWithTimestamp = params.appended("timestamp", now.toString)
//     val paramsStr           = paramsWithTimestamp.map(i => s"${i._1}=${i._2}").mkString("&")

//     val uri = okhttp3.HttpUrl.parse(restUrl).newBuilder().addEncodedPathSegments(path)

//     val hmacSha256 = Mac.getInstance("HmacSHA256")
//     val secKey     = new spec.SecretKeySpec(apiSecret.getBytes(), "2")
//     hmacSha256.init(secKey)
//     val sign          = new String(Hex.encodeHex(hmacSha256.doFinal(paramsStr.getBytes())));
//     val paramsWithSig = paramsWithTimestamp.appended("signature", sign)

//     val uriWithParams =
//       paramsWithSig.foldLeft(uri)((acc, item) => acc.addQueryParameter(item._1, item._2)).build()

//     val urlReq = okhttp3.Request.Builder().url(uriWithParams).header("X-MBX-APIKEY", apiKey)

//     val body = if (method.name == "GET") {
//       null
//     } else {
//       RequestBody.create(Array.emptyByteArray)
//     }
//     val req = urlReq.method(method.name, body).build()
//     println(req)
//     (ZIO.attemptBlocking {
//       client.newCall(req).execute()
//     }.flatMap { res =>
//       if (!res.isSuccessful()) {
//         ZIO.fail(Exception(s"failed send request: ${res.code()} ${res.body().byteString().toString()}"))
//       } else {
//         ZIO.fromEither {
//           val body = String(res.body().bytes())
//           println("response" -> body)
//           body.fromJson[OUT].left.map(e => e.and(DecodeError.EmptyContent(body)))
//         }
//       }
//     }).timed
//       .flatMap(i => ZIO.logInfo(s"$path cost ${i._1.getNano() / 1000 / 1000} ms") *> ZIO.succeed(i._2))
//   }
// }

// object BinanceF {
//   case class Order(
//     orderId: Long,
//     clientOrderId: String,
//     avgPrice: String,
//     origQty: String,
//     executedQty: String,
//     price: String,
//     side: String,
//     status: String,
//     symbol: String,
//     time: Long,
//     timeInForce: String,
//     `type`: String,
//     updateTime: Long
//   ) derives Schema
//   case class CreateOrderResponse(
//     orderId: Long
//   ) derives Schema
//   case class ListenKeyResponse(
//     listenKey: String
//   ) derives Schema

//   case class OrderPushItem(
//     o: OrderPushItemO
//   ) derives Schema
//   case class OrderPushItemO(
//     i: Long,    // id
//     s: String,  // symbol
//     S: String,  // side
//     o: String,  // type
//     f: String,  // time in force
//     q: String,  // size
//     p: String,  // price
//     ap: String, // avg price
//     X: String,  // status
//     l: String,  // 末次成交量
//     z: String,  // 累计成交量
//     L: String,  // 末次成交价格
//     T: Long     // 成交时间
//   ) derives Schema

//   case class AccountUpdatePush(
//     T: Long,
//     a: AccountUpdatePushPosition
//   ) derives Schema
//   case class AccountUpdatePushPosition(
//     P: Seq[AccountUpdatePosition]
//   ) derives Schema
//   case class AccountUpdatePosition(
//     s: String,  // symbol
//     pa: String, // size
//     ep: String  // price
//   ) derives Schema
//   case class BinanceBalance(
//     asset: String, // token name
//     balance: String,
//     availableBalance: String
//   ) derives Schema
// }

// extension (flag: Trader.LimitOrderFlag) {
//   def binanceTimeInForce =
//     flag match
//       case LimitOrderFlag.Gtc       => "GTC"
//       case LimitOrderFlag.Fok       => "FOK"
//       case LimitOrderFlag.Ioc       => "IOC"
//       case LimitOrderFlag.MakerOnly => "GTX"

// }