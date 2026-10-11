package com.example.riffmaker

import android.app.Application
import android.content.Context
import android.util.Log
import java.io.File

class RiffMakerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashReporter.install(this)
    }
}

/** Writes crash traces locally so users can email a report. No network, no SDKs. */
object CrashReporter {

    private const val FILE_NAME = "last_crash.txt"
    private fun file(ctx: Context) = File(ctx.filesDir, FILE_NAME)

    fun install(ctx: Context) {
        val previous = file(ctx)
        val system = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try { previous.writeText(Log.getStackTraceString(throwable)) } catch (_: Exception) {}
            system?.uncaughtException(thread, throwable)
        }
    }

    /** Returns and clears the previous crash log, if any. */
    fun takeReport(ctx: Context): String? {
        val f = file(ctx)
        val text = runCatching { f.takeIf { it.exists() }?.readText() }.getOrNull()
        f.delete()
        return text?.trim()?.takeIf { it.isNotEmpty() }
    }
}
