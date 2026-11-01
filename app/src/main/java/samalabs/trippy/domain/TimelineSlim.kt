package samalabs.trippy.domain

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.PushbackReader
import java.io.Reader

/** The server's limit for a Timeline upload, after the app has slimmed it. */
const val MAX_TIMELINE_BYTES = 20 * 1024 * 1024

/** Biggest raw export the app will try to read: phone exports with years of rawSignals get this big. */
const val MAX_TIMELINE_INPUT_BYTES = 400L * 1024 * 1024

class TimelineTooLargeException : IllegalArgumentException(
    "That Timeline is still over 20 MB after trimming. Export a shorter date range from Google Maps and try again.",
)

/**
 * Streams a Google Maps Timeline export and drops what the importer never reads: the top-level
 * `rawSignals` (raw GPS and Wi-Fi scans, usually most of the file) and `userLocationProfile`,
 * plus all whitespace outside strings. Everything else is copied as-is, so the server sees the
 * same JSON minus those keys. Works on all three shapes (`semanticSegments`, the iOS top-level
 * list, Takeout `timelineObjects`) without parsing the file into memory.
 */
object TimelineSlim {
    val DROPPED_KEYS = setOf("rawSignals", "userLocationProfile")

    fun slim(input: InputStream, limit: Int = MAX_TIMELINE_BYTES): ByteArray =
        slim(InputStreamReader(input, Charsets.UTF_8), limit)

    fun slim(input: Reader, limit: Int = MAX_TIMELINE_BYTES): ByteArray {
        val bytes = ByteArrayOutputStream()
        val out = OutputStreamWriter(bytes, Charsets.UTF_8)
        Slimmer(PushbackReader(input.buffered(64 * 1024), 1), out, bytes, limit).run()
        out.flush()
        if (bytes.size() > limit) throw TimelineTooLargeException()
        return bytes.toByteArray()
    }

    private class Slimmer(val r: PushbackReader, val w: OutputStreamWriter, val bytes: ByteArrayOutputStream, val limit: Int) {
        private var written = 0

        fun run() {
            when (peek()) {
                '{' -> topObject()
                '[' -> value(write = true)
                else -> throw IllegalArgumentException("That isn't a Google Maps Timeline file (expected JSON).")
            }
            if (peek() != null) throw IllegalArgumentException("That Timeline file has extra data after the JSON.")
        }

        private fun topObject() {
            expect('{'); put('{')
            var first = true
            while (true) {
                when (val c = next()) {
                    '}' -> { put('}'); return }
                    ',' -> continue
                    '"' -> {
                        val key = StringBuilder()
                        string { key.append(it) }
                        expect(':')
                        if (key.toString() in DROPPED_KEYS) value(write = false)
                        else {
                            if (!first) put(',')
                            put('"'); key.forEach { put(it) }; put('"'); put(':')
                            value(write = true)
                            first = false
                        }
                    }
                    else -> throw bad(c)
                }
            }
        }

        /** Copies (or skips) one JSON value. Containers are walked with a depth counter, not recursion. */
        private fun value(write: Boolean) {
            val c = next() ?: throw bad(null)
            when (c) {
                '"' -> { if (write) put('"'); string { if (write) put(it) }; if (write) put('"') }
                '{', '[' -> {
                    if (write) put(c)
                    var depth = 1
                    while (depth > 0) {
                        val ch = raw() ?: throw bad(null)
                        when {
                            ch.isWhitespace() -> continue
                            ch == '"' -> { if (write) put('"'); string { if (write) put(it) }; if (write) put('"'); continue }
                            ch == '{' || ch == '[' -> depth++
                            ch == '}' || ch == ']' -> depth--
                        }
                        if (write) put(ch)
                    }
                }
                else -> {
                    var ch: Char? = c
                    while (ch != null && ch != ',' && ch != '}' && ch != ']' && !ch.isWhitespace()) {
                        if (write) put(ch)
                        ch = raw()
                    }
                    if (ch != null) r.unread(ch.code)
                }
            }
        }

        /** Body of a string after its opening quote, escapes kept verbatim. */
        private inline fun string(emit: (Char) -> Unit) {
            while (true) {
                val ch = raw() ?: throw bad(null)
                if (ch == '"') return
                emit(ch)
                if (ch == '\\') emit(raw() ?: throw bad(null))
            }
        }

        private fun raw(): Char? = r.read().let { if (it < 0) null else it.toChar() }
        private fun next(): Char? { while (true) { val c = raw() ?: return null; if (!c.isWhitespace()) return c } }
        private fun peek(): Char? = next()?.also { r.unread(it.code) }
        private fun expect(c: Char) { val got = next(); if (got != c) throw bad(got) }
        private fun bad(c: Char?) = IllegalArgumentException(
            if (c == null) "That Timeline file ends early. Export it again from Google Maps." else "That Timeline file isn't valid JSON.",
        )

        private fun put(c: Char) {
            w.write(c.code)
            if (++written % (256 * 1024) == 0) { w.flush(); if (bytes.size() > limit) throw TimelineTooLargeException() }
        }
    }
}
