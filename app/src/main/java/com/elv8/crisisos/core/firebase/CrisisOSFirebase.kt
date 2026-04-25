package com.elv8.crisisos.core.firebase

import android.content.Context
import android.os.Bundle
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thin Firebase facade. Initialization happens automatically through the
 * `google-services` Gradle plugin + the `FirebaseInitProvider` ContentProvider
 * baked into `firebase-common`. We only expose [logEvent] so the app can log
 * lifecycle events without each caller pulling Firebase APIs directly.
 *
 * No mocks here — the live `google-services.json` for `com.elv8.crisisos`
 * (project `zenith-devs`) is shipped in `app/google-services.json`.
 *
 * NOTE: kept intentionally simple — no `by lazy`, no default args, no Kotlin
 * delegated properties. KSP2 (Analysis API) on Kotlin 2.2.10 + Hilt 2.56
 * occasionally fails ("unexpected jvm signature V") when those patterns
 * appear on `@Inject`-annotated classes.
 */
@Singleton
class CrisisOSFirebase @Inject constructor() {

    fun ensureInitialized(context: Context) {
        if (FirebaseApp.getApps(context).isEmpty()) {
            FirebaseApp.initializeApp(context)
        }
    }

    fun logEvent(name: String) {
        FirebaseAnalytics.getInstance(appContextOrThrow()).logEvent(name, Bundle())
    }

    fun logEvent(name: String, params: Map<String, String>) {
        val bundle = Bundle()
        for ((k, v) in params) bundle.putString(k, v)
        FirebaseAnalytics.getInstance(appContextOrThrow()).logEvent(name, bundle)
    }

    private fun appContextOrThrow(): Context {
        val app = FirebaseApp.getInstance()
        return app.applicationContext
    }
}
