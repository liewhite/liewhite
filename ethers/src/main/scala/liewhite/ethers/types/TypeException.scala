package liewhite.ethers.types

import liewhite.ethers.EtherException

class TypeException(msg: String) extends EtherException(f"type error: $msg")