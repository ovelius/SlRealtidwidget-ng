package se.locutus.sl.realtidhem.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.util.Base64
import android.view.Menu
import android.widget.ListView
import kotlinx.android.synthetic.main.widget_configure_activty.*
import se.locutus.proto.Ng.WidgetConfiguration
import se.locutus.proto.Ng.StopConfiguration
import se.locutus.sl.realtidhem.R
import java.util.logging.Logger

const val ADD_STOP_REQUEST_CODE: Int = 1
const val STOP_CONFIG_DATA_KEY = "stop_config_data_key"
const val WIDGET_CONFIG_PREFS = "widget_configs"

fun widgetKey(widgetId : Int) : String {
    return "widget_$widgetId"
}

fun loadWidgetConfigOrDefault(prefs : SharedPreferences, widgetId : Int) : WidgetConfiguration {
    val widgetKey = widgetKey(widgetId)
    if (prefs.contains(widgetKey)) {
        val bytes = Base64.decode(prefs.getString(widgetKey, ""), 0)
        return WidgetConfiguration.parseFrom(bytes)
    }
    return WidgetConfiguration.newBuilder().setWidgetId(widgetId.toLong()).build()
}
fun deleteWidget(prefs : SharedPreferences, widgetId : Int) {
    val widgetKey = widgetKey(widgetId.toInt())
    val edit = prefs.edit()
    edit.remove(widgetKey).apply()
}

fun storeWidgetConfig(prefs : SharedPreferences, config : WidgetConfiguration) {
    val widgetKey = widgetKey(config.widgetId.toInt())
    val edit = prefs.edit()
    val data = Base64.encodeToString(config.toByteArray(), 0)
    edit.putString(widgetKey, data).apply()
}

class WidgetConfigureActivity : AppCompatActivity() {
    companion object {
        val LOG = Logger.getLogger(WidgetConfigureActivity::class.java.name)
    }
    internal var mAppWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    internal lateinit var mWidgetPrefs : SharedPreferences
    internal lateinit var mListView : ListView
    internal lateinit var mStopListAdapter : StopListAdapter
    internal lateinit var widgetConfig : WidgetConfiguration

    public override fun onCreate(icicle: Bundle?) {
        super.onCreate(icicle)
        setResult(Activity.RESULT_CANCELED)
        setContentView(R.layout.widget_configure_activty)
        setSupportActionBar(config_toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        mListView = findViewById(R.id.stop_list_view)
        if (intent.extras != null) {
            mAppWidgetId = intent.extras.getInt(
                AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID
            )
        }

        if (mAppWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {

            // https://developer.android.com/reference/android/appwidget/AppWidgetManager#isRequestPinAppWidgetSupported() ?
            /*
            val pickIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_PICK)
            pickIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetID)
            startActivityForResult(pickIntent, KEY_CODE)
            */
            finish()
            return
        }
        mWidgetPrefs = getSharedPreferences(WIDGET_CONFIG_PREFS, 0)
        widgetConfig = loadWidgetConfigOrDefault(mWidgetPrefs, mAppWidgetId)
        mStopListAdapter = StopListAdapter(this, widgetConfig)
        mListView.adapter = mStopListAdapter

        add_stop_button.setOnClickListener { view ->
            startActivityForResult(Intent(this, AddStopActivity::class.java), ADD_STOP_REQUEST_CODE)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode != ADD_STOP_REQUEST_CODE || resultCode != Activity.RESULT_OK) {
            LOG.severe("Got activity result with unexpected requestCode $requestCode and resultCode $resultCode")
            finish()
        }
        if (data?.hasExtra(STOP_CONFIG_DATA_KEY) != true) {
            finish()
        }
        val config = StopConfiguration.parseFrom(data!!.getByteArrayExtra(STOP_CONFIG_DATA_KEY))
        LOG.info("Got StopConfiguration $config")
        widgetConfig = widgetConfig.toBuilder().addStopConfiguration(config).build()
        mStopListAdapter.update(widgetConfig)
    }

    fun finishOk() {
        LOG.info("Finishing with config $widgetConfig")
        val resultValue = Intent()
        resultValue.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, mAppWidgetId)
        setResult(Activity.RESULT_OK, resultValue)
        finish()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.widget_config_action_bar_menu, menu)
        menu.findItem(R.id.save_widget_action).setOnMenuItemClickListener {item ->
            storeWidgetConfig(mWidgetPrefs, widgetConfig)
            finishOk()
            true
        }
        return true
    }
}

