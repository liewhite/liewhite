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


export zio.schema.Schema
export zio.schema.derived

extension [T:Schema](s: T) {
  def toJson = JsonCodec.JsonEncoder.encode(summon[Schema[T]], s)
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

given Schema[Json] = dynamicSchema.transformOrFail(
  a => {
    zio.json
      .JsonDecoder[Json]
      .decodeJson(zio.schema.codec.JsonCodec.JsonEncoder.encode(dynamicSchema, a).asString)
  },
  b => {
    val str = zio.json.JsonEncoder[Json].encodeJson(b).toString()
    val s   = zio.schema.codec.JsonCodec.JsonDecoder.decode(dynamicSchema, str.toString()).left.map(_.toString())
    s
  }
)
