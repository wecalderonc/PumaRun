package com.pumarun.app

import android.app.Application
import com.pumarun.app.service.RunNotification
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class PumaApp : Application() {
    override fun onCreate() {
        super.onCreate()
        RunNotification.createChannel(this)
    }
}
