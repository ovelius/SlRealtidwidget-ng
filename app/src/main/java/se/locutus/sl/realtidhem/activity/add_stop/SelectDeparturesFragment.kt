package se.locutus.sl.realtidhem.activity.add_stop

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ListView
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import se.locutus.sl.realtidhem.R
import se.locutus.sl.realtidhem.activity.add_stop.AddStopActivity

class SelectDeparturesFragment : androidx.fragment.app.Fragment() {
    internal lateinit var mDepartureList : ListView
    internal lateinit var addStopActivity : AddStopActivity
    internal lateinit var saveAndExit : ExtendedFloatingActionButton

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val mainView =  inflater.inflate(R.layout.content_select_departures, container, false)
        addStopActivity = activity as AddStopActivity
        mDepartureList = mainView.findViewById(R.id.departure_list_view)
        mDepartureList.adapter = addStopActivity.departureAdapter

        saveAndExit = mainView.findViewById(R.id.save_config)
        saveAndExit.setOnClickListener {
            addStopActivity.finishSuccessfully()
        }
        if (addStopActivity.window.statusBarColor != 0) {
            saveAndExit.backgroundTintList = ColorStateList.valueOf(addStopActivity.window.statusBarColor)
        }
        mDepartureList.setOnItemClickListener { _, _, position, _ ->
            addStopActivity.departureAdapter.clickItem(position)
        }

        return mainView
    }
}