package se.locutus.sl.realtidhem.widget

import android.app.Activity
import android.os.Bundle
import android.support.v4.app.Fragment
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.ListView
import com.android.volley.Request
import com.android.volley.RequestQueue
import com.android.volley.Response
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import org.json.JSONArray
import org.json.JSONObject
import se.locutus.sl.realtidhem.R
import se.locutus.sl.realtidhem.widget.AddStopActivity.Companion.LOG
import java.util.ArrayList

class SelectStopFragment : Fragment() {
    internal lateinit var mAutoCompleteTextView : AutoCompleteTextView
    internal lateinit var addStopActivity : AddStopActivity
    internal var nameToSiteIDs : HashMap<String, Int> = HashMap()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val mainView =  inflater.inflate(R.layout.content_add_stop, container, false)
        addStopActivity = activity as AddStopActivity
        val adapter = ArrayAdapter<String>(
            this.activity!!,
            android.R.layout.simple_dropdown_item_1line, arrayOf()
        )
        mAutoCompleteTextView = mainView.findViewById(R.id.stop_auto_complete)
        mAutoCompleteTextView.setText(addStopActivity.config.stopData.canonicalName, false)
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
                    val imm = addStopActivity.getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.hideSoftInputFromWindow(mAutoCompleteTextView.windowToken, 0)
                    mAutoCompleteTextView.setBackgroundColor((0x3300FF00).toInt())
                    addStopActivity.stopConfigureTabAdapter.selectDeparturesFragment.loadDepsFor(siteId)
                } else {
                    mAutoCompleteTextView.setBackgroundColor((0x00000000).toInt())
                    addStopActivity.config.clearStopData()
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
                            mAutoCompleteTextView.performCompletion()
                        },
                        Response.ErrorListener { error -> LOG.severe("Failure to autocomplete $error!") })
                    addStopActivity.requestQueue.add(stringRequest)
                }
            }
        })
        return mainView
    }

    companion object {
        fun newInstance(): SelectStopFragment = SelectStopFragment()
    }
}