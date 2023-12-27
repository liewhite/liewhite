package liewhite.json

import zio.schema.*
import zio.schema.DeriveSchema
import zio.json.ast.Json
import zio.schema.codec.JsonCodec
import zio.schema.codec.DecodeError
import zio.Chunk
import zio.json.JsonDecoder
import zio.schema.codec.JsonCodec.JsonEncoder
import zio.schema.codec.JsonCodec.JsonDecoder
import zio.schema.annotation.*
import org.apache.commons.codec.binary.Hex
import scala.util.Try
import liewhite.common.union.*
import scala.reflect.TypeTest

export zio.schema.Schema
export zio.schema.derived
export zio.json.ast.Json

extension [T: Schema](s: T) {
  def toJson = JsonCodec.JsonEncoder.encode(summon[Schema[T]], s, JsonCodec.Config.default)
  def toJsonAst =
    JsonCodec.JsonEncoder.encode(summon[Schema[T]], s, JsonCodec.Config.default).fromJson[Json].toOption.get
}

extension (s: String) {
  def fromJson[T](using schema: Schema[T]): Either[DecodeError, T] = JsonCodec.JsonDecoder.decode(schema, s)
}
extension (s: Array[Byte]) {
  def fromJson[T](using schema: Schema[T]): Either[DecodeError, T] = JsonCodec.JsonDecoder.decode(schema, String(s))
}
extension (s: CharSequence) {
  def fromJson[T](using schema: Schema[T]): Either[DecodeError, T] = JsonCodec.JsonDecoder.decode(schema, s.toString())
}
extension (s: Chunk[Byte]) {
  def fromJson[T](using schema: Schema[T]): Either[DecodeError, T] = JsonCodec.JsonDecoder.decode(schema, s.asString)
}

// 必须要directDynamicMapping才能把dynamicValue映射到json
// 不然默认按照enum进行decode
/**
 * if (directMapping) { Json.decoder.map(jsonToDynamicValue) } else {
 * schemaDecoder(DynamicValue.schema) }
 */
val dynamicSchema = Schema.dynamicValue.annotate(directDynamicMapping())

// given [T: Schema]: Schema[Tuple1[T]] = Schema[Seq[T]].transform(f => {}, g => {})
given Schema[Json] = dynamicSchema.transformOrFail(
  a => {
    zio.json
      .JsonDecoder[Json]
      .decodeJson(zio.schema.codec.JsonCodec.JsonEncoder.encode(dynamicSchema, a, JsonCodec.Config.default).asString)
  },
  b => {
    val str = zio.json.JsonEncoder[Json].encodeJson(b).toString()
    val s   = zio.schema.codec.JsonCodec.JsonDecoder.decode(dynamicSchema, str.toString()).left.map(_.toString())
    s
  }
)

given Schema[Array[Byte]] = Schema[String].transformOrFail(
  a => {
    Try {
      Hex.decodeHex(a.stripPrefix("0x"))
    }.toEither.left.map(_.getMessage())
  },
  b => {
    Right("0x" + Hex.encodeHexString(b))
  }
)
extension (j: Json) {
  def asType[T](using s: Schema[T]) = j.as[T](using JsonCodec.jsonDecoder(s))
}

given [T](using l: Schema[List[T]]): Schema[Seq[T]] = l.transform(l => l.toSeq, s => s.toList)

given [L: Schema, R: Schema]: Schema[Either[L, R]] = Schema[Json].transformOrFail(
  j => {
    j.asType[L] match
      case Left(value)  => j.asType[R].map(Right(_))
      case Right(value) => Right(Left(value))
  },
  lr => {
    lr match
      case Left(value)  => Right(value.toJsonAst)
      case Right(value) => Right(value.toJsonAst)
  }
)

// inline given derivedUnion[A](using IsUnion[A]): Schema[A] = UnionDerivation.derive[Schema, A]
