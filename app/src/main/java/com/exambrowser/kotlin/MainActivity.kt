package com.exambrowser.kotlin

import android.app.ActivityManager
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
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
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
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
    private var controlBar: LinearLayout? = null
    private var killed = false
    private var timeView: TextView? = null
    private var batteryView: TextView? = null
    private var statusBar: LinearLayout? = null
    private var hideNavRunnable: Runnable? = null
    private val handler = Handler(Looper.getMainLooper())
    private val clockUpdater = object : Runnable {
        override fun run() {
            timeView?.text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            handler.postDelayed(this, 30000)
        }
    }

    // QR Scanner launcher
    private val qrLauncher = registerForActivityResult(ScanContract()) { result ->
        if (result.contents != null) {
            inputUrl.setText(result.contents)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            buildHomeScreen()
            if (pinGranted) {
                // Pin already granted from previous session
                Toast.makeText(this, "Pin aktif — siap ujian", Toast.LENGTH_SHORT).show()
            }
            handler.postDelayed({ if (!killed && !isFinishing && !isDestroyed && !pinGranted) showPinDialog() }, 600)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Error: " + e.message, Toast.LENGTH_LONG).show()
            pinGranted = true
        }
    }

    // ============ HOME SCREEN ============

    private fun buildHomeScreen() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#f5f5f5"))
        }
        val scroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            setPadding(dp(20), dp(40), dp(20), dp(20))
        }
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(24), dp(20), dp(20))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE; cornerRadius = dp(12).toFloat()
                setColor(Color.WHITE); setStroke(1, Color.parseColor("#f0f0f0"))
            }
        }
        card.addView(TextView(this).apply {
            text = "Exam Browser"; textSize = 22f; setTextColor(Color.parseColor("#16213e"))
            gravity = Gravity.CENTER; setPadding(0, 0, 0, dp(8))
        })
        card.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1)
                .apply { setMargins(0, dp(8), 0, dp(8)) }
            setBackgroundColor(Color.parseColor("#e0e0e0"))
        })
        val inputRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        }
        inputUrl = EditText(this).apply {
            hint = "Masukan Url atau IP CBT"; setHintTextColor(Color.parseColor("#aaaaaa"))
            setTextColor(Color.BLACK); textSize = 14f
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE; cornerRadius = dp(6).toFloat()
                setColor(Color.parseColor("#f9f9f9")); setStroke(1, Color.parseColor("#e0e0e0"))
            }
            setPadding(dp(14), dp(12), dp(14), dp(12))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        inputUrl.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) { updateStartButton() }
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })
        inputRow.addView(inputUrl)
        inputRow.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(dp(8), 0) })
        // QR Scanner button with real QR scanning
        val qrContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE; cornerRadius = dp(6).toFloat()
                setColor(Color.parseColor("#16213e"))
            }
            setOnClickListener { startQrScanner() }
        }
        qrContainer.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_qr_scanner)
            layoutParams = LinearLayout.LayoutParams(dp(22), dp(22))
        })
        inputRow.addView(qrContainer)
        card.addView(inputRow)
        card.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(14))
        })
        btnStart = TextView(this).apply {
            text = "Mulai Ujian"; gravity = Gravity.CENTER; textSize = 15f
            setPadding(0, dp(14), 0, dp(14))
            setOnClickListener {
                if (!pinGranted) {
                    // Re-pin if somehow lost
                    doLockTask()
                    handler.postDelayed({
                        if (!pinGranted) {
                            Toast.makeText(this@MainActivity, "Setujui pin terlebih dahulu!", Toast.LENGTH_SHORT).show()
                        } else {
                            handleStart()
                        }
                    }, 2000)
                    waitForPin()
                } else {
                    handleStart()
                }
            }
        }
        card.addView(btnStart)
        updateStartButton()
        card.addView(makeIconLabelRow(R.drawable.ic_exit_app, "Keluar Aplikasi", Color.parseColor("#dc2626"), dp(14)) {
            AlertDialog.Builder(this@MainActivity)
                .setTitle("Keluar Aplikasi")
                .setMessage("Apakah Anda yakin ingin keluar dari Exam Browser?")
                .setPositiveButton("Ya, Keluar") { _, _ -> killApp() }.setNegativeButton("Batal", null).show()
        })
        featureToggle = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
            setPadding(0, dp(14), 0, dp(4))
            setOnClickListener { featuresOpen = !featuresOpen; buildFeatureList() }
        }
        chevronIcon = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(14), dp(14)); setImageResource(R.drawable.ic_chevron)
        }
        featureToggle.addView(chevronIcon)
        featureToggle.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(dp(4), 0) })
        featureToggle.addView(TextView(this).apply {
            text = "Fitur Aplikasi"; setTextColor(Color.parseColor("#888888")); textSize = 12f
        })
        card.addView(featureToggle)
        featureList = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(6), 0, dp(6), dp(4))
        }
        buildFeatureList()
        card.addView(featureList)
        container.addView(card)
        scroll.addView(container)
        root.addView(scroll)
        setContentView(root)
    }

    private fun startQrScanner() {
        val options = ScanOptions().apply {
            setDesiredBarcodeFormats(ScanOptions.QR_CODE)
            setPrompt("Arahkan kamera ke QR Code")
            setBeepEnabled(false)
            setOrientationLocked(true)
        }
        qrLauncher.launch(options)
    }

    private fun buildFeatureList() {
        featureList.removeAllViews()
        chevronIcon.rotation = if (featuresOpen) 180f else 0f
        if (!featuresOpen) return
        for ((icon, lbl) in arrayOf(
            Pair(R.drawable.ic_lock, "Kunci perangkat (Lock Task)"),
            Pair(R.drawable.ic_no_screenshot, "Nonaktifkan screenshot"),
            Pair(R.drawable.ic_copy_paste, "Blokir copy/paste"),
            Pair(R.drawable.ic_globe, "Navigasi terbatas (hostname saja)"))) {
            featureList.addView(LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(5), 0, dp(5))
                addView(ImageView(this@MainActivity).apply {
                    setImageResource(icon)
                    layoutParams = LinearLayout.LayoutParams(dp(16), dp(16)).apply { setMargins(0, 0, dp(8), 0) }
                })
                addView(TextView(this@MainActivity).apply {
                    text = lbl; setTextColor(Color.parseColor("#555555")); textSize = 12f
                })
            })
        }
    }

    private fun makeIconLabelRow(iconRes: Int, label: String, color: Int, topMargin: Int, onClick: () -> Unit) =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
            setPadding(0, topMargin, 0, 0); setOnClickListener { onClick() }
            addView(ImageView(this@MainActivity).apply {
                setImageResource(iconRes)
                layoutParams = LinearLayout.LayoutParams(dp(16), dp(16)).apply { setMargins(0, 0, dp(6), 0) }
            })
            addView(TextView(this@MainActivity).apply {
                text = label; setTextColor(color); textSize = 13f
            })
        }

    private fun updateStartButton() {
        val hasText = inputUrl.text.toString().trim().isNotEmpty()
        btnStart.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE; cornerRadius = dp(6).toFloat()
            setColor(if (hasText) Color.parseColor("#16213e") else Color.parseColor("#888888"))
        }
        btnStart.setTextColor(if (hasText) Color.WHITE else Color.parseColor("#cccccc"))
    }

    private fun handleStart() {
        val raw = inputUrl.text.toString().trim()
        if (raw.isEmpty()) { Toast.makeText(this, "Masukkan URL Server CBT terlebih dahulu.", Toast.LENGTH_SHORT).show(); return }
        if (!pinGranted) { Toast.makeText(this, "Pin belum disetujui!", Toast.LENGTH_SHORT).show(); return }
        playAlarm()
        val isIp = Regex("^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}").containsMatchIn(raw)
        examUrl = if (raw.contains("://")) raw else "${if (isIp) "http" else "https"}://$raw"
        buildExamScreen()
    }

    // ============ EXAM SCREEN ============

    private fun buildExamScreen() {
        // Hide navigation bar IMMEDIATELY + periodically
        hideSystemBars()
        startHideNavLoop()
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)

        val root = FrameLayout(this)
        val statusH = dp(24)
        statusBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, statusH).apply { gravity = Gravity.TOP }
            setBackgroundColor(Color.parseColor("#0d1a2d")); setPadding(dp(10), 0, dp(10), 0)
        }
        timeView = TextView(this).apply {
            text = "00:00"; setTextColor(Color.parseColor("#ffffff")); textSize = 11f
        }
        statusBar!!.addView(timeView)
        statusBar!!.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(0, 0, 1f) })
        batteryView = TextView(this).apply {
            text = "${getBatteryLevel()}%"; setTextColor(Color.parseColor("#ffffff")); textSize = 11f
        }
        statusBar!!.addView(batteryView)
        root.addView(statusBar)

        val wv = WebView(this).apply {
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                .apply { topMargin = statusH; bottomMargin = dp(48) }
            settings.javaScriptEnabled = true; settings.domStorageEnabled = true
            settings.allowFileAccess = false; setLayerType(View.LAYER_TYPE_HARDWARE, null)
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: android.webkit.WebResourceRequest) =
                    try {
                        val targetHost = request.url.host ?: return true
                        val initialHost = java.net.URL(examUrl).host
                        targetHost != initialHost && !targetHost.endsWith(".$initialHost")
                    } catch (_: Exception) { true }
                override fun onPageFinished(view: WebView, u: String) {
                    super.onPageFinished(view, u)
                    progressBar?.visibility = View.GONE
                    try { blockCopyPaste() } catch (_: Exception) {}
                    try { injectBlockerJs(view) } catch (_: Exception) {}
                }
            }
            setOnLongClickListener { true }
        }
        webView = wv; root.addView(wv)
        progressBar = View(this).apply {
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(3))
                .apply { gravity = Gravity.TOP; topMargin = statusH }
            setBackgroundColor(Color.parseColor("#e94560"))
        }
        root.addView(progressBar)
        controlBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)).apply { gravity = Gravity.BOTTOM }
            setBackgroundColor(Color.parseColor("#16213e"))
        }
        root.addView(controlBar)
        fun addBtn(icon: Int, lbl: String, onClick: () -> Unit) {
            controlBar?.addView(LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
                setPadding(dp(16), dp(4), dp(16), dp(4)); setOnClickListener { onClick() }
                addView(ImageView(this@MainActivity).apply {
                    setImageResource(icon); layoutParams = LinearLayout.LayoutParams(dp(20), dp(20))
                })
                addView(TextView(this@MainActivity).apply {
                    text = lbl; setTextColor(Color.parseColor("#aaaaaa")); textSize = 9f; gravity = Gravity.CENTER
                })
            })
        }
        addBtn(R.drawable.ic_reload, "Reload") { try { wv.reload() } catch (_: Exception) {} }
        controlBar?.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(0, 0, 1f) })
        addBtn(R.drawable.ic_home, "Home") { try { wv.loadUrl(examUrl) } catch (_: Exception) {} }
        controlBar?.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(0, 0, 1f) })
        addBtn(R.drawable.ic_exit, "Exit") { showExitPinDialog() }
        setContentView(root)
        startPeriodicCheck()
        startClock()
        wv.loadUrl(examUrl)
    }

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

    private fun startHideNavLoop() {
        hideNavRunnable?.let { handler.removeCallbacks(it) }
        hideNavRunnable = object : Runnable {
            override fun run() {
                if (killed) return
                hideSystemBars()
                handler.postDelayed(this, 500)
            }
        }
        handler.postDelayed(hideNavRunnable!!, 500)
    }

    // ============ EXIT PIN ============

    private fun showExitPinDialog() {
        val editPin = EditText(this).apply {
            hint = "Masukkan PIN (1234)"
            setHintTextColor(Color.parseColor("#aaaaaa")); setTextColor(Color.BLACK)
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
        }
        AlertDialog.Builder(this)
            .setTitle("Keluar Ujian")
            .setMessage("Masukkan PIN untuk keluar dari ujian.")
            .setView(editPin)
            .setPositiveButton("OK") { _, _ ->
                if (editPin.text.toString().trim() == "1234") {
                    // NOTE: pinGranted tetap true — agar tidak perlu re-pin saat mulai lagi
                    handler.removeCallbacks(hideNavRunnable ?: return@setPositiveButton)
                    try { window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN) } catch (_: Exception) {}
                    statusBar = null; timeView = null; batteryView = null; webView = null
                    buildHomeScreen()
                } else {
                    Toast.makeText(this, "PIN salah!", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Batal", null).show()
    }

    private fun getBatteryLevel(): Int = try {
        (getSystemService(Context.BATTERY_SERVICE) as BatteryManager)
            .getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    } catch (_: Exception) { 100 }

    private fun startClock() { handler.post(clockUpdater) }

    // ============ PIN / LOCK TASK ============

    private fun showPinDialog() {
        if (isFinishing || isDestroyed) return
        try {
            AlertDialog.Builder(this)
                .setTitle("Peringatan!")
                .setMessage("Anda wajib menyematkan (pin) aplikasi untuk mengerjakan ujian.\n\nJika Anda menolak, aplikasi akan ditutup.")
                .setCancelable(false)
                .setPositiveButton("Saya Setuju") { _, _ -> doLockTask(); waitForPin() }
                .setNegativeButton("Tidak Setuju") { _, _ -> handler.postDelayed({ killApp() }, 100) }
                .show()
        } catch (e: Exception) { e.printStackTrace(); killApp() }
    }

    private fun doLockTask() { hideSystemBars(); try { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) { @Suppress("DEPRECATION") startLockTask() } } catch (_: Exception) {} }

    private fun waitForPin() {
        Thread {
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
    }

    private fun startPeriodicCheck() {
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

    override fun onResume() {
        super.onResume()
        if (pinGranted && !killed) { try { hideSystemBars(); doLockTask() } catch (_: Exception) {} }
    }

    // ============ JS BLOCKER ============

    private fun injectBlockerJs(wv: WebView) {
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
            val resId = resources.getIdentifier("alarm", "raw", packageName); if (resId == 0) return
            val afd = resources.openRawResourceFd(resId) ?: return
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

    override fun onBackPressed() {}
    override fun onKeyDown(keyCode: Int, event: KeyEvent?) = when (keyCode) { KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_HOME, KeyEvent.KEYCODE_APP_SWITCH -> true; else -> super.onKeyDown(keyCode, event) }
    override fun onUserLeaveHint() { if (pinGranted && !killed && webView != null) killApp(); super.onUserLeaveHint() }

    private fun killApp() { if (killed) return; killed = true; try { finishAffinity() } catch (_: Exception) {}; handler.postDelayed({ android.os.Process.killProcess(android.os.Process.myPid()); System.exit(0) }, 300) }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
