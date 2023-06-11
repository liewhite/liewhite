package liewhite.config

import zio.*
import liewhite.json.{*, given}
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import java.io.File

def loadConfig[T: Schema](
  path: String = "conf/config.yaml"
): IO[Throwable, T] =
  val yamlReader = new ObjectMapper(new YAMLFactory())
  val obj        = yamlReader.readTree(new File(path))
  val jsonWriter = new ObjectMapper()
  val result     = jsonWriter.writeValueAsString(obj)
  ZIO.fromEither(
    result.fromJson[T]
  )


object TestConfig extends ZIOAppDefault{
  case class A(a:Int) derives Schema
  def run: ZIO[Any & (ZIOAppArgs & Scope), Any, Any] = {
    loadConfig[A]().debug
  }
}