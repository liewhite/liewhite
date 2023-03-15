package io.github.liewhite.sqlx

import scala.compiletime.*
import scala.quoted.*
import scala.deriving.Mirror
import zio.ZIO
import org.jooq.DSLContext
import scala.jdk.CollectionConverters.*
import org.jooq
import org.jooq.InsertValuesStepN

import io.github.liewhite.sqlx.DSLMacros


enum JoinType {
  case InnerJoin
  case LeftJoin
  case RightJoin
}
case class JoinItem(
    table: Table[_],
    joinType: JoinType)
// query 的 selectable 要用refinement type来加入各个table
class Query(
    val tables: Map[String, Table[_]],
    val joins: Vector[JoinItem] = Vector.empty)
    extends Selectable {

  def selectDynamic(name: String): Any = {
    tables(name)
  }

}

object Query {
  def insertOne[T <: Product: Table](
      o: T
    ): ZIO[DSLContext, Nothing, InsertValuesStepN[org.jooq.Record]] = {
    import org.jooq.impl.DSL.*
    val t = summon[Table[T]]
    for {
      ctx <- ZIO.service[DSLContext]
    } yield {
      ctx
        .insertInto(t.table)
        .columns(t.jooqCols.filter(item => true).asJava)
        .values(t.values(o).asJava)
    }
  }

  def insertMany[T <: Product: Table](
      o: Seq[T]
    ): ZIO[DSLContext, Nothing, InsertValuesStepN[org.jooq.Record]] = {
    import org.jooq.impl.DSL.*
    val t = summon[Table[T]]
    for {
      ctx <- ZIO.service[DSLContext]
    } yield {
      // drop id
      val clause: InsertValuesStepN[org.jooq.Record] =
        ctx.insertInto(table(t.tableName)).columns(t.jooqCols.asJava)
      o.foldLeft(clause)((cls, item) => {
        cls.values(t.values(item).asJava)
      })
    }
  }

  transparent inline def apply[T <: Product: Table] = {
    val t = summon[Table[T]]
    val q = new Query(Map.empty)
    DSLMacros.refinementQuery(q, t)
  }

  extension [Q <: Query](q: Q) {
    transparent inline def join(t: Table[_])      = {
      val newQ = new Query(
        q.tables.updated(t.tableName, t),
        q.joins.appended(JoinItem(t, JoinType.InnerJoin))
      )
      DSLMacros.refinementQuery(newQ.asInstanceOf[Q], t)
    }
    transparent inline def leftJoin(t: Table[_])  = {
      val newQ = new Query(
        q.tables.updated(t.tableName, t),
        q.joins.appended(JoinItem(t, JoinType.LeftJoin))
      )
      DSLMacros.refinementQuery(newQ.asInstanceOf[Q], t)
    }
    transparent inline def rightJoin(t: Table[_]) = {
      val newQ = new Query(
        q.tables.updated(t.tableName, t),
        q.joins.appended(JoinItem(t, JoinType.RightJoin))
      )
      DSLMacros.refinementQuery(newQ.asInstanceOf[Q], t)
    }

    /**
      * 
      *
      * @param condition
      * @return
      */
    inline def where(conditionFunc: Q => Condition): AfterWhere[Q] = {
      val condition = conditionFunc(q)
      AfterWhere(q,condition)
    }
  }

}

class Condition {}

class AfterWhere[Q <: Query](val query: Q, val condition: Condition) {
  inline def select[T <: Tuple](fields: Q => T): AfterSelect[T] = {
    new AfterSelect(this,fields(query))
  }

  inline def orderBy[T](fields: Q => Field[T]): AfterOrderBy[T] = {
    new AfterOrderBy(this,fields(query))
  }
}


class AfterOrderBy[T](val afterWhere: AfterWhere[_],val orderBy: Field[T]){
  def limit(n: Int): AfterLimit[T] = {
    new AfterLimit(this,n)
  }
}

class AfterLimit[T](val AfterOrderBy: AfterOrderBy[_],val limit: Int){

}

class AfterSelect[T <: Tuple](val afterWhere: AfterWhere[_],val fields: T){
  inline def end() = {

  }


}
