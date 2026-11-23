package com.example

import android.app.Application
import android.content.Context
import androidx.annotation.StringRes

/**
 * Application class — inicializa o resolvedor global de strings.
 */
class VyApp : Application() {
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        Res.appContext = base
    }
}

/**
 * Acesso global a string resources fora do escopo Composable.
 * Initialized in [VyApp.attachBaseContext], before any app code.
 */
object Res {
    @Volatile
    lateinit var appContext: Context
        internal set

    fun get(@StringRes id: Int, vararg args: Any?): String {
        val c = appContext
        return if (args.isEmpty()) c.getString(id) else c.getString(id, *args)
    }
}
