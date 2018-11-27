package se.locutus.sl.realtidhem.activity

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v4.content.ContextCompat
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
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
import com.google.android.gms.maps.MapFragment
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import org.json.JSONArray
import org.json.JSONObject
import se.locutus.sl.realtidhem.R
import java.util.ArrayList
import java.util.logging.Logger

fun setGreenBg(view : View) {
    view.setBackgroundColor((0x3300FF00).toInt())
}

class SelectStopFragment : Fragment() {
    companion object {
        val LOG = Logger.getLogger(SelectStopFragment::class.java.name)
        fun newInstance(): SelectStopFragment =
            SelectStopFragment()
    }
    internal lateinit var mAutoCompleteTextView : AutoCompleteTextView
    internal lateinit var displayNameText : EditText
    internal lateinit var addStopActivity : AddStopActivity
    internal lateinit var mapContainer : View
    internal var map : GoogleMap? = null
    internal var nameToSiteIDs : HashMap<String, Int> = HashMap()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val mainView =  inflater.inflate(R.layout.content_add_stop, container, false)
        addStopActivity = activity as AddStopActivity

        displayNameText = mainView.findViewById(R.id.stop_display_name_text)
        mAutoCompleteTextView = mainView.findViewById(R.id.stop_auto_complete)
        mapContainer = mainView.findViewById(R.id.map_container)
        mapContainer.visibility = View.GONE
        mAutoCompleteTextView.threshold = 1
        return mainView
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

    override fun onStart() {
        super.onStart()
        val config = addStopActivity.config.stopData
        LOG.info("OnStart, set stuff from ${config}")
        mAutoCompleteTextView.setText(config.canonicalName, false)


        val mapFragment = childFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
              mapFragment.getMapAsync{it ->
                  map = it
                  if (config.siteId != 0L) {
                      mapTo(config.lat, config.lng)
                  }
         }
        displayNameText.setText(config.displayName, TextView.BufferType.EDITABLE)
        displayNameText.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(p0: Editable?) {
            }
            override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {
            }
            override fun onTextChanged(p: CharSequence?, p1: Int, p2: Int, p3: Int) {
                addStopActivity.updateStopDataDisplayText()
            }
        })

        if (config.siteId != 0L && !config.canonicalName.isEmpty()) {
            setGreenBg(mAutoCompleteTextView)
        }

        val adapter = ArrayAdapter<String>(
            this.activity!!,
            android.R.layout.simple_dropdown_item_1line, ArrayList()
        )
        mAutoCompleteTextView.setAdapter(adapter)
        mAutoCompleteTextView.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(p0: Editable?) {
            }
            override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {
            }
            override fun onTextChanged(p: CharSequence?, p1: Int, p2: Int, p3: Int) {
                var siteId : Int? = nameToSiteIDs[p.toString()]

                val configuredName = addStopActivity.config.stopData.canonicalName
                if (addStopActivity.config.stopData.siteId != 0L && configuredName == p) {
                    LOG.info("Configured name in autocomplete. Not doing anything.")
                    return
                }
                if (p?.length == 0) {
                    displayNameText.setText("", TextView.BufferType.EDITABLE)
                    return
                }
                if (siteId != null) {
                    LOG.info("Selected $p $siteId")
                    hideKeyboard()
                    setGreenBg(mAutoCompleteTextView)
                    if (p!!.contains("(")) {
                        displayNameText.setText(p.substring(0, p.indexOf("(") - 1), TextView.BufferType.EDITABLE)
                    } else {
                        displayNameText.setText(p, TextView.BufferType.EDITABLE)
                    }
                    addStopActivity.departureAdapter.clear()
                    addStopActivity.departureAdapter.notifyDataSetChanged()
                    addStopActivity.config.clearDeparturesFilter()
                    addStopActivity.linesAdapter.clear()
                    addStopActivity.linesAdapter.notifyDataSetChanged()
                    addStopActivity.config.clearLineFilter()
                    addStopActivity.loadDepsFor(siteId)
                } else {
                    mAutoCompleteTextView.setBackgroundColor((0x00000000).toInt())
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
                                nameToSiteIDs[name] = siteId
                            }
                            mAutoCompleteTextView.performCompletion()
                        },
                        Response.ErrorListener { error -> LOG.severe("Failure to autocomplete $error!") })
                    addStopActivity.requestQueue.add(stringRequest)
                }
            }
        })
    }
}