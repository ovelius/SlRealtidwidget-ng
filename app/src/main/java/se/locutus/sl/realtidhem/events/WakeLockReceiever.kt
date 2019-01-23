package se.locutus.sl.realtidhem.events

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import se.locutus.sl.realtidhem.service.BackgroundUpdaterService
import java.util.logging.Logger

class WakeLockReceiever(val service : BackgroundUpdaterService) : BroadcastReceiver() {
    companion object {
        val LOG = Logger.getLogger(WakeLockReceiever::class.java.name)
    }
    override fun onReceive(context: Context, intent: Intent) {
        val km = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        if (Intent.ACTION_SCREEN_ON == intent.action) {
            if (km.isKeyguardLocked) {
                LOG.info("Intent received ${intent.action} but screen still locked")
                return
            }
        }
        if (Intent.ACTION_SCREEN_OFF == intent.action) {
            service.stopAutoUpdateSequence()
        } else {
            service.startAutoUpdateSequence()
        }
        LOG.info("Intent received $intent")
    }
}
