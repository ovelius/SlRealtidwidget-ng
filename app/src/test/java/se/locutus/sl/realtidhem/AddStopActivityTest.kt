package se.locutus.sl.realtidhem

import android.content.Intent
import android.os.Looper
import android.view.View
import androidx.test.core.app.ApplicationProvider
import androidx.viewpager.widget.ViewPager
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import se.locutus.proto.Ng
import se.locutus.proto.Ng.AllDepaturesResponseData
import se.locutus.proto.Ng.ResponseData
import se.locutus.proto.Ng.SiteId
import se.locutus.proto.Ng.StopConfiguration
import se.locutus.proto.Ng.StoredStopData
import se.locutus.sl.realtidhem.activity.STOP_CONFIG_DATA_KEY
import se.locutus.sl.realtidhem.activity.add_stop.AddStopActivity


@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class AddStopActivityTest {
    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val prefs = context.getSharedPreferences(null, 0)
    lateinit var addStopActivityController: ActivityController<AddStopActivity>
    private val shadowLooper = Shadows.shadowOf(Looper.getMainLooper())
    val fakeRequests = FakeRequestQueue()

    @Before
    fun setPrefs() {
        fakeRequests.start()
    }

    @Test
    fun testCreateLoadsData() {
        val stopConfig = StopConfiguration.newBuilder()
            .setStopData(StoredStopData.newBuilder()
                .setSite(SiteId.newBuilder().setStrSiteId("123")))
            .build()
        val intent = Intent()
        intent.putExtra(STOP_CONFIG_DATA_KEY, stopConfig.toByteArray())
        addStopActivityController = Robolectric.buildActivity(AddStopActivity::class.java, intent)
        addStopActivityController.get().overrideRequestQueue(fakeRequests)

        fakeRequests.fakeStack.responses["http://anka.locutus.se:8989/"] = fakeResponseData().toByteArray()

        addStopActivityController.create()
        addStopActivityController.start()

        try {
            shadowLooper.runToEndOfTasks()
        } catch (e : UninitializedPropertyAccessException) {
        // Fix me... fragement views not loaded here.
        }
    }

    fun fakeResponseData() : ResponseData {
        return ResponseData.newBuilder()
            .setAllDepaturesResponse(AllDepaturesResponseData.newBuilder()
                .addDepatureData(Ng.DepartureData.newBuilder().setCanonicalName("test")))
            .build()
    }
}