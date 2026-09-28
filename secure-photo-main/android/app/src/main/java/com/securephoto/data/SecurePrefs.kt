package com.securephoto.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

class SecurePrefs(context: Context) {
    private val prefs = EncryptedSharedPreferences.create("secure_photo", MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC), context, EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV, EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)
    var roomId: String? get() = prefs.getString("room_id", null) set(value) { prefs.edit().putString("room_id", value).apply() }
    var receiverToken: String? get() = prefs.getString("receiver_token", null) set(value) { prefs.edit().putString("receiver_token", value).apply() }
    var senderSecret: String? get() = prefs.getString("sender_secret", null) set(value) { prefs.edit().putString("sender_secret", value).apply() }
    var biometric: Boolean get() = prefs.getBoolean("biometric", false) set(value) { prefs.edit().putBoolean("biometric", value).apply() }
}
