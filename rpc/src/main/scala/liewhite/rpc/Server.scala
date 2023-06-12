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

/*
 * Server端只需要将上层业务的返回序列化， 以及恢复异常
 *
 * Client只关心是否消息到达了， 以及对方是否回复了， 不关心回复内容
 */

// 协议层面的返回， 业务层判断code后自行处理data
case class RpcResponse(code: Int, msg: String = "", data: String) derives Schema

class RpcServer(transport: Transport) {

  val channels = new ConcurrentHashMap[Int, Channel]

  def declareQueue(channel: Channel, queueName: String, route: String) =
    ZIO.attemptBlocking {
      channel.queueDeclare(
        queueName,
        true,
        false,
        true,
        new ju.HashMap[String, Object]
      )
      channel.queueBind(queueName, "amq.topic", route)
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
    callback: Delivery => Task[String],
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
             val process =
               ZIO.logInfo(s"serve request: [route: $route queue: $queue body: ${String(msg.getBody())}]") *> ZIO
                 .attempt(callback(msg)) // 捕获业务层面抛出来的异常, 业务层面也应该捕获异常，返回结构化数据
                 .flatten
                 .tapErrorCause(err => ZIO.logWarning(s"failed process handler: $err"))
                 .map(data => RpcResponse(0, "ok", data))
                 .catchAllCause { e =>
                   ZIO.succeed(
                     RpcResponse(
                       500,
                       "failed process message",
                       e.toString()
                     )
                   )
                 }

             process.flatMap { result =>
               val replyTo = Option(msg.getProperties().getReplyTo())
               val deliveryTag = Option(msg.getProperties().getHeaders()).flatMap(i =>
                 Try(i.get("deliveryTag").asInstanceOf[Long]).toOption
               )
               val z = ZIO.succeed(replyTo.zip(deliveryTag))

               z.flatMap { rede =>
                 rede match
                   case None => {
                     ZIO.unit
                   }
                   case Some((reply, tag)) => {
                     publish(
                       channel,
                       "amq.direct",
                       reply,
                       result.toJson.toArray,
                       false,
                       AMQP.BasicProperties
                         .Builder()
                         .headers(
                           Map(
                             "deliveryTag" -> tag
                           ).asJava
                         )
                         .build()
                     )
                   }

               } *> ZIO.attemptBlocking {
                 channel.basicAck(msg.getEnvelope().getDeliveryTag(), false)
               }
             }
           }.tapErrorCause(e => ZIO.logError(s"server exit with: $e")).fork
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
  def layer: ZLayer[Transport & Scope, Nothing, RpcServer] =
    ZLayer(for {
      transport <- ZIO.service[Transport]
      server <-
        ZIO.acquireRelease(ZIO.succeed(RpcServer(transport)))(s => ZIO.logInfo("server exit: "))
    } yield server)
}
