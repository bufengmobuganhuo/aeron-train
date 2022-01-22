package com.mengyu.aeron.ipc;

import io.aeron.Aeron;
import io.aeron.Publication;
import io.aeron.Subscription;
import io.aeron.driver.MediaDriver;
import io.aeron.logbuffer.FragmentHandler;
import lombok.extern.slf4j.Slf4j;
import org.agrona.concurrent.IdleStrategy;
import org.agrona.concurrent.SleepingIdleStrategy;
import org.agrona.concurrent.UnsafeBuffer;

import java.nio.ByteBuffer;

/**
 * @author yu zhang
 */
@Slf4j
public class SimpleCase {
    public static void main(String[] args) {

        final String channel = "aeron:ipc";
        final String message = "my message";

        final IdleStrategy idleStrategy = new SleepingIdleStrategy();
        // 对外内存，保存要发送的消息
        final UnsafeBuffer unsafeBuffer = new UnsafeBuffer(ByteBuffer.allocate(256));
        // MediaDriver：负责所有IPC（进程间通信）和网络活动
        try (MediaDriver mediaDriver = MediaDriver.launch();
             Aeron aeron = Aeron.connect();
             // 拉取消息
             Subscription subscription = aeron.addSubscription(channel, 10);
             // 发送消息
             Publication publication = aeron.addPublication(channel, 10);
        ){
            // 等待，直到subscription已经达到connected状态，在Subscription准备好前，Publication无法发送数据
            while (!publication.isConnected()) {
                idleStrategy.idle();
            }

            unsafeBuffer.putStringAscii(0, message);
            log.info("sending:{}", message);
            // offer方法是非阻塞的，因此需要轮询；关于offer方法的返回可以参考幕布
            while (publication.offer(unsafeBuffer) < 0) {
                idleStrategy.idle();
            }
            // buffer最好保证是read only的，同时最好在onFragment()方法中一次性取出所有数据
            FragmentHandler handler = (buffer, offset, length, header) -> log.info("received:{}",
                buffer.getStringAscii(offset));
            // 拉取消息，如果<=0说明还没收到消息
            while (subscription.poll(handler, 1) <= 0) {
                idleStrategy.idle();
            }
        }
    }
}
