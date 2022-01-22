package com.mengyu.aeron.ipc.agents;

import io.aeron.Publication;
import java.nio.ByteBuffer;
import org.agrona.concurrent.Agent;
import org.agrona.concurrent.UnsafeBuffer;

/**
 * @author yu zhang
 */
public class SendAgent implements Agent {
    private final Publication publication;

    private final int sendCount;

    private final UnsafeBuffer unsafeBuffer;

    private int currentCountItem = 1;

    public SendAgent(Publication publication, int sendCount) {
        this.publication = publication;
        this.sendCount = sendCount;
        this.unsafeBuffer = new UnsafeBuffer(ByteBuffer.allocate(64));
    }

    @Override
    public int doWork() throws Exception {
        if (currentCountItem > sendCount) {
            return 0;
        }
        if (publication.isConnected()) {
            if (publication.offer(unsafeBuffer) > 0) {
                currentCountItem++;
                unsafeBuffer.putInt(0, currentCountItem);
            }
        }
        return 0;
    }

    @Override
    public String roleName() {
        return "sender";
    }
}
