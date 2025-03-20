package flashscore.weeklydatascraping.fffffff;

import javax.crypto.Cipher;
import java.security.*;
import java.util.Base64;

public class RSAEncryptionExample {

    // RSA ile şifreleme
    public static String encrypt(String data, PublicKey publicKey) throws Exception {
        Cipher cipher = Cipher.getInstance("RSA");
        cipher.init(Cipher.ENCRYPT_MODE, publicKey);
        byte[] encryptedBytes = cipher.doFinal(data.getBytes());
        return Base64.getEncoder().encodeToString(encryptedBytes); // Şifreli veriyi Base64 formatına çevir
    }

    // RSA ile şifre çözme
    public static String decrypt(String encryptedData, PrivateKey privateKey) throws Exception {
        Cipher cipher = Cipher.getInstance("RSA");
        cipher.init(Cipher.DECRYPT_MODE, privateKey);
        byte[] decodedBytes = Base64.getDecoder().decode(encryptedData); // Base64 formatını çöz
        byte[] decryptedBytes = cipher.doFinal(decodedBytes);
        return new String(decryptedBytes);
    }

    public static void main(String[] args) {
        try {
            // RSA anahtar çifti oluştur (Public ve Private Key)
            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
            keyPairGenerator.initialize(2048); // RSA-2048 bit anahtar
            KeyPair keyPair = keyPairGenerator.generateKeyPair();
            PublicKey publicKey = keyPair.getPublic();
            PrivateKey privateKey = keyPair.getPrivate();

            // Şifrelenecek veri
            String originalData = "Bu bir asenkron şifreleme örneğidir.";

            // Şifreleme işlemi (Public Key ile)
            String encryptedData = encrypt(originalData, publicKey);
            System.out.println("Şifrelenmiş Veri: " + encryptedData);

            // Şifre çözme işlemi (Private Key ile)
            String decryptedData = decrypt(encryptedData, privateKey);
            System.out.println("Çözülmüş Veri: " + decryptedData);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}