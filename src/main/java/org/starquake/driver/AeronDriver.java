package org.starquake.driver;

import io.aeron.driver.MediaDriver;
import io.aeron.driver.ThreadingMode;
import org.agrona.concurrent.ShutdownSignalBarrier;
import org.starquake.config.Configuration;

public class AeronDriver {
    public static void main(String[] args) {
        final MediaDriver.Context ctx = new MediaDriver.Context()
                .dirDeleteOnStart(true)
                .dirDeleteOnShutdown(true)
                .aeronDirectoryName(Configuration.MEDIA_DRIVER_DIR)
                .threadingMode(ThreadingMode.DEDICATED);

        try (MediaDriver driver = MediaDriver.launch(ctx)) {
            System.out.println("MediaDriver started...");
            new ShutdownSignalBarrier().await();
            System.out.println("MediaDriver shutting down...");
        } catch (Exception e) {
            System.err.println("MediaDriver error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}