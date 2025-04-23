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

    public static String getDatabasePassword() {
        String dbPassword = dotenv.get("DB_PASSWORD");
        if (dbPassword == null || dbPassword.trim().isEmpty()) {
            throw new IllegalStateException("Database password not found in environment variables");
        }
        return dbPassword;
    }

    public static String getPwrRpcUrl() {
        String pwrRpcUrl = dotenv.get("PWR_RPC_URL");
        if (pwrRpcUrl == null || pwrRpcUrl.trim().isEmpty()) {
            throw new IllegalStateException("PWR RPC URL not found in environment variables");
        }
        return pwrRpcUrl;
    }

    public static String getDatabaseUserName() {
        String dbUserName = dotenv.get("DB_USERNAME");
        if (dbUserName == null || dbUserName.trim().isEmpty()) {
            throw new IllegalStateException("Database username not found in environment variables");
        }
        return dbUserName;
    }
}