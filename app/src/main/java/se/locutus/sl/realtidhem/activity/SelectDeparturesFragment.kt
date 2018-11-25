package se.locutus.sl.realtidhem.activity

import android.os.Bundle
import android.support.v4.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ListView
import se.locutus.sl.realtidhem.R

class SelectDeparturesFragment : Fragment() {
    internal lateinit var mDepartureList : ListView
    internal lateinit var addStopActivity : AddStopActivity

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val mainView =  inflater.inflate(R.layout.content_select_departures, container, false)
        addStopActivity = activity as AddStopActivity
        mDepartureList = mainView.findViewById(R.id.departure_list_view)
        mDepartureList.adapter = addStopActivity.departureAdapter
        return mainView
    }

    companion object {
        fun newInstance(): SelectDeparturesFragment =
            SelectDeparturesFragment()
    }
}