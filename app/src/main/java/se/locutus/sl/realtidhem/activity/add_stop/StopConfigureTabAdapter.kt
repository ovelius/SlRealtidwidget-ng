package se.locutus.sl.realtidhem.activity.add_stop

import android.content.Context
import se.locutus.sl.realtidhem.R

class StopConfigureTabAdapter(var context : Context, fm : androidx.fragment.app.FragmentManager,
                              val selectStopFragment : SelectStopFragment,
                              val selectLinesFragment : SelectLinesFragment,
                              val selectDeparturesFragment : SelectDeparturesFragment
)
      : androidx.fragment.app.FragmentPagerAdapter(fm) {

    private val fragmentList = ArrayList<androidx.fragment.app.Fragment>().apply {
        add(selectStopFragment)
        add(selectLinesFragment)
        add(selectDeparturesFragment)
    }
    private val resourceIds = HashMap<androidx.fragment.app.Fragment, Int>().apply {
        put(selectStopFragment, R.string.select_stop_tab)
        put(selectLinesFragment, R.string.select_lines_tab)
        put(selectDeparturesFragment, R.string.select_departures_tab)
    }

    override fun getItem(position: Int): androidx.fragment.app.Fragment? {
        return fragmentList[position]
    }

    override fun getPageTitle(position: Int): CharSequence {
        val fragment = getItem(position)
        return context.getString(resourceIds[fragment]!!)
    }

    override fun getCount(): Int = fragmentList.size
}