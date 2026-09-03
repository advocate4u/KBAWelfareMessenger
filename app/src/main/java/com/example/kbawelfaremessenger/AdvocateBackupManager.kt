package com.example.kbawelfaremessenger

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.io.OutputStream
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object AdvocateBackupManager {
    private const val FORMAT = "KBAWELFARE_CASE_BACKUP"
    private const val VERSION = 1
    private const val ITERATIONS = 120_000
    private const val KEY_BITS = 256
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12

    fun writeBackup(output: OutputStream, cases: List<AdvocateCase>, password: String) {
        require(password.length >= 6) { "Backup password must be at least 6 characters." }
        val salt = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(IV_BYTES).also { SecureRandom().nextBytes(it) }
        val key = deriveKey(password, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        val encrypted = cipher.doFinal(toJson(cases).toString().toByteArray(Charsets.UTF_8))
        val root = JSONObject()
            .put("format", FORMAT)
            .put("version", VERSION)
            .put("algorithm", "PBKDF2-HMAC-SHA256/AES-256-GCM")
            .put("iterations", ITERATIONS)
            .put("salt", Base64.encodeToString(salt, Base64.NO_WRAP))
            .put("iv", Base64.encodeToString(iv, Base64.NO_WRAP))
            .put("data", Base64.encodeToString(encrypted, Base64.NO_WRAP))
        output.bufferedWriter(Charsets.UTF_8).use { it.write(root.toString()) }
    }

    fun readBackup(input: InputStream, password: String): List<AdvocateCase> {
        require(password.length >= 6) { "Backup password must be at least 6 characters." }
        val root = JSONObject(input.bufferedReader(Charsets.UTF_8).use { it.readText() })
        require(root.optString("format") == FORMAT) { "Invalid KBA backup file." }
        require(root.optInt("version") == VERSION) { "Unsupported backup version." }
        val salt = Base64.decode(root.getString("salt"), Base64.DEFAULT)
        val iv = Base64.decode(root.getString("iv"), Base64.DEFAULT)
        val encrypted = Base64.decode(root.getString("data"), Base64.DEFAULT)
        val key = deriveKey(password, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
        val json = String(cipher.doFinal(encrypted), Charsets.UTF_8)
        return fromJson(JSONObject(json).getJSONArray("cases"))
    }

    private fun deriveKey(password: String, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_BITS)
        val bytes = try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally { spec.clearPassword() }
        return SecretKeySpec(bytes, "AES")
    }

    private fun toJson(cases: List<AdvocateCase>): JSONObject {
        val array = JSONArray()
        cases.forEach { item ->
            array.put(JSONObject()
                .put("caseNumber", item.caseNumber)
                .put("clientName", item.clientName)
                .put("clientPhone", item.clientPhone)
                .put("courtName", item.courtName)
                .put("previousDate", item.previousDate)
                .put("currentDate", item.currentDate)
                .put("nextDate", item.nextDate)
                .put("currentUpdate", item.currentUpdate)
                .put("newUpdate", item.newUpdate)
                .put("totalFee", item.totalFee)
                .put("amountReceived", item.amountReceived)
                .put("createdAt", item.createdAt)
                .put("updatedAt", item.updatedAt))
        }
        return JSONObject().put("cases", array)
    }

    private fun fromJson(array: JSONArray): List<AdvocateCase> {
        val result = ArrayList<AdvocateCase>(array.length())
        for (i in 0 until array.length()) {
            val o = array.getJSONObject(i)
            val total = o.optDouble("totalFee", 0.0).coerceAtLeast(0.0)
            val received = o.optDouble("amountReceived", 0.0).coerceIn(0.0, total)
            result += AdvocateCase(
                caseNumber = o.optString("caseNumber").trim(),
                clientName = o.optString("clientName").trim(),
                clientPhone = o.optString("clientPhone").trim(),
                courtName = o.optString("courtName").trim(),
                previousDate = o.optString("previousDate").trim(),
                currentDate = o.optString("currentDate").trim(),
                nextDate = o.optString("nextDate").trim(),
                currentUpdate = o.optString("currentUpdate").trim(),
                newUpdate = o.optString("newUpdate").trim(),
                totalFee = total,
                amountReceived = received,
                createdAt = o.optLong("createdAt", System.currentTimeMillis()),
                updatedAt = o.optLong("updatedAt", System.currentTimeMillis())
            )
        }
        return result.filter { it.caseNumber.isNotBlank() && it.clientName.isNotBlank() }
    }
}
