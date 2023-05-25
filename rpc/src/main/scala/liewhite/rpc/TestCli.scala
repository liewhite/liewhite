package liewhite.rpc

import zio.*
import liewhite.json.*
import com.rabbitmq.client.Delivery
import java.time.ZonedDateTime

object TestCli extends ZIOAppDefault {
  val endpoint  = Endpoint[XX, String]("jqk")
  val broadcast = Broadcast[Int]("broadcast")

  val url = "amqp://guest:guest@localhost:5672"

  def run = {
    ???
    // val x = (for {
    //   client <- ZIO.service[RpcClient]
    //   res    <- client.call("jqk", XX(1).toJson.toArray).flatMap(i => ZIO.logInfo(String(i)))
    //   // _      <- ZIO.sleep(2000.second)
    // } yield ())
    //   .provide(Transport.layer(url), RpcClient.layer)
    // x
  }
}
