package liewhite.sqlx

import com.zaxxer.hikari.HikariDataSource
import zio.ZIO
import zio.ZLayer

import liewhite.sqlx.DBDataSource
import org.jooq.SQLDialect

case class DBConfig(
  `type`: String,
  host: String,
  username: String,
  db: String,
  port: Option[Int] = None,
  password: Option[String] = None,
  maxConnection: Int = 20,
  minIdle: Int = 1,
  idleMills: Int = 60 * 1000
)

class DBDataSource(val config: DBConfig) {
  val datasource = new HikariDataSource()
  val port = config.port.getOrElse(
    if (config.`type` == "mysql") {
      3306
    } else if (config.`type` == "postgresql") {
      5432
    } else {
      throw Exception("must provide port for :" + config.`type`)
    }
  )
  val dialect: SQLDialect =
    if (config.`type` == "mysql") {
      SQLDialect.MYSQL
    } else if (config.`type` == "postgresql") {
      SQLDialect.POSTGRES
    } else if (config.`type` == "sqlite") {
      SQLDialect.SQLITE
    } else {
      throw Exception("not support db:" + config.`type`)
    }
  datasource.setJdbcUrl(
    s"jdbc:${config.`type`}://${config.host}:${port}/${config.db}"
  )
  datasource.setUsername(config.username)
  datasource.setMaximumPoolSize(config.maxConnection)
  datasource.setMinimumIdle(config.minIdle)
  datasource.setIdleTimeout(config.idleMills)
  config.password.foreach(datasource.setPassword(_))
}

object DBDataSource {
  def layer: ZLayer[DBConfig, Nothing, DBDataSource] =
    ZLayer(ZIO.service[DBConfig].map(DBDataSource(_)))
}
