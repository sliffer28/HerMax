package com.hermes.client

import android.app.Application
import android.util.Log
import dagger.hilt.android.HiltAndroidApp
import java.io.File

@HiltAndroidApp
class HermesApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e("HermesCrash", "FATAL CRASH in thread ${thread.name}: ${throwable.message}", throwable)
            try {
                val crashLog = File(filesDir, "crash.log")
                crashLog.writeText("CRASH in thread ${thread.name}:\n" + Log.getStackTraceString(throwable))
            } catch (_: Exception) {}
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }
}
