import java.io.*;
import java.net.*;
import java.util.Base64;

public class ErachainClient {
    // Порт API ноды (тот, что мы нашли: 9047)
    private static final String HOST = "127.0.0.1";
    private static final int PORT = 9047;

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.out.println("Usage: java -cp . ErachainClient <tx_data_base64> <signature_base64>");
            return;
        }

        String txDataB64 = args[0];
        String sigB64 = args[1];

        System.out.println("Connecting to " + HOST + ":" + PORT + "...");

        try (Socket socket = new Socket(HOST, PORT)) {
            socket.setSoTimeout(10000); // 10 секунд таймаут

            OutputStream out = socket.getOutputStream();
            InputStream in = socket.getInputStream();

            // Формат запроса: JSON в одной строке
            String json = "{\"tx_data\":\"" + txDataB64 + "\",\"signature\":\"" + sigB64 + "\"}";
            byte[] payload = (json + "\n").getBytes("UTF-8");

            out.write(payload);
            out.flush();

            // Читаем ответ (простой текстовый или JSON)
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, "UTF-8"));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line).append("\n");
            }

            System.out.println("Response from node:\n" + response.toString());
        } catch (ConnectException e) {
            System.out.println("ERROR: Cannot connect to node. Is it running on " + HOST + ":" + PORT + "?");
        } catch (Exception e) {
            System.out.println("ERROR: " + e.toString());
            e.printStackTrace();
        }
    }
}
