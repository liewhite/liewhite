package liewhite.quant.common


def align(a: Double, step: Double): Double =
  BigDecimal((a / step).round * step).setScale(BigDecimal(step).scale, BigDecimal.RoundingMode.HALF_UP).toDouble
