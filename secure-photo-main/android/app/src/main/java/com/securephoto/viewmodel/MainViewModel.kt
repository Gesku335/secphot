package com.securephoto.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.securephoto.BuildConfig
import com.securephoto.crypto.PhotoCrypto
import com.securephoto.data.ApiClient
import com.securephoto.data.PhotoItem
import com.securephoto.data.SecurePrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface ScreenState { data object Loading: ScreenState; data class Setup(val error: String?=null): ScreenState; data class List(val photos: kotlin.collections.List<PhotoItem>, val error: String?=null): ScreenState; data class Viewing(val id:String, val bitmap: Bitmap, val bytes: ByteArray, val expiresAt:Long): ScreenState }
class MainViewModel(app: Application): AndroidViewModel(app) {
    private val prefs = SecurePrefs(app); private val api = ApiClient(BuildConfig.API_BASE_URL); private val state = MutableStateFlow<ScreenState>(ScreenState.Loading); val ui = state.asStateFlow()
    val link: String get() = PhotoCrypto.encodeLink(BuildConfig.WEB_BASE_URL, prefs.roomId.orEmpty(), prefs.senderSecret.orEmpty(), PhotoCrypto.publicKeyB64Url())
    val biometricEnabled: Boolean get() = prefs.biometric
    init { viewModelScope.launch { if (prefs.roomId == null) state.value=ScreenState.Setup() else refresh() } }
    fun setup(registrationSecret: String) = viewModelScope.launch(Dispatchers.IO) { runCatching { val room=api.createRoom(registrationSecret, PhotoCrypto.publicKeyB64Url()); prefs.roomId=room.room_id; prefs.receiverToken=room.receiver_token; prefs.senderSecret=room.sender_secret; refresh() }.onFailure { state.value=ScreenState.Setup(it.message) } }
    fun refresh() = viewModelScope.launch(Dispatchers.IO) { runCatching { state.value=ScreenState.List(api.list(prefs.receiverToken!!, prefs.roomId!!)) }.onFailure { state.value=ScreenState.List(emptyList(),it.message) } }
    fun open(item: PhotoItem) = viewModelScope.launch(Dispatchers.IO) { runCatching { val (payload,expires)=api.open(prefs.receiverToken!!,prefs.roomId!!,item.id); val bytes=PhotoCrypto.decrypt(payload,item.id); val bitmap=BitmapFactory.decodeByteArray(bytes,0,bytes.size) ?: error("decode"); state.value=ScreenState.Viewing(item.id,bitmap,bytes,expires.toLong()) }.onFailure { state.value=ScreenState.List(emptyList(),it.message) } }
    fun closeViewing() { val current=state.value; if (current is ScreenState.Viewing) { current.bytes.fill(0); current.bitmap.recycle() }; refresh() }
    fun clearSensitiveView() { val current=state.value; if (current is ScreenState.Viewing) { current.bytes.fill(0); current.bitmap.recycle(); state.value=ScreenState.List(emptyList()) } }
    fun delete(item: PhotoItem) = viewModelScope.launch(Dispatchers.IO) { api.delete(prefs.receiverToken!!,prefs.roomId!!,item.id); refresh() }
    fun rotate() = viewModelScope.launch(Dispatchers.IO) { runCatching { prefs.senderSecret=api.rotate(prefs.receiverToken!!,prefs.roomId!!) }.onFailure { } }
    fun setBiometric(enabled: Boolean) { prefs.biometric = enabled }
    fun deleteAll() = viewModelScope.launch(Dispatchers.IO) { runCatching { api.list(prefs.receiverToken!!,prefs.roomId!!).forEach { api.delete(prefs.receiverToken!!,prefs.roomId!!,it.id) }; refresh() } }
    override fun onCleared() { val current=state.value; if (current is ScreenState.Viewing) { current.bytes.fill(0); current.bitmap.recycle() }; super.onCleared() }
}
