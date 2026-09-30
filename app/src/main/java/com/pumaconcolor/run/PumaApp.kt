package com.pumaconcolor.run

import android.app.Application
import com.pumaconcolor.run.service.RunNotification
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class PumaApp : Application() {
    override fun onCreate() {
        super.onCreate()
        RunNotification.createChannel(this)
    }
}
