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
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

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

    private val colorPrimary = Color.parseColor("#16213e")
    private val colorAccent = Color.parseColor("#e94560")
    private val colorWhite = Color.WHITE
    private val colorMuted = Color.parseColor("#aaa")
    private val colorBg = Color.parseColor("#f5f5f5")
    private val colorCard = Color.WHITE
    private val colorBtnActive = Color.parseColor("#16213e")
    private val colorBtnDisabled = Color.parseColor("#888888")
    private val colorDivider = Color.parseColor("#e0e0e0")
    private val colorFeatureText = Color.parseColor("#555")
    private val colorRed = Color.parseColor("#dc2626")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildHomeScreen()
        showPinDialog()
    }

    // ============ HOME SCREEN ============

    private fun buildHomeScreen() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(colorBg)
            gravity = Gravity.CENTER
        }
        val scroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(40), dp(16), dp(16))
            gravity = Gravity.CENTER_HORIZONTAL
        }

        // Card
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(20))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(12).toFloat()
                setColor(colorCard)
                setStroke(1, Color.parseColor("#f0f0f0"))
            }
        }

        // Title
        card.addView(TextView(this).apply {
            text = "Exam Browser"
            textSize = 22f
            setTextColor(colorPrimary)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(8))
        })

        // Divider
        card.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 1
            ).apply { setMargins(0, dp(8), 0, dp(8)) }
            setBackgroundColor(colorDivider)
        })

        // Input row
        val inputRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        inputUrl = EditText(this).apply {
            hint = "Masukan Url atau IP CBT"
            setHintTextColor(colorMuted)
            setTextColor(Color.BLACK)
            textSize = 14f
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(6).toFloat()
                setColor(Color.parseColor("#f9f9f9"))
                setStroke(1, Color.parseColor("#e0e0e0"))
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

        // QR Scanner button with VectorDrawable icon
        val qrContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(6).toFloat()
                setColor(colorPrimary)
            }
            setOnClickListener {
                Toast.makeText(this@MainActivity, "QR Scanner — coming soon", Toast.LENGTH_SHORT).show()
            }
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

        // Mulai Ujian
        btnStart = TextView(this).apply {
            text = "Mulai Ujian"
            gravity = Gravity.CENTER
            textSize = 15f
            setPadding(0, dp(14), 0, dp(14))
            setOnClickListener { handleStart() }
        }
        card.addView(btnStart)
        updateStartButton()

        // Keluar Aplikasi row
        card.addView(makeIconLabelRow(
            R.drawable.ic_exit_app, "Keluar Aplikasi", colorRed, dp(14)
        ) {
            AlertDialog.Builder(this@MainActivity)
                .setTitle("Keluar Aplikasi")
                .setMessage("Apakah Anda yakin ingin keluar dari Exam Browser?")
                .setPositiveButton("Ya, Keluar") { _, _ -> killApp() }
                .setNegativeButton("Batal", null)
                .show()
        })

        // Fitur Aplikasi collapse
        featureToggle = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(16), 0, dp(6))
            setOnClickListener {
                featuresOpen = !featuresOpen
                buildFeatureList()
            }
        }
        chevronIcon = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(14), dp(14))
            setImageResource(R.drawable.ic_chevron)
        }
        featureToggle.addView(chevronIcon)
        featureToggle.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(dp(4), 0) })
        featureToggle.addView(TextView(this).apply {
            text = "Fitur Aplikasi"
            setTextColor(Color.parseColor("#888"))
            textSize = 12f
        })
        card.addView(featureToggle)

        featureList = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(6), 0, dp(6), dp(4))
        }
        buildFeatureList()
        card.addView(featureList)

        container.addView(card)
        scroll.addView(container)
        root.addView(scroll)
        setContentView(root)
    }

    private fun makeIconLabelRow(iconRes: Int, label: String, color: Int, topMargin: Int, onClick: () -> Unit): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, topMargin, 0, 0)
            setOnClickListener { onClick() }
            addView(ImageView(this@MainActivity).apply {
                setImageResource(iconRes)
                layoutParams = LinearLayout.LayoutParams(dp(16), dp(16)).apply { setMargins(0, 0, dp(6), 0) }
            })
            addView(TextView(this@MainActivity).apply {
                text = label
                setTextColor(color)
                textSize = 13f
            })
        }
    }

    private fun buildFeatureList() {
        featureList.removeAllViews()
        chevronIcon.rotation = if (featuresOpen) 180f else 0f
        if (!featuresOpen) return

        val items = arrayOf(
            Pair(R.drawable.ic_lock, "Kunci perangkat (Lock Task)"),
            Pair(R.drawable.ic_no_screenshot, "Nonaktifkan screenshot"),
            Pair(R.drawable.ic_copy_paste, "Blokir copy/paste"),
            Pair(R.drawable.ic_globe, "Navigasi terbatas (hostname saja)")
        )
        for ((icon, label) in items) {
            featureList.addView(LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, dp(5), 0, dp(5))
                addView(ImageView(this@MainActivity).apply {
                    setImageResource(icon)
                    layoutParams = LinearLayout.LayoutParams(dp(16), dp(16)).apply { setMargins(0, 0, dp(8), 0) }
                })
                addView(TextView(this@MainActivity).apply {
                    text = label
                    setTextColor(colorFeatureText)
                    textSize = 12f
                })
            })
        }
    }

    private fun updateStartButton() {
        val hasText = inputUrl.text.toString().trim().isNotEmpty()
        btnStart.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(6).toFloat()
            setColor(if (hasText) colorBtnActive else colorBtnDisabled)
        }
        btnStart.setTextColor(if (hasText) colorWhite else Color.parseColor("#cccccc"))
    }

    private fun handleStart() {
        val raw = inputUrl.text.toString().trim()
        if (raw.isEmpty()) {
            Toast.makeText(this, "Masukkan URL Server CBT terlebih dahulu.", Toast.LENGTH_SHORT).show()
            return
        }
        if (!pinGranted) {
            Toast.makeText(this, "Pin belum disetujui!", Toast.LENGTH_SHORT).show()
            return
        }
        playAlarm()
        val isIp = Regex("^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}").containsMatchIn(raw)
        examUrl = if (raw.contains("://")) raw else "${if (isIp) "http" else "https"}://$raw"
        buildExamScreen()
    }

    // ============ PIN DIALOG ============

    private fun showPinDialog() {
        AlertDialog.Builder(this)
            .setTitle("Peringatan!")
            .setMessage("Anda wajib menyematkan (pin) aplikasi untuk mengerjakan ujian.\n\nJika Anda menolak, aplikasi akan ditutup.")
            .setCancelable(false)
            .setPositiveButton("Saya Setuju") { _, _ -> doLockTask(); waitForPin() }
            .setNegativeButton("Tidak Setuju") { _, _ -> killApp() }
            .show()
    }

    // ============ EXAM SCREEN ============

    private fun buildExamScreen() {
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        val root = FrameLayout(this)
        val safeTop = dp(12)

        val wv = WebView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            ).apply { setMargins(0, safeTop, 0, dp(48)) }
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
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
                    blockCopyPaste()
                    injectBlockerJs(view)
                }
            }
            setOnLongClickListener { true }
        }
        webView = wv

        progressBar = View(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(3)
            ).apply { gravity = Gravity.TOP; topMargin = safeTop }
            setBackgroundColor(colorAccent)
        }

        // ControlBar with VectorDrawable icons
        controlBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48)
            ).apply { gravity = Gravity.BOTTOM }
            setBackgroundColor(colorPrimary)
        }

        fun addCtrl(icon: Int, label: String, onClick: () -> Unit) {
            val btn = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(16), dp(4), dp(16), dp(4))
                setOnClickListener { onClick() }
                addView(ImageView(this@MainActivity).apply {
                    setImageResource(icon)
                    layoutParams = LinearLayout.LayoutParams(dp(20), dp(20))
                })
                addView(TextView(this@MainActivity).apply {
                    text = label
                    setTextColor(Color.parseColor("#aaa"))
                    textSize = 9f
                    gravity = Gravity.CENTER
                })
            }
            controlBar!!.addView(btn)
        }
        addCtrl(R.drawable.ic_reload, "Reload") { wv.reload() }
        controlBar!!.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(0, 0, 1f) })
        addCtrl(R.drawable.ic_home, "Home") { wv.loadUrl(examUrl) }
        controlBar!!.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(0, 0, 1f) })
        addCtrl(R.drawable.ic_exit, "Exit") {
            pinGranted = false
            window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
            buildHomeScreen()
        }

        root.addView(wv)
        root.addView(progressBar)
        root.addView(controlBar)
        setContentView(root)

        startPeriodicCheck()
        wv.loadUrl(examUrl)
    }

    // ============ PIN / LOCK TASK ============

    private fun doLockTask() {
        try {
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                @Suppress("DEPRECATION") startLockTask()
            }
        } catch (_: Exception) {}
    }

    private fun waitForPin() {
        Thread {
            val start = System.currentTimeMillis()
            while (true) {
                if (System.currentTimeMillis() - start > 15000) { killApp(); return@Thread }
                try {
                    val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                    if (am.isInLockTaskMode || am.lockTaskModeState == ActivityManager.LOCK_TASK_MODE_LOCKED) {
                        pinGranted = true; return@Thread
                    }
                } catch (_: Exception) {}
                Thread.sleep(500)
            }
        }.start()
    }

    private fun startPeriodicCheck() {
        val runnable = object : Runnable {
            override fun run() {
                if (!pinGranted) return
                try {
                    val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                    if (!am.isInLockTaskMode && am.lockTaskModeState != ActivityManager.LOCK_TASK_MODE_LOCKED) {
                        killApp(); return
                    }
                } catch (_: Exception) { killApp(); return }
                window.decorView.postDelayed(this, 1000)
            }
        }
        window.decorView.postDelayed(runnable, 1000)
    }

    override fun onResume() {
        super.onResume()
        if (pinGranted) {
            try {
                val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                if (!am.isInLockTaskMode && am.lockTaskModeState != ActivityManager.LOCK_TASK_MODE_LOCKED) {
                    killApp(); return
                }
                doLockTask()
            } catch (_: Exception) { killApp() }
        }
    }

    // ============ JS BLOCKER ============

    private fun injectBlockerJs(wv: WebView) {
        wv.evaluateJavascript("""
(function(){
    if(window.__exambrowser_blocked)return;
    window.__exambrowser_blocked=true;
    var css='*{user-select:none!important;-webkit-user-select:none!important;-webkit-touch-callout:none!important}'+
        'input,textarea,[contenteditable]{user-select:auto!important;-webkit-user-select:auto!important}';
    var s=document.createElement('style');s.innerHTML=css;document.head.appendChild(s);
    document.addEventListener('copy',function(e){e.preventDefault();return false},true);
    document.addEventListener('cut',function(e){e.preventDefault();return false},true);
    document.addEventListener('paste',function(e){e.preventDefault();return false},true);
    document.addEventListener('selectstart',function(e){
        var t=(e.target.tagName||'').toLowerCase();
        if(t==='input'||t==='textarea'||e.target.isContentEditable)return;
        e.preventDefault();return false;
    },true);
    document.addEventListener('contextmenu',function(e){e.preventDefault();return false},true);
    document.addEventListener('touchstart',function(e){if(e.touches.length>1)e.preventDefault()},{passive:false});
})();
""".trimIndent(), null)
    }

    private fun blockCopyPaste() {
        try {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.clearPrimaryClip()
            if (clipboardListener == null) {
                clipboardListener = ClipboardManager.OnPrimaryClipChangedListener { cm.clearPrimaryClip() }
                cm.addPrimaryClipChangedListener(clipboardListener!!)
            }
        } catch (_: Exception) {}
    }

    // ============ ALARM ============

    private fun playAlarm() {
        try {
            val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            am.setStreamVolume(AudioManager.STREAM_MUSIC, am.getStreamMaxVolume(AudioManager.STREAM_MUSIC), 0)
            am.setStreamVolume(AudioManager.STREAM_ALARM, am.getStreamMaxVolume(AudioManager.STREAM_ALARM), 0)
            val resId = resources.getIdentifier("alarm", "raw", packageName)
            if (resId == 0) return
            val afd = resources.openRawResourceFd(resId) ?: return
            mediaPlayer?.release(); mediaPlayer = null
            val mp = MediaPlayer().apply {
                setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                setAudioAttributes(AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                setVolume(1f, 1f)
                setOnPreparedListener { try { afd.close() } catch (_: Exception) {}; start() }
                setOnCompletionListener { release(); mediaPlayer = null }
                setOnErrorListener { _, _, _ ->
                    try { afd.close() } catch (_: Exception) {}; release(); mediaPlayer = null; true }
            }
            mediaPlayer = mp; mp.prepareAsync()
        } catch (_: Exception) {}
    }

    // ============ BLOCK BACK & HOME ============

    override fun onBackPressed() {}
    override fun onKeyDown(keyCode: Int, event: KeyEvent?) = when (keyCode) {
        KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_HOME, KeyEvent.KEYCODE_APP_SWITCH -> true
        else -> super.onKeyDown(keyCode, event)
    }
    override fun onUserLeaveHint() { if (pinGranted) killApp(); super.onUserLeaveHint() }

    private fun killApp() {
        finishAffinity()
        android.os.Process.killProcess(android.os.Process.myPid())
        System.exit(0)
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
