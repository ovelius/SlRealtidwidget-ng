package se.locutus.sl.realtidhem.activity.add_stop

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ListView
import se.locutus.sl.realtidhem.R
import se.locutus.sl.realtidhem.activity.add_stop.AddStopActivity

class SelectDeparturesFragment : androidx.fragment.app.Fragment() {
    internal lateinit var mDepartureList : ListView
    internal lateinit var addStopActivity : AddStopActivity

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val mainView =  inflater.inflate(R.layout.content_select_departures, container, false)
        addStopActivity = activity as AddStopActivity
        mDepartureList = mainView.findViewById(R.id.departure_list_view)
        mDepartureList.adapter = addStopActivity.departureAdapter

        mDepartureList.setOnItemClickListener { _, _, position, _ ->
            addStopActivity.departureAdapter.clickItem(position)
        }

        return mainView
    }
}