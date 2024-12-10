package liewhite.quant.engine
// import scala.collection.mutable.TreeMap
import scala.collection.SortedMap
import scala.collection.immutable.TreeMap
// import java.util.TreeMap

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
        result.insert(item(0), item(1))
      }
    }

  def applyUpdate(d: DepthUpdate) =
    if (d.isSnapshot) {
      reset()
    }
    asks = overrideBook(d.asks, asks)
    bids = overrideBook(d.bids, bids)
    ts = d.ts
    println("apply depth")
}

case class DepthUpdate(
  ts: Long,
  seqId: Long,
  isSnapshot: Boolean,
  bids: Seq[Seq[Double]],
  asks: Seq[Seq[Double]]
)
