package org.starquake.send;

import io.aeron.Aeron;
import io.aeron.Publication;
import org.agrona.concurrent.AgentRunner;
import org.agrona.concurrent.BusySpinIdleStrategy;
import org.agrona.concurrent.IdleStrategy;
import org.agrona.concurrent.ShutdownSignalBarrier;

public class MarketDataPublisherMain {
    private static final int STREAM_ID = 10;
    private static final long MESSAGES_PER_SECOND = 1;

    public static void main(String[] args) {
        if (args.length != 3) {
            System.out.println("Usage: MarketDataPublisherMain <hostname> <port> <aeron-dir>");
            System.exit(1);
        }

        final String channel = String.format("aeron:udp?endpoint=%s:%d", args[0], Integer.parseInt(args[1]));
        final String aeronDir = args[2];

        // Set up Aeron context
        final Aeron.Context aeronCtx = new Aeron.Context()
                .aeronDirectoryName(aeronDir)
                .idleStrategy(new BusySpinIdleStrategy())
                .awaitingIdleStrategy(new BusySpinIdleStrategy());

        try (Aeron aeron = Aeron.connect(aeronCtx)) {
            // Create publication
            final Publication publication = aeron.addExclusivePublication(channel, STREAM_ID);

            // Wait for publication to connect
            while (!publication.isConnected()) {
                Thread.sleep(100);
                System.out.println("Waiting for publication to connect...");
            }
            System.out.println("Publication connected!");

            // Create the market data agent
            final MarketDataAgent marketDataAgent = new MarketDataAgent(
                    publication,
                    MESSAGES_PER_SECOND,
                    new BusySpinIdleStrategy()
            );

            // Set up the idle strategy for the agent
            final IdleStrategy idleStrategy = new BusySpinIdleStrategy();

            // Create the agent runner
            final AgentRunner agentRunner = new AgentRunner(
                    idleStrategy,
                    Throwable::printStackTrace,  // Error handler
                    null,                        // Event counter
                    marketDataAgent
            );

            // Start the agent runner
            AgentRunner.startOnThread(agentRunner);

            // Start statistics reporting thread
            Thread statsThread = new Thread(() -> {
                while (!Thread.currentThread().isInterrupted()) {
                    try {
                        Thread.sleep(1000);
                        final long messagesPublished = marketDataAgent.getMessagesPublished();
                        final long backPressureCount = marketDataAgent.getBackPressureCount();

                        System.out.printf(
                                "Messages Published: %d, Back Pressure Events: %d%n",
                                messagesPublished,
                                backPressureCount
                        );
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
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
            publication.close();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}