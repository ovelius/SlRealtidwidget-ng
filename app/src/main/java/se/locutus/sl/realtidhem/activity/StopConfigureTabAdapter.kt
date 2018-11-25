package se.locutus.sl.realtidhem.activity

import android.content.Context
import android.support.v4.app.Fragment
import android.support.v4.app.FragmentManager
import android.support.v4.app.FragmentPagerAdapter
import android.support.v4.app.FragmentTabHost
import android.view.ViewGroup
import se.locutus.sl.realtidhem.R

class StopConfigureTabAdapter(var context : Context, fm : FragmentManager) : FragmentPagerAdapter(fm) {
    var selectStopFragment = SelectStopFragment()
    var selectDeparturesFragment = SelectDeparturesFragment()
    var selectLinesFragment = SelectLinesFragment()

    private val fragmentList = ArrayList<Fragment>().apply {
        add(selectStopFragment)
        add(selectLinesFragment)
        add(selectDeparturesFragment)
    }
    private val resourceIds = HashMap<Fragment, Int>().apply {
        put(selectStopFragment, R.string.select_stop_tab)
        put(selectLinesFragment, R.string.select_lines_tab)
        put(selectDeparturesFragment, R.string.select_departures_tab)
    }

    fun removeLinesFragment() {
        if (fragmentList.size == 3) {
            fragmentList.removeAt(1)
            notifyDataSetChanged()
        }
    }

    fun removeDeparturesFragment() {
        if (fragmentList.size == 3) {
            fragmentList.removeAt(2)
            notifyDataSetChanged()
        }
    }

    fun resetFragments() {
        if (fragmentList.size == 3) {
            return
        }
        fragmentList.removeAt(1)
        fragmentList.add(selectLinesFragment)
        fragmentList.add(selectDeparturesFragment)
        notifyDataSetChanged()
    }

    override fun getItem(position: Int): Fragment? {
        return fragmentList[position]
    }

    override fun getPageTitle(position: Int): CharSequence {
        val fragment = getItem(position)
        return context.getString(resourceIds[fragment]!!)
    }

    override fun getCount(): Int = fragmentList.size
}