package se.locutus.sl.realtidhem.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.ListView
import kotlinx.android.synthetic.main.widget_configure_activty.*
import se.locutus.proto.Ng
import se.locutus.sl.realtidhem.R
import java.util.logging.Logger

const val ADD_STOP_REQUEST_CODE: Int = 1
const val STOP_CONFIG_DATA_KEY = "stop_config_data_key"

class WidgetConfigureActivity : Activity() {
    companion object {
        val LOG = Logger.getLogger(WidgetConfigureActivity::class.java.name)
    }
    internal var mAppWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    internal lateinit var mListView : ListView
    internal lateinit var mAdapter: ArrayAdapter<String>

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode != ADD_STOP_REQUEST_CODE || resultCode != Activity.RESULT_OK) {
            LOG.severe("Got activity result with unexpected requestCode $requestCode and resultCode $resultCode")
            finish()
        }
        if (data?.hasExtra(STOP_CONFIG_DATA_KEY) != true) {
            finish()
        }
        val config = Ng.StopConfiguration.parseFrom(data!!.getByteArrayExtra(STOP_CONFIG_DATA_KEY))
        LOG.info("Got StopConfiguration $config")
        finishOk()
    }


    fun finishOk() {
        val resultValue = Intent()
        resultValue.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, mAppWidgetId)
        setResult(Activity.RESULT_OK, resultValue)
        finish()
    }

    public override fun onCreate(icicle: Bundle?) {
        super.onCreate(icicle)
        setResult(Activity.RESULT_CANCELED)
        setContentView(R.layout.widget_configure_activty)

        mListView = findViewById(R.id.stop_list_view)
        val toViews = intArrayOf(android.R.id.text1)
        mAdapter = ArrayAdapter(this,
            android.R.layout.simple_list_item_1,     arrayOf("kista", "hello", "anka"))
        mListView.adapter = mAdapter

        if (intent.extras != null) {
            mAppWidgetId = intent.extras.getInt(
                AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID
            )
        }

        if (mAppWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        add_stop_button.setOnClickListener { view ->
            startActivityForResult(Intent(this, AddStopActivity::class.java), ADD_STOP_REQUEST_CODE)
        }
    }


}

