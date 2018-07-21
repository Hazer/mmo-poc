package mmo.server.util

import com.soywiz.korio.file.*
import com.soywiz.korio.stream.*
import com.soywiz.korio.util.*
import io.ktor.application.*
import io.ktor.content.*
import io.ktor.http.*
import io.ktor.response.*
import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.io.*
import java.nio.file.*
import java.util.*

fun VfsFile.openByteReadChannel(range: LongRange? = null) = writer(DefaultDispatcher) {
    val ch = channel
    val temp = ByteArray(1024)
    open(VfsOpenMode.READ).use {
        val ss = if (range != null) sliceWithBounds(range.start, range.endInclusive + 1) else this
        while (ss.hasAvailable()) {
            val read = ss.read(temp, 0, temp.size)
            if (read <= 0) break
            ch.writeFully(temp, 0, read)
        }
    }
}.channel

class VfsFileContent(
    val file: VfsFile,
    val stat: VfsStat,
    override val contentType: ContentType = ContentType.defaultForFile(Paths.get(file.absolutePath))
) : OutgoingContent.ReadChannelContent() {
    companion object {
        suspend operator fun invoke(
            file: VfsFile,
            contentType: ContentType = ContentType.defaultForFile(Paths.get(file.absolutePath))
        ) = VfsFileContent(file, file.stat(), contentType)
    }

    override val contentLength: Long get() = stat.size

    init {
        versions += LastModifiedVersion(Date(stat.modifiedTime))
    }

    // TODO: consider using WriteChannelContent to avoid piping
    // Or even make it dual-content so engine implementation can choose
    override fun readFrom(): ByteReadChannel = file.openByteReadChannel()

    override fun readFrom(range: LongRange): ByteReadChannel = file.openByteReadChannel(range)
}


suspend fun ApplicationCall.respondFile(file: VfsFile) = respond(VfsFileContent(file))
