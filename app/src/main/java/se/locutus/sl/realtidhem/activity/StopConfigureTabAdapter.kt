package se.locutus.sl.realtidhem.activity

import android.content.Context
import android.support.v4.app.Fragment
import android.support.v4.app.FragmentManager
import android.support.v4.app.FragmentPagerAdapter
import android.view.ViewGroup
import se.locutus.sl.realtidhem.R

class StopConfigureTabAdapter(var context : Context, fm : FragmentManager) : FragmentPagerAdapter(fm) {
    lateinit var selectStopFragment : SelectStopFragment
    lateinit var selectDeparturesFragment : SelectDeparturesFragment
    lateinit var selectLinesFragment : SelectLinesFragment

    override fun getItem(position: Int): Fragment? = when (position) {
        0 -> SelectStopFragment.newInstance()
        1 -> SelectLinesFragment.newInstance()
        2 -> SelectDeparturesFragment.newInstance()
        else -> null
    }

    override fun getPageTitle(position: Int): CharSequence = when (position) {
        0 -> context.getString(R.string.select_stop_tab)
        1 -> context.getString(R.string.select_lines_tab)
        2 -> context.getString(R.string.select_departures_tab)
        else -> ""
    }

    override fun instantiateItem(container: ViewGroup, position: Int): Any {
        val createdFragment = super.instantiateItem(container, position) as Fragment
        // save the appropriate reference depending on position
        when (position) {
            0 -> selectStopFragment = createdFragment as SelectStopFragment
            1 -> selectLinesFragment = createdFragment as SelectLinesFragment
            2 -> selectDeparturesFragment = createdFragment as SelectDeparturesFragment
        }
        return createdFragment
    }

    override fun getCount(): Int = 3
}