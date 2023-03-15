package io.github.liewhite.sqlx

import java.sql.ResultSet
import java.sql.PreparedStatement
import java.sql.SQLException
import org.jooq.*
import org.jooq
import org.jooq.impl.BuiltInDataType
import org.jooq.impl.SQLDataType
import java.time.LocalDateTime
import java.time.ZonedDateTime
import scala.deriving.Mirror
import java.{util => ju}
import java.lang
import java.time.Instant
import java.time.ZoneId
import java.math.BigInteger
import io.github.liewhite.sqlx.Precision
import scala.reflect.ClassTag
import org.jooq.impl.DefaultDataType

case class Field[T](
    index: Int,
    modelName: String,
    // scala case class field name
    fieldName: String,
    // database table name
    primaryKey: Boolean,
    colName: String,
    unique: Boolean,
    default: Option[Any],
    length: Option[Int],
    precision: Option[Precision],
    t: TField[T]) {

  def field: jooq.Field[Object] = {
    jooq.impl.DSL.field(fullColName)
  }

  def fullColName: String = Vector(modelName, colName).mkString(".")
  def getValue[A <: Product: Mirror.ProductOf](o: A): T = {
    val value = Tuple.fromProductTyped(o).toList.apply(index).asInstanceOf[T]
    value
  }

  def uniqueKeyName: String = "uk:" + colName

  def getDataType: DataType[Any] = {
    var datatype = t.dataType.asInstanceOf[DataType[Any]]
    if (primaryKey) {
      datatype = datatype.nullable(false).identity(true)
    } else {
      if (default.isDefined) {
        val dv = t.dataType.getConverter().to(default.get.asInstanceOf)
        datatype = datatype.defaultValue(dv)
      }
    }

    length.foreach(l => {
      datatype = datatype.length(l)
    })

    precision.foreach(p => {
      datatype = datatype.precision(p.precision, p.scale)
    })

    datatype
  }
}

trait TField[T] {
  // option type with true
  def nullable: Boolean     = false
  // jooq datatype
  def dataType: DataType[T] = innerDataType.nullable(nullable)
  def innerDataType: DataType[T]
}

object TField {

  given TField[Int] with {
    def innerDataType: DataType[Int] =
      SQLDataType.INTEGER.asConvertedDataType(new Converter[Integer, Int] {

        override def from(databaseObject: Integer): Int = {
          databaseObject
        }

        override def to(userObject: Int): Integer = {
          userObject
        }

        override def fromType(): Class[Integer] = classOf[Integer]

        override def toType(): Class[Int] = classOf[Int]

      })
  }

  given TField[Long] with {
    def innerDataType: DataType[Long] = SQLDataType.BIGINT.asConvertedDataType(
      new Converter[java.lang.Long, Long] {

        override def from(databaseObject: java.lang.Long): Long = {
          databaseObject
        }

        override def to(userObject: Long): java.lang.Long = {
          userObject
        }

        override def fromType(): Class[java.lang.Long] = classOf[java.lang.Long]

        override def toType(): Class[Long] = classOf[Long]

      }
    )
  }

  given TField[Float] with {
    def innerDataType: DataType[Float] = SQLDataType.REAL.asConvertedDataType(
      new Converter[java.lang.Float, Float] {

        override def from(databaseObject: java.lang.Float): Float =
          databaseObject

        override def to(userObject: Float): java.lang.Float = userObject

        override def fromType(): Class[java.lang.Float] =
          classOf[java.lang.Float]

        override def toType(): Class[Float] = classOf[Float]

      }
    )
  }

  given TField[Double] with {
    def innerDataType: DataType[Double] = SQLDataType.FLOAT.asConvertedDataType(
      new Converter[java.lang.Double, Double] {

        override def from(databaseObject: java.lang.Double): Double =
          databaseObject

        override def to(userObject: Double): java.lang.Double = userObject

        override def fromType(): Class[java.lang.Double] =
          classOf[java.lang.Double]

        override def toType(): Class[Double] = classOf[Double]

      }
    )
  }

  given TField[String] with {
    def innerDataType: DataType[String] = SQLDataType.VARCHAR(255)
  }

  given TField[Boolean] with {
    def innerDataType: DataType[Boolean] =
      SQLDataType.BOOLEAN.asConvertedDataType(
        new Converter[java.lang.Boolean, Boolean] {

          override def from(databaseObject: lang.Boolean): Boolean = {
            databaseObject
          }

          override def to(userObject: Boolean): lang.Boolean = {
            userObject
          }

          override def fromType(): Class[lang.Boolean] = classOf[lang.Boolean]

          override def toType(): Class[Boolean] = classOf[Boolean]

        }
      )
  }

  given TField[BigInt] with {
    def innerDataType: DataType[BigInt] = SQLDataType
      .DECIMAL_INTEGER(65)
      .asConvertedDataType(new Converter[java.math.BigInteger, BigInt] {

        override def from(databaseObject: BigInteger): BigInt = databaseObject

        override def to(userObject: BigInt): BigInteger = userObject.bigInteger

        override def fromType(): Class[BigInteger] = classOf[BigInteger]

        override def toType(): Class[BigInt] = classOf[BigInt]

      })
  }

