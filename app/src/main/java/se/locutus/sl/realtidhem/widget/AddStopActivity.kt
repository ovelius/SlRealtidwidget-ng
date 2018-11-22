package se.locutus.sl.realtidhem.widget

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.support.design.widget.Snackbar
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
    internal lateinit var requestQueue : RequestQueue
    internal lateinit var mAutoCompleteTextView : AutoCompleteTextView
    internal lateinit var mDepartureList : ListView
    internal var nameToSiteIDs : HashMap<String, Int> = HashMap()
    internal var config : Ng.StopConfiguration.Builder = Ng.StopConfiguration.newBuilder()
    internal var stopIndex : Int = -1
    internal lateinit var departureAdapter : DepartureListAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_stop)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        if (intent.hasExtra(STOP_CONFIG_DATA_KEY)) {
            config = Ng.StopConfiguration.parseFrom(intent.getByteArrayExtra(STOP_CONFIG_DATA_KEY)).toBuilder()
        }
        if (intent.hasExtra(STOP_INDEX_DATA_KEY)) {
            stopIndex = intent.getIntExtra(STOP_INDEX_DATA_KEY, -1)
        }
        requestQueue = Volley.newRequestQueue(this)
        val adapter = ArrayAdapter<String>(
            this,
            android.R.layout.simple_dropdown_item_1line, arrayOf()
        )
        departureAdapter = DepartureListAdapter(
            this,
            ArrayList()
        )
        mDepartureList = findViewById(R.id.departure_list_view)
        mDepartureList.adapter = departureAdapter
        mAutoCompleteTextView = findViewById(R.id.stop_auto_complete)
        mAutoCompleteTextView.setText(config.stopData.canonicalName, false)
        mAutoCompleteTextView.setAdapter(adapter)
        mAutoCompleteTextView.threshold = 1
        mAutoCompleteTextView.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(p0: Editable?) {
            }
            override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {
            }
            override fun onTextChanged(p: CharSequence?, p1: Int, p2: Int, p3: Int) {
                val siteId : Int? = nameToSiteIDs[p.toString()]
                if (siteId != null) {
                    LOG.info("Selected $p $siteId")
                    val imm = getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.hideSoftInputFromWindow(mAutoCompleteTextView.windowToken, 0)
                    loadDepsFor(siteId)
                } else {
                    config.clearStopData()
                    LOG.info("Searching for $p")
                    val url = "http://anka.locutus.se/P?q=$p"
                    val stringRequest = StringRequest(Request.Method.GET, url,
                        Response.Listener<String> { response ->
                            LOG.warning("got $response")
                            adapter.clear()
                            var json: JSONObject = JSONObject(response)
                            var list: JSONArray = json.getJSONArray("suggestions")
                            for (i in 0 until list.length() - 1) {
                                var item: JSONObject = list.getJSONObject(i)
                                var name: String = item.getString("name")
                                var siteId: Int = item.getInt("sid")
                                adapter.add(name)
                                nameToSiteIDs.put(name, siteId)
                            }
                            mAutoCompleteTextView.showDropDown()
                        },
                        Response.ErrorListener { error -> LOG.severe("Failure to autocomplete $error!") })
                    requestQueue.add(stringRequest)
                }
            }
        })
        if (config.stopData.siteId != 0L) {
            loadDepsFor(config.stopData.siteId.toInt())
        }
    }

    fun loadDepsFor (siteId : Int) {
        val stopData = Ng.StoredStopData.newBuilder()
        val existing = config.departuresFilter.departuresList.toSet()
        departureAdapter.clear()
        LOG.info("Loading depatures for $siteId")
        val url = "http://anka.locutus.se/F?a=49&sid=$siteId&t=3"
        val stringRequest = StringRequest(Request.Method.GET, url,
            Response.Listener<String> { response ->
                LOG.warning("got $response")
                var json: JSONObject = JSONObject(response)
                var list: JSONArray = json.getJSONArray("allDep")
                var nameSplit: JSONArray = json.getJSONArray("nameSplit")
                stopData.siteId = siteId.toLong()
                stopData.lat = json.getDouble("lat")
                stopData.lng = json.getDouble("lng")
                stopData.canonicalName = nameSplit.getString(1)
                for (existingDep in existing) {
                    departureAdapter.add(existingDep, true)
                }
                for (i in 0 until list.length() - 1) {
                    var name: String = list.getString(i)
                    if (!existing.contains(name)) {
                        departureAdapter.add(name, false)
                    }
                }
                departureAdapter.notifyDataSetChanged()
                config.stopData = stopData.build()
            },
            Response.ErrorListener { error -> LOG.severe("Failure to autocomplete $error!") })
        requestQueue.add(stringRequest)
    }

    fun getConfigErrorMessage() : Int? {
        if (config.stopData.siteId == 0L) {
            return R.string.no_stop_selected
        }
        if (departureAdapter.getCheckedItems().isEmpty()) {
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
        menu.findItem(R.id.save_stop_action).setOnMenuItemClickListener {item ->
            val message = getConfigErrorMessage()
            if (message != null) {
                Snackbar.make(mDepartureList, message, Snackbar.LENGTH_SHORT)
                    .setAction("Action", null).show()
                false
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
                .addAllDepartures(departureAdapter.getCheckedItems()))
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
