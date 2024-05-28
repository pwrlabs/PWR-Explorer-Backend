//package API;
//
//import spark.Filter;
//import spark.Request;
//import spark.Response;
//
//
//public class CompressionFilter implements Filter {
//    @Override
//    public void handle(Request request, Response response) throws Exception {
//        response.header("Content-Encoding", "gzip");
//    }
//}