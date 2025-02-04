package org.starquake.receive;

import io.aeron.Aeron;
import io.aeron.Subscription;
import org.agrona.concurrent.*;
import org.HdrHistogram.Histogram;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;

public class MarketDataSubscriberMain {
    private static final int STREAM_ID = 10;
    
    public static void main(String[] args) {
        if (args.length != 3) {
            System.out.println("Usage: MarketDataSubscriberMain <hostname> <port> <aeron-dir>");
            System.exit(1);
        }

        final String channel = String.format("aeron:udp?endpoint=%s:%d", args[0], Integer.parseInt(args[1]));
        final String aeronDir = args[2];

        final Histogram histogram = new Histogram(TimeUnit.SECONDS.toNanos(1), 3);

        final Aeron.Context aeronCtx = new Aeron.Context()
            .aeronDirectoryName(aeronDir)
            .idleStrategy(new BusySpinIdleStrategy())
            .awaitingIdleStrategy(new BusySpinIdleStrategy());

        try (Aeron aeron = Aeron.connect(aeronCtx)) {
            final Subscription subscription = aeron.addSubscription(channel, STREAM_ID);

            // Create the market data handler
            final MarketDataHandler handler = new MarketDataHandler(
                new OffsetEpochNanoClock(),
                histogram
            );

            final MarketDataSubscriberAgent agent = new MarketDataSubscriberAgent(
                subscription,
                handler,
                new BusySpinIdleStrategy()
            );

            final AgentRunner agentRunner = new AgentRunner(
                new BusySpinIdleStrategy(),
                Throwable::printStackTrace,
                null,
                agent
            );

            AgentRunner.startOnThread(agentRunner);

            // Set up statistics reporting thread
            Thread statsThread = new Thread(() -> {
                try (PrintStream histogramLog = new PrintStream(new FileOutputStream(
                        String.format("market-data-latency-%s.hgrm",
                        LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))))) {
                    while (!Thread.currentThread().isInterrupted()) {
                        Thread.sleep(1000);
                        final long messagesReceived = agent.getMessagesReceived();

                        System.out.printf(
                                "Messages Received: %d, Latency (ms) min/mean/max = %.3f/%.3f/%.3f, 99%%/99.9%% = %.3f/%.3f%n",
                                messagesReceived,
                                histogram.getMinValue() / 1_000_000.0,
                                histogram.getMean() / 1_000_000.0,
                                histogram.getMaxValue() / 1_000_000.0,
                                histogram.getValueAtPercentile(99.0) / 1_000_000.0,
                                histogram.getValueAtPercentile(99.9) / 1_000_000.0
                        );
                        
                        // Optionally reset histogram after each report
                        // histogram.reset();
                    }
                    
                    // Write final histogram to file
                    System.out.println("\nWriting histogram to file...");
                    histogram.outputPercentileDistribution(histogramLog, 1000.0);
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            });
            
            statsThread.setName("market-data-stats");
            statsThread.start();

            // Wait for shutdown signal
            final ShutdownSignalBarrier barrier = new ShutdownSignalBarrier();
            barrier.await();
            
            // Clean shutdown
            statsThread.interrupt();
            agentRunner.close();
            subscription.close();
            
            System.out.println("Shutdown complete");
        }
    }
}