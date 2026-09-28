package com.securephoto

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.securephoto.crypto.PhotoCrypto
import org.junit.Assert.assertEquals
import java.security.KeyStore
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class KeystoreInteropTest {
    @Test fun receiverPublicKeyIsP256Spki() {
        val encoded = PhotoCrypto.ensureReceiverKey()
        assertEquals(91, encoded.size)
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        assertEquals(91, ks.getCertificate("receiver_ecdh").publicKey.encoded.size)
    }
}
