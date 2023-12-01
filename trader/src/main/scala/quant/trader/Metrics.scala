package quant.trader
import zio.stream.*
import liewhite.json.{*, given}

object Metrics {
  case class Kdj(ts: Long, rsv: Float, k: Float, d: Float, j: Float, end: Boolean) derives Schema {
    def next(ks: Seq[Trader.Kline]) = {
      val headK = ks.last

      val low9  = ks.map(_.low).min
      val high9 = ks.map(_.high).max
      val rsv   = (headK.close - low9) / (high9 - low9) * 100
      val newK  = k * 2 / 3 + rsv / 3
      val newD  = d * 2 / 3 + newK / 3
      val newJ  = 3 * newK - 2 * newD
      Kdj(headK.ts, rsv, newK, newD, newJ, headK.end)
    }
  }
  object Kdj {
    def newKdj(klines: Seq[Trader.Kline]): Kdj = {
      val k     = klines.last
      val low9  = klines.map(_.low).min
      val high9 = klines.map(_.high).max
      val rsv   = (k.close - low9) / (high9 - low9) * 100
      val newK  = 50 * 2 / 3 + rsv / 3
      val newD  = 50 * 2 / 3 + newK / 3
      val newJ  = 3 * newK - 2 * newD
      Kdj(k.ts, rsv, newK, newD, newJ, k.end)
    }
  }

  def kdj(klines: ZStream[Any, Throwable, Trader.Kline]): ZStream[Any, Throwable, Kdj] =
    // 获取最近九根K线
    // 暂存最近9根end kline和最近一根end的kdj
    klines
      .mapAccum((None: Option[Kdj], Seq.empty[Trader.Kline])) { (acc, kline) =>
        val lastKs = acc._2
        val len    = lastKs.length
        if (len < 9) {
          // 先攒到9根K线
          val newKs = if (kline.end) {
            lastKs.appended(kline)
          } else {
            lastKs
          }
          ((None, newKs), None)
        } else {
          // 已经有9根K线了， 则取最新9根
          val ksWithCurrent = lastKs.appended(kline)
          val last9K        = ksWithCurrent.takeRight(9)

          val nextKdj = acc._1 match {
            case None =>
              Kdj.newKdj(last9K)
            case Some(lastKdj) => {
              lastKdj.next(last9K)
            }
          }
          if (kline.end) {
            ((Some(nextKdj), last9K), Some(nextKdj))
          } else {
            // 如果未收盘， 则保持lastkdj和klines不动
            (acc, Some(nextKdj))
          }
        }
      }
      .collectSome

  case class Macd(ts: Long, ema12: Float, ema26: Float, diff: Float, dea: Float, bar: Float, end: Boolean)
      derives Schema {
    def next(k: Trader.Kline) = {
      val short  = 12
      val long   = 26
      val mid    = 9
      val price  = k.close
      val e12    = ema12 * (short - 1) / (short + 1) + price * 2 / (short + 1)
      val e26    = ema26 * (long - 1) / (long + 1) + price * 2 / (long + 1)
      val newDif = e12 - e26
      val newDea = this.dea * (mid - 1) / (mid + 1) + newDif * 2 / (mid + 1)
      val b      = (newDif - newDea) * 2
      Macd(k.ts, e12, e26, newDif, newDea, b, k.end)
    }
  }
  object Macd {
    def newMacd(k: Trader.Kline): Macd =
      Macd(k.ts, k.close, k.close, 0, 0, 0, k.end)
  }

  def macd(klines: ZStream[Any, Throwable, Trader.Kline]): ZStream[Any, Throwable, Macd] =
    // 获取最近九根K线
    // 暂存最近9根end kline和最近一根end的kdj
    val init: Option[Macd] = None
    klines
      .mapAccum(init) ( (acc, kline) =>
        // val nextMacd = Macd(1,1,1,1,1,1,true)
        val nextMacd = acc match {
          case None =>
            Macd.newMacd(kline)
          case Some(lastMacd) => {
            lastMacd.next(kline)
          }
        }
        if (kline.end) {
          (Some(nextMacd), nextMacd)
        } else {
          (acc, nextMacd)
        }
      )
}
