package API;

public class IptablesBlocker {
    public static void blockIp(String ip) {
        try {
            String cmd = "sudo iptables -A INPUT -s " + ip + " -j DROP";

            Process process = Runtime.getRuntime().exec(cmd);
            process.waitFor();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

