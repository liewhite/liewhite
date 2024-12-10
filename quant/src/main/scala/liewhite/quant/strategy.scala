package liewhite.quant

import liewhite.quant.trader.Trader
import zio.*
import liewhite.quant.trader.exchange.Okx
import scala.collection.mutable
import zio.stream.ZStream
import java.time.ZonedDateTime
import liewhite.quant.engine.*

def findMaxIncrease(nums: Seq[Double], sign: Double): Double = {
  var maxDiff      = 0.0
  var minValue     = nums(0)
  var minIdx       = 0
  var resultMinIdx = 0
  var resultMaxIdx = 0

  for (i <- 1 until nums.length) {
    val currentDiff = nums(i) - minValue

    if ((currentDiff - maxDiff) * sign > 0) {
      maxDiff = currentDiff
      resultMinIdx = minIdx
      resultMaxIdx = i
    }

    if ((nums(i) - minValue) * sign < 0) {
      minValue = nums(i)
      minIdx = i
    }
  }
  maxDiff
}

class Strategy() extends SingleTokenStrategy {
  val orderSize   = 1             // 0.1张合约
  val maxPosition = orderSize * 3 // 3张合约
  val queue       = mutable.Queue[Trader.AggTrade]()

  def tickInterval: Duration = 1.second
  def token: String = "PUFFER"

  def process(event: Event, state: State): Chunk[Action] = {
    // 每10秒重新挂单
    val cancels = Chunk.fromIterable(state.orders.map(o => Action.CancelOrder(o._2.orderId)))
    // 过去10秒的最大上涨幅度和最大下跌幅度， 分别取 61.8%, 上下挂单
    val maxIncrease = findMaxIncrease(queue.map(_.px).toSeq, 1)
    val maxDecrease = findMaxIncrease(queue.map(_.px).toSeq, -1)
    val midPrice    = state.midPrice
    println(s"maxIncrease: $maxIncrease maxDecrease: $maxDecrease midPrice: $midPrice")
    println(s"position: ${state.position} orders: ${state.orders}")
    val buyOrder = if (state.position >= maxPosition) {
      Chunk.empty
    } else {
      Chunk(
        Action.CreateOrder(
          state.symbol,
          Trader.OrderAction.Buy,
          Trader.OrderType.Limit(midPrice + maxDecrease * 0.618, Trader.LimitOrderFlag.MakerOnly),
          orderSize
        )
      )
    }
    val sellOrder = if (state.position <= -maxPosition) {
      Chunk.empty
    } else {
      Chunk(
        Action.CreateOrder(
          state.symbol,
          Trader.OrderAction.Sell,
          Trader.OrderType.Limit(midPrice + maxIncrease * 0.618, Trader.LimitOrderFlag.MakerOnly),
          orderSize
        )
      )
    }
    cancels ++ buyOrder ++ sellOrder

  }
  def onEvent(event: Event, state: State): Chunk[Action] =
    event match {
      case Event.OrderBook(order) => {
        Chunk.empty
      }
      // 计算上下波动距离均线的最大距离
      // 计算当前均线
      // 在当前均线基础上， 根据最大波动距离 * 0.618， 计算上下挂单价格
      // 不用考虑现价， 以均线为准
      case Event.AggTrade(trade) => {
        queue.enqueue(trade)
        val expireTs = trade.ts - 10000 // 10秒
        val removed  = queue.dequeueWhile(_.ts <= expireTs)
        Chunk.empty
      }
      case Event.Clock(t) => {
        if (queue.length < 10 || t.toEpochSecond() % 3 != 0) {
          Chunk.empty
        } else {
          process(event, state)
        }
      }
      case Event.Order(order) => {
        if (order.state == Trader.OrderState.Filled) {
          process(event, state)
        } else {
          Chunk.empty
        }
      }
      case e => {
        Chunk.empty
      }
    }
}

object Main extends ZIOAppDefault {
  def run =
    val okx = Okx("", "", "")
    okx.orderbookStream("BTC-USDT-SWAP").map(item =>{
      (item.asks.head(0)  + item.bids.head(0)) / 2
    }).debug.runDrain

  // val strategy = new Strategy()
  // val engine   = LiveSingleEngine(okx, strategy)
  // engine
  //   .run()
  //   .debug("end22 of ap11111p: ")
  //   .retry(Schedule.fixed(3.second))
}
