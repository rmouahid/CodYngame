package utils;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * Password hashing and verification.
 *
 * <p>New hashes use PBKDF2 with HMAC-SHA256, a random 16-byte salt per password and
 * {@value #ITERATIONS} iterations. They are stored as
 * {@code pbkdf2$<iterations>$<salt base64>$<hash base64>}, so the parameters can be
 * raised later without breaking existing accounts.</p>
 *
 * <p>Accounts created before this format still hold an unsalted SHA-256 hex digest.
 * {@link #verify} accepts it, and {@link #needsRehash} tells the caller to replace it
 * with a PBKDF2 hash on the next successful login.</p>
 */
public final class PasswordUtils {

    private static final String PREFIX = "pbkdf2";
    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    static final int ITERATIONS = 210_000;
    private static final int SALT_BYTES = 16;
    private static final int KEY_BITS = 256;
    private static final SecureRandom RANDOM = new SecureRandom();

    private PasswordUtils() {
    }

    /**
     * Hashes a password with a fresh random salt.
     *
     * @param password the plaintext password
     * @return the encoded hash, safe to store in {@code User.password_hash}
     */
    public static String hashPassword(String password) {
        byte[] salt = new byte[SALT_BYTES];
        RANDOM.nextBytes(salt);
        byte[] hash = pbkdf2(password, salt, ITERATIONS);
        Base64.Encoder b64 = Base64.getEncoder();
        return PREFIX + "$" + ITERATIONS + "$" + b64.encodeToString(salt) + "$" + b64.encodeToString(hash);
    }

    /**
     * Checks a plaintext password against a stored hash (PBKDF2 or legacy SHA-256).
     *
     * @param password the plaintext password entered by the user
     * @param stored   the value of {@code User.password_hash}, may be {@code null}
     * @return {@code true} if the password matches
     */
    public static boolean verify(String password, String stored) {
        if (password == null || stored == null) {
            return false;
        }
        if (stored.startsWith(PREFIX + "$")) {
            String[] parts = stored.split("\\$");
            if (parts.length != 4) {
                return false;
            }
            try {
                int iterations = Integer.parseInt(parts[1]);
                byte[] salt = Base64.getDecoder().decode(parts[2]);
                byte[] expected = Base64.getDecoder().decode(parts[3]);
                return MessageDigest.isEqual(expected, pbkdf2(password, salt, iterations));
            } catch (IllegalArgumentException e) {
                return false;
            }
        }
        byte[] legacy = sha256Hex(password).getBytes(StandardCharsets.US_ASCII);
        return MessageDigest.isEqual(legacy, stored.toLowerCase().getBytes(StandardCharsets.US_ASCII));
    }

    /**
     * Tells whether a stored hash should be replaced by a fresh {@link #hashPassword} one:
     * legacy SHA-256 digests and PBKDF2 hashes with fewer iterations than the current setting.
     *
     * @param stored the value of {@code User.password_hash}
     * @return {@code true} if the hash should be upgraded after a successful login
     */
    public static boolean needsRehash(String stored) {
        if (stored == null || !stored.startsWith(PREFIX + "$")) {
            return true;
        }
        String[] parts = stored.split("\\$");
        try {
            return parts.length != 4 || Integer.parseInt(parts[1]) < ITERATIONS;
        } catch (NumberFormatException e) {
            return true;
        }
    }

    private static byte[] pbkdf2(String password, byte[] salt, int iterations) {
        KeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, KEY_BITS);
        try {
            return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("PBKDF2 is not available on this JVM", e);
        }
    }

    private static String sha256Hex(String password) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(password.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("SHA-256 is not available on this JVM", e);
        }
    }
}
