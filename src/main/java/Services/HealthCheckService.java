package Services;

import com.github.pwrlabs.pwrj.protocol.PWRJ;
import com.postmarkapp.postmark.client.ApiClient;
import com.postmarkapp.postmark.client.data.model.message.Message;
import com.postmarkapp.postmark.client.data.model.message.MessageResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import spark.Request;
import spark.Response;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static Utils.ResponseBuilder.*;

public class HealthCheckService {
    private static final Logger logger = LogManager.getLogger(HealthCheckService.class);

    private static final String POSTMARK_SERVER_URL = "api.postmarkapp.com";
    private static final String POSTMARK_TOKEN = "7c24d497-d46a-40e3-b7d9-cb14fdf3d760";
    private static final String FROM_EMAIL = "social@pwrlabs.io";
    private static final String TO_EMAIL = "amir619halabi@gmail.com";
    private static final String EXPLORER_ENDPOINT = "http://localhost:8082/explorerInfo/";
    private static final long BLOCK_TIMEOUT_MINUTES = 10;
    private static final String PWR_RPC_URL = "https://pwrrpc.pwrlabs.io/";
    private static final AtomicBoolean isBlockchainDown = new AtomicBoolean(false);
    private static final AtomicLong lastNotificationTime = new AtomicLong(0);
    private static final long NOTIFICATION_COOLDOWN_MS = 5 * 60 * 1000;

    private static final Map<String, Object> HEADERS = Map.of(
            "X-Postmark-Server-Token", POSTMARK_TOKEN,
            "Accept", "application/json",
            "Content-Type", "application/json"
    );

    private static final PWRJ pwrj = new PWRJ(PWR_RPC_URL);

