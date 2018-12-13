package se.locutus.sl.realtidhem.activity

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.Menu
import android.view.View
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import se.locutus.sl.realtidhem.R
import se.locutus.sl.realtidhem.events.EXTRA_COLOR_THEME
import com.pes.androidmaterialcolorpickerdialog.ColorPicker
import kotlinx.android.synthetic.main.activity_theme.*
import se.locutus.proto.Ng
import se.locutus.sl.realtidhem.events.EXTRA_THEME_CONFIG
import java.util.*
import java.util.logging.Logger


class ThemeActivity : AppCompatActivity() {
    companion object {
        val LOG = Logger.getLogger(ThemeActivity::class.java.name)
    }
    private lateinit var tagText : TextView
    private lateinit var separatorView : View
    private lateinit var line1Text : TextView
    private lateinit var line2Text : TextView
    private lateinit var minText : TextView
    private lateinit var bgLayout : View
    private lateinit var widgetMainView : LinearLayout
    private lateinit var checkBoxBg : CheckBox
    private lateinit var checkBoxMain : CheckBox
    private lateinit var checkBoxText : CheckBox
    private lateinit var checkBoxTagText : CheckBox
    private lateinit var checkBoxSeparator : CheckBox
    private lateinit var bgViewColor : ImageView
    private lateinit var mainViewColor : ImageView
    private lateinit var textViewColor : ImageView
    private lateinit var tagTextViewColor : ImageView
    private lateinit var separatorViewColor : ImageView
    private var themeData = Ng.ThemeData.newBuilder()
    private var stopConfig = Ng.StopConfiguration.newBuilder()
    private var departureResponse = Ng.AllDepaturesResponseData.getDefaultInstance()

