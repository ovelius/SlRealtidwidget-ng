package se.locutus.sl.realtidhem.activity.add_stop

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.app.AppCompatActivity
import com.android.volley.RequestQueue
import com.android.volley.toolbox.Volley
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout
import kotlinx.android.synthetic.main.activity_add_stop.*
import se.locutus.proto.Ng
import se.locutus.proto.Ng.DeparturesFilter
import se.locutus.sl.realtidhem.R
import se.locutus.sl.realtidhem.activity.*
import se.locutus.sl.realtidhem.activity.theme.ThemeActivity
import se.locutus.sl.realtidhem.events.EXTRA_COLOR_THEME
import se.locutus.sl.realtidhem.events.EXTRA_THEME_CONFIG
import se.locutus.sl.realtidhem.net.NetworkInterface
import se.locutus.sl.realtidhem.net.NetworkManager
import java.util.*
import java.util.logging.Logger

const val MODIFY_THEME_REQUEST_CODE = 12

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
    internal lateinit var viewPager: androidx.viewpager.widget.ViewPager
    private var allDeparturesResponse : Ng.AllDepaturesResponseData? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_stop)

        if (savedInstanceState?.containsKey(STOP_CONFIG_DATA_KEY) == true) {
            val configBuilt = Ng.StopConfiguration.parseFrom(savedInstanceState.getByteArray(STOP_CONFIG_DATA_KEY))
            LOG.info("Got stop from saved bundle $configBuilt")
            if (savedInstanceState.containsKey(ALL_DEPARTURES_DATA_KEY)) {
                allDeparturesResponse = Ng.AllDepaturesResponseData.parseFrom(savedInstanceState.getByteArray(
                    ALL_DEPARTURES_DATA_KEY
                ))
            }
            config = configBuilt.toBuilder()
        } else if (intent.hasExtra(STOP_CONFIG_DATA_KEY)) {
            val configBuilt = Ng.StopConfiguration.parseFrom(intent.getByteArrayExtra(STOP_CONFIG_DATA_KEY))
            LOG.info("Got stop from start intent $configBuilt")
            config = configBuilt.toBuilder()
        }
        if (intent.hasExtra(STOP_INDEX_DATA_KEY)) {
            stopIndex = intent.getIntExtra(STOP_INDEX_DATA_KEY, -1)
        }
        setSupportActionBar(toolbar)
        supportActionBar!!.setDisplayHomeAsUpEnabled(true)
        supportActionBar!!.setDisplayShowHomeEnabled(true)

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
        if (intent.hasExtra(EXTRA_COLOR_THEME)) {
            setColor(this, tabLayout, intent.getIntExtra(EXTRA_COLOR_THEME, 0))
        }
        viewPager = findViewById(R.id.view_pager)

        // Create or recycle fragments.
        val stopFragment = supportFragmentManager.findFragmentByTag(fragmentName(0))
            ?: SelectStopFragment()
        val lineFragment = supportFragmentManager.findFragmentByTag(fragmentName(1))
            ?: SelectLinesFragment()
        val departuresFragment = supportFragmentManager.findFragmentByTag(fragmentName(2))
            ?: SelectDeparturesFragment()
        stopConfigureTabAdapter = StopConfigureTabAdapter(
            this, supportFragmentManager,
            stopFragment as SelectStopFragment,
            lineFragment as SelectLinesFragment,
            departuresFragment as SelectDeparturesFragment
        )

        viewPager.adapter = stopConfigureTabAdapter
        tabLayout.setupWithViewPager(viewPager)

        if (config.stopData.siteId != 0L /* && allDeparturesResponse == null */) {
            loadDepsFor(config.stopData.siteId.toInt())
        }

        viewPager.addOnPageChangeListener(object : androidx.viewpager.widget.ViewPager.OnPageChangeListener {
            override fun onPageScrollStateChanged(state: Int) {
            }
            override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {
            }
            override fun onPageSelected(position: Int) {
                if (config.stopData.siteId == 0L && position != 0) {
                    viewPager.setCurrentItem(0, true)
                }  else if (position != 0) {
                    val imm = getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.hideSoftInputFromWindow(tabLayout.windowToken, 0)
                }
                if (departureAdapter.getCheckedItems().isNotEmpty() && position == 1) {
                    Snackbar.make(tabLayout, R.string.select_just_one_dep , Snackbar.LENGTH_SHORT).show()
                    viewPager.setCurrentItem(2, true)
                    stopConfigureTabAdapter.selectDeparturesFragment.mDepartureList.smoothScrollToPosition(departureAdapter.getCheckedPosition())
                }
                if (linesAdapter.isSelected() && position == 2) {
                    Snackbar.make(tabLayout, R.string.select_just_one_line , Snackbar.LENGTH_SHORT).show()
                    viewPager.setCurrentItem(1, true)
                    stopConfigureTabAdapter.selectLinesFragment.mLineList.smoothScrollToPosition(linesAdapter.firstSelectedIndex())
                }
            }
        })
    }

    private fun fragmentName(index: Int): String {
        return "android:switcher:${R.id.view_pager}:$index"
    }

    override fun onSaveInstanceState(outState: Bundle?) {
        super.onSaveInstanceState(outState)
        LOG.info("onSaveInstanceState $outState")
        outState?.putByteArray(STOP_CONFIG_DATA_KEY, config.build().toByteArray())
        if (allDeparturesResponse != null) {
            outState?.putByteArray(ALL_DEPARTURES_DATA_KEY, allDeparturesResponse!!.toByteArray())
        }
    }

    private fun getDisplayText() : String {
        return config.stopData.displayName
    }


    fun clearDeparturesList() {
        departureAdapter.clear()
        departureAdapter.notifyDataSetChanged()
        config.clearDeparturesFilter()
        linesAdapter.clear()
        linesAdapter.notifyDataSetChanged()
        config.clearLineFilter()
    }

    private fun snackbarRetryError(error_id : Int, siteId : Int) {
        Snackbar.make(tabLayout, error_id , Snackbar.LENGTH_INDEFINITE)
            .setAction(R.string.retry_load) {
                loadDepsFor(siteId)
            }.show()
    }

    fun loadDepsFor (siteId : Int) {
        val request = Ng.StopDataRequest.newBuilder()
            .setSiteId(siteId.toLong())
            .build()
        departureAdapter.clear()
        LOG.info("Loading departures for $siteId")
        network.doStopDataRequest(request, true) { incomingRequestId: Int, responseData: Ng.ResponseData, e: Exception? ->
            if (responseData.hasErrorResponse() && responseData.errorResponse.errorType != Ng.ErrorType.UNKNOWN_ERROR) {
                if (responseData.errorResponse.errorType == Ng.ErrorType.SL_API_ERROR) {
                    snackbarRetryError(R.string.sl_api_error, siteId)
                } else {
                    snackbarRetryError(R.string.error, siteId)
                }
            } else if (e != null) {
                snackbarRetryError(R.string.error, siteId)
            } else {
                LOG.info("Got response with ${responseData.allDepaturesResponse.depatureDataCount} departures")
                handleLoadResonse(responseData)
            }
        }
    }

    private fun handleLoadResonse(response : Ng.ResponseData) {
        val existing = config.departuresFilter.departuresList.toSet()
        val stopDataResponse = response.allDepaturesResponse.stopData
        val departures = response.allDepaturesResponse.depatureDataList
        val stopData = Ng.StoredStopData.newBuilder()
            .setCanonicalName(stopDataResponse.canonicalName)
            .setLat(stopDataResponse.lat)
            .setLng(stopDataResponse.lng)
            .setDisplayName(getDisplayText())
            .setSiteId(stopDataResponse.siteId)
        config.stopData = stopData.build()

        stopConfigureTabAdapter.selectStopFragment.mapTo(stopDataResponse.lat, stopDataResponse.lng)

        if (departures.isEmpty()) {
            Snackbar.make(tabLayout, R.string.error_no_departures , Snackbar.LENGTH_LONG)
                .setAction("none", null).show()
            tabLayout.getTabAt(0)!!.select()
            return
        }

        departureAdapter.clear()
        for (departure in departures) {
            var name: String = departure.canonicalName
            departureAdapter.add(departure, existing.contains(name))
        }

        val colorMap = createColorMap(response.allDepaturesResponse)
        stopConfigureTabAdapter.selectLinesFragment.indexDepartures(colorMap, response.allDepaturesResponse, linesAdapter, config.lineFilterList)
        departureAdapter.sort(colorMap)
        departureAdapter.notifyDataSetChanged()
        allDeparturesResponse = response.allDepaturesResponse
    }

    private fun getConfigErrorMessage() : Int? {
        if (config.stopData.siteId == 0L) {
            return R.string.no_stop_selected
        }
        if (departureAdapter.getCheckedItems().isEmpty() && !linesAdapter.isSelected()) {
            return R.string.no_departures_selected
        }
        return null
    }

    override fun onBackPressed() {
        if (getConfigErrorMessage() == null) {
            finishSuccessfully()
        } else {
            super.onBackPressed()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        val message = getConfigErrorMessage()
        if (message != null) {
            Snackbar.make(findViewById(R.id.tab_layout), message, Snackbar.LENGTH_SHORT)
                .setAction("Action", null).show()
            return false
        }
        finishSuccessfully()
        return true
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.stop_config_action_bar_menu, menu)
        menu.findItem(R.id.theme_widget_button).setOnMenuItemClickListener { _ ->
            updateConfigProto()
            LOG.info("Launching theme settings with config ${config.build()}")
            if (allDeparturesResponse != null) {
                val intent = Intent(this, ThemeActivity::class.java).apply {
                    putExtra(EXTRA_COLOR_THEME, window.statusBarColor)
                    putExtra(STOP_CONFIG_DATA_KEY, config.build().toByteArray())
                    putExtra(ALL_DEPARTURES_DATA_KEY, allDeparturesResponse!!.toByteArray())
                }
                startActivityForResult(intent, MODIFY_THEME_REQUEST_CODE)
            } else {
                Snackbar.make(findViewById(R.id.tab_layout), R.string.no_data_to_theme, Snackbar.LENGTH_SHORT).show()
            }
            true
        }
        return true
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        LOG.info("Activity result with request code $requestCode and data $data")
        if (requestCode == MODIFY_THEME_REQUEST_CODE && data != null && data.hasExtra(EXTRA_THEME_CONFIG)) {
            val themeConfig = Ng.ThemeData.parseFrom(data.getByteArrayExtra(EXTRA_THEME_CONFIG))
            LOG.info("Received theme config of $themeConfig ")
            config.themeData = themeConfig
        } else {
            LOG.severe("onActivityResult malformed request code/data $requestCode intent $data")
        }
    }

    fun updateStopDataDisplayText(displayText : String) {
        val stopData = config.stopData.toBuilder()
        stopData.displayName = displayText
        config.setStopData(stopData)
    }

    private fun updateConfigProto() {
        if (linesAdapter.isSelected()) {
            config.clearDeparturesFilter()
            config.clearLineFilter()
            for (item in linesAdapter.getSelectedItems()) {
                config.addLineFilter(
                    Ng.LineFilter.newBuilder()
                        .setGroupOfLineId(item.groupOfLineId)
                        .setDirectionId(item.directionId)
                )
            }
        } else {
            config.clearLineFilter()
            config
                .setDeparturesFilter(
                    DeparturesFilter.newBuilder()
                        .addAllDepartures(departureAdapter.getCheckedItems())
                )
        }
    }

    private fun finishSuccessfully() {
        updateConfigProto()

        val resultIntent = Intent()
        resultIntent.putExtra(STOP_CONFIG_DATA_KEY, config.build().toByteArray())
        if (stopIndex >= 0) {
            resultIntent.putExtra(STOP_INDEX_DATA_KEY, stopIndex)
        }
        setResult(Activity.RESULT_OK, resultIntent)
        finish()
    }
}
