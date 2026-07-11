package com.griddrop.net

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import com.griddrop.util.Diag
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong


data class SharedFile(
    val id: String,
    val displayName: String,
    val file: File,
    val size: Long,
    val mime: String,
)


data class ReceivedFile(
    val name: String,
    val location: String,
)


data class UploadSession(
    val id: String,
    val displayName: String,
    val declaredSize: Long,
    val temp: File,
)


class TransferRepository(context: Context) {

    private val appContext = context.applicationContext
    private val outgoingDir = File(appContext.filesDir, "outgoing").apply { mkdirs() }
    private val incomingDir = File(appContext.filesDir, "incoming").apply { mkdirs() }

    private val shared = ConcurrentHashMap<String, SharedFile>()
    private val uploads = ConcurrentHashMap<String, UploadSession>()
    private val completed = ConcurrentHashMap<String, ReceivedFile>()
    private val idSeq = AtomicLong(0)

    
    
    private val _sharedFlow = MutableStateFlow<List<SharedFile>>(emptyList())
    val sharedFlow: StateFlow<List<SharedFile>> = _sharedFlow.asStateFlow()

    private val _receivedFlow = MutableStateFlow<List<ReceivedFile>>(emptyList())
    val receivedFlow: StateFlow<List<ReceivedFile>> = _receivedFlow.asStateFlow()

    private fun publishShared() { _sharedFlow.value = shared.values.sortedBy { it.displayName } }
    private fun publishReceived() { _receivedFlow.value = completed.values.sortedBy { it.name } }

    

    fun sharedFiles(): List<SharedFile> = shared.values.sortedBy { it.displayName }

    fun sharedFile(id: String): SharedFile? = shared[id]

    
    fun importForSending(uri: Uri): SharedFile {
        val resolver = appContext.contentResolver
        var name = "file"
        var size = -1L
        resolver.query(uri, null, null, null, null)?.use { c ->
            val nameIdx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIdx = c.getColumnIndex(OpenableColumns.SIZE)
            if (c.moveToFirst()) {
                if (nameIdx >= 0) name = c.getString(nameIdx) ?: name
                if (sizeIdx >= 0 && !c.isNull(sizeIdx)) size = c.getLong(sizeIdx)
            }
        }
        val id = nextId()
        val dest = File(outgoingDir, "$id-$name")
        resolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Cannot open $uri" }
            dest.outputStream().use { input.copyTo(it, bufferSize = 1 shl 16) }
        }
        val mime = resolver.getType(uri) ?: "application/octet-stream"
        val entry = SharedFile(id, name, dest, dest.length().also { size = it }, mime)
        shared[id] = entry
        publishShared()
        return entry
    }

    fun removeShared(id: String) {
        shared.remove(id)?.file?.delete()
        publishShared()
    }

    fun clearShared() {
        shared.values.forEach { it.file.delete() }
        shared.clear()
        publishShared()
    }

    

    fun createUpload(displayName: String, declaredSize: Long): UploadSession {
        val id = nextId()
        val temp = File(incomingDir, "$id.part")
        temp.delete()
        val session = UploadSession(id, sanitize(displayName), declaredSize, temp)
        uploads[id] = session
        return session
    }

    fun upload(id: String): UploadSession? = uploads[id]

    
    fun bytesWritten(id: String): Long = uploads[id]?.temp?.length() ?: 0L

    
    fun writeChunk(id: String, offset: Long, data: ByteArray): Long {
        val session = uploads[id] ?: error("Unknown upload $id")
        RandomAccessFile(session.temp, "rw").use { raf ->
            raf.seek(offset)
            raf.write(data)
        }
        Diag.addReceived(data.size.toLong())
        return session.temp.length()
    }

    
    fun isCompleted(id: String): Boolean = completed.containsKey(id)

    
    fun finishUpload(id: String): ReceivedFile? {
        val session = uploads.remove(id) ?: return null
        val size = session.temp.length()
        return try {
            val location = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                publishToMediaStore(session.temp, session.displayName)
            } else {
                publishToLegacyDownloads(session.temp, session.displayName)
            }
            session.temp.delete()
            Diag.log("Received \"${session.displayName}\" ($size B) -> $location")
            ReceivedFile(session.displayName, location).also {
                completed[id] = it
                publishReceived()
            }
        } catch (e: Exception) {
            Diag.log("Saving \"${session.displayName}\" failed: ${e.message}")
            null
        }
    }

    fun completedFiles(): List<ReceivedFile> = completed.values.sortedBy { it.name }

    

    private fun publishToMediaStore(src: File, name: String): String {
        val resolver = appContext.contentResolver
        val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.MIME_TYPE, guessMime(name))
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = resolver.insert(collection, values)
            ?: error("MediaStore insert failed for $name")
        resolver.openOutputStream(uri).use { out ->
            requireNotNull(out) { "Cannot open output for $uri" }
            src.inputStream().use { it.copyTo(out, bufferSize = 1 shl 16) }
        }
        
        values.clear()
        values.put(MediaStore.Downloads.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        return "Downloads/$name"
    }

    private fun publishToLegacyDownloads(src: File, name: String): String {
        @Suppress("DEPRECATION")
        val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        dir.mkdirs()
        val dest = uniqueFile(dir, name)
        src.copyTo(dest, overwrite = false)
        return dest.absolutePath
    }

    private fun guessMime(name: String): String {
        val ext = name.substringAfterLast('.', "").lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
            ?: "application/octet-stream"
    }

    

    private fun nextId(): String = idSeq.incrementAndGet().toString(36) +
        "-" + System.currentTimeMillis().toString(36)

    private fun sanitize(name: String): String =
        name.substringAfterLast('/').substringAfterLast('\\').ifBlank { "file" }

    private fun uniqueFile(dir: File, name: String): File {
        var candidate = File(dir, name)
        if (!candidate.exists()) return candidate
        val dot = name.lastIndexOf('.')
        val base = if (dot > 0) name.substring(0, dot) else name
        val ext = if (dot > 0) name.substring(dot) else ""
        var n = 1
        while (candidate.exists()) candidate = File(dir, "$base ($n)$ext").also { n++ }
        return candidate
    }
}
