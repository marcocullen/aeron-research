package org.starquake.send;

import io.aeron.Aeron;
import io.aeron.Publication;
import io.aeron.Subscription;
import org.agrona.concurrent.*;
import org.HdrHistogram.SingleWriterRecorder;
import java.util.concurrent.TimeUnit;

public class PingPongPublisherMain {
    public static void main(String[] args) {
        if (args.length != 4) {
            System.out.println("Usage: PingPongPublisherMain <hostname> <port> <aeron-dir>");
            System.exit(1);
        }

        // Publisher (172.16.1.10) configuration
        // For sending pings: bind to our IP, send to subscriber's IP
        final String publishChannel = String.format("aeron:udp?endpoint=%s:%d|interface=%s", args[0], Integer.parseInt(args[1]), args[3]);

        // For receiving pongs: bind to our IP
        final String subscribeChannel = String.format("aeron:udp?endpoint=%s:%d", args[3], Integer.parseInt(args[1]) + 1);

        System.out.println("Publishing to: " + publishChannel);
        System.out.println("Subscribing to: " + subscribeChannel);

        final String aeronDir = args[2];

        // Create recorder for latency tracking
        final SingleWriterRecorder recorder = new SingleWriterRecorder(
                TimeUnit.SECONDS.toNanos(1),  // Highest trackable value
                3                             // Number of significant digits
        );

        final Aeron.Context aeronCtx = new Aeron.Context()
                .aeronDirectoryName(aeronDir)
                .awaitingIdleStrategy(new BusySpinIdleStrategy());

        try (Aeron aeron = Aeron.connect(aeronCtx)) {
            final Publication publication = aeron.addExclusivePublication(publishChannel, 10);
            final Subscription subscription = aeron.addSubscription(subscribeChannel, 11);

            // Wait for publication to connect
            while (!publication.isConnected()) {
                Thread.sleep(100);
                System.out.println("Waiting for publication to connect...");
            }
            System.out.println("Publication connected!");

            final PingPongPublisher publisher = new PingPongPublisher(
                    publication,
                    subscription,
                    new BusySpinIdleStrategy(),
                    recorder
            );

            final AgentRunner agentRunner = new AgentRunner(
                    new BusySpinIdleStrategy(),
                    Throwable::printStackTrace,
                    null,
                    publisher
            );

            AgentRunner.startOnThread(agentRunner);

            Thread statsThread = buildStatsThread(publisher);

            // Wait for shutdown signal
            new ShutdownSignalBarrier().await();

            // Clean shutdown
            statsThread.interrupt();
            agentRunner.close();
            publication.close();
            subscription.close();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private static Thread buildStatsThread(PingPongPublisher publisher) {
        Thread statsThread = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(1000);
                    final var hist = publisher.getRecordedHistogram();
                    System.out.printf(
                            "Messages: %d, RTT (μs) min/mean/max = %.3f/%.3f/%.3f, 99%%/99.9%% = %.3f/%.3f%n",
                            publisher.getMessageCount(),
                            hist.getMinValue() / 1000.0,
                            hist.getMean() / 1000.0,
                            hist.getMaxValue() / 1000.0,
                            hist.getValueAtPercentile(99.0) / 1000.0,
                            hist.getValueAtPercentile(99.9) / 1000.0
                    );
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        });

        statsThread.setName("ping-pong-stats");
        statsThread.start();
        return statsThread;
    }
}