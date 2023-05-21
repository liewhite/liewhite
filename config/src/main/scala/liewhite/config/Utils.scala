package liewhite.config

import zio.Unsafe
import zio.config.magnolia.{DeriveConfig, deriveConfig}
import zio.ConfigProvider
import java.io.File
import zio.*
import zio.config.*
import zio.config.magnolia.*
import zio.config.yaml.*

def loadConfig[T: DeriveConfig](
  path: String = "conf/config.yaml"
): IO[Throwable, T] =
  ZIO.attempt {
    ConfigProvider
      .fromYamlFile(new File(path))
      .load(deriveConfig[T])
  }.flatten
