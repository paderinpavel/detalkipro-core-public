import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;

public class KeyGen {
    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.out.println("Usage: java -cp . KeyGen validator1");
            return;
        }
        String prefix = args[0];

        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("EC");
        keyGen.initialize(256);
        KeyPair pair = keyGen.generateKeyPair();

        String privateKeyB64 = Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded());
        String publicKeyB64  = Base64.getEncoder().encodeToString(pair.getPublic().getEncoded());

        try (FileOutputStream fos = new FileOutputStream(prefix + ".key")) {
            fos.write(privateKeyB64.getBytes(StandardCharsets.UTF_8));
        }
        try (FileOutputStream fos = new FileOutputStream(prefix + ".pub")) {
            fos.write(publicKeyB64.getBytes(StandardCharsets.UTF_8));
        }

        System.out.println("Keys created: " + prefix + ".key and " + prefix + ".pub");
    }
}
