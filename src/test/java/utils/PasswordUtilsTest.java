package utils;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PasswordUtilsTest {

    /** SHA-256 of "secret", as stored by the previous implementation. */
    private static final String LEGACY_SECRET =
            "2bb80d537b1da3e38bd30361aa855686bde0eacd7162fef6a25fe97bf527a25b";

    @Test
    void hashVerifiesTheRightPasswordOnly() {
        String hash = PasswordUtils.hashPassword("secret");

        assertTrue(PasswordUtils.verify("secret", hash));
        assertFalse(PasswordUtils.verify("Secret", hash));
        assertFalse(PasswordUtils.verify("", hash));
    }

    @Test
    void samePasswordGetsDifferentSalts() {
        assertNotEquals(PasswordUtils.hashPassword("secret"), PasswordUtils.hashPassword("secret"));
    }

    @Test
    void hashIsNotTheUnsaltedDigest() {
        assertFalse(PasswordUtils.hashPassword("secret").contains(LEGACY_SECRET));
    }

    @Test
    void legacyDigestStillVerifiesAndNeedsRehash() {
        assertTrue(PasswordUtils.verify("secret", LEGACY_SECRET));
        assertTrue(PasswordUtils.verify("secret", LEGACY_SECRET.toUpperCase()));
        assertFalse(PasswordUtils.verify("other", LEGACY_SECRET));
        assertTrue(PasswordUtils.needsRehash(LEGACY_SECRET));
    }

    @Test
    void currentHashDoesNotNeedRehash() {
        assertFalse(PasswordUtils.needsRehash(PasswordUtils.hashPassword("secret")));
    }

    @Test
    void weakerIterationCountNeedsRehash() {
        String current = PasswordUtils.hashPassword("secret");
        String weaker = current.replace("$" + PasswordUtils.ITERATIONS + "$", "$1000$");
        assertTrue(PasswordUtils.needsRehash(weaker));
    }

    @Test
    void malformedOrMissingHashesNeverVerify() {
        assertFalse(PasswordUtils.verify("secret", null));
        assertFalse(PasswordUtils.verify(null, LEGACY_SECRET));
        assertFalse(PasswordUtils.verify("secret", "pbkdf2$abc"));
        assertFalse(PasswordUtils.verify("secret", "pbkdf2$10$***$***"));
        assertFalse(PasswordUtils.verify("secret", ""));
    }
}
