package com.example.kbawelfaremessenger

import android.content.Context
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Offline license manager.
 *
 * License data contains ONLY:
 *   phone
 *   expiry date
 *
 * The administrator signs the license using the RSA PRIVATE KEY.
 * The Android application contains ONLY the RSA PUBLIC KEY.
 *
 * Advocate-facing license ID example:
 *   ANI7-X4P9-M2Q8-T6R3
 *
 * IMPORTANT:
 * The private key must NEVER be placed inside the Android application.
 */
object LicenseManager {

    private const val PREF_NAME = "kba_license"

    private const val KEY_LICENSE_ID = "license_id"
    private const val KEY_LICENSE_TOKEN = "license_token"

    /*
     * ---------------------------------------------------------
     * IMPORTANT
     * ---------------------------------------------------------
     *
     * Replace the value below with the PUBLIC KEY generated
     * by your Admin license tool.
     *
     * The PRIVATE KEY must NEVER be placed here.
     */
    private const val PUBLIC_KEY_PEM = """
-----BEGIN CERTIFICATE-----
MIIFSTCCAzGgAwIBAgIIGt1ndg0Nz0AwDQYJKoZIhvcNAQEMBQAwUjELMAkGA1UE
BhMCSU4xEzARBgNVBAoTCkFkdm9jYXRlNFUxLjAsBgNVBAMTJUtCQVdlbGZhcmVN
ZXNzZW5nZXIgTGljZW5zZSBBdXRob3JpdHkwIBcNMjYwOTAzMTczNjIyWhgPMjA1
NDAxMTkxNzM2MjJaMFIxCzAJBgNVBAYTAklOMRMwEQYDVQQKEwpBZHZvY2F0ZTRV
MS4wLAYDVQQDEyVLQkFXZWxmYXJlTWVzc2VuZ2VyIExpY2Vuc2UgQXV0aG9yaXR5
MIICIjANBgkqhkiG9w0BAQEFAAOCAg8AMIICCgKCAgEAsaZIXqE0CgS6aFIceKCt
dtkw7gtga3Z/BN80DN3pWbTAxcf5DEu6umjm9tbTy2/nafe5MuSPntQnPfpWlqfG
+B4xFTnYbVheNpbZea4XIXIITlZ2Efom6HQBWoht6Q01gKPx367DErKV5whplm+H
XbY2FxQ5SdUdmHKVJ7HRwIIA+TqvG/KRLEBPspxDwsb3NL5CWdGmdrhUW4lQGxA/
6MwUzmZu2C4SJcFDLnQty51AB2mZNIALkUPidOsC+yrnTHhwEPuXGgwpFSsgNtTo
aQMDq3NhEhcS3uOrZ3TYkzdiZQOo5YTRRLO53oQnwCdYoYTIUAu5SQrFUwkpewVL
Wk6GqvtGr7oXcRu8sJGlSM/5pG4MUakjWRRKyEosYmJn9rXGpjbeyBGlfkjpCI2b
JeuNrJMTGr8O7DqbYwBW5ByMySaJ/ns3x9HifOd+WmmMscqG8sxXCuZzmorY6uQP
KaFQ7daQv02+iFa0535wyoG1GEQexmU7Eb1pOTfcEErFUZ7WBxGnQdtIyZ6IWVmE
srnrOie4NPG32XR9jA6P/3ImSUfRUnVbZir9Hkae8P2hSfq0F3AjnRjd6yQpEYXq
tNDRMfy47Fo/yNRS5K+zx5OVUNYbTfKYR5u6HSWy0e1xYCRl/qwek07UJMKqCI37
PeCrpcJUlfIZwMotZjf6BEsCAwEAAaMhMB8wHQYDVR0OBBYEFC/Q+IzKRxxfpWB+
7moncBd6rMTmMA0GCSqGSIb3DQEBDAUAA4ICAQAIbE2ErQqlxVSAd2gpek9EYso8
my7JIzmIk747jmRwiRr2wtFrIbu1ZTN6gL0XPklfCeSuLsvCv5rl29zMZm1Hn2X9
lufIjMk0WNsvtMosPNbM3zh/2lLy+FN2N5Y/yj/u+eBorFsY0J8iC6d4fJztNWh3
cHSQRWHKtp9nXUiD+8FcD7B5emt1Ewp5Tv6PspIl4jQM3vFLYrm2OElQ261C8OcE
+w9fpZE3fCC1RAHLqP2VtaBRzKX06NfvMXvEHso660JqRtu5wi8WMuYR8XAmfNUh
eKubDgvLPkpD+7Cc8mvpAlR1LDF9NktZsThP9C/jbn2c0Udvo0OXnopK95h3M6wI
V6UcM+zJQ7f7Yd5Ul0Ee4I0HIQn1naqCvvcnQRhrC4Q8vPhRuVc6Wo5ZBKHlYhjZ
C0hr6ULOovyR95o/NVHdBjALfm8+024n8+IouOtWeokuss/rTzcntik0sT7Hb5Y9
j4zFS5wsMu9HQ8cmEznD6CJ8KwJF+CMCx9Qn04ZPEV5QFv9ax+IGGQEEJBAQKw2w
5CYbI17zNCvV6wn21WsHTfdIEciKEFAvalQCHj9oH+HZmCVCRqBny4K/eC90ld1I
S9aingWN6kAbWpCjYb3WT2hVmUMhr/Njs8P4Q6DPcBnVwgAbwQfgIC3FXipQbcQB
yn90df4QmpIDXBKWYA==
-----END CERTIFICATE-----

"""

