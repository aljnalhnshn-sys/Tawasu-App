package com.tawasul.app

import android.app.Application
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class TawasulApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        if (FirebaseApp.getApps(this).isEmpty()) {
            val options = FirebaseOptions.Builder()
                .setProjectId("stalwart-overview-sklwk")
                .setApplicationId("1:743734969863:web:dd14fa327de8024bab8ca2")
                .setApiKey("AIzaSyA5VrW46L80vg3v_3m2FeKFSQrlcIpUZEA")
                .setGcmSenderId("743734969863")
                .setStorageBucket("stalwart-overview-sklwk.firebasestorage.app")
                .build()
            FirebaseApp.initializeApp(this, options)
        }
    }
}
