package org.grebenyk.airhub.db;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PasswordsTest {

    @Test
    void hashIsVerifiedOnlyWithTheSamePassword() {
        String hash = Passwords.hash("secret");
        assertTrue(Passwords.isHashed(hash));
        assertTrue(Passwords.verify("secret", hash));
        assertFalse(Passwords.verify("Secret", hash));
        assertFalse(Passwords.verify("", hash));
    }

    @Test
    void samePasswordGetsDifferentHashesBecauseOfSalt() {
        assertNotEquals(Passwords.hash("secret"), Passwords.hash("secret"));
    }

    @Test
    void plainOrMalformedValuesAreNotAccepted() {
        assertFalse(Passwords.isHashed("admin"));
        assertFalse(Passwords.isHashed(null));
        assertFalse(Passwords.verify("admin", "admin"));
        assertFalse(Passwords.verify("admin", "pbkdf2$broken"));
        assertFalse(Passwords.verify("admin", "pbkdf2$many$AAAA$AAAA"));
        assertFalse(Passwords.verify("admin", "pbkdf2$1000$not base64!$AAAA"));
        assertFalse(Passwords.verify(null, Passwords.hash("admin")));
    }
}