    private var color : Int = 0x7fff0000

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_theme)
        setSupportActionBar(theme_toolbar)
        line1Text = findViewById(R.id.widgetline1)
        line2Text = findViewById(R.id.widgetline2)
        minText = findViewById(R.id.widgetmin)
        tagText = findViewById(R.id.widgettag)
        separatorView = findViewById(R.id.widgetseparator)
        bgLayout = findViewById<View>(R.id.widgetbg_layout)
        widgetMainView = findViewById(R.id.widgetcolor)

        checkBoxBg = findViewById(R.id.checkBox_bg)
        checkBoxMain = findViewById(R.id.checkBox_main)
        checkBoxText = findViewById(R.id.checkBox_text)
        checkBoxTagText = findViewById(R.id.checkBox_tag_text)
        checkBoxSeparator = findViewById(R.id.checkBox_separator)

        if (intent.hasExtra(STOP_CONFIG_DATA_KEY)) {
            val stopConfigBuilt = Ng.StopConfiguration.parseFrom(intent.getByteArrayExtra(STOP_CONFIG_DATA_KEY))
            LOG.info("Got stop config $stopConfigBuilt")
            stopConfig = stopConfigBuilt.toBuilder()
            themeData = stopConfigBuilt.themeData.toBuilder()
            title = "${getString(R.string.theme_stop)} ${stopConfig.stopData.displayName}"
            tagText.text = stopConfig.stopData.displayName
            minText.text = "${Random().nextInt(20)} min"
        }

        if (intent.hasExtra(ALL_DEPARTURES_DATA_KEY)) {
            departureResponse = Ng.AllDepaturesResponseData.parseFrom(intent.getByteArrayExtra(ALL_DEPARTURES_DATA_KEY))
        }

        if (intent.hasExtra(EXTRA_COLOR_THEME)) {
            color = intent.getIntExtra(EXTRA_COLOR_THEME, 0x7fff0000)
            setColor(this, null ,intent.getIntExtra(EXTRA_COLOR_THEME, 0))
        }


        mainViewColor = findViewById(R.id.imageView_main_color)
        mainViewColor.setOnClickListener {
            val c = themeData.colorConfig.mainColor
            val cp = ColorPicker(this,Color.alpha(c), Color.red(c), Color.green(c), Color.blue(c))
            cp.enableAutoClose()
            cp.show()
            cp.setCallback {
                val colorConfig = Ng.ColorConfig.newBuilder(themeData.colorConfig)
                colorConfig.mainColor = it
                colorConfig.overrideMainColor = true
                checkBoxMain.isChecked = true
                themeData.colorConfig = colorConfig.build()
                val drawable = ColorDrawable(it)
                mainViewColor.background = drawable
                updateMainColor()
            }
        }

        textViewColor = findViewById(R.id.imageView_text_color)
        textViewColor.setOnClickListener {
            val c = themeData.colorConfig.textColor
            val cp = ColorPicker(this, Color.alpha(c), Color.red(c), Color.green(c), Color.blue(c))
            cp.enableAutoClose()
            cp.show()
            cp.setCallback {
                val colorConfig = Ng.ColorConfig.newBuilder(themeData.colorConfig)
                colorConfig.textColor = it
                colorConfig.overrideTextColor = true
                checkBoxText.isChecked = true
                themeData.colorConfig = colorConfig.build()
                val drawable = ColorDrawable(it)
                textViewColor.background = drawable
                updateTextColor()
            }
        }

        separatorViewColor = findViewById(R.id.imageView_separator_color)
        separatorViewColor.setOnClickListener {
            val c = themeData.colorConfig.middleBarColor
            val cp = ColorPicker(this, Color.alpha(c), Color.red(c), Color.green(c), Color.blue(c))
            cp.enableAutoClose()
            cp.show()
            cp.setCallback {
                val colorConfig = Ng.ColorConfig.newBuilder(themeData.colorConfig)
                colorConfig.middleBarColor = it
                colorConfig.overrideMiddleBarColor = true
                checkBoxSeparator.isChecked = true
                themeData.colorConfig = colorConfig.build()
                val drawable = ColorDrawable(it)
                separatorViewColor.background = drawable
                updateSeparatorColor()
            }
        }

        tagTextViewColor = findViewById(R.id.imageView_tag_text_color)
        tagTextViewColor.setOnClickListener {
            val c = themeData.colorConfig.tagTextColor
            val cp = ColorPicker(this, Color.alpha(c), Color.red(c), Color.green(c), Color.blue(c))
            cp.enableAutoClose()
            cp.show()
            cp.setCallback {
                val colorConfig = Ng.ColorConfig.newBuilder(themeData.colorConfig)
                colorConfig.tagTextColor = it
                colorConfig.overrideTagTextColor = true
                checkBoxTagText.isChecked = true
                themeData.colorConfig = colorConfig.build()
                val drawable = ColorDrawable(it)
                tagTextViewColor.background = drawable
                updateTagTextColor()
            }
        }

        bgViewColor = findViewById(R.id.imageView_bg_color)
        bgViewColor.setOnClickListener {
            val c = themeData.colorConfig.bgColor
            val cp = ColorPicker(this, Color.alpha(c), Color.red(c), Color.green(c), Color.blue(c))
            cp.enableAutoClose()
            cp.show()
            cp.setCallback {
                val colorConfig = Ng.ColorConfig.newBuilder(themeData.colorConfig)
                colorConfig.bgColor = it
                colorConfig.overrideBgColor = true
                checkBoxBg.isChecked = true
                themeData.colorConfig = colorConfig.build()
                val drawable = ColorDrawable(it)
                bgViewColor.background = drawable
                updateBgColor()
            }
        }

        checkBoxBg.setOnClickListener {
            val colorConfig = Ng.ColorConfig.newBuilder(themeData.colorConfig)
            colorConfig.overrideBgColor = (it as CheckBox).isChecked
            themeData.colorConfig = colorConfig.build()
            updateBgColor()
        }
        checkBoxMain.setOnClickListener {
            val colorConfig = Ng.ColorConfig.newBuilder(themeData.colorConfig)
            colorConfig.overrideMainColor = (it as CheckBox).isChecked
            themeData.colorConfig = colorConfig.build()
            updateMainColor()
        }
        checkBoxText.setOnClickListener {
            val colorConfig = Ng.ColorConfig.newBuilder(themeData.colorConfig)
            colorConfig.overrideTextColor = (it as CheckBox).isChecked
            themeData.colorConfig = colorConfig.build()
            updateTextColor()
        }
        checkBoxTagText.setOnClickListener {
            val colorConfig = Ng.ColorConfig.newBuilder(themeData.colorConfig)
            colorConfig.overrideTagTextColor = (it as CheckBox).isChecked
            themeData.colorConfig = colorConfig.build()
            updateTagTextColor()
        }
        checkBoxSeparator.setOnClickListener {
            val colorConfig = Ng.ColorConfig.newBuilder(themeData.colorConfig)
            colorConfig.overrideMiddleBarColor = (it as CheckBox).isChecked
            themeData.colorConfig = colorConfig.build()
            updateSeparatorColor()
        }
        setThemeStateFromConfig()
    }

    private fun setThemeStateFromConfig() {
        checkBoxText.isChecked = themeData.colorConfig.overrideTextColor
        checkBoxMain.isChecked = themeData.colorConfig.overrideMainColor
        checkBoxBg.isChecked = themeData.colorConfig.overrideBgColor
        checkBoxSeparator.isChecked = themeData.colorConfig.overrideMiddleBarColor
        checkBoxTagText.isChecked = themeData.colorConfig.overrideTagTextColor


        val colorConfig = themeData.colorConfig.toBuilder()
        if (!checkBoxTagText.isChecked) {
            colorConfig.tagTextColor = ContextCompat.getColor(this, R.color.baseWidgetTagText)
        }
        if (!checkBoxMain.isChecked) {
            colorConfig.mainColor = color
        }
        if (!checkBoxSeparator.isChecked) {
            colorConfig.middleBarColor = ContextCompat.getColor(this, R.color.baseWidgetGreyerBg)
        }
        if (!checkBoxBg.isChecked) {
            colorConfig.bgColor = ContextCompat.getColor(this, R.color.baseWidgetBg)
        }
        if (!checkBoxText.isChecked) {
            colorConfig.textColor = ContextCompat.getColor(this, R.color.baseWidgetText)
        }
        themeData.colorConfig = colorConfig.build()

        // Set default values here..
        tagTextViewColor.background = ColorDrawable(themeData.colorConfig.tagTextColor)
        bgViewColor.background = ColorDrawable(themeData.colorConfig.bgColor)
        textViewColor.background = ColorDrawable(themeData.colorConfig.textColor)
        mainViewColor.background = ColorDrawable(themeData.colorConfig.mainColor)
        separatorViewColor.background = ColorDrawable(themeData.colorConfig.middleBarColor)

        updateTextColor()
        updateMainColor()
        updateBgColor()
        updateTagTextColor()
        updateSeparatorColor()
    }

    private fun updateTextColor() {
        if (checkBoxText.isChecked) {
            setTextViewsColor(themeData.colorConfig.textColor)
        } else {
            line1Text.setTextColor(ContextCompat.getColor(this, R.color.baseWidgetText))
        }
    }

    private fun updateMainColor() {
        if (checkBoxMain.isChecked) {
            widgetMainView.background = ColorDrawable(themeData.colorConfig.mainColor)
        } else {
            widgetMainView.background = ColorDrawable(color)
        }
    }

    private fun updateSeparatorColor() {
        if (checkBoxSeparator.isChecked) {
            separatorView.background = ColorDrawable(themeData.colorConfig.middleBarColor)
        } else {
            separatorView.background = ContextCompat.getDrawable(this, R.color.baseWidgetGreyerBg)
        }
    }

    private fun updateTagTextColor() {
        if (checkBoxTagText.isChecked) {
            tagText.setTextColor(themeData.colorConfig.tagTextColor)
        } else {
            tagText.setTextColor(ContextCompat.getColor(this, R.color.baseWidgetTagText))
        }
    }

    private fun updateBgColor() {
        if (checkBoxBg.isChecked) {
            bgLayout.background = ColorDrawable(themeData.colorConfig.bgColor)
        } else {
            bgLayout.background = ContextCompat.getDrawable(this, R.color.baseWidgetGreyBg)
        }
    }

    private fun setTextViewsColor(color : Int) {
        line1Text.setTextColor(color)
        line2Text.setTextColor(color)
        minText.setTextColor(color)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.widget_theme_action_bar_menu, menu)
        menu.findItem(R.id.save_widget_action).setOnMenuItemClickListener {_ ->
            val resultIntent = Intent()
            resultIntent.putExtra(EXTRA_THEME_CONFIG, themeData.build().toByteArray())
            setResult(Activity.RESULT_OK, resultIntent)
            finish()
            true
        }
        return true
    }
}
