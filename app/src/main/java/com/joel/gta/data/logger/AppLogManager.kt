package com.joel.gta.data.logger

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.content.FileProvider
import com.joel.gta.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Robust Crash and Event File Logger for GTAR.
 * Intercepts uncaught exceptions and appends timestamped diagnostics to
 * Android/data/<applicationId>/files/logs/gtar-debug.log
 * Accessible via Android File Explorer, USB, or in-app Debug Logs viewer.
 */
object AppLogManager {

    private const val LOG_TAG = "AppLogManager"
    private const val LOG_FILE_NAME = "gtar-debug.log"
    private const val MAX_LOG_SIZE_BYTES = 2 * 1024 * 1024L // 2MB

    private var appContext: Context? = null
    private var logFile: File? = null
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    private val lock = Any()

    private val _liveLogs = MutableStateFlow("")
    val liveLogs: StateFlow<String> = _liveLogs.asStateFlow()

    fun init(context: Context) {
        val appCtx = context.applicationContext
        appContext = appCtx

        try {
            val logsDir = File(appCtx.getExternalFilesDir(null), "logs")
            if (!logsDir.exists()) {
                logsDir.mkdirs()
            }
            logFile = File(logsDir, LOG_FILE_NAME)
            if (!logFile!!.exists()) {
                logFile!!.createNewFile()
            }

            // Install uncaught exception crash handler
            val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
                try {
                    logCrash(thread, throwable)
                } catch (t: Throwable) {
                    t.printStackTrace()
                } finally {
                    previousHandler?.uncaughtException(thread, throwable)
                }
            }

            i(LOG_TAG, "Logger initialized. App: ${BuildConfig.APPLICATION_ID} ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}), Debug: ${BuildConfig.DEBUG}")
            i(LOG_TAG, "Device: ${Build.MANUFACTURER} ${Build.MODEL} (Android API ${Build.VERSION.SDK_INT})")
            refreshLiveLogs()
        } catch (e: Exception) {
            Log.e(LOG_TAG, "Failed to initialize AppLogManager file storage", e)
        }
    }

    private fun logCrash(thread: Thread, throwable: Throwable) {
        val stringWriter = StringWriter()
        throwable.printStackTrace(PrintWriter(stringWriter))
        val stackTrace = stringWriter.toString()

        val crashReport = buildString {
            appendLine("==========================================================================")
            appendLine("CRASH DETECTED AT: ${dateFormat.format(Date())}")
            appendLine("Thread: ${thread.name} (ID: ${thread.id})")
            appendLine("Exception: ${throwable.javaClass.name}: ${throwable.message}")
            appendLine("Package: ${BuildConfig.APPLICATION_ID} ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}, Android SDK ${Build.VERSION.SDK_INT}")
            appendLine("--------------------------------------------------------------------------")
            appendLine("STACK TRACE:")
            appendLine(stackTrace)
            appendLine("==========================================================================")
        }

        writeRaw(crashReport)
        Log.e(LOG_TAG, crashReport)
    }

    fun d(tag: String, message: String) {
        writeEntry("DEBUG", tag, message)
        if (BuildConfig.DEBUG) {
            Log.d(tag, message)
        }
    }

    fun i(tag: String, message: String) {
        writeEntry("INFO", tag, message)
        Log.i(tag, message)
    }

    fun w(tag: String, message: String, throwable: Throwable? = null) {
        val extra = if (throwable != null) "\n" + Log.getStackTraceString(throwable) else ""
        writeEntry("WARN", tag, message + extra)
        Log.w(tag, message, throwable)
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        val extra = if (throwable != null) "\n" + Log.getStackTraceString(throwable) else ""
        writeEntry("ERROR", tag, message + extra)
        Log.e(tag, message, throwable)
    }

    fun logPresentationEvent(action: String, details: String, success: Boolean, error: Throwable? = null) {
        val status = if (success) "SUCCESS" else "FAILED"
        val message = "Presentation/Cast [$action] -> Status: $status | Details: $details"
        if (success) {
            i("StagePresentation", message)
        } else {
            e("StagePresentation", message, error)
        }
    }

    private fun writeEntry(level: String, tag: String, message: String) {
        val timeStr = synchronized(dateFormat) { dateFormat.format(Date()) }
        val line = "[$timeStr] [${level.padEnd(5)}] [$tag] $message\n"
        writeRaw(line)
    }

    private fun writeRaw(content: String) {
        synchronized(lock) {
            try {
                val file = logFile ?: return
                // Check log rotation if exceeds max size
                if (file.length() > MAX_LOG_SIZE_BYTES) {
                    val existing = file.readText()
                    val trimmed = existing.takeLast((MAX_LOG_SIZE_BYTES / 2).toInt())
                    file.writeText("[LOG ROTATED - PREVIOUS LOGS TRIMMED]\n$trimmed")
                }

                FileWriter(file, true).use { writer ->
                    writer.write(content)
                }
            } catch (e: Exception) {
                Log.e(LOG_TAG, "Error writing to debug log file", e)
            }
        }
        refreshLiveLogs()
    }

    fun readLogs(): String {
        synchronized(lock) {
            return try {
                val file = logFile ?: return "Log file not initialized."
                if (file.exists()) {
                    file.readText()
                } else {
                    "Log file is empty."
                }
            } catch (e: Exception) {
                "Error reading log file: ${e.message}"
            }
        }
    }

    fun clearLogs(): Boolean {
        synchronized(lock) {
            return try {
                val file = logFile ?: return false
                if (file.exists()) {
                    file.writeText("")
                }
                i(LOG_TAG, "Log file cleared by user.")
                refreshLiveLogs()
                true
            } catch (e: Exception) {
                Log.e(LOG_TAG, "Failed to clear log file", e)
                false
            }
        }
    }

    fun getLogFile(): File? = logFile

    fun getLogFileUri(context: Context): Uri? {
        val file = logFile ?: return null
        if (!file.exists() || file.length() == 0L) {
            writeRaw("GTAR Log Initialized\n")
        }
        return try {
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
        } catch (e: Exception) {
            Log.e(LOG_TAG, "Failed to get Uri for log file", e)
            null
        }
    }

    fun createExportIntent(context: Context): Intent? {
        val uri = getLogFileUri(context) ?: return null
        return Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "GTAR Debug Log (${BuildConfig.VERSION_NAME})")
            putExtra(Intent.EXTRA_TEXT, "Attached is the GTAR debug log file generated on ${dateFormat.format(Date())}.")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun refreshLiveLogs() {
        try {
            val content = readLogs()
            _liveLogs.value = content
        } catch (_: Exception) {}
    }
}
