package ma.tany.collect.core.push

import android.util.Log
import ma.tany.collect.BuildConfig

/**
 * Privacy-safe push diagnostics (DEV / STAGING builds only — silent in PROD): event names and codes, never a token,
 * a notification body, an amount or a personal detail.
 */
object PushDiagnostics {
    private const val TAG = "TanyPush"

    fun log(event: String, detail: String? = null) {
        if (BuildConfig.TANY_ENVIRONMENT == "PROD") return
        Log.i(TAG, if (detail == null) event else "$event · $detail")
    }
}
