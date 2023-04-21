package liewhite.rpc

import zio.*
import com.rabbitmq.client.Delivery
import java.time.ZonedDateTime
import liewhite.json.*

import liewhite.rpc.XX
case class XX(a: Int, b: Option[Boolean] = None) derives Schema

object App extends ZIOAppDefault {
  val endpoint  = Endpoint[XX, String]("jqk")
  val endpoint2 = Endpoint[XX, String]("jqk2")
  val broadcast = Broadcast[Int]("broadcast")
  val broadcast2 = Broadcast[Int]("broadcast2")

  val url = "amqp://guest:guest@localhost:5672"
  def run = {
    val x = ZIO.scoped(for {
      client <- ZIO.service[RpcClient]
      _      <- endpoint.listen(i => ZIO.logInfo(i.toString()) *> ZIO.succeed("x"))
      _      <- endpoint2.listen(i => ZIO.logInfo(i.toString()) *> ZIO.succeed("x"))
      _ <- broadcast.subscribe(
        "i-o",
        i => {
          ZIO.debug("xxxx" + i.toString())
        }
      )
      _ <- broadcast.subscribe(
        "i-o",
        i => {
          ZIO.debug("yyyy" + i.toString())
        }
      )
      _ <- broadcast.broadcast(1).forever.fork
      // result <- ZIO.foreachPar(1 to 100)(i => broadcast.broadcast(i)).debug
      // _ <- endpoint.send(1)
      // doc <- client.call("jqk.doc", "".getBytes())
      // _   <- Console.printLine(new String(doc))
      // res <- endpoint2
      //          .call(XX(1))
      //          .debug("response 2: ")
      //          .forever
      //          .fork

      // _ <- endpoint
      //        .call(XX(1), timeout = 30.second)
      //        .debug("response: ")
      //        .forever
      //        .fork
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
