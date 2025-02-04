package org.starquake.receive;

import io.aeron.logbuffer.FragmentHandler;
import io.aeron.logbuffer.Header;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.EpochNanoClock;
import org.HdrHistogram.Histogram;
import com.cmcmarkets.aeron.messages.MarketDataDecoder;
import com.cmcmarkets.aeron.messages.MessageHeaderDecoder;
import org.agrona.concurrent.SystemEpochNanoClock;

public class MarketDataHandler implements FragmentHandler {
    private final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
    private final MarketDataDecoder marketDataDecoder = new MarketDataDecoder();
    private final EpochNanoClock clock;
    private final Histogram histogram;
    
    public MarketDataHandler(EpochNanoClock clock, Histogram histogram) {
        this.clock = clock;
        this.histogram = histogram;
    }
    
    @Override
    public void onFragment(DirectBuffer buffer, int offset, int length, Header header) {
        headerDecoder.wrap(buffer, offset);
        
        // Ensure we have a market data message
        if (headerDecoder.templateId() != MarketDataDecoder.TEMPLATE_ID) {
            return;
        }
        
        marketDataDecoder.wrap(buffer, 
                             offset + MessageHeaderDecoder.ENCODED_LENGTH, 
                             headerDecoder.blockLength(), 
                             headerDecoder.version());
        
        final long sendTimestamp = marketDataDecoder.timestamp();
        final long receiveTimestamp = clock.nanoTime();
        final long latencyNanos = receiveTimestamp - sendTimestamp;
        
        histogram.recordValue(latencyNanos);
    }
}