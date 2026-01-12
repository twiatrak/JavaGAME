package com.timonipumba.level;

/**
 * Implementation of the Vigenère cipher for encryption and decryption.
 *
 * Uses the standard A–Z alphabet mapping (A=0, B=1, ..., Z=25).
 * - Encryption: C = (P + K) mod 26
 * - Decryption: P = (C - K) mod 26
 *
 * Non-alphabetic characters are passed through unchanged.
 * The key is repeated as needed to match the length of the text (only advancing
 * the key index for alphabetic characters).
 */
public class VigenereCipher {

    private VigenereCipher() {
        // Utility class - prevent instantiation
    }

    /**
     * Decrypt a ciphertext using the Vigenère cipher.
     *
     * @param ciphertext The encrypted text (case-insensitive)
     * @param key The decryption key (case-insensitive)
     * @return The decrypted plaintext in uppercase, or empty string if inputs are invalid
     */
    public static String decrypt(String ciphertext, String key) {
        if (ciphertext == null || key == null || key.isEmpty()) {
            return "";
        }

        StringBuilder result = new StringBuilder();
        String upperKey = key.toUpperCase();
        int keyLen = upperKey.length();
        int keyIdx = 0;

        for (char c : ciphertext.toUpperCase().toCharArray()) {
            if (Character.isLetter(c)) {
                int cVal = c - 'A';
                int kVal = upperKey.charAt(keyIdx % keyLen) - 'A';
                // Decryption: P = (C - K) mod 26
                int pVal = (cVal - kVal + 26) % 26;
                result.append((char) (pVal + 'A'));
                keyIdx++;
            } else {
                // Pass through non-alphabetic characters
                result.append(c);
            }
        }

        return result.toString();
    }

    /**
     * Encrypt a plaintext using the Vigenère cipher.
     *
     * @param plaintext The text to encrypt (case-insensitive)
     * @param key The encryption key (case-insensitive)
     * @return The encrypted ciphertext in uppercase, or empty string if inputs are invalid
     */
    public static String encrypt(String plaintext, String key) {
        if (plaintext == null || key == null || key.isEmpty()) {
            return "";
        }

        StringBuilder result = new StringBuilder();
        String upperKey = key.toUpperCase();
        int keyLen = upperKey.length();
        int keyIdx = 0;

        for (char p : plaintext.toUpperCase().toCharArray()) {
            if (Character.isLetter(p)) {
                int pVal = p - 'A';
                int kVal = upperKey.charAt(keyIdx % keyLen) - 'A';
                // Encryption: C = (P + K) mod 26
                int cVal = (pVal + kVal) % 26;
                result.append((char) (cVal + 'A'));
                keyIdx++;
            } else {
                // Pass through non-alphabetic characters
                result.append(p);
            }
        }

        return result.toString();
    }

    /**
     * Normalize input by removing spaces and converting to uppercase.
     * Useful for comparing player input against expected answers.
     *
     * @param input The input string to normalize
     * @return Normalized string (uppercase, no spaces), or empty string if null
     */
    public static String normalize(String input) {
        if (input == null) {
            return "";
        }
        return input.replaceAll("\\s+", "").toUpperCase();
    }
}
