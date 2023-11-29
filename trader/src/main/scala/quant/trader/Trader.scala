package quant.trader

import zio.ZIOAppDefault
import zio.Scope
import zio.ZIO
import zio.ZIOAppArgs
import zio.stream.ZStream
import zio.stream.ZSink
import zio.Chunk
import quant.trader.common.Utils
import zio.ZLayer
import java.time.ZonedDateTime
import zio.*
import liewhite.json.{*, given}

trait Trader {
  // 统一使用小写表示
  def getBalance(currency: String): Task[Trader.Balance]

  // 统一使用小写表示token, 对应交易所实现自行转换拼接为symbol
  def createOrder(
    action: Trader.OrderAction,
    orderType: Trader.OrderType,
    quantity: Double,
    clientOrderID: Option[String],
    marginMode: Trader.MarginMode
  ): Task[String] // 返回订单ID

  def revokeOrder(
    orderID: Option[String],
    clientOrderID: Option[String]
  ): Task[Unit]

  def revokeOrders(orders: Seq[Trader.BatchRevokeOrdersItem]): Task[Unit]

  def getOpenOrders(): Task[Seq[Trader.Order]]

  def getOrder(
    orderID: Option[String],
    clientOrderID: Option[String]
  ): Task[Trader.Order]

  def orderStream(
  ): ZStream[Any, Throwable, Trader.Order]

  def positionStream(): ZStream[Any, Throwable, Trader.Position]

  def klineStream(interval: String): ZStream[Any, Throwable, Trader.Kline]

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

  case class BatchRevokeOrdersItem(
    ordId: Option[String],
    clOrdId: Option[String]
  )
  case class OkxBatchRevokeOrdersItem(
    
    ordId: Option[String],
    clOrdId: Option[String]
  )
  case class Order(
    orderId: String,
    orderClientId: String,
    side: OrderAction,
    avgPrice: Double, // 成交均价
    price: Double, // 委托价格
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

  case class Position(
    marginMode: MarginMode,
    side: PositionSide,
    size: Option[Double],     // 平仓事件此处为空
    avgPrice: Option[Double], // 平仓事件此处为空
    createTime: Long,
    updateTime: Long
  ) derives Schema

  case class Balance(currency: String, value: Double) derives Schema

  case class Kline(
      ts: Long,
      open: Float,
      low: Float,
      high: Float,
      close: Float,
      volume: Float,
      end: Boolean
  ) derives Schema

}