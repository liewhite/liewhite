package liewhite.sqlx.examples

import io.getquill.PostgresZioJdbcContext
import io.getquill.SnakeCase
import io.getquill._
import zio.ZIOAppDefault
import zio.Scope
import zio.ZIO
import zio.ZIOAppArgs
import com.zaxxer.hikari.HikariDataSource
import zio.ZLayer

case class Skill(title: String)

object Main extends ZIOAppDefault {
  def run: ZIO[Any & (ZIOAppArgs & Scope), Any, Any] = {
    val ctx = new MysqlZioJdbcContext(SnakeCase)
    import ctx._
    val datasource = new HikariDataSource()

    datasource.setJdbcUrl(
      "jdbc:mysql://root:@127.0.01:3306/test"
    )
    val i = quote {
      liftQuery(List(Skill("John"), Skill("name2"))).foreach(e => query[Skill].insertValue(e))
    }

    (ctx.translate(i).debug *>
      ctx.run(i, 10)).provide(ZLayer.succeed(datasource))
    // ZIO.unit
  }
}
