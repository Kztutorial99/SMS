package com.android.declock

import android.app.TimePickerDialog
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

class MainActivity : AppCompatActivity() {
    private lateinit var content: FrameLayout
    private lateinit var nav: LinearLayout
    private val handler = Handler(Looper.getMainLooper())
    private val prefs by lazy { getSharedPreferences("clock_prefs", Context.MODE_PRIVATE) }
    private var currentTab = 0
    private var analog = true
    private var timerRunning = false
    private var timerEnd = 0L
    private var timerTotal = 0L
    private var timerEntered = ""
    private var timerDisplay: TextView? = null
    private var stopwatchRunning = false
    private var stopwatchStart = 0L
    private var stopwatchAccum = 0L
    private var stopwatchDisplay: TextView? = null
    private var analogView: View? = null

    private val ticker = object : Runnable {
        override fun run() {
            if (currentTab == 1) analogView?.invalidate()
            if (currentTab == 2 && timerRunning) timerDisplay?.text = formatDuration((timerEnd - System.currentTimeMillis()).coerceAtLeast(0))
            if (currentTab == 3 && stopwatchRunning) stopwatchDisplay?.text = formatStopwatch()
            handler.postDelayed(this, 100)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.BLACK
        window.navigationBarColor = android.graphics.Color.BLACK
        analog = prefs.getBoolean("analog", true)
        buildShell()
        showTab(0)
    }

    override fun onResume() {
        super.onResume(); handler.post(ticker)
        AlarmStore.load(this).filter { it.enabled }.forEach { AlarmStore.schedule(this, it) }
    }
    override fun onPause() { handler.removeCallbacks(ticker); super.onPause() }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun text(s: String, size: Float, color: Int = 0xFFF5F5F5.toInt()) = TextView(this).apply { this.text = s; textSize = size; setTextColor(color); fontFeatureSettings = "tnum" }

    private fun buildShell() {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(0xFF000000.toInt()) }
        content = FrameLayout(this); nav = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER; setBackgroundColor(0xFF000000.toInt()); setPadding(0, dp(6), 0, dp(6)) }
        root.addView(content, LinearLayout.LayoutParams(-1, 0, 1f)); root.addView(nav, LinearLayout.LayoutParams(-1, dp(78))); setContentView(root)
        listOf("⏰\nAlarm", "●\nJam", "⌛\nTimer", "⏱\nStopwatch").forEachIndexed { i, label ->
            val v = text(label, 14f).apply { gravity = Gravity.CENTER }
            v.setOnClickListener { showTab(i) }; nav.addView(v, LinearLayout.LayoutParams(0, -1, 1f))
        }
    }

    private fun showTab(tab: Int) {
        currentTab = tab; content.removeAllViews(); timerDisplay = null; stopwatchDisplay = null; analogView = null
        when (tab) { 0 -> renderAlarms(); 1 -> renderClock(); 2 -> renderTimer(); 3 -> renderStopwatch() }
        for (i in 0 until nav.childCount) (nav.getChildAt(i) as TextView).setTextColor(if (i == tab) 0xFF6E9AEF.toInt() else 0xFF777777.toInt())
    }

    private fun page(title: String): LinearLayout {
        val page = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(0xFF000000.toInt()) }
        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(dp(24), dp(18), dp(18), dp(8)) }
        header.addView(text(title, 54f).apply { includeFontPadding = false }, LinearLayout.LayoutParams(0, dp(72), 1f))
        val menu = text("⋮", 34f, 0xFFBDBDBD.toInt()).apply { gravity = Gravity.CENTER }; menu.setOnClickListener { showSettings() }
        header.addView(menu, LinearLayout.LayoutParams(dp(45), dp(60))); page.addView(header); return page
    }

    private fun renderAlarms() {
        val page = page("Alarm")
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(24), 0, dp(24), dp(8)) }
        val alarms = AlarmStore.load(this).sortedBy { it.hour * 60 + it.minute }
        if (alarms.isEmpty()) list.addView(text("Belum ada alarm", 18f, 0xFF777777.toInt()).apply { gravity = Gravity.CENTER; setPadding(0, dp(80), 0, 0) }, LinearLayout.LayoutParams(-1, dp(150)))
        alarms.forEach { addAlarmRow(list, it) }
        page.addView(ScrollView(this).apply { addView(list) }, LinearLayout.LayoutParams(-1, 0, 1f))
        val fab = text("+", 42f).apply { gravity = Gravity.CENTER; background = circle(0xFF5D8FE8.toInt()); setTextColor(0xFFFFFFFF.toInt()); elevation = dp(8).toFloat() }
        fab.setOnClickListener { alarmEditor(null) }
        page.addView(FrameLayout(this).apply { addView(fab, FrameLayout.LayoutParams(dp(72), dp(72), Gravity.CENTER)) }, LinearLayout.LayoutParams(-1, dp(95)))
        content.addView(page)
    }

    private fun addAlarmRow(parent: LinearLayout, alarm: AlarmItem) {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(22), dp(8), 0, dp(8)) }
        val left = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        left.addView(text(String.format("%02d.%02d", alarm.hour, alarm.minute), 42f, if (alarm.enabled) 0xFFF0F0F0.toInt() else 0xFF555555.toInt()).apply { typeface = Typeface.create("sans-serif-light", 0) })
        left.addView(text(alarm.daysText() + if (alarm.label.isBlank()) "" else " · ${alarm.label}", 16f, if (alarm.enabled) 0xFF777777.toInt() else 0xFF444444.toInt()))
        row.addView(left, LinearLayout.LayoutParams(0, -2, 1f))
        val sw = SwitchCompat(this).apply { isChecked = alarm.enabled }
        sw.setOnCheckedChangeListener { _, checked ->
            val items = AlarmStore.load(this@MainActivity).map { if (it.id == alarm.id) it.copy(enabled = checked) else it }; AlarmStore.save(this@MainActivity, items)
            if (checked) AlarmStore.schedule(this@MainActivity, alarm.copy(enabled = true)) else AlarmStore.cancel(this@MainActivity, alarm); renderAlarms()
        }
        row.addView(sw, LinearLayout.LayoutParams(dp(58), dp(50))); row.setOnClickListener { alarmActions(alarm) }; parent.addView(row)
    }

    private fun alarmActions(alarm: AlarmItem) {
        AlertDialog.Builder(this).setTitle(String.format("%02d.%02d", alarm.hour, alarm.minute)).setItems(arrayOf("Ubah", "Hapus")) { _, which ->
            if (which == 0) alarmEditor(alarm) else { AlarmStore.cancel(this, alarm); AlarmStore.save(this, AlarmStore.load(this).filterNot { it.id == alarm.id }); renderAlarms() }
        }.show()
    }

    private fun alarmEditor(existing: AlarmItem?) {
        val now = Calendar.getInstance(); val base = existing ?: AlarmItem(System.currentTimeMillis(), now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE))
        var h = base.hour; var m = base.minute; var mask = base.repeatMask; var vibrate = base.vibrate
        val wrap = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(6), dp(20), dp(4)) }
        val time = Button(this).apply { text = String.format("%02d:%02d", h, m); textSize = 30f; setTextColor(0xFF6E9AEF.toInt()) }
        time.setOnClickListener { TimePickerDialog(this, { _, hh, mm -> h = hh; m = mm; time.text = String.format("%02d:%02d", hh, mm) }, h, m, true).show() }; wrap.addView(time)
        wrap.addView(text("Ulangi", 14f, 0xFF777777.toInt()).apply { setPadding(0, dp(10), 0, dp(3)) })
        val days = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }; listOf("Sen","Sel","Rab","Kam","Jum","Sab","Min").forEachIndexed { i, d ->
            val c = CheckBox(this).apply { text = d; textSize = 10f; isChecked = mask and (1 shl i) != 0 }; c.setOnCheckedChangeListener { _, on -> mask = if (on) mask or (1 shl i) else mask and (1 shl i).inv() }; days.addView(c, LinearLayout.LayoutParams(0, -2, 1f))
        }; wrap.addView(days)
        val label = EditText(this).apply { hint = "Label"; setSingleLine(true); setText(base.label) }; wrap.addView(label)
        val vib = SwitchCompat(this).apply { text = "Getar"; isChecked = vibrate }; vib.setOnCheckedChangeListener { _, on -> vibrate = on }; wrap.addView(vib)
        val d = AlertDialog.Builder(this).setTitle(if (existing == null) "Tetapkan alarm" else "Ubah alarm").setView(wrap).setNegativeButton("Batal", null).setPositiveButton("Simpan", null).create()
        d.setOnShowListener { d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val item = base.copy(hour = h, minute = m, repeatMask = mask, label = label.text.toString(), vibrate = vibrate, enabled = true); val all = AlarmStore.load(this).toMutableList()
            if (existing == null) all.add(item) else for (i in all.indices) if (all[i].id == existing.id) all[i] = item
            AlarmStore.save(this, all); AlarmStore.schedule(this, item); d.dismiss(); renderAlarms()
        } }; d.show()
    }

    private fun renderClock() {
        val page = page("Jam"); val box = FrameLayout(this); val clock = if (analog) AnalogClockView(this) else DigitalClockView(this); analogView = clock
        box.addView(clock, FrameLayout.LayoutParams(-1, dp(470), Gravity.CENTER)); val date = text(SimpleDateFormat("EEE, dd MMM", Locale("id","ID")).format(Calendar.getInstance().time), 18f, 0xFF777777.toInt()).apply { gravity = Gravity.CENTER }
        box.addView(date, FrameLayout.LayoutParams(-1, dp(50), Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM)); page.addView(box, LinearLayout.LayoutParams(-1, 0, 1f))
        val globe = text("◎", 42f).apply { gravity = Gravity.CENTER; background = circle(0xFF5D8FE8.toInt()) }; globe.setOnClickListener { showTimeZones() }
        page.addView(FrameLayout(this).apply { addView(globe, FrameLayout.LayoutParams(dp(72), dp(72), Gravity.CENTER)) }, LinearLayout.LayoutParams(-1, dp(100))); content.addView(page)
    }

    private fun showTimeZones() { val zones = TimeZone.getAvailableIDs().sorted().toTypedArray(); AlertDialog.Builder(this).setTitle("Zona waktu").setItems(zones) { _, i -> prefs.edit().putString("zone", zones[i]).apply(); renderClock() }.show() }

    private fun renderTimer() {
        val page = page("Timer"); val display = text(if (timerRunning) formatDuration((timerEnd-System.currentTimeMillis()).coerceAtLeast(0)) else if (timerTotal > 0) formatDuration(timerTotal) else formatDigits(timerEntered), 64f).apply { gravity = Gravity.CENTER; typeface = Typeface.create("sans-serif-light", 0) }; timerDisplay = display
        page.addView(display, LinearLayout.LayoutParams(-1, dp(170)))
        val grid = GridLayout(this).apply { columnCount = 3; setPadding(dp(45),0,dp(45),dp(8)) }
        (1..9).forEach { n -> grid.addView(numButton(n.toString()) { if (!timerRunning && timerTotal == 0L && timerEntered.length < 6) { timerEntered += n; display.text = formatDigits(timerEntered) } }) }
        grid.addView(numButton("⌫") { if (!timerRunning && timerTotal == 0L && timerEntered.isNotEmpty()) { timerEntered = timerEntered.dropLast(1); display.text = formatDigits(timerEntered) } })
        grid.addView(numButton("0") { if (!timerRunning && timerTotal == 0L && timerEntered.length < 6) { timerEntered += "0"; display.text = formatDigits(timerEntered) } })
        grid.addView(numButton("00") { if (!timerRunning && timerTotal == 0L && timerEntered.length <= 4) { timerEntered += "00"; display.text = formatDigits(timerEntered) } })
        page.addView(grid, LinearLayout.LayoutParams(-1, 0, 1f))
        val start = Button(this).apply { text = if (timerRunning) "Jeda" else "Mulai" }; val reset = Button(this).apply { text = "Reset" }
        start.setOnClickListener { if (timerRunning) { timerTotal = (timerEnd-System.currentTimeMillis()).coerceAtLeast(0); timerRunning=false } else { if (timerTotal==0L) timerTotal=parseDigits(timerEntered); if (timerTotal>0) { timerEnd=System.currentTimeMillis()+timerTotal; timerRunning=true } }; renderTimer() }
        reset.setOnClickListener { timerRunning=false; timerTotal=0; timerEntered=""; renderTimer() }
        page.addView(LinearLayout(this).apply { gravity=Gravity.CENTER; addView(reset); addView(start) }, LinearLayout.LayoutParams(-1,dp(80))); content.addView(page)
    }

    private fun numButton(s:String, click:()->Unit)=Button(this).apply { text=s; textSize=22f; setOnClickListener{click()}; layoutParams=GridLayout.LayoutParams().apply{width=0;height=dp(62);columnSpec=GridLayout.spec(GridLayout.UNDEFINED,1f)} }
    private fun parseDigits(s:String):Long { val x=s.padStart(6,'0'); return (x.substring(0,2).toLong()*3600+x.substring(2,4).toLong()*60+x.substring(4,6).toLong())*1000 }
    private fun formatDigits(s:String):String { val x=s.padStart(6,'0'); return "${x.substring(0,2)}:${x.substring(2,4)}:${x.substring(4,6)}" }
    private fun formatDuration(ms:Long):String { val sec=ms/1000; return String.format("%02d:%02d:%02d",sec/3600,(sec/60)%60,sec%60) }

    private fun renderStopwatch() {
        val page=page("Stopwatch"); val display=text(formatStopwatch(),58f).apply{gravity=Gravity.CENTER;typeface=Typeface.create("sans-serif-light",0)}; stopwatchDisplay=display; page.addView(display,LinearLayout.LayoutParams(-1,dp(430)))
        val lap=Button(this).apply{text=if(stopwatchRunning)"Lap" else "Reset"}; val start=Button(this).apply{text=if(stopwatchRunning)"Jeda" else "Mulai"}
        lap.setOnClickListener{if(stopwatchRunning)Toast.makeText(this,"Lap: ${formatStopwatch()}",Toast.LENGTH_SHORT).show()else{stopwatchAccum=0;renderStopwatch()}}
        start.setOnClickListener{if(stopwatchRunning){stopwatchAccum+=System.currentTimeMillis()-stopwatchStart;stopwatchRunning=false}else{stopwatchStart=System.currentTimeMillis();stopwatchRunning=true};renderStopwatch()}
        page.addView(LinearLayout(this).apply{gravity=Gravity.CENTER;addView(lap);addView(start)},LinearLayout.LayoutParams(-1,dp(90)));content.addView(page)
    }
    private fun formatStopwatch():String{val ms=stopwatchAccum+if(stopwatchRunning)System.currentTimeMillis()-stopwatchStart else 0;return String.format("%02d:%02d,%02d",ms/60000,(ms/1000)%60,(ms/10)%100)}

    private fun showSettings(){AlertDialog.Builder(this).setTitle("Pengaturan").setItems(arrayOf("Gaya jam","Alarm","Timer","Tentang")){_,w->when(w){0->clockSettings();1->alarmSettings();2->timerSettings();3->showAbout()}}.show()}
    private fun clockSettings(){AlertDialog.Builder(this).setTitle("Gaya").setSingleChoiceItems(arrayOf("Analog","Digital"),if(analog)0 else 1){d,w->analog=w==0;prefs.edit().putBoolean("analog",analog).apply();d.dismiss();if(currentTab==1)showTab(1)}.show()}
    private fun alarmSettings(){AlertDialog.Builder(this).setTitle("Alarm").setItems(arrayOf("Matikan setelah","Volume alarm","Keraskan volume secara bertahap","Tombol Volume & Daya","Hari pertama")){_,w->Toast.makeText(this,arrayOf("10 menit","Volume sistem","Nonaktif","Ingatkan saya nanti","Minggu")[w],Toast.LENGTH_SHORT).show()}.show()}
    private fun timerSettings(){AlertDialog.Builder(this).setTitle("Timer").setItems(arrayOf("Bunyi timer","Keraskan volume secara bertahap","Timer bergetar")){_,w->Toast.makeText(this,if(w==0)"Nada dering default" else if(w==1)"Nonaktif" else "Getar timer",Toast.LENGTH_SHORT).show()}.show()}
    private fun showAbout(){AlertDialog.Builder(this).setTitle("Jam").setMessage("Aplikasi Jam\nAlarm, jam dunia, timer, dan stopwatch.\nVersi 2.0").setPositiveButton("OK",null).show()}
    private fun circle(c:Int)=android.graphics.drawable.GradientDrawable().apply{shape=android.graphics.drawable.GradientDrawable.OVAL;setColor(c)}

    private class AnalogClockView(context:Context):View(context){private val p=Paint(Paint.ANTI_ALIAS_FLAG);override fun onDraw(c:Canvas){val cx=width/2f;val cy=height/2f;val r=min(width,height)*.38f;p.style=Paint.Style.STROKE;p.strokeWidth=2f;p.color=0xFF777777.toInt();c.drawCircle(cx,cy,r,p);p.style=Paint.Style.FILL;p.textAlign=Paint.Align.CENTER;p.textSize=r*.22f;p.color=0xFFF2F2F2.toInt();for(i in 1..12){val a=Math.toRadians(i*30.0-90);c.drawText(i.toString(),cx+cos(a).toFloat()*r*.82f,cy+sin(a).toFloat()*r*.82f+p.textSize/3,p)};val n=Calendar.getInstance();val sec=n.get(Calendar.SECOND);val min=n.get(Calendar.MINUTE)+sec/60f;val hour=n.get(Calendar.HOUR)+min/60f;fun hand(v:Float,s:Float,w:Float){val a=Math.toRadians(v*6.0-90);p.strokeWidth=w;p.strokeCap=Paint.Cap.ROUND;c.drawLine(cx,cy,cx+cos(a).toFloat()*r*s,cy+sin(a).toFloat()*r*s,p)};p.color=0xFFF5F5F5.toInt();hand(hour*5,.48f,7f);hand(min,.68f,6f);p.color=0xFF6E9AEF.toInt();hand(sec,.74f,3f);c.drawCircle(cx,cy,7f,p)}}
    private class DigitalClockView(context:Context):View(context){private val p=Paint(Paint.ANTI_ALIAS_FLAG);override fun onDraw(c:Canvas){val n=Calendar.getInstance();p.textAlign=Paint.Align.CENTER;p.typeface=Typeface.create("sans-serif-light",0);p.textSize=82f*resources.displayMetrics.density;p.color=0xFFF5F5F5.toInt();c.drawText(String.format("%02d:%02d",n.get(Calendar.HOUR_OF_DAY),n.get(Calendar.MINUTE)),width/2f,height/2f,p)}}
}
