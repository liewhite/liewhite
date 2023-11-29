package quant.trader.exchange


def withPrecision(v: Double, precision: Int): Double = {
    ((v / (Math.pow(10, precision))).toInt * (Math.pow(10, precision)))
}