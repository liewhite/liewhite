package liewhite.quant.engine
// import scala.collection.mutable
import java.util.TreeMap

class Depth {
  val bids  = new TreeMap[Double, Double]
  val asks  = new TreeMap[Double, Double]
  var ts    = 0L
  var preId = 0L
  var seqId = 0L

  def reset() = {
    bids.clear()
    asks.clear()
    ts = 0
  }

  def overrideBook(src: Seq[Seq[Double]], dst: TreeMap[Double, Double]) =
    src.foreach { kv =>
      if (kv(1) == 0) {
        dst.remove(kv(0))
      } else {
        dst.put(kv(0), kv(1))
      }
    }

  def applyUpdate(d: DepthUpdate) =
    if (d.isSnapshot) {
      reset()
    }
    overrideBook(d.asks, asks)
    overrideBook(d.bids, bids)
    ts = d.ts
}

case class DepthUpdate(
  ts: Long,
  seqId: Long,
  isSnapshot: Boolean,
  bids: Seq[Seq[Double]],
  asks: Seq[Seq[Double]]
)
