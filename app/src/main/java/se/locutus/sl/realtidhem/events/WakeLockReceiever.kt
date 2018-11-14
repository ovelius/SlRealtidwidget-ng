package se.locutus.sl.realtidhem.events

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.util.logging.Logger

class WakeLockReceiever : BroadcastReceiver() {
    companion object {
        val LOG = Logger.getLogger(WakeLockReceiever::class.java.name)
    }
    override fun onReceive(context: Context, intent: Intent) {
        LOG.warning("Intent received $intent")
    }
}
