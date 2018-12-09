package se.locutus.sl.realtidhem.activity

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import android.widget.ViewSwitcher
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout
import kotlinx.android.synthetic.main.widget_configure_activty.*
import se.locutus.proto.Ng.StopConfiguration
import se.locutus.proto.Ng.WidgetConfiguration
import se.locutus.sl.realtidhem.R
import se.locutus.sl.realtidhem.events.EXTRA_COLOR_THEME
import se.locutus.sl.realtidhem.events.WIDGET_CONFIG_UPDATED
import se.locutus.sl.realtidhem.events.WidgetBroadcastReceiver
import se.locutus.sl.realtidhem.widget.loadWidgetConfigOrDefault
import se.locutus.sl.realtidhem.widget.storeWidgetConfig
import java.util.*
import java.util.logging.Logger

const val ADD_STOP_REQUEST_CODE: Int = 1
const val MODIFY_STOP_REQUEST_CODE: Int = 2
const val ADD_WIDGET_REQUEST_CODE: Int = 3
const val LOCATION_ACCESS_REQUEST_CODE = 99
const val STOP_CONFIG_DATA_KEY = "stop_config_data_key"
const val STOP_INDEX_DATA_KEY = "stop_config_index_data_key"
const val WIDGET_CONFIG_PREFS = "widget_configs"

fun setColor(activity : AppCompatActivity, tabLayout : TabLayout?, color : Int) {
    val drawable = ColorDrawable(color!!)
    activity.supportActionBar!!.setBackgroundDrawable(drawable)
    val window = activity.window
    window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
    window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
    window.statusBarColor = color!!
    if(tabLayout != null) {
        tabLayout.background = drawable
    }
}

class WidgetConfigureActivity : AppCompatActivity() {
    companion object {
        val LOG = Logger.getLogger(WidgetConfigureActivity::class.java.name)
    }
    internal var mAppWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    internal lateinit var mWidgetPrefs : SharedPreferences
    internal lateinit var mListView : ListView
    internal lateinit var mAddStopHelperText : TextView
    internal lateinit var mStopListAdapter: StopListAdapter
    internal lateinit var viewSwitcher : ViewSwitcher
    internal var widgetConfig : WidgetConfiguration = WidgetConfiguration.getDefaultInstance()
    internal var color : Int? = null

    public override fun onResume() {
        super.onResume()
        if (widgetConfig.stopConfigurationCount == 0) {
            mAddStopHelperText.visibility = View.VISIBLE
            mListView.visibility = View.GONE
        } else {
            mListView.visibility = View.VISIBLE
            mAddStopHelperText.visibility = View.GONE
        }
    }

    private fun showWidgetDialog() {
        val url = "https://support.google.com/android/answer/2781850?hl=${Locale.getDefault().language}"
        val builder = AlertDialog.Builder(this)
        builder.setMessage(R.string.widget_help)
            .setNegativeButton(R.string.ok)
                { _, _ -> finish() }
            .setPositiveButton(R.string.widget_read_more) { _, _ ->
                    val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    startActivity(browserIntent)
                    finish()
                }
        builder.create().show()
    }

