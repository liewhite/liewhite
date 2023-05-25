package liewhite.rpc

import zio.*
import com.rabbitmq.client.AMQP
import com.rabbitmq.client.Delivery

import liewhite.json.{*, given}

// 业务异常
case class EndpointResponse[T](code: Int, internalCode: Int = 0, msg: String = "", data: Option[T]) derives Schema
case class EndpointException(code: Int, internalCode: Int = 0, msg: String = "")
    extends Exception(s"code: $code, msg: $msg") derives Schema

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
               // 要区分fail和cause, fail需要正常返回， cause直接500
               for {
                 body <- ZIO
                           .fromEither(String(req.getBody()).fromJson[IN])
                           .mapError(err => EndpointException(400, 0, err.toString()))
                 res <- ZIO
                          .attemptBlocking(callback(body).map(r => EndpointResponse(200, 200, "ok", Some(r))))
                          .flatten
                          .catchSome { case e @ EndpointException(code, icode, msg) =>
                            ZIO.succeed(
                              EndpointResponse[OUT](code, icode, msg, None)
                            )
                          }
                          .catchAllCause { err =>
                            ZIO.succeed(
                              EndpointResponse[OUT](500, 500, err.toString(), None)
                            )
                          }
                 ser = res.toJson.asString
               } yield ser
             }
           )
      _ <- server.listen(
             route + ".doc",
             _ => {
               val in  = summon[Schema[IN]]
               val out = summon[Schema[OUT]]
               ZIO.succeed(
                 EndpointResponse[String](
                   200,
                   200,
                   "",
                   Some(s"""|IN:
                            |${in.ast}
                            |
                            |OUT:
                            |${out.ast}
                            |""".stripMargin)
                 ).toJson.asString
               )
             }
           )
    } yield ()

  def call(req: IN, timeout: Duration = 30.second): ZIO[RpcClient, Throwable, OUT] =
    for {
      client <- ZIO.service[RpcClient]
      res <- client
               .call(route, req.toJson.asString, timeout = timeout)
      body <- {
        if (res.code != 0) {
          ZIO.fail(EndpointException(500, 500, res.msg))
        } else {
          ZIO
            .fromEither(res.data.fromJson[EndpointResponse[OUT]])
            .mapError(err => EndpointException(500, 500, "response can't be decode: " + err.toString()))
        }
      }
      out <- {
        if (body.code >= 300) {
          ZIO.fail(EndpointException(body.code, body.internalCode, body.msg))
        } else {
          ZIO.attempt(body.data.get)
        }
      }
    } yield out

  def send(req: IN): ZIO[RpcClient, Throwable, Unit] =
    for {
      client <- ZIO.service[RpcClient]
      _ <- client
             .send(route, req.toJson.toArray)
    } yield ()
}
