package com.photocleanup

import android.app.Application
import com.photocleanup.utils.PreferencesManager

class PhotoCleanupApp : Application() {

    lateinit var preferencesManager: PreferencesManager
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        preferencesManager = PreferencesManager(this)
    }

    companion object {
        lateinit var instance: PhotoCleanupApp
            private set
    }
}
