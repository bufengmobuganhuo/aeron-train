package com.mengyu.aeron.agrona.ringbuffer.tryclaim;

import lombok.extern.slf4j.Slf4j;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.Agent;
import org.agrona.concurrent.ShutdownSignalBarrier;
import org.agrona.concurrent.ringbuffer.OneToOneRingBuffer;


/**
 * @author yu zhang
 */
@Slf4j
public class ReceiveAgent implements Agent {
    private final ShutdownSignalBarrier barrier;

    private final OneToOneRingBuffer ringBuffer;

    private final int sendCount;

    public ReceiveAgent(ShutdownSignalBarrier barrier, OneToOneRingBuffer ringBuffer, int sendCount) {
        this.barrier = barrier;
        this.ringBuffer = ringBuffer;
        this.sendCount = sendCount;
    }

    @Override
    public int doWork() throws Exception {
        ringBuffer.read(this::handler);
        return 0;
    }

    @Override
    public String roleName() {
        return "receiver";
    }

    private void handler(int messageType, DirectBuffer buffer, int offset, int length) {
        final int lastValue = buffer.getInt(offset);

        if (lastValue == sendCount) {
            log.info("received: {}", lastValue);
            barrier.signal();
        }
    }
}
