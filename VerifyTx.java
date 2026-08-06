import java.io.File;
import java.nio.file.Files;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

public class VerifyTx {
    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.out.println("Usage: java -cp . VerifyTx <tx_data_base64> <signature_base64>");
            return;
        }

        String txDataB64 = args[0];
        String sigB64 = args[1];

        byte[] txDataBytes = Base64.getDecoder().decode(txDataB64);
        byte[] sigBytes = Base64.getDecoder().decode(sigB64);

        // Читаем публичный ключ из test1_new.pub
        byte[] pubKeyBytes = Files.readAllBytes(new File("test1_new.pub").toPath());

        KeyFactory kf = KeyFactory.getInstance("EC");
        X509EncodedKeySpec pubSpec = new X509EncodedKeySpec(pubKeyBytes);
        PublicKey pubKey = kf.generatePublic(pubSpec);

        Signature verifier = Signature.getInstance("SHA256withECDSA");
        verifier.initVerify(pubKey);
        verifier.update(txDataBytes);

        boolean isValid = verifier.verify(sigBytes);
        System.out.println(isValid ? "✅ Signature is VALID" : "❌ Signature is INVALID");
    }
}
