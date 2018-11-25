package se.locutus.sl.realtidhem.activity

import android.os.Bundle
import android.support.v4.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ListView
import se.locutus.proto.Ng
import se.locutus.sl.realtidhem.R
import java.util.ArrayList
import java.util.logging.Logger

class SelectLinesFragment : Fragment() {
    companion object {
        val LOG = Logger.getLogger(SelectLinesFragment::class.java.name)
        fun newInstance(): SelectLinesFragment =
            SelectLinesFragment()
    }
    internal lateinit var mLineList : ListView
    internal lateinit var addStopActivity : AddStopActivity

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val mainView =  inflater.inflate(R.layout.content_select_lines, container, false)
        addStopActivity = activity as AddStopActivity

        mLineList = mainView.findViewById(R.id.lines_list_view)
        mLineList.adapter = addStopActivity.linesAdapter
        return mainView
    }

    fun indexDepartures(data : Ng.AllDepaturesResponseData) {
        LOG.info("Indexing departures ${data.depatureDataCount}")
        addStopActivity.linesAdapter.clear()
        val map = HashMap<String, MutableList<Ng.DepartureData>>()
        for (departure in data.depatureDataList) {
            val key = "${departure.groupOfLineId}_${departure.directionId}"
            if (!map.containsKey(key)) {
                map[key] = ArrayList<Ng.DepartureData>()
            }
            map[key]!!.add(departure)
        }
        for (key in map.keys) {
            addStopActivity.linesAdapter.add(map[key]!!, false)
        }
        LOG.info("Indexed to ${map.keys.size} lines")
        if (addStopActivity.config.lineFilter.directionId != 0) {
            val lineFilter = addStopActivity.config.lineFilter
            val selectedKey = "${lineFilter.groupOfLineId}_${lineFilter.directionId}"
            if (map.containsKey(selectedKey)) {
                addStopActivity.linesAdapter.selected = map[selectedKey]!![0]
            }
        }
        addStopActivity.linesAdapter.notifyDataSetChanged()
    }


}