package com.mengyu.aeron.archive;

import io.aeron.Aeron;
import io.aeron.ChannelUri;
import io.aeron.ExclusivePublication;
import io.aeron.Subscription;
import io.aeron.archive.Archive;
import io.aeron.archive.ArchivingMediaDriver;
import io.aeron.archive.client.AeronArchive;
import io.aeron.archive.client.RecordingDescriptorConsumer;
import io.aeron.archive.codecs.SourceLocation;
import io.aeron.archive.status.RecordingPos;
import io.aeron.driver.MediaDriver;
import io.aeron.logbuffer.Header;
import java.io.File;
import lombok.extern.slf4j.Slf4j;
import org.agrona.CloseHelper;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.collections.MutableLong;
import org.agrona.concurrent.IdleStrategy;
import org.agrona.concurrent.SleepingIdleStrategy;
import org.agrona.concurrent.status.CountersReader;

/**
 * @author yu zhang
 */
@Slf4j
public class SimpleCase {
    private final String channel = "aeron:ipc";
    private final int streamCapture = 16;
    private final int streamReplay = 17;
    private final int sendCount = 10_000;

    private final IdleStrategy idleStrategy = new SleepingIdleStrategy();
    private final ExpandableArrayBuffer buffer = new ExpandableArrayBuffer();
    private final File tempDir = Utils.createTempDir();
    boolean complete = false;
    private AeronArchive archive;
    private Aeron aeron;
    private ArchivingMediaDriver mediaDriver;

    public static void main(String[] args) {
        final SimpleCase simpleCase = new SimpleCase();
        simpleCase.setup();
        log.info("Writing");
        simpleCase.write();
        log.info("Reading");
        simpleCase.read();
        simpleCase.cleanUp();
    }

    private void cleanUp() {
        CloseHelper.quietClose(archive);
        CloseHelper.quietClose(aeron);
        CloseHelper.quietClose(mediaDriver);
    }

    private void read() {
        try (AeronArchive reader = AeronArchive.connect(new AeronArchive.Context().aeron(aeron))) {
            // 找到需要读取的recording ID
            final long recordingId = findLatestRecording(reader, channel, streamCapture);
            // 设置从头开始读取数据
            // 当有新subscription加入时，会从头开始读取数据，并且接收实时数据
            final long position = 0L;
            // 告诉Aeron Archive读取实时数据
            final long length = Long.MAX_VALUE;
            final long sessionId = reader.startReplay(recordingId, position, length, channel, streamReplay);
            final String channelRead = ChannelUri.addSessionId(channel, (int) sessionId);

            final Subscription subscription = reader.context().aeron().addSubscription(channelRead, streamReplay);

            while (!subscription.isConnected()) {
                idleStrategy.idle();
            }
            log.info("startPos: {}, stopPos: {}", archive.getStartPosition(recordingId), archive.getStopPosition(recordingId));
            while (!complete) {
                int fragments = subscription.poll(this::archiveReader, 1);
                idleStrategy.idle(fragments);
            }

        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
    }

    // 开始record，同时创建可以被录入的publication
    private void write() {
        // 告诉Aeron archive开始记录给定的channel和stream上的数据，同时指明media driver是本地的
        archive.startRecording(channel, streamCapture, SourceLocation.LOCAL);
        // 创建publication，注意要记录的channel和stream必须和publication一致
        try (ExclusivePublication publication = aeron.addExclusivePublication(channel, streamCapture)) {
            // 等待publication变为连接状态
            while (!publication.isConnected()) {
                idleStrategy.idle();
            }
            // 发送数据
            for (int i = 0; i < sendCount + 1; i++) {
                buffer.putInt(0, i);
                log.info("Sent: {}", i);
                while (publication.offer(buffer, 0, Integer.BYTES) < 0) {
                    idleStrategy.idle();
                }
            }

            final long stopPosition = publication.position();
            final CountersReader countersReader = aeron.countersReader();
            final int counterId = RecordingPos.findCounterIdBySession(countersReader, publication.sessionId());
            // 等待，直到发送的数据被录入，这是因为该示例由单线程跑，所以只有publication发送的数据被完全录入后才能被读取，正常情况下这个不需要
            while (countersReader.getCounterValue(counterId) < stopPosition) {
                idleStrategy.idle();
            }
        }
    }

    private long findLatestRecording(final AeronArchive archive, String channel, int stream) throws IllegalAccessException {
        final MutableLong lastRecordingId = new MutableLong();
        // 可以返回关于recording的各种信息
        final RecordingDescriptorConsumer consumer = (controlSessionId, correlationId, recordingId, startTimestamp,
                                                      stopTimestamp, startPosition, stopPosition, initialTermId,
                                                      segmentFileLength, termBufferLength, mtuLength, sessionId,
                                                      streamId, strippedChannel, originalChannel, sourceIdentity) -> lastRecordingId.set(recordingId);

        final long fromRecordingId = 0L;
        final int recordCount = 100;
        final int foundCount = archive.listRecordingsForUri(fromRecordingId, recordCount, channel, stream, consumer);
        log.info("foundCount: {}", foundCount);
        // 如果获取到的recording的个数=0，说明没找到recording
        if (foundCount == 0) {
            throw new IllegalAccessException("no recordings found");
        }

        return lastRecordingId.get();
    }

    public void archiveReader(DirectBuffer buffer, int offset, int length, Header header) {
        final int valueRead = buffer.getInt(offset);
        log.info("Received: {}", valueRead);
        if (valueRead == sendCount) {
            complete = true;
        }
    }

    public void setup() {
        mediaDriver = ArchivingMediaDriver.launch(
                new MediaDriver.Context()
                        // 创建一个虚拟的subscription，这样publication在没有真正的subscription接入时就可以被写入
                        .spiesSimulateConnection(true)
                        .dirDeleteOnStart(true),
                new Archive.Context()
                        .deleteArchiveOnStart(true)
                        // 指定archive所需的文件
                        .archiveDir(tempDir)
        );

        aeron = Aeron.connect();

        archive = AeronArchive.connect(
                new AeronArchive.Context()
                        .aeron(aeron)
        );
    }
}
