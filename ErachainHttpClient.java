import java.io.*;
import java.net.http.*;
import java.time.Duration;

public class ErachainHttpClient {
    private static final String URL = "http://127.0.0.1:9047/api/transaction";

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.out.println("Usage: java -cp . ErachainHttpClient <tx_data_base64> <signature_base64>");
            return;
        }

        String txDataB64 = args[0];
        String sigB64 = args[1];

        String jsonBody = "{\"tx_data\":\"" + txDataB64 + "\",\"signature\":\"" + sigB64 + "\"}";

        HttpClient client = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(10))
            .build();

        HttpRequest request = HttpRequest.newBuilder()
            .uri(java.net.URI.create(URL))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
            .build();

        System.out.println("Sending transaction to " + URL);

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        System.out.println("Status code: " + response.statusCode());
        System.out.println("Response body:\n" + response.body());
    }
}
