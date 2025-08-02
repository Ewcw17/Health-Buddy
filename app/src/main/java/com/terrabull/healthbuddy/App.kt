package com.terrabull.healthbuddy
import android.app.Application

class App: Application() {
    override fun onCreate(){
        super.onCreate()
        createNotificationChannel()
    }

    private fun createNotificationChannel(){
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                
            )
        }
    }
}