package com.pin.lever

import java.io.BufferedInputStream
import java.io.IOException
import java.io.InputStream

/**
 * An input stream wrapper that supports unlimited independent cursors for
 * marking and resetting. Each cursor is a token, and it's the caller's
 * responsibility to keep track of these.
 */
internal class MarkableInputStream private constructor(
    `in`: InputStream,
    size: Int,
    limitIncrement: Int
) : InputStream() {
    private val `in`: InputStream
    private var offset: Long = 0
    private var reset: Long = 0
    private var limit: Long = 0
    private var defaultMark: Long = -1
    private var allowExpire = true
    private var limitIncrement = -1

    @JvmOverloads
    constructor(
        `in`: InputStream,
        size: Int = DEFAULT_BUFFER_SIZE
    ) : this(`in`, size, DEFAULT_LIMIT_INCREMENT) {
    }

    /** Marks this place in the stream so we can reset back to it later.  */
    override fun mark(readLimit: Int) {
        defaultMark = savePosition(readLimit)
    }

    /**
     * Returns an opaque token representing the current position in the stream.
     * Call [.reset] to return to this position in the stream later.
     * It is an error to call [.reset] after consuming more than
     * `readLimit` bytes from this stream.
     */
    fun savePosition(readLimit: Int): Long {
        val offsetLimit = offset + readLimit
        if (limit < offsetLimit) {
            setLimit(offsetLimit)
        }
        return offset
    }

    fun allowMarksToExpire(allowExpire: Boolean) {
        this.allowExpire = allowExpire
    }

    /**
     * Makes sure that the underlying stream can backtrack the full range from
     * `reset` thru `limit`. Since we can't call `mark()`
     * without also adjusting the reset-to-position on the underlying stream this
     * method resets first and then marks the union of the two byte ranges. On
     * buffered streams this additional cursor motion shouldn't result in any
     * additional I/O.
     */
    private fun setLimit(limit: Long) {
        try {
            if (reset < offset && offset <= this.limit) {
                `in`.reset()
                `in`.mark((limit - reset).toInt())
                skip(reset, offset)
            } else {
                reset = offset
                `in`.mark((limit - offset).toInt())
            }
            this.limit = limit
        } catch (e: IOException) {
            throw IllegalStateException("Unable to mark: $e")
        }
    }

    /** Resets the stream to the most recent [mark][.mark].  */
    @Throws(IOException::class)
    override fun reset() {
        reset(defaultMark)
    }

    /** Resets the stream to the position recorded by `token`.  */
    @Throws(IOException::class)
    fun reset(token: Long) {
        if (offset > limit || token < reset) {
            throw IOException("Cannot reset")
        }
        `in`.reset()
        skip(reset, token)
        offset = token
    }

    /** Skips `target - current` bytes and returns.  */
    @Throws(IOException::class)
    private fun skip(current: Long, target: Long) {
        var current = current
        while (current < target) {
            var skipped = `in`.skip(target - current)
            if (skipped == 0L) {
                skipped = if (read() == -1) {
                    break // EOF
                } else {
                    1
                }
            }
            current += skipped
        }
    }

    @Throws(IOException::class)
    override fun read(): Int {
        if (!allowExpire && offset + 1 > limit) {
            setLimit(limit + limitIncrement)
        }
        val result = `in`.read()
        if (result != -1) {
            offset++
        }
        return result
    }

    @Throws(IOException::class)
    override fun read(buffer: ByteArray): Int {
        if (!allowExpire && offset + buffer.size > limit) {
            setLimit(offset + buffer.size + limitIncrement)
        }
        val count = `in`.read(buffer)
        if (count != -1) {
            offset += count.toLong()
        }
        return count
    }

    @Throws(IOException::class)
    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (!allowExpire && this.offset + length > limit) {
            setLimit(this.offset + length + limitIncrement)
        }
        val count = `in`.read(buffer, offset, length)
        if (count != -1) {
            this.offset += count.toLong()
        }
        return count
    }

    @Throws(IOException::class)
    override fun skip(byteCount: Long): Long {
        if (!allowExpire && offset + byteCount > limit) {
            setLimit(offset + byteCount + limitIncrement)
        }
        val skipped = `in`.skip(byteCount)
        offset += skipped
        return skipped
    }

    @Throws(IOException::class)
    override fun available(): Int {
        return `in`.available()
    }

    @Throws(IOException::class)
    override fun close() {
        `in`.close()
    }

    override fun markSupported(): Boolean {
        return `in`.markSupported()
    }

    companion object {
        private const val DEFAULT_BUFFER_SIZE = 4096
        private const val DEFAULT_LIMIT_INCREMENT = 1024
    }

    init {
        var `in` = `in`
        if (!`in`.markSupported()) {
            `in` = BufferedInputStream(`in`, size)
        }
        this.`in` = `in`
        this.limitIncrement = limitIncrement
    }
}