package liewhite.rpc

import zio.*
import com.rabbitmq.client.Delivery
import java.time.ZonedDateTime
import zio.json.*
import zio.schema.*

object TestCli extends ZIOAppDefault {
  val endpoint  = Endpoint[XX, String]("jqk")
  val broadcast = Broadcast[Int]("broadcast")

  val url = "amqp://guest:guest@localhost:5672"

  def run = {
    val x = (for {
      client <- ZIO.service[RpcClient]
      res    <- client.call("jqk", XX(1).toJson.getBytes()).flatMap(i => ZIO.logInfo(String(i)))
      // _      <- ZIO.sleep(2000.second)
    } yield ())
      .provide(Transport.layer(url), RpcClient.layer)
    x
  }
}
