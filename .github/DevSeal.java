import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Seals dev builds with the release token of the dev channel (and opens them again). The repositories are public -
 * the token does not keep the code secret, it keeps people from installing unfinished builds without permission.
 *
 * <p>Format: {@code "MRDEV1"} | salt (16) | iv (16) | AES-256-CBC ciphertext | HMAC-SHA256 (32) over everything
 * before it. Both keys come from PBKDF2-HMAC-SHA256(token, salt, 210 000 rounds, 64 bytes). A wrong token or a changed
 * file fails the HMAC check.
 *
 * <p>Single-file program for CI: {@code java DevSeal.java seal <in> <out>} / {@code open <in> <out>}, token in the
 * environment variable {@code DEV_TOKEN}. The same class (other package) is in MRadmin (starter + plugin updates).
 */
public final class DevSeal {

    private static final byte[] MAGIC = "MRDEV1".getBytes(StandardCharsets.US_ASCII);
    private static final int SALT = 16;
    private static final int IV = 16;
    private static final int MAC = 32;
    private static final int ROUNDS = 210_000;

    /** Wrong token or damaged file. */
    public static final class BadTokenException extends GeneralSecurityException {
        BadTokenException(String message) {
            super(message);
        }
    }

    private DevSeal() {
    }

    public static byte[] seal(byte[] plain, String token) throws GeneralSecurityException {
        SecureRandom random = new SecureRandom();
        byte[] salt = new byte[SALT];
        byte[] iv = new byte[IV];
        random.nextBytes(salt);
        random.nextBytes(iv);
        byte[][] keys = keys(token, salt);
        Cipher aes = Cipher.getInstance("AES/CBC/PKCS5Padding");
        aes.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(keys[0], "AES"), new IvParameterSpec(iv));
        byte[] cipher = aes.doFinal(plain);
        byte[] head = concat(MAGIC, salt, iv, cipher);
        return concat(head, mac(keys[1], head));
    }

    public static byte[] open(byte[] sealed, String token) throws GeneralSecurityException {
        int min = MAGIC.length + SALT + IV + 16 + MAC;
        if (sealed.length < min || !Arrays.equals(Arrays.copyOf(sealed, MAGIC.length), MAGIC)) {
            throw new BadTokenException("not a sealed dev build");
        }
        byte[] salt = Arrays.copyOfRange(sealed, MAGIC.length, MAGIC.length + SALT);
        byte[] iv = Arrays.copyOfRange(sealed, MAGIC.length + SALT, MAGIC.length + SALT + IV);
        byte[] head = Arrays.copyOf(sealed, sealed.length - MAC);
        byte[] mac = Arrays.copyOfRange(sealed, sealed.length - MAC, sealed.length);
        byte[][] keys = keys(token, salt);
        if (!MessageDigest.isEqual(mac, mac(keys[1], head))) {
            throw new BadTokenException("wrong release token (or the file is damaged)");
        }
        Cipher aes = Cipher.getInstance("AES/CBC/PKCS5Padding");
        aes.init(Cipher.DECRYPT_MODE, new SecretKeySpec(keys[0], "AES"), new IvParameterSpec(iv));
        return aes.doFinal(head, MAGIC.length + SALT + IV, head.length - MAGIC.length - SALT - IV);
    }

    private static byte[][] keys(String token, byte[] salt) throws GeneralSecurityException {
        String t = token == null ? "" : token.trim();
        if (t.isEmpty()) {
            throw new BadTokenException("no release token");
        }
        PBEKeySpec spec = new PBEKeySpec(t.toCharArray(), salt, ROUNDS, 512);
        try {
            byte[] both = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
            return new byte[][]{Arrays.copyOf(both, 32), Arrays.copyOfRange(both, 32, 64)};
        } finally {
            spec.clearPassword();
        }
    }

    private static byte[] mac(byte[] key, byte[] data) throws GeneralSecurityException {
        Mac hmac = Mac.getInstance("HmacSHA256");
        hmac.init(new SecretKeySpec(key, "HmacSHA256"));
        return hmac.doFinal(data);
    }

    private static byte[] concat(byte[]... parts) {
        int n = 0;
        for (byte[] p : parts) {
            n += p.length;
        }
        byte[] out = new byte[n];
        int at = 0;
        for (byte[] p : parts) {
            System.arraycopy(p, 0, out, at, p.length);
            at += p.length;
        }
        return out;
    }

    public static void main(String[] args) throws IOException, GeneralSecurityException {
        if (args.length != 3 || !(args[0].equals("seal") || args[0].equals("open"))) {
            System.err.println("usage: java DevSeal.java seal|open <in> <out>   (token in DEV_TOKEN)");
            System.exit(2);
        }
        String token = System.getenv("DEV_TOKEN");
        byte[] in = Files.readAllBytes(Path.of(args[1]));
        byte[] out = args[0].equals("seal") ? seal(in, token) : open(in, token);
        Files.write(Path.of(args[2]), out);
        System.out.println(args[0] + "ed " + args[1] + " -> " + args[2] + " (" + out.length + " bytes)");
    }
}
