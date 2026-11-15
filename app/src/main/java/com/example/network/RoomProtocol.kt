package com.example.network

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.model.RoomMember
import com.example.model.RoomPhase
import com.example.model.RoomSyncCommand
import com.example.model.Track
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.security.MessageDigest
import kotlin.random.Random

data class TrackAudioPayload(
    val fileHash: String,
    val fileSize: Long,
    val fileExt: String,
    val mimeType: String,
    val openStream: () -> InputStream?
)

object RoomProtocol {
    private const val TAG = "RoomProtocol"
    const val MAX_TRANSFER_BYTES = 50L * 1024L * 1024L
    const val DEFAULT_CONTROL_PORT = 45892
    const val DEFAULT_UDP_PORT = 45891
    const val NSD_SERVICE_TYPE = "_asdmusic._tcp."

    fun generateFiveDigitCode(): String = Random.nextInt(10_000, 100_000).toString()

    fun isValidRoomCode(code: String): Boolean = code.length == 5 && code.all { it.isDigit() }

    fun encodeJoin(code: String, memberName: String, memberId: String): String {
        return JSONObject().apply {
            put("type", "JOIN")
            put("code", code)
            put("name", memberName)
            put("id", memberId)
        }.toString()
    }

    fun encodeWelcome(code: String, phase: RoomPhase, members: List<RoomMember>): String {
        return JSONObject().apply {
            put("type", "WELCOME")
            put("code", code)
            put("phase", phase.name)
            put("members", encodeMembersArray(members))
        }.toString()
    }

    fun encodeReject(reason: String): String {
        return JSONObject().apply {
            put("type", "REJECT")
            put("reason", reason)
        }.toString()
    }

    fun encodeMembers(members: List<RoomMember>): String {
        return JSONObject().apply {
            put("type", "MEMBERS")
            put("members", encodeMembersArray(members))
        }.toString()
    }

    fun encodeStartRoom(): String {
        return JSONObject().apply {
            put("type", "START_ROOM")
        }.toString()
    }

    fun encodePrepareTrack(
        transferId: String,
        track: Track,
        fileHash: String?,
        fileSize: Long,
        fileExt: String?,
        hasBinary: Boolean,
        audioDownloadUrl: String? = null
    ): String {
        return JSONObject().apply {
            put("type", "PREPARE_TRACK")
            put("transferId", transferId)
            put("trackId", track.id)
            put("title", track.title)
            put("artist", track.artist)
            put("album", track.album)
            put("durationSeconds", track.durationSeconds)
            put("sourceUrl", track.sourceUrl ?: "")
            put("fileHash", fileHash ?: "")
            put("fileSize", fileSize)
            put("fileExt", fileExt ?: "mp3")
            put("hasBinary", hasBinary)
            put("audioDownloadUrl", audioDownloadUrl ?: "")
        }.toString()
    }

    fun encodeTrackReady(transferId: String, fileHash: String): String {
        return JSONObject().apply {
            put("type", "TRACK_READY")
            put("transferId", transferId)
            put("fileHash", fileHash)
        }.toString()
    }

    fun encodeTrackFailed(transferId: String, reason: String): String {
        return JSONObject().apply {
            put("type", "TRACK_FAILED")
            put("transferId", transferId)
            put("reason", reason)
        }.toString()
    }

    fun encodeSyncPlay(
        track: Track,
        positionMs: Long,
        speed: Float,
        isPlaying: Boolean,
        syncDelayMs: Long = 180L,
        fileHash: String? = null,
        fileSize: Long = 0L,
        fileExt: String? = null
    ): String {
        return JSONObject().apply {
            put("type", "SYNC_PLAY")
            put("trackId", track.id)
            put("title", track.title)
            put("artist", track.artist)
            put("album", track.album)
            put("durationSeconds", track.durationSeconds)
            put("sourceUrl", track.sourceUrl ?: "")
            put("positionMs", positionMs.coerceAtLeast(0L))
            put("speed", speed.toDouble())
            put("isPlaying", isPlaying)
            put("syncDelayMs", syncDelayMs)
            put("fileHash", fileHash ?: "")
            put("fileSize", fileSize)
            put("fileExt", fileExt ?: "mp3")
        }.toString()
    }

    fun encodeSyncPause(positionMs: Long): String {
        return JSONObject().apply {
            put("type", "SYNC_PAUSE")
            put("positionMs", positionMs.coerceAtLeast(0L))
        }.toString()
    }

    fun encodeSyncSeek(positionMs: Long, isPlaying: Boolean): String {
        return JSONObject().apply {
            put("type", "SYNC_SEEK")
            put("positionMs", positionMs.coerceAtLeast(0L))
            put("isPlaying", isPlaying)
        }.toString()
    }

    fun encodePing(): String {
        return JSONObject().apply {
            put("type", "PING")
        }.toString()
    }

    fun encodeRoomClosed(reason: String): String {
        return JSONObject().apply {
            put("type", "ROOM_CLOSED")
            put("reason", reason)
        }.toString()
    }

    fun encodeMembersArray(members: List<RoomMember>): JSONArray {
        val arr = JSONArray()
        members.forEach { m ->
            arr.put(
                JSONObject().apply {
                    put("id", m.id)
                    put("name", m.name)
                    put("isHost", m.isHost)
                    if (m.address != null) put("address", m.address)
                }
            )
        }
        return arr
    }

