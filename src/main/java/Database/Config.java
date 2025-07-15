package Database;

import io.github.cdimascio.dotenv.Dotenv;

public class Config {
    private static final Dotenv dotenv = Dotenv.load();

    public static String getDbURL() {
        String dbURL = dotenv.get("DB_URL");
        if (dbURL == null || dbURL.trim().isEmpty()) {
            throw new IllegalStateException("Database URL not found in environment variables");
        }
        return dbURL;
    }

    public static String getPwrRpcUrl() {
        String pwrRpcUrl = dotenv.get("RPC_URL");
        if (pwrRpcUrl == null || pwrRpcUrl.trim().isEmpty()) {
            throw new IllegalStateException("PWR RPC URL not found in environment variables");
        }
        return pwrRpcUrl;
    }

    public static String getDiscordBotToken() {
        String discordBotToken = dotenv.get("DISCORD_BOT_TOKEN");
        if (discordBotToken == null || discordBotToken.trim().isEmpty()) {
            throw new IllegalStateException("Discord bot token not found in environment variables");
        }
        return discordBotToken;
    }

    public static String getDiscordChannelId() {
        String discordChannelId = dotenv.get("DISCORD_CHANNEL_ID");
        if (discordChannelId == null || discordChannelId.trim().isEmpty()) {
            throw new IllegalStateException("Discord channel ID not found in environment variables");
        }
        return discordChannelId;
    }

    public static String getEnvironment() {
        String env = dotenv.get("ENV");
        if (env == null || env.trim().isEmpty()) {
            env = "dev";
        }
        return env;
    }
}