package liewhite.ethers.abi.types

import org.web3j.abi.TypeEncoder
import org.web3j.abi.datatypes.Type
import liewhite.ethers.toBytes

def web3jEncode(t: Type[_]) =  
    TypeEncoder.encode(t).toBytes

def web3jEncodePacked(t: Type[_]) =  
    TypeEncoder.encodePacked(t).toBytes