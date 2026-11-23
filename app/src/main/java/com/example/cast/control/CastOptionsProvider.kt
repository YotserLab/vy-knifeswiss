package com.example.cast.control

import android.content.Context
import com.google.android.gms.cast.CastMediaControlIntent
import com.google.android.gms.cast.framework.CastOptions
import com.google.android.gms.cast.framework.OptionsProvider
import com.google.android.gms.cast.framework.SessionProvider

/**
 * Provides [CastOptions] to the Google Cast framework. The class is
 * instantiated by GMS itself — it is wired up via the
 * `com.google.android.gms.cast.framework.OPTIONS_PROVIDER_CLASS_NAME`
 * meta-data entry in AndroidManifest.xml and must keep a public no-arg
 * constructor.
 *
 * Uses the default media receiver so arbitrary URLs can be streamed
 * without registering a custom receiver app.
 */
class CastOptionsProvider : OptionsProvider {

    override fun getCastOptions(context: Context): CastOptions =
        CastOptions.Builder()
            .setReceiverApplicationId(
                CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID
            )
            .setStopReceiverApplicationWhenEndingSession(true)
            .build()

    override fun getAdditionalSessionProviders(context: Context): List<SessionProvider>? = null
}
