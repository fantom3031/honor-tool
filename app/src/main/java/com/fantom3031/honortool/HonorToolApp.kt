package com.fantom3031.honortool

import android.app.Application
import com.fantom3031.honortool.core.ServiceBridge

class HonorToolApp : Application() {

    override fun onCreate() {
        super.onCreate()
        ServiceBridge.init(this)
    }
}
