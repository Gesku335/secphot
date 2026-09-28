package com.securephoto.crypto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.nio.ByteBuffer
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.interfaces.ECPrivateKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECPublicKeySpec
import java.security.spec.ECPoint
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object PhotoCrypto {
    private const val ALIAS = "receiver_ecdh"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private val b64 = Base64.getUrlEncoder().withoutPadding()
    private val b64Decoder = Base64.getUrlDecoder()

    fun ensureReceiverKey(): ByteArray {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        if (!ks.containsAlias(ALIAS)) {
            val generator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, ANDROID_KEYSTORE)
            generator.initialize(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_AGREE_KEY)
                .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                .setDigests(KeyProperties.DIGEST_SHA256)
                .setUserAuthenticationRequired(false)
                .build())
            generator.generateKeyPair()
        }
        return ks.getCertificate(ALIAS).publicKey.encoded
    }

    fun publicKeyB64Url(): String = b64.encodeToString(ensureReceiverKey())

    fun decrypt(payload: ByteArray, photoId: String): ByteArray {
        require(payload.size >= 65 + 12 + 16) { "payload" }
        require(payload[0].toInt() == 4) { "point" }
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val privateKey = ks.getKey(ALIAS, null) as java.security.PrivateKey
        val receiverParams = (privateKey as ECPrivateKey).params
        val x = java.math.BigInteger(1, payload.copyOfRange(1, 33))
        val y = java.math.BigInteger(1, payload.copyOfRange(33, 65))
        val eph = KeyFactory.getInstance("EC").generatePublic(ECPublicKeySpec(ECPoint(x, y), receiverParams))
        val agreement = KeyAgreement.getInstance("ECDH").apply { init(privateKey); doPhase(eph, true) }
        val aesBytes = hkdf(agreement.generateSecret())
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(aesBytes, "AES"), GCMParameterSpec(128, payload.copyOfRange(65, 77)))
        cipher.updateAAD(photoId.toByteArray(Charsets.UTF_8))
        return cipher.doFinal(payload.copyOfRange(77, payload.size))
    }

    private fun hkdf(shared: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(ByteArray(32), "HmacSHA256"))
        val prk = mac.doFinal(shared)
        mac.init(SecretKeySpec(prk, "HmacSHA256"))
        return mac.doFinal("securephoto-v1".toByteArray(Charsets.UTF_8) + byteArrayOf(1))
    }

    fun encodeLink(base: String, roomId: String, senderSecret: String, publicKey: String): String =
        "$base/s/#r=${enc(roomId)}&k=${enc(senderSecret)}&pk=${enc(publicKey)}"

    private fun enc(value: String) = value.replace("+", "-").replace("/", "_").replace("=", "")
}
