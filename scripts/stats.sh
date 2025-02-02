#!/bin/bash

java --add-opens java.base/sun.nio.ch=ALL-UNNAMED \
     --add-opens java.base/java.nio=ALL-UNNAMED \
     --add-opens java.base/jdk.internal.misc=ALL-UNNAMED \
     -Daeron.dir=/dev/shm/aeron \
     -cp /app/build/libs/aeron-research-1.0-SNAPSHOT-all.jar \
     io.aeron.samples.AeronStat