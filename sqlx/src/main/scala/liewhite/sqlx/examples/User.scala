package liewhite.sqlx.examples

import zio.*
import liewhite.sqlx.Table
import liewhite.json.*
import java.sql.SQLException
import scala.util.Try
import liewhite.sqlx.DBDataSource
import liewhite.sqlx.DBConfig
import javax.sql.DataSource
import liewhite.sqlx.TField

import liewhite.sqlx.*
import java.time.ZonedDateTime

case class Detail(email: String) derives Schema

// object Detail {
//   given TField[Detail] with {
//     def innerDataType: DataType[Detail] =
//       SQLDataType.VARCHAR.asConvertedDataType(new Converter[String, Detail] {

//         override def from(databaseObject: String): Detail =
//           databaseObject.fromJson[Detail].toOption.get

//         override def to(userObject: Detail): String =
//           userObject.toJson.asString

//         override def fromType(): Class[String] = classOf[String]

//         override def toType(): Class[Detail] = classOf[Detail]

//       })
//   }
// }
enum EE derives Schema{
  case EE1
  case EE2
  case EE3
}


case class O(
  ooo: String
) derives Schema


@TableName("split_user")
@SplitTable(5)
case class User(
  @Primary id: Long,
  age: Option[Long],
  o: O,
  e: EE,
  @Index("kakak") kakaka: String,
  @ColumnName("details")
  @Length(100)
  detail: Detail = Detail("xxxx"),
  time: ZonedDateTime = ZonedDateTime.now()
)
