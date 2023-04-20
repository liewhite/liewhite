package liewhite.rpc

import com.rabbitmq.client.AMQP
import zio.*
import zio.json.*
import com.rabbitmq.client.Delivery

import liewhite.rpc.RpcClient
import liewhite.rpc.{RpcFailure, RpcServer}
import zio.schema.Schema

class Broadcast[IN: JsonEncoder: JsonDecoder: Schema](
    route: String) {
  def subscribe(
      queueName: String,
      callback: IN => Task[Unit]
    ): RIO[RpcServer, Unit] = {
    for {
      server <- ZIO.service[RpcServer]
      _      <- server.listen(
        route,
        req => {
          for {
            body <- ZIO
              .fromEither(String(req.getBody()).fromJson[IN])
              .mapError(err => RpcFailure(400, 0, err))
            _    <- callback(body)
            ser = Array.emptyByteArray.toJson.getBytes()
          } yield ser
        },
        Some(queueName)
      )
      _      <- server.listen(
        route + ".doc",
        _ => {
          val in = summon[Schema[IN]]
          ZIO.succeed(
            s"""|IN:
             |${in.ast}
             |""".stripMargin.getBytes()
          )
        }
      )
    } yield ()
  }

  def broadcast(req: IN): ZIO[RpcClient, Throwable, Unit] = {
    for {
      client <- ZIO.service[RpcClient]
      res    <- client.send(route, req.toJson.getBytes)
    } yield res
  }

}
