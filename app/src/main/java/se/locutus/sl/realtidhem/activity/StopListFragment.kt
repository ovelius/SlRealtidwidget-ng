package se.locutus.sl.realtidhem.activity

import android.app.AlertDialog
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import se.locutus.proto.Ng
import se.locutus.sl.realtidhem.R
import se.locutus.sl.realtidhem.activity.add_stop.AddStopActivity
import se.locutus.sl.realtidhem.events.EXTRA_COLOR_THEME

const val QUICK_TOGGLE_HINT_SHOWN = "quick_toggle_hint"
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
            if (widgetConfigureActivity.widgetConfig.stopConfigurationCount > 1) {
                maybeShowStopSwitcherDialog(widgetConfigureActivity.widgetConfig)
            }
        }
    }

    fun maybeShowStopSwitcherDialog(config : Ng.WidgetConfiguration) {
        val prefs = widgetConfigureActivity.mWidgetPrefs
        if (prefs.getBoolean(QUICK_TOGGLE_HINT_SHOWN, false)) {
          return
        }
        check(config.stopConfigurationCount > 1) { "Must have at least two stops! "}
        val builder = AlertDialog.Builder(widgetConfigureActivity)
        val inflater = requireActivity().layoutInflater;
        val view = inflater.inflate(R.layout.dialog_stop_toggle, null)
        val mainText = view.findViewById<TextView>(R.id.widgettag)
        val line1Text = view.findViewById<TextView>(R.id.widgetline1)
        val minText = view.findViewById<TextView>(R.id.widgetmin)
        val mainViewColor = view.findViewById<View>(R.id.widgetcolor)

        val stop1 = config.stopConfigurationList[0].stopData.displayName
        val stop2 = config.stopConfigurationList[1].stopData.displayName
        mainText.text = stop1
        view.findViewById<TextView>(R.id.widgetline2).text = ""
        line1Text.text = getString(R.string.sample_departure, stop2)
        minText.setText(R.string.sample_minutes)

        builder.setTitle(R.string.multiple_stops)
        builder.setView(view)
        val dialog = builder.create()
        val dismissClick : (View) -> Unit = { view ->
            mainText.text = stop2
            line1Text.text = getString(R.string.sample_departure, stop1)
            minText.setText(R.string.sample_minutes2)
            mainViewColor.background = ColorDrawable(Color.GREEN)
            Toast.makeText(widgetConfigureActivity, R.string.quick_toggle_toast, Toast.LENGTH_SHORT).show()
            prefs.edit().putBoolean(QUICK_TOGGLE_HINT_SHOWN, true).apply()
            Handler(Looper.getMainLooper()).postDelayed({
                dialog.dismiss()
            }, 2500)
        }
        view.findViewById<ImageView>(R.id.larrow).setOnClickListener(dismissClick)
        view.findViewById<ImageView>(R.id.rarrow).setOnClickListener(dismissClick)
        dialog.show()
    }

}