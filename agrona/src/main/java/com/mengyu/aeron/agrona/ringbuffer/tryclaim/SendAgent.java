package com.mengyu.aeron.agrona.ringbuffer.tryclaim;

import lombok.extern.slf4j.Slf4j;
import org.agrona.concurrent.Agent;
import org.agrona.concurrent.AtomicBuffer;
import org.agrona.concurrent.ringbuffer.OneToOneRingBuffer;

/**
 * @author yu zhang
 */
@Slf4j
public class SendAgent implements Agent {

    private final int sendCount;
    private final OneToOneRingBuffer ringBuffer;
    private int currentCountItem = 1;

    public SendAgent(int sendCount, OneToOneRingBuffer ringBuffer) {
        this.sendCount = sendCount;
        this.ringBuffer = ringBuffer;
    }

    @Override
    public int doWork() throws Exception {
        if (currentCountItem > sendCount) {
            return 0;
        }
        // 1. 获取预定义长度的索引，如果>0，可以继续使用这个索引作为buffer.put的offset
        int claimIndex = ringBuffer.tryClaim(1, Integer.BYTES);
        if (claimIndex > 0) {
            currentCountItem += 1;
            // 2. 获取到ring buffer底层的buffer
            final AtomicBuffer buffer = ringBuffer.buffer();
            // 3. 写入数据
            buffer.putInt(claimIndex, currentCountItem);
            // 4. 提交（中止），注意没有像Aeron中的解锁超时，如果没有提交（中止），则消费者无法消费超过第一个未提交/中止生命的point
            ringBuffer.commit(claimIndex);
        }
        return 0;
    }

    @Override
    public String roleName() {
        return "sender";
    }
}