    public static String checkBlockchainHealth(Request request, Response response) {
        try {
            response.header("Content-Type", "application/json");
            logger.info("Starting blockchain health check...");

            ExplorerHealthInfo healthInfo = getExplorerHealth();

            if (healthInfo == null) {
                handleBlockchainDown("Failed to connect to PWR RPC");
                return getError(response, "PWR RPC unreachable").toString();
            }

            boolean isHealthy = isBlockchainHealthy(healthInfo);

            if (!isHealthy) {
                String downReason = String.format("Latest block timestamp is %s (older than %d minutes)",
                        healthInfo.latestBlockTime, BLOCK_TIMEOUT_MINUTES);
                handleBlockchainDown(downReason);
            } else {
                handleBlockchainUp();
            }

            return "{\"status\":\"" + (isHealthy ? "UP" : "DOWN") + "\",\"latestBlockTime\":\"" + healthInfo.latestBlockTime + "\",\"minutesSinceLastBlock\":" + healthInfo.minutesSinceLastBlock + ",\"blockNumber\":\"" + healthInfo.latestBlockNumber + "\",\"timestamp\":\"" + LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) + "\"}";

        } catch (Exception e) {
            logger.error("Error during health check: {}", e.getMessage(), e);
            handleBlockchainDown("Health check failed: " + e.getMessage());
            return getError(response, "Health check failed: " + e.getMessage()).toString();
        }
    }

    private static ExplorerHealthInfo getExplorerHealth() {
        try {
            logger.info("Getting block timestamp from PWR RPC...");

            long blockTimestamp = pwrj.getBlockTimestamp();
            logger.info("Retrieved block timestamp: {}", blockTimestamp);

            return parseBlockTimestamp(blockTimestamp);

        } catch (Exception e) {
            logger.error("Failed to get block timestamp from PWR RPC: {}", e.getMessage(), e);
            return null;
        }
    }

    private static ExplorerHealthInfo parseBlockTimestamp(long blockTimestamp) {
        try {
            LocalDateTime blockTime = LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(blockTimestamp),
                    ZoneId.systemDefault()
            );

            LocalDateTime now = LocalDateTime.now();
            long minutesSince = Duration.between(blockTime, now).toMinutes();

            logger.info("Block time: {}, Current time: {}, Minutes since: {}",
                    blockTime, now, minutesSince);

            return new ExplorerHealthInfo(
                    blockTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                    minutesSince,
                    "latest"
            );

        } catch (Exception e) {
            logger.error("Failed to parse block timestamp: {}", e.getMessage(), e);
            return null;
        }
    }

    private static boolean isBlockchainHealthy(ExplorerHealthInfo healthInfo) {
        return healthInfo.minutesSinceLastBlock < BLOCK_TIMEOUT_MINUTES;
    }

    private static void handleBlockchainDown(String reason) {
        boolean wasUp = !isBlockchainDown.getAndSet(true);

        if (wasUp && shouldSendNotification()) {
            sendDownAlert(reason);
            lastNotificationTime.set(System.currentTimeMillis());
        }
    }

    private static void handleBlockchainUp() {
        boolean wasDown = isBlockchainDown.getAndSet(false);

        if (wasDown && shouldSendNotification()) {
            sendUpAlert();
            lastNotificationTime.set(System.currentTimeMillis());
        }
    }

    private static boolean shouldSendNotification() {
        long now = System.currentTimeMillis();
        long lastNotification = lastNotificationTime.get();
        return (now - lastNotification) > NOTIFICATION_COOLDOWN_MS;
    }

    private static void sendDownAlert(String reason) {
        try {
            String subject = "🚨 PWR Blockchain is DOWN 🚨";
            String htmlBody = String.format("""
                <!DOCTYPE html>
                <html>
                <body style="margin: 0; padding: 0; font-family: Arial, sans-serif; background-color: #f5f5f5;">
                    <div style="max-width: 600px; margin: 0 auto; background-color: #ffffff; padding: 40px; border-radius: 8px; box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1); margin-top: 20px;">
                        <h1 style="color: #dc3545; font-size: 24px; margin-bottom: 20px; text-align: center;">🚨 BLOCKCHAIN ALERT 🚨</h1>
                        
                        <div style="background-color: #f8d7da; border: 1px solid #f5c6cb; border-radius: 6px; padding: 20px; margin-bottom: 20px;">
                            <h2 style="color: #721c24; margin-top: 0;">Blockchain is DOWN</h2>
                            <p style="color: #721c24; margin-bottom: 0;"><strong>Reason:</strong> %s</p>
                        </div>
                        
                        <div style="background-color: #fff3cd; border: 1px solid #ffeaa7; border-radius: 6px; padding: 15px; margin-bottom: 20px;">
                            <p style="color: #856404; margin: 0;"><strong>Alert Time:</strong> %s</p>
                        </div>
                        
                        <p style="color: #666666; font-size: 14px;">
                            The PWR blockchain has not produced a new block in over %d minutes. 
                            Please investigate the issue immediately.
                        </p>
                        
                        <div style="border-top: 1px solid #eeeeee; padding-top: 20px; text-align: center;">
                            <p style="color: #999999; font-size: 12px;">
                                This is an automated alert from PWR Labs Monitoring System.
                            </p>
                        </div>
                    </div>
                </body>
                </html>
                """, reason, LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME), BLOCK_TIMEOUT_MINUTES);

            String textBody = String.format("""
                🚨 PWR BLOCKCHAIN ALERT 🚨
                
                Status: BLOCKCHAIN DOWN
                Reason: %s
                Alert Time: %s
                
                The PWR blockchain has not produced a new block in over %d minutes.
                Please investigate the issue immediately.
                
                This is an automated alert from PWR Labs Monitoring System.
                """, reason, LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME), BLOCK_TIMEOUT_MINUTES);

            sendEmail(subject, htmlBody, textBody);
            logger.info("Blockchain DOWN alert sent successfully");

        } catch (Exception e) {
            logger.error("Failed to send DOWN alert: {}", e.getMessage(), e);
        }
    }

    private static void sendUpAlert() {
        try {
            String subject = "✅ PWR Blockchain is BACK UP! ✅";
            String htmlBody = String.format("""
                <!DOCTYPE html>
                <html>
                <body style="margin: 0; padding: 0; font-family: Arial, sans-serif; background-color: #f5f5f5;">
                    <div style="max-width: 600px; margin: 0 auto; background-color: #ffffff; padding: 40px; border-radius: 8px; box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1); margin-top: 20px;">
                        <h1 style="color: #28a745; font-size: 24px; margin-bottom: 20px; text-align: center;">✅ RECOVERY NOTIFICATION ✅</h1>
                        
                        <div style="background-color: #d4edda; border: 1px solid #c3e6cb; border-radius: 6px; padding: 20px; margin-bottom: 20px;">
                            <h2 style="color: #155724; margin-top: 0;">Blockchain is BACK ONLINE!</h2>
                            <p style="color: #155724; margin-bottom: 0;">The PWR blockchain has successfully recovered and is now processing blocks normally.</p>
                        </div>
                        
                        <div style="background-color: #fff3cd; border: 1px solid #ffeaa7; border-radius: 6px; padding: 15px; margin-bottom: 20px;">
                            <p style="color: #856404; margin: 0;"><strong>Recovery Time:</strong> %s</p>
                        </div>
                        
                        <p style="color: #666666; font-size: 14px;">
                            Normal blockchain operations have resumed. The system is now producing blocks properly.
                        </p>
                        
                        <div style="border-top: 1px solid #eeeeee; padding-top: 20px; text-align: center;">
                            <p style="color: #999999; font-size: 12px;">
                                This is an automated notification from PWR Labs Monitoring System.
                            </p>
                        </div>
                    </div>
                </body>
                </html>
                """, LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));

            String textBody = String.format("""
                ✅ PWR BLOCKCHAIN RECOVERY ✅
                
                Status: BLOCKCHAIN BACK ONLINE
                Recovery Time: %s
                
                The PWR blockchain has successfully recovered and is now producing blocks normally.
                Normal blockchain operations have resumed.
                
                This is an automated notification from PWR Labs Monitoring System.
                """, LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));

            sendEmail(subject, htmlBody, textBody);
            logger.info("Blockchain UP alert sent successfully");

        } catch (Exception e) {
            logger.error("Failed to send UP alert: {}", e.getMessage(), e);
        }
    }

    private static void sendEmail(String subject, String htmlBody, String textBody) throws Exception {
        ApiClient client = new ApiClient(POSTMARK_SERVER_URL, HEADERS);

        Message message = new Message(FROM_EMAIL, TO_EMAIL, subject, htmlBody, textBody);
        message.setMessageStream("broadcast");

        MessageResponse response = client.deliverMessage(message);

        if (response.getErrorCode() != 0) {
            throw new RuntimeException("Failed to send email: " + response.getMessage());
        }

        logger.info("Email sent successfully. Message ID: {}", response.getMessageId());
    }

    private static class ExplorerHealthInfo {
        final String latestBlockTime;
        final long minutesSinceLastBlock;
        final String latestBlockNumber;

        ExplorerHealthInfo(String latestBlockTime, long minutesSinceLastBlock, String latestBlockNumber) {
            this.latestBlockTime = latestBlockTime;
            this.minutesSinceLastBlock = minutesSinceLastBlock;
            this.latestBlockNumber = latestBlockNumber;
        }
    }
}