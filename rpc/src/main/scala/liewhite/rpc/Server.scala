package liewhite.rpc

import zio.*
import zio.concurrent.*
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
import liewhite.json.*

import liewhite.rpc.Transport

// 返回给调用端的错误
case class RpcFailure(code: Int, internalCode: Int = 0, msg: String = "", data: Json = Json.Null)
    extends Exception(s"code: $code, msg: $msg data: $data") derives Schema

class RpcServer(transport: Transport, defaultExchange: String = "amq.direct") {

  val channels = new ConcurrentHashMap[Int, Channel]

  def declareQueue(channel: Channel, queueName: String, route: String) =
    ZIO.attemptBlocking {
      channel.queueDeclare(
        queueName,
        false,
        false,
        true,
        new ju.HashMap[String, Object]
      )
      channel.queueBind(queueName, "amq.direct", route)
    }

  def returnListener(channel: Channel) =
    ZStream.asyncScoped[Any, Nothing, Return] { cb =>
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

  def consumer(channel: Channel, queueName: String) =
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
        ZIO.logInfo(s"[rpc-server] create consumer $queueName") *>
          ZIO.succeed(consumer)
      )(ln =>
        ZIO.logInfo(s"[rpc-server] remove consumer $queueName") *>
          ZIO
            .succeed(Try(channel.basicCancel(consumer)))
      )
    }

  def listen(
    route: String,
    callback: Delivery => Task[Array[Byte]],
    queue: Option[String] = None
  ): ZIO[Scope, Throwable, Fiber.Runtime[Throwable, Unit]] = {
    val queueName = queue.getOrElse(route)

    (for {
      channel <- transport.scopedChannel()
      _       <- declareQueue(channel, queueName, route)
      _ <- returnListener(channel)
             .runForeach(item =>
               ZIO
                 .logWarning("[rpc-server] response returned, may be client is dead")
             )
             .fork
      f <- consumer(channel, queueName).runForeach { msg =>
             val process = ZIO.logInfo(s"[$route] [$queue] ${String(msg.getBody())}") *> ZIO
               .attempt(callback(msg))
               .flatten
               .catchSome {
                 case e: RpcFailure => {
                   ZIO.succeed(e.toJson.toArray)
                 }
               }
               .catchAllCause { e =>
                 ZIO.succeed(
                   RpcFailure(
                     500,
                     0,
                     "internal error",
                     e.toString().toJsonAst
                   ).toJson.toArray
                 )
               }

             process.flatMap { result =>
               ZIO.when(
                 msg
                   .getProperties()
                   .getReplyTo() != null && msg
                   .getProperties()
                   .getReplyTo() != "" && msg.getProperties().getHeaders() != null &&
                   msg.getProperties().getHeaders().get("deliveryTag") != null &&
                   msg.getProperties().getHeaders().get("deliveryTag").isInstanceOf[Long]
               )(
                 publish(
                   channel,
                   "",
                   msg.getProperties().getReplyTo(),
                   result,
                   false,
                   AMQP.BasicProperties
                     .Builder()
                     .headers(
                       Map(
                         "deliveryTag" -> msg.getProperties().getHeaders().get("deliveryTag")
                       ).asJava
                     )
                     .build()
                 )
               ) *> ZIO.attemptBlocking {
                 channel.basicAck(msg.getEnvelope().getDeliveryTag(), false)
               }
             }
           }
             .tapErrorCause(e => ZIO.logError(s"server exit with: $e"))
             .fork

    } yield f)
  }

  def publish(
    channel: Channel,
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
          ZIO.acquireRelease(ZIO.succeed(RpcServer(transport)))(s => ZIO.logInfo("server exit: "))
      } yield server
    )
}