    private const val LICENSE_PREFIX = "ANI"

    private const val DATE_PATTERN = "yyyy-MM-dd"

    private val dateFormatter =
        DateTimeFormatter.ofPattern(DATE_PATTERN)

    // =========================================================
    // LICENSE DATA
    // =========================================================

    data class License(
        val licenseId: String,
        val phone: String,
        val expiryDate: LocalDate
    )

    data class LicenseCheckResult(
        val allowed: Boolean,
        val message: String
    )

    // =========================================================
    // INSTALL LICENSE
    // =========================================================

    /**
     * Installs a license after:
     *
     * 1. Checking license format
     * 2. Verifying RSA signature
     * 3. Checking expiry date
     *
     * The license is stored only after successful verification.
     */
    fun installLicense(
        context: Context,
        licenseId: String,
        signedToken: String
    ): LicenseCheckResult {

        return try {

            val cleanId =
                normaliseLicenseId(
                    licenseId
                )

            if (!isValidLicenseIdFormat(cleanId)) {

                return LicenseCheckResult(
                    false,
                    "Invalid license key format."
                )
            }

            val license =
                verifySignedToken(
                    cleanId,
                    signedToken
                )
                    ?: return LicenseCheckResult(
                        false,
                        "License verification failed."
                    )

            val today =
                LocalDate.now()

            if (today.isAfter(license.expiryDate)) {

                return LicenseCheckResult(
                    false,
                    "This license has expired."
                )
            }

            context
                .getSharedPreferences(
                    PREF_NAME,
                    Context.MODE_PRIVATE
                )
                .edit()
                .putString(
                    KEY_LICENSE_ID,
                    license.licenseId
                )
                .putString(
                    KEY_LICENSE_TOKEN,
                    signedToken
                )
                .apply()

            AppLogger.success(
                context,
                "LICENSE",
                "License installed successfully. ID: ${license.licenseId}"
            )

            LicenseCheckResult(
                true,
                "License activated successfully."
            )

        } catch (e: Exception) {

            AppLogger.error(
                context,
                "LICENSE",
                "License installation failed: ${e.message}"
            )

            LicenseCheckResult(
                false,
                "Unable to install license."
            )
        }
    }

    // =========================================================
    // GET INSTALLED LICENSE
    // =========================================================

    /**
     * Returns the installed license only if its RSA signature
     * is valid.
     *
     * Expired licenses are returned here so the UI can display
     * their expiry status.
     */
    fun getInstalledLicense(
        context: Context
    ): License? {

        return try {

            val preferences =
                context.getSharedPreferences(
                    PREF_NAME,
                    Context.MODE_PRIVATE
                )

            val licenseId =
                preferences.getString(
                    KEY_LICENSE_ID,
                    null
                )
                    ?: return null

            val token =
                preferences.getString(
                    KEY_LICENSE_TOKEN,
                    null
                )
                    ?: return null

            verifySignedToken(
                normaliseLicenseId(
                    licenseId
                ),
                token
            )

        } catch (_: Exception) {

            null
        }
    }

    // =========================================================
    // GET VALID LICENSE
    // =========================================================

    /**
     * Returns the license only when:
     *
     * - signature is valid
     * - expiry date has not passed
     */
    fun getValidLicense(
        context: Context
    ): License? {

        val license =
            getInstalledLicense(
                context
            )
                ?: return null

        val today =
            LocalDate.now()

        if (
            today.isAfter(
                license.expiryDate
            )
        ) {

            return null
        }

        return license
    }

