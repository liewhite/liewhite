package liewhite.rpc

import scala.util.Try
import scala.jdk.CollectionConverters.*

import zio.*
import zio.json.*
import zio.stream.*
import zio.concurrent.*

import java.util.concurrent.ConcurrentHashMap
import com.rabbitmq.client.*
import com.rabbitmq.client.AMQP.Confirm
import com.rabbitmq.client.AMQP.Basic.Deliver
import com.rabbitmq.utility.BlockingCell

import liewhite.rpc.Transport
// rpc 内部错误
class RpcException(msg: String) extends Exception(msg)

class NoRouteException(route: String) extends RpcException(s"no route ${route}")
class NackException(tag: String)      extends RpcException(s"nack ${tag}")
class TimeoutException(route: String) extends RpcException(s"timeout ${route}")

enum MessageReceipt(tag: Long, multiple: Boolean) {
  case Ack(tag: Long, multiple: Boolean) extends MessageReceipt(tag, multiple)
  case Nack(tag: Long, multiple: Boolean) extends MessageReceipt(tag, multiple)
  case NoRoute(tag: Long, route: String) extends MessageReceipt(tag, false)
  case Response(delivery: Delivery)
      extends MessageReceipt(
        Try(
          delivery
            .getProperties()
            .getHeaders()
            .get("deliveryTag")
            .asInstanceOf[Long]
        ).getOrElse(0),
        false
      )
  def getTag      = tag
  def getMultiple = multiple
}

class RpcClient(transport: Transport, publishLock: ReentrantLock, exchange: String = "amq.direct") {
  val channel = transport.newChannel()

  // deliveryTag, route, promise
  val requests =
    new ConcurrentHashMap[Long, (String, Promise[RpcException, Delivery])]

  def close() =
    Try(channel.close())

  // 启动 confirmListener， returnListener， consumer
  def start(): ZIO[Any, Nothing, Fiber.Runtime[Throwable, Long]] = {
    (for {
      confirmPromise <- Promise.make[Nothing, Unit]
      returnPromise  <- Promise.make[Nothing, Unit]
      consumePromise <- Promise.make[Nothing, Unit]
      confirmStream =
        ZStream.asyncScoped[Any, Nothing, MessageReceipt] { cb =>
          val listener = channel.addConfirmListener(
            (a, b) => {
              cb(
                ZIO.succeed(Chunk(MessageReceipt.Ack(a, b)))
              )
            },
            (a, b) => {
              cb(
                ZIO.succeed(
                  Chunk(MessageReceipt.Nack(a, b))
                )
              )
            }
          )
          ZIO.acquireRelease(
            ZIO.logInfo("[rpc-client] add confirm listener") *>
              confirmPromise.succeed(()) *>
              ZIO.succeed(listener)
          )(ln =>
            ZIO.logInfo("[rpc-client]  remove confirm listener") *>
              ZIO.succeed(Try(channel.removeConfirmListener(ln)))
          )
        }
      returnStream = ZStream.asyncScoped[Any, Nothing, MessageReceipt] { cb =>
                       val listener = channel.addReturnListener { msg =>
                         val tag = msg
                           .getProperties()
                           .getHeaders()
                           .get("deliveryTag")
                           .asInstanceOf[Long]
                         cb(
                           ZIO.succeed(
                             Chunk(MessageReceipt.NoRoute(tag, requests.get(tag)._1))
                           )
                         )
                       }

                       ZIO.acquireRelease(
                         ZIO.logInfo("[rpc-client] add return listener") *> returnPromise
                           .succeed(
                             ()
                           ) *> ZIO.succeed(listener)
                       )(ln =>
                         ZIO.logInfo("[rpc-client] remove return listener") *>
                           ZIO
                             .succeed(Try(channel.removeReturnListener(ln)))
                       )
                     }
      consumeStream =
        ZStream.asyncScoped[Any, Nothing, MessageReceipt] { cb =>
          val consumer = channel.basicConsume(
            "amq.rabbitmq.reply-to",
            true,
            new DefaultConsumer(channel) {
              override def handleDelivery(
                consumerTag: String,
                envelope: Envelope,
                properties: AMQP.BasicProperties,
                body: Array[Byte]
              ): Unit =
                cb(
                  ZIO.succeed(
                    Chunk(
                      MessageReceipt.Response(
                        Delivery(envelope, properties, body)
                      )
                    )
                  )
                )
            }
          )

          ZIO.acquireRelease(
            ZIO.logInfo("[rpc-client] add consumer") *> consumePromise.succeed(
              ()
            ) *> ZIO.succeed(consumer)
          )(ln =>
            ZIO.logInfo("[rpc-client] remove consumer") *> ZIO.succeed(
              Try(
                channel.basicCancel(consumer)
              )
            )
          )
        }

      receipts = confirmStream.merge(returnStream).merge(consumeStream)

      f <- receipts
             .runFoldZIO(0L) { (lastTag, r) =>
               val tag = r.getTag
               val tags = if (r.getMultiple) {
                 // 左开右闭
                 ((lastTag + 1) to tag).filter(i => requests.contains(i))
               } else {
                 Seq(tag)
               }
               val nextTag = if (r.getMultiple) {
                 tag + 1
               } else {
                 Seq(lastTag + 1, tag).min
               }

               ZIO.attempt {
                 ZIO.foreach(tags) { t =>
                   val req = requests.get(t)
                   if (req == null) {
                     // response 可能在ack前到达
                     ZIO.unit
                   } else {
                     r match
                       case e: MessageReceipt.Nack =>
                         ZIO.logWarning(s"message nacked: ${e.tag}") *>
                           req._2.fail(NackException(e.tag.toString()))
                       case e: MessageReceipt.NoRoute =>
                         ZIO.logWarning(s"message no route: ${e.route}") *>
                           req._2.fail(NoRouteException(e.route))
                       case v: MessageReceipt.Ack => ZIO.unit
                       case MessageReceipt.Response(delivery) =>
                         for {
                           tag <- ZIO.attempt(
                                    delivery
                                      .getProperties()
                                      .getHeaders()
                                      .get("deliveryTag")
                                      .asInstanceOf[Long]
                                  )
                           _ <- req._2.succeed(delivery)
                         } yield ()
                   }
                 }
               }.flatten
                 .catchAllCause(e => ZIO.logError(s"failed process receipt: $e") *> ZIO.unit)
                 .as(nextTag)
             }
             .fork
      _ <- confirmPromise.await
      _ <- returnPromise.await
      _ <- consumePromise.await
    } yield f)
  }

