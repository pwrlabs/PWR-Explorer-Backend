package Services;

import Core.Synchronizer;
import Database.DatabaseInitialization;
import Main.Main;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import spark.Request;
import spark.Response;

import static Utils.ResponseBuilder.getError;
import static Utils.ResponseBuilder.getSuccess;

public class AdminService {
    private static final Logger logger = LogManager.getLogger(AdminService.class);

    public static Object resetSystem(Request request, Response response) {
        response.header("Content-Type", "application/json");
        try {
            logger.info("Initiating system reset...");
            new Thread(() -> {
                try {
                    logger.info("Stopping synchronizer...");
                    Synchronizer.stop();
                    int maxWaitTime = 5;
                    int waitedTime = 0;
                    while (Synchronizer.isRunning() && waitedTime < maxWaitTime) {
                        try {
                            Thread.sleep(500);
                            waitedTime += 0.5;
                        } catch (InterruptedException e) {
                            logger.warn("Interrupted while waiting for synchronizer to stop", e);
                            break;
                        }
                    }
                    if (Synchronizer.isRunning()) {
                        logger.warn("Synchronizer didn't stop within {} seconds, proceeding with reset anyway", maxWaitTime);
                    } else {
                        logger.info("Synchronizer stopped successfully");
                    }
                    logger.info("Resetting database...");
                    DatabaseInitialization.resetDatabase();
                    logger.info("Resetting cache...");
                    if (Main.cacheManager != null) {
                        Main.cacheManager.resetCache();
                    }
                    logger.info("Restarting synchronizer...");
                    Main.startSynchronizer(Main.pwrj);
                    logger.info("System reset completed successfully");
                } catch (Exception e) {
                    logger.error("Error during system reset: ", e);

                    if (!Synchronizer.isRunning()) {
                        logger.info("Restarting synchronizer after error...");
                        Main.startSynchronizer(Main.pwrj);
                    }
                }
            }).start();
            return getSuccess(
                    "message", "System reset initiated. This process will take some time to complete.",
                    "status", "resetting"
            );
        } catch (Exception e) {
            logger.error("Failed to initiate system reset: ", e);
            return getError(response, "Failed to initiate system reset: " + e.getMessage());
        }
    }
}