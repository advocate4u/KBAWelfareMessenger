package com.example.kbawelfaremessenger

import android.content.Context
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

enum class UserRole { SUPER_ADMIN, ADMIN, USER }
data class LocalUser(val userId: String, val role: UserRole)

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

    private fun preferences(c: Context) = c.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    fun hasUser(c: Context) = preferences(c).getBoolean(KEY_SETUP_COMPLETE, false)
    private fun normalizePhone(v: String) = v.filter(Char::isDigit).takeLast(10)

    fun isAdminPhoneNumber(phoneNumber: String) = false
    fun isSuperAdminPhoneNumber(phoneNumber: String) = false

    fun createUser(c: Context, userId: String, password: String): Boolean {
        val id = userId.trim()
        if (id.isBlank() || password.length < 6 || hasUser(c)) return false
        val license = LicenseManager.getValidLicense(c) ?: return false
        if (normalizePhone(license.phone) != normalizePhone(id)) return false
        val salt = ByteArray(SALT_LENGTH).also { SecureRandom().nextBytes(it) }
        val hash = hashPassword(password, salt)
        val record = JSONObject().put("userId", id).put("role", license.role.name).put("hash", encode(hash)).put("salt", encode(salt))
        preferences(c).edit()
            .putString(KEY_USERS, JSONArray().put(record).toString())
            .putString(KEY_USER_ID, id)
            .putString(KEY_PASSWORD_HASH, encode(hash))
            .putString(KEY_PASSWORD_SALT, encode(salt))
            .putString(KEY_CURRENT_USER, id)
            .putBoolean(KEY_SETUP_COMPLETE, true)
            .apply()
        return true
    }

    /** Existing users authenticate with their own account ID. The license must be valid, but its phone is not the user's login ID. */
    fun authenticate(c: Context, userId: String, password: String): Boolean {
        val id = userId.trim()
        val user = findUser(c, id) ?: return false
        if (LicenseManager.getValidLicense(c) == null) return false
        val ok = try {
            MessageDigest.isEqual(
                Base64.decode(user.getString("hash"), Base64.NO_WRAP),
                hashPassword(password, Base64.decode(user.getString("salt"), Base64.NO_WRAP))
            )
        } catch (_: Exception) { false }
        if (!ok) return false
        // Keep the account's assigned role. License permissions remain authoritative for features.
        preferences(c).edit().putString(KEY_CURRENT_USER, id).apply()
        return true
    }

    /** SUPER_ADMIN can create ADMIN or USER. ADMIN can create USER only. */
    fun addUser(c: Context, userId: String, password: String, role: UserRole = UserRole.USER): Boolean {
        val actor = currentRole(c) ?: return false
        if (actor != UserRole.SUPER_ADMIN && actor != UserRole.ADMIN) return false
        if (role == UserRole.SUPER_ADMIN) return false
        if (actor == UserRole.ADMIN && role != UserRole.USER) return false
        val id = userId.trim()
        if (id.isBlank() || password.length < 6 || findUser(c, id) != null) return false
        val salt = ByteArray(SALT_LENGTH).also { SecureRandom().nextBytes(it) }
        val hash = hashPassword(password, salt)
        val users = readUsers(c)
        users.put(JSONObject().put("userId", id).put("role", role.name).put("hash", encode(hash)).put("salt", encode(salt)))
        saveUsers(c, users)
        return true
    }

    fun listUsers(c: Context): List<LocalUser> {
        val result = mutableListOf<LocalUser>()
        val users = readUsers(c)
        for (i in 0 until users.length()) {
            val obj = users.optJSONObject(i) ?: continue
            val id = obj.optString("userId").trim()
            if (id.isBlank()) continue
            val role = runCatching { UserRole.valueOf(obj.optString("role", UserRole.USER.name)) }.getOrDefault(UserRole.USER)
            result.add(LocalUser(id, role))
        }
        return result
    }

    fun deleteUser(c: Context, userId: String): Boolean {
        val actor = currentRole(c) ?: return false
        if (actor != UserRole.SUPER_ADMIN && actor != UserRole.ADMIN) return false
        val id = userId.trim()
        if (id.isBlank() || id == currentUserId(c)) return false
        val target = findUser(c, id) ?: return false
        val targetRole = runCatching { UserRole.valueOf(target.optString("role", UserRole.USER.name)) }.getOrDefault(UserRole.USER)
        if (targetRole == UserRole.SUPER_ADMIN) return false
        if (actor == UserRole.ADMIN && targetRole != UserRole.USER) return false
        val users = readUsers(c)
        var removed = false
        for (i in users.length() - 1 downTo 0) if (users.optJSONObject(i)?.optString("userId") == id) { users.remove(i); removed = true }
        if (removed) saveUsers(c, users)
        return removed
    }

    fun currentUserId(c: Context): String? = preferences(c).getString(KEY_CURRENT_USER, null)
    fun currentUser(c: Context): LocalUser? = currentUserId(c)?.let { id -> listUsers(c).firstOrNull { it.userId == id } }
    fun currentRole(c: Context): UserRole? = currentUser(c)?.role

    fun updateCurrentUserRole(c: Context, role: UserRole): Boolean {
        val id = currentUserId(c) ?: return false
        val users = readUsers(c)
        for (i in 0 until users.length()) {
            val obj = users.optJSONObject(i) ?: continue
            if (obj.optString("userId") == id) { obj.put("role", role.name); saveUsers(c, users); return true }
        }
        return false
    }

    fun isSuperAdmin(c: Context) = currentRole(c) == UserRole.SUPER_ADMIN && LicenseManager.getLicenseRole(c) == UserRole.SUPER_ADMIN
    fun canManageLicenses(c: Context) = currentRole(c) == UserRole.SUPER_ADMIN || currentRole(c) == UserRole.ADMIN
    fun isAdmin(c: Context) =
        (currentRole(c) == UserRole.SUPER_ADMIN || currentRole(c) == UserRole.ADMIN) &&
            (LicenseManager.getLicenseRole(c) == UserRole.SUPER_ADMIN || LicenseManager.getLicenseRole(c) == UserRole.ADMIN)

    fun logout(c: Context) { preferences(c).edit().remove(KEY_CURRENT_USER).apply() }

    private fun readUsers(c: Context): JSONArray {
        val prefs = preferences(c)
        val stored = prefs.getString(KEY_USERS, null)
        if (!stored.isNullOrBlank()) return try { JSONArray(stored) } catch (_: Exception) { JSONArray() }
        val oldId = prefs.getString(KEY_USER_ID, null)
        val oldHash = prefs.getString(KEY_PASSWORD_HASH, null)
        val oldSalt = prefs.getString(KEY_PASSWORD_SALT, null)
        if (!oldId.isNullOrBlank() && !oldHash.isNullOrBlank() && !oldSalt.isNullOrBlank()) {
            val migrated = JSONArray().put(JSONObject().put("userId", oldId).put("role", UserRole.USER.name).put("hash", oldHash).put("salt", oldSalt))
            prefs.edit().putString(KEY_USERS, migrated.toString()).putString(KEY_CURRENT_USER, oldId).apply()
            return migrated
        }
        return JSONArray()
    }

    private fun findUser(c: Context, userId: String): JSONObject? {
        val users = readUsers(c)
        for (i in 0 until users.length()) if (users.optJSONObject(i)?.optString("userId") == userId) return users.optJSONObject(i)
        return null
    }

    private fun saveUsers(c: Context, users: JSONArray) { preferences(c).edit().putString(KEY_USERS, users.toString()).apply() }

    private fun hashPassword(password: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_LENGTH)
        return try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded } finally { spec.clearPassword() }
    }

    private fun encode(value: ByteArray) = Base64.encodeToString(value, Base64.NO_WRAP)
}
