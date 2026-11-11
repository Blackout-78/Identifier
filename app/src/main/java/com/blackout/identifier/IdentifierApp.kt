package com.blackout.identifier

import android.app.Application
import com.blackout.identifier.data.Prefs

class IdentifierApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
    }
}
