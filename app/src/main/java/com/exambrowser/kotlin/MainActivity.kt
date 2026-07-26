package com.exambrowser.kotlin

import android.app.ActivityManager
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.view.ActionMode
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var inputUrl: EditText
    private var pinGranted = false
    private var mediaPlayer: MediaPlayer? = null
    private var clipboardListener: ClipboardManager.OnPrimaryClipChangedListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // === HOME SCREEN (input URL) ===
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTATION
            setPadding(60, 160, 60, 60)
        }

        val title = TextView(this).apply {
            text = "Exam Browser"
            textSize = 24f
            setTextColor(Color.parseColor("#1a1a2e"))
            setPadding(0, 0, 0, 30)
        }

        inputUrl = EditText(this).apply {
            hint = "Masukan Url atau IP CBT"
            setPadding(30, 24, 30, 24)
        }

        val btnStart = Button(this).apply {
            text = "Mulai Ujian"
            setBackgroundColor(Color.parseColor("#0f3460"))
            setTextColor(Color.WHITE)
            setPadding(30, 20, 30, 20)
            setOnClickListener { startExam() }
        }

        val btnExit = Button(this).apply {
            text = "Keluar Aplikasi"
            setBackgroundColor(Color.parseColor("#dc2626"))
            setTextColor(Color.WHITE)
            setPadding(30, 20, 30, 20)
            setOnClickListener {
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("Keluar Aplikasi")
                    .setMessage("Apakah Anda yakin ingin keluar dari Exam Browser?")
                    .setPositiveButton("Ya, Keluar") { _, _ -> killApp() }
                    .setNegativeButton("Batal", null)
                    .show()
            }
        }

        layout.addView(title)
        layout.addView(inputUrl)
        layout.addView(btnStart)
        layout.addView(btnExit)
        setContentView(layout)

        // === PIN DIALOG (muncul otomatis) ===
        AlertDialog.Builder(this)
            .setTitle("Peringatan!")
            .setMessage("Anda wajib menyematkan (pin) aplikasi untuk mengerjakan ujian.\n\nJika Anda menolak, aplikasi akan ditutup.")
            .setCancelable(false)
            .setPositiveButton("Saya Setuju") { _, _ ->
                startLockTask()
                waitForPin()
            }
            .setNegativeButton("Tidak Setuju") { _, _ -> killApp() }
            .show()
    }

    // ============ PIN / LOCK TASK ============

    private fun startLockTask() {
        try {
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
            startLockTask()
        } catch (_: Exception) {}
    }

    private fun waitForPin() {
        Thread {
            val start = System.currentTimeMillis()
            while (true) {
                if (System.currentTimeMillis() - start > 15000) {
                    killApp()
                    return@Thread
                }
                val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                if (am.isInLockTaskMode || am.lockTaskModeState == ActivityManager.LOCK_TASK_MODE_LOCKED) {
                    pinGranted = true
                    return@Thread
                }
                Thread.sleep(500)
            }
        }.start()
    }

    private val periodicCheck = object : Runnable {
        override fun run() {
            if (!pinGranted) return
            try {
                val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                if (!am.isInLockTaskMode && am.lockTaskModeState != ActivityManager.LOCK_TASK_MODE_LOCKED) {
                    killApp()
                    return
                }
            } catch (_: Exception) {
                killApp()
                return
            }
            window.decorView.postDelayed(this, 1000)
        }
    }

    override fun onResume() {
        super.onResume()
        if (pinGranted) {
            try {
                val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                if (!am.isInLockTaskMode && am.lockTaskModeState != ActivityManager.LOCK_TASK_MODE_LOCKED) {
                    killApp()
                    return
                }
                startLockTask()
            } catch (_: Exception) {
                killApp()
            }
        }
    }

    // ============ MULAI UJIAN ============

    private fun startExam() {
        val raw = inputUrl.text.toString().trim()
        if (raw.isEmpty()) {
            Toast.makeText(this, "Masukkan URL terlebih dahulu", Toast.LENGTH_SHORT).show()
            return
        }
        if (!pinGranted) {
            Toast.makeText(this, "Pin belum disetujui!", Toast.LENGTH_SHORT).show()
            return
        }

        // Play alarm
        playAlarm()

        val isIp = Regex("^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}").containsMatchIn(raw)
        val url = if (raw.contains("://")) raw else "${if (isIp) "http" else "https"}://$raw"

        val webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            setLayerType(View.LAYER_TYPE_HARDWARE, null)

            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: android.webkit.WebResourceRequest): Boolean {
                    return try {
                        val targetHost = request.url.host ?: return true
                        val initialHost = java.net.URL(url).host
                        targetHost != initialHost && !targetHost.endsWith(".$initialHost")
                    } catch (_: Exception) { true }
                }
                override fun onPageFinished(view: WebView, u: String) {
                    super.onPageFinished(view, u)
                    blockCopyPaste()
                    injectBlockerJs(view)
                }
            }

            // Block ActionMode (copy/paste toolbar) — tidak blokir input
            setCustomActionModeCallback(object : ActionMode.Callback {
                override fun onCreateActionMode(mode: ActionMode?, menu: Menu?): Boolean = false
                override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?): Boolean = false
                override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?): Boolean = false
                override fun onDestroyActionMode(mode: ActionMode?) {}
            })
        }

        // Start periodic unpin check
        window.decorView.postDelayed(periodicCheck, 1000)

        setContentView(webView)
        webView.loadUrl(url)
    }

    private fun injectBlockerJs(webView: WebView) {
        val js = """
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
""".trimIndent()
        webView.evaluateJavascript(js, null)
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

            mediaPlayer?.release()
            mediaPlayer = null

            val mp = MediaPlayer().apply {
                setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                setAudioAttributes(AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build())
                setVolume(1f, 1f)
                setOnPreparedListener {
                    try { afd.close() } catch (_: Exception) {}
                    start()
                }
                setOnCompletionListener { release(); mediaPlayer = null }
                setOnErrorListener { _, _, _ ->
                    try { afd.close() } catch (_: Exception) {}
                    release(); mediaPlayer = null; true
                }
            }
            mediaPlayer = mp
            mp.prepareAsync()
        } catch (_: Exception) {}
    }

    // ============ BLOCK BACK & HOME ============

    override fun onBackPressed() { /* BLOCKED */ }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_HOME, KeyEvent.KEYCODE_APP_SWITCH -> true
            else -> super.onKeyDown(keyCode, event)
        }
    }

    override fun onUserLeaveHint() {
        if (pinGranted) killApp()
        super.onUserLeaveHint()
    }

    // ============ KILL ============

    private fun killApp() {
        finishAffinity()
        android.os.Process.killProcess(android.os.Process.myPid())
        System.exit(0)
    }
}
