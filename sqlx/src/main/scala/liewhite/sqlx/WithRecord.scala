package liewhite.sqlx

import org.jooq
import org.jooq.impl.DSL
import scala.deriving.Mirror
import scala.util.Try
import scala.jdk.CollectionConverters.*
import zio.*
import scala.compiletime.summonAll

import shapeless3.deriving.Labelling

trait FromRecord[T] {
  def fromRecord(record: jooq.Record): Either[Throwable, T]
}

object FromRecord {
  inline given derived[A <: Product](using
    gen: Mirror.ProductOf[A]
  ): FromRecord[A] = {
    new FromRecord[A] {

      override def fromRecord(record: jooq.Record): Either[Throwable, A] =
        Try {
          val columnTypes =
            summonAll[Tuple.Map[gen.MirroredElemTypes, TField]].toArray.toList
              .asInstanceOf[List[TField[_]]]
          val values = columnTypes.zip(record.intoList().asScala).map {
            case (col, recItem) => {
              col.dataType.getConverter().from(recItem.asInstanceOf)
            }
          }
          val tuple = Tuple.fromArray(values.toArray)
          gen.fromProduct(tuple)

        }.toEither
    }
  }
  inline given tuple[A <: Tuple]: FromRecord[A] = {
    new FromRecord[A] {
      override def fromRecord(record: jooq.Record): Either[Throwable, A] =
        Try {
          val columnTypes = summonAll[Tuple.Map[A, TField]].toArray.toList
            .asInstanceOf[List[TField[_]]]
          val values = columnTypes.zip(record.intoList().asScala).map {
            case (col, recItem) => {
              col.dataType.getConverter().from(recItem.asInstanceOf)
            }
          }
          val tp = Tuple.fromArray(record.intoArray())
          tp.asInstanceOf[A]
        }.toEither
    }
  }
}

extension [R <: jooq.Record](record: R) {
  def as[T](using t: FromRecord[T]): Either[Throwable, T] =
    t.fromRecord(record)
}

extension [R <: jooq.Record](records: jooq.Result[R]) {
  def as[T](using t: FromRecord[T]): Vector[Either[Throwable, T]] =
    records.asScala.map(i => t.fromRecord(i)).toVector
}

trait ToRecord[T] {
  def toRecord(t: T): ZIO[DSLContext, Throwable, org.jooq.Record]
}

object ToRecord {
  inline given derived[A <: Product](using
    gen: Mirror.ProductOf[A],
    table: Table[A]
  ): ToRecord[A] = {
    new ToRecord[A] {
      def toRecord(t: A): ZIO[DSLContext, Throwable, org.jooq.Record] =
        for {
          ctx <- ZIO.service[jooq.DSLContext]
          result <- ZIO.attempt {
                      val columnTypes =
                        summonAll[Tuple.Map[gen.MirroredElemTypes, TField]].toArray.toList
                          .asInstanceOf[List[TField[Any]]]
                      val labels = table.columns.map(_.colName)

                      val fields = labels.map(DSL.field(_))
                      val r      = ctx.newRecord(fields*)

                      val converters = columnTypes.map(_.dataType.getConverter())
                      val values     = Tuple.fromProductTyped(t).toArray
                      fields.zip(values).zip(converters).foreach {
                        case ((f, v), c) => {
                          r.set(f, v, c)
                        }
                      }
                      r
                    }
        } yield result
    }
  }
}

extension [T <: Product: Mirror.ProductOf](t: T) {
  def toRecord(using
    tr: ToRecord[T]
  ): ZIO[DSLContext, Throwable, org.jooq.Record] =
    tr.toRecord(t)
}
