package liewhite.rpc

import zio.*
import zio.concurrent.*
import zio.json.*
import com.rabbitmq.client.AMQP
import com.rabbitmq.client.ConnectionFactory
import scala.jdk.CollectionConverters.*
import com.rabbitmq.client.AMQP.Basic.Deliver
import com.rabbitmq.client.Delivery
import java.util.concurrent.ConcurrentMap
import com.rabbitmq.utility.BlockingCell
import java.util.concurrent.ConcurrentHashMap
import com.rabbitmq.client.UnroutableRpcRequestException
import zio.stream.ZStream
import com.rabbitmq.client.AMQP.Confirm
import com.rabbitmq.client.Return
import com.rabbitmq.client.Consumer
import com.rabbitmq.client.DefaultConsumer
import com.rabbitmq.client.AMQP.BasicProperties
import com.rabbitmq.client.Envelope
import java.{util => ju}
import com.rabbitmq.client.Channel
import scala.util.Try
import zio.json.ast.Json
import zio.json.*

import liewhite.rpc.Transport
// 返回给调用端的错误
case class RpcFailure(code: Int, internalCode: Int = 0, msg: String = "", data: Json = Json.Null)
    extends Exception(s"code: $code, msg: $msg data: $data") derives JsonEncoder, JsonDecoder

class RpcServer(transport: Transport, defaultExchange: String = "amq.direct") {
  val channel = transport.newChannel()

  def close() =
    Try(channel.close())

  def listen(
    route: String,
    callback: Delivery => Task[Array[Byte]],
    queue: Option[String] = None
  ): ZIO[Any, Throwable, Fiber.Runtime[Throwable, Unit]] = {
    val queueName = queue.getOrElse(route)

    val queueDeclare = ZIO.attemptBlocking {
      channel.queueDeclare(
        queueName,
        false,
        false,
        true,
        new ju.HashMap[String, Object]
      )
      channel.queueBind(queueName, "amq.direct", route)
    }

    val returnStream = ZStream.asyncScoped[Any, Nothing, Return] { cb =>
      val listener = channel.addReturnListener(msg => cb(ZIO.succeed(Chunk(msg))))

      ZIO.acquireRelease(
        ZIO.logInfo("[rpc-server] create server return listener") *>
          ZIO.succeed(listener)
      )(ln =>
        ZIO.logInfo("[rpc-server] remove return listener") *>
          ZIO
            .succeed(Try(channel.removeReturnListener(ln)))
      )
    }

    val consumeStream =
      ZStream.asyncScoped[Any, Nothing, Delivery] { cb =>
        val consumer = channel.basicConsume(
          queueName,
          false,
          new DefaultConsumer(channel) {
            override def handleDelivery(
              consumerTag: String,
              envelope: Envelope,
              properties: BasicProperties,
              body: Array[Byte]
            ): Unit =
              cb(
                // ZIO.debug(envelope.toString()) *> ZIO.debug(
                //   properties.toString()
                // ) *>
                ZIO.succeed(Chunk(Delivery(envelope, properties, body)))
              )
          }
        )

        ZIO.acquireRelease(
          ZIO.logInfo(s"[rpc-server] create consumer $route") *>
            ZIO.succeed(consumer)
        )(ln =>
          ZIO.logInfo(s"[rpc-server] remove consumer $route") *>
            ZIO
              .succeed(Try(channel.basicCancel(consumer)))
        )
      }

    (for {
      _ <- queueDeclare
      _ <- returnStream
             .runForeach(item =>
               ZIO
                 .logWarning("[rpc-server] response returned, may be client is dead")
             )
             .fork
      f <- consumeStream.runForeach { msg =>
             val process = ZIO
               .attempt(callback(msg))
               .flatten
               .catchSome {
                 case e: RpcFailure => {
                   ZIO.succeed(e.toJson.getBytes)
                 }
               }
               .catchAllCause { e =>
                 ZIO.succeed(
                   RpcFailure(
                     500,
                     0,
                     "internal error",
                     Json.Str(e.toString())
                   ).toJson.getBytes()
                 )
               }

             process.flatMap { result =>
               ZIO.when(
                 msg
                   .getProperties()
                   .getReplyTo() != null && msg
                   .getProperties()
                   .getReplyTo() != ""
               )(
                 publish(
                   "",
                   msg.getProperties().getReplyTo(),
                   result,
                   false,
                   AMQP.BasicProperties
                     .Builder()
                     .headers(
                       Map(
                         "deliveryTag" -> msg.getEnvelope().getDeliveryTag()
                       ).asJava
                     )
                     .build()
                 )
               ) *> ZIO.attemptBlocking(
                 channel.basicAck(msg.getEnvelope().getDeliveryTag(), false)
               )

             }
           }
             .tapErrorCause(e => ZIO.logError(s"server exit with: $e"))
             .fork

    } yield f)
  }

  def publish(
    exchange: String,
    route: String,
    message: Array[Byte],
    mandatory: Boolean = false,
    props: AMQP.BasicProperties = null
  ): Task[Unit] =
    ZIO.attemptBlocking {
      val r = channel.basicPublish(exchange, route, mandatory, props, message);
      r
    }

}

object RpcServer {
  // 不应该释放
  def layer: ZLayer[Transport, Nothing, RpcServer] =
    ZLayer.scoped(
      for {
        transport <- ZIO.service[Transport]
        server <-
          ZIO.acquireRelease(ZIO.succeed(RpcServer(transport)))(s => ZIO.succeed(s.close()).debug("server exit: "))
      } yield server
    )
}
