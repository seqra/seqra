package issues.i76;

public class BenchmarkTest00005_min {

    public void doPost() throws Exception  {
        javax.crypto.Cipher c = javax.crypto.Cipher.getInstance("DES/CBC/PKCS5Padding");

        // Prepare the cipher to encrypt
        javax.crypto.SecretKey key = javax.crypto.KeyGenerator.getInstance("DES").generateKey();
    }
}
