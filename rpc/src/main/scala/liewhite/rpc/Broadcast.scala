package liewhite.rpc

import com.rabbitmq.client.AMQP
import zio.*
import liewhite.json.*
import com.rabbitmq.client.Delivery
import liewhite.rpc.*

class Broadcast[IN: Schema](val route: String) {
  def subscribe(
    queueName: String,
    callback: IN => Task[Unit]
  ) =
    for {
      server <- ZIO.service[RpcServer]
      _ <- server.listen(
             route,
             req => {
               for {
                 body <- ZIO
                           .fromEither(String(req.getBody()).fromJson[IN])
                           .mapError(err => EndpointException(400, 0, err.toString()))
                 _ <- ZIO
                        .attemptBlocking(callback(body)).flatten
               } yield ""
             },
             Some(queueName)
           )
      _ <- server.listen(
             route + ".doc",
             _ => {
               val in = summon[Schema[IN]]
               ZIO.succeed(
                 s"""|IN:
                     |${in.ast}
                     |""".stripMargin
               )
             }
           )
    } yield ()

  def broadcast(req: IN): ZIO[RpcClient, Throwable, Unit] =
    for {
      client <- ZIO.service[RpcClient]
      res    <- client.send(route, req.toJson.toArray)
    } yield res

}
