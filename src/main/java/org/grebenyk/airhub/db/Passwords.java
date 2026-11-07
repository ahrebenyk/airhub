package org.grebenyk.airhub.db;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Password hashing with PBKDF2 and a random salt. Stored format: pbkdf2$iterations$salt$hash (Base64),
 * so the iteration count can be raised later without breaking existing hashes.
 */
final class Passwords {
    static final String PREFIX = "pbkdf2$";
    private static final String ALGORITHM = "PBKDF2WithHmacSHA512";
    // OWASP recommendation for PBKDF2-HMAC-SHA512
    private static final int ITERATIONS = 210_000;
    private static final int SALT_BYTES = 16, HASH_BITS = 512;
    private static final int PARTS = 4, ITERATIONS_PART = 1, SALT_PART = 2, HASH_PART = 3;
    private static final SecureRandom RANDOM = new SecureRandom();

    private Passwords() {
    }

    static boolean isHashed(String stored) {
        return stored != null && stored.startsWith(PREFIX);
    }

    static String hash(String password) {
        byte[] salt = new byte[SALT_BYTES];
        RANDOM.nextBytes(salt);
        Base64.Encoder b64 = Base64.getEncoder();
        return PREFIX + ITERATIONS + "$" + b64.encodeToString(salt) + "$" + b64.encodeToString(pbkdf2(password, salt, ITERATIONS));
    }

    static boolean verify(String password, String stored) {
        if (password == null || !isHashed(stored)) return false;
        String[] parts = stored.split("\\$");
        if (parts.length != PARTS) return false;
        Base64.Decoder b64 = Base64.getDecoder();
        try {
            byte[] expected = b64.decode(parts[HASH_PART]);
            byte[] actual = pbkdf2(password, b64.decode(parts[SALT_PART]), Integer.parseInt(parts[ITERATIONS_PART]));
            // constant-time comparison does not reveal how many leading bytes matched
            return MessageDigest.isEqual(expected, actual);
        } catch (IllegalArgumentException e) {
            // a damaged stored value (bad number or Base64) means the password cannot match
            return false;
        }
    }

    private static byte[] pbkdf2(String password, byte[] salt, int iterations) {
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, HASH_BITS);
        try {
            return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException e) {
            // the algorithm is part of every JDK, so this is a broken runtime rather than a user error
            throw new IllegalStateException(e);
        } finally {
            spec.clearPassword();
        }
    }
}
