package com.vincentwill.calm.util

import android.util.Log
import com.vincentwill.calm.BuildConfig

/**
 * The only logger the app uses. Logs nothing in release builds.
 *
 * Even in debug builds, callers must pass metadata only (package names, counts, states), never
 * notification titles, texts or sender names.
 */
object SafeLog {
    private const val TAG = "Calm"

    fun d(message: () -> String) {
        if (BuildConfig.DEBUG) Log.d(TAG, message())
    }

    fun w(message: () -> String, error: Throwable? = null) {
        if (BuildConfig.DEBUG) Log.w(TAG, message(), error)
    }
}
