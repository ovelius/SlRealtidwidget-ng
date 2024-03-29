package se.locutus.sl.realtidhem.activity

import android.appwidget.AppWidgetManager
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import com.google.android.material.slider.Slider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import se.locutus.proto.Ng
import se.locutus.sl.realtidhem.R
import se.locutus.sl.realtidhem.events.WidgetTouchHandler
import se.locutus.sl.realtidhem.service.TimeTracker
import se.locutus.sl.realtidhem.service.sortRecordsByTimeAndCutoff
import se.locutus.sl.realtidhem.widget.getWidgetLayoutId
import java.util.logging.Logger
import kotlin.coroutines.CoroutineContext

const val MAX_UPDATE_PERIOD = 100000000
const val DEFAULT_UPDATE_PERIOD = 4
const val DEFAULT_LEARNING_PERIODS = 6
const val DEFAULT_INTERACTIONS_TO_LEARN = 4

fun getInteractionsToLearn(updateSettings : Ng.UpdateSettings) : Int {
    if (updateSettings.interactionsToLearn <= 0) {
        return DEFAULT_INTERACTIONS_TO_LEARN
    }
    return updateSettings.interactionsToLearn
}

fun getLearningPeriods(updateSettings : Ng.UpdateSettings) : Int {
    if (updateSettings.learningPeriods <= 0) {
        return DEFAULT_LEARNING_PERIODS
    }
    return updateSettings.learningPeriods
}

fun getUpdateSequenceLength(updateSettings: Ng.UpdateSettings) : Int {
    if (updateSettings.updateSequenceLength <= 0) {
        return DEFAULT_UPDATE_PERIOD
    }
    return updateSettings.updateSequenceLength
}

class DebugScrollThread(private val theLine: String,
                        private val textView: TextView,
                        private val context: Context,
                        private val scrollSleepMs : Long) :
    WidgetTouchHandler.ScrollThread(-1, RemoteViews(context.packageName, R.layout.widgetlayout_base), theLine, context, scrollSleepMs) {
    override fun updateView(manager: AppWidgetManager, s: String) {
        textView.setText(s)
    }
}

class UpdateModeFragment : androidx.fragment.app.Fragment() {
    companion object {
        val LOG = Logger.getLogger(UpdateModeFragment::class.java.name)
    }
    private lateinit var widgetConfigureActivity : WidgetConfigureActivity
    private lateinit var spinner : Spinner
    private lateinit var updateModeHelpText: TextView
    private lateinit var updatePeriodList : ListView
    private lateinit var updateTextArray : Array<String>

    private lateinit var alwaysUpdateSettings : View
    private lateinit var selfLearningSettings : View

    private lateinit var updateOnUnlock : CheckBox
    private lateinit var updateForever : CheckBox
    private lateinit var updateSequenceLength : EditText

