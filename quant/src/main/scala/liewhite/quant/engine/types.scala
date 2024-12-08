package liewhite.quant.engine

import liewhite.quant.trader.Trader
import java.time.ZonedDateTime
import scala.collection.mutable


enum Event {
  case Order(order: Trader.Order)
  case AggTrade(trade: Trader.AggTrade)
  case Position(position: Trader.Position)
  case OrderBook(orderbook: Trader.Depth)
  case Clock(time: ZonedDateTime)
}

class State(
  val symbol: String,
  val orders: mutable.HashMap[String, Trader.Order],
  var position: Double,
  var depth: Trader.Depth,
  var midPrice: Double,
)
enum Action {
  case CreateOrder(
    symbol: String,
    action: Trader.OrderAction,
    orderType: Trader.OrderType,
    quantity: Double
  )
  case CancelOrder(orderId: String)
}