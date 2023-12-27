package liewhite.json

import zio.schema.annotation.noDiscriminator
import zio.json.ast.Json
import zio.schema.DeriveSchema
import zio.schema.codec.JsonCodec
import zio.schema.Schema.Sequence
import zio.schema.Schema.Transform
import zio.schema.Schema.Primitive
import zio.schema.Schema.Optional
import zio.schema.Schema.Fail
import zio.schema.Schema.Lazy
import zio.schema.Schema.Enum1
import zio.schema.Schema.Enum2
import zio.schema.Schema.Enum3
import zio.schema.Schema.Enum4
import zio.schema.Schema.Enum5
import zio.schema.Schema.Enum6
import zio.schema.Schema.Enum7
import zio.schema.Schema.Enum8
import zio.schema.Schema.Enum9
import zio.schema.Schema.Enum10
import zio.schema.Schema.Enum11
import zio.schema.Schema.Enum12
import zio.schema.Schema.Enum13
import zio.schema.Schema.Enum14
import zio.schema.Schema.Enum15
import zio.schema.Schema.Enum16
import zio.schema.Schema.Enum17
import zio.schema.Schema.Enum18
import zio.schema.Schema.Enum19
import zio.schema.Schema.Enum20
import zio.schema.Schema.Enum21
import zio.schema.Schema.Enum22
import zio.schema.Schema.EnumN
import zio.schema.Schema.GenericRecord
import zio.schema.Schema.CaseClass3
import zio.schema.Schema.CaseClass5

case class A[T](a: Int , b :String, c: Option[T] = None) derives Schema

@noDiscriminator()
enum E derives Schema{
    case E1(a: Int)
    case E2(b: String)
}

@noDiscriminator()
enum Constant derives Schema{
    case C1
    case C2
}

// object E{
//     given Schema[E.E1] = DeriveSchema.gen[E.E1]
//     given Schema[E.E2] = DeriveSchema.gen[E.E2]
// }

case class X(m: Map[String, String]) derives Schema

case class Y() derives Schema
case class XX(a:Int = 100,b:Int, c: Int = 200, d: Int, e: Int = 300) derives Schema
case class WithDefaultValue(orderId: Int,  description: String = "desc")
object WithDefaultValue {
    implicit lazy val schema: Schema[WithDefaultValue] = DeriveSchema.gen[WithDefaultValue]
}

@main def main = {
    Schema[Tuple2[Int,String]]
    // println(Tuple(1).toJson)
    

//    println("""{"a":1,"b": "xx"}""".fromJson[A[Boolean]].toOption.get.toJson.asString)
//    println("""{"b":"asd"}""".fromJson[E])
//    println("""{"m": {}}""".fromJson[X])
//    println("{}".fromJson[Json].toOption.get.asType[Y])
//    println("\"C1\"".fromJson[Constant])

//    println(Vector(Json.Num(1.1),Json.Bool(false)).toJson.asString)
//    println(A[Boolean](1,"asd").toJson)
//    println((E.E1(1):E).toJson.asString)
//    println(X(Map.empty).toJson.asString)
//    println(Y().toJson.asString)
//    println((Constant.C1:Constant).toJson.asString)
}