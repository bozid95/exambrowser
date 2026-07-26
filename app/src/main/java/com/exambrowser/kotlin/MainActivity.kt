package com.exambrowser.kotlin

import android.app.ActivityManager
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var inputUrl: EditText
    private lateinit var btnStart: TextView
    private lateinit var featureToggle: LinearLayout
    private lateinit var featureList: LinearLayout
    private lateinit var chevronIcon: ImageView
    private var featuresOpen = false
    private var pinGranted = false
    private var mediaPlayer: MediaPlayer? = null
    private var clipboardListener: ClipboardManager.OnPrimaryClipChangedListener? = null
    private var examUrl = ""
    private var webView: WebView? = null
    private var progressBar: View? = null
    private var killed = false
    private var timeView: TextView? = null
    private var batteryView: TextView? = null
    private var hideNavRunnable: Runnable? = null
    private val handler = Handler(Looper.getMainLooper())

    private val qrLauncher = registerForActivityResult(ScanContract()) { result ->
        if (result.contents != null) inputUrl.setText(result.contents)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildHome()
        handler.postDelayed({
            if (!killed && !isFinishing && !isDestroyed && !pinGranted) showPinDialog()
        }, 400)
    }

    // ═══════════════════════════════════
    //  HOME SCREEN
    // ═══════════════════════════════════
    private fun buildHome() {
        val root = FrameLayout(this).apply {
            setBackgroundColor(parse("#f8f9fa"))
            layoutParams = matchParent()
        }
        val scroll = ScrollView(this).apply { layoutParams = matchParent() }
        val wrapper = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(20), dp(20), dp(20), dp(24))
        }

        // ── Card ──
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(28), dp(24), dp(24))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(20).toFloat()
                setColor(Color.WHITE)
                setStroke(1, parse("#e5e7eb"))
            }
        }

        // Title
        card.addView(makeText("Exam Browser", 24f, "#111827", Gravity.CENTER).apply {
            setPadding(0, 0, 0, dp(4))
        })
        // Subtitle
        card.addView(makeText("Aplikasi Ujian Terkunci untuk CBT", 12f, "#9ca3af", Gravity.CENTER).apply {
            setPadding(0, 0, 0, dp(16))
        })

        // Divider
        card.addView(makeDivider())

        // Input row
        val inputRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        inputUrl = EditText(this).apply {
            hint = "Masukan Url atau IP CBT"
            setHintTextColor(parse("#9ca3af"))
            setTextColor(Color.BLACK)
            textSize = 14f
            background = roundedRect("#f9fafb", 10, 1, "#e5e7eb")
            setPadding(dp(14), dp(13), dp(14), dp(13))
            layoutParams = LinearLayout.LayoutParams(0, dp(48), 1f)
        }
        inputUrl.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) { updateStartButton() }
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })
        inputRow.addView(inputUrl)
        inputRow.addView(space(8, 0))

        // QR button
        val qrBtn = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = roundedRect("#111827", 10, 0, null)
            setOnClickListener { startQrScanner() }
        }
        val qrIcon = ImageView(this).apply {
            setImageResource(R.drawable.ic_qr_scanner)
            layoutParams = LinearLayout.LayoutParams(dp(24), dp(24))
        }
        qrBtn.addView(qrIcon)
        inputRow.addView(qrBtn)
        card.addView(inputRow)
        card.addView(space(0, 18))

        // Mulai Ujian
        btnStart = makeText("Mulai Ujian", 15f, "#ffffff", Gravity.CENTER)
        btnStart.setPadding(0, dp(14), 0, dp(14))
        btnStart.setOnClickListener {
            if (!pinGranted) {
                doLockTask()
                waitForPin()
                handler.postDelayed({
                    if (!pinGranted) toast("Setujui pin terlebih dahulu!")
                    else startExam()
                }, 2000)
            } else startExam()
        }
        card.addView(btnStart)
        updateStartButton()

        // Fitur Aplikasi (collapse)
        card.addView(space(0, 18))

        featureToggle = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(4), 0, dp(4))
            setOnClickListener { featuresOpen = !featuresOpen; buildFeatureList() }
        }
        chevronIcon = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(14), dp(14))
            setImageResource(R.drawable.ic_chevron)
            setColorFilter(parse("#9ca3af"))
        }
        featureToggle.addView(chevronIcon)
        featureToggle.addView(space(4, 0))
        featureToggle.addView(makeText("Fitur Aplikasi", 12f, "#9ca3af"))
        card.addView(featureToggle)

        featureList = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(6), 0, dp(6), 0)
        }
        buildFeatureList()
        card.addView(featureList)

        // Developer credit
        card.addView(space(0, 20))
        card.addView(makeText("Developed by Maswa, S.Pd.", 10f, "#d1d5db", Gravity.CENTER))

        wrapper.addView(card)
        wrapper.addView(space(0, 24))

        // Exit row
        val exitRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setOnClickListener {
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("Keluar Aplikasi")
                    .setMessage("Apakah Anda yakin ingin keluar?")
                    .setPositiveButton("Ya, Keluar") { _, _ -> killApp() }
                    .setNegativeButton("Batal", null).show()
            }
        }
        val exitIcon = ImageView(this).apply {
            setImageResource(R.drawable.ic_exit_app)
            layoutParams = LinearLayout.LayoutParams(dp(16), dp(16))
            setColorFilter(parse("#dc2626"))
        }
        exitRow.addView(exitIcon)
        exitRow.addView(space(6, 0))
        exitRow.addView(makeText("Keluar Aplikasi", 13f, "#dc2626"))
        wrapper.addView(exitRow)

        scroll.addView(wrapper)
        root.addView(scroll)
        setContentView(root)
    }

    private fun buildFeatureList() {
        featureList.removeAllViews()
        chevronIcon.rotation = if (featuresOpen) 180f else 0f
        if (!featuresOpen) return

        val features = listOf(
            R.drawable.ic_lock to "Kunci perangkat (Lock Task) — Mencegah akses keluar aplikasi",
            R.drawable.ic_no_screenshot to "Nonaktifkan screenshot — Mencegah tangkapan layar",
            R.drawable.ic_copy_paste to "Blokir copy/paste — Mencegah kecurangan ujian",
            R.drawable.ic_globe to "Navigasi terbatas — Hanya hostname yang sama",
            R.drawable.ic_qr_scanner to "QR Scanner — Pindai QR Code untuk URL ujian",
            R.drawable.ic_music to "Audio Alarm — Alarm berbunyi saat ujian dimulai",
            R.drawable.ic_pin_exit to "PIN Exit — PIN 1234 untuk keluar dari ujian",
            R.drawable.ic_statusbar to "Status Bar — Jam & baterai selalu terlihat"
        )
        for ((icon, label) in features) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, dp(5), 0, dp(5))
            }
            val iconView = ImageView(this).apply {
                setImageResource(icon)
                layoutParams = LinearLayout.LayoutParams(dp(18), dp(18))
                setColorFilter(parse("#6b7280"))
            }
            row.addView(iconView)
            row.addView(makeText(label, 11f, "#6b7280"))
            featureList.addView(row)
        }
    }

    private fun updateStartButton() {
        val active = inputUrl.text.toString().trim().isNotEmpty()
        btnStart.background = roundedRect(if (active) "#111827" else "#9ca3af", 12, 0, null)
        btnStart.setTextColor(if (active) Color.WHITE else parse("#e5e7eb"))
    }

    private fun startQrScanner() {
        qrLauncher.launch(ScanOptions().apply {
            setDesiredBarcodeFormats(ScanOptions.QR_CODE)
            setPrompt("Arahkan kamera ke QR Code")
            setBeepEnabled(false)
            setOrientationLocked(true)
        })
    }

    // ═══════════════════════════════════
    //  EXAM SCREEN
    // ═══════════════════════════════════
    private fun buildExam() {
        hideSystemBars()
        startHideLoop()
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)

        val root = FrameLayout(this)
        val statusH = dp(26)

        // Custom status bar
        val sb = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = FrameLayout.LayoutParams(MATCH, statusH).apply { gravity = Gravity.TOP }
            setBackgroundColor(parse("#0f172a"))
            setPadding(dp(12), 0, dp(12), 0)
        }
        timeView = makeText("00:00", 12f, "#ffffff")
        sb.addView(timeView)
        sb.addView(space(0, 0, 1f))
        batteryView = makeText("100%", 12f, "#ffffff")
        sb.addView(batteryView)
        root.addView(sb)

        // WebView
        val wv = WebView(this).apply {
            layoutParams = FrameLayout.LayoutParams(MATCH, MATCH).apply {
                topMargin = statusH; bottomMargin = dp(52)
            }
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(v: WebView, r: android.webkit.WebResourceRequest) = try {
                    val th = r.url.host ?: return true
                    val ih = java.net.URL(examUrl).host
                    th == ih || th.endsWith(".$ih")
                } catch (_: Exception) { true }
                override fun onPageFinished(v: WebView, u: String) {
                    super.onPageFinished(v, u)
                    progressBar?.visibility = View.GONE
                    blockCopyPaste()
                    injectJs(v)
                }
            }
            setOnLongClickListener { true }
        }
        webView = wv
        root.addView(wv)

        // Progress bar
        progressBar = View(this).apply {
            layoutParams = FrameLayout.LayoutParams(MATCH, dp(3)).apply {
                gravity = Gravity.TOP; topMargin = statusH
            }
            setBackgroundColor(parse("#ef4444"))
        }
        root.addView(progressBar)

        // Control bar
        val cb = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(MATCH, dp(52)).apply { gravity = Gravity.BOTTOM }
            setBackgroundColor(parse("#111827"))
        }
        fun addCtrl(icon: Int, lbl: String, cl: () -> Unit) {
            val btn = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(16), dp(4), dp(16), dp(4))
                setOnClickListener { cl() }
            }
            btn.addView(ImageView(this).apply {
                setImageResource(icon)
                layoutParams = LinearLayout.LayoutParams(dp(22), dp(22))
            })
            btn.addView(makeText(lbl, 9f, "#9ca3af", Gravity.CENTER))
            cb.addView(btn)
        }
        addCtrl(R.drawable.ic_reload, "Reload") { wv.reload() }
        cb.addView(space(0, 0, 1f))
        addCtrl(R.drawable.ic_home, "Home") { wv.loadUrl(examUrl) }
        cb.addView(space(0, 0, 1f))
        addCtrl(R.drawable.ic_exit, "Exit") { showExitPinDialog() }
        root.addView(cb)

        setContentView(root)
        startWatchdog()
        startClock()
        wv.loadUrl(examUrl)
    }

    // ═══════════════════════════════════
    //  DIALOGS
    // ═══════════════════════════════════
    private fun showPinDialog() {
        if (isFinishing || isDestroyed) return
        AlertDialog.Builder(this)
            .setTitle("Peringatan!")
            .setMessage("Anda wajib menyematkan (pin) aplikasi untuk mengerjakan ujian.\n\nJika Anda menolak, aplikasi akan ditutup.")
            .setCancelable(false)
            .setPositiveButton("Saya Setuju") { _, _ -> doLockTask(); waitForPin() }
            .setNegativeButton("Tidak Setuju") { _, _ -> handler.postDelayed({ killApp() }, 150) }
            .show()
    }

    private fun showExitPinDialog() {
        val edit = EditText(this).apply {
            hint = "Masukkan PIN (1234)"
            setHintTextColor(parse("#9ca3af"))
            setTextColor(Color.BLACK)
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = roundedRect("#f9fafb", 10, 1, "#e5e7eb")
        }
        AlertDialog.Builder(this)
            .setTitle("Keluar Ujian")
            .setMessage("Masukkan PIN untuk keluar.")
            .setView(edit)
            .setPositiveButton("OK") { _, _ ->
                if (edit.text.toString().trim() == "1234") {
                    stopHideLoop()
                    window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
                    webView = null; timeView = null; batteryView = null
                    buildHome()
                } else toast("PIN salah!")
            }
            .setNegativeButton("Batal", null).show()
    }

    // ═══════════════════════════════════
    //  LOCK TASK
    // ═══════════════════════════════════
    private fun doLockTask() {
        hideSystemBars()
        try { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) { @Suppress("DEPRECATION") startLockTask() } } catch (_: Exception) {}
    }

    private fun waitForPin() = Thread {
        val start = System.currentTimeMillis()
        while (!killed) {
            if (System.currentTimeMillis() - start > 15000) { pinGranted = true; return@Thread }
            try {
                val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                if (am.isInLockTaskMode || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && am.lockTaskModeState == ActivityManager.LOCK_TASK_MODE_LOCKED)) {
                    pinGranted = true; return@Thread
                }
            } catch (_: Exception) { pinGranted = true; return@Thread }
            Thread.sleep(500)
        }
    }.start()

    // ═══════════════════════════════════
    //  SYSTEM UI HIDE
    // ═══════════════════════════════════
    private fun hideSystemBars() {
        try {
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window.setDecorFitsSystemWindows(false)
                window.insetsController?.apply {
                    hide(android.view.WindowInsets.Type.navigationBars())
                    systemBarsBehavior = android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }
            }
        } catch (_: Exception) {}
    }

    private fun startHideLoop() {
        stopHideLoop()
        hideNavRunnable = object : Runnable {
            override fun run() { if (!killed) { hideSystemBars(); handler.postDelayed(this, 500) } }
        }
        handler.postDelayed(hideNavRunnable!!, 500)
    }

    private fun stopHideLoop() {
        hideNavRunnable?.let { handler.removeCallbacks(it) }; hideNavRunnable = null
    }

    // ═══════════════════════════════════
    //  HELPERS
    // ═══════════════════════════════════
    private fun startExam() {
        val raw = inputUrl.text.toString().trim()
        playAlarm()
        val isIp = Regex("^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}").containsMatchIn(raw)
        examUrl = if (raw.contains("://")) raw else "${if (isIp) "http" else "https"}://$raw"
        buildExam()
    }

    private fun startClock() {
        handler.post(object : Runnable {
            override fun run() {
                timeView?.text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
                batteryView?.text = "${getBattery()}%"
                handler.postDelayed(this, 30000)
            }
        })
    }

    private fun getBattery() = try {
        (getSystemService(Context.BATTERY_SERVICE) as BatteryManager)
            .getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    } catch (_: Exception) { 100 }

    private fun startWatchdog() {
        handler.postDelayed(object : Runnable {
            override fun run() {
                if (killed || !pinGranted) return
                try {
                    val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                        !am.isInLockTaskMode && am.lockTaskModeState != ActivityManager.LOCK_TASK_MODE_LOCKED) { killApp(); return }
                } catch (_: Exception) {}
                handler.postDelayed(this, 1000)
            }
        }, 1000)
    }

    private fun injectJs(wv: WebView) {
        try { wv.evaluateJavascript("(function(){if(window.__exambrowser_blocked)return;window.__exambrowser_blocked=true;var css='*{user-select:none!important;-webkit-user-select:none!important;-webkit-touch-callout:none!important}'+'input,textarea,[contenteditable]{user-select:auto!important;-webkit-user-select:auto!important}';var s=document.createElement('style');s.innerHTML=css;document.head.appendChild(s);document.addEventListener('copy',function(e){e.preventDefault();return false},true);document.addEventListener('cut',function(e){e.preventDefault();return false},true);document.addEventListener('paste',function(e){e.preventDefault();return false},true);document.addEventListener('selectstart',function(e){var t=(e.target.tagName||'').toLowerCase();if(t==='input'||t==='textarea'||e.target.isContentEditable)return;e.preventDefault();return false},true);document.addEventListener('contextmenu',function(e){e.preventDefault();return false},true);document.addEventListener('touchstart',function(e){if(e.touches.length>1)e.preventDefault()},{passive:false});})();", null) } catch (_: Exception) {} }

    private fun blockCopyPaste() {
        try {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager; cm.clearPrimaryClip()
            if (clipboardListener == null) { clipboardListener = ClipboardManager.OnPrimaryClipChangedListener { try { cm.clearPrimaryClip() } catch (_: Exception) {} }; cm.addPrimaryClipChangedListener(clipboardListener!!) }
        } catch (_: Exception) {}
    }

    private fun playAlarm() {
        try {
            (getSystemService(Context.AUDIO_SERVICE) as AudioManager).apply {
                setStreamVolume(AudioManager.STREAM_MUSIC, getStreamMaxVolume(AudioManager.STREAM_MUSIC), 0)
                setStreamVolume(AudioManager.STREAM_ALARM, getStreamMaxVolume(AudioManager.STREAM_ALARM), 0)
            }
            val rid = resources.getIdentifier("alarm", "raw", packageName); if (rid == 0) return
            val afd = resources.openRawResourceFd(rid) ?: return
            mediaPlayer?.release(); mediaPlayer = null
            mediaPlayer = MediaPlayer().apply {
                setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                setVolume(1f, 1f)
                setOnPreparedListener { try { afd.close() } catch (_: Exception) {}; start() }
                setOnCompletionListener { release(); mediaPlayer = null }
                setOnErrorListener { _, _, _ -> try { afd.close() } catch (_: Exception) {}; release(); mediaPlayer = null; true }
                prepareAsync()
            }
        } catch (_: Exception) {}
    }

    /* ── Micro helpers ── */
    private fun makeText(t: String, size: Float, color: String, gravity: Int = -1) = TextView(this).apply {
        text = t; textSize = size; setTextColor(parse(color))
        if (gravity >= 0) this.gravity = gravity
    }

    private fun makeDivider() = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(MATCH, 1).apply { setMargins(0, dp(4), 0, dp(10)) }
        setBackgroundColor(parse("#f3f4f6"))
    }

    private fun roundedRect(color: String, radius: Int, strokeW: Int, strokeC: String?) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE; cornerRadius = dp(radius).toFloat(); setColor(parse(color))
        if (strokeW > 0 && strokeC != null) setStroke(dp(strokeW), parse(strokeC))
    }

    private fun space(w: Int, h: Int, weight: Float = 0f) = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(dp(w), if (h > 0) dp(h) else 0, weight)
    }

    private fun matchParent() = LinearLayout.LayoutParams(MATCH, MATCH)
    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_SHORT).show()
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun parse(c: String) = Color.parseColor(c)
    private val MATCH = ViewGroup.LayoutParams.MATCH_PARENT

    override fun onResume() { super.onResume(); if (pinGranted && !killed) { try { hideSystemBars(); doLockTask() } catch (_: Exception) {} } }
    override fun onBackPressed() {}
    override fun onKeyDown(kc: Int, ev: KeyEvent?) = when (kc) { KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_HOME, KeyEvent.KEYCODE_APP_SWITCH -> true; else -> super.onKeyDown(kc, ev) }
    override fun onUserLeaveHint() { if (pinGranted && !killed && webView != null) killApp(); super.onUserLeaveHint() }
    private fun killApp() { if (killed) return; killed = true; try { finishAffinity() } catch (_: Exception) {}; handler.postDelayed({ android.os.Process.killProcess(android.os.Process.myPid()); System.exit(0) }, 300) }
}
