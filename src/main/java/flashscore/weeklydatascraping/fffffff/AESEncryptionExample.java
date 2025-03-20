package flashscore.weeklydatascraping.fffffff;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import java.util.Base64;

public class AESEncryptionExample {

    // AES ile şifreleme
    public static String encrypt(String data, SecretKey secretKey) throws Exception {
        Cipher cipher = Cipher.getInstance("AES");
        cipher.init(Cipher.ENCRYPT_MODE, secretKey);
        byte[] encryptedBytes = cipher.doFinal(data.getBytes());
        return Base64.getEncoder().encodeToString(encryptedBytes); // Şifreli veriyi Base64 formatına çevir
    }

    // AES ile şifre çözme
    public static String decrypt(String encryptedData, SecretKey secretKey) throws Exception {
        Cipher cipher = Cipher.getInstance("AES");
        cipher.init(Cipher.DECRYPT_MODE, secretKey);
        byte[] decodedBytes = Base64.getDecoder().decode(encryptedData); // Base64 formatını çöz
        byte[] decryptedBytes = cipher.doFinal(decodedBytes);
        return new String(decryptedBytes);
    }

    public static void main(String[] args) {
        try {
            // AES anahtarını oluştur
            KeyGenerator keyGenerator = KeyGenerator.getInstance("AES");
            keyGenerator.init(128); // AES-128 bit anahtar
            SecretKey secretKey = keyGenerator.generateKey();

            // Şifrelenecek veri
            String originalData = "Bu bir senkron şifreleme örneğidir.";

            // Şifreleme işlemi
            String encryptedData = encrypt(originalData, secretKey);
            System.out.println("Şifrelenmiş Veri: " + encryptedData);

            // Şifre çözme işlemi
            String decryptedData = decrypt(encryptedData, secretKey);
            System.out.println("Çözülmüş Veri: " + decryptedData);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
