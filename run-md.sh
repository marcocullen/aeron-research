#!/bin/bash
/opt/java/openjdk/bin/java \
  -javaagent:/app/agent-jar/aeron-agent-1.47.2.jar \
  --add-opens java.base/sun.nio.ch=ALL-UNNAMED \
  --add-opens java.base/java.nio=ALL-UNNAMED \
  --add-opens java.base/jdk.internal.misc=ALL-UNNAMED \
  -Daeron.event.log=all \
  -Daeron.event.log.print.stdout=true \
  -Daeron.dir=/dev/shm/aeron \
  -Daeron.threading.mode=DEDICATED \
  -Daeron.dir.delete.on.start=true \
  -Daeron.dir.delete.on.shutdown=true \
  -cp /app/build/libs/aeron-research-1.0-SNAPSHOT-all.jar \
  io.aeron.driver.MediaDriver