  def waitForResponse(tag: Long): Task[Delivery] =
    ZIO.attempt(requests.get(tag)._2).flatMap(_.await)

  def nextTag: ZIO[Any, Nothing, Long] =
    ZIO.succeed(channel.getNextPublishSeqNo())

  def call(
    route: String,
    message: Array[Byte],
    mandatory: Boolean = true,
    props: AMQP.BasicProperties = AMQP.BasicProperties(),
    timeout: Duration = 30.second
  ): Task[Array[Byte]] = {
    val lockedScope =
      ZIO.scoped(
        for {
          _   <- publishLock.withLock
          tag <- nextTag
          newProps = props
                       .builder()
                       .headers(Map("deliveryTag" -> tag).asJava)
                       .replyTo("amq.rabbitmq.reply-to")
                       .build()
          promise <- Promise.make[RpcException, Delivery]
          _ <- (ZIO.succeed {
                 requests.put(tag, (route, promise))
               })
          _ <- ZIO.attemptBlocking {
                 channel
                   .basicPublish(exchange, route, mandatory, newProps, message)
               }
        } yield tag
      )

    // 等待response可以在scope外
    ZIO.scoped {
      for {
        tag       <- lockedScope
        cleanTask <- ZIO.acquireRelease(ZIO.succeed(tag))(t => ZIO.succeed(requests.remove(t)))
        response <- waitForResponse(tag)
                      .timeoutFail(TimeoutException(route))(timeout)
      } yield response.getBody()
    }
  }

  def send(
    route: String,
    message: Array[Byte],
    mandatory: Boolean = false,
    props: AMQP.BasicProperties = AMQP.BasicProperties()
  ): Task[Unit] = {
    val lockedScope: ZIO[Any, Throwable, Unit] =
      ZIO.scoped(
        for {
          _   <- publishLock.withLock
          tag <- nextTag
          _ <- ZIO.attemptBlocking {
                 channel
                   .basicPublish(exchange, route, mandatory, props, message)
               }
        } yield ()
      )
    lockedScope
  }
}

object RpcClient {
  def layer: ZLayer[Transport, Nothing, RpcClient] =
    ZLayer.scoped(for {
      lock <- ReentrantLock.make()
      tp   <- ZIO.service[Transport]
      cli <- ZIO.acquireRelease(ZIO.succeed(RpcClient(tp, lock)))(client =>
               ZIO.succeed(client.close()).debug("client exit: ")
             )
      _ <- cli.start()
    } yield cli)
}