    // =========================================================
    // LICENSE PHONE
    // =========================================================

    fun getLicensedPhone(
        context: Context
    ): String? {

        return getValidLicense(
            context
        )?.phone
    }

    // =========================================================
    // LICENSE ID
    // =========================================================

    fun getLicenseId(
        context: Context
    ): String? {

        return getInstalledLicense(
            context
        )?.licenseId
    }

    // =========================================================
    // LICENSE EXPIRY
    // =========================================================

    fun getExpiryDate(
        context: Context
    ): LocalDate? {

        return getInstalledLicense(
            context
        )?.expiryDate
    }

    // =========================================================
    // LICENSE VALIDITY
    // =========================================================

    fun isLicenseValid(
        context: Context
    ): Boolean {

        return getValidLicense(
            context
        ) != null
    }

    // =========================================================
    // STRICT PHONE CHECK
    // =========================================================

    /**
     * This is the security check used before SMS sending.
     *
     * SMS is allowed ONLY when:
     *
     * 1. A valid license exists.
     * 2. License has not expired.
     * 3. Actual SMS SIM number matches license phone.
     *
     * If the actual SIM number cannot be obtained,
     * SMS must be blocked.
     */
    fun checkLicenseAndSmsPhone(
        context: Context,
        smsPhone: String?
    ): LicenseCheckResult {

        val license =
            getValidLicense(
                context
            )
                ?: run {

                    val installed =
                        getInstalledLicense(
                            context
                        )

                    if (installed != null) {

                        return LicenseCheckResult(
                            false,
                            "License has expired."
                        )
                    }

                    return LicenseCheckResult(
                        false,
                        "No valid KBA license is installed."
                    )
                }

        if (smsPhone.isNullOrBlank()) {

            return LicenseCheckResult(
                false,
                "Unable to verify the phone number of the SMS SIM.\n\n" +
                        "SMS sending is blocked because the licensed phone number " +
                        "cannot be verified."
            )
        }

        val normalisedSmsPhone =
            normalisePhone(
                smsPhone
            )

        if (normalisedSmsPhone.isBlank()) {

            return LicenseCheckResult(
                false,
                "The SMS SIM phone number could not be read correctly.\n\n" +
                        "SMS sending is blocked."
            )
        }

        if (
            normalisedSmsPhone !=
            license.phone
        ) {

            return LicenseCheckResult(
                false,
                "Licensed phone number does not match the SMS SIM.\n\n" +
                        "Licensed number: ${displayPhone(license.phone)}\n" +
                        "SMS SIM number: ${displayPhone(normalisedSmsPhone)}\n\n" +
                        "SMS sending is blocked."
            )
        }

        return LicenseCheckResult(
            true,
            "License and SMS SIM verified."
        )
    }

    // =========================================================
    // CLEAR LICENSE
    // =========================================================

    fun clearLicense(
        context: Context
    ) {

        context
            .getSharedPreferences(
                PREF_NAME,
                Context.MODE_PRIVATE
            )
            .edit()
            .clear()
            .apply()

        AppLogger.info(
            context,
            "LICENSE",
            "Installed license cleared."
        )
    }

    // =========================================================
    // SIGNED TOKEN VERIFICATION
    // =========================================================

    /**
     * Signed token format:
     *
     * Base64(payload).Base64(signature)
     *
     * Signed payload:
     *
     * phone=919518804747
     * expiry=2027-09-02
     *
     * The exact payload is signed.
     */
    private fun verifySignedToken(
        licenseId: String,
        signedToken: String
    ): License? {

        try {

            val tokenParts =
                signedToken.split(
                    ".",
                    limit = 2
                )

            if (tokenParts.size != 2) {
                return null
            }

            val payloadBytes =
                Base64.decode(
                    tokenParts[0],
                    Base64.DEFAULT
                )

            val signatureBytes =
                Base64.decode(
                    tokenParts[1],
                    Base64.DEFAULT
                )

            val payload =
                String(
                    payloadBytes,
                    StandardCharsets.UTF_8
                )

            val publicKey =
                loadPublicKey()

            val signature =
                Signature.getInstance(
                    "SHA256withRSA"
                )

            signature.initVerify(
                publicKey
            )

            signature.update(
                payloadBytes
            )

            val validSignature =
                signature.verify(
                    signatureBytes
                )

            if (!validSignature) {
                return null
            }

            val values =
                parsePayload(
                    payload
                )
                    ?: return null

            val phone =
                values.first

            val expiryDate =
                values.second

            /*
             * Rebuild the exact canonical payload.
             *
             * This prevents alternate formatting from being
             * accepted.
             */
            val canonicalPayload =
                createCanonicalPayload(
                    phone,
                    expiryDate
                )

            if (
                payload !=
                canonicalPayload
            ) {

                return null
            }

            return License(
                licenseId = licenseId,
                phone = phone,
                expiryDate = expiryDate
            )

        } catch (_: Exception) {

            return null
        }
    }

