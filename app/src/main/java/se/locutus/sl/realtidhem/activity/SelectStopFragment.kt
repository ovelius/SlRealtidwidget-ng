package se.locutus.sl.realtidhem.activity

import android.app.Activity
import android.os.Bundle
import android.support.v4.app.Fragment
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
import org.json.JSONArray
import org.json.JSONObject
import se.locutus.sl.realtidhem.R
import java.util.ArrayList
import java.util.logging.Logger

class SelectStopFragment : Fragment() {
    companion object {
        val LOG = Logger.getLogger(SelectStopFragment::class.java.name)
        fun newInstance(): SelectStopFragment =
            SelectStopFragment()
    }
    internal lateinit var mAutoCompleteTextView : AutoCompleteTextView
    internal lateinit var displayNameText : EditText
    internal lateinit var addStopActivity : AddStopActivity
    internal var nameToSiteIDs : HashMap<String, Int> = HashMap()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val mainView =  inflater.inflate(R.layout.content_add_stop, container, false)
        addStopActivity = activity as AddStopActivity

        displayNameText = mainView.findViewById(R.id.stop_display_name_text)
        mAutoCompleteTextView = mainView.findViewById(R.id.stop_auto_complete)
        mAutoCompleteTextView.threshold = 1


        return mainView
    }


    override fun onStart() {
        super.onStart()
        val config = addStopActivity.config.stopData
        LOG.info("OnStart, set stuff from ${config}")
        mAutoCompleteTextView.setText(config.canonicalName, false)
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
            mAutoCompleteTextView.setBackgroundColor((0x3300FF00).toInt())
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
                    val imm = addStopActivity.getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.hideSoftInputFromWindow(mAutoCompleteTextView.windowToken, 0)
                    mAutoCompleteTextView.setBackgroundColor((0x3300FF00).toInt())
                    displayNameText.setText(p, TextView.BufferType.EDITABLE)
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