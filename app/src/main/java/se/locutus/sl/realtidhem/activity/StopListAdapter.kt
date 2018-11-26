package se.locutus.sl.realtidhem.activity

import android.app.Activity
import android.content.Context
import android.support.design.widget.Snackbar
import android.support.v7.widget.PopupMenu
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import se.locutus.proto.Ng
import se.locutus.sl.realtidhem.R


class StopListAdapter(private val activity: Activity, private var widgetConfig : Ng.WidgetConfiguration) : BaseAdapter() {
    private var deletedStop: Ng.StopConfiguration = Ng.StopConfiguration.getDefaultInstance()
    private var deleteIndex = -1
    private val inflater: LayoutInflater = activity.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
    override fun getItemId(position: Int): Long {
        return widgetConfig.getStopConfiguration(position).stopData.siteId
    }

    override fun getCount(): Int {
        return widgetConfig.stopConfigurationCount
    }

    override fun getItem(position: Int): Ng.StopConfiguration {
        return widgetConfig.getStopConfiguration(position)
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val root: View = convertView ?: inflater.inflate(R.layout.stop_list_item, parent, false)
        val nameText: TextView = root.findViewById(R.id.stop_name)
        val rowClickImage = root.findViewById<ImageView>(R.id.row_click_image)
        nameText.text = widgetConfig.getStopConfiguration(position).stopData.displayName
        rowClickImage.setOnClickListener { view: View ->
            val popup = PopupMenu(activity, view)
            popup.menuInflater.inflate(R.menu.stop_context_menu, popup.menu)
            popup.show()
            popup.setOnMenuItemClickListener {
                when (it.itemId) {
                    R.id.menu_delete_stop -> {
                        deleteStopAt(position)
                        Snackbar.make(view, R.string.deleted, Snackbar.LENGTH_LONG)
                            .setAction(R.string.undo) {
                                undoDelete()
                            }.show()
                    }
                }
                true
            }
        }
        return root
    }

    private fun undoDelete() {
        if (deletedStop != Ng.StopConfiguration.getDefaultInstance()) {
            val newList = widgetConfig.stopConfigurationList.toMutableList()
            newList.add(deleteIndex, deletedStop)
            refreshListWithItems(newList)
            notifyDataSetChanged()
        }
    }

    fun refreshListWithItems(items : Collection<Ng.StopConfiguration>) {
        widgetConfig = widgetConfig.toBuilder().clearStopConfiguration().addAllStopConfiguration(items).build()
        notifyDataSetChanged()
    }

    private fun deleteStopAt(position: Int) {
        val newList = widgetConfig.stopConfigurationList.toMutableList()
        deletedStop = newList.removeAt(position)
        deleteIndex = position
        refreshListWithItems(newList)
    }

    fun update(config : Ng.WidgetConfiguration) {
        widgetConfig = config
        notifyDataSetChanged()
    }
}