package com.mengyu.aeron.mdc.subscription;

import lombok.extern.slf4j.Slf4j;
import org.agrona.CloseHelper;
import org.agrona.concurrent.AgentRunner;
import org.agrona.concurrent.ShutdownSignalBarrier;
import org.agrona.concurrent.SleepingMillisIdleStrategy;

/**
 * @author yu zhang
 */
@Slf4j
public class MultiDestinationSubscriber {
    public static void main(String[] args) {
        final String thisHost = "localhost";
        final String mdcHost = "localhost";
        final String controlPort = "8081";

        final int controlChannelPort = Integer.parseInt(controlPort);
        final ShutdownSignalBarrier barrier  = new ShutdownSignalBarrier();
        final MultiDestinationSubscriberAgent hostAgent = new MultiDestinationSubscriberAgent(mdcHost, thisHost,
                controlChannelPort);
        final AgentRunner runner = new AgentRunner(new SleepingMillisIdleStrategy(), MultiDestinationSubscriber::errorHandler
                , null ,hostAgent);

        barrier.await();

        CloseHelper.quietClose(runner);
    }

    private static void errorHandler(Throwable throwable)
    {
        log.error("agent error {}", throwable.getMessage(), throwable);
    }
}
