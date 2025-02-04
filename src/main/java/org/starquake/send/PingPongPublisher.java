package org.starquake.send;

import com.cmcmarkets.aeron.messages.MarketDataEncoder;
import com.cmcmarkets.aeron.messages.MessageHeaderEncoder;
import io.aeron.Publication;
import io.aeron.Subscription;
import io.aeron.logbuffer.BufferClaim;
import io.aeron.logbuffer.FragmentHandler;
import io.aeron.logbuffer.Header;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.*;
import org.HdrHistogram.Histogram;
import org.HdrHistogram.SingleWriterRecorder;
import java.util.concurrent.TimeUnit;

public class PingPongPublisher implements Agent, FragmentHandler {
    private final Publication publication;
    private final Subscription subscription;
    private final MessageHeaderEncoder headerEncoder;
    private final MarketDataEncoder marketDataEncoder;
    private final BufferClaim bufferClaim;
    private final int messageLength;
    private final SystemNanoClock clock;
    private final SingleWriterRecorder recorder;
    private final IdleStrategy idleStrategy;

    private long lastSentTimestamp;
    private long messageCount;
    private boolean awaitingPong = false;

    public PingPongPublisher(
            Publication publication,
            Subscription subscription,
            IdleStrategy idleStrategy,
            SingleWriterRecorder recorder) {
        this.publication = publication;
        this.subscription = subscription;
        this.headerEncoder = new MessageHeaderEncoder();
        this.marketDataEncoder = new MarketDataEncoder();
        this.bufferClaim = new BufferClaim();
        this.messageLength = MessageHeaderEncoder.ENCODED_LENGTH + MarketDataEncoder.BLOCK_LENGTH;
        this.clock = SystemNanoClock.INSTANCE;
        this.recorder = recorder;
        this.idleStrategy = idleStrategy;
    }

    @Override
    public int doWork() {
        // If we're waiting for a pong, poll for responses
        if (awaitingPong) {
            final int fragmentsRead = subscription.poll(this, 1);
            if (fragmentsRead == 0) {
                idleStrategy.idle();
            }
            return fragmentsRead;
        }

        // Otherwise send a new ping
        final long result = tryPublish();
        if (result >= 0) {
            awaitingPong = true;
            lastSentTimestamp = clock.nanoTime();
            messageCount++;
            return 1;
        } else if (result == Publication.BACK_PRESSURED) {
            idleStrategy.idle();
        } else if (result == Publication.NOT_CONNECTED ||
                result == Publication.CLOSED ||
                result == Publication.MAX_POSITION_EXCEEDED) {
            throw new IllegalStateException("Publication error: " + result);
        }

        return 0;
    }

    private long tryPublish() {
        final long result = publication.tryClaim(messageLength, bufferClaim);
        if (result < 0) {
            return result;
        }

        try {
            marketDataEncoder.wrapAndApplyHeader(bufferClaim.buffer(), bufferClaim.offset(), headerEncoder)
                    .timestamp(clock.nanoTime())
                    .symbol("AAPL    ")
                    .price(150.25f)
                    .quantity(100)
                    .tradeId(messageCount);

            bufferClaim.commit();
            return result;
        } catch (Exception ex) {
            bufferClaim.abort();
            throw ex;
        }
    }

    @Override
    public void onFragment(DirectBuffer buffer, int offset, int length, Header header) {
        // We received a pong
        final long roundTripTime = clock.nanoTime() - lastSentTimestamp;
        recorder.recordValue(roundTripTime);
        awaitingPong = false;
    }

    @Override
    public String roleName() {
        return "ping-pong-publisher";
    }

    public Histogram getRecordedHistogram() {
        return recorder.getIntervalHistogram();
    }

    public long getMessageCount() {
        return messageCount;
    }
}