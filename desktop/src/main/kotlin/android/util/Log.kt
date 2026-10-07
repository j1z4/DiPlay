package android.util

import java.io.PrintWriter
import java.io.StringWriter
import java.time.LocalTime

/**
 * Desktop stand-in for Android's logger so DiPlay's shared protocol code compiles unchanged
 * on the JVM. Only the desktop build compiles this file; Android uses the framework class.
 */
object Log {
    const val VERBOSE = 2
    const val DEBUG = 3
    const val INFO = 4
    const val WARN = 5
    const val ERROR = 6

    @Volatile var minimumLevel: Int = INFO

    @JvmStatic fun v(tag: String?, msg: String): Int = write(VERBOSE, tag, msg, null)
    @JvmStatic fun v(tag: String?, msg: String, tr: Throwable?): Int = write(VERBOSE, tag, msg, tr)
    @JvmStatic fun d(tag: String?, msg: String): Int = write(DEBUG, tag, msg, null)
    @JvmStatic fun d(tag: String?, msg: String, tr: Throwable?): Int = write(DEBUG, tag, msg, tr)
    @JvmStatic fun i(tag: String?, msg: String): Int = write(INFO, tag, msg, null)
    @JvmStatic fun i(tag: String?, msg: String, tr: Throwable?): Int = write(INFO, tag, msg, tr)
    @JvmStatic fun w(tag: String?, msg: String): Int = write(WARN, tag, msg, null)
    @JvmStatic fun w(tag: String?, msg: String, tr: Throwable?): Int = write(WARN, tag, msg, tr)
    @JvmStatic fun w(tag: String?, tr: Throwable?): Int = write(WARN, tag, "", tr)
    @JvmStatic fun e(tag: String?, msg: String): Int = write(ERROR, tag, msg, null)
    @JvmStatic fun e(tag: String?, msg: String, tr: Throwable?): Int = write(ERROR, tag, msg, tr)

    @JvmStatic fun isLoggable(tag: String?, level: Int): Boolean = level >= minimumLevel

    @JvmStatic fun getStackTraceString(tr: Throwable?): String {
        if (tr == null) return ""
        val writer = StringWriter()
        tr.printStackTrace(PrintWriter(writer))
        return writer.toString()
    }

    private fun write(level: Int, tag: String?, msg: String, tr: Throwable?): Int {
        if (level < minimumLevel) return 0
        val line = "${LocalTime.now()} ${"VDIWE"[level - VERBOSE]}/${tag ?: "-"}: $msg"
        val stream = if (level >= WARN) System.err else System.out
        stream.println(line)
        tr?.let { stream.println(getStackTraceString(it)) }
        return line.length
    }
}
