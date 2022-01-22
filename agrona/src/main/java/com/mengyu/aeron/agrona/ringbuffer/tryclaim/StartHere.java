package com.mengyu.aeron.agrona.ringbuffer.tryclaim;

import lombok.extern.slf4j.Slf4j;
import org.agrona.concurrent.*;
import org.agrona.concurrent.ringbuffer.OneToOneRingBuffer;
import org.agrona.concurrent.ringbuffer.RingBufferDescriptor;

import java.nio.ByteBuffer;

/**
 * @author yu zhang
 */
@Slf4j
public class StartHere {
    public static void main(String[] args) {
        final int sendCount = 18_000_000;
        final int bufferLength = 16384 + RingBufferDescriptor.TRAILER_LENGTH;
        final UnsafeBuffer unsafeBuffer = new UnsafeBuffer(ByteBuffer.allocateDirect(bufferLength));
        // 空闲策略
        final IdleStrategy idleStrategySend = new BusySpinIdleStrategy();
        final IdleStrategy idleStrategyReceive = new BusySpinIdleStrategy();
        final ShutdownSignalBarrier barrier = new ShutdownSignalBarrier();
        final OneToOneRingBuffer ringBuffer = new OneToOneRingBuffer(unsafeBuffer);

        // 构建agents
        final SendAgent sendAgent = new SendAgent(sendCount, ringBuffer);
        final ReceiveAgent receiveAgent = new ReceiveAgent(barrier, ringBuffer, sendCount);

        // 构建agents runner
        final AgentRunner sendAgentRunner = new AgentRunner(idleStrategySend, Throwable::printStackTrace, null,
            sendAgent);
        final AgentRunner receiveAgentRunner = new AgentRunner(idleStrategyReceive, Throwable::printStackTrace, null,
            receiveAgent);

        log.info("starting");

        // 开启runners
        AgentRunner.startOnThread(sendAgentRunner);
        AgentRunner.startOnThread(receiveAgentRunner);

        // 等待，直到最后一个元素被消费到
        barrier.await();

        // 关闭资源
        receiveAgentRunner.close();
        sendAgentRunner.close();
    }
}
