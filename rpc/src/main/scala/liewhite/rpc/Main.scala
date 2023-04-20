package liewhite.rpc

import zio.*
import com.rabbitmq.client.Delivery
import java.time.ZonedDateTime
import liewhite.json.*

import liewhite.rpc.XX
case class XX(a: Int, b: Option[Boolean] = None) derives Schema

object App extends ZIOAppDefault {
  val endpoint  = Endpoint[XX, String]("jqk")
  val endpoint2  = Endpoint[XX, String]("jqk2")
  val broadcast = Broadcast[Int]("broadcast")

  val url = "amqp://guest:guest@localhost:5672"
  def run = {
    val x = ZIO.scoped(for {
      client <- ZIO.service[RpcClient]
      _      <- endpoint.listen(i => ZIO.logInfo(i.toString()) *> ZIO.succeed("x"))
      _      <- endpoint2.listen(i => ZIO.logInfo(i.toString()) *> ZIO.succeed("x"))
      // _ <- broadcast.subscribe(
      //   "i-o",
      //   i => {
      //     ZIO.debug("xxxx" + i.toString())
      //   }
      // )
      // _ <- broadcast.subscribe(
      //   "i-o",
      //   i => {
      //     ZIO.debug("yyyy" + i.toString())
      //   }
      // )
      // result <- ZIO.foreachPar(1 to 100)(i => broadcast.broadcast(i)).debug
      // _ <- endpoint.send(1)
      // doc <- client.call("jqk.doc", "".getBytes())
      // _   <- Console.printLine(new String(doc))
      res <- client.call("jqk", XX(1).toJson.toArray).flatMap(i => ZIO.logInfo("response:" + String(i)))
      _ <- endpoint2
             .call(XX(1)).debug("response: ")
      //   .catchAll(e => ZIO.succeed(e.toString()))
      //   .debug("response222: ")
      //   .forever

      // _ <- ZIO.never
      _ <- ZIO.sleep(2000.second)
    } yield ())
    val layers = RpcClient.layer ++ RpcServer.layer
    x.provide(Transport.layer(url), layers)
  }
}
