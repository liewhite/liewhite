package liewhite.ethers
import zio.*

import liewhite.common.zio.{*,given}


class ABIInt[S <: Int]()


@main def main = {
    val a = ZIO.attempt{
        println("123")
        throw Exception("xxxxx")
    } 
    a.force
}