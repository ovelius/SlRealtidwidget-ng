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

    fun removeFragment(fragment : Fragment) {
        fragmentList.remove(fragment)
        notifyDataSetChanged()
    }

    fun addFragment(fragment : Fragment, position : Int) {
        fragmentList.set(position, fragment)
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