package se.locutus.sl.realtidhem

import org.robolectric.shadows.ShadowAppWidgetManager
import se.locutus.sl.realtidhem.widget.StandardWidgetProvider

fun createWidgetId(shadowAppWidgetManager : ShadowAppWidgetManager) : Int {
    return shadowAppWidgetManager.createWidget(StandardWidgetProvider::class.java, R.layout.widgetlayout_base)
}