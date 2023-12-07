package User;

import java.util.HashMap;
import java.util.Map;

public class Users {
    private static Map<String /*Address*/, User> userByAddress = new HashMap<>();
    private static int delegatorsCount = 0;
    public static void add(User user) {
        userByAddress.put(user.getAddress(), user);
    }

    public static void addDelegator() {
        delegatorsCount++;
    }

    public static void removeDelegator() {
        delegatorsCount--;
    }
    public static int getDelegatorsCount() {
        return delegatorsCount;
    }

    public static User getUser(String address) {
        return userByAddress.get(address.toLowerCase().trim());
    }

    public static User getUser(byte[] address) {
        return getUser("0x" + bytesToHex(address));
    }

    public static User getUserCreateIfMissing(String address) {
        User user = getUser(address);
        if(user == null) {
            user = new User(address.toLowerCase());
        }
        return user;
    }

    public static User getUserCreateIfMissing(byte[] address) {
        return getUserCreateIfMissing("0x" + bytesToHex(address).toLowerCase());
    }

    public static String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : bytes) {
            // Convert each byte to a 2-digit hexadecimal string.
            hexString.append(String.format("%02X", b));
        }
        return hexString.toString();
    }
}
