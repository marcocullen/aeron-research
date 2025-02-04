package org.starquake.send;

import com.cmcmarkets.aeron.messages.MarketDataEncoder;
import com.cmcmarkets.aeron.messages.MessageHeaderEncoder;
import io.aeron.Publication;
import io.aeron.logbuffer.BufferClaim;
import org.agrona.concurrent.*;

public class MarketDataAgent implements Agent {
    private final Publication publication;
    private final SystemNanoClock nanoClock;
//    private final SystemEpochNanoClock epochNanoClock;
    final EpochNanoClock epochNanoClock;

    private final MarketDataEncoder marketDataEncoder;
    private final MessageHeaderEncoder headerEncoder;
    private final BufferClaim bufferClaim;
    private final int messageLength;
    private final IdleStrategy idleStrategy;

    private final long targetPublicationIntervalNanos;
    private long nextPublicationTimeNanos;

    private long messagesPublished;
    private long backPressureCount;

    public MarketDataAgent(
            Publication publication,
            long targetPublicationRatePerSecond,
            IdleStrategy idleStrategy) {
        this.publication = publication;
        this.nanoClock = SystemNanoClock.INSTANCE;
//        this.epochNanoClock = new SystemEpochNanoClock();
        this.epochNanoClock = new OffsetEpochNanoClock();
        this.marketDataEncoder = new MarketDataEncoder();
        this.headerEncoder = new MessageHeaderEncoder();
        this.bufferClaim = new BufferClaim();
        this.messageLength = MessageHeaderEncoder.ENCODED_LENGTH + MarketDataEncoder.BLOCK_LENGTH;
        this.idleStrategy = idleStrategy;

        this.targetPublicationIntervalNanos = 1_000_000_000L / targetPublicationRatePerSecond;
        this.nextPublicationTimeNanos = nanoClock.nanoTime();
    }

    @Override
    public int doWork() {
        final long now = nanoClock.nanoTime();

        if (now >= nextPublicationTimeNanos) {
            final long result = tryPublish("AAPL    ", 150.25f, 100, messagesPublished);

            if (result >= 0) {
                messagesPublished++;
                nextPublicationTimeNanos += targetPublicationIntervalNanos;
                return 1;
            } else if (result == Publication.BACK_PRESSURED) {
                backPressureCount++;
                idleStrategy.idle();
                return 0;
            } else if (result == Publication.NOT_CONNECTED ||
                    result == Publication.CLOSED ||
                    result == Publication.MAX_POSITION_EXCEEDED) {
                throw new IllegalStateException("Publication error: " + result);
            }
        }

        idleStrategy.idle();
        return 0;
    }

    private long tryPublish(String symbol, float price, int quantity, long tradeId) {
        final long result = publication.tryClaim(messageLength, bufferClaim);
        if (result < 0) {
            return result;
        }

        final int offset = bufferClaim.offset();
        final long timestamp = epochNanoClock.nanoTime();

        try {
            marketDataEncoder.wrapAndApplyHeader(bufferClaim.buffer(), offset, headerEncoder)
                    .timestamp(timestamp)
                    .symbol(symbol)
                    .price(price)
                    .quantity(quantity)
                    .tradeId(tradeId);

            bufferClaim.commit();
            return result;
        } catch (Exception ex) {
            bufferClaim.abort();
            throw ex;
        }
    }

    @Override
    public String roleName() {
        return "market-data-publisher";
    }

    public long getMessagesPublished() {
        return messagesPublished;
    }

    public long getBackPressureCount() {
        return backPressureCount;
    }
}