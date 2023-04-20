package liewhite.rpc

import zio.*
import com.rabbitmq.client.Delivery
import java.time.ZonedDateTime
import zio.json.*
import zio.schema.*

case class XX(
    a: Int,
    b: Option[Boolean])
    derives JsonDecoder,
      JsonEncoder,
      Schema

object App extends ZIOAppDefault {
  val endpoint  = Endpoint[XX, String]("jqk")
  val broadcast = Broadcast[Int]("broadcast")

  val url = "amqp://guest:guest@localhost:5672"
  def run = {
    val x = (for {
      client <- ZIO.service[RpcClient]
      _   <- endpoint.listen(i => ZIO.logInfo(i.toString()) *> ZIO.succeed("x"))
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
      doc <- client.call("jqk.doc", "".getBytes())
      _   <- Console.printLine(new String(doc))
      // res <- client.call("jqka", "xxx".getBytes()).debug
      // _ <- endpoint
      //   .call(123)
      //   .catchAll(e => ZIO.succeed(e.toString()))
      //   .debug("response222: ")
      //   .forever

      // _ <- ZIO.sleep(200.second)
    } yield ())
      .provide(Transport.layer(url), RpcClient.layer, RpcServer.layer)
    x
  }
}
