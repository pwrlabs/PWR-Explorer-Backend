package API;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class RateLimiter {
    private static Map<String /*IP*/, Integer> numberOfRequestsForThePastMinuteByIp = new ConcurrentHashMap<>();
    private static Set<String> bannedIPs = new HashSet<>();
    private static Set<String> openAccessIps = new HashSet<>(); //Ips with unlimited access

    static {
        openAccessIps.add("167.172.160.157"); //EVM Validator server
        openAccessIps.add("138.68.84.236"); //EVM Faucet server
        openAccessIps.add("207.154.201.217"); //PWR Faucet server
        openAccessIps.add("164.90.169.136"); //PWR Explorer Backend server
    }

    public static boolean isRequestAllowed(String ip) {
        if(openAccessIps.contains(ip)) {return true;}

        int numberOfRequests = numberOfRequestsForThePastMinuteByIp.getOrDefault(ip, 0);
        numberOfRequestsForThePastMinuteByIp.put(ip, numberOfRequests + 1);

        if(numberOfRequests >= 1000) {
            IptablesBlocker.blockIp(ip);
            bannedIPs.add(ip);
            return false;
        }

        if (numberOfRequests > 100) {
            return false;
        }

        return true;
    }

    public static boolean isIpBanned(String ip) {
        return bannedIPs.contains(ip);
    }

    public static void initRateLimiter() {
        ScheduledExecutorService scheduledExecutor = Executors.newSingleThreadScheduledExecutor();

        Runnable task = () -> {
            numberOfRequestsForThePastMinuteByIp.clear();
        };

        scheduledExecutor.scheduleAtFixedRate(task, 0, 1, TimeUnit.SECONDS);
    }
}
