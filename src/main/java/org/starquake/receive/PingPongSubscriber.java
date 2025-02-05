package org.starquake.receive;

import com.cmcmarkets.aeron.messages.MarketDataDecoder;
import com.cmcmarkets.aeron.messages.MessageHeaderDecoder;
import io.aeron.Aeron;
import io.aeron.Publication;
import io.aeron.Subscription;
import io.aeron.logbuffer.FragmentHandler;
import io.aeron.logbuffer.BufferClaim;
import io.aeron.logbuffer.Header;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.*;

public class PingPongSubscriber implements Agent, FragmentHandler {
    private final Subscription subscription;
    private final Publication publication;
    private final MessageHeaderDecoder headerDecoder;
    private final MarketDataDecoder marketDataDecoder;
    private final BufferClaim bufferClaim;
    private final IdleStrategy idleStrategy;
    private long messagesReceived;

    public PingPongSubscriber(
            Subscription subscription,
            Publication publication,
            IdleStrategy idleStrategy) {
        this.subscription = subscription;
        this.publication = publication;
        this.headerDecoder = new MessageHeaderDecoder();
        this.marketDataDecoder = new MarketDataDecoder();
        this.bufferClaim = new BufferClaim();
        this.idleStrategy = idleStrategy;
    }

    @Override
    public int doWork() {
        final int fragmentsRead = subscription.poll(this, 10);
        if (fragmentsRead == 0) {
            idleStrategy.idle();
        }
        return fragmentsRead;
    }

    @Override
    public void onFragment(DirectBuffer buffer, int offset, int length, Header header) {
        headerDecoder.wrap(buffer, offset);

        // Ensure we have a market data message
        if (headerDecoder.templateId() != MarketDataDecoder.TEMPLATE_ID) {
            return;
        }

        messagesReceived++;

        // Immediately send pong back
        long result;
        while ((result = publication.tryClaim(length, bufferClaim)) < 0) {
            if (result == Publication.BACK_PRESSURED) {
                idleStrategy.idle();
            } else if (result == Publication.NOT_CONNECTED) {
                System.out.println("Publication not connected - waiting...");
                try {
                    Thread.sleep(100);  // Add small delay when not connected
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            } else if (result == Publication.CLOSED) {
                throw new IllegalStateException("Publication is closed");
            } else if (result == Publication.MAX_POSITION_EXCEEDED) {
                throw new IllegalStateException("Publication position exceeded");
            } else if (result == Publication.ADMIN_ACTION) {
                // Just idle and retry on admin action
                idleStrategy.idle();
            } else {
                System.out.printf("Unknown publication error: %d%n", result);
                idleStrategy.idle();
            }
        }

        try {
            // Simply copy the original message back
            final int bufferOffset = bufferClaim.offset();
            bufferClaim.buffer().putBytes(bufferOffset, buffer, offset, length);
            bufferClaim.commit();
        } catch (Exception ex) {
            bufferClaim.abort();
            throw ex;
        }
    }

    @Override
    public String roleName() {
        return "ping-pong-subscriber";
    }

    public long getMessagesReceived() {
        return messagesReceived;
    }

    public static void main(String[] args) {
        if (args.length != 4) {
            System.out.println("Usage: PingPongSubscriber <hostname> <port> <aeron-dir>");
            System.exit(1);
        }

        // Subscriber (172.16.2.11) configuration
        // For receiving pings: bind to our IP
        final String subscribeChannel = String.format("aeron:udp?endpoint=%s:%d",args[3], Integer.parseInt(args[1]));
        System.out.printf("Sub channel: %s%n", subscribeChannel);
        // For sending pongs: bind to our IP, send to publisher's IP (args[0])
        final String publishChannel = String.format("aeron:udp?endpoint=%s:%d|interface=%s",args[0], Integer.parseInt(args[1]) + 1, args[3]);
        System.out.printf("Pub channel: %s%n", publishChannel);
        final String aeronDir = args[2];

        final Aeron.Context aeronCtx = new Aeron.Context()
                .aeronDirectoryName(aeronDir)
                .awaitingIdleStrategy(new BusySpinIdleStrategy());

        try (Aeron aeron = Aeron.connect(aeronCtx)) {
            final Subscription subscription = aeron.addSubscription(subscribeChannel, 10);
            final Publication publication = aeron.addExclusivePublication(publishChannel, 11);

            // Wait for publication to connect
            while (!publication.isConnected()) {
                Thread.sleep(1000);
                System.out.println("Waiting for publication to connect...");
            }
            System.out.println("Publication connected!");

            final PingPongSubscriber subscriber = new PingPongSubscriber(
                    subscription,
                    publication,
                    new BusySpinIdleStrategy()
            );

            final AgentRunner agentRunner = new AgentRunner(
                    new BusySpinIdleStrategy(),
                    Throwable::printStackTrace,
                    null,
                    subscriber
            );

            AgentRunner.startOnThread(agentRunner);

            // Statistics reporting thread
            Thread statsThread = new Thread(() -> {
                while (!Thread.currentThread().isInterrupted()) {
                    try {
                        Thread.sleep(1000);
                        System.out.printf("Messages Received: %d%n", subscriber.getMessagesReceived());
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            });

            statsThread.setName("ping-pong-stats");
            statsThread.start();

            // Wait for shutdown signal
            new ShutdownSignalBarrier().await();

            // Clean shutdown
            statsThread.interrupt();
            agentRunner.close();
            subscription.close();
            publication.close();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}