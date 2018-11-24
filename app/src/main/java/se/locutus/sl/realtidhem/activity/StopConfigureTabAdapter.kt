package se.locutus.sl.realtidhem.activity

import android.support.v4.app.Fragment
import android.support.v4.app.FragmentManager
import android.support.v4.app.FragmentPagerAdapter
import android.view.ViewGroup



class StopConfigureTabAdapter(fm : FragmentManager) : FragmentPagerAdapter(fm) {
    lateinit var selectStopFragment : SelectStopFragment
    lateinit var selectDeparturesFragment : SelectDeparturesFragment

    override fun getItem(position: Int): Fragment? = when (position) {
        0 -> SelectStopFragment.newInstance()
        1 -> SelectDeparturesFragment.newInstance()
        else -> null
    }

    override fun getPageTitle(position: Int): CharSequence = when (position) {
        0 -> "Stop"
        1 -> "Departures"
        2 -> "Tab 3 Item"
        else -> ""
    }

    override fun instantiateItem(container: ViewGroup, position: Int): Any {
        val createdFragment = super.instantiateItem(container, position) as Fragment
        // save the appropriate reference depending on position
        when (position) {
            0 -> selectStopFragment = createdFragment as SelectStopFragment
            1 -> selectDeparturesFragment = createdFragment as SelectDeparturesFragment
        }
        return createdFragment
    }

    override fun getCount(): Int = 2
}