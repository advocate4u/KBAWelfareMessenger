package com.example.kbawelfaremessenger

import android.content.Context
import android.util.Base64
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import java.security.Signature
import java.security.cert.CertificateFactory
import java.time.LocalDate
import java.time.format.DateTimeFormatter

object LicenseManager {
    private const val PREF_NAME="kba_license"; private const val KEY_LICENSE_ID="license_id"; private const val KEY_LICENSE_TOKEN="license_token"; private const val PREFIX="ANI"
    private val fmt=DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val PUBLIC_KEY_PEM="""
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
5CYbI17zNCvV6wn21WsHTfdIEciKEFAvalQCHj9o+HZmCVCRqBny4K/eC90ld1I
S9aingWN6kAbWpCjYb3WT2hVmUMhr/Njs8P4Q6DPcBnVwgAbwQfgIC3FXipQbcQB
yn90df4QmpIDXBKWYA==
-----END CERTIFICATE-----
""".trimIndent()
    data class LicenseOptions(val validatePhone:Boolean=true,val sms:Boolean=true,val bulkSms:Boolean=true,val smsLogs:Boolean=true,val advocateDiary:Boolean=true,val advocateHelper:Boolean=true,val editMessageOnScreen:Boolean=true,val skipAlreadySent:Boolean=true,val confirmBeforeBulkSend:Boolean=true,val loggingEnabled:Boolean=true,val removeDuplicates:Boolean=true,val skipInvalidNumbers:Boolean=true)
    data class License(val licenseId:String,val phone:String,val expiryDate:LocalDate,val issueDate:LocalDate,val role:UserRole,val options:LicenseOptions)
    data class LicenseCheckResult(val allowed:Boolean,val message:String)

    fun installLicense(c:Context,idInput:String,token:String):LicenseCheckResult=try{
        val id=idInput.trim().uppercase().replace(" ","")
        if(!Regex("^$PREFIX[A-Z0-9]-[A-Z0-9]{4}-[A-Z0-9]{4}-[A-Z0-9]{4}$").matches(id)) return LicenseCheckResult(false,"Invalid license ID format.")
        val l=verify(id,token)?:return LicenseCheckResult(false,"License verification failed.")
        val today=LocalDate.now(); if(today.isBefore(l.issueDate)) return LicenseCheckResult(false,"This license is not active yet."); if(today.isAfter(l.expiryDate)) return LicenseCheckResult(false,"This license has expired.")
        val uid=SecurityManager.currentUserId(c); val r=SecurityManager.currentRole(c)
        if(uid!=null&&r!=null){if(norm(uid)!=l.phone)return LicenseCheckResult(false,"License phone does not match the current User ID.");if(r!=l.role)return LicenseCheckResult(false,"License role does not match the current account role.")}
        c.getSharedPreferences(PREF_NAME,0).edit().putString(KEY_LICENSE_ID,l.licenseId).putString(KEY_LICENSE_TOKEN,token.trim()).apply(); AppLogger.success(c,"LICENSE","License activated: ${l.licenseId}"); LicenseCheckResult(true,"License activated successfully.")
    }catch(e:Exception){AppLogger.error(c,"LICENSE","License installation failed: ${e.message}");LicenseCheckResult(false,"Unable to install license.")}

