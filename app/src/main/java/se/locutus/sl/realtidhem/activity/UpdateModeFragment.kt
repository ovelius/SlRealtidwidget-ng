package se.locutus.sl.realtidhem.activity

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.Spinner
import se.locutus.proto.Ng
import se.locutus.sl.realtidhem.R
import se.locutus.sl.realtidhem.service.BackgroundUpdaterService
import java.util.logging.Logger

class UpdateModeFragment : androidx.fragment.app.Fragment() {
    companion object {
        val LOG = Logger.getLogger(UpdateModeFragment::class.java.name)
    }
    private lateinit var widgetConfigureActivity : WidgetConfigureActivity
    private lateinit var spinner : Spinner
    private lateinit var updatePeriodList : ListView

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val mainView = inflater.inflate(R.layout.content_update_mode, container, false)
        widgetConfigureActivity = activity as WidgetConfigureActivity
        spinner = mainView.findViewById(R.id.update_mode_spinner)
        updatePeriodList = mainView.findViewById(R.id.update_period_list_view)
        updatePeriodList.adapter = widgetConfigureActivity.adapter
        configureUpdateModeSpinner(mainView)
        return mainView
    }

    private fun updateUpdatePeriod() {
        widgetConfigureActivity.adapter.clear()
        val records = widgetConfigureActivity.getTimeRecords()
        for (record in records) {
            if (record.count > 1) {
                widgetConfigureActivity.adapter.add("${record.hour}:${record.minute} -wk ${record.weekday} ${record.count}")
            }
        }
        widgetConfigureActivity.adapter.notifyDataSetChanged()
    }

 fun configureUpdateModeSpinner(mainView : View) {
     spinner = mainView.findViewById(R.id.update_mode_spinner)
     val adapter = ArrayAdapter<String>(activity,
         android.R.layout.simple_spinner_item,
         resources.getStringArray(R.array.update_mode_array))
     adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
     spinner.adapter = adapter
     spinner.setSelection(widgetConfigureActivity.widgetConfig.updateSettings.updateModeValue)
     spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
         override fun onItemSelected(parent: AdapterView<*>, view: View, position: Int, id: Long) {
             LOG.info("selected update mode $position")
             val updateSettings = widgetConfigureActivity.widgetConfig.updateSettings.toBuilder()
             updateSettings.updateModeValue = position
             widgetConfigureActivity.widgetConfig = widgetConfigureActivity.widgetConfig.toBuilder()
                 .setUpdateSettings(updateSettings).build()
             val intent = Intent(activity, BackgroundUpdaterService::class.java)
             if (position == Ng.UpdateSettings.UpdateMode.ALWAYS_UPDATE_MODE_VALUE) {
                 activity!!.startService(intent)
             } else {
                 activity!!.stopService(intent)
             }
             if (position == Ng.UpdateSettings.UpdateMode.LEARNING_UPDATE_MODE_VALUE) {
                 updatePeriodList.visibility = View.VISIBLE
                 updateUpdatePeriod()
             } else {
                 updatePeriodList.visibility = View.GONE
             }

         }
         override fun onNothingSelected(parent: AdapterView<*>) {
         }

     }
 }
}