package com.griddrop.net

import android.content.Context
import com.griddrop.Role
import com.griddrop.SERVER_PORT
import com.griddrop.util.Diag
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.request.receiveStream
import io.ktor.server.response.header
import io.ktor.server.response.respondOutputStream
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.routing
import java.io.RandomAccessFile


class GridDropServer(
    private val context: Context,
    private val repo: TransferRepository,
    private val role: Role,
) {
    private var engine: EmbeddedServer<*, *>? = null

    fun start() {
        engine = embeddedServer(CIO, port = SERVER_PORT, host = "0.0.0.0") {
            install(CORS) { anyHost() }
            routing {
                
                get("/") { respondAsset("web/index.html", ContentType.Text.Html) }
                get("/app.js") { respondAsset("web/app.js", ContentType.Application.JavaScript) }
                get("/style.css") { respondAsset("web/style.css", ContentType.Text.CSS) }

                get("/api/info") {
                    call.respondText(infoJson(), ContentType.Application.Json)
                }

                
                get("/d/{id}") { serveDownload() }

                
                post("/api/upload/init") {
                    val name = call.parameters["name"] ?: query("name") ?: "file"
                    val size = (query("size"))?.toLongOrNull() ?: -1L
                    val session = repo.createUpload(name, size)
                    call.respondText(
                        """{"id":"${session.id}","offset":0}""",
                        ContentType.Application.Json,
                    )
                }
                get("/api/upload/status") {
                    val id = query("id") ?: return@get call.respondText(
                        """{"error":"missing id"}""",
                        ContentType.Application.Json,
                        HttpStatusCode.BadRequest,
                    )
                    val status = if (repo.isCompleted(id)) "completed" else "active"
                    call.respondText(
                        """{"offset":${repo.bytesWritten(id)},"status":"$status"}""",
                        ContentType.Application.Json,
                    )
                }
                put("/api/upload/chunk") {
                    val id = query("id")
                    val offset = query("offset")?.toLongOrNull()
                    if (id == null || offset == null || repo.upload(id) == null) {
                        return@put call.respondText(
                            """{"error":"bad chunk request"}""",
                            ContentType.Application.Json,
                            HttpStatusCode.BadRequest,
                        )
                    }
                    val bytes = call.receiveStream().readBytes()
                    val total = repo.writeChunk(id, offset, bytes)
                    call.respondText("""{"offset":$total}""", ContentType.Application.Json)
                }
                post("/api/upload/finish") {
                    val id = query("id")
                    val file = id?.let { repo.finishUpload(it) }
                    if (file == null) {
                        call.respondText(
                            """{"error":"unknown upload"}""",
                            ContentType.Application.Json,
                            HttpStatusCode.BadRequest,
                        )
                    } else {
                        call.respondText(
                            """{"saved":"${file.name.jsonEscape()}"}""",
                            ContentType.Application.Json,
                        )
                    }
                }
            }
        }.also { it.start(wait = false) }
        Diag.log("Server listening on :$SERVER_PORT (role $role)")
    }

    fun stop() {
        engine?.stop(gracePeriodMillis = 500, timeoutMillis = 1500)
        engine = null
        Diag.log("Server stopped")
    }

    

    private suspend fun io.ktor.server.routing.RoutingContext.serveDownload() {
        val id = call.parameters["id"]
        val shared = id?.let { repo.sharedFile(it) }
        if (shared == null) {
            call.respondText("Not found", ContentType.Text.Plain, HttpStatusCode.NotFound)
            return
        }
        val file = shared.file
        val total = file.length()
        val etag = "\"$total-${file.lastModified()}\""
        val contentType = runCatching { ContentType.parse(shared.mime) }.getOrDefault(ContentType.Application.OctetStream)

        call.response.header(HttpHeaders.AcceptRanges, "bytes")
        call.response.header(HttpHeaders.ETag, etag)
        call.response.header(
            HttpHeaders.ContentDisposition,
            "attachment; filename=\"${shared.displayName.headerEscape()}\"",
        )

        val rangeHeader = call.request.headers[HttpHeaders.Range]
        val ifRange = call.request.headers[HttpHeaders.IfRange]
        val range = parseRange(rangeHeader, total)

        
        val honourRange = range != null && (ifRange == null || ifRange == etag)

        if (!honourRange) {
            call.respondOutputStream(contentType, HttpStatusCode.OK, contentLength = total) {
                streamFile(file, 0, total, this)
            }
            Diag.addSent(total)
            Diag.log("Sent \"${shared.displayName}\" ($total B, full)")
            return
        }

        val (start, end) = range!!
        val length = end - start + 1
        call.response.header(HttpHeaders.ContentRange, "bytes $start-$end/$total")
        call.respondOutputStream(contentType, HttpStatusCode.PartialContent, contentLength = length) {
            streamFile(file, start, length, this)
        }
        Diag.addSent(length)
        Diag.log("Sent \"${shared.displayName}\" ($length B, range $start-$end)")
    }

    private fun streamFile(file: java.io.File, offset: Long, length: Long, out: java.io.OutputStream) {
        RandomAccessFile(file, "r").use { raf ->
            raf.seek(offset)
            val buf = ByteArray(1 shl 16)
            var remaining = length
            while (remaining > 0) {
                val toRead = minOf(remaining, buf.size.toLong()).toInt()
                val read = raf.read(buf, 0, toRead)
                if (read <= 0) break
                out.write(buf, 0, read)
                remaining -= read
            }
            out.flush()
        }
    }

    
    private fun parseRange(header: String?, total: Long): Pair<Long, Long>? {
        if (header == null || !header.startsWith("bytes=") || total <= 0) return null
        val spec = header.removePrefix("bytes=").substringBefore(",").trim()
        val dash = spec.indexOf('-')
        if (dash < 0) return null
        val startStr = spec.substring(0, dash)
        val endStr = spec.substring(dash + 1)
        return try {
            when {
                startStr.isEmpty() -> {
                    
                    val n = endStr.toLong().coerceAtMost(total)
                    (total - n) to (total - 1)
                }
                endStr.isEmpty() -> startStr.toLong() to (total - 1)
                else -> startStr.toLong() to endStr.toLong().coerceAtMost(total - 1)
            }.takeIf { it.first in 0 until total && it.second >= it.first }
        } catch (e: NumberFormatException) {
            null
        }
    }

    private suspend fun io.ktor.server.routing.RoutingContext.respondAsset(
        path: String,
        type: ContentType,
    ) {
        val bytes = context.assets.open(path).use { it.readBytes() }
        call.respondText(String(bytes, Charsets.UTF_8), type)
    }

    private fun io.ktor.server.routing.RoutingContext.query(name: String): String? =
        call.request.queryParameters[name]

    private fun infoJson(): String {
        val files = repo.sharedFiles().joinToString(",") { f ->
            """{"id":"${f.id.jsonEscape()}","name":"${f.displayName.jsonEscape()}","size":${f.size},"mime":"${f.mime.jsonEscape()}"}"""
        }
        return """{"role":"${role.name}","files":[$files]}"""
    }

    private fun String.jsonEscape(): String =
        replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")

    private fun String.headerEscape(): String = replace("\"", "").replace("\n", "")
}
