package liewhite.rpc

import com.rabbitmq.client.AMQP
import zio.*
import zio.json.*
import com.rabbitmq.client.Delivery

import liewhite.rpc.RpcClient
import liewhite.rpc.{RpcFailure, RpcServer}
class Endpoint[IN: JsonEncoder: JsonDecoder, OUT: JsonEncoder: JsonDecoder](
    route: String
) {
  def listen(
      callback: IN => Task[OUT]
  ): RIO[RpcServer, Unit] = {
    for {
      server <- ZIO.service[RpcServer]
      _ <- server.listen(
        route,
        req => {
          for {
            body <- ZIO
              .fromEither(String(req.getBody()).fromJson[IN])
              .mapError(err => RpcFailure(400, 0, err))
            res <- callback(body)
            ser = res.toJson.getBytes()
          } yield ser
        }
      )
    } yield ()
  }

  def call(req: IN): ZIO[RpcClient, RpcFailure, OUT] = {
    for {
      client <- ZIO.service[RpcClient]
      res <- client
        .call(route, req.toJson.getBytes)
        .mapError(e => {
          RpcFailure(500, 1, s"failed send request : $e")
        })
      out <- ZIO
        .fromEither(new String(res).fromJson[OUT]) // 尝试decode
        .mapError(e => {
          new String(res)
            .fromJson[RpcFailure]
            .toOption
            .getOrElse(RpcFailure(500, 0, e)) // 按错误进行decode
        })
    } yield out
  }

}
