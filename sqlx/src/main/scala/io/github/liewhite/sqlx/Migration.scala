package io.github.liewhite.sqlx

import org.jooq
import scala.collection.mutable
import scala.jdk.CollectionConverters.*
import org.jooq.impl.DSL
import org.jooq.impl.SQLDataType
import org.jooq.impl.DSL.*
import zio.ZIO
import scala.util.*
import org.jooq.DataType
import zio.Unsafe
import zio.Task
import java.sql.Connection

object Migration {
  def Migrate[T <: Product: Table]: ZIO[DBDataSource, Throwable, Unit] = {
    for {
      datasource <- ZIO.service[DBDataSource]
      result     <- {
        ZIO.attempt({
          val table      = summon[Table[T]]
          val jdbc       = datasource.datasource.getConnection()
          table.splitCount match{
            case None => migrateTable[T](table.tableName, jdbc)
            case Some(count) => {
              Range(0,count).foreach(item => migrateTable[T](s"${table.tableName}_${item}", jdbc))
            }
          }
        })
      }
    } yield result
  }

  def migrateTable[T<:Product:Table](tableName: String, conn: Connection): Unit = {
      val table      = summon[Table[T]]
      val jdbc       = conn
      val driverName = jdbc.getMetaData.getDriverName

      // "PostgreSQL JDBC Driver"
      // "MySQL Connector/J"
      val jooqConn                              = DSL.using(jdbc)
      var metaCache: jooq.Meta                  = jooqConn.meta()
      val tables: mutable.Map[String, Table[_]] = mutable.Map.empty

      getTable(tableName) match {
        case None    => createTable(table)
        case Some(t) => updateTable(table, t)
      }

      def getTable(name: String): Option[jooq.Table[_]] = {
        val result = metaCache.getTables(name)
        if (result.isEmpty) {
          None
        } else {
          Some(result.get(0))
        }
      }

      def createTable(table: Table[_]) = {
        val cols       = table.columns
        // default and nullable
        val createStmt = {
          val create = jooqConn.createTable(tableName)
          table.columns.foldLeft(create)((b, col) => {
            var datatype = col.getDataType
            var c        = b.column(col.colName, datatype)
            c
          })
        }
        table.pk match
          case None        => createStmt.execute()
          case Some(value) => createStmt.primaryKey(value.colName).execute()

        // add unique constraint
        table.columns.foreach(item => {
          if (item.unique) {
            jooqConn
              .alterTable(tableName)
              .add(constraint(item.uniqueKeyName).unique(item.colName))
              .execute
          }
        })

        table.indexes.foreach(idx => {
          if (idx.unique) {
            jooqConn
              .createUniqueIndex(idx.indexName)
              .on(tableName, idx.cols*)
              .execute
          } else {
            jooqConn
              .createIndex(idx.indexName)
              .on(tableName, idx.cols*)
              .execute
          }
        })
      }

      def updateTable(
          table: Table[_],
          current: jooq.Table[_]
        ) = {
        // 新增column, column 比较， 只新增， 不删除, 不重命名
        table.columns.foreach(col => {
          if (current.field(col.colName) == null) {
            createColumn(current, col)
          } else {
            val datatype = col.getDataType
            jooqConn
              .alterTable(current)
              .alter(col.colName)
              .set(datatype)
              .execute
            if (col.default.isDefined) {
              jooqConn
                .alterTable(current)
                .alter(col.colName)
                .setDefault(col.default.get)
                .execute
            } else {
              if (col.t.nullable) {
                // TEXT等类型无法set default
                Try {
                  jooqConn
                    .alterTable(current)
                    .alter(col.colName)
                    .dropDefault()
                    .execute
                }
              } else {
                Unsafe.unsafe { implicit unsafe =>
                  {
                    zio.Runtime.default.unsafe
                      .run {
                        for {
                          _ <- ZIO.logInfo(
                            s"skip dropping default on not null column: ${col.modelName}.${col.colName}"
                          )
                        } yield ()
                      }
                      .getOrThrowFiberFailure()
                  }
                }
              }
            }
          }
        })
        // postgresql: 定义为Unique的会出现在这里 : "uk_xx"
        // mysql: 定义为Unique或者唯一索引会出现在这里, 如果同时定义了unique和唯一索引，会出现多次
        // 用库中结构来适配代码定义
        val currentUniques = current.getUniqueKeys.asScala
          .map(_.getName)
          .toSet
        val defineUniques  =
          table.columns.filter(_.unique).map(_.uniqueKeyName).toSet

        (defineUniques -- currentUniques).foreach(item => {
          jooqConn
            .alterTable(tableName)
            .add(constraint(item).unique(item.stripPrefix("uk:")))
            .execute
        })
        (currentUniques -- defineUniques).foreach(item => {
          if (item.startsWith("uk:")) {
            jooqConn
              .alterTable(tableName)
              .drop(constraint(item).unique(item.stripPrefix("uk:")))
              .execute
          }
        })

        // Mysql:  会查询到所有普通索引, 没有唯一索引和唯一约束
        // postgres: 查询到所有索引， 没有唯一约束
        val oldIdxes =
          (current.getIndexes.asScala.map(item =>
            item.getName
          ) ++ currentUniques
            .filter(
              _.startsWith("ui:")
            )).filter(!_.startsWith("uk:"))

        val newIdxes = table.indexes.map(item => item.indexName)

        newIdxes.foreach(idx => {
          if (!oldIdxes.contains(idx)) {
            val names_unique = idx.split(":")
            val names        = names_unique(1).split("-").toVector
            val unique       = if (names_unique(0) == "i") false else true
            if (unique) {
              jooqConn
                .createUniqueIndex(idx)
                .on(current.getName, names*)
                .execute
            } else {
              jooqConn
                .createIndex(idx)
                .on(current.getName, names*)
                .execute
            }
          }
        })

        oldIdxes.foreach(idx => {
          val names_unique = idx.split(":")
          val names        = names_unique(1).split("-").toVector
          val unique       = if (names_unique(0) == "i") false else true
          if (!newIdxes.contains(idx)) {
            jooqConn
              .dropIndex(idx)
              .on(current.getName)
              .execute
          }
        })
      }

      def createColumn(
          jooqTable: jooq.Table[_],
          col: Field[_]
        ) = {
        val stm = jooqConn
          .alterTable(jooqTable)
          .addColumn(col.colName, col.getDataType)
        stm.execute
      }

  }
}
