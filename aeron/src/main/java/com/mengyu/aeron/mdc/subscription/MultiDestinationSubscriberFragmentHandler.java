package com.mengyu.aeron.mdc.subscription;

import io.aeron.logbuffer.FragmentHandler;
import io.aeron.logbuffer.Header;
import lombok.extern.slf4j.Slf4j;
import org.agrona.DirectBuffer;

/**
 * @author yu zhang
 */
@Slf4j
public class MultiDestinationSubscriberFragmentHandler implements FragmentHandler {
    @Override
    public void onFragment(DirectBuffer buffer, int offset, int length, Header header) {
        final Long read = buffer.getLong(offset);
        log.info("received {}", read);
    }
}
