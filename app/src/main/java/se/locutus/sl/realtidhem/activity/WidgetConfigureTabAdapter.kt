package se.locutus.sl.realtidhem.activity

import android.content.Context
import androidx.fragment.app.Fragment
import se.locutus.sl.realtidhem.R

class WidgetConfigureTabAdapter(var context : Context,
                                val stopListFragment: StopListFragment, val updateModeFragment: UpdateModeFragment,
                                fm : androidx.fragment.app.FragmentManager)
    :  androidx.fragment.app.FragmentPagerAdapter(fm) {

    override fun getPageTitle(position: Int): CharSequence? {
        if (position == 0) {
            return context.getString(R.string.stop_list_tab)
        }
        return return context.getString(R.string.update_mode_tab)
    }

    override fun getItem(position: Int): Fragment {
        if (position == 0) {
            return stopListFragment
        }
        return updateModeFragment
    }

    override fun getCount(): Int {
        return 2
    }

}