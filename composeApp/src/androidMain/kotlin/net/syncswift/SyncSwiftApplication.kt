package net.syncswift

import android.app.Application

class SyncSwiftApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        SessionManager.init(this)
        net.syncswift.discovery.DeviceDiscoveryManager.init(this)
        net.syncswift.transfer.TransferEngine.init(this)
        net.syncswift.fcm.FcmSyncManager.init(this)
    }
}
