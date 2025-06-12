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
import java.util.concurrent.atomic.AtomicLong;

import static Utils.ResponseBuilder.*;

public class DiscordAlertService {
    private static final Logger logger = LogManager.getLogger(DiscordAlertService.class);

    private static final String BOT_TOKEN = Config.getDiscordBotToken();
    private static final String CHANNEL_ID = Config.getDiscordChannelId();
    private static final long BLOCK_TIMEOUT_MINUTES = 10;
    private static final AtomicBoolean isRpcDown = new AtomicBoolean(false);
    private static final AtomicBoolean isBlockchainDown = new AtomicBoolean(false);

    private static JDA jda;
    private static final PWRJ pwrj = new PWRJ(Config.getPwrRpcUrl());
    static {
        initializeDiscordBot();
    }

    private static void initializeDiscordBot() {
        try {
            jda = JDABuilder.createDefault(BOT_TOKEN).build();
            jda.awaitReady();
            logger.info("Discord bot initialized successfully");
            logger.info("Bot user: {}", jda.getSelfUser().getName());
            logger.info("Bot is in {} guilds", jda.getGuilds().size());

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
                handleBlockchainDown("Failed to connect to PWR RPC");
                return getError(response, "PWR RPC unreachable").toString();
            }

            boolean isHealthy = healthInfo.minutesSinceLastBlock < BLOCK_TIMEOUT_MINUTES;

            if (!isHealthy) {
                String downReason = String.format("Latest block timestamp is %s (older than %d minutes)",
                        healthInfo.latestBlockTime, BLOCK_TIMEOUT_MINUTES);
                handleBlockchainDown(downReason);
            } else {
                handleBlockchainUp();
            }

            return "{\"status\":\"" + (isHealthy ? "UP" : "DOWN") +
                    "\",\"latestBlockTime\":\"" + healthInfo.latestBlockTime +
                    "\",\"minutesSinceLastBlock\":" + healthInfo.minutesSinceLastBlock +
                    ",\"blockNumber\":\"" + healthInfo.latestBlockNumber +
                    "\",\"timestamp\":\"" + LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) +
                    "\",\"alertChannel\":\"Discord\"}";

        } catch (Exception e) {
            logger.error("Error during Discord health check: {}", e.getMessage(), e);
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

    private static void handleBlockchainDown(String reason) {
        boolean wasUp = !isBlockchainDown.getAndSet(true);
        if (wasUp) {
            sendDownAlert(reason);
        }
    }

    private static void handleBlockchainUp() {
        boolean wasDown = isBlockchainDown.getAndSet(false);
        if (wasDown) {
            sendUpAlert();
        }
    }

    private static void sendDownAlert(String reason) {
        try {
            String message = "@everyone 🚨 Explorer is DOWN!";
            sendDiscordMessage(message);
            logger.info("Blockchain DOWN alert sent to Discord successfully");
        } catch (Exception e) {
            logger.error("Failed to send DOWN alert to Discord: {}", e.getMessage(), e);
        }
    }

    private static void sendUpAlert() {
        try {
            String message = "@everyone **✅** Explorer is back UP!";
            sendDiscordMessage(message);
            logger.info("Blockchain UP alert sent to Discord successfully");
        } catch (Exception e) {
            logger.error("Failed to send UP alert to Discord: {}", e.getMessage(), e);
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
            sendDownAlert("RPC connection failed");
        }
    }

    public static void handleRpcRecovery() {
        boolean wasDown = isRpcDown.getAndSet(false);
        if (wasDown) {
            sendUpAlert();
        }
    }

    public static String getBotDebugInfo() {
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