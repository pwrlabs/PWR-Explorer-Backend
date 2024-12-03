package Main;

import API.GET;
import API.POST;
import API.RateLimiter;
import Core.DatabaseInitialization;
import PWRChain.Synchronizer;
import VM.VM;
import com.github.pwrlabs.dbm.DBM;
import com.github.pwrlabs.pwrj.protocol.PWRJ;

import java.io.IOException;

import static spark.Spark.*;

public class Main {

    public static void main(String[] args) throws NoSuchMethodException, IOException {
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


        // test explorer
        PWRJ pwrj = new PWRJ(Config.getPwrRpcUrl());

        GET.run(pwrj);
        POST.run(pwrj);

//        DBM.loadAllObjectsFromDatabase(Block.Block.class);
        DBM.loadAllObjectsFromDatabase(VM.class);
//        DBM.loadAllObjectsFromDatabase(User.class);
//        DBM.loadAllObjectsFromDatabase(Txn.Txn.class);

        Block.Initializer.init();
        Txn.Initializer.init();

        Synchronizer.sync(pwrj);
    }
}
