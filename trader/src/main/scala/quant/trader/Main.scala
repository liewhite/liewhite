package quant.trader

import java.time.ZonedDateTime
import zio.*
import zio.http.Client
import zio.http.URL
import zio.stream.ZStream
import zio.stream.ZSink
import liewhite.json.{*, given}
import quant.trader.exchange.Okx
import quant.trader.Trader
import quant.trader.common.Utils
import java.net.InetSocketAddress

case class Tick(ts: Long)
case class MidPrice(ts: Long, price: Double)
case class Volatility(ts: Long, volatility: Double)
case class Offset(ts: Long, offset: Double)

// 挂单越少， 挂的越远
// 过去1秒成交越大， 挂的越远
// 距离基数来自于两边挂单的差距
object Main extends ZIOAppDefault {
  def run: ZIO[Any & (ZIOAppArgs & Scope), Any, Any] = {
    val okx = exchange.Okx("BTC","USDT","","","",None)
    okx.klineStream("1m").debug.runDrain
  }
  // def run: ZIO[Any & (ZIOAppArgs & Scope), Any, Any] =
  //   val local: Boolean = true
  //   val proxy          = new java.net.Proxy(java.net.Proxy.Type.HTTP, new InetSocketAddress("127.0.0.1", 6152))
  //   val m = (for {
  //     rpcClient <- ZIO.service[RpcClient]
  //     okx = Okx(
  //             "",
  //             "BTC",
  //             "USDT",
  //             "",
  //             "",
  //             "",
  //             rpcClient,
  //             Some(proxy)
  //           )
  //     fm = Subscribe(rpcClient)

  //     // 三个因子
  //     // 波动率
  //     volatilityFactor =
  //       fm.factor[Unit, (Long, Double)](
  //         "price-volatility",
  //         FactorConfig[Unit]((), "btcusdt", Exchange.Okx, windowSize = 5000),
  //         local
  //       ).map(item => Volatility(item._1, item._2))
  //     // .debug("vol: ")

  //     // 中间价
  //     midPriceFactor =
  //       fm.factor[Unit, (Long, Double)]("mid-price", FactorConfig[Unit]((), "btcusdt", Exchange.Okx), local)
  //         .map(item => MidPrice(item._1, item._2))
  //     // .debug("mp: ")

  //     // 订单本厚度偏移比例
  //     orderbookOffsetFactor = fm.factor[Json, (Long, Double)](
  //                               "orderbook-20bp-weighted-ratio",
  //                               FactorConfig[Json](Json("weight" -> Json.Num(0.95)), "btcusdt", Exchange.Okx),
  //                               local
  //                             ).map(item => Offset(item._1, item._2))
  //     // .debug("offset: ")

  //     positionStream = okx.positionStream()
  //     orderStream    = okx.orderStream()
  //     tickStream     = ZStream.tick(3.second).map(i => Tick(ZonedDateTime.now().toInstant().toEpochMilli()))

  //     eventStream =
  //       Utils.merge(positionStream, tickStream, midPriceFactor, orderbookOffsetFactor, volatilityFactor, orderStream)

  //     ps <- Ref.make(Chunk.empty[Trader.Position])
  //     os <- Ref.make(Chunk.empty[Trader.Order])

  //     offset     <- Ref.make(0.0)
  //     midPrice   <- Ref.make(0.0)
  //     volatility <- Ref.make(0.0)

  //     _ <- eventStream.runFoldZIO(StrategyState(ps, os, midPrice, offset, volatility)) { (state, event) =>
  //            event match {
  //              case Tick(_) => {
  //                (for {
  //                  mp <- state.midPrice.get
  //                  of <- state.offset.get
  //                  vo <- state.volatility.get
  //                } yield {
  //                  okx
  //                    .getOpenOrders()
  //                    .map(orders => orders.map(_.orderId))
  //                    .flatMap { orderIds =>
  //                      ZIO.when(orderIds.nonEmpty) {
  //                        println(s"revoke all at ${ZonedDateTime.now()}")
  //                        okx.revokeOrders(
  //                          orderIds.map(id => Trader.BatchRevokeOrdersItem(Some(id), None))
  //                        )
  //                      }
  //                    } *>
  //                    (if (Seq(mp, of, vo).count(_ == 0) == 0) {
  //                       // 市场潜在价格 = 中间价 * (1 + 0.002 * offset )
  //                       // 上下挂单的价格为潜在价格 + 标准差, 减小偏移的影响，求平方根
  //                       val fairPrice = mp * scala.math.sqrt(1 + 0.002 * (of - 1))
  //                       val up        = fairPrice + vo
  //                       val down      = fairPrice - vo
  //                       ZIO.logInfo(s"up: $up, down: $down fair price: $fairPrice") *>
  //                         (ZIO.when(up > mp) {
  //                           okx.createOrder(
  //                             Trader.OrderAction.Sell,
  //                             Trader.OrderType.Limit(up, Trader.LimitOrderFlag.MakerOnly),
  //                             1,
  //                             None,
  //                             Trader.MarginMode.Cross
  //                           )
  //                         } <&> ZIO.when(down < mp) {
  //                           okx.createOrder(
  //                             Trader.OrderAction.Buy,
  //                             Trader.OrderType.Limit(down, Trader.LimitOrderFlag.MakerOnly),
  //                             1,
  //                             None,
  //                             Trader.MarginMode.Cross
  //                           )
  //                         })
  //                     } else {
  //                       ZIO.unit
  //                     })
  //                    *> ZIO.succeed(state)
  //                }).flatten
  //              }
  //              case MidPrice(ts, v) => {
  //                state.midPrice.set(v) *> ZIO.succeed(state)
  //              }
  //              case Offset(ts, v) => {
  //                state.offset.set(v) *> ZIO.succeed(state)
  //              }
  //              case Volatility(ts, v) => {
  //                state.volatility.set(v) *> ZIO.succeed(state)
  //              }
  //              case p: Trader.Position => ZIO.logInfo(p.toJson.asString) *> ZIO.succeed(state)
  //              case o: Trader.Order    => ZIO.logInfo(o.toJson.asString) *> ZIO.succeed(state)
  //            }
  //          }
  //   } yield ())
  //     .provideSomeLayer(Client.default)
  //     .provideSomeLayer(Transport.layer("amqp://rpc:@172.18.1.6:5672/rpc") >>> RpcClient.layer)
  //   m
}
