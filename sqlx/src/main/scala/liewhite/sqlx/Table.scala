package liewhite.sqlx

import scala.compiletime.*
import scala.quoted.*
import scala.deriving.Mirror
import scala.jdk.CollectionConverters.*

import shapeless3.deriving.{K0, Continue, Labelling}

import org.jooq
import org.jooq.impl.DSL.*

import zio.ZIO
import org.jooq.DSLContext
import liewhite.common.*

class DriverNotSupportError(driver: String) extends Exception(s"driver not support: $driver")

case class Idx(name: String, cols: Vector[String], unique: Boolean) {
  def indexName: String = {
    val prefix = if (unique) "ui:" else "i:"
    prefix + cols.mkString("-")
  }
}

// migrate时， 先拿到meta，
// 然后将diff apply 到db
// 再从database meta 恢复出table,用作后续jooq的操作
trait Table[T <: Product: Mirror.ProductOf] extends Selectable {
  self =>
  def tableName: String
  def splitCount: Option[Int]
  def indexes: Vector[Idx]
  def columns: Vector[Field[_]]

  def table: jooq.Table[org.jooq.Record] = jooq.impl.DSL.table(tableName)

  def splitWith(key: Long): self.type =
    (new Table[T] {
      def tableName: String = {
        val split   = self.splitCount.get
        val rawName = self.tableName
        s"${rawName}_${key % split}"
      }

      def splitCount: Option[Int] = self.splitCount

      override def indexes: Vector[Idx] = ???

      def columns: Vector[Field[?]] = self.columns.map(f => f.copy(modelName = tableName))

      override def selectDynamic(name: String): Any =
        colMap(name.stripPrefix("field_")).field
    }).asInstanceOf[self.type]

  def pk: Option[Field[_]] = columns.find(_.primaryKey)

  def jooqCols: Vector[org.jooq.Field[Object]] =
    columns.map(item => field(item.colName))

  def fields: Vector[org.jooq.Field[Object]] = jooqCols

  def colMap: Map[String, Field[_]] =
    columns.map(item => (item.fieldName, item)).toMap

  def values(t: T): Vector[Any] = {
    val vs = Tuple.fromProductTyped(t).toArray.toVector
    vs
  }

  def selectDynamic(name: String): Any =
    colMap(name.stripPrefix("field_")).field

}

object Table {

  inline given derived[A <: Product](using
    gen: Mirror.ProductOf[A],
    labelling: Labelling[A],
    primary: RepeatableAnnotations[Primary, A],
    index: RepeatableAnnotations[Index, A],
    unique: RepeatableAnnotations[Unique, A],
    length: RepeatableAnnotations[Length, A],
    precision: RepeatableAnnotations[Precision, A],
    defaultValue: DefaultValue[A],
    renamesAnn: RepeatableAnnotations[ColumnName, A],
    tableNameAnn: RepeatableAnnotation[TableName, A],
    splitAnn: RepeatableAnnotation[SplitTable, A]
  ): Table[A] = {
    val defaults = defaultValue.defaults
    val columnTypes =
      summonAll[Tuple.Map[gen.MirroredElemTypes, TField]].toArray.toList
        .asInstanceOf[List[TField[_]]]
    val customTableName = tableNameAnn()
    val split           = splitAnn()
    val splitTo = if (split.nonEmpty) { Some(split.head.splitTo) }
    else None

    val tName = if (customTableName.isEmpty) {
      labelling.label
    } else {
      customTableName.head.name
    }

    val primaryKey = primary().filter(_.nonEmpty)
    if (primaryKey.length > 1) {
      throw Exception(s"more than 1 primary key in table $tName")
    }
    val isPrimaryKey = primary().map(_.nonEmpty)

    // val tName           = labelling.label
    // scala name
    val scalaFieldNames = labelling.elemLabels.toVector
    // db name
    val renames = renamesAnn().map { col =>
      if (col.isEmpty) {
        None
      } else {
        Some(col.head.name)
      }
    }.toVector

    val dbColNames = renames.zip(scalaFieldNames).map {
      case (rename, oriName) => {
        rename.getOrElse(oriName)
      }
    }
    // 仍然保存原始scala name
    // val dbColNames = scalaFieldNames

    val uniques = unique().map(item => if (item.isEmpty) false else true)

    val len = length().map(item => if (item.isEmpty) None else Some(item(0).l))
    val prec =
      precision().map(item => if (item.isEmpty) None else Some(item(0)))

    val idxes = index().zipWithIndex
      .filter(!_._1.isEmpty)
      .map { item =>
        item._1.map(i =>
          (
            i.copy(priority = if (i.priority != 0) i.priority else item._2),
            dbColNames(item._2)
          )
        )
      }
      .flatten

    val groupedIdx = idxes
      .groupBy(item => item._1.name)
      .map {
        case (name, items) => {
          Idx(name, items.map(_._2).toVector, items(0)._1.unique)
        }
      }
      .toVector

    val cols = scalaFieldNames.zipWithIndex.map {
      case (name, index) => {
        val tp       = columnTypes(index)
        val unique   = uniques(index)
        val default  = defaults.get(name)
        val typeLen  = len(index)
        val typePrec = prec(index)
        val pk       = isPrimaryKey(index)

        Field(
          index,
          tName,
          name,
          pk,
          dbColNames(index),
          unique,
          default,
          typeLen,
          typePrec,
          tp
        )
      }
    }

    val result = new Table[A] {
      def splitCount: Option[Int] = splitTo

      def tableName = tName

      def indexes = groupedIdx
      def columns = cols.toVector
    }
    result
  }

  transparent inline def apply[T <: Product] =
    ${ queryImpl[T] }

  private def queryImpl[T <: Product: Type](using Quotes): Expr[Any] = {
    import quotes.reflect.*

    def recur[mels: Type, mets: Type](baseType: TypeRepr): TypeRepr =
      Type.of[mels] match
        case '[mel *: melTail] => {
          Type.of[mets] match {
            case '[head *: tail] => {
              val label = Type.valueOfConstant[mel].get.toString
              val withField =
                Refinement(
                  baseType,
                  "field_" + label,
                  TypeRepr.of[jooq.Field[head]]
                )
              recur[melTail, tail](withField)
            }
          }
        }
        case '[EmptyTuple] => baseType
    Expr.summon[Mirror.ProductOf[T]].get match {
      case '{
            $m: Mirror.ProductOf[T] {
              type MirroredElemLabels = mels; type MirroredElemTypes = mets
            }
          } =>
        val tableType = recur[mels, mets](TypeRepr.of[Table[T]])

        tableType.asType match {
          case '[tpe] =>
            '{
              val table = summonInline[Table[T]]
              table.asInstanceOf[tpe]
            }
        }
      case e => report.error(e.show); ???
    }
  }
}

def getTable[T <: Product: Table](key: Long): jooq.Table[org.jooq.Record] = {
  val t     = summon[Table[T]]
  val split = t.splitCount.get
  val table = jooq.impl.DSL.table(s"${t.tableName}_${key % split}")
  table
}
