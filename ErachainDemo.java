import java.io.*;
import java.security.*;
import java.util.Base64;

public class ErachainDemo {
    public static void main(String[] args) throws Exception {
        // Генерируем пару ключей
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("EC");
        keyGen.initialize(256);
        KeyPair keyPair = keyGen.generateKeyPair();

        // Сохраняем ключи
        try (FileOutputStream fosKey = new FileOutputStream("test1_new.key");
             FileOutputStream fosPub = new FileOutputStream("test1_new.pub")) {
            fosKey.write(keyPair.getPrivate().getEncoded());
            fosPub.write(keyPair.getPublic().getEncoded());
        }

        // Адрес (упрощённо: Base64 от публичного ключа)
        String address = Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
        String simplifiedAddress = address.substring(0, Math.min(50, address.length()));

        long timestamp = System.currentTimeMillis(); // РЕАЛЬНОЕ ТЕКУЩЕЕ ВРЕМЯ
        long amount = 100;
        long fee = 1;

        // Формируем TxData: From|To|Amount|Fee|Timestamp
        String txDataRaw = simplifiedAddress + "|" + simplifiedAddress + "|" + amount + "|" + fee + "|" + timestamp;
        byte[] txDataBytes = txDataRaw.getBytes("UTF-8");
        String txDataB64 = Base64.getEncoder().encodeToString(txDataBytes);

        // Подписываем
        Signature sig = Signature.getInstance("SHA256withECDSA");
        sig.initSign(keyPair.getPrivate());
        sig.update(txDataBytes);
        byte[] signatureBytes = sig.sign();
        String signatureB64 = Base64.getEncoder().encodeToString(signatureBytes);

        System.out.println("Keys saved: test1_new.key, test1_new.pub");
        System.out.println("Address (simplified): " + simplifiedAddress);
        System.out.println("Timestamp: " + timestamp);
        System.out.println("Amount: " + amount);
        System.out.println("Fee: " + fee);
        System.out.println("From/To: " + simplifiedAddress);
        System.out.println("TxData (Base64): " + txDataB64);
        System.out.println("Signature (Base64): " + signatureB64);
    }
}
