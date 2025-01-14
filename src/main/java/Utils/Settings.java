package Utils;


public class Settings {
    private static int blockSizeLimit = 5000000; //Bytes
    private static int price = 100; //Dollar with 2 decimal places

    public static int getBlockSizeLimit() {
        return blockSizeLimit;
    }

    public static int getPrice() {
        return price;
    }
}
