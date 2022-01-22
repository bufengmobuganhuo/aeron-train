package com.mengyu.aeron.ipc.agents;

import io.aeron.Subscription;
import io.aeron.logbuffer.Header;
import lombok.extern.slf4j.Slf4j;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.Agent;
import org.agrona.concurrent.ShutdownSignalBarrier;

/**
 * @author yu zhang
 */
@Slf4j
public class ReceiveAgent implements Agent {
    private final Subscription subscription;

    private final ShutdownSignalBarrier barrier;

    private final int sendCount;

    public ReceiveAgent(Subscription subscription, ShutdownSignalBarrier barrier, int sendCount) {
        this.subscription = subscription;
        this.barrier = barrier;
        this.sendCount = sendCount;
    }

    @Override
    public int doWork() throws Exception {
        subscription.poll(this::handler, 100);
        return 0;
    }

    @Override
    public String roleName() {
        return "receiver";
    }

    private void handler(DirectBuffer buffer, int offset, int length, Header header) {
        final int lastValue = buffer.getInt(offset);
        // 如果接收到的元素值比要发送的元素个数大（因为发送的元素都是1，2，3，4。。。），则说明发送完成
        if (lastValue >= sendCount) {
            log.info("received: {}", lastValue);
            // 让barrier.await()执行下去，startHere.java结束
            barrier.signal();
        }
    }
}
