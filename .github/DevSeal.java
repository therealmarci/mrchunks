import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Seals dev builds with the release token of the dev channel (and opens them again). The repositories are public -
 * the token does not keep the code secret, it keeps people from installing unfinished builds without permission.
 *
 * <p>Format: {@code "MRDEV1"} | salt (16) | nonce (12) | AES-256-GCM ciphertext with 128-bit tag; the header is
 * authenticated too. The key comes from PBKDF2-HMAC-SHA256(token, salt, 210 000 rounds). A wrong token or a changed
 * file fails the tag check.
 *
 * <p>Single-file program for CI: {@code java DevSeal.java seal <in> <out>} / {@code open <in> <out>}, token in the
 * environment variable {@code DEV_TOKEN}. The same class (other package) is in MRadmin (starter + plugin updates).
 */
public final class DevSeal {

    private static final byte[] MAGIC = "MRDEV1".getBytes(StandardCharsets.US_ASCII);
    private static final int SALT = 16;
    private static final int NONCE = 12;
    private static final int TAG_BITS = 128;
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
        byte[] nonce = new byte[NONCE];
        random.nextBytes(salt);
        random.nextBytes(nonce);
        byte[] header = concat(MAGIC, salt, nonce);
        Cipher gcm = Cipher.getInstance("AES/GCM/NoPadding");
        gcm.init(Cipher.ENCRYPT_MODE, key(token, salt), new GCMParameterSpec(TAG_BITS, nonce));
        gcm.updateAAD(header);
        return concat(header, gcm.doFinal(plain));
    }

    public static byte[] open(byte[] sealed, String token) throws GeneralSecurityException {
        int head = MAGIC.length + SALT + NONCE;
        if (sealed.length < head + TAG_BITS / 8 || !Arrays.equals(Arrays.copyOf(sealed, MAGIC.length), MAGIC)) {
            throw new BadTokenException("not a sealed dev build");
        }
        byte[] salt = Arrays.copyOfRange(sealed, MAGIC.length, MAGIC.length + SALT);
        byte[] nonce = Arrays.copyOfRange(sealed, MAGIC.length + SALT, head);
        Cipher gcm = Cipher.getInstance("AES/GCM/NoPadding");
        gcm.init(Cipher.DECRYPT_MODE, key(token, salt), new GCMParameterSpec(TAG_BITS, nonce));
        gcm.updateAAD(sealed, 0, head);
        try {
            return gcm.doFinal(sealed, head, sealed.length - head);
        } catch (AEADBadTagException e) {
            throw new BadTokenException("wrong release token (or the file is damaged)");
        }
    }

    private static SecretKeySpec key(String token, byte[] salt) throws GeneralSecurityException {
        String t = token == null ? "" : token.trim();
        if (t.isEmpty()) {
            throw new BadTokenException("no release token");
        }
        PBEKeySpec spec = new PBEKeySpec(t.toCharArray(), salt, ROUNDS, 256);
        try {
            return new SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded(), "AES");
        } finally {
            spec.clearPassword();
        }
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
