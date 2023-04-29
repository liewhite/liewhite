package liewhite.sqlx

import zio.ZIO
import zio.ZLayer
import org.jooq.impl.DefaultDSLContext
import org.jooq.DSLContext

class DBContext(val ds: DBDataSource) {
  val ctx = new DefaultDSLContext(ds.datasource, ds.dialect)
}

object DBContext {
  def layer: ZLayer[DBDataSource, Nothing, DSLContext] =
    ZLayer(ZIO.service[DBDataSource].map(DBContext(_).ctx))
}
