package se.locutus.sl.realtidhem.activity

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.support.design.widget.Snackbar
import android.support.design.widget.TabLayout
import android.support.v4.view.ViewPager
import android.support.v7.app.AppCompatActivity
import android.view.Menu
import android.widget.EditText
import se.locutus.sl.realtidhem.R
import kotlinx.android.synthetic.main.activity_add_stop.*
import com.android.volley.RequestQueue
import com.android.volley.toolbox.Volley
import se.locutus.proto.Ng
import se.locutus.proto.Ng.DeparturesFilter
import se.locutus.sl.realtidhem.net.NetworkInterface
import se.locutus.sl.realtidhem.net.NetworkManager
import java.util.ArrayList
import java.util.logging.Logger


class AddStopActivity : AppCompatActivity() {
    companion object {
        val LOG = Logger.getLogger(AddStopActivity::class.java.name)
    }
    internal var config : Ng.StopConfiguration.Builder = Ng.StopConfiguration.newBuilder()
    internal var stopIndex : Int = -1
    internal lateinit var requestQueue : RequestQueue
    internal lateinit var stopConfigureTabAdapter : StopConfigureTabAdapter
    internal lateinit var departureAdapter : DepartureListAdapter
    internal lateinit var linesAdapter : LineListAdapter
    internal lateinit var network : NetworkInterface
    internal lateinit var tabLayout: TabLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_stop)

        // Do this early so fragments have access to the information.
        if (intent.hasExtra(STOP_CONFIG_DATA_KEY)) {
            config = Ng.StopConfiguration.parseFrom(intent.getByteArrayExtra(STOP_CONFIG_DATA_KEY)).toBuilder()
        }
        if (intent.hasExtra(STOP_INDEX_DATA_KEY)) {
            stopIndex = intent.getIntExtra(STOP_INDEX_DATA_KEY, -1)
        }
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)

        requestQueue = Volley.newRequestQueue(this)
        network = NetworkManager(this)

        departureAdapter = DepartureListAdapter(
            this,
            ArrayList()
        )
        linesAdapter = LineListAdapter(
            this,
            ArrayList()
        )
        tabLayout = findViewById(R.id.tab_layout)
        val viewPager: ViewPager = findViewById(R.id.view_pager)
        stopConfigureTabAdapter = StopConfigureTabAdapter(this, supportFragmentManager)
        viewPager.adapter = stopConfigureTabAdapter
        tabLayout.setupWithViewPager(viewPager)

        if (config.stopData.siteId != 0L) {
            loadDepsFor(config.stopData.siteId.toInt())
        } else {

        }
    }

    fun getDisplayText() : String {
        return stopConfigureTabAdapter.selectStopFragment.displayNameText.text.toString()
    }

    fun loadDepsFor (siteId : Int) {
        val request = Ng.StopDataRequest.newBuilder()
            .setSiteId(siteId.toLong())
            .build()
        departureAdapter.clear()
        LOG.info("Loading depatures for $siteId")
        network.doStopDataRequest(request, true) { incomingRequestId: Int, responseData: Ng.ResponseData, e: Exception? ->
            if (e != null) {
                Snackbar.make(tabLayout, R.string.error , Snackbar.LENGTH_SHORT)
                    .setAction("Action", null).show()
            } else {
                LOG.info("Got response with ${responseData} departures")
                handleLoadResonse(responseData)
            }
        }
    }

    fun handleLoadResonse(response : Ng.ResponseData) {
        val existing = config.departuresFilter.departuresList.toSet()
        val stopDataResponse = response.allDepaturesResponse.stopData
        val departures = response.allDepaturesResponse.depatureDataList
        val stopData = Ng.StoredStopData.newBuilder()
            .setCanonicalName(stopDataResponse.canonicalName)
            .setLat(stopDataResponse.lat)
            .setLng(stopDataResponse.lng)
            .setDisplayName(getDisplayText())
            .setSiteId(stopDataResponse.siteId)
        for (existingDep in existing) {
            departureAdapter.add(existingDep, true)
        }
        for (i in 0 until departures.size - 1) {
            var name: String = departures[i].canonicalName
            if (!existing.contains(name)) {
                departureAdapter.add(name, false)
            }
        }
        stopConfigureTabAdapter.selectLinesFragment.indexDepartures(response.allDepaturesResponse)
        departureAdapter.notifyDataSetChanged()
        config.stopData = stopData.build()
    }

    fun getConfigErrorMessage() : Int? {
        if (config.stopData.siteId == 0L) {
            return R.string.no_stop_selected
        }
        if (departureAdapter.getCheckedItems().isEmpty() && !linesAdapter.isSelected()) {
            return R.string.no_departures_selected
        }
        return null
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.add_stop_action_bar_menu, menu)
        menu.findItem(R.id.save_stop_action).setOnMenuItemClickListener {_ ->
            val message = getConfigErrorMessage()
            if (message != null) {
                Snackbar.make(findViewById(R.id.tab_layout), message, Snackbar.LENGTH_SHORT)
                    .setAction("Action", null).show()
            } else {
                finishSuccessfully()
            }
            true
        }
        return true
    }

    fun updateStopDataDisplayText() {
        val stopData = config.stopData.toBuilder()
        stopData.displayName = getDisplayText()
        config.setStopData(stopData)
    }

    fun finishSuccessfully() {
        updateStopDataDisplayText()

        if (linesAdapter.isSelected()) {
            config.clearDeparturesFilter()
            val selectedLineFilter = linesAdapter.selected
            config.setLineFilter(Ng.LineFilter.newBuilder()
                .setGroupOfLineId(selectedLineFilter.groupOfLineId)
                .setDirectionId(selectedLineFilter.directionId))
        } else {
            config.clearLineFilter()
            config
                .setDeparturesFilter(
                    DeparturesFilter.newBuilder()
                        .addAllDepartures(departureAdapter.getCheckedItems())
                )
        }
        val resultIntent = Intent()
        resultIntent.putExtra(STOP_CONFIG_DATA_KEY, config.build().toByteArray())
        if (stopIndex >= 0) {
            resultIntent.putExtra(STOP_INDEX_DATA_KEY, stopIndex)
        }
        setResult(Activity.RESULT_OK, resultIntent)
        finish()
    }
}
