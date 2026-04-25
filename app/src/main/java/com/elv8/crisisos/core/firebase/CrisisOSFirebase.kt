package com.elv8.crisisos.core.firebase

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.ktx.Firebase
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thin Firebase facade. Initialization happens automatically through the
 * `google-services` Gradle plugin + the `FirebaseInitProvider` ContentProvider
 * baked into `firebase-common`. We only expose [analytics] so the app can log
 * lifecycle events without each caller pulling Firebase APIs directly.
 *
 * No mocks here — the live `google-services.json` for `com.elv8.crisisos`
 * (project `zenith-devs`) is shipped in `app/google-services.json`.
 */
@Singleton
class CrisisOSFirebase @Inject constructor() {

    val analytics: FirebaseAnalytics by lazy { Firebase.analytics }

    fun ensureInitialized(context: Context) {
        // Firebase auto-inits via ContentProvider; this is just a safety call
        // for unit-test / instrumentation contexts where the provider didn't run.
        if (FirebaseApp.getApps(context).isEmpty()) {
            FirebaseApp.initializeApp(context)
        }
    }

    fun logEvent(name: String, params: Map<String, String> = emptyMap()) {
        val bundle = android.os.Bundle().apply {
            params.forEach { (k, v) -> putString(k, v) }
        }
        analytics.logEvent(name, bundle)
    }
}
