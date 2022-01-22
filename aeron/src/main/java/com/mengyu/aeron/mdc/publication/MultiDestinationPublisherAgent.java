package com.mengyu.aeron.mdc.publication;

import io.aeron.Aeron;
import io.aeron.Publication;
import io.aeron.driver.MediaDriver;
import io.aeron.driver.ThreadingMode;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.util.Enumeration;
import lombok.extern.slf4j.Slf4j;
import org.agrona.CloseHelper;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.Agent;
import org.agrona.concurrent.EpochClock;
import org.agrona.concurrent.SleepingMillisIdleStrategy;
import org.agrona.concurrent.SystemEpochClock;
import org.agrona.concurrent.UnsafeBuffer;

/**
 * @author yu zhang
 */
@Slf4j
public class MultiDestinationPublisherAgent implements Agent {

    private static final EpochClock EPOCH_CLOCK = SystemEpochClock.INSTANCE;
    private static final int STREAM_ID = 100;
    private final MediaDriver mediaDriver;
    private final Aeron aeron;
    private final MutableDirectBuffer mutableDirectBuffer;
    private final Publication publication;
    private long nextAppend = Long.MIN_VALUE;
    private long lastSeq = 0;

    public MultiDestinationPublisherAgent(String host, int controlChannelPort) {
        this.mediaDriver = launchMediaDriver();
        this.mutableDirectBuffer = new UnsafeBuffer(ByteBuffer.allocate(Long.BYTES));

        this.aeron = launchAeron(mediaDriver);
        log.info("Media Driver directory is {}", mediaDriver.aeronDirectoryName());
        // dynamic：告诉Aeron这是一个Dynamic MDC publication
        // host必须是publication本机的地址，subscription会通过localhost:port来构建运行时流
        // 还可以配置"最少有多少个subscription连接上之后，publication才可以变为connected状态
        final String publicationChannel =
                "aeron:udp?control-mode=dynamic|control=" + localHost(host) + ":" + controlChannelPort;
        log.info("creating publication");
        publication = aeron.addExclusivePublication(publicationChannel, STREAM_ID);
    }

    @Override
    public int doWork() throws Exception {
        if (EPOCH_CLOCK.time() >= nextAppend) {
            lastSeq += 1;
            mutableDirectBuffer.putLong(0, lastSeq);
            nextAppend = EPOCH_CLOCK.time() + 2000;
            log.info("appended {}", lastSeq);
        }
        return 0;
    }

    @Override
    public String roleName() {
        return "mdc-publisher";
    }

    @Override
    public void onClose() {
        Agent.super.onClose();
        log.info("Shutting down");
        CloseHelper.quietClose(publication);
        CloseHelper.quietClose(aeron);
        CloseHelper.quietClose(mediaDriver);
    }

    public String localHost(String fallback) {
        try {
            final Enumeration<NetworkInterface> interfaceEnumeration = NetworkInterface.getNetworkInterfaces();
            while (interfaceEnumeration.hasMoreElements()) {
                final NetworkInterface networkInterface = interfaceEnumeration.nextElement();
                if (networkInterface.getName().startsWith("eth0")) {
                    Enumeration<InetAddress> inetAddressEnumeration = networkInterface.getInetAddresses();
                    while (inetAddressEnumeration.hasMoreElements()) {
                        Object inet4Address = inetAddressEnumeration.nextElement();
                        if (inet4Address instanceof Inet4Address) {
                            log.info("detected ip4 address as {}", ((Inet4Address) inet4Address).getHostAddress());
                            return ((Inet4Address) inet4Address).getHostAddress();
                        }
                    }
                }
            }
        } catch (SocketException e) {
            log.error("Failed to get address, using {}", fallback);
        }
        return fallback;
    }

    private MediaDriver launchMediaDriver() {
        log.info("launching media driver");
        final MediaDriver.Context mediaDriverContext = new MediaDriver.Context()
                // publication可以在没有subscription的情况下投递消息
                .spiesSimulateConnection(true)
                .errorHandler(this::errorHandler)
                .threadingMode(ThreadingMode.SHARED)
                .sharedIdleStrategy(new SleepingMillisIdleStrategy())
                .dirDeleteOnStart(true);
        return MediaDriver.launch(mediaDriverContext);
    }

    private Aeron launchAeron(MediaDriver mediaDriver) {
        log.info("launching aeron");
        return Aeron.connect(new Aeron.Context()
                .aeronDirectoryName(mediaDriver.aeronDirectoryName())
                .errorHandler(this::errorHandler)
                .idleStrategy(new SleepingMillisIdleStrategy()));
    }

    private void errorHandler(Throwable throwable) {
        log.error("unexpected failure {}", throwable.getMessage(), throwable);
    }
}
