package liewhite.sqlx.examples

import scala.jdk.CollectionConverters.*
import zio.*
import liewhite.sqlx.*
import org.jooq.DSLContext
import org.jooq.impl.DefaultDSLContext
import org.jooq.Configuration
import org.jooq.SQLDialect

import liewhite.sqlx.DBDataSource

import liewhite.sqlx.Migration
import org.jooq.impl.DefaultDataType
import org.jooq.impl.SQLDataType
import org.jooq.impl.DSL
import org.jooq.Record2

import liewhite.sqlx.as
case class OK(
  a: Option[Long],
  b: Detail
)
object MyApp extends ZIOAppDefault {
  def run = {
    val config = DBConfig("mysql", "localhost", "root", "test")

    val key = 1233
    val q   = Table[User].splitWith(key)
    (for {
      migResult <- Migration.Migrate[User]
      ctx       <- ZIO.service[org.jooq.DSLContext]
      user1     <- User(0, Some(key), "kakaka1", Detail("jqk")).toRecord
      user2     <- User(0, Some(key), "kakaka2", Detail("jqk")).toRecord
      user3     <- User(0, Some(key), "kakaka3", Detail("jqk")).toRecord
      _ <- ZIO.attempt {
             ctx.insertInto(q.table).columns(q.jooqCols*).valuesOfRecords(Vector(user1, user2, user3)*).execute()
             val result = ctx.select(q.field_age, q.field_detail).from(q.table).fetch()
             println(result.as[OK])
             println(result.as[(Option[Long], Detail)])
           }
    } yield ()).provide(
      ZLayer.succeed(config),
      DBDataSource.layer,
      DBContext.layer
    )
  }
}
