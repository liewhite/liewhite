package liewhite.rpc

import com.rabbitmq.client.AMQP
import zio.*
import com.rabbitmq.client.Delivery

import liewhite.json.*
import liewhite.rpc.RpcClient
import liewhite.rpc.{RpcFailure, RpcServer}

class Endpoint[
  IN: Schema,
  OUT: Schema
](route: String) {
  def listen(
    callback: IN => Task[OUT]
  ) =
    for {
      server <- ZIO.service[RpcServer]
      _ <- server.listen(
             route,
             req => {
               for {
                 body <- ZIO
                           .fromEither(String(req.getBody()).fromJson[IN])
                           .mapError(err => RpcFailure(400, 0, err.toString()))
                 res <- callback(body)
                 ser  = res.toJson.toArray
               } yield ser
             }
           )
      _ <- server.listen(
             route + ".doc",
             _ => {
               val in  = summon[Schema[IN]]
               val out = summon[Schema[OUT]]
               ZIO.succeed(
                 s"""|IN:
                     |${in.ast}
                     |
                     |OUT:
                     |${out.ast}
                     |""".stripMargin.getBytes()
               )
             }
           )
    } yield ()

  def call(req: IN, timeout: Duration = 30.second): ZIO[RpcClient, RpcFailure, OUT] =
    for {
      client <- ZIO.service[RpcClient]
      res <- client
               .call(route, req.toJson.toArray, timeout = timeout)
               .mapError(e => RpcFailure(500, 1, s"failed send request : $e"))
      out <- ZIO
               .fromEither(new String(res).fromJson[OUT]) // 尝试decode
               .mapError { e =>
                 new String(res)
                   .fromJson[RpcFailure]
                   .toOption
                   .getOrElse(RpcFailure(500, 0, e.toString)) // 按错误进行decode
               }
    } yield out

  def send(req: IN): ZIO[RpcClient, RpcFailure, Unit] =
    for {
      client <- ZIO.service[RpcClient]
      _ <- client
             .send(route, req.toJson.toArray)
             .mapError(e => RpcFailure(500, 1, s"failed send request : $e"))
    } yield ()
}
