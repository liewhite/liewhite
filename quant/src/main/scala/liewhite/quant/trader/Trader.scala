package liewhite.quant.trader

import zio.ZIOAppDefault
import zio.Scope
import zio.ZIO
import zio.ZIOAppArgs
import zio.stream.ZStream
import zio.stream.ZSink
import zio.Chunk
import zio.ZLayer
import java.time.ZonedDateTime
import zio.*
import liewhite.json.{*, given}
import liewhite.quant.trader.Trader.AggTrade

trait Trader {
  def token2Symbol(token: String): String
  // def symbolsInfo(): Task[Seq[Trader.SymbolInfo]]
  def symbolInfo(symbol: String): Task[Trader.SymbolInfo]

  def klines(symbol: String, interval: String, limit: Int): Task[Seq[Trader.Kline]]
  // 统一使用小写表示
  def getBalance(currency: String): Task[Trader.Balance]

  def getSymbolPosition(symbol: String, mgnMode: Trader.MarginMode): Task[Option[Trader.RestPosition]]

  def getPositions(mgnMode: Trader.MarginMode): Task[Seq[Trader.RestPosition]]

  def getDepth(symbol: String, depth: Int): Task[Trader.OrderBook]

  // 统一使用小写表示token, 对应交易所实现自行转换拼接为symbol
  def createOrder(
    symbol: String,
    action: Trader.OrderAction,
    orderType: Trader.OrderType,
    quantity: Double,
    clientOrderID: Option[String],
    marginMode: Trader.MarginMode
  ): Task[String] // 返回订单ID

  def revokeOrder(
    symbol: String,
    orderID: Option[String],
    clientOrderID: Option[String]
  ): Task[Unit]

  def revokeOrders(orders: Seq[Trader.BatchRevokeOrdersItem]): Task[Unit]
  def revokeAll(symbol: Option[String]): Task[Unit]

  def getOpenOrders(symbol: Option[String]): Task[Seq[Trader.Order]]

  def getOrder(
    symbol: String,
    orderID: Option[String],
    clientOrderID: Option[String]
  ): Task[Trader.Order]

  def orderStream(symbol: String): ZStream[Any, Throwable, Trader.Order]

  def positionStream(symbol: String): ZStream[Any, Throwable, Trader.Position]

  def klineStream(symbol: String, interval: String): ZStream[Any, Throwable, Trader.Kline]

  // 维护好的本地订单本
  def orderbookStream(symbol: String): ZStream[Any, Throwable, Trader.Depth]
  def aggTradeStream(symbol: String): ZStream[Any, Throwable, Trader.AggTrade]

  def start(): Task[Unit]

}

object Trader {
  enum OrderAction derives Schema {
    case Buy
    case Sell
  }
  object OrderAction {
    def parse(s: String) =
      if (s.equalsIgnoreCase("buy")) {
        Buy
      } else if (s.equalsIgnoreCase("sell")) {
        Sell
      } else {
        throw Exception(s"bad orderAction $s")
      }
  }
  enum PositionSide derives Schema {
    // 开平仓模式
    case Long
    case Short
    // 买卖模式
    case Net
  }

  enum OrderType derives Schema {
    case Limit(price: Double, flag: LimitOrderFlag) // 限价
    case Market()                                   // 市价, 成交为止
    override def toString(): String =
      this match
        case Limit(price, flag) => "LIMIT"
        case Market()           => "MARKET"

  }

  enum LimitOrderFlag derives Schema {
    case Gtc       // 限价,成交为止
    case Fok       // 限价， 无法全部成交就取消订单
    case Ioc       // 限价， 无法全部成交就取消剩余部分
    case MakerOnly // 限价， 只做maker
  }

  enum OrderState derives Schema {
    case Canceled
    case Submitted
    case PartialFilled
    case Filled
    case Rejected
    case Expired
    case Unknown
  }

  object OrderState {}
  case class SymbolInfo(
    symbol: String,
    settelCcy: String,    // 结算货币
    quantityStep: Double, // 买入数量精度, 币安按币的数量买入， ok按合约张数， 所以ok取1， 币安取stepSize
    priceStep: Double,    // 价格精度
    ctVal: Double,        // 面值, 比如ok一张合约代表0.1个ETH， 币安没有张的概念， 直接取1
    lotSz: Double
  ) derives Schema

  case class BatchRevokeOrdersItem(
    symbol: String,
    ordId: Option[String],
    clOrdId: Option[String]
  )
  case class AggTrade(
    symbol: String,
    ts: Long,
    px: Double,
    sz: Double,
    side: OrderAction
  ) derives Schema

  case class Order(
    symbol: String,
    orderId: String,
    orderClientId: String,
    side: OrderAction,
    avgPrice: Double, // 成交均价
    price: Double,    // 委托价格
    size: Double,
    filledQty: Double,
    state: OrderState,
    orderType: OrderType,
    fee: Double,
    createTime: Long,
    updateTime: Long
  ) derives Schema

  enum MarginMode derives Schema {
    case Isolated
    case Cross
  }

  case class RestPosition(
    symbol: String,
    marginMode: MarginMode,
    side: PositionSide,
    size: Double,
    avgPrice: Double,
    createTime: Long,
    updateTime: Long
  ) derives Schema

  case class Position(
    symbol: String,
    marginMode: MarginMode,
    side: PositionSide,
    size: Double,     // 平仓事件此处为空
    avgPrice: Double, // 平仓事件此处为空
    createTime: Long,
    updateTime: Long
  ) derives Schema

  case class Balance(currency: String, value: Double) derives Schema

  case class Kline(
    ts: Long,
    open: Double,
    low: Double,
    high: Double,
    close: Double,
    volume: Double,
    end: Boolean
  ) derives Schema

  case class OrderBook(
    ts: Long,
    preId: Long,
    id: Long,
    bids: Seq[Seq[Double]],
    asks: Seq[Seq[Double]]
  ) derives Schema

  import scala.collection.immutable.TreeMap
  class Depth {
    var bids  = TreeMap.empty[Double, Double]
    var asks  = TreeMap.empty[Double, Double]
    var ts    = 0L
    var preId = 0L
    var seqId = 0L

    def reset() = {
      bids = TreeMap.empty
      asks = TreeMap.empty
      ts = 0
    }

    def overrideBook(src: Seq[Seq[Double]], dst: TreeMap[Double, Double]): TreeMap[Double, Double] =
      src.foldLeft(dst) { (result, item) =>
        if (item(1) == 0) {
          result.removed(item(0))
        } else {
          result.updated(item(0), item(1))
        }
      }

    def applyUpdate(d: DepthUpdate, isSnapshot: Boolean) =
      if (isSnapshot) {
        reset()
      }
      asks = overrideBook(d.asks, asks)
      bids = overrideBook(d.bids, bids)
      ts = d.ts
  }

  case class DepthUpdate(
    ts: Long,
    bids: Seq[Seq[Double]],
    asks: Seq[Seq[Double]]
  )

}
