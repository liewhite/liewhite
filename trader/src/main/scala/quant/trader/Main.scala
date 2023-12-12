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
    val okx = exchange.Okx("ETH", "USDT", "a5fc0cdc-f3b8-4fd5-9346-737f70c4c2fc", "8F8F728C0517C9264A1FC28A201D8EFA","YBuYXZcEljx7ZXg@", None)
    okx.getPosition().debug
  }
}
