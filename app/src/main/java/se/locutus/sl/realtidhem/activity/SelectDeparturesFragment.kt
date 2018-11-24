package se.locutus.sl.realtidhem.activity

import android.os.Bundle
import android.support.v4.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ListView
import com.android.volley.Request
import com.android.volley.Response
import com.android.volley.toolbox.StringRequest
import org.json.JSONArray
import org.json.JSONObject
import se.locutus.proto.Ng
import se.locutus.sl.realtidhem.R
import se.locutus.sl.realtidhem.activity.AddStopActivity.Companion.LOG
import java.util.ArrayList

class SelectDeparturesFragment : Fragment() {
    internal lateinit var mDepartureList : ListView
    internal lateinit var departureAdapter : DepartureListAdapter
    internal lateinit var addStopActivity : AddStopActivity
    internal var nameToSiteIDs : HashMap<String, Int> = HashMap()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val mainView =  inflater.inflate(R.layout.content_select_departures, container, false)
        addStopActivity = activity as AddStopActivity
        departureAdapter = DepartureListAdapter(
            this.activity!!,
            ArrayList()
        )
        mDepartureList = mainView.findViewById(R.id.departure_list_view)
        mDepartureList.adapter = departureAdapter

        if (addStopActivity.config.stopData.siteId != 0L) {
            loadDepsFor(addStopActivity.config.stopData.siteId.toInt())
        }
        return mainView
    }

    fun loadDepsFor (siteId : Int) {
        val stopData = Ng.StoredStopData.newBuilder()
        val existing = addStopActivity.config.departuresFilter.departuresList.toSet()
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
                addStopActivity.config.stopData = stopData.build()
            },
            Response.ErrorListener { error -> LOG.severe("Failure to autocomplete $error!") })
        addStopActivity.requestQueue.add(stringRequest)
    }

    companion object {
        fun newInstance(): SelectDeparturesFragment =
            SelectDeparturesFragment()
    }
}