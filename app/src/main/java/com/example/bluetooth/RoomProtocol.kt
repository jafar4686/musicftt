package com.example.bluetooth

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

data class TrackAudioPayload(
    val fileHash: String,
    val fileSize: Long,
    val fileExt: String,
    val openStream: () -> InputStream?
)

object RoomProtocol {
    private const val TAG = "RoomProtocol"
    const val MAX_TRANSFER_BYTES = 35L * 1024L * 1024L
    const val CHUNK_RAW_BYTES = 16 * 1024

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
        hasBinary: Boolean
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
        }.toString()
    }

    fun encodeNeedFile(transferId: String, fileHash: String): String {
        return JSONObject().apply {
            put("type", "NEED_FILE")
            put("transferId", transferId)
            put("fileHash", fileHash)
        }.toString()
    }

    fun encodeTrackReady(transferId: String, fileHash: String): String {
        return JSONObject().apply {
            put("type", "TRACK_READY")
            put("transferId", transferId)
            put("fileHash", fileHash)
        }.toString()
    }

    fun encodeFileStart(transferId: String, fileHash: String, fileSize: Long, fileExt: String, title: String): String {
        return JSONObject().apply {
            put("type", "FILE_START")
            put("transferId", transferId)
            put("fileHash", fileHash)
            put("fileSize", fileSize)
            put("fileExt", fileExt)
            put("title", title)
        }.toString()
    }

    fun encodeFileChunk(transferId: String, seq: Int, base64Data: String): String {
        return JSONObject().apply {
            put("type", "FILE_CHUNK")
            put("transferId", transferId)
            put("seq", seq)
            put("data", base64Data)
        }.toString()
    }

    fun encodeFileEnd(transferId: String, fileHash: String): String {
        return JSONObject().apply {
            put("type", "FILE_END")
            put("transferId", transferId)
            put("fileHash", fileHash)
        }.toString()
    }

    fun encodeSyncPlay(
        track: Track,
        positionMs: Long,
        speed: Float,
        isPlaying: Boolean,
        syncDelayMs: Long = 240L,
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
                val ext = detectExtFromUriOrMime(context, uri, track.isVideo)
                val digestAndSize = computeStreamHashAndSize {
                    context.contentResolver.openInputStream(uri)
                } ?: return null
                val (hash, size) = digestAndSize
                if (size <= 512L || size > MAX_TRANSFER_BYTES) return null
                TrackAudioPayload(
                    fileHash = hash,
                    fileSize = size,
                    fileExt = ext,
                    openStream = { context.contentResolver.openInputStream(uri) }
                )
            } else {
                val file = File(path)
                if (!file.exists() || !file.isFile || file.length() <= 512L || file.length() > MAX_TRANSFER_BYTES) {
                    return null
                }
                val ext = file.extension.lowercase().ifBlank { if (track.isVideo) "mp4" else "mp3" }
                val digestAndSize = computeStreamHashAndSize { FileInputStream(file) } ?: return null
                TrackAudioPayload(
                    fileHash = digestAndSize.first,
                    fileSize = digestAndSize.second,
                    fileExt = ext,
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

    private fun detectExtFromUriOrMime(context: Context, uri: Uri, isVideo: Boolean): String {
        val mime = try {
            context.contentResolver.getType(uri)?.lowercase()
        } catch (_: Exception) {
            null
        }
        return when {
            mime == null -> if (isVideo) "mp4" else "mp3"
            mime.contains("mp4") && mime.contains("audio") -> "m4a"
            mime.contains("mp4") -> "mp4"
            mime.contains("mpeg") || mime.contains("mp3") -> "mp3"
            mime.contains("wav") -> "wav"
            mime.contains("ogg") -> "ogg"
            mime.contains("flac") -> "flac"
            mime.contains("aac") -> "aac"
            mime.contains("webm") -> "webm"
            else -> if (isVideo) "mp4" else "mp3"
        }
    }
}
