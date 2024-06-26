package se.locutus.sl.realtidhem.activity.add_stop

import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.os.Bundle
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
import android.widget.Filter
import android.widget.Filterable
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.snackbar.Snackbar
import se.locutus.proto.Ng
import se.locutus.proto.Ng.SiteId
import se.locutus.sl.realtidhem.R
import se.locutus.sl.realtidhem.widget.isSiteConfigured
import java.util.logging.Logger


fun setGreenBg(view : View) {
    view.setBackgroundColor((0x33484848).toInt())
}

class SelectStopFragment : androidx.fragment.app.Fragment() {
    companion object {
        val LOG = Logger.getLogger(SelectStopFragment::class.java.name)
    }
    internal lateinit var mAutoCompleteTextView : AutoCompleteTextView
    internal lateinit var searchProgress : ProgressBar
    internal lateinit var stopLoadProgressBar : ProgressBar
    internal lateinit var displayNameText : EditText
    internal lateinit var addStopActivity : AddStopActivity
    internal lateinit var selectDepsButton : ExtendedFloatingActionButton
    private lateinit var mapContainer : View
    internal var map : GoogleMap? = null

    lateinit var autoCompleteAdapter : ArrayAdapter<String>
    val autoCompleteSet = HashSet<String>()
    internal var nameToSiteIDs : HashMap<String, SiteId> = HashMap()

    inner class AutoCompleteFilter: Filter(){
        override fun performFiltering(constraint: CharSequence?): FilterResults {
            val results = FilterResults()

            val filteredView = mutableListOf<String>()
            if (!constraint.isNullOrEmpty()) {
                val lowerCase = constraint.toString().lowercase()
                for (it in autoCompleteSet) {
                    val itLowerCase = it.lowercase()
                    if (itLowerCase.contains(lowerCase)) {
                        filteredView.add(it)
                    }
                }
            }
            activity?.runOnUiThread {
                autoCompleteAdapter.clear()
                autoCompleteAdapter.addAll(filteredView)
            }
            results.count = filteredView.size
            return results
        }
        override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
            if (results?.count!! > 0) {
                autoCompleteAdapter.notifyDataSetChanged()
            } else {
                autoCompleteAdapter.notifyDataSetInvalidated()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LOG.info("Fragment create with bundle $savedInstanceState")
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val mainView =  inflater.inflate(R.layout.content_add_stop, container, false)
        addStopActivity = activity as AddStopActivity

        displayNameText = mainView.findViewById(R.id.stop_display_name_text)
        mAutoCompleteTextView = mainView.findViewById(R.id.stop_auto_complete)
        searchProgress = mainView.findViewById(R.id.searchProgressBar)
        stopLoadProgressBar = mainView.findViewById(R.id.stopLoadProgressBar)

        selectDepsButton = mainView.findViewById(R.id.select_deps_fab)
        selectDepsButton.setOnClickListener {
            addStopActivity.tabLayout.getTabAt(1)!!.select()
        }
        if (addStopActivity.window.statusBarColor != 0) {
            selectDepsButton.backgroundTintList = ColorStateList.valueOf(addStopActivity.window.statusBarColor)
        }

        mapContainer = mainView.findViewById(R.id.map_container)
        mapContainer.visibility = View.GONE
        mAutoCompleteTextView.threshold = 2

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
        if (config.canonicalName.isEmpty()) {
            mAutoCompleteTextView.requestFocus()
            val imm = addStopActivity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager?
            imm!!.toggleSoftInput(
                InputMethodManager.SHOW_FORCED,
                InputMethodManager.HIDE_IMPLICIT_ONLY
            )
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
                stopLoadingFinishedAtPosition(config.lat, config.lng)
            }
        }
        autoCompleteAdapter = object : Filterable, ArrayAdapter<String>(
            this.requireActivity(),
            android.R.layout.simple_dropdown_item_1line, ArrayList()
        ) {
            override fun getFilter(): Filter {
                return AutoCompleteFilter()
            }
        }
        mAutoCompleteTextView.setAdapter(autoCompleteAdapter)
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
                    stopLoadProgressBar.visibility = View.VISIBLE
                    addStopActivity.loadDepsFor(siteId)
                } else {
                    mAutoCompleteTextView.setBackgroundColor((0x00000000).toInt())
                    LOG.info("Searching for $p")
                    searchProgress.visibility = View.VISIBLE
                    doNewStopSearchRequest(p)
                }
            }
        })

        return mainView
    }

    private fun doNewStopSearchRequest(p : CharSequence?) {
        val req = Ng.RequestData.newBuilder()
            .setStopSearchRequest(Ng.StopSearchRequest.newBuilder()
                .setMaxResults(10)
                .setUseWideSearch(true)
                .setQuery(p.toString()))
            .build()
        addStopActivity.network.doGenericRequest(req, forceHttp = true) {
                incomingRequestId : Int, responseData: Ng.ResponseData, e: Exception? ->

            searchProgress.visibility = View.INVISIBLE
            if (e != null) {
                Snackbar.make(
                    mAutoCompleteTextView,
                    R.string.error_loading_autocomplete,
                    Snackbar.LENGTH_SHORT
                ).show()
            } else {
                for (stop in responseData.stopSearchResponse.stopDataList) {
                    val name = stop.canonicalName
                    if (!autoCompleteSet.contains(name)) {
                        autoCompleteSet.add(name)
                    }
                    nameToSiteIDs[name] = stop.site
                }
                LOG.info("Found ${responseData.stopSearchResponse.stopDataList.size} items")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    mAutoCompleteTextView.refreshAutoCompleteResults()
                } else {
                    mAutoCompleteTextView.performCompletion()
                }
            }
        }
    }

    private fun bitmapDescriptorFromVector(context : Context, vectorResId : Int) : BitmapDescriptor {
        val vectorDrawable = ContextCompat.getDrawable(context, vectorResId)!!
        vectorDrawable.setBounds(0, 0, vectorDrawable.intrinsicWidth, vectorDrawable.intrinsicHeight)
        val bitmap = Bitmap.createBitmap(vectorDrawable.intrinsicWidth, vectorDrawable.intrinsicHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        vectorDrawable.draw(canvas)
        return BitmapDescriptorFactory.fromBitmap(bitmap)
}

    fun stopLoadingFinishedAtPosition(lat : Double, lng : Double) {
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
        selectDepsButton.show()
        stopLoadProgressBar.visibility = View.GONE
    }

    private fun hideKeyboard() {
        val imm = addStopActivity.getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(mAutoCompleteTextView.windowToken, 0)
    }
}