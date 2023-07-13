package liewhite.ethers.abi

import scala.quoted.*
import liewhite.json.{*, given}
import org.web3j.abi.FunctionEncoder
import scala.jdk.CollectionConverters.*
import org.web3j.abi.DefaultFunctionEncoder
import liewhite.ethers.toBytes
import liewhite.ethers.abi.types.{ABIStruct, ABIStaticArray}
import org.web3j.abi.datatypes.AbiTypes
import org.web3j.abi.datatypes.StaticArray

class Functions(functions: Map[String, ABIFunction[_, _]]) extends Selectable {
  def selectDynamic(name: String): Any =
    functions(name)
}

class Events(events: Map[String, ABIEvent[_]]) extends Selectable {
  def selectDynamic(name: String): Any =
    events(name)
}

class Contract[FS, ES](val functions: FS, val events: ES) {}

object Contract {
  transparent inline def apply(inline abiDef: String) =
    ${ impl('abiDef) }

  private def impl(abiExpr: Expr[String])(using q: Quotes): Expr[Any] = {
    // 解析ABI， 生成 Functions和Events， 然后构建出Contract
    import q.reflect.*

    def makeTypeFromInput(in: Input): TypeRepr =
      if (in.`type` == "tuple") {
        TypeRepr.of[ABIStruct].appliedTo(makeType(in.components.get))
      } else if (in.`type`.endsWith("[]")) {
        TypeRepr.of[java.util.List].appliedTo(makeTypeFromInput(in.copy(`type` = in.`type`.dropRight(2))))
      } else {
        TypeRepr.typeConstructorOf(AbiTypes.getType(in.`type`))
      }

    def makeType(item: Seq[Input]): TypeRepr = {
      val types = item.map { in =>
        makeTypeFromInput(in)
      }
      val result =
        types.reverse.foldLeft(TypeRepr.of[EmptyTuple])((acc, item) => TypeRepr.of[*:].appliedTo(List(item,acc)))
      result
      // inputType.asType match
      //   case '[t] => TypeRepr.of[ABIFunction[t, ]]
    }

    def makeEventType(item: ABIItem): TypeRepr =
      TypeRepr.of[Int]

    def makeFunctionsType(functions: Seq[ABIItem]): TypeRepr = {
      val baseType = TypeRepr.of[Functions]

      val withFs = functions.foldLeft(baseType) { (result, item) =>
        val inputType  = makeType(item.inputs)
        val outputType = makeType(item.outputs.getOrElse(Seq.empty))
        (inputType.asType, outputType.asType) match {
          case ('[i], '[o]) => {
            Refinement(
              result,
              item.name.get,
              TypeRepr.of[ABIFunction[i, o]]
            )
          }
        }
      }
      withFs
    }
    def makeEventsType(events: Seq[ABIItem]): TypeRepr = {
      val baseType = TypeRepr.of[Events]

      val withFs = events.foldLeft(baseType) { (result, item) =>
        Refinement(
          result,
          item.name.get,
          makeEventType(item)
        )
      }
      withFs
    }

    val abiValue = abiExpr.valueOrAbort
    val a        = abiValue.fromJson[Seq[ABIItem]].toOption.get
    val fs       = a.filter(_.`type` == ABIItemType.function)
    val es       = a.filter(_.`type` == ABIItemType.event)

    (makeFunctionsType(fs).asType, makeEventsType(es).asType) match {
      case ('[f], '[e]) => {
        '{
          val defs     = ${ Expr(abiValue) }
          val a        = defs.fromJson[Seq[ABIItem]].toOption.get
          val fs       = a.filter(_.`type` == ABIItemType.function)
          val es       = a.filter(_.`type` == ABIItemType.event)
          val contract = makeContract(fs, es)
          contract.asInstanceOf[Contract[f, e]]
        }
      }
    }
  }

  def makeContract(abifs: Seq[ABIItem], abies: Seq[ABIItem]): Contract[Any, Any] = {
    val fs = makeFunctions(abifs)
    val es = makeEvents(abies)
    new Contract(fs, es)
  }

  def makeFunctions(fs: Seq[ABIItem]): Functions = {
    val f = fs.map(item => item.name.get -> makeFunction(item)).toMap
    new Functions(f)
  }

  def makeEvents(es: Seq[ABIItem]): Events = {
    val f = es.map(item => item.name.get -> makeEvent(item)).toMap
    new Events(f)
  }

  def makeFunction(item: ABIItem): ABIFunction[_, _] =
    new ABIFunction[Tuple, _] {
      def encode(in: Tuple): Array[Byte] =
        val f = FunctionEncoder.makeFunction(
          item.name.get,
          item.inputs.map(_.`type`).asJava,
          in.toArray.toList.asJava,
          item.outputs.map(_.map(_.`type`).asJava).getOrElse(java.util.ArrayList())
        )
        DefaultFunctionEncoder().encodeFunction(f).toBytes
    }

  def makeEvent(item: ABIItem): ABIEvent[_] = new ABIEvent[Int] {}
}
