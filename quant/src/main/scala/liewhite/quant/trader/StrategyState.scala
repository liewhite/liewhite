package liewhite.quant.trader

import zio.*

class StrategyState(
  val positions: Ref[Chunk[Trader.Position]],
  val orders: Ref[Chunk[Trader.Order]],
  val midPrice: Ref[Double],
  val offset: Ref[Double],
  val volatility: Ref[Double]
)