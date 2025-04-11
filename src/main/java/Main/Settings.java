package Main;

import com.github.pwrlabs.pwrj.protocol.PWRJ;
import org.json.JSONObject;

public class Settings {
    public static final PWRJ pwrj = new PWRJ("http://localhost:8085");

    private static int blockSizeLimit = 5000000; //Bytes
    private static int price = 100; //Dollar with 2 decimal places

    public static int getBlockSizeLimit() {
        return blockSizeLimit;
    }

    public static int getPrice() {
        return price;
    }
}
