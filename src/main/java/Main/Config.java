package Main;

import org.json.JSONObject;

import java.io.File;
import java.nio.file.Files;

public class Config {

    private static String pwrRpcUrl, databaseUserName, databasePassword, databaseName;

    static {
        File configFile = new File("config.json");
        JSONObject config = new JSONObject();
        if (configFile.exists()) {
            System.out.println("Config:static:Config file found");
            System.out.println("Config:static:Loading config file");
            try {
                config = new JSONObject(Files.readString(configFile.toPath()));
            } catch (Exception e) {
                System.err.println("Config:static:Failed to load config file");
                e.printStackTrace();
            }
        } else {
            System.out.println("Config:static:Config file not found");
        }

        try {
//             pwrRpcUrl = config.optString("pwrRpcUrl", "http://164.92.238.215:8085");
            pwrRpcUrl = config.optString("pwrRpcUrl", "https://pwrrpc.pwrlabs.io");
            databaseUserName = config.optString("databaseUserName", "postgres");


            // explorer v2
            databasePassword = config.optString("databasePassword", "bXgzfYVU49ki");
            databaseName = config.optString("databaseName", "pwrexplorer");
//             databaseName = config.optString("databaseName", "explorer");
//             databasePassword = config.optString("databasePassword", "Kriko2004");


            // test explorer
            // databasePassword = config.optString("databasePassword", "Kriko2004");
            // databaseName = config.optString("databaseName", "testexplorer");
            // databasePassword = config.optString("databasePassword", "KUX3bgHxE4ksPRrpu");
            // databaseName = config.optString("databaseName", "pwrexplorer");


        } catch (Exception e) {
            System.err.println("Config:static:Failed to load config file");
            e.printStackTrace();

            System.exit(0);
        }

        System.out.println("Config:static:Loaded config file");
        System.out.println("Config:static:pwrRpcUrl: " + pwrRpcUrl);
        System.out.println("Config:static:databaseUserName: " + databaseUserName);
        System.out.println("Config:static:databasePassword: " + databasePassword);
        System.out.println("Config:static:databaseName: " + databaseName);
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