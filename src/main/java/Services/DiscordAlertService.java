package Services;

import Database.Config;
import com.github.pwrlabs.pwrj.protocol.PWRJ;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import spark.Request;
import spark.Response;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicBoolean;
import static Database.Constants.Constants.BLOCK_TIMEOUT_MINUTES;

import static Utils.ResponseBuilder.*;

public class DiscordAlertService {
    private static final Logger logger = LogManager.getLogger(DiscordAlertService.class);

    private static final String BOT_TOKEN = Config.getDiscordBotToken();
    private static final String CHANNEL_ID = Config.getDiscordChannelId();
    private static final AtomicBoolean isRpcDown = new AtomicBoolean(false);
    public static final AtomicBoolean isBlockchainDown = new AtomicBoolean(false);
    private static final AtomicBoolean isBotReady = new AtomicBoolean(false);
    private static volatile long lastDownAlertTime = 0; // in millis
    private static final long ALERT_COOLDOWN_MS = 5 * 60 * 1000; // 5 minutes

    private static JDA jda;
    private static final PWRJ pwrj = new PWRJ(Config.getPwrRpcUrl());
    static {
        initializeDiscordBot();
    }

    private static void initializeDiscordBot() {
        try {
            jda = JDABuilder.createDefault(BOT_TOKEN).build();
            jda.awaitReady();
            isBotReady.set(true);
            logger.info("✅ Discord bot is fully initialized and ready for alerts.");
            TextChannel targetChannel = jda.getTextChannelById(CHANNEL_ID);
            if (targetChannel != null) {
                logger.info("Successfully found target channel: {} in guild: {}",
                        targetChannel.getName(), targetChannel.getGuild().getName());
            } else {
                logger.warn("Target channel {} not found! Available channels:", CHANNEL_ID);
                jda.getTextChannels().forEach(channel ->
                        logger.info("Available channel: {} (ID: {}) in guild: {}",
                                channel.getName(), channel.getId(), channel.getGuild().getName())
                );
            }
        } catch (Exception e) {
            logger.error("Failed to initialize Discord bot: {}", e.getMessage(), e);
        }
    }

