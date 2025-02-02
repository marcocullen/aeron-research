package org.starquake.config;

public class Configuration {
    public static final String CHANNEL_SUB1 = "aeron:udp?endpoint=172.16.2.11:20121";
    public static final String CHANNEL_SUB2 = "aeron:udp?endpoint=172.16.1.11:20121";
    public static final int STREAM_ID = 1001;
    public static final String MEDIA_DRIVER_DIR = "/dev/shm/aeron";  // Using mapped volume
}