package se.locutus.sl.realtidhem.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.util.logging.Logger

internal class WakeLockReceiever(val service : BackgroundUpdaterService) : BroadcastReceiver() {
    companion object {
        val LOG = Logger.getLogger(WakeLockReceiever::class.java.name)
    }
    override fun onReceive(context: Context, intent: Intent) {
        if (Intent.ACTION_SCREEN_OFF == intent.action) {
            service.stopAutoUpdateSequence()
        } else if (Intent.ACTION_SCREEN_ON == intent.action) {
            service.startAutoUpdateSequence(true)
        }
        LOG.info("Intent received $intent")
    }
}
