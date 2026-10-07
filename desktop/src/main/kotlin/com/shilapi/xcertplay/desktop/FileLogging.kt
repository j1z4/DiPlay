package com.shilapi.xcertplay.desktop

import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.io.PrintStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Mirrors everything written to stdout/stderr (OpenPlay's log and DiPlay's android.util.Log shim)
 * into %APPDATA%\OpenPlay\logs\openplay-<timestamp>.log; the packaged OpenPlay.exe has no console.
 */
object FileLogging {
    const val DEFAULT_KEEP_FILES = 10
    private val STAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")

    /** Starts a new log file for this run, keeping the newest [keepFiles] in all, and returns it. */
    fun install(root: File, keepFiles: Int = DEFAULT_KEEP_FILES): File {
        val directory = File(root, "logs").apply { mkdirs() }
        val file = File(directory, "openplay-${LocalDateTime.now().format(STAMP)}.log")
        val sink = FileOutputStream(file, true)
        System.setOut(PrintStream(Tee(System.out, sink), true, Charsets.UTF_8))
        System.setErr(PrintStream(Tee(System.err, sink), true, Charsets.UTF_8))
        prune(directory, keepFiles)
        return file
    }

    /** Keeps only the newest [keepFiles] logs (at least the current one). */
    internal fun prune(directory: File, keepFiles: Int = DEFAULT_KEEP_FILES) {
        directory.listFiles { candidate -> candidate.name.startsWith("openplay-") && candidate.name.endsWith(".log") }
            ?.sortedByDescending { it.name }
            ?.drop(keepFiles.coerceAtLeast(1))
            ?.forEach { it.delete() }
    }

    private class Tee(private val first: OutputStream, private val second: OutputStream) : OutputStream() {
        override fun write(b: Int) = synchronized(second) {
            first.write(b)
            second.write(b)
        }

        override fun write(b: ByteArray, off: Int, len: Int) = synchronized(second) {
            first.write(b, off, len)
            second.write(b, off, len)
        }

        override fun flush() = synchronized(second) {
            first.flush()
            second.flush()
        }
    }
}