    private lateinit var learnPeriodCount : EditText
    private lateinit var interactionsToLearn : EditText
    private lateinit var speedSlider: Slider
    private lateinit var widgetLine2 : TextView
    private var debugScrollThread : DebugScrollThread? = null
    private var mainHandler = Handler(Looper.getMainLooper())
    private var scrollDebouncer : Runnable? = null
    private var touchCount = 0

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val mainView = inflater.inflate(R.layout.content_update_mode, container, false)
        widgetConfigureActivity = activity as WidgetConfigureActivity
        val updateSettings = widgetConfigureActivity.widgetConfig.updateSettings
        spinner = mainView.findViewById(R.id.update_mode_spinner)
        updateModeHelpText = mainView.findViewById(R.id.update_mode_explain)
        updatePeriodList = mainView.findViewById(R.id.update_period_list_view)
        alwaysUpdateSettings = mainView.findViewById(R.id.always_update_settings)
        selfLearningSettings = mainView.findViewById(R.id.self_learning_settings)
        updateOnUnlock = mainView.findViewById(R.id.checkbox_screen_on)
        learnPeriodCount = mainView.findViewById(R.id.self_update_period_count)
        interactionsToLearn = mainView.findViewById(R.id.self_update_period_threshold)
        updateSequenceLength = mainView.findViewById(R.id.update_sequence_length)
        updateOnUnlock.isChecked = updateSettings.updateWhenScreenOn
        updateOnUnlock.setOnCheckedChangeListener { _, isChecked ->
            val updateSettings = widgetConfigureActivity.widgetConfig.updateSettings.toBuilder()
            updateSettings.updateWhenScreenOn = isChecked
            widgetConfigureActivity.widgetConfig = widgetConfigureActivity.widgetConfig.toBuilder()
                .setUpdateSettings(updateSettings).build()
        }
        updateForever = mainView.findViewById(R.id.checkbox_update_forever)
        if (updateSettings.updateSequenceLength >= MAX_UPDATE_PERIOD) {
            updateForever.isChecked = true
            updateSequenceLength.isEnabled = false
            updateSequenceLength.setText(DEFAULT_UPDATE_PERIOD.toString(), TextView.BufferType.EDITABLE)
        } else {
            updateForever.isChecked = false
            updateSequenceLength.isEnabled = true
            updateSequenceLength.setText(getUpdateSequenceLength(updateSettings).toString(), TextView.BufferType.EDITABLE)
        }
        updateForever.setOnCheckedChangeListener { _, isChecked ->
            refreshUpdatePeriod(isChecked)
        }
        updateSequenceLength.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(p0: Editable?) {}
            override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
            override fun onTextChanged(p: CharSequence?, p1: Int, p2: Int, p3: Int) {
                if (!p.isNullOrBlank()) {
                  refreshUpdatePeriod(updateForever.isChecked)
                }
            }})
        updatePeriodList.adapter = widgetConfigureActivity.adapter
        configureUpdateModeSpinner(mainView)
        updateTextArray = resources.getStringArray(R.array.update_mode_help)
        learnPeriodCount.setText(getLearningPeriods(updateSettings).toString(), TextView.BufferType.EDITABLE)
        learnPeriodCount.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(p0: Editable?) {}
            override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
            override fun onTextChanged(p: CharSequence?, p1: Int, p2: Int, p3: Int) {
                if (!p.isNullOrBlank()) {
                    val updateSettings = widgetConfigureActivity.widgetConfig.updateSettings.toBuilder()
                    updateSettings.learningPeriods = Integer.parseInt(p.toString())
                    widgetConfigureActivity.widgetConfig = widgetConfigureActivity.widgetConfig.toBuilder()
                        .setUpdateSettings(updateSettings).build()
                    updateUpdatePeriod()
                }
            }})
        interactionsToLearn.setText(getInteractionsToLearn(updateSettings).toString(), TextView.BufferType.EDITABLE)
        interactionsToLearn.addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(p0: Editable?) {}
                override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
                override fun onTextChanged(p: CharSequence?, p1: Int, p2: Int, p3: Int) {
                    if (!p.isNullOrBlank()) {
                        val updateSettings = widgetConfigureActivity.widgetConfig.updateSettings.toBuilder()
                        updateSettings.interactionsToLearn = Integer.parseInt(p.toString())
                        widgetConfigureActivity.widgetConfig = widgetConfigureActivity.widgetConfig.toBuilder()
                            .setUpdateSettings(updateSettings).build()
                        updateUpdatePeriod()
                    }
                }})
        widgetLine2 = mainView.findViewById(R.id.widgetline2)
        widgetLine2.setText(R.string.sample_line2)
        mainView.findViewById<TextView>(R.id.widgettag).setText(R.string.sample_stop)
        mainView.findViewById<TextView>(R.id.widgetline1).setText(R.string.sample_line1)
        mainView.findViewById<TextView>(R.id.widgetmin).setText(R.string.sample_minutes)
        mainView.findViewById<TextView>(R.id.widgettag).setOnClickListener{
            sampleScrollThread(getConfiguredScrollDelay(), 10)
        }
        mainView.findViewById<TextView>(R.id.widgetline1).setOnClickListener{
            sampleScrollThread(getConfiguredScrollDelay(), 10)
        }
        mainView.findViewById<TextView>(R.id.widgetline2).setOnClickListener{
            sampleScrollThread(getConfiguredScrollDelay(), 10)
        }

        speedSlider = mainView.findViewById(R.id.speed_slider)
        speedSlider.value = getConfiguredScrollDelay().toFloat()
        speedSlider.addOnChangeListener { slider, value, fromUser ->
            touchCount++
            sampleScrollThread(value.toInt())
        }
        updateUpdatePeriod()
        return mainView
    }

    private fun getConfiguredScrollDelay() : Int {
        return if (widgetConfigureActivity.widgetConfig.updateSettings.scrollThreadStepMs > 0) widgetConfigureActivity.widgetConfig.updateSettings.scrollThreadStepMs else 70
    }

    private fun sampleScrollThread(value : Int, delay : Long = 300) {
        if (scrollDebouncer != null) {
            mainHandler.removeCallbacks(scrollDebouncer!!)
            debugScrollThread?.running = false
            debugScrollThread?.agressiveOff = true
        }

        val updateSettings = widgetConfigureActivity.widgetConfig.updateSettings.toBuilder()
        updateSettings.setScrollThreadStepMs(value)
        widgetConfigureActivity.widgetConfig =
            widgetConfigureActivity.widgetConfig.toBuilder()
                .setUpdateSettings(updateSettings).build()

        scrollDebouncer = Runnable{
            debugScrollThread?.running = false
            debugScrollThread?.agressiveOff = true
            debugScrollThread = DebugScrollThread(
                if (touchCount> 3) getString(R.string.sample_wee) else getString(R.string.sample_line2),
                widgetLine2,
                widgetConfigureActivity,
                value.toLong()
            )
            debugScrollThread?.start()
        }
        mainHandler.postDelayed(scrollDebouncer!!, delay)
    }

    private fun refreshUpdatePeriod(isChecked : Boolean) {
        val updateSettings = widgetConfigureActivity.widgetConfig.updateSettings.toBuilder()
        if (isChecked) {
            updateSettings.updateSequenceLength = MAX_UPDATE_PERIOD
            updateSequenceLength.isEnabled = false
        } else {
            updateSettings.updateSequenceLength = Integer.parseInt(updateSequenceLength.text.toString())
            updateSequenceLength.isEnabled = true
        }
        widgetConfigureActivity.widgetConfig = widgetConfigureActivity.widgetConfig.toBuilder()
            .setUpdateSettings(updateSettings).build()
    }

    private fun setListSize() {
        val adapter = widgetConfigureActivity.adapter
        var totalHeight = 0
        for (i in 0 until adapter.count) {
            val listItem = adapter.getView(i, null, updatePeriodList)
            listItem.measure(0, 0)
            totalHeight += listItem.measuredHeight
        }
        val params = updatePeriodList.layoutParams
        params.height = totalHeight + (updatePeriodList.dividerHeight * (adapter.count - 1))
        updatePeriodList.layoutParams = params
        updatePeriodList.requestLayout()
    }

    private fun updateUpdatePeriod() {
        widgetConfigureActivity.adapter.clear()
        val records = widgetConfigureActivity.getTimeRecords()
        val updateSettings = widgetConfigureActivity.widgetConfig.updateSettings
        val sortedRecords = sortRecordsByTimeAndCutoff(records, getInteractionsToLearn(updateSettings), getLearningPeriods(updateSettings))
        for (record in sortedRecords) {
            widgetConfigureActivity.adapter.add(record)
        }
        widgetConfigureActivity.adapter.notifyDataSetChanged()
        setListSize()
    }

 fun configureUpdateModeSpinner(mainView : View) {
     spinner = mainView.findViewById(R.id.update_mode_spinner)
     val adapter = ArrayAdapter<String>(requireActivity(),
         android.R.layout.simple_spinner_item,
         resources.getStringArray(R.array.update_mode_array))
     adapter.setDropDownViewResource(R.layout.spinner_item)
     spinner.adapter = adapter
     spinner.setSelection(widgetConfigureActivity.widgetConfig.updateSettings.updateModeValue)
     spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
         override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
             LOG.info("selected update mode $position")
             val updateSettings = widgetConfigureActivity.widgetConfig.updateSettings.toBuilder()
             updateSettings.updateModeValue = position
             widgetConfigureActivity.widgetConfig = widgetConfigureActivity.widgetConfig.toBuilder()
                 .setUpdateSettings(updateSettings).build()
             if (position == Ng.UpdateMode.LEARNING_UPDATE_MODE_VALUE) {
                 selfLearningSettings.visibility = View.VISIBLE
                 updateUpdatePeriod()
             } else {
                 selfLearningSettings.visibility = View.GONE
             }
             if (position == Ng.UpdateMode.ALWAYS_UPDATE_MODE_VALUE) {
                 alwaysUpdateSettings.visibility = View.VISIBLE
             } else {
                 alwaysUpdateSettings.visibility = View.GONE
             }
             updateModeHelpText.text = updateTextArray[position]

         }
         override fun onNothingSelected(parent: AdapterView<*>) {
         }

     }
 }
}