package org.starquake.receive;

import io.aeron.Aeron;
import io.aeron.Subscription;
import io.aeron.logbuffer.FragmentHandler;
import org.agrona.concurrent.SleepingIdleStrategy;
import org.agrona.concurrent.IdleStrategy;
import org.starquake.config.Configuration;

public class Subscriber {
    public static void main(String[] args) {
        final IdleStrategy idle = new SleepingIdleStrategy();
        final FragmentHandler fragmentHandler = (buffer, offset, length, header) -> {
            final String message = buffer.getStringWithoutLengthAscii(offset, length);
            System.out.println("Received: " + message);
        };

        Aeron.Context context = new Aeron.Context().aeronDirectoryName(Configuration.MEDIA_DRIVER_DIR);

        try (Aeron aeron = Aeron.connect(context);
             Subscription subscription = aeron.addSubscription(
                 Configuration.CHANNEL_SUB1,
                 Configuration.STREAM_ID)) {

            while (true) {
                final int fragmentsRead = subscription.poll(fragmentHandler, 10);
                idle.idle(fragmentsRead);
            }
        }
    }
}