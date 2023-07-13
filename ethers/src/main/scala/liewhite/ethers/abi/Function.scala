package liewhite.ethers.abi


trait ABIFunction[IN,OUT]{
    def encode(in: IN): Array[Byte]
}

trait ABIEvent[IN]{
}