    fun getInstalledLicense(c:Context):License?=try{val p=c.getSharedPreferences(PREF_NAME,0);val id=p.getString(KEY_LICENSE_ID,null)?:return null;val t=p.getString(KEY_LICENSE_TOKEN,null)?:return null;verify(id,t)}catch(_:Exception){null}
    fun getValidLicense(c:Context):License?=getInstalledLicense(c)?.takeIf{val d=LocalDate.now();!d.isBefore(it.issueDate)&&!d.isAfter(it.expiryDate)}
    fun getLicensedPhone(c:Context)=getValidLicense(c)?.phone
    fun getLicenseId(c:Context)=getInstalledLicense(c)?.licenseId
    fun getExpiryDate(c:Context)=getInstalledLicense(c)?.expiryDate
    fun getLicenseRole(c:Context)=getValidLicense(c)?.role
    fun isLicenseValid(c:Context)=getValidLicense(c)!=null
    fun isFeatureEnabled(c:Context,f:String):Boolean{val o=getValidLicense(c)?.options?:return false;return when(f.lowercase()){"sms"->o.sms;"bulk_sms"->o.bulkSms;"sms_logs"->o.smsLogs;"diary","advocate_diary"->o.advocateDiary;"helper","advocate_helper"->o.advocateHelper;"edit_message"->o.editMessageOnScreen;"skip_already_sent"->o.skipAlreadySent;"confirm_bulk"->o.confirmBeforeBulkSend;"logging"->o.loggingEnabled;"remove_duplicates"->o.removeDuplicates;"skip_invalid_numbers"->o.skipInvalidNumbers;else->false}}
    fun checkLicenseAndSmsPhone(c:Context,smsPhone:String?):LicenseCheckResult{val l=getValidLicense(c)?:return LicenseCheckResult(false,if(getInstalledLicense(c)!=null)"License has expired or is not active." else "No valid MyAdv license is installed.");if(!l.options.sms)return LicenseCheckResult(false,"SMS sending is not enabled in this license.");if(!l.options.validatePhone)return LicenseCheckResult(true,"License verified. SMS number validation is disabled by the license.");val actual=smsPhone?.let(::norm).orEmpty();if(actual.isBlank())return LicenseCheckResult(false,"Unable to verify the SMS SIM number. SMS sending is blocked.");if(actual!=l.phone)return LicenseCheckResult(false,"Licensed phone number does not match the SMS SIM. SMS sending is blocked.");return LicenseCheckResult(true,"License and SMS SIM verified.")}
    fun clearLicense(c:Context){c.getSharedPreferences(PREF_NAME,0).edit().clear().apply();AppLogger.info(c,"LICENSE","Installed license cleared.")}

    private fun verify(id:String,token:String):License?{val p=token.trim().split(".",limit=2);if(p.size!=2)return null;val b=Base64.decode(p[0],Base64.DEFAULT);val s=Signature.getInstance("SHA256withRSA");s.initVerify(publicKey());s.update(b);if(!s.verify(Base64.decode(p[1],Base64.DEFAULT)))return null;val fields=String(b,StandardCharsets.UTF_8).lineSequence().mapNotNull{val i=it.indexOf('=');if(i<=0)null else it.substring(0,i) to it.substring(i+1)}.toMap();if(fields["license"]!=id)return null;val phone=norm(fields["phone"].orEmpty());val expiry=LocalDate.parse(fields["expiry"].orEmpty(),fmt);val issue=LocalDate.parse(fields["issue"]?:fields["expiry"].orEmpty(),fmt);val role=runCatching{UserRole.valueOf(fields["role"].orEmpty())}.getOrNull()?:return null;if(phone.isBlank())return null;fun b(k:String,d:Boolean)=fields[k]?.toBooleanStrictOrNull()?:d;return License(id,phone,expiry,issue,role,LicenseOptions(b("validatePhone",true),b("sms",true),b("bulkSms",true),b("smsLogs",true),b("advocateDiary",true),b("advocateHelper",true),b("editMessageOnScreen",true),b("skipAlreadySent",true),b("confirmBeforeBulkSend",true),b("loggingEnabled",true),b("removeDuplicates",true),b("skipInvalidNumbers",true)))}
    private fun publicKey()=run{val x=PUBLIC_KEY_PEM.replace("-----BEGIN CERTIFICATE-----","").replace("-----END CERTIFICATE-----","").replace(Regex("\\s"),"");CertificateFactory.getInstance("X.509").generateCertificate(ByteArrayInputStream(Base64.decode(x,Base64.DEFAULT))).publicKey}
    private fun norm(v:String):String{var n=v.trim().replace(Regex("[^0-9+]"),"");if(n.startsWith("+"))n=n.substring(1);if(n.startsWith("00"))n=n.substring(2);return when{n.length==10&&n.all(Char::isDigit)->"91$n";n.length==12&&n.startsWith("91")&&n.all(Char::isDigit)->n;else->""}}
}
