package se.locutus.sl.realtidhem

import android.appwidget.AppWidgetManager
import android.content.Context
import android.os.PowerManager
import androidx.test.core.app.ApplicationProvider
import org.hamcrest.CoreMatchers
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAppWidgetManager
import org.robolectric.shadows.ShadowContextWrapper
import org.robolectric.shadows.ShadowPowerManager
import se.locutus.proto.Ng
import se.locutus.proto.Ng.StopConfiguration
import se.locutus.proto.Ng.StoredStopData
import se.locutus.sl.realtidhem.activity.WIDGET_CONFIG_PREFS
import se.locutus.sl.realtidhem.widget.loadWidgetConfigOrDefault
import se.locutus.sl.realtidhem.widget.storeWidgetConfig

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class ConfigurationTest {

    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val prefs = context.getSharedPreferences(WIDGET_CONFIG_PREFS, 0)
    private val stop1Data = Ng.StoredStopData.newBuilder().setSiteId(123L).setDisplayName("Stop1")
    private val stop1Filter = Ng.DeparturesFilter.newBuilder().addDepartures("123 Bla")
    private val stop1 = Ng.StopConfiguration.newBuilder()
        .setStopData(stop1Data)
        .setDeparturesFilter(stop1Filter)
        .build()
    private val shadowAppWidgetManager: ShadowAppWidgetManager =
        Shadows.shadowOf(AppWidgetManager.getInstance(context))

    @Test
    fun testUpgradeFromLegacyFormat() {
        val widgetId = createWidgetConfig()

        val config = loadWidgetConfigOrDefault(prefs, widgetId)

        val expectedConfig = Ng.WidgetConfiguration.newBuilder()
            .setWidgetId(widgetId.toLong())
            .addStopConfiguration(StopConfiguration.newBuilder().
                setStopData(StoredStopData.newBuilder()
                    .setDisplayName("Stop1")
                    .setSiteId(123)
                    .setSite(Ng.SiteId.newBuilder()
                    .setSiteId(123)))
                .setDeparturesFilter(stop1Filter))
            .build()

        Assert.assertThat(config, CoreMatchers.`is`(expectedConfig))
    }

    private fun createWidgetConfig() : Int{
        val widgetId = createWidgetId(shadowAppWidgetManager)
        val config = Ng.WidgetConfiguration.newBuilder()
            .setWidgetId(widgetId.toLong())
            .addStopConfiguration(stop1)
            .build()
        storeWidgetConfig(prefs, config)
        return widgetId
    }
}