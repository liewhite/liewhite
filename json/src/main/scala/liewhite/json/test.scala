package liewhite.json

import zio.schema.annotation.noDiscriminator
import zio.json.ast.Json


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
case class WithDefaultValue(orderId: Int,  description: String = "desc") derives Schema
// case class Op(a: Option[Int]) derives Schema

@main def main = {
    println("""{"orderId": 2}""".fromJson[WithDefaultValue])

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