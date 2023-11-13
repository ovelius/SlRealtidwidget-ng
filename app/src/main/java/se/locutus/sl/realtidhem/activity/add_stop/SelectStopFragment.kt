package se.locutus.sl.realtidhem.activity.add_stop

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import com.google.android.material.snackbar.Snackbar
import androidx.core.content.ContextCompat
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.EditText
import android.widget.TextView
import com.android.volley.Request
import com.android.volley.Response
import com.android.volley.toolbox.StringRequest
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import org.json.JSONArray
import org.json.JSONObject
import se.locutus.proto.Ng
import se.locutus.proto.Ng.SiteId
import se.locutus.sl.realtidhem.R
import se.locutus.sl.realtidhem.events.WidgetTouchHandler
import se.locutus.sl.realtidhem.widget.getUseNewBackend
import se.locutus.sl.realtidhem.widget.isSiteConfigured
import java.lang.Exception
import java.util.*
import java.util.logging.Logger

fun setGreenBg(view : View) {
    view.setBackgroundColor((0x33484848).toInt())
}

class SelectStopFragment : androidx.fragment.app.Fragment() {
    companion object {
        val LOG = Logger.getLogger(SelectStopFragment::class.java.name)
    }
    val autoCompleteSet = HashSet<String>()
    internal lateinit var mAutoCompleteTextView : AutoCompleteTextView
    internal lateinit var displayNameText : EditText
    internal lateinit var addStopActivity : AddStopActivity
    private var useNewBackend : Boolean = false
    private lateinit var mapContainer : View
    internal var map : GoogleMap? = null
    internal var nameToSiteIDs : HashMap<String, SiteId> = HashMap()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LOG.info("Fragment create with bundle $savedInstanceState")
        useNewBackend = getUseNewBackend(requireActivity().getSharedPreferences(null, 0))
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val mainView =  inflater.inflate(R.layout.content_add_stop, container, false)
        addStopActivity = activity as AddStopActivity

        displayNameText = mainView.findViewById(R.id.stop_display_name_text)
        mAutoCompleteTextView = mainView.findViewById(R.id.stop_auto_complete)
        mapContainer = mainView.findViewById(R.id.map_container)
        mapContainer.visibility = View.GONE
        mAutoCompleteTextView.threshold = 1

        val config = addStopActivity.config.stopData
        LOG.info("onCreateView, set stuff from $config")
        mAutoCompleteTextView.setText(config.canonicalName, false)
        mAutoCompleteTextView.setOnKeyListener{ _, keyCode : Int, _ ->
            if(keyCode == KeyEvent.KEYCODE_DEL) {
                if (isSiteConfigured(addStopActivity.config)) {
                    mAutoCompleteTextView.setText("", false)
                }
            }
            false
        }

