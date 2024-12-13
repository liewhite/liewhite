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
  def tickInterval: Duration
}

class LiveSingleEngine(okx: Trader, strategy: SingleTokenStrategy) {
  val token  = strategy.token
  val symbol = okx.token2Symbol(token)
  val state  = State(symbol, mutable.HashMap.empty, 0.0, Trader.Depth(), 0.0)

  // 定时同步挂单和position
  def syncAccount(state: State): Task[Unit] =
    for {
      orders   <- okx.getOpenOrders(Some(symbol))
      position <- okx.getSymbolPosition(symbol, Trader.MarginMode.Cross)
    } yield {
      orders.foreach(o => state.orders.addOne(o.orderId, o))
      state.position = position.map(_.size).getOrElse(0.0)
    }

  def run(): Task[Unit] = {
    val orderStream     = okx.orderStream(symbol).map(Event.Order(_))
    val aggTradeStream  = okx.aggTradeStream(symbol).map(Event.AggTrade(_))
    val positionStream  = okx.positionStream(symbol).map(Event.Position(_))
    val tickerStream    = ZStream.tick(strategy.tickInterval).map(_ => Event.Clock(ZonedDateTime.now()))
    val orderbookStream = okx.orderbookStream(symbol).map(Event.OrderBook(_))

    for {
      symbolsInfo <- okx.symbolsInfo()
      symbolInfo   = symbolsInfo.find(_.symbol == symbol).get
      _           <- syncAccount(state)
      _ <- {
        orderStream
          .mergeHaltEither(aggTradeStream)
          .mergeHaltEither(orderbookStream)
          .mergeHaltEither(positionStream)
          .mergeHaltEither(tickerStream)
          .mapZIO { event =>
            (event match {
              // 更新状态, 比如订单本，持仓，挂单
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
                ZIO.unit
              }
              case Event.Position(position) => {
                state.position = position.size
                ZIO.unit
              }
              case Event.OrderBook(orderbook) => {
                state.midPrice = (orderbook.bids.lastKey + orderbook.asks.firstKey) / 2
                state.depth = orderbook
                ZIO.unit
              }
              case Event.AggTrade(trade) => {
                ZIO.unit
              }
              case Event.Clock(c) => {
                // 每10秒强制同步一次挂单和持仓
                ZIO.when(c.toEpochSecond() % 10 == 0) {
                  syncAccount(state)
                }
              }
            }) *>
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
