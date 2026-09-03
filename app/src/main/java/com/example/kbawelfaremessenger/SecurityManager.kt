package com.example.kbawelfaremessenger

import android.content.Context
import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

class SecurityManager(private val context: Context) {

    companion object {
        private const val PREFS_NAME = "secure_auth"

        private const val KEY_USER_ID = "user_id"
        private const val KEY_PASSWORD_HASH = "password_hash"
        private const val KEY_PASSWORD_SALT = "password_salt"
        private const val KEY_SETUP_COMPLETE = "setup_complete"

        private const val ITERATIONS = 120_000
        private const val KEY_LENGTH = 256
        private const val SALT_LENGTH = 32
    }

    private val preferences =
        context.getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE
        )

    /**
     * Returns true after the first user account has been created.
     */
    fun isSetupComplete(): Boolean {
        return preferences.getBoolean(
            KEY_SETUP_COMPLETE,
            false
        )
    }

    /**
     * Returns the configured User ID.
     */
    fun getUserId(): String {
        return preferences.getString(
            KEY_USER_ID,
            ""
        ) ?: ""
    }

    /**
     * Creates the first local user account.
     *
     * The actual password is never stored.
     * Only a salted PBKDF2 hash is stored.
     */
    fun createUser(
        userId: String,
        password: String
    ): Boolean {

        if (userId.isBlank()) {
            return false
        }

        if (password.isEmpty()) {
            return false
        }

        // Only one local user/password setup is allowed.
        if (isSetupComplete()) {
            return false
        }

        return try {

            val salt = generateSalt()

            val passwordHash =
                hashPassword(
                    password = password,
                    salt = salt
                )

            preferences.edit()
                .putString(
                    KEY_USER_ID,
                    userId.trim()
                )
                .putString(
                    KEY_PASSWORD_SALT,
                    encode(salt)
                )
                .putString(
                    KEY_PASSWORD_HASH,
                    encode(passwordHash)
                )
                .putBoolean(
                    KEY_SETUP_COMPLETE,
                    true
                )
                .apply()

            true

        } catch (_: Exception) {
            false
        }
    }

    /**
     * Authenticates the supplied User ID and password.
     */
    fun authenticate(
        userId: String,
        password: String
    ): Boolean {

        if (!isSetupComplete()) {
            return false
        }

        if (userId.isBlank() || password.isEmpty()) {
            return false
        }

        val savedUserId =
            preferences.getString(
                KEY_USER_ID,
                null
            ) ?: return false

        if (!savedUserId.equals(
                userId.trim(),
                ignoreCase = true
            )
        ) {
            return false
        }

        val savedSalt =
            preferences.getString(
                KEY_PASSWORD_SALT,
                null
            ) ?: return false

        val savedHash =
            preferences.getString(
                KEY_PASSWORD_HASH,
                null
            ) ?: return false

        return try {

            val salt =
                decode(savedSalt)

            val expectedHash =
                decode(savedHash)

            val actualHash =
                hashPassword(
                    password = password,
                    salt = salt
                )

            MessageDigest.isEqual(
                expectedHash,
                actualHash
            )

        } catch (_: Exception) {
            false
        }
    }

    /**
     * Generates a cryptographically secure random salt.
     */
    private fun generateSalt(): ByteArray {

        return ByteArray(SALT_LENGTH).also {
            SecureRandom().nextBytes(it)
        }
    }

    /**
     * Creates a PBKDF2-HMAC-SHA256 password hash.
     */
    private fun hashPassword(
        password: String,
        salt: ByteArray
    ): ByteArray {

        val keySpec =
            PBEKeySpec(
                password.toCharArray(),
                salt,
                ITERATIONS,
                KEY_LENGTH
            )

        return try {

            SecretKeyFactory
                .getInstance(
                    "PBKDF2WithHmacSHA256"
                )
                .generateSecret(keySpec)
                .encoded

        } finally {

            keySpec.clearPassword()
        }
    }

    /**
     * Android-compatible Base64 encoding.
     */
    private fun encode(
        value: ByteArray
    ): String {

        return Base64.encodeToString(
            value,
            Base64.NO_WRAP
        )
    }

    /**
     * Android-compatible Base64 decoding.
     */
    private fun decode(
        value: String
    ): ByteArray {

        return Base64.decode(
            value,
            Base64.NO_WRAP
        )
    }
}
