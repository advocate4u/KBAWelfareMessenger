package com.example.kbawelfaremessenger

import android.content.Context
import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object SecurityManager {

    private const val PREF_NAME = "secure_auth"

    private const val KEY_USER_ID = "user_id"
    private const val KEY_PASSWORD_HASH = "password_hash"
    private const val KEY_PASSWORD_SALT = "password_salt"
    private const val KEY_SETUP_COMPLETE = "setup_complete"

    private const val ITERATIONS = 120000
    private const val KEY_LENGTH = 256
    private const val SALT_LENGTH = 32

    private fun preferences(context: Context) =
        context.getSharedPreferences(
            PREF_NAME,
            Context.MODE_PRIVATE
        )

    /**
     * Returns true when a local user has already been created.
     */
    fun hasUser(context: Context): Boolean {

        val prefs =
            preferences(context)

        return prefs.getBoolean(
            KEY_SETUP_COMPLETE,
            false
        )
    }

    /**
     * Creates the local user.
     *
     * The password is never stored directly.
     * Only a PBKDF2 hash and random salt are stored.
     */
    fun createUser(
        context: Context,
        userId: String,
        password: String
    ): Boolean {

        if (userId.isBlank()) {
            return false
        }

        if (password.length < 6) {
            return false
        }

        val salt =
            ByteArray(SALT_LENGTH)

        SecureRandom().nextBytes(salt)

        val hash =
            hashPassword(
                password,
                salt
            )

        preferences(context)
            .edit()
            .putString(
                KEY_USER_ID,
                userId.trim()
            )
            .putString(
                KEY_PASSWORD_HASH,
                encode(salt = hash)
            )
            .putString(
                KEY_PASSWORD_SALT,
                encode(salt = salt)
            )
            .putBoolean(
                KEY_SETUP_COMPLETE,
                true
            )
            .apply()

        return true
    }

    /**
     * Authenticates the local user.
     */
    fun authenticate(
        context: Context,
        userId: String,
        password: String
    ): Boolean {

        val prefs =
            preferences(context)

        if (
            !prefs.getBoolean(
                KEY_SETUP_COMPLETE,
                false
            )
        ) {
            return false
        }

        val savedUserId =
            prefs.getString(
                KEY_USER_ID,
                null
            )
                ?: return false

        if (
            !savedUserId.equals(
                userId.trim(),
                ignoreCase = false
            )
        ) {
            return false
        }

        val savedHash =
            prefs.getString(
                KEY_PASSWORD_HASH,
                null
            )
                ?: return false

        val savedSalt =
            prefs.getString(
                KEY_PASSWORD_SALT,
                null
            )
                ?: return false

        return try {

            val salt =
                Base64.decode(
                    savedSalt,
                    Base64.NO_WRAP
                )

            val expectedHash =
                Base64.decode(
                    savedHash,
                    Base64.NO_WRAP
                )

            val actualHash =
                hashPassword(
                    password,
                    salt
                )

            MessageDigest.isEqual(
                expectedHash,
                actualHash
            )

        } catch (
            e: Exception
        ) {

            false
        }
    }

    private fun hashPassword(
        password: String,
        salt: ByteArray
    ): ByteArray {

        val spec =
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
                .generateSecret(spec)
                .encoded

        } finally {

            spec.clearPassword()
        }
    }

    private fun encode(
        salt: ByteArray
    ): String {

        return Base64.encodeToString(
            salt,
            Base64.NO_WRAP
        )
    }
}