    public override fun onCreate(icicle: Bundle?) {
        super.onCreate(icicle)
        setResult(Activity.RESULT_CANCELED)
        setContentView(R.layout.widget_configure_activty)
        setSupportActionBar(config_toolbar)
        mListView = findViewById(R.id.stop_list_view)
        viewSwitcher = findViewById(R.id.viewSwitcher1)

        findViewById<Button>(R.id.ok_btn_about).setOnClickListener {
            viewSwitcher.showPrevious()
        }
        findViewById<Button>(R.id.github_btn).setOnClickListener {
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/ovelius/SlRealtidwidget-ng"))
            startActivity(browserIntent)
        }

        mAddStopHelperText = findViewById(R.id.no_stops_help_text)
        if (intent.extras != null) {
            mAppWidgetId = intent.extras.getInt(
                AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID
            )
        }

        if (mAppWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            showWidgetDialog()
            return
        }

        if (intent.hasExtra(EXTRA_COLOR_THEME)) {
            color = intent.getIntExtra(EXTRA_COLOR_THEME, 0)
           setColor(this, null, color!!)
        }

        mWidgetPrefs = getSharedPreferences(WIDGET_CONFIG_PREFS, 0)
        widgetConfig = loadWidgetConfigOrDefault(mWidgetPrefs, mAppWidgetId)
        mStopListAdapter = StopListAdapter(this)
        mListView.adapter = mStopListAdapter
        mListView.setOnItemClickListener { _, _, position, _ ->
            val intent = Intent(this, AddStopActivity::class.java).apply {
                putExtra(STOP_CONFIG_DATA_KEY, widgetConfig.getStopConfiguration(position).toByteArray())
                putExtra(STOP_INDEX_DATA_KEY, position)
                if (color != null) {
                    putExtra(EXTRA_COLOR_THEME, color!!)
                }
            }
            startActivityForResult(intent, MODIFY_STOP_REQUEST_CODE)
        }

        add_stop_button.setOnClickListener { _ ->
            val addIntent = Intent(this, AddStopActivity::class.java).apply {
                if (color != null) {
                    putExtra(EXTRA_COLOR_THEME, color!!)
                }
            }
            startActivityForResult(addIntent, ADD_STOP_REQUEST_CODE)
        }

        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_COARSE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {

            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.ACCESS_COARSE_LOCATION)) {
                Snackbar.make(mListView, getString(R.string.location_access_rationale), Snackbar.LENGTH_LONG)
                    .setAction(R.string.location_access_give) {
                        ActivityCompat.requestPermissions(this,
                            arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION),
                            LOCATION_ACCESS_REQUEST_CODE
                        )
                    }.show()
            } else {
                ActivityCompat.requestPermissions(this,
                    arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION),
                    LOCATION_ACCESS_REQUEST_CODE
                )
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        LOG.info("Activity result with request code $requestCode and data $data")
        if (requestCode == ADD_WIDGET_REQUEST_CODE) {
            mAppWidgetId = data!!.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            LOG.info("Found add widget callback with id $mAppWidgetId")
            if (mAppWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
                LOG.severe("Got activity result with unexpected appWidgetId $mAppWidgetId")
                finish()
            } else {
                // Continue as normal.
                widgetConfig = loadWidgetConfigOrDefault(mWidgetPrefs, mAppWidgetId)
                return
            }
        }
        if (widgetConfig.stopConfigurationCount <= 0) {
            if (requestCode != ADD_STOP_REQUEST_CODE || resultCode != Activity.RESULT_OK) {
                LOG.severe("Got activity result with unexpected requestCode $requestCode and resultCode $resultCode")
                finish()
            }
            if (data?.hasExtra(STOP_CONFIG_DATA_KEY) != true) {
                finish()
                return
            }
        }
        if (data?.hasExtra(STOP_CONFIG_DATA_KEY) == true) {
            val config = StopConfiguration.parseFrom(data.getByteArrayExtra(STOP_CONFIG_DATA_KEY))
            LOG.info("Got StopConfiguration $config")

            if (data.hasExtra(STOP_INDEX_DATA_KEY)) {
                val index = data.getIntExtra(STOP_INDEX_DATA_KEY, 0)
                widgetConfig = widgetConfig.toBuilder().setStopConfiguration(index, config).build()
            } else {
                widgetConfig = widgetConfig.toBuilder().addStopConfiguration(config).build()
            }
            mStopListAdapter.update(widgetConfig)
        }
    }

    private fun finishOk() {
        LOG.info("Finishing with config $widgetConfig")
        val resultValue = Intent()
        resultValue.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, mAppWidgetId)
        setResult(Activity.RESULT_OK, resultValue)
        finish()
    }

    private fun setTextVersion(tv : TextView) {
        val pInfo = packageManager.getPackageInfo(packageName, 0)
        val versionText = "Version ${pInfo.versionName} build ${pInfo.versionCode}"
        tv.setText(versionText, TextView.BufferType.NORMAL)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.widget_config_action_bar_menu, menu)
        menu.findItem(R.id.about_btn).setOnMenuItemClickListener {_ ->
            setTextVersion(findViewById(R.id.version_text))
            viewSwitcher.showNext()
            true
        }
        menu.findItem(R.id.save_widget_action).setOnMenuItemClickListener {_ ->
            val intentUpdate = Intent(this, WidgetBroadcastReceiver::class.java).apply {
                action = WIDGET_CONFIG_UPDATED
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, mAppWidgetId)
            }
            val message = getConfigErrorMessage()
            if (message != null) {
                Snackbar.make(mListView, message, Snackbar.LENGTH_SHORT)
                    .setAction("Action", null).show()
            } else {
                LOG.info("Storing config for $mAppWidgetId")
                storeWidgetConfig(mWidgetPrefs, widgetConfig)
                sendBroadcast(intentUpdate)
                finishOk()
            }
            true
        }
        return true
    }

    private fun getConfigErrorMessage() : Int? {
        if (widgetConfig.stopConfigurationCount <= 0) {
            return R.string.missing_configuration
        }
        return null
    }
}

