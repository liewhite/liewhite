package liewhite.rpc

import zio.*
import com.rabbitmq.client.Delivery
import java.time.ZonedDateTime
import liewhite.json.*

import liewhite.rpc.XX
case class XX(a: Int, b: Option[Boolean] = None) derives Schema

object App extends ZIOAppDefault {
  val endpoint        = Endpoint[Unit, String]("jqk")
  val norouteEndpoint = Endpoint[Unit, String]("jqk111")
  val endpoint2       = Endpoint[XX, String]("jqk2")
  val broadcast       = Broadcast[Int]("broadcast")
  val broadcast2      = Broadcast[Int]("broadcast2")

  val serverUrl = "amqp://guest:guest@localhost:5672"
  val clientUrl = "amqp://guest:guest@localhost:5673"

  def run = {
    val server = (for {
      _ <- endpoint.listen(i => ZIO.logInfo(s"$i") *> ZIO.succeed("oooook"))
      _ <- endpoint2.listen(i => ZIO.logInfo(i.toString()) *> ZIO.succeed("x2"))
      _ <- broadcast.subscribe(
             "i-o",
             i => {
               ZIO.logInfo("xxxx" + i.toString())
             }
           )
      _ <- broadcast.subscribe(
             "i-o2",
             i => {
               ZIO.logInfo("yyyy" + i.toString())
             }
           )
    } yield ())
      .provideSomeLayer(Transport.layer(serverUrl) >>> RpcServer.layer)

    val client = (for {
      client <- ZIO.service[RpcClient]
      // _      <- broadcast.broadcast(1).schedule(Schedule.fixed(1.second)).fork
      _ <- norouteEndpoint
             .call(XX(1), timeout = 30.second)
             .catchAllCause(e => ZIO.succeed(e.toString()))
             .debug("response1: ")
             .schedule(Schedule.fixed(1.second))
             .fork
      // _ <- endpoint
      //        .call(XX(1), timeout = 30.second)
      //        .catchAllCause(e => ZIO.succeed(e.toString()))
      //        .debug("response1: ")
      //        .schedule(Schedule.fixed(1.second))
      //        .fork
      // _ <- endpoint2
      //        .call(XX(1), timeout = 30.second)
      //        .debug("response: ")
      //        .schedule(Schedule.fixed(1.second))
      //        .fork
    } yield ())
      .provideSomeLayer(Transport.layer(clientUrl) >>> RpcClient.layer)

    ZIO.scoped(for {
      s <- server
      c <- client
      _ <- ZIO.never
    } yield ())
  }
}
