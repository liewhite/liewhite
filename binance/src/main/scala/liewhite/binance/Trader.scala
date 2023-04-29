import zio.stream.ZStream

/*
 * 
 * 事件流的形式进行处理
 * 整个策略有两条流， 一个是因子和行情数据流
 * 一个是事件流， 事件流包括一系列系统事件（初始化完毕， 网络异常事件等）和账户事件（余额变动， 订单状态更新等）
 * 
 * 
 *
 */

enum Side {
    case BUY
    case SELL
}

enum AccountEvent {
    case OrderUpdate(ts: Long, symbol: String, side: Side)
}

trait Trader {
    def eventStream(): ZStream[Any, Throwable, AccountEvent]
}
