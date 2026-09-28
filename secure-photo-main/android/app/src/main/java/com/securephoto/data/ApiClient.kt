package com.securephoto.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.CacheControl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.UUID

@Serializable data class RoomResponse(val room_id: String, val receiver_token: String, val sender_secret: String)
@Serializable data class RotateResponse(val sender_secret: String)
@Serializable data class PhotoItem(val id: String, val created_at: Long, val size: Long, val max_views: Int, val views_used: Int, val expires_at: Long)

class ApiClient(private val baseUrl: String) {
    private val json = Json { ignoreUnknownKeys = true }
    private val client = OkHttpClient.Builder().cache(null).build()
    private val binary = "application/octet-stream".toMediaType()
    private fun request(path: String, token: String, room: String) = Request.Builder().url("${baseUrl.trimEnd('/')}$path").header("Authorization", "Bearer $token").header("X-Room-Id", room).cacheControl(CacheControl.FORCE_NETWORK)
    fun createRoom(registrationSecret: String, pubkey: String): RoomResponse {
        val body = "{\"pubkey\":\"$pubkey\"}".toRequestBody("application/json".toMediaType())
        val req = Request.Builder().url("${baseUrl.trimEnd('/')}/api/rooms").header("X-Registration-Secret", registrationSecret).post(body).build()
        client.newCall(req).execute().use { r -> if (!r.isSuccessful) error("room_${r.code}"); return json.decodeFromString(r.body!!.string()) }
    }
    fun list(token: String, room: String): List<PhotoItem> {
        client.newCall(request("/api/photos", token, room).get().build()).execute().use { r -> if (!r.isSuccessful) error("list_${r.code}"); return json.decodeFromString(r.body!!.string()) }
    }
    fun open(token: String, room: String, photoId: String): Pair<ByteArray, String> {
        val req = request("/api/photos/$photoId/open", token, room).header("X-Open-Id", UUID.randomUUID().toString()).post(ByteArray(0).toRequestBody(null)).build()
        client.newCall(req).execute().use { r -> if (!r.isSuccessful) error("open_${r.code}"); return Pair(r.body!!.bytes(), r.header("X-Expires-At", "0")!!) }
    }
    fun delete(token: String, room: String, photoId: String) { client.newCall(request("/api/photos/$photoId", token, room).delete().build()).execute().use { if (!it.isSuccessful && it.code != 404) error("delete_${it.code}") } }
    fun rotate(token: String, room: String): String { client.newCall(request("/api/rooms/$room/rotate-sender", token, room).post(ByteArray(0).toRequestBody(null)).build()).execute().use { r -> if (!r.isSuccessful) error("rotate_${r.code}"); return json.decodeFromString<RotateResponse>(r.body!!.string()).sender_secret } }
}
