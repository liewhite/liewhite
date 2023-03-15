package io.github.liewhite.sqlx

import zio.ZIO
import zio.ZLayer
import org.jooq.impl.DefaultDSLContext
import org.jooq.SQLDialect
import org.jooq.DSLContext

class DBContext(val ds: DBDataSource) {
  val ctx = new DefaultDSLContext(ds.datasource ,ds.dialect)
}

object DBContext {
  def layer: ZLayer[DBDataSource, Nothing, DSLContext] = {
    ZLayer {
      for {
        ds <- ZIO.service[DBDataSource]
      } yield DBContext(ds).ctx
    }
  }
}
