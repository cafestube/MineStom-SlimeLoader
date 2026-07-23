package eu.cafestube.slimeloader.helpers

import java.io.IOException
import java.io.InputStream

class LimitedInputStream(private val inputStream: InputStream, private var remaining: Int) : InputStream() {

    override fun read(): Int {
        if (remaining <= 0) return -1
        val b = inputStream.read()
        if (b != -1) remaining--
        return b
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (remaining <= 0) return -1
        val readLen = minOf(len, remaining)
        val read = inputStream.read(b, off, readLen)
        if (read > 0) remaining -= read
        return read
    }

    fun drainRemaining() {
        val buffer = ByteArray(512)
        try {
            while (remaining > 0) {
                val read = read(buffer, 0, minOf(buffer.size, remaining))
                if (read == -1) break
            }
        } catch (_: IOException) {
        }
    }

    override fun close() {
        drainRemaining()
    }
}