        displayNameText.setText(config.displayName, TextView.BufferType.EDITABLE)
        displayNameText.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(p0: Editable?) {
            }
            override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {
            }
            override fun onTextChanged(p: CharSequence?, p1: Int, p2: Int, p3: Int) {
                addStopActivity.updateStopDataDisplayText(p.toString())
            }
        })
        if (isSiteConfigured(addStopActivity.config) && !config.canonicalName.isEmpty()) {
            setGreenBg(mAutoCompleteTextView)
        }

        val mapFragment = childFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync{it ->
            map = it
            if (isSiteConfigured(addStopActivity.config)) {
                mapTo(config.lat, config.lng)
            }
        }
        val adapter = ArrayAdapter<String>(
            this.requireActivity(),
            android.R.layout.simple_dropdown_item_1line, ArrayList()
        )
        mAutoCompleteTextView.setAdapter(adapter)
        mAutoCompleteTextView.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(p0: Editable?) {
            }
            override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {
            }
            override fun onTextChanged(p: CharSequence?, p1: Int, p2: Int, p3: Int) {
                var siteId : SiteId? = nameToSiteIDs[p.toString()]

                val configuredName = addStopActivity.config.stopData.canonicalName
                if (isSiteConfigured(addStopActivity.config) && configuredName == p.toString()) {
                    LOG.info("Configured name in autocomplete. Not doing anything.")
                    return
                }
                if (p?.length == 0) {
                    addStopActivity.clearDeparturesList()
                    displayNameText.setText("", TextView.BufferType.EDITABLE)
                    return
                }
                if (siteId != null) {
                    LOG.info("Selected $p $siteId")
                    hideKeyboard()
                    setGreenBg(mAutoCompleteTextView)
                    if (addStopActivity.hasLoadedDeparturesFor(siteId)) {
                        LOG.info("Departures already loaded")
                        return
                    }
                    if (p!!.contains("(")) {
                        displayNameText.setText(p.substring(0, p.indexOf("(") - 1), TextView.BufferType.EDITABLE)
                    } else {
                        displayNameText.setText(p, TextView.BufferType.EDITABLE)
                    }
                    addStopActivity.updateStopDataDisplayText(displayNameText.text.toString())
                    addStopActivity.clearDeparturesList()
                    addStopActivity.loadDepsFor(siteId)
                } else {
                    mAutoCompleteTextView.setBackgroundColor((0x00000000).toInt())
                    LOG.info("Searching for $p")
                    if (useNewBackend) {
                        doNewStopSearchRequest(p, adapter)
                    } else {
                        val stringRequest = buildLegacyStringRequest(p, adapter)
                        addStopActivity.requestQueue.add(stringRequest)
                    }
                }
            }
        })

        return mainView
    }

    private fun doNewStopSearchRequest(p : CharSequence?, adapter : ArrayAdapter<String>) {
        val req = Ng.RequestData.newBuilder()
            .setStopSearchRequest(Ng.StopSearchRequest.newBuilder().setQuery(p.toString()))
            .build()
        addStopActivity.network.doGenericRequest(req, forceHttp = true) {
                incomingRequestId : Int, responseData: Ng.ResponseData, e: Exception? ->

            if (e != null) {
                Snackbar.make(
                    mAutoCompleteTextView,
                    R.string.error_loading_autocomplete,
                    Snackbar.LENGTH_SHORT
                )
            } else {
                for (stop in responseData.stopSearchResponse.stopDataList) {
                    val name = stop.canonicalName
                    if (!autoCompleteSet.contains(name)) {
                        adapter.add(name)
                        autoCompleteSet.add(name)
                    }
                    nameToSiteIDs[name] = stop.site
                }
                mAutoCompleteTextView.performCompletion()
            }
        }
    }

    private fun buildLegacyStringRequest(p : CharSequence?, adapter : ArrayAdapter<String>) : StringRequest {
        val url = "http://anka.locutus.se/P?q=$p"
        return StringRequest(Request.Method.GET, url,
            Response.Listener<String> { response ->
                LOG.info("got $response")
                var json = JSONObject(response)
                var list: JSONArray = json.getJSONArray("suggestions")
                for (i in 0 until list.length() - 1) {
                    var item: JSONObject = list.getJSONObject(i)
                    var name: String = item.getString("name")
                    var siteId: Int = item.getInt("sid")
                    if (!autoCompleteSet.contains(name)) {
                        adapter.add(name)
                        autoCompleteSet.add(name)
                    }
                    nameToSiteIDs[name] = SiteId.newBuilder().setSiteId(siteId.toLong()).build()
                }
                mAutoCompleteTextView.performCompletion()
            },
            Response.ErrorListener {
                LOG.severe("Error autocompleting $it")
                try {
                    Snackbar.make(
                        mAutoCompleteTextView,
                        R.string.error_loading_autocomplete,
                        Snackbar.LENGTH_SHORT
                    )
                        .show()
                } catch (e: IllegalArgumentException) {
                    LOG.severe("Failed to create snackbar $e, activity dead?")
                }
            })
    }

    private fun bitmapDescriptorFromVector(context : Context, vectorResId : Int) : BitmapDescriptor {
        val vectorDrawable = ContextCompat.getDrawable(context, vectorResId)!!
        vectorDrawable.setBounds(0, 0, vectorDrawable.intrinsicWidth, vectorDrawable.intrinsicHeight)
        val bitmap = Bitmap.createBitmap(vectorDrawable.intrinsicWidth, vectorDrawable.intrinsicHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        vectorDrawable.draw(canvas)
        return BitmapDescriptorFactory.fromBitmap(bitmap)
}

    fun mapTo(lat : Double, lng : Double) {
        if (map != null) {
            mapContainer.visibility = View.VISIBLE
            val latLng = LatLng(lat, lng)
            val mapMarker =  bitmapDescriptorFromVector(addStopActivity, R.drawable.ic_location_on_24px)
            val marker = MarkerOptions().apply {
                position(latLng)
                icon(mapMarker)
            }
            map!!.clear()
            map!!.addMarker(marker)
            map!!.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 11.0f))
        }
    }

    private fun hideKeyboard() {
        val imm = addStopActivity.getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(mAutoCompleteTextView.windowToken, 0)
    }
}