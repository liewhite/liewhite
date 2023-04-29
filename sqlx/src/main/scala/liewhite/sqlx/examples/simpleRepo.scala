package liewhite.sqlx.examples

import scala.jdk.CollectionConverters.*
import zio.*
import liewhite.sqlx.{*, given}
import liewhite.sqlx.DBDataSource

import liewhite.sqlx.Migration
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
      ctx       <- ZIO.service[DSLContext]
      user1     <- User(0, Some(key), O("1"), EE.EE1, "kakaka1", Detail("jqk")).toRecord
      user2     <- User(0, Some(key), O("2"), EE.EE2, "kakaka2", Detail("jqk")).toRecord
      user3     <- User(0, Some(key), O("3"), EE.EE3, "kakaka3", Detail("jqk")).toRecord
      _ <- ZIO.attempt {
             ctx.transaction { tx =>
               ctx.deleteFrom(q.table).execute()
               ctx.insertInto(q.table).columns(q.jooqCols*).valuesOfRecords(user1, user2, user3).execute()
               val result = ctx.select(q.jooqCols*).from(q.table).fetch()
               result.as[User].foreach(println)
               ctx.deleteFrom(q.table).execute()
             }
           }
    } yield ()).provide(
      ZLayer.succeed(config),
      DBDataSource.layer,
      DBContext.layer
    )
  }
}
