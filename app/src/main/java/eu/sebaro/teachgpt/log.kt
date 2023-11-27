package eu.sebaro.teachgpt

import android.util.Log

const val logFileName = "log.kt"

fun log(msg: String, error: Throwable? = null) {
    val ct = Thread.currentThread()
    val tagName = ct.name
    val traces = ct.stackTrace
    val lineCount = traces.size - 1
    val stackTrace = traces.slice(3..lineCount).find { it.fileName != logFileName }
    val message = if (stackTrace != null) {
        val cname = stackTrace.className.substringAfterLast(".")
        "[${stackTrace.fileName}:${stackTrace.lineNumber}] $cname.${stackTrace.methodName} : $msg"
    } else {
        msg
    }

    if (error != null) {
        Log.e(tagName, message, error)
    } else {
        Log.d(tagName, message)
    }
}

fun log(error: Throwable) {
    log(error.message ?: "", error)
}