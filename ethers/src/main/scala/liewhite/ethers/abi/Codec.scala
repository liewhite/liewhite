package liewhite.ethers.abi


object Codec {
    // 根据ABI对function进行encode
    // 其实就是selector + encode
    def encodeFunction(functionABI: ABIItem, params: Seq[ABIValue]): Array[Byte] = {
        ???
    }

    // 根据ABI对params进行encode
    def encode(abi: Seq[Input], params: Seq[ABIValue]): Array[Byte] = {
        if(abi.length != params.length) {
            throw ABIException(s"abi length ${abi.length} != params length ${params.length}")
        }
        val zipped = abi.zip(params)
        zipped.map((abi, param) => {
            abi.`type`

        })
        
        ???

    }

    // 根据ABI对params进行packed encode
    def encodePacked(abi: Seq[Input], params: Seq[ABIValue]): Array[Byte] = ???

}