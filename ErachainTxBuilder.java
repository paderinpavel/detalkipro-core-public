import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

public class ErachainTxBuilder {

    private static final char[] ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz".toCharArray();

    public static void main(String[] args) throws Exception {
        if (args.length < 3) {
            System.out.println("Usage: java -cp . ErachainTxBuilder <priv.key> <pub.key> <amount>");
            return;
        }

        String privPath = args[0];
        String pubPath = args[1];
        long amount = Long.parseLong(args[2]);
        long fee = 1;
        long timestamp = System.currentTimeMillis();

        byte[] privBytes = Files.readAllBytes(Paths.get(privPath));
        byte[] pubBytes = Files.readAllBytes(Paths.get(pubPath));

        // Адрес: SHA-256(pub) → checksum SHA-256×2 → Base58
        String address = getAddressFromPub(pubBytes);
        System.out.println("Address (simplified): " + address);

        // Транзакция: from|to|amount|fee|timestamp
        String txString = address + "|" + address + "|" + amount + "|" + fee + "|" + timestamp;
        byte[] txData = txString.getBytes();

        // Подпись ECDSA
        byte[] signature = signECDSA(privBytes, txData);

        System.out.println("Timestamp: " + timestamp);
        System.out.println("Amount: " + amount);
        System.out.println("Fee: " + fee);
        System.out.println("From/To: " + address);
        System.out.println("TxData (Base64): " + Base64.getEncoder().encodeToString(txData));
        System.out.println("Signature (Base64): " + Base64.getEncoder().encodeToString(signature));
        System.out.println("---");
        System.out.println("Пакет готов. Дальше его нужно передать ноде (CLI/RPC).");
    }

    private static String getAddressFromPub(byte[] pubBytes) throws NoSuchAlgorithmException {
        MessageDigest sha256 = MessageDigest.getInstance("SHA-256");

        byte[] hash1 = sha256.digest(pubBytes);

        byte[] check1 = sha256.digest(hash1);
        byte[] check2 = sha256.digest(check1);
        byte[] checksum = new byte[4];
        System.arraycopy(check2, 0, checksum, 0, 4);

        byte[] payload = new byte[hash1.length + checksum.length];
        System.arraycopy(hash1, 0, payload, 0, hash1.length);
        System.arraycopy(checksum, 0, payload, hash1.length, checksum.length);

        return base58Encode(payload);
    }

    private static byte[] signECDSA(byte[] privKeyBytes, byte[] data) throws GeneralSecurityException {
        KeyFactory kf = KeyFactory.getInstance("EC");
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(privKeyBytes);
        PrivateKey privateKey = kf.generatePrivate(spec);

        Signature ecdsaSign = Signature.getInstance("SHA256withECDSA");
        ecdsaSign.initSign(privateKey);
        ecdsaSign.update(data);
        return ecdsaSign.sign();
    }

    private static String base58Encode(byte[] bytes) {
        BigInteger bigInt = new BigInteger(1, bytes);
        StringBuilder result = new StringBuilder();
        BigInteger base = BigInteger.valueOf(58);

        while (bigInt.compareTo(BigInteger.ZERO) > 0) {
            BigInteger[] dr = bigInt.divideAndRemainder(base);
            bigInt = dr[0];
            result.append(ALPHABET[dr[1].intValue()]);
        }

        for (byte b : bytes) {
            if (b == 0) result.append('1');
        }

        return result.reverse().toString();
    }
}
