package liewhite.json

import zio.schema.DeriveSchema

case class A[T](a: Int , b :String, c: Option[T] = None) derives Schema

enum E derives Schema{
    case E1(a: Int)
    case E2(b: String)
}

object E{
    given Schema[E.E1] = DeriveSchema.gen[E.E1]
    given Schema[E.E2] = DeriveSchema.gen[E.E2]
}

@main def main = {
   println("""{"a":1,"b": "xx"}""".fromJson[A[Boolean]].toOption.get.toJson.asString)
   println("""{"a":1}""".fromJson[E.E1])
}