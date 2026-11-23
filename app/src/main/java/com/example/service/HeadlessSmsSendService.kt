package com.example.service

import android.app.Service
import android.content.Intent
import android.os.IBinder

// COM-R06
class HeadlessSmsSendService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null
}
