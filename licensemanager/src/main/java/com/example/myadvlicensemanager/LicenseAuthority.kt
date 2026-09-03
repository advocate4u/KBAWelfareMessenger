package com.example.myadvlicensemanager

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.io.InputStream
import java.security.KeyFactory
import java.security.KeyStore
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.time.LocalDate
import java.time.format.DateTimeFormatter

object LicenseAuthority {
    private const val PREFS="myadv_manager"; private const val ROLE="role"; private const val PHONE="phone"; private const val NEXT_ADMIN="next_admin"; private const val NEXT_ADVOCATE="next_advocate"; private const val KEY_CIPHERTEXT="private_key"; private const val KEY_IV="private_key_iv"; private const val KS="AndroidKeyStore"; private const val WRAP="MyAdv.Manager.WrapKey"
    private val fmt=DateTimeFormatter.ofPattern("yyyy-MM-dd")
    enum class ManagerRole{SUPER_ADMIN,ADMIN}
    data class License(val number:String,val phone:String,val role:ManagerRole,val expiry:LocalDate,val token:String)
    data class AdvocateLicense(val number:String,val phone:String,val expiry:LocalDate,val token:String)
    fun role(c:Context)=c.getSharedPreferences(PREFS,0).getString(ROLE,null)?.let{runCatching{ManagerRole.valueOf(it)}.getOrNull()}
    fun phone(c:Context)=c.getSharedPreferences(PREFS,0).getString(PHONE,null)
    fun hasKey(c:Context)=c.getSharedPreferences(PREFS,0).contains(KEY_CIPHERTEXT)
    fun activateSuperAdmin(c:Context,input:String):Boolean{val p=normalize(input)?:return false;if(p!="919813337779"&&p!="919104371000")return false;saveRole(c,ManagerRole.SUPER_ADMIN,p);return true}
    fun activateAdmin(c:Context,licenseNo:String,licenseText:String):Boolean{if(!licenseNo.uppercase().startsWith("ADM-"))return false;val phone=Regex("Phone:\\s*([0-9+ -]{10,18})").find(licenseText)?.groupValues?.get(1)?.let(::normalize)?:return false;saveRole(c,ManagerRole.ADMIN,phone);return true}
    fun installSigningKey(c:Context,input:InputStream,password:CharArray):Boolean=try{val ks=KeyStore.getInstance("PKCS12");input.use{ks.load(it,password)};val a=ks.aliases();var k:PrivateKey?=null;var cert:java.security.cert.Certificate?=null;while(a.hasMoreElements()){val x=a.nextElement();if(ks.isKeyEntry(x)){val q=ks.getKey(x,password);if(q is PrivateKey){k=q;cert=ks.getCertificate(x);break}}};if(k!=null&&cert!=null)saveKey(c,k!!,cert!!)else false}catch(_:Exception){false}finally{java.util.Arrays.fill(password,'\u0000')}
    fun createAdmin(c:Context,target:String,expiry:LocalDate):License?{if(role(c)!=ManagerRole.SUPER_ADMIN||!hasKey(c)||expiry.isBefore(LocalDate.now()))return null;val p=normalize(target)?:return null;val n=next(c,NEXT_ADMIN,"ADM");val t=sign(c,"version=2\nlicense=$n\nphone=$p\nrole=ADMIN\nexpiry=${expiry.format(fmt)}")?:return null;return License(n,p,ManagerRole.ADMIN,expiry,t)}
    fun createAdvocate(c:Context,target:String,expiry:LocalDate):AdvocateLicense?{if(role(c)==null||!hasKey(c)||expiry.isBefore(LocalDate.now()))return null;val p=normalize(target)?:return null;val n=next(c,NEXT_ADVOCATE,"ADV");val t=sign(c,"version=2\nlicense=$n\nphone=$p\nrole=ADVOCATE\nexpiry=${expiry.format(fmt)}")?:return null;return AdvocateLicense(n,p,expiry,t)}
    private fun next(c:Context,key:String,prefix:String):String{val p=c.getSharedPreferences(PREFS,0);val n=p.getInt(key,1);p.edit().putInt(key,n+1).apply();return "$prefix-${LocalDate.now().year}-${n.toString().padStart(5,'0')}"}
    private fun sign(c:Context,payload:String):String?=try{val s=Signature.getInstance("SHA256withRSA");s.initSign(decryptKey(c));val b=payload.toByteArray(Charsets.UTF_8);s.update(b);Base64.encodeToString(b,Base64.NO_WRAP)+"."+Base64.encodeToString(s.sign(),Base64.NO_WRAP)}catch(_:Exception){null}
    private fun normalize(v:String):String?{var n=v.trim().replace(Regex("[^0-9+]"),"");if(n.startsWith("+"))n=n.substring(1);if(n.startsWith("00"))n=n.substring(2);return when{n.length==10&&n.all(Char::isDigit)->"91$n";n.length==12&&n.startsWith("91")&&n.all(Char::isDigit)->n;else->null}}
    private fun saveRole(c:Context,r:ManagerRole,p:String){c.getSharedPreferences(PREFS,0).edit().putString(ROLE,r.name).putString(PHONE,p).apply()}
    private fun saveKey(c:Context,key:PrivateKey,cert:java.security.cert.Certificate):Boolean{val cipher=javax.crypto.Cipher.getInstance("AES/GCM/NoPadding");cipher.init(javax.crypto.Cipher.ENCRYPT_MODE,wrapKey());val e=cipher.doFinal(key.encoded);c.getSharedPreferences(PREFS,0).edit().putString(KEY_CIPHERTEXT,Base64.encodeToString(e,Base64.NO_WRAP)).putString(KEY_IV,Base64.encodeToString(cipher.iv,Base64.NO_WRAP)).apply();return true}
    private fun wrapKey():javax.crypto.SecretKey{val ks=KeyStore.getInstance(KS).apply{load(null)};(ks.getKey(WRAP,null) as? javax.crypto.SecretKey)?.let{return it};val g=javax.crypto.KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,KS);g.init(KeyGenParameterSpec.Builder(WRAP,KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build());return g.generateKey()}
    private fun decryptKey(c:Context):PrivateKey{val p=c.getSharedPreferences(PREFS,0);val e=Base64.decode(p.getString(KEY_CIPHERTEXT,null)?:error("key"),Base64.DEFAULT);val iv=Base64.decode(p.getString(KEY_IV,null)?:error("iv"),Base64.DEFAULT);val cipher=javax.crypto.Cipher.getInstance("AES/GCM/NoPadding");cipher.init(javax.crypto.Cipher.DECRYPT_MODE,wrapKey(),javax.crypto.spec.GCMParameterSpec(128,iv));return KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(cipher.doFinal(e)))}
}
