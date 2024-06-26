package se.locutus.sl.realtidhem.activity.add_stop

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ListView
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import se.locutus.proto.Ng
import se.locutus.sl.realtidhem.R
import se.locutus.sl.realtidhem.events.EXTRA_COLOR_THEME
import java.util.ArrayList
import java.util.logging.Logger
import kotlin.collections.HashMap
import kotlin.collections.Map
import kotlin.collections.MutableList
import kotlin.collections.set

/**
 * Map unique colors to a low value for sorting.
 */
fun createColorMap(data : Ng.AllDepaturesResponseData) : Map<Int, Int> {
    val colorMap = HashMap<Int, Int>()
    for (departure in data.departureDataList) {
        colorMap[departure.color] = if (colorMap.containsKey(departure.color)) colorMap[departure.color]!! + 1 else 1
    }
    return colorMap
}

class SelectLinesFragment : androidx.fragment.app.Fragment() {
    companion object {
        val LOG = Logger.getLogger(SelectLinesFragment::class.java.name)
    }
    internal lateinit var mLineList : ListView
    internal lateinit var saveAndExit : ExtendedFloatingActionButton
    internal lateinit var addStopActivity : AddStopActivity

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        LOG.info("onCreateView")
        val mainView =  inflater.inflate(R.layout.content_select_lines, container, false)
        addStopActivity = activity as AddStopActivity

        mLineList = mainView.findViewById(R.id.lines_list_view)
        mLineList.adapter = addStopActivity.linesAdapter

        mLineList.setOnItemClickListener { _, _, position, _ ->
            addStopActivity.linesAdapter.clickItem(position)
        }

        saveAndExit = mainView.findViewById(R.id.save_config)
        saveAndExit.setOnClickListener {
            addStopActivity.finishSuccessfully()
        }
        if (addStopActivity.window.statusBarColor != 0) {
            saveAndExit.backgroundTintList = ColorStateList.valueOf(addStopActivity.window.statusBarColor)
        }

        return mainView
    }

    fun indexDepartures(colorMap : Map<Int, Int>, data : Ng.AllDepaturesResponseData,
                        linesAdapter : LineListAdapter,
                        lineFilterList: List<Ng.LineFilter>) {
        LOG.info("Indexing departures ${data.departureDataCount}")
        linesAdapter.clear()
        // Map with line as key, departureList as value.
        val map = HashMap<String, MutableList<Ng.DepartureData>>()
        for (departure in data.departureDataList) {
            val key = "${departure.groupOfLineId}_${departure.directionId}"
            if (!map.containsKey(key)) {
                map[key] = ArrayList()
            }
            map[key]!!.add(departure)
        }

        val configuredLines = HashSet<String>()
        for (lineFilter in lineFilterList) {
            val selectedKey = "${lineFilter.groupOfLineId}_${lineFilter.directionId}"
            configuredLines.add(selectedKey)
        }

        for (key in map.keys) {
            linesAdapter.add(map[key]!!, configuredLines.contains(key))
        }
        LOG.info("Indexed to ${map.keys.size} lines, selected ${configuredLines.size}")
        linesAdapter.sort(colorMap)
        linesAdapter.notifyDataSetChanged()
    }


}