  given TField[BigDecimal] with {
    def innerDataType: DataType[BigDecimal] = SQLDataType
      .NUMERIC(65, 10)
      .asConvertedDataType(new Converter[java.math.BigDecimal, BigDecimal] {

        override def from(databaseObject: java.math.BigDecimal): BigDecimal = {
          databaseObject
        }

        override def to(userObject: BigDecimal): java.math.BigDecimal = {
          userObject.bigDecimal
        }

        override def fromType(): Class[java.math.BigDecimal] =
          classOf[java.math.BigDecimal]

        override def toType(): Class[BigDecimal] = classOf[BigDecimal]
      })
  }

  given TField[ZonedDateTime] with {
    def innerDataType: DataType[ZonedDateTime] =
      SQLDataType.BIGINT.asConvertedDataType(
        new Converter[java.lang.Long, ZonedDateTime] {

          override def from(databaseObject: lang.Long): ZonedDateTime = {
            ZonedDateTime.ofInstant(
              Instant.ofEpochMilli(databaseObject),
              ZoneId.systemDefault()
            )
          }

          override def to(userObject: ZonedDateTime): lang.Long = {
            userObject.toInstant().toEpochMilli()
          }

          override def fromType(): Class[lang.Long] = classOf[lang.Long]

          override def toType(): Class[ZonedDateTime] = classOf[ZonedDateTime]

        }
      )
  }

  given TField[ju.Date] with {
    def innerDataType: DataType[ju.Date] =
      SQLDataType.BIGINT.asConvertedDataType(
        new Converter[java.lang.Long, ju.Date] {

          override def from(databaseObject: lang.Long): ju.Date = {
            ju.Date.from(Instant.ofEpochMilli(databaseObject))
          }

          override def to(userObject: ju.Date): lang.Long = {
            userObject.toInstant().toEpochMilli()
          }

          override def fromType(): Class[lang.Long] = classOf[java.lang.Long]

          override def toType(): Class[ju.Date] = classOf[ju.Date]

        }
      )
  }

  given TField[Array[Byte]] with {
    def innerDataType: DataType[Array[Byte]] = SQLDataType.BLOB
  }

  // trick on option
  // jooq不支持子类型Convert
  // jooq 内部通过class作为key来查找DataType, 注册Option会查找不到Some和None， 所以都注册一遍
  given opt[T](using t: TField[T]): TField[Option[T]] with {
    override def nullable: Boolean         = true
    def innerDataType: DataType[Option[T]] = {
      t.dataType
        .asConvertedDataType(new Converter[T, Option[T]] {
          override def from(databaseObject: T): Option[T] = {
            if (databaseObject != null) {
              Some(t.dataType.getConverter().from(databaseObject.asInstanceOf))
            } else {
              None
            }
          }
          override def to(userObject: Option[T]): T       = {
            val result = userObject match
              case None        => null
              case Some(value) =>
                t.dataType.getConverter().to(value).asInstanceOf[T]
            result.asInstanceOf[T]
          }

          override def fromType(): Class[T] = {
            val ft = t.dataType.getConverter().fromType().asInstanceOf[Class[T]]
            ft
          }

          override def toType(): Class[Option[T]] = {
            classOf[Some[T]].asInstanceOf[Class[Option[T]]]
          }
        })
        .nullable(true)
      t.dataType
        .asConvertedDataType(new Converter[T, Option[T]] {
          override def from(databaseObject: T): Option[T] = {
            if (databaseObject != null) {
              Some(t.dataType.getConverter().from(databaseObject.asInstanceOf))
            } else {
              None
            }
          }
          override def to(userObject: Option[T]): T       = {
            val result = userObject match
              case None        => null
              case Some(value) =>
                t.dataType.getConverter().to(value).asInstanceOf[T]
            result.asInstanceOf[T]
          }

          override def fromType(): Class[T] = {
            val ft = t.dataType.getConverter().fromType().asInstanceOf[Class[T]]
            ft
          }

          override def toType(): Class[Option[T]] = {
            classOf[None.type].asInstanceOf[Class[Option[T]]]
          }
        })
        .nullable(true)

      val result = t.dataType
        .asConvertedDataType(new Converter[T, Option[T]] {
          override def from(databaseObject: T): Option[T] = {
            if (databaseObject != null) {
              Some(t.dataType.getConverter().from(databaseObject.asInstanceOf))
            } else {
              None
            }
          }
          override def to(userObject: Option[T]): T       = {
            val result = userObject match
              case None        => null
              case Some(value) =>
                t.dataType.getConverter().to(value).asInstanceOf[T]
            result.asInstanceOf[T]
          }

          override def fromType(): Class[T] = {
            val ft = t.dataType.getConverter().fromType().asInstanceOf[Class[T]]
            ft
          }

          override def toType(): Class[Option[T]] = {
            classOf[Option[T]].asInstanceOf[Class[Option[T]]]
          }
        })
        .nullable(true)
      // todo jooq 不支持子类型, 所以Some <: Option 这种就会找不到type
      result
    }
  }

  /** java 基本类型
    */
  given TField[Integer] with {
    def innerDataType: DataType[Integer] = SQLDataType.INTEGER
  }
  given jlong: TField[java.lang.Long] with {
    def innerDataType: DataType[java.lang.Long] = SQLDataType.BIGINT
  }
  given jbool: TField[java.lang.Boolean] with {
    def innerDataType: DataType[java.lang.Boolean] = SQLDataType.BOOLEAN
  }
  given jfloat: TField[java.lang.Float] with {
    def innerDataType: DataType[java.lang.Float] = SQLDataType.REAL
  }
  given jdouble: TField[java.lang.Double] with {
    def innerDataType: DataType[java.lang.Double] = SQLDataType.FLOAT
  }
}
