package Main;

import API.GET;
import API.RateLimiter;
import Core.Cache.CacheManager;
import Database.Config;
import Database.DatabaseInitialization;
import Core.Synchronizer;
import Services.*;
import com.github.pwrlabs.pwrj.protocol.PWRJ;

import java.io.IOException;
import java.sql.SQLException;

import static spark.Spark.*;

public class Main {
    public static final PWRJ pwrj = new PWRJ(Config.getPwrRpcUrl());
    public static CacheManager cacheManager;
    public static Thread synchronizerThread;

    public static void main(String[] args) throws NoSuchMethodException, IOException, SQLException {
        port(8081);

        options("/*",
                (request, response) -> {

                    String accessControlRequestHeaders = request
                            .headers("Access-Control-Request-Headers");
                    if (accessControlRequestHeaders != null) {
                        response.header("Access-Control-Allow-Headers",
                                accessControlRequestHeaders);
                    }

                    String accessControlRequestMethod = request
                            .headers("Access-Control-Request-Method");
                    if (accessControlRequestMethod != null) {
                        response.header("Access-Control-Allow-Methods",
                                accessControlRequestMethod);
                    }

                    return "OK";
                });
        before("/*", (request, response) -> {
            String ip = request.ip();
            if(RateLimiter.isIpBanned(ip)) {
                halt(403, "Your IP has been banned."); // 403 Forbidden response
            } else if(!RateLimiter.isRequestAllowed(ip)) {
                halt(429, "Your IP is being rate limited."); // 403 Forbidden response
            }
        });
        before((request, response) -> response.header("Access-Control-Allow-Origin", "*"));

        RateLimiter.initRateLimiter();

        DatabaseInitialization.initialize();

        cacheManager = new CacheManager(pwrj);

        BlockService.initialize(pwrj);
        NodeService.initialize(pwrj);
        StakingService.initialize(pwrj);
        TransactionService.initialize(pwrj);
        GeneralService.initialize(pwrj);

        GET.run();

        startSynchronizer(pwrj);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Shutting down... Stopping synchronizer");
            Synchronizer.stop();
        }));
    }

    public static void startSynchronizer(PWRJ pwrj) {
        if (Synchronizer.isRunning()) {
            System.out.println("Cannot start synchronizer: already running");
            return;
        }

        synchronizerThread = new Thread(() -> {
            try {
                Synchronizer.sync(pwrj);
            } catch (Exception e) {
                System.err.println("Error in synchronizer thread: " + e.getMessage());
            }
        });

        synchronizerThread.setName("Synchronizer-Thread");
        synchronizerThread.start();
    }
}
