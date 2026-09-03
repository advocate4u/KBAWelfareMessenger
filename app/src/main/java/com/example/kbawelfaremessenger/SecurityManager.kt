package com.example.kbawelfaremessenger

import android.content.Context
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

enum class UserRole {
    ADMIN,
    USER
}

data class LocalUser(
    val userId: String,
    val role: UserRole
)

object SecurityManager {
    private const val PREF_NAME = "secure_auth"
    private const val KEY_USER_ID = "user_id"
    private const val KEY_PASSWORD_HASH = "password_hash"
    private const val KEY_PASSWORD_SALT = "password_salt"
    private const val KEY_SETUP_COMPLETE = "setup_complete"
    private const val KEY_USERS = "users"
    private const val KEY_CURRENT_USER = "current_user"

    private const val ITERATIONS = 120000
    private const val KEY_LENGTH = 256
    private const val SALT_LENGTH = 32

    private fun preferences(context: Context) =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun hasUser(context: Context): Boolean = preferences(context).getBoolean(KEY_SETUP_COMPLETE, false)

    /** First installation creates exactly one ADMIN account. */
    fun createUser(context: Context, userId: String, password: String): Boolean {
        val id = userId.trim()
        if (id.isBlank() || password.length < 6 || hasUser(context)) return false
        val salt = ByteArray(SALT_LENGTH).also { SecureRandom().nextBytes(it) }
        val hash = hashPassword(password, salt)
        val record = JSONObject()
            .put("userId", id)
            .put("role", UserRole.ADMIN.name)
            .put("hash", encode(hash))
            .put("salt", encode(salt))
        preferences(context).edit()
            .putString(KEY_USERS, JSONArray().put(record).toString())
            .putString(KEY_USER_ID, id)
            .putString(KEY_PASSWORD_HASH, encode(hash))
            .putString(KEY_PASSWORD_SALT, encode(salt))
            .putBoolean(KEY_SETUP_COMPLETE, true)
            .apply()
        return true
    }

    fun authenticate(context: Context, userId: String, password: String): Boolean {
        val id = userId.trim()
        val user = findUser(context, id) ?: return false
        val ok = try {
            MessageDigest.isEqual(
                Base64.decode(user.getString("hash"), Base64.NO_WRAP),
                hashPassword(password, Base64.decode(user.getString("salt"), Base64.NO_WRAP))
            )
        } catch (_: Exception) { false }
        if (ok) preferences(context).edit().putString(KEY_CURRENT_USER, id).apply()
        return ok
    }

    /** Adds a normal USER account. Only the authenticated ADMIN may call this. */
    fun addUser(context: Context, userId: String, password: String, role: UserRole = UserRole.USER): Boolean {
        if (!isAdmin(context)) return false
        val id = userId.trim()
        if (id.isBlank() || password.length < 6 || findUser(context, id) != null) return false
        val salt = ByteArray(SALT_LENGTH).also { SecureRandom().nextBytes(it) }
        val hash = hashPassword(password, salt)
        val users = readUsers(context)
        users.put(JSONObject()
            .put("userId", id)
            .put("role", role.name)
            .put("hash", encode(hash))
            .put("salt", encode(salt)))
        saveUsers(context, users)
        return true
    }

    fun listUsers(context: Context): List<LocalUser> = buildList {
        val users = readUsers(context)
        for (i in 0 until users.length()) {
            val o = users.optJSONObject(i) ?: continue
            val id = o.optString("userId").trim()
            if (id.isBlank()) continue
            val role = runCatching { UserRole.valueOf(o.optString("role", UserRole.USER.name)) }.getOrDefault(UserRole.USER)
            add(LocalUser(id, role))
        }
    }

    fun deleteUser(context: Context, userId: String): Boolean {
        if (!isAdmin(context)) return false
        val id = userId.trim()
        if (id.isBlank() || id.equals(currentUserId(context), ignoreCase = false)) return false
        val users = readUsers(context)
        var removed = false
        for (i in users.length() - 1 downTo 0) {
            if (users.optJSONObject(i)?.optString("userId") == id) {
                users.remove(i)
                removed = true
            }
        }
        if (removed) saveUsers(context, users)
        return removed
    }

    fun currentUserId(context: Context): String? = preferences(context).getString(KEY_CURRENT_USER, null)

    fun currentUser(context: Context): LocalUser? = currentUserId(context)?.let { id -> listUsers(context).firstOrNull { it.userId == id } }

    fun currentRole(context: Context): UserRole? = currentUser(context)?.role

    fun isAdmin(context: Context): Boolean = currentRole(context) == UserRole.ADMIN

    fun logout(context: Context) {
        preferences(context).edit().remove(KEY_CURRENT_USER).apply()
    }

    private fun findUser(context: Context, userId: String): JSONObject? {
        val users = readUsers(context)
        for (i in 0 until users.length()) {
            val o = users.optJSONObject(i) ?: continue
            if (o.optString("userId") == userId) return o
        }
        return null
    }

    /** Migrates the original single-account storage into the new users list. */
    private fun readUsers(context: Context): JSONArray {
        val prefs = preferences(context)
        val stored = prefs.getString(KEY_USERS, null)
        if (!stored.isNullOrBlank()) return try { JSONArray(stored) } catch (_: Exception) { JSONArray() }

        val oldId = prefs.getString(KEY_USER_ID, null)
        val oldHash = prefs.getString(KEY_PASSWORD_HASH, null)
        val oldSalt = prefs.getString(KEY_PASSWORD_SALT, null)
        if (!oldId.isNullOrBlank() && !oldHash.isNullOrBlank() && !oldSalt.isNullOrBlank()) {
            val migrated = JSONArray().put(JSONObject()
                .put("userId", oldId)
                .put("role", UserRole.ADMIN.name)
                .put("hash", oldHash)
                .put("salt", oldSalt))
            prefs.edit().putString(KEY_USERS, migrated.toString()).apply()
            return migrated
        }
        return JSONArray()
    }

    private fun saveUsers(context: Context, users: JSONArray) {
        preferences(context).edit().putString(KEY_USERS, users.toString()).apply()
    }

    private fun hashPassword(password: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_LENGTH)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally { spec.clearPassword() }
    }

    private fun encode(value: ByteArray): String = Base64.encodeToString(value, Base64.NO_WRAP)
}
