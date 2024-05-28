//package API;
//
//
//import com.fasterxml.jackson.databind.ObjectMapper;
//import com.fasterxml.jackson.databind.ObjectWriter;
//
//
//public class JsonUtil {
//    private static final ObjectWriter writer = new ObjectMapper().writer();
//
//    public static String toJson(Object obj) {
//        try {
//            return writer.writeValueAsString(obj);
//        } catch (Exception e) {
//            throw new RuntimeException("Error serializing to JSON", e);
//        }
//    }
//}
//
