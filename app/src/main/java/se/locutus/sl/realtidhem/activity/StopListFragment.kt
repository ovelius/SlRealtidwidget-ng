package se.locutus.sl.realtidhem.activity

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ListView
import android.widget.TextView
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import se.locutus.sl.realtidhem.R
import se.locutus.sl.realtidhem.activity.add_stop.AddStopActivity
import se.locutus.sl.realtidhem.events.EXTRA_COLOR_THEME

class StopListFragment : androidx.fragment.app.Fragment() {

    private lateinit var widgetConfigureActivity : WidgetConfigureActivity
    private lateinit var mListView : ListView
    internal lateinit var mStopListAdapter: StopListAdapter
    private lateinit var add_stop_button : View
    lateinit var saveConfigButton : ExtendedFloatingActionButton
    private lateinit var mAddStopHelperText : TextView

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val mainView =  inflater.inflate(R.layout.content_stop_list, container, false)
        widgetConfigureActivity = activity as WidgetConfigureActivity
        mListView = mainView.findViewById(R.id.stop_list_view)
        mStopListAdapter = StopListAdapter(widgetConfigureActivity)
        mListView.adapter = mStopListAdapter
        mAddStopHelperText =  mainView.findViewById(R.id.no_stops_help_text)
        mListView.setOnItemClickListener { _, _, position, _ ->
            val intent = Intent(activity, AddStopActivity::class.java).apply {
                putExtra(STOP_CONFIG_DATA_KEY, widgetConfigureActivity.widgetConfig.getStopConfiguration(position).toByteArray())
                putExtra(STOP_INDEX_DATA_KEY, position)
                if (widgetConfigureActivity.color != null) {
                    putExtra(EXTRA_COLOR_THEME, widgetConfigureActivity.color!!)
                }
            }
            widgetConfigureActivity.startActivityForResult(intent, MODIFY_STOP_REQUEST_CODE)
        }
        add_stop_button = mainView.findViewById(R.id.add_stop_button)
        add_stop_button.setOnClickListener { _ ->
            val addIntent = Intent(activity, AddStopActivity::class.java).apply {
                if (widgetConfigureActivity.color != null) {
                   putExtra(EXTRA_COLOR_THEME, widgetConfigureActivity.color!!)
                }
            }
            widgetConfigureActivity.startActivityForResult(addIntent, ADD_STOP_REQUEST_CODE)
        }
        saveConfigButton = mainView.findViewById(R.id.save_widget_config)
        saveConfigButton.setOnClickListener {
            widgetConfigureActivity.finishSuccessFully()
        }
        saveConfigButton.backgroundTintList =
                ColorStateList.valueOf(widgetConfigureActivity.window.statusBarColor)
        if (widgetConfigureActivity.color != null && widgetConfigureActivity.color != 0) {
            add_stop_button.backgroundTintList= ColorStateList.valueOf(widgetConfigureActivity.color!!)
        }
        return mainView
    }

    override fun onResume() {
        super.onResume()
        maybeShowStopListView()
    }

    fun maybeShowStopListView() {
        if (widgetConfigureActivity.widgetConfig.stopConfigurationCount == 0) {
            mAddStopHelperText.visibility = View.VISIBLE
            mListView.visibility = View.GONE
        } else {
            mListView.visibility = View.VISIBLE
            mAddStopHelperText.visibility = View.GONE
        }
    }

}