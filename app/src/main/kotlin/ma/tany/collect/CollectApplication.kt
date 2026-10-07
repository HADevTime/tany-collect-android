package ma.tany.collect

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import ma.tany.collect.core.push.PushNotifications

@HiltAndroidApp
class CollectApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Push channels exist before the first push (FCM uses the backend's channel_id even before the app ever ran).
        PushNotifications.ensureChannels(this)
    }
}
