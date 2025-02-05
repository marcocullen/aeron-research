#!/bin/bash

AERON_DIR="/dev/shm/aeron-${SERVICE_NAME}"

# Subscriber should bind to its own IP
java --add-opens java.base/sun.nio.ch=ALL-UNNAMED \
     --add-opens java.base/java.nio=ALL-UNNAMED \
     --add-opens java.base/jdk.internal.misc=ALL-UNNAMED \
     -cp /app/build/libs/aeron-research-1.0-SNAPSHOT-all.jar \
     org.starquake.receive.PingPongSubscriber 172.16.1.10 20121 ${AERON_DIR} 172.16.2.11