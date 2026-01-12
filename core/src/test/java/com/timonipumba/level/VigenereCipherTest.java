package com.timonipumba.level;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for VigenereCipher.
 *
 * Tests verify correctness against known plaintext/key/ciphertext triples.
 */
class VigenereCipherTest {

    @Test
    void testDecryptAccessOkWithKeyKey() {
        // ACCESSOK encrypted with KEY = KGAOWQYO
        String ciphertext = "KGAOWQYO";
        String key = "KEY";
        String expected = "ACCESSOK";

        assertEquals(expected, VigenereCipher.decrypt(ciphertext, key));
    }

    @Test
    void testEncryptAccessOkWithKeyKey() {
        String plaintext = "ACCESSOK";
        String key = "KEY";
        String expected = "KGAOWQYO";

        assertEquals(expected, VigenereCipher.encrypt(plaintext, key));
    }

    @Test
    void testDecryptHelloWorldWithKeyKey() {
        // Well-known example
        String ciphertext = "RIJVSUYVJN";
        String key = "KEY";
        String expected = "HELLOWORLD";

        assertEquals(expected, VigenereCipher.decrypt(ciphertext, key));
    }

    @Test
    void testEncryptHelloWorldWithKeyKey() {
        String plaintext = "HELLOWORLD";
        String key = "KEY";
        String expected = "RIJVSUYVJN";

        assertEquals(expected, VigenereCipher.encrypt(plaintext, key));
    }

    @Test
    void testDecryptAttackAtDawnWithKeyLemon() {
        // Classic Vigenère example
        String ciphertext = "LXFOPVEFRNHR";
        String key = "LEMON";
        String expected = "ATTACKATDAWN";

        assertEquals(expected, VigenereCipher.decrypt(ciphertext, key));
    }

    @Test
    void testEncryptAttackAtDawnWithKeyLemon() {
        String plaintext = "ATTACKATDAWN";
        String key = "LEMON";
        String expected = "LXFOPVEFRNHR";

        assertEquals(expected, VigenereCipher.encrypt(plaintext, key));
    }

    @Test
    void testDecryptWithLowerCase() {
        // Should be case-insensitive
        String ciphertext = "kgaowqyo";
        String key = "key";
        String expected = "ACCESSOK";

        assertEquals(expected, VigenereCipher.decrypt(ciphertext, key));
    }

    @Test
    void testEncryptWithLowerCase() {
        String plaintext = "accessok";
        String key = "key";
        String expected = "KGAOWQYO";

        assertEquals(expected, VigenereCipher.encrypt(plaintext, key));
    }

    @Test
    void testDecryptWithMixedCase() {
        String ciphertext = "KgAoWqYo";
        String key = "KeY";
        String expected = "ACCESSOK";

        assertEquals(expected, VigenereCipher.decrypt(ciphertext, key));
    }

    @Test
    void testDecryptWithSpaces() {
        // Non-alphabetic characters should be preserved
        String ciphertext = "RIJV SUYV JN";
        String key = "KEY";
        String expected = "HELL OWOR LD";

        assertEquals(expected, VigenereCipher.decrypt(ciphertext, key));
    }

    @Test
    void testEncryptWithSpaces() {
        String plaintext = "HELLO WORLD";
        String key = "KEY";
        // Key only advances on letters, so spaces don't affect key position
        String expected = "RIJVS UYVJN";

        assertEquals(expected, VigenereCipher.encrypt(plaintext, key));
    }

    @Test
    void testRoundTrip() {
        String[] plaintexts = {"ACCESSOK", "HELLOWORLD", "THEQUICKBROWNFOX", "ZYXWV"};
        String[] keys = {"KEY", "LEMON", "A", "SECRET"};

        for (String plaintext : plaintexts) {
            for (String key : keys) {
                String encrypted = VigenereCipher.encrypt(plaintext, key);
                String decrypted = VigenereCipher.decrypt(encrypted, key);
                assertEquals(plaintext, decrypted,
                    "Round trip failed for plaintext '" + plaintext + "' with key '" + key + "'");
            }
        }
    }

    @Test
    void testDecryptNullCiphertext() {
        assertEquals("", VigenereCipher.decrypt(null, "KEY"));
    }

    @Test
    void testDecryptNullKey() {
        assertEquals("", VigenereCipher.decrypt("HELLO", null));
    }

    @Test
    void testDecryptEmptyKey() {
        assertEquals("", VigenereCipher.decrypt("HELLO", ""));
    }

    @Test
    void testEncryptNullPlaintext() {
        assertEquals("", VigenereCipher.encrypt(null, "KEY"));
    }

    @Test
    void testEncryptNullKey() {
        assertEquals("", VigenereCipher.encrypt("HELLO", null));
    }

    @Test
    void testEncryptEmptyKey() {
        assertEquals("", VigenereCipher.encrypt("HELLO", ""));
    }

    @Test
    void testDecryptEmptyCiphertext() {
        assertEquals("", VigenereCipher.decrypt("", "KEY"));
    }

    @Test
    void testEncryptEmptyPlaintext() {
        assertEquals("", VigenereCipher.encrypt("", "KEY"));
    }

    @Test
    void testNormalizeRemovesSpaces() {
        assertEquals("HELLOWORLD", VigenereCipher.normalize("hello world"));
        assertEquals("HELLOWORLD", VigenereCipher.normalize("  HELLO   WORLD  "));
    }

    @Test
    void testNormalizeConvertsToUpperCase() {
        assertEquals("ACCESSOK", VigenereCipher.normalize("accessok"));
        assertEquals("ACCESSOK", VigenereCipher.normalize("AccessOk"));
    }

    @Test
    void testNormalizeNull() {
        assertEquals("", VigenereCipher.normalize(null));
    }

    @Test
    void testNormalizeEmpty() {
        assertEquals("", VigenereCipher.normalize(""));
    }

    @Test
    void testDecryptWithKeyLongerThanCiphertext() {
        String plaintext = "AB";
        String key = "VERYLONG";
        String encrypted = VigenereCipher.encrypt(plaintext, key);
        String decrypted = VigenereCipher.decrypt(encrypted, key);
        assertEquals(plaintext, decrypted);
    }

    @Test
    void testDecryptSingleCharKey() {
        // Single char key = Caesar cipher shift
        String plaintext = "ABC";
        String key = "B";  // Shift by 1
        String expected = "BCD";

        assertEquals(expected, VigenereCipher.encrypt(plaintext, key));
        assertEquals(plaintext, VigenereCipher.decrypt(expected, key));
    }

    @Test
    void testDecryptWithNumbers() {
        // Numbers should pass through
        String ciphertext = "R123I456JV";
        String key = "KEY";
        // Only letters are decrypted
        String expected = "H123E456LL";

        assertEquals(expected, VigenereCipher.decrypt(ciphertext, key));
    }
}