    // =========================================================
    // PAYLOAD PARSING
    // =========================================================

    private fun parsePayload(
        payload: String
    ): Pair<String, LocalDate>? {

        val lines =
            payload.split(
                "\n"
            )

        if (lines.size != 2) {
            return null
        }

        if (
            !lines[0]
                .startsWith(
                    "phone="
                )
        ) {
            return null
        }

        if (
            !lines[1]
                .startsWith(
                    "expiry="
                )
        ) {
            return null
        }

        val rawPhone =
            lines[0]
                .removePrefix(
                    "phone="
                )

        val rawExpiry =
            lines[1]
                .removePrefix(
                    "expiry="
                )

        val phone =
            normalisePhone(
                rawPhone
            )

        if (phone.isBlank()) {
            return null
        }

        val expiry =
            try {

                LocalDate.parse(
                    rawExpiry,
                    dateFormatter
                )

            } catch (_: Exception) {

                return null
            }

        return Pair(
            phone,
            expiry
        )
    }

    // =========================================================
    // CANONICAL PAYLOAD
    // =========================================================

    private fun createCanonicalPayload(
        phone: String,
        expiryDate: LocalDate
    ): String {

        return "phone=$phone\n" +
                "expiry=${expiryDate.format(dateFormatter)}"
    }

    // =========================================================
    // PUBLIC KEY
    // =========================================================

    private fun loadPublicKey() =
        run {

            val cleaned =
                PUBLIC_KEY_PEM
                    .replace(
                        "-----BEGIN PUBLIC KEY-----",
                        ""
                    )
                    .replace(
                        "-----END PUBLIC KEY-----",
                        ""
                    )
                    .replace(
                        Regex("\\s"),
                        ""
                    )

            if (
                cleaned.isBlank() ||
                cleaned.contains(
                    "REPLACE_WITH_YOUR_ADMIN_PUBLIC_KEY"
                )
            ) {

                throw IllegalStateException(
                    "Android public key has not been configured."
                )
            }

            val keyBytes =
                Base64.decode(
                    cleaned,
                    Base64.DEFAULT
                )

            val keySpec =
                X509EncodedKeySpec(
                    keyBytes
                )

            KeyFactory
                .getInstance("RSA")
                .generatePublic(
                    keySpec
                )
        }

    // =========================================================
    // LICENSE ID
    // =========================================================

    private fun normaliseLicenseId(
        value: String
    ): String {

        return value
            .trim()
            .uppercase()
            .replace(
                " ",
                ""
            )
    }

    private fun isValidLicenseIdFormat(
        licenseId: String
    ): Boolean {

        /*
         * Example:
         *
         * ANI7-X4P9-M2Q8-T6R3
         *
         * Prefix = ANI
         * Four groups of four characters.
         */
        val regex =
            Regex(
                "^ANI[A-Z0-9]-[A-Z0-9]{4}-[A-Z0-9]{4}-[A-Z0-9]{4}$"
            )

        return regex.matches(
            licenseId
        )
    }

    // =========================================================
    // PHONE NORMALIZATION
    // =========================================================

    /**
     * Converts Indian phone numbers to:
     *
     * 919518804747
     *
     * The license and actual SIM number are therefore compared
     * using the same representation.
     */
    private fun normalisePhone(
        value: String
    ): String {

        var number =
            value
                .trim()
                .replace(
                    Regex("[^0-9+]"),
                    ""
                )

        if (number.startsWith("+")) {

            number =
                number.substring(1)
        }

        if (number.startsWith("00")) {

            number =
                number.substring(2)
        }

        if (
            number.length == 10 &&
            number.all { it.isDigit() }
        ) {

            return "91$number"
        }

        if (
            number.length == 12 &&
            number.startsWith("91") &&
            number.all { it.isDigit() }
        ) {

            return number
        }

        return ""
    }

    // =========================================================
    // PHONE DISPLAY
    // =========================================================

    private fun displayPhone(
        phone: String
    ): String {

        return if (
            phone.length == 12 &&
            phone.startsWith("91")
        ) {

            "+${phone.substring(0, 2)} " +
                    phone.substring(2)

        } else {

            phone
        }
    }
}