    public static String checkBlockchainHealthAndAlert(Request request, Response response) {
        try {
            response.header("Content-Type", "application/json");
            logger.info("Starting blockchain health check with Discord alerts...");
            ExplorerHealthInfo healthInfo = getExplorerHealth();
            if (healthInfo == null) {
                DiscordAlertService.handleBlockchainDown(
                        "PWR RPC unreachable",
                        -1,
                        "unknown",
                        -1
                );
                return getError(response, "PWR RPC unreachable").toString();
            }
            boolean isHealthy = healthInfo.minutesSinceLastBlock < BLOCK_TIMEOUT_MINUTES;
            if (!isHealthy) {
                String downReason = String.format("Latest block timestamp is %s (older than %d minutes)",
                        healthInfo.latestBlockTime, BLOCK_TIMEOUT_MINUTES);

                DiscordAlertService.handleBlockchainDown(
                        downReason,
                        Long.parseLong(healthInfo.latestBlockNumber),
                        healthInfo.latestBlockTime,
                        healthInfo.minutesSinceLastBlock
                );
            } else {
                DiscordAlertService.handleBlockchainUp(
                        Long.parseLong(healthInfo.latestBlockNumber),
                        healthInfo.latestBlockTime,
                        healthInfo.minutesSinceLastBlock
                );
            }

            return "{\"status\":\"" + (isHealthy ? "UP" : "DOWN") +
                    "\",\"latestBlockTime\":\"" + healthInfo.latestBlockTime +
                    "\",\"minutesSinceLastBlock\":" + healthInfo.minutesSinceLastBlock +
                    ",\"blockNumber\":\"" + healthInfo.latestBlockNumber +
                    "\",\"timestamp\":\"" + LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) +
                    "\",\"alertChannel\":\"Discord\"}";

        } catch (Exception e) {
            logger.error("Error during Discord health check: {}", e.getMessage(), e);
            DiscordAlertService.handleBlockchainDown(
                    "Health check failed: " + e.getMessage(),
                    -1,
                    "unknown",
                    -1
            );
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

    public static void debugStateChange(String caller, boolean newState) {
        boolean oldState = isBlockchainDown.get();
        logger.info("STATE CHANGE DEBUG - Caller: {}, Old: {}, New: {}, StackTrace: {}",
                caller, oldState, newState, Thread.currentThread().getStackTrace()[2]);
    }

    public static void handleBlockchainDown(String reason, long blockNumber, String blockTime, long ageMinutes) {
        debugStateChange("handleBlockchainDown", true);
        boolean previousState = isBlockchainDown.get();
        boolean wasUp = !previousState;
        logger.info("handleBlockchainDown() called — previousState: {}, wasUp: {}", previousState, wasUp);
        if (wasUp) {
            isBlockchainDown.set(true);
            logger.info("Blockchain state changed: UP → DOWN");
            sendDownAlert(reason, blockNumber, blockTime, ageMinutes);
        } else {
            logger.info("Blockchain already DOWN, skipping duplicate alert");
        }
    }

    public static void handleBlockchainUp(long blockNumber, String blockTime, long ageMinutes) {
        debugStateChange("handleBlockchainUp", false);
        boolean previousState = isBlockchainDown.get();
        boolean wasDown = previousState;
        logger.info("handleBlockchainUp() called — previousState: {}, wasDown: {}", previousState, wasDown);
        if (wasDown) {
            isBlockchainDown.set(false);
            logger.info("Blockchain state changed: DOWN → UP");
            sendUpAlert(blockNumber, blockTime, ageMinutes);
        } else {
            logger.info("Blockchain already UP, skipping duplicate alert");
        }
    }

    public static void sendDownAlert(String reason, long blockNumber, String blockTime, long gapMinutes) {
        String message = String.format("@everyone \uD83D\uDEA8 Explorer is DOWN!",blockNumber, gapMinutes, reason);
        try {
            sendDiscordMessage(message);
        } catch (Exception e) {
            logger.error("Failed to send down alert: {}", e.getMessage(), e);
        }
    }

    public static void sendUpAlert(long blockNumber, String blockTime, long gapMinutes) {
        String message = String.format("@everyone ✅  Explorer is back UP!",blockNumber, gapMinutes);
        try {
            sendDiscordMessage(message);
        } catch (Exception e) {
            logger.error("Failed to send up alert: {}", e.getMessage(), e);
        }
    }

    private static void sendDiscordMessage(String message) throws Exception {
        if (jda == null) {
            throw new RuntimeException("Discord bot is not initialized");
        }

        if (jda.getStatus() != JDA.Status.CONNECTED) {
            throw new RuntimeException("Discord bot is not connected. Status: " + jda.getStatus());
        }

        TextChannel channel = jda.getTextChannelById(CHANNEL_ID);
        if (channel == null) {
            logger.error("Channel {} not found. Available channels:", CHANNEL_ID);
            jda.getTextChannels().forEach(ch ->
                    logger.error("Available: {} (ID: {}) in guild: {}",
                            ch.getName(), ch.getId(), ch.getGuild().getName())
            );
            throw new RuntimeException("Discord channel not found: " + CHANNEL_ID);
        }

        if (!channel.canTalk()) {
            throw new RuntimeException("Bot doesn't have permission to send messages in channel: " + channel.getName());
        }

        channel.sendMessage(message).queue(
                success -> logger.info("Discord message sent successfully to channel: {}", channel.getName()),
                failure -> logger.error("Failed to send Discord message: {}", failure.getMessage())
        );
    }

    public static void handleRpcFailure() {
        boolean wasUp = !isRpcDown.getAndSet(true);
        if (wasUp) {
            try {
                sendDownAlert("RPC connection failed", -1, "unknown", -1);
            } catch (Exception e) {
                logger.error("Failed to send RPC down alert: {}", e.getMessage(), e);
            }
        }
    }

    public static void handleRpcRecovery() {
        boolean wasDown = isRpcDown.getAndSet(false);
        if (wasDown) {
            try {
                sendUpAlert(-1, "unknown", -1);
            } catch (Exception e) {
                logger.error("Failed to send RPC up alert: {}", e.getMessage(), e);
            }
        }
    }

    public static void handleRpcReset() {
        try {
            String message = "@amir619h ⚠️ RPC Reset Detected - Auto-resetting DB!";
            sendDiscordMessage(message);
            logger.info("RPC reset alert sent to Discord");
        } catch (Exception e) {
            logger.error("Failed to send RPC reset alert: {}", e.getMessage(), e);
        }
    }

    public static Object getBotDebugInfo(Request req, Response res) {
        try {
            res.header("Content-Type", "text/plain");
            if (jda == null) {
                return "Discord bot not initialized";
            }

            StringBuilder info = new StringBuilder();
            info.append("Bot Status: ").append(jda.getStatus()).append("\n");
            info.append("Bot User: ").append(jda.getSelfUser().getName()).append("\n");
            info.append("Guilds: ").append(jda.getGuilds().size()).append("\n");

            TextChannel targetChannel = jda.getTextChannelById(CHANNEL_ID);
            if (targetChannel != null) {
                info.append("Target Channel Found: ").append(targetChannel.getName())
                        .append(" in ").append(targetChannel.getGuild().getName()).append("\n");
                info.append("Can Talk: ").append(targetChannel.canTalk()).append("\n");
            } else {
                info.append("Target Channel NOT FOUND\n");
                info.append("Available Channels:\n");
                jda.getTextChannels().forEach(channel ->
                        info.append("- ").append(channel.getName())
                                .append(" (ID: ").append(channel.getId())
                                .append(") in ").append(channel.getGuild().getName()).append("\n")
                );
            }

            return info.toString();
        } catch (Exception e) {
            logger.error("Error retrieving bot status info: {}", e.getMessage(), e);
            return getError(res, "Failed to get bot status: " + e.getMessage());
        }
    }

    public static boolean isReady() {
        return isBotReady.get();
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