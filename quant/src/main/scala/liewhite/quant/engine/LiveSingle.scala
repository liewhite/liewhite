package liewhite.quant.engine

import liewhite.quant.trader.Trader
import liewhite.quant.engine.*
import zio.*
import scala.collection.mutable
import zio.stream.ZStream
import java.time.ZonedDateTime
import liewhite.quant.common.*

trait SingleTokenStrategy {
  // 基于event作出决策
  def onEvent(event: Event, state: State): Chunk[Action]
  def token: String
}

class LiveSingleEngine(okx: Trader, strategy: SingleTokenStrategy) {
  val token  = strategy.token
  val symbol = okx.token2Symbol(token)
  val state  = State(symbol, mutable.HashMap.empty, 0.0, 0.0)

  def run(): Task[Unit] = {
    val orderStream     = okx.orderStream(symbol).map(Event.Order(_))
    val aggTradeStream  = okx.aggTradeStream(symbol).map(Event.AggTrade(_))
    val orderbookStream = okx.orderbookStream(symbol).map(Event.OrderBook(_))
    val positionStream  = okx.positionStream(symbol).map(Event.Position(_))
    val tickerStream    = ZStream.tick(1.second).map(_ => Event.Clock(ZonedDateTime.now()))

    for {
      symbolInfo <- okx.symbolInfo(symbol)
      orders     <- okx.getOpenOrders(Some(symbol))
      position   <- okx.getSymbolPosition(symbol, Trader.MarginMode.Cross)
      _ <- {
        orders.foreach(o => state.orders.addOne(o.orderId, o))
        state.position = position.map(_.size).getOrElse(0.0)
        orderStream
          .mergeHaltEither(aggTradeStream)
          .mergeHaltEither(orderbookStream)
          .mergeHaltEither(positionStream)
          .mergeHaltEither(tickerStream)
          .mapZIO { event =>
            // 更新状态, 比如订单本，持仓，挂单
            event match {
              case Event.Order(order) => {
                state.orders.update(order.orderId, order)
                state.orders.filterInPlace { (_, order) =>
                  !Seq(
                    Trader.OrderState.Filled,
                    Trader.OrderState.Canceled,
                    Trader.OrderState.Expired,
                    Trader.OrderState.Rejected
                  ).contains(order.state)
                }
              }
              case Event.Position(position)   => state.position = position.size
              case Event.OrderBook(orderbook) => state.midPrice = (orderbook.bids.head(0) + orderbook.asks.head(0)) / 2
              case Event.AggTrade(trade)      => {}
              case _                          => {}
            }
            ZIO.attempt(strategy.onEvent(event, state))
          }
          .mapZIO { actions =>
            // 执行action, 只有挂单撤单两个操作
            val actionZIOs = actions.map { action =>
              action match {
                case o @ Action.CreateOrder(symbol, action, orderType, quantity) => {
                  // 精度对齐
                  val alignedOrderType = orderType match {
                    case Trader.OrderType.Limit(price, flag) =>
                      Trader.OrderType.Limit(align(price, symbolInfo.priceStep), flag)
                    case o => o
                  }
                  okx
                    .createOrder(
                      symbol,
                      action,
                      orderType,
                      quantity,
                      None,
                      Trader.MarginMode.Cross
                    )
                    .fork
                }
                case c @ Action.CancelOrder(orderId) => {
                  ZIO.logInfo(f"cancel order $c") *>
                    okx.revokeOrder(symbol, Some(orderId), None).fork
                }
              }
            }
            ZIO.when(!actionZIOs.isEmpty)(ZIO.collectAll(actionZIOs))
          }
          .runDrain
      }
    } yield ()

  }
}