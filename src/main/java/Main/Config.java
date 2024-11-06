package Main;

import org.json.JSONObject;

import java.io.File;
import java.nio.file.Files;

public class Config {

    private static String pwrRpcUrl, databaseUserName, databasePassword, databaseName;

    static {
        File configFile = new File("config.json");
        JSONObject config = new JSONObject();
        if(configFile.exists()) {
            try {
                new JSONObject(Files.readString(configFile.toPath()));
            } catch (Exception e) {
                System.err.println("Config:static:Failed to load config file");
                e.printStackTrace();
            }
        }

        try {
//            pwrRpcUrl = config.optString("pwrRpcUrl", "http://147.182.172.216:8085/");
            pwrRpcUrl = config.optString("pwrRpcUrl", "https://pwrrpc.pwrlabs.io");
            databaseUserName = config.optString("databaseUserName", "postgres");
            databasePassword = config.optString("databasePassword", "KUX3bgHxE4ksPRrpu");
            databaseName = config.optString("databaseName", "pwrexplorer");

//            databasePassword = config.optString("databasePassword", "Kriko2004");
//            databaseName = config.optString("databaseName", "testexplorer");

        } catch (Exception e ) {
            System.err.println("Config:static:Failed to load config file");
            e.printStackTrace();

            System.exit(0);
        }
    }

    public static String getPwrRpcUrl() {
        return pwrRpcUrl;
    }

    public static String getDatabaseUserName() {
        return databaseUserName;
    }

    public static String getDatabasePassword() {
        return databasePassword;
    }

    public static String getDatabaseName() {
        return databaseName;
    }

}
