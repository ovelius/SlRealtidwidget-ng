package se.locutus.sl.realtidhem.widget

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.support.design.widget.Snackbar
import android.support.design.widget.TabLayout
import android.support.v4.view.ViewPager
import android.support.v7.app.AppCompatActivity
import android.text.Editable
import android.text.TextWatcher
import android.view.Menu
import android.view.inputmethod.InputMethodManager
import android.widget.AutoCompleteTextView
import android.widget.ListView
import se.locutus.sl.realtidhem.R
import kotlinx.android.synthetic.main.activity_add_stop.*
import android.widget.ArrayAdapter
import com.android.volley.Request
import com.android.volley.RequestQueue
import com.android.volley.Response
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import org.json.JSONArray
import org.json.JSONObject
import se.locutus.proto.Ng
import se.locutus.proto.Ng.StopConfiguration
import se.locutus.proto.Ng.DeparturesFilter
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_stop)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)

        requestQueue = Volley.newRequestQueue(this)

        val tabLayout: TabLayout = findViewById(R.id.tab_layout)
        val viewPager: ViewPager = findViewById(R.id.view_pager)
        stopConfigureTabAdapter = StopConfigureTabAdapter(supportFragmentManager)
        viewPager.adapter = stopConfigureTabAdapter
        tabLayout.setupWithViewPager(viewPager)


        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                System.err.println("item ${viewPager.currentItem}")
            }

            override fun onTabUnselected(tab: TabLayout.Tab) {

            }

            override fun onTabReselected(tab: TabLayout.Tab) {

            }
        })

        if (intent.hasExtra(STOP_CONFIG_DATA_KEY)) {
            config = Ng.StopConfiguration.parseFrom(intent.getByteArrayExtra(STOP_CONFIG_DATA_KEY)).toBuilder()
        }
        if (intent.hasExtra(STOP_INDEX_DATA_KEY)) {
            stopIndex = intent.getIntExtra(STOP_INDEX_DATA_KEY, -1)
        }
    }


    fun getConfigErrorMessage() : Int? {
        if (config.stopData.siteId == 0L) {
            return R.string.no_stop_selected
        }
        if (stopConfigureTabAdapter.selectDeparturesFragment.departureAdapter.getCheckedItems().isEmpty()) {
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

    fun finishSuccessfully() {
        val builtConfig = config
            .setDeparturesFilter(DeparturesFilter.newBuilder()
                .addAllDepartures(stopConfigureTabAdapter.selectDeparturesFragment.departureAdapter.getCheckedItems()))
            .build()
        val resultIntent = Intent()
        resultIntent.putExtra(STOP_CONFIG_DATA_KEY, builtConfig.toByteArray())
        if (stopIndex >= 0) {
            resultIntent.putExtra(STOP_INDEX_DATA_KEY, stopIndex)
        }
        setResult(Activity.RESULT_OK, resultIntent)
        finish()
    }
}