    fun parseMembersArray(arr: JSONArray?): List<RoomMember> {
        if (arr == null) return emptyList()
        val result = mutableListOf<RoomMember>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            result.add(
                RoomMember(
                    id = obj.optString("id", "member_$i"),
                    name = obj.optString("name", "Device ${i + 1}"),
                    isHost = obj.optBoolean("isHost", false),
                    address = obj.optString("address").ifBlank { null }
                )
            )
        }
        return result
    }

    fun parseSyncCommand(json: JSONObject): RoomSyncCommand? {
        return when (val type = json.optString("type")) {
            "SYNC_PLAY" -> RoomSyncCommand(
                action = type,
                trackId = json.optString("trackId").ifBlank { null },
                title = json.optString("title").ifBlank { null },
                artist = json.optString("artist").ifBlank { null },
                album = json.optString("album").ifBlank { null },
                durationSeconds = json.optInt("durationSeconds", 180).coerceAtLeast(10),
                sourceUrl = json.optString("sourceUrl").ifBlank { null },
                positionMs = json.optLong("positionMs", 0L).coerceAtLeast(0L),
                speed = json.optDouble("speed", 1.0).toFloat().coerceIn(0.5f, 2.0f),
                isPlaying = json.optBoolean("isPlaying", true),
                syncDelayMs = json.optLong("syncDelayMs", 0L).coerceAtLeast(0L),
                fileHash = json.optString("fileHash").ifBlank { null },
                fileSize = json.optLong("fileSize", 0L).coerceAtLeast(0L),
                fileExt = json.optString("fileExt").ifBlank { null }
            )
            "SYNC_PAUSE" -> RoomSyncCommand(
                action = type,
                positionMs = json.optLong("positionMs", 0L).coerceAtLeast(0L),
                isPlaying = false
            )
            "SYNC_SEEK" -> RoomSyncCommand(
                action = type,
                positionMs = json.optLong("positionMs", 0L).coerceAtLeast(0L),
                isPlaying = json.optBoolean("isPlaying", true)
            )
            else -> null
        }
    }

    fun resolveTrackAudioPayload(context: Context, track: Track): TrackAudioPayload? {
        val path = track.localFilePath ?: return null
        return try {
            if (path.startsWith("content://")) {
                val uri = Uri.parse(path)
                val (ext, mime) = detectExtAndMimeFromUri(context, uri, track.isVideo)
                val digestAndSize = computeStreamHashAndSize {
                    context.contentResolver.openInputStream(uri)
                } ?: return null
                val (hash, size) = digestAndSize
                if (size <= 512L || size > MAX_TRANSFER_BYTES) return null
                TrackAudioPayload(
                    fileHash = hash,
                    fileSize = size,
                    fileExt = ext,
                    mimeType = mime,
                    openStream = { context.contentResolver.openInputStream(uri) }
                )
            } else {
                val file = File(path)
                if (!file.exists() || !file.isFile || file.length() <= 512L || file.length() > MAX_TRANSFER_BYTES) {
                    return null
                }
                val ext = file.extension.lowercase().ifBlank { if (track.isVideo) "mp4" else "mp3" }
                val mime = mimeForExtension(ext)
                val digestAndSize = computeStreamHashAndSize { FileInputStream(file) } ?: return null
                TrackAudioPayload(
                    fileHash = digestAndSize.first,
                    fileSize = digestAndSize.second,
                    fileExt = ext,
                    mimeType = mime,
                    openStream = { FileInputStream(file) }
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "resolveTrackAudioPayload skipped: ${e.message}")
            null
        }
    }

    fun computeFileSha256(file: File): String? {
        if (!file.exists() || !file.isFile || file.length() <= 512L) return null
        return computeStreamHashAndSize { FileInputStream(file) }?.first
    }

    private fun computeStreamHashAndSize(open: () -> InputStream?): Pair<String, Long>? {
        return try {
            val stream = open() ?: return null
            val md = MessageDigest.getInstance("SHA-256")
            var total = 0L
            val buf = ByteArray(16 * 1024)
            stream.use { input ->
                var read: Int
                while (input.read(buf).also { read = it } != -1) {
                    md.update(buf, 0, read)
                    total += read
                }
            }
            val hex = md.digest().joinToString("") { "%02x".format(it) }
            hex to total
        } catch (_: Exception) {
            null
        }
    }

    private fun detectExtAndMimeFromUri(context: Context, uri: Uri, isVideo: Boolean): Pair<String, String> {
        val rawMime = try {
            context.contentResolver.getType(uri)?.lowercase()
        } catch (_: Exception) {
            null
        }
        return when {
            rawMime == null -> if (isVideo) "mp4" to "video/mp4" else "mp3" to "audio/mpeg"
            rawMime.contains("mp4") && rawMime.contains("audio") -> "m4a" to "audio/mp4"
            rawMime.contains("mp4") -> "mp4" to "video/mp4"
            rawMime.contains("mpeg") || rawMime.contains("mp3") -> "mp3" to "audio/mpeg"
            rawMime.contains("wav") -> "wav" to "audio/wav"
            rawMime.contains("ogg") -> "ogg" to "audio/ogg"
            rawMime.contains("flac") -> "flac" to "audio/flac"
            rawMime.contains("aac") -> "aac" to "audio/aac"
            rawMime.contains("webm") -> "webm" to rawMime
            else -> if (isVideo) "mp4" to "video/mp4" else "mp3" to "audio/mpeg"
        }
    }

    private fun mimeForExtension(ext: String): String {
        return when (ext.lowercase()) {
            "mp3" -> "audio/mpeg"
            "m4a" -> "audio/mp4"
            "mp4" -> "video/mp4"
            "wav" -> "audio/wav"
            "ogg" -> "audio/ogg"
            "flac" -> "audio/flac"
            "aac" -> "audio/aac"
            "webm" -> "audio/webm"
            else -> "application/octet-stream"
        }
    }
}
