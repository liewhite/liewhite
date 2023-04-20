package liewhite.rpc

import zio.*
import com.rabbitmq.client.*
import zio.concurrent.ConcurrentSet

class Transport(val connection: Connection) {
  def newChannel(qos: Int = 1) = {
    val channel = connection.createChannel()
    channel.confirmSelect()
    channel.basicQos(qos)
    channel
  }
  def close() = ZIO.succeed(
    connection.close()
  )

}

object Transport {
  def layer(uri: String): ZLayer[Any, Nothing, Transport] = {
    val tp = ZIO.acquireRelease({
      ZIO.succeed(newTransport(uri))
    })(tp =>
      ZIO.debug("release transport") *> ZIO
        .succeed(tp.close())
    )
    ZLayer.scoped(tp)
  }

  def newTransport(uri: String) = {
    val factory = new ConnectionFactory()
    factory.setUri(uri)
    val connection = factory.newConnection()
    Transport(connection)
  }
}
