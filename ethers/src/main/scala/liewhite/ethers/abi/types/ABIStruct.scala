package liewhite.ethers.abi.types

import org.web3j.abi.datatypes.StaticStruct
import org.web3j.abi.datatypes.Type
import scala.jdk.CollectionConverters.*

// export org.web3j.abi.datatypes

// web3j的struct泛型信息不足, 使用ABIStruct代替
class ABIStruct[T <: Tuple](ts: T)(using ev: Tuple.Union[T] <:< Type[_])
    extends StaticStruct(ts.toArray.toList.asInstanceOf[List[Type[_]]].asJava) {}
