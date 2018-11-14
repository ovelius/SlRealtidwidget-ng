package se.locutus.sl.realtidhem

import android.content.Intent
import android.os.Bundle
import android.support.design.widget.Snackbar
import android.support.v7.app.AppCompatActivity;
import android.view.Menu
import android.view.MenuItem

import kotlinx.android.synthetic.main.activity_configuration_activty.*
import se.locutus.sl.realtidhem.R
import se.locutus.sl.realtidhem.service.BackgroundUpdaterService

class ConfigurationActivty : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_configuration_activty)
        setSupportActionBar(toolbar)

        fab_start.setOnClickListener { view ->
            Snackbar.make(view, "Starting service...", Snackbar.LENGTH_LONG)
                .setAction("Action", null).show()
            startService(Intent(this, BackgroundUpdaterService::class.java))
        }
        fab_stop.setOnClickListener { view ->
            Snackbar.make(view, "Stopping service...", Snackbar.LENGTH_LONG)
                .setAction("Action", null).show()
            stopService(Intent(this, BackgroundUpdaterService::class.java))
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.add_stop_action_bar_menu, menu)
        menu.findItem(R.id.save_stop_action).setOnMenuItemClickListener {item ->
            finish()
            true
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        return when (item.itemId) {
            R.id.action_settings -> true
            else -> super.onOptionsItemSelected(item)
        }
    }
}
