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

    private val ADMIN_PHONE_NUMBERS = setOf("9813337779", "9104371000")

    private fun preferences(context: Context) = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    fun hasUser(context: Context): Boolean = preferences(context).getBoolean(KEY_SETUP_COMPLETE, false)
    private fun normalizePhone(value: String): String = value.filter { it.isDigit() }.takeLast(10)
    fun isAdminPhoneNumber(phoneNumber: String): Boolean = normalizePhone(phoneNumber) in ADMIN_PHONE_NUMBERS
    fun isSuperAdminPhoneNumber(phoneNumber: String): Boolean = isAdminPhoneNumber(phoneNumber)

    /** First account setup is always license-first. The signed license decides the role. */
    fun createUser(context: Context, userId: String, password: String): Boolean {
        val id = userId.trim()
        if (id.isBlank() || password.length < 6 || hasUser(context)) return false

        val license = LicenseManager.getValidLicense(context) ?: return false
        val normalizedId = normalizePhone(id)
        if (normalizedId.isBlank() || normalizePhone(license.phone) != normalizedId) return false

        val role = license.role
        if ((role == UserRole.SUPER_ADMIN || role == UserRole.ADMIN) && !isAdminPhoneNumber(id)) return false
        if (role == UserRole.USER && isAdminPhoneNumber(id)) return false

        val salt = ByteArray(SALT_LENGTH).also { SecureRandom().nextBytes(it) }
        val hash = hashPassword(password, salt)
        val record = JSONObject().put("userId", id).put("role", role.name).put("hash", encode(hash)).put("salt", encode(salt))
        preferences(context).edit()
            .putString(KEY_USERS, JSONArray().put(record).toString())
            .putString(KEY_USER_ID, id)
            .putString(KEY_PASSWORD_HASH, encode(hash))
            .putString(KEY_PASSWORD_SALT, encode(salt))
            .putString(KEY_CURRENT_USER, id)
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

    /**
     * Super Admin can create ADMIN or USER accounts. A normal ADMIN can create USER accounts only.
     * SUPER_ADMIN is intentionally not exposed as a creatable role.
     */
    fun addUser(context: Context, userId: String, password: String, role: UserRole = UserRole.USER): Boolean {
        if (!isAdmin(context)) return false
        if (role == UserRole.SUPER_ADMIN) return false

        val id = userId.trim()
        if (id.isBlank() || password.length < 6 || findUser(context, id) != null) return false
        if (isAdminPhoneNumber(id)) return false
        if (role == UserRole.ADMIN && !isSuperAdmin(context)) return false

        val salt = ByteArray(SALT_LENGTH).also { SecureRandom().nextBytes(it) }
        val hash = hashPassword(password, salt)
        val users = readUsers(context)
        users.put(JSONObject().put("userId", id).put("role", role.name).put("hash", encode(hash)).put("salt", encode(salt)))
        saveUsers(context, users)
        return true
    }

    fun listUsers(context: Context): List<LocalUser> = buildList {
        val users = readUsers(context)
        for (i in 0 until users.length()) {
            val o = users.optJSONObject(i) ?: continue
            val id = o.optString("userId").trim()
            if (id.isBlank()) continue
            val storedRole = runCatching { UserRole.valueOf(o.optString("role", UserRole.USER.name)) }.getOrDefault(UserRole.USER)
            add(LocalUser(id, storedRole))
        }
    }

    fun deleteUser(context: Context, userId: String): Boolean {
        if (!isAdmin(context)) return false
        val id = userId.trim()
        if (id.isBlank() || id == currentUserId(context)) return false
        val users = readUsers(context)
        var removed = false
        for (i in users.length() - 1 downTo 0) if (users.optJSONObject(i)?.optString("userId") == id) { users.remove(i); removed = true }
        if (removed) saveUsers(context, users)
        return removed
    }

    fun currentUserId(context: Context): String? = preferences(context).getString(KEY_CURRENT_USER, null)
    fun currentUser(context: Context): LocalUser? = currentUserId(context)?.let { id -> listUsers(context).firstOrNull { it.userId == id } }
    fun currentRole(context: Context): UserRole? = currentUser(context)?.role

    fun isSuperAdmin(context: Context): Boolean =
        currentUser(context)?.role == UserRole.SUPER_ADMIN &&
            currentUserId(context)?.let { isSuperAdminPhoneNumber(it) } == true &&
            LicenseManager.getLicenseRole(context) == UserRole.SUPER_ADMIN

    fun isAdmin(context: Context): Boolean =
        currentUser(context)?.role?.let { it == UserRole.SUPER_ADMIN || it == UserRole.ADMIN } == true &&
            LicenseManager.getLicenseRole(context)?.let { it == UserRole.SUPER_ADMIN || it == UserRole.ADMIN } == true

    fun logout(context: Context) { preferences(context).edit().remove(KEY_CURRENT_USER).apply() }

    private fun readUsers(context: Context): JSONArray {
        val prefs = preferences(context)
        val stored = prefs.getString(KEY_USERS, null)
        if (!stored.isNullOrBlank()) return try { JSONArray(stored) } catch (_: Exception) { JSONArray() }
        val oldId = prefs.getString(KEY_USER_ID, null)
        val oldHash = prefs.getString(KEY_PASSWORD_HASH, null)
        val oldSalt = prefs.getString(KEY_PASSWORD_SALT, null)
        if (!oldId.isNullOrBlank() && !oldHash.isNullOrBlank() && !oldSalt.isNullOrBlank()) {
            val licenseRole = LicenseManager.getLicenseRole(context)
            val role = when {
                licenseRole == UserRole.SUPER_ADMIN && isSuperAdminPhoneNumber(oldId) -> UserRole.SUPER_ADMIN
                licenseRole == UserRole.ADMIN && isAdminPhoneNumber(oldId) -> UserRole.ADMIN
                else -> UserRole.USER
            }
            val migrated = JSONArray().put(JSONObject().put("userId", oldId).put("role", role.name).put("hash", oldHash).put("salt", oldSalt))
            prefs.edit().putString(KEY_USERS, migrated.toString()).putString(KEY_CURRENT_USER, oldId).apply()
            return migrated
        }
        return JSONArray()
    }

    private fun findUser(context: Context, userId: String): JSONObject? {
        val users = readUsers(context)
        for (i in 0 until users.length()) if (users.optJSONObject(i)?.optString("userId") == userId) return users.optJSONObject(i)
        return null
    }
    private fun saveUsers(context: Context, users: JSONArray) { preferences(context).edit().putString(KEY_USERS, users.toString()).apply() }
    private fun hashPassword(password: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_LENGTH)
        return try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded } finally { spec.clearPassword() }
    }
    private fun encode(value: ByteArray): String = Base64.encodeToString(value, Base64.NO_WRAP)
}
