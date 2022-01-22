package com.mengyu.aeron.mdc.subscription;

import io.aeron.Aeron;
import io.aeron.Subscription;
import io.aeron.driver.MediaDriver;
import io.aeron.driver.ThreadingMode;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;
import lombok.extern.slf4j.Slf4j;
import org.agrona.CloseHelper;
import org.agrona.concurrent.Agent;
import org.agrona.concurrent.SleepingMillisIdleStrategy;

/**
 * @author yu zhang
 */
@Slf4j
public class MultiDestinationSubscriberAgent implements Agent {

    private static final int STREAM_ID = 100;
    private final MediaDriver mediaDriver;
    private final MultiDestinationSubscriberFragmentHandler fragmentHandler;
    private final Aeron aeron;
    private Subscription mdcSubscription;

    public MultiDestinationSubscriberAgent(String mdcHost, String thisHost, int controlPort) {
        fragmentHandler = new MultiDestinationSubscriberFragmentHandler();

        log.info("launching media driver");
        this.mediaDriver = MediaDriver.launch(
                new MediaDriver.Context()
                        .dirDeleteOnStart(true)
                        .threadingMode(ThreadingMode.SHARED)
                        .sharedIdleStrategy(new SleepingMillisIdleStrategy())
        );
        log.info("connecting aeron; media driver directory {}", mediaDriver.aeronDirectoryName());
        // 创建 aeron client
        this.aeron = Aeron.connect(
                new Aeron.Context()
                        .aeronDirectoryName(mediaDriver.aeronDirectoryName())
                        .idleStrategy(new SleepingMillisIdleStrategy())
        );

        // 创建MDC subscription
        // endport：Aeron将会通过这个地址把消息发送给subscription
        // mdcHost：必须是publication的host
        // controlPort：publication中配置的port
        // 以上两个会决定Aeron如何连接上远程的MDC publication
         final String channel =
                 "aeron:udp?endpoint=" + localHost(thisHost) + ":12001|control=" + mdcHost + ":" + controlPort +
                         "|control-mode=dynamic";
         log.info("adding the subscription to channel: {}", channel);
         mdcSubscription = aeron.addSubscription(channel, STREAM_ID);
    }

    @Override
    public void onStart() {
        Agent.super.onStart();
        log.info("starting");
    }

    @Override
    public int doWork() throws Exception {
        mdcSubscription.poll(fragmentHandler, 100);
        return 0;
    }

    @Override
    public void onClose() {
        Agent.super.onClose();
        log.info("shutting down");
        CloseHelper.quietClose(mdcSubscription);
        CloseHelper.quietClose(aeron);
        CloseHelper.quietClose(mediaDriver);
    }

    @Override
    public String roleName() {
        return "mdc-subscription";
    }

    public String localHost(String fallback)
    {
        try
        {
            final Enumeration<NetworkInterface> interfaceEnumeration = NetworkInterface.getNetworkInterfaces();
            while (interfaceEnumeration.hasMoreElements())
            {
                final NetworkInterface networkInterface = interfaceEnumeration.nextElement();

                if (networkInterface.getName().startsWith("eth0"))
                {
                    Enumeration<InetAddress> interfaceAddresses = networkInterface.getInetAddresses();
                    while (interfaceAddresses.hasMoreElements())
                    {
                        Object inet4Address = interfaceAddresses.nextElement();
                        if (inet4Address instanceof Inet4Address) {
                            log.info("detected ip4 address as {}", ((Inet4Address) inet4Address).getHostAddress());
                            return ((Inet4Address) inet4Address).getHostAddress();
                        }
                    }
                }
            }
        } catch (Exception e)
        {
            log.info("Failed to get address, using {}", fallback);
        }
        return fallback;
    }
}
