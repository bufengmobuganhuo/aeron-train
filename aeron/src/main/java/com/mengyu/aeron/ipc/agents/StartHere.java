package com.mengyu.aeron.ipc.agents;

import io.aeron.Aeron;
import io.aeron.Publication;
import io.aeron.Subscription;
import io.aeron.driver.MediaDriver;
import io.aeron.driver.ThreadingMode;
import lombok.extern.slf4j.Slf4j;
import org.agrona.concurrent.AgentRunner;
import org.agrona.concurrent.BusySpinIdleStrategy;
import org.agrona.concurrent.IdleStrategy;
import org.agrona.concurrent.ShutdownSignalBarrier;

/**
 * @author yu zhang
 */
@Slf4j
public class StartHere {
    public static void main(String[] args) {
        final String channel = "aeron:ipc";
        final int stream = 10;
        final int sendCount = 10_000_000;
        final IdleStrategy idleStrategySend = new BusySpinIdleStrategy();
        final IdleStrategy idleStrategyReceive = new BusySpinIdleStrategy();
        final ShutdownSignalBarrier barrier = new ShutdownSignalBarrier();

        final MediaDriver.Context ctx = new MediaDriver.Context()
                .dirDeleteOnStart(true)
                .threadingMode(ThreadingMode.SHARED)
                .sharedIdleStrategy(new BusySpinIdleStrategy())
                .dirDeleteOnShutdown(true);
        final MediaDriver driver = MediaDriver.launch(ctx);

        final Aeron.Context aeronCtx = new Aeron.Context().aeronDirectoryName(driver.aeronDirectoryName());
        final Aeron aeron = Aeron.connect(aeronCtx);

        log.info("dir {}", driver.aeronDirectoryName());

        final Subscription subscription = aeron.addSubscription(channel, stream);
        final Publication publication = aeron.addPublication(channel, stream);

        // 创建agents
        final SendAgent sendAgent = new SendAgent(publication, sendCount);
        final ReceiveAgent receiveAgent = new ReceiveAgent(subscription,barrier,sendCount);

        final AgentRunner sendAgentRunner = new AgentRunner(idleStrategySend, Throwable::printStackTrace, null, sendAgent);
        final AgentRunner receiveAgentRunner = new AgentRunner(idleStrategyReceive, Throwable::printStackTrace, null,
                receiveAgent);
        log.info("starting");
        // 在不同线程上启动runner
        AgentRunner.startOnThread(sendAgentRunner);
        AgentRunner.startOnThread(receiveAgentRunner);

        // 等待最后一个元素被接收到
        barrier.await();

        receiveAgentRunner.close();
        sendAgentRunner.close();
        aeron.close();
        driver.close();
    }
}
