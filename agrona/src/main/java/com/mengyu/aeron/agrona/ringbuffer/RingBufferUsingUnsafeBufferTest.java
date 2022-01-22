package com.mengyu.aeron.agrona.ringbuffer;

import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.ControlledMessageHandler;
import org.agrona.concurrent.MessageHandler;
import org.agrona.concurrent.UnsafeBuffer;
import org.agrona.concurrent.ringbuffer.OneToOneRingBuffer;
import org.agrona.concurrent.ringbuffer.RingBufferDescriptor;
import org.junit.jupiter.api.Assertions;

import java.nio.ByteBuffer;
import java.util.HashSet;

/**
 * @author yu zhang
 */
public class RingBufferUsingUnsafeBufferTest {

    public static void main(String[] args) {
        canSendAndReceiveUsingUnsafeBuffer();
    }

    private static final String testString = "0123456789";

    private static void canSendAndReceiveUsingUnsafeBuffer() {
        // 4096:要存储的内容长度
        // RingBufferDescriptor.TRAILER_LENGTH：为了支持RingBuffer的数据保存在同一个底层缓冲区中（写入RingBuffer的数据会额外添加8byte的header）
        final int bufferLength = 4096 + RingBufferDescriptor.TRAILER_LENGTH;
        final UnsafeBuffer internalBuffer = new UnsafeBuffer(ByteBuffer.allocateDirect(bufferLength));
        final OneToOneRingBuffer ringBuffer = new OneToOneRingBuffer(internalBuffer);
        final MessageCapture capture = new MessageCapture();

        final UnsafeBuffer toSend = new UnsafeBuffer(ByteBuffer.allocateDirect(10));
        toSend.putStringWithoutLengthAscii(0, testString);

        for (int i = 0; i < 10000; i++) {
            for (int k = 0; k < 20; k++) {
                // 写入数据，这种方式是通过ByteBuffer写入的
                final boolean success = ringBuffer.write(1, toSend, 0, 10);
                if (!success)
                {
                    System.err.println("Failed to write!");
                }
            }
            // 指定接收到消息后的handler，有两个选项
            ringBuffer.read(capture, 40);
        }

        Assertions.assertEquals(1, capture.receivedStrings.size());
        Assertions.assertTrue(capture.receivedStrings.contains(testString));
        Assertions.assertEquals(200000, capture.count);
        Assertions.assertNotEquals(0, ringBuffer.consumerPosition());
        Assertions.assertNotEquals(0, ringBuffer.producerPosition());
    }

    static class MessageCapture implements MessageHandler {
        private HashSet<String> receivedStrings = new HashSet<>();
        private int count = 0;

        @Override
        public void onMessage(int msgTypeId, MutableDirectBuffer buffer, int index, int length) {
            receivedStrings.add(buffer.getStringWithoutLengthAscii(index, length));
            count++;
        }
    }

    static class ControlledMessageCapture implements ControlledMessageHandler {
        @Override
        public Action onMessage(int i, MutableDirectBuffer mutableDirectBuffer, int i1, int i2) {
            /**
             * ABORT：中止读操作，下次会重新投递消息
             * BREAK：在本次读取完成后，停止后续的读操作
             * COMMIT：继续读，但是在当前批次结束时提交
             * CONTINUE：继续处理，在当前批次结束时提交（和MessageHandler一样）
             *
             */
            return Action.COMMIT;
        }
    }
}
