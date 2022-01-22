package com.mengyu.aeron.mdc.publication;

import lombok.extern.slf4j.Slf4j;
import org.agrona.CloseHelper;
import org.agrona.concurrent.AgentRunner;
import org.agrona.concurrent.ShutdownSignalBarrier;
import org.agrona.concurrent.SleepingMillisIdleStrategy;

/**
 * @author yu zhang
 */
@Slf4j
public class MultiDestinationPublisher {
    public static void main(String[] args) {
        // publication的host
        final String mdcHost = "localhost";
        // 本地的一个port
        final String controlPort = "8081";

        final int controlChannelPort = Integer.parseInt(controlPort);
        final ShutdownSignalBarrier barrier = new ShutdownSignalBarrier();
        final MultiDestinationPublisherAgent hostAgent = new MultiDestinationPublisherAgent(mdcHost, controlChannelPort);
        final AgentRunner runner = new AgentRunner(new SleepingMillisIdleStrategy(),
                MultiDestinationPublisher::errorHandler, null ,hostAgent);
        AgentRunner.startOnThread(runner);

        barrier.await();
        CloseHelper.quietClose(runner);
    }

    private static void errorHandler(Throwable throwable) {
        log.error("agent failure {}", throwable.getMessage(), throwable);
    }
}
