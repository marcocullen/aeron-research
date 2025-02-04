#!/bin/bash
AERON_DIR="/dev/shm/aeron-${SERVICE_NAME}"

/opt/java/openjdk/bin/java \
  --add-opens java.base/sun.nio.ch=ALL-UNNAMED \
  --add-opens java.base/java.nio=ALL-UNNAMED \
  --add-opens java.base/jdk.internal.misc=ALL-UNNAMED \
  -Daeron.dir=${AERON_DIR} \
  -Daeron.threading.mode=DEDICATED \
  -Daeron.conductor.idle.strategy=org.agrona.concurrent.BusySpinIdleStrategy \
  -Daeron.sender.idle.strategy=org.agrona.concurrent.BusySpinIdleStrategy \
  -Daeron.receiver.idle.strategy=org.agrona.concurrent.BusySpinIdleStrategy \
  -Daeron.term.buffer.sparse.file=false \
  -Daeron.operation.timeout=1000000000 \
  -Daeron.dir.delete.on.start=true \
  -Daeron.dir.delete.on.shutdown=true \
  -cp /app/build/libs/aeron-research-1.0-SNAPSHOT-all.jar \
  io.aeron.driver.MediaDriver