package org.starquake.receive;

import io.aeron.Subscription;
import org.agrona.concurrent.Agent;
import org.agrona.concurrent.IdleStrategy;

public class MarketDataSubscriberAgent implements Agent {
    private final Subscription subscription;
    private final MarketDataHandler fragmentHandler;
    private final IdleStrategy idleStrategy;
    private long messagesReceived;
    
    public MarketDataSubscriberAgent(
            Subscription subscription,
            MarketDataHandler fragmentHandler,
            IdleStrategy idleStrategy) {
        this.subscription = subscription;
        this.fragmentHandler = fragmentHandler;
        this.idleStrategy = idleStrategy;
    }
    
    @Override
    public int doWork() {
        final int fragments = subscription.poll(fragmentHandler, 10);
        messagesReceived += fragments;
        
        idleStrategy.idle(fragments);
        return fragments;
    }
    
    @Override
    public String roleName() {
        return "market-data-subscriber";
    }
    
    public long getMessagesReceived() {
        return messagesReceived;
    }
}