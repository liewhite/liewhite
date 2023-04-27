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
    val config = DBConfig("mysql", "localhost", "root", "test", password = Some("123"))

    val key = 1233
    val q   = Table[User].tableBySplitKey(key)
    (for {
      migResult <- Migration.Migrate[User]
      ctx       <- ZIO.service[org.jooq.DSLContext]
      user1     <- User(0, Some(key), O("oo"),EE.EE1, "kakaka1", Detail("jqk")).toRecord
      user2     <- User(0, Some(key), O("oo"), EE.EE2,"kakaka2", Detail("jqk")).toRecord
      user3     <- User(0, Some(key), O("oo"), EE.EE3,"kakaka3", Detail("jqk")).toRecord
      _ <- ZIO.attempt {
             ctx.insertInto(q.table).columns(q.jooqCols*).valuesOfRecords(user1, user2, user3).execute()
             val result = ctx.select(q.jooqCols*).from(q.table).fetch()
             println(result.as[User])
            //  println(result.as[(Option[Long], Detail)])
           }
    } yield ()).provide(
      ZLayer.succeed(config),
      DBDataSource.layer,
      DBContext.layer
    )
  }
}
