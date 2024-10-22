package liewhite.config

import zio.*
import liewhite.json.{*, given}
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import java.io.File

def loadConfig[T: Schema](
  path: String = "conf/config.yaml"
): IO[Throwable, T] =
  if (path.endsWith(".yml") || path.endsWith(".yaml")) {
    ZIO.attemptBlocking {
      val yamlReader = new ObjectMapper(new YAMLFactory())
      val obj        = yamlReader.readTree(new File(path))
      val jsonWriter = new ObjectMapper()
      val result     = jsonWriter.writeValueAsString(obj)
      result
    }.flatMap { str =>
      ZIO.fromEither(
        str.fromJson[T]
      )
    }
  } else if (path.endsWith(".json")) {
    ZIO
      .readFile(path)
      .flatMap { str =>
        ZIO.fromEither(
          str.fromJson[T]
        )
      }
  } else {
    ZIO.fail(Exception("unknown config type"))
  }

object TestConfig extends ZIOAppDefault {
  case class A(a: Int) derives Schema
  def run: ZIO[Any & (ZIOAppArgs & Scope), Any, Any] =
    loadConfig[A]("conf/config.json").debug *>
      loadConfig[A]("conf/config.yaml").debug
}
