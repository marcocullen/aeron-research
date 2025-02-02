package org.starquake.send;

import io.aeron.Aeron;
import io.aeron.Publication;
import org.agrona.concurrent.UnsafeBuffer;
import org.agrona.concurrent.IdleStrategy;
import org.agrona.concurrent.SleepingIdleStrategy;
import org.starquake.config.Configuration;

import java.nio.ByteBuffer;

public class Publisher {
    public static void main(String[] args) {
        final IdleStrategy idle = new SleepingIdleStrategy();

        Aeron.Context context = new Aeron.Context().aeronDirectoryName(Configuration.MEDIA_DRIVER_DIR);


        try (Aeron aeron = Aeron.connect(context);
             Publication publication = aeron.addPublication(
                 Configuration.CHANNEL_SUB1,
                 Configuration.STREAM_ID)) {

            final UnsafeBuffer buffer = new UnsafeBuffer(ByteBuffer.allocate(256));
            final String message = "Hello Aeron!";
            buffer.putStringWithoutLengthAscii(0, message);

            while (true) {
                long result = publication.offer(buffer, 0, message.length());
                if (result > 0) {
                    System.out.println("Message sent: " + message);
                    Thread.sleep(1000);  // Send every second
                } else {
                    idle.idle();
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}