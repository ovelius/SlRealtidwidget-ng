package se.locutus.sl.realtidhem.activity

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.Menu
import android.view.View
import android.view.WindowManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout
import com.google.protobuf.InvalidProtocolBufferException
import kotlinx.android.synthetic.main.widget_configure_activty.*
import se.locutus.proto.Ng.StopConfiguration
import se.locutus.proto.Ng.WidgetConfiguration
import se.locutus.sl.realtidhem.R
import se.locutus.sl.realtidhem.activity.add_stop.*
import se.locutus.sl.realtidhem.events.EXTRA_COLOR_THEME
import se.locutus.sl.realtidhem.events.EXTRA_RECONFIGURE_WIDGET
import se.locutus.sl.realtidhem.service.TimeTracker
import se.locutus.sl.realtidhem.widget.*
import java.lang.IllegalArgumentException
import java.util.*
import java.util.logging.Logger

const val ADD_STOP_REQUEST_CODE: Int = 1
const val MODIFY_STOP_REQUEST_CODE: Int = 2
const val ADD_WIDGET_REQUEST_CODE: Int = 3
const val LOCATION_ACCESS_REQUEST_CODE = 99
const val STOP_CONFIG_DATA_KEY = "stop_config_data_key"
const val ALL_DEPARTURES_DATA_KEY = "all_departures_data_key"
const val STOP_INDEX_DATA_KEY = "stop_config_index_data_key"
const val WIDGET_CONFIG_PREFS = "widget_configs"
const val WIDGET_CONFIG_DATA_KEY = "widget_config_data_key"

fun setColor(activity : AppCompatActivity, tabLayout : TabLayout?, color : Int) {
    val drawable = ColorDrawable(color)
    activity.supportActionBar!!.setBackgroundDrawable(drawable)
    val window = activity.window
    window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
    window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
    window.statusBarColor = color
    if(tabLayout != null) {
        tabLayout.background = drawable
    }
}

class WidgetConfigureActivity : AppCompatActivity() {
    companion object {
        val LOG = Logger.getLogger(WidgetConfigureActivity::class.java.name)
    }
    private var mAppWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private lateinit var mWidgetPrefs : SharedPreferences
    private lateinit var stopListFragment : StopListFragment
    private lateinit var updateModeFragment : UpdateModeFragment
    private lateinit var viewSwitcher : ViewSwitcher
    internal lateinit var tabLayout: TabLayout
    internal lateinit var viewPager: androidx.viewpager.widget.ViewPager
    private lateinit var widgetConfigureTabAdapter: WidgetConfigureTabAdapter
    internal var widgetConfig : WidgetConfiguration = WidgetConfiguration.getDefaultInstance()
    internal var color : Int? = null
    private lateinit var timeTracker : TimeTracker
    lateinit var adapter : ArrayAdapter<String>
    private var isNewWidget = false

    private fun showWidgetDialog() {
        for (widgetId in getAllWidgetIds(this)) {
            sendWidgetUpdateBroadcast(this, widgetId, widgetConfig)
        }
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

    public override fun onCreate(bundle: Bundle?) {
        super.onCreate(bundle)
        setResult(Activity.RESULT_CANCELED)
        setContentView(R.layout.widget_configure_activty)
        setSupportActionBar(config_toolbar)
        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, ArrayList<String>())
        viewSwitcher = findViewById(R.id.viewSwitcher1)
        findViewById<Button>(R.id.ok_btn_about).setOnClickListener {
            viewSwitcher.showPrevious()
        }
        findViewById<Button>(R.id.github_btn).setOnClickListener {
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/ovelius/SlRealtidwidget-ng"))
            startActivity(browserIntent)
        }
        timeTracker = TimeTracker(this)
        if (intent?.hasExtra(AppWidgetManager.EXTRA_APPWIDGET_ID) == true) {
            mAppWidgetId = intent!!.extras!!.getInt(
                AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID
            )
        }
        mWidgetPrefs = getSharedPreferences(WIDGET_CONFIG_PREFS, 0)

        tabLayout = findViewById(R.id.tab_layout)
        viewPager = findViewById(R.id.view_pager)
        stopListFragment = (supportFragmentManager.findFragmentByTag(fragmentName(0))
                ?: StopListFragment()) as StopListFragment
        updateModeFragment = (supportFragmentManager.findFragmentByTag(fragmentName(1))
                ?: UpdateModeFragment()) as UpdateModeFragment
        // Create or recycle fragments.
        widgetConfigureTabAdapter = WidgetConfigureTabAdapter(
            this,
            stopListFragment,
            updateModeFragment,
            supportFragmentManager
        )
        viewPager.adapter = widgetConfigureTabAdapter
        tabLayout.setupWithViewPager(viewPager)


        if (mAppWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            showWidgetDialog()
            return
        }

        // Use the color extra to determine if this is a new widget.
        if (intent.hasExtra(EXTRA_COLOR_THEME)) {
            color = intent.getIntExtra(EXTRA_COLOR_THEME, 0)
           setColor(this, tabLayout, color!!)
        }
        isNewWidget = !intent.getBooleanExtra(EXTRA_RECONFIGURE_WIDGET, false)
        supportActionBar!!.setDisplayHomeAsUpEnabled(true)
        supportActionBar!!.setDisplayShowHomeEnabled(true)

        if (bundle?.containsKey(WIDGET_CONFIG_DATA_KEY) == true) {
            widgetConfig = WidgetConfiguration.parseFrom(bundle.getByteArray(WIDGET_CONFIG_DATA_KEY))
            LOG.info("Loaded config from bundle $widgetConfig")
        } else {
            widgetConfig = loadWidgetConfigOrDefault(mWidgetPrefs, mAppWidgetId)
            LOG.info("Loaded config from prefs $widgetConfig")
        }

        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_COARSE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {

            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.ACCESS_COARSE_LOCATION)) {
                Snackbar.make(viewPager, getString(R.string.location_access_rationale), Snackbar.LENGTH_LONG)
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

    fun getTimeRecords() : ArrayList<TimeTracker.TimeRecord> {
        return timeTracker.getRecords(mAppWidgetId, 0)
    }

    override fun onSaveInstanceState(outState: Bundle?) {
        super.onSaveInstanceState(outState)
        LOG.info("onSaveInstanceState")
        outState?.putByteArray(WIDGET_CONFIG_DATA_KEY, widgetConfig.toByteArray())
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        LOG.info("Activity result with request code $requestCode resultCode $resultCode and data $data")
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
            stopListFragment.mStopListAdapter.update(widgetConfig)
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
        menu.findItem(R.id.export_btn).isVisible = widgetConfig.stopConfigurationCount != 0
        menu.findItem(R.id.import_btn).isVisible = widgetConfig.stopConfigurationCount == 0
        menu.findItem(R.id.about_btn).setOnMenuItemClickListener {_ ->
            setTextVersion(findViewById(R.id.version_text))
            viewSwitcher.showNext()
            true
        }
        menu.findItem(R.id.import_btn).setOnMenuItemClickListener { _ ->
            createConfigInputDialog()
            true
        }
        menu.findItem(R.id.export_btn).setOnMenuItemClickListener { _ ->
            val configString = "SL realtime widget config:${widgetConfigToString(widgetConfig)}:"
            val sharingIntent = Intent(android.content.Intent.ACTION_SEND)
            sharingIntent.type = "text/plain"
            sharingIntent.putExtra(android.content.Intent.EXTRA_SUBJECT, "SL realtime widget config")
            sharingIntent.putExtra(android.content.Intent.EXTRA_TEXT, configString)
            startActivity(Intent.createChooser(sharingIntent, resources.getString(R.string.share_using)))
            true
        }
        return true
    }

    override fun onBackPressed() {
        if (getConfigErrorMessage() == null) {
            finishSuccessFully()
        } else {
            super.onBackPressed()
        }
    }

    private fun finishSuccessFully() {
        LOG.info("Storing config for $mAppWidgetId")
        storeWidgetConfig(mWidgetPrefs, widgetConfig)
        sendWidgetUpdateBroadcast(this, mAppWidgetId, widgetConfig)
        finishOk()
    }

    override fun onSupportNavigateUp(): Boolean {
        val message = getConfigErrorMessage()
        if (message != null) {
            Snackbar.make(viewPager, message, Snackbar.LENGTH_SHORT)
                .setAction("Action", null).show()
            return false
        } else {
            finishSuccessFully()
            return true
        }
    }

    private fun createConfigInputDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle(R.string.import_settings)
        val input = EditText(this)
        input.minLines = 5
        input.setLines(5)
        input.inputType = InputType.TYPE_TEXT_FLAG_MULTI_LINE
        builder.setView(input)
        builder.setPositiveButton(R.string.import_settings_btn) { _, _ ->
            val settings = input.text.toString()
            try {
                widgetConfig = fromUserInputString(settings, mAppWidgetId)
                Snackbar.make(viewPager, getString(R.string.loaded_config, widgetConfig.stopConfigurationCount),
                    Snackbar.LENGTH_LONG).show()
                stopListFragment.maybeShowStopListView()
                stopListFragment.mStopListAdapter.notifyDataSetChanged()
            } catch (e : IllegalArgumentException) {
                LOG.warning("Invalid config string $settings")
                Snackbar.make(viewPager, getString(R.string.failed_config), Snackbar.LENGTH_LONG).show()
            } catch (e : InvalidProtocolBufferException) {
                LOG.warning("Invalid config string $settings")
                Snackbar.make(viewPager, getString(R.string.failed_config), Snackbar.LENGTH_LONG).show()
            }
        }
        builder.setNegativeButton(R.string.import_settings_btn_cancel) { dialog, p1 ->
            dialog.cancel()
        }
        builder.show()
    }


    private fun getConfigErrorMessage() : Int? {
        if (widgetConfig.stopConfigurationCount <= 0) {
            return R.string.missing_configuration
        }
        return null
    }
}

