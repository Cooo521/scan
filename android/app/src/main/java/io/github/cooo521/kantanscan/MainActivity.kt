package io.github.cooo521.kantanscan

import android.annotation.SuppressLint
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import org.json.JSONObject
import java.io.File

/**
 * かんたんスキャン（Androidアプリ）
 * 画面はホームページ（GitHub Pages）をそのまま表示する。
 *
 * ボタンが押されたときの動き:
 *  1. そのボタンが「ドライブのスキャン」とつながっていれば、アプリの中に見えないように置いた
 *     ドライブの「ドライブ スキャン」ウィジェットを代わりに押して、ドライブのスキャン画面を開く
 *     （保存先のフォルダは、つなぐときにドライブの画面で選んだもの）。
 *  2. つながっていない・開けなかったときは、Google のドキュメントスキャナー（ML Kit）を開き、
 *     できた PDF を画面側に渡す（送り先のフォルダへの保存は画面側＝受け取り係が行う）。
 */
class MainActivity : ComponentActivity() {

    companion object {
        const val START_URL = "https://cooo521.github.io/scan/?app=1"
        const val DRIVE_PKG = "com.google.android.apps.docs"
        const val HOST_ID = 4801
        const val REQ_BIND = 4802
        const val REQ_CONFIG = 4803
        const val PREFS = "drive_widgets"
        const val OPEN_TIMEOUT_MS = 3500L
    }

    private lateinit var web: WebView
    private var pendingDest: String? = null
    @Volatile private var lastPdfB64: String? = null

    // ドライブのウィジェットを置くための仕組み
    private lateinit var widgetMgr: AppWidgetManager
    private lateinit var widgetHost: AppWidgetHost
    private lateinit var widgetBox: FrameLayout
    private val prefs by lazy { getSharedPreferences(PREFS, MODE_PRIVATE) }
    private val widgetViews = HashMap<String, AppWidgetHostView>()
    private var connectDest: String? = null
    private var connectId = -1
    private var waitOpenDest: String? = null
    private val ui = Handler(Looper.getMainLooper())
    private val openTimeout = Runnable {
        val d = waitOpenDest ?: return@Runnable
        waitOpenDest = null
        jsCall("onDriveScan", d, "noopen")
    }

    private val scanLauncher =
        registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            val dest = pendingDest ?: ""
            pendingDest = null
            if (result.resultCode != RESULT_OK) {
                js("window.onNativeScanCancel && window.onNativeScanCancel()")
                return@registerForActivityResult
            }
            val pdf = GmsDocumentScanningResult.fromActivityResultIntent(result.data)?.pdf
            if (pdf == null) {
                js("window.onNativeScanError && window.onNativeScanError('nopdf')")
                return@registerForActivityResult
            }
            Thread {
                try {
                    val bytes = contentResolver.openInputStream(pdf.uri)!!.use { it.readBytes() }
                    lastPdfB64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                    runOnUiThread {
                        js("window.onNativeScan && window.onNativeScan(${JSONObject.quote(dest)}, ${pdf.pageCount})")
                    }
                } catch (e: Exception) {
                    runOnUiThread { js("window.onNativeScanError && window.onNativeScanError(${JSONObject.quote(e.message ?: "read")})") }
                }
            }.start()
        }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        widgetMgr = AppWidgetManager.getInstance(this)
        widgetHost = AppWidgetHost(applicationContext, HOST_ID)
        if (savedInstanceState != null) {
            connectDest = savedInstanceState.getString("connectDest")
            connectId = savedInstanceState.getInt("connectId", -1)
        }

        val root = FrameLayout(this)
        val dp = resources.displayMetrics.density
        // ドライブのウィジェットは、画面のうしろに透明にして置いておく（見えない・さわれない）
        widgetBox = FrameLayout(this).apply {
            alpha = 0f
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        }
        root.addView(widgetBox, FrameLayout.LayoutParams((140 * dp).toInt(), (140 * dp).toInt()))

        web = WebView(this)
        web.setBackgroundColor(Color.parseColor("#FFF7EE"))
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true        // 受け取り係の設定をこのスマホに覚えておくため
        web.settings.mediaPlaybackRequiresUserGesture = false
        web.addJavascriptInterface(Bridge(), "Android")
        web.webViewClient = object : WebViewClient() {
            // 自分のホームページの中だけを開く（ほかのサイトはブラウザで開く）
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url
                if (url.host == "cooo521.github.io") return false
                startActivity(Intent(Intent.ACTION_VIEW, url))
                return true
            }
        }
        // 画面の上下（時計やナビゲーションのバー）に重ならないようにする
        ViewCompat.setOnApplyWindowInsetsListener(web) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        root.addView(web, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        setContentView(root)
        if (savedInstanceState != null) web.restoreState(savedInstanceState) else web.loadUrl(START_URL)

        restoreWidgets(cleanUp = savedInstanceState == null)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                web.evaluateJavascript("(window.appBack ? window.appBack() : 'exit')") { r ->
                    if (r == null || r.contains("exit")) finish()
                }
            }
        })
    }

    override fun onStart() {
        super.onStart()
        try { widgetHost.startListening() } catch (_: Exception) {}
    }

    override fun onStop() {
        super.onStop()
        try { widgetHost.stopListening() } catch (_: Exception) {}
    }

    override fun onPause() {
        super.onPause()
        // ドライブの画面が前に出た＝開けた
        val d = waitOpenDest
        if (d != null) {
            waitOpenDest = null
            ui.removeCallbacks(openTimeout)
            jsCall("onDriveScan", d, "opened")
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        web.saveState(outState)
        outState.putString("connectDest", connectDest)
        outState.putInt("connectId", connectId)
    }

    private fun js(code: String) {
        web.evaluateJavascript(code, null)
    }

    private fun jsCall(fn: String, vararg args: String) {
        val a = args.joinToString(",") { JSONObject.quote(it) }
        js("window.$fn && window.$fn($a)")
    }

    // ---------------- Google のドキュメントスキャナー（予備） ----------------

    private fun startScan(dest: String) {
        val options = GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(true)
            .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_PDF)
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .build()
        GmsDocumentScanning.getClient(options)
            .getStartScanIntent(this)
            .addOnSuccessListener { sender ->
                pendingDest = dest
                scanLauncher.launch(IntentSenderRequest.Builder(sender).build())
            }
            .addOnFailureListener { e ->
                js("window.onNativeScanError && window.onNativeScanError(${JSONObject.quote(e.message ?: "start")})")
            }
    }

    // ---------------- ドライブの「ドライブ スキャン」ウィジェット ----------------

    /** ドライブのアプリの中から「スキャン」のウィジェットを探す */
    private fun findScanProvider(): AppWidgetProviderInfo? {
        val list = try {
            widgetMgr.getInstalledProvidersForPackage(DRIVE_PKG, null)
        } catch (_: Exception) {
            emptyList()
        }
        fun label(p: AppWidgetProviderInfo): String =
            try { p.loadLabel(packageManager) ?: "" } catch (_: Exception) { "" }
        return list.firstOrNull { label(it).contains("スキャン") || label(it).contains("scan", ignoreCase = true) }
            ?: list.firstOrNull { it.provider.className.contains("scan", ignoreCase = true) }
    }

    /** 前につないだウィジェットを、アプリを開くたびに置き直す */
    private fun restoreWidgets(cleanUp: Boolean) {
        val keep = HashSet<Int>()
        val ed = prefs.edit()
        for ((dest, v) in prefs.all) {
            val id = (v as? Int) ?: continue
            val info = widgetMgr.getAppWidgetInfo(id)
            if (info == null) { ed.remove(dest); continue }
            keep.add(id)
            attachView(dest, id, info)
        }
        ed.apply()
        if (connectId >= 0) keep.add(connectId)
        // 使っていない部品を片づける
        if (cleanUp) {
            try {
                widgetHost.appWidgetIds.filter { it !in keep }.forEach { widgetHost.deleteAppWidgetId(it) }
            } catch (_: Exception) {}
        }
    }

    private fun attachView(dest: String, id: Int, info: AppWidgetProviderInfo) {
        widgetViews.remove(dest)?.let { widgetBox.removeView(it) }
        val v = widgetHost.createView(this, id, info)
        widgetBox.addView(v, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        widgetViews[dest] = v
    }

    /** つなぐ: ①ウィジェットを作る許可 → ②ドライブの画面で保存先フォルダを選ぶ */
    @Suppress("DEPRECATION")
    private fun connect(dest: String) {
        if (connectDest != null) return
        val info = findScanProvider()
        if (info == null) { jsCall("onDriveConnected", dest, "noprovider"); return }
        val id = widgetHost.allocateAppWidgetId()
        connectDest = dest
        connectId = id
        val allowed = try { widgetMgr.bindAppWidgetIdIfAllowed(id, info.provider) } catch (_: Exception) { false }
        if (allowed) { afterBind(); return }
        val i = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
        try { startActivityForResult(i, REQ_BIND) } catch (e: Exception) { failConnect("bind") }
    }

    private fun afterBind() {
        val info = widgetMgr.getAppWidgetInfo(connectId)
        if (info == null) { failConnect("bind"); return }
        if (info.configure == null) { finishConnect("noconfig"); return }
        try {
            widgetHost.startAppWidgetConfigureActivityForResult(this, connectId, 0, REQ_CONFIG, null)
        } catch (e: Exception) {
            failConnect("config")
        }
    }

    private fun finishConnect(msg: String) {
        val dest = connectDest ?: return
        val id = connectId
        connectDest = null
        connectId = -1
        val old = prefs.getInt(dest, -1)
        if (old >= 0 && old != id) try { widgetHost.deleteAppWidgetId(old) } catch (_: Exception) {}
        prefs.edit().putInt(dest, id).apply()
        widgetMgr.getAppWidgetInfo(id)?.let { attachView(dest, id, it) }
        jsCall("onDriveConnected", dest, msg)
    }

    private fun failConnect(msg: String) {
        val dest = connectDest ?: ""
        if (connectId >= 0) try { widgetHost.deleteAppWidgetId(connectId) } catch (_: Exception) {}
        connectDest = null
        connectId = -1
        jsCall("onDriveConnected", dest, msg)
    }

    @Deprecated("ウィジェットの許可・設定の結果を受け取るため")
    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQ_BIND -> if (resultCode == RESULT_OK) afterBind() else failConnect("denied")
            REQ_CONFIG -> if (resultCode == RESULT_OK) finishConnect("ok") else failConnect("cancel")
        }
    }

    private fun disconnect(dest: String) {
        val id = prefs.getInt(dest, -1)
        if (id >= 0) try { widgetHost.deleteAppWidgetId(id) } catch (_: Exception) {}
        prefs.edit().remove(dest).apply()
        widgetViews.remove(dest)?.let { widgetBox.removeView(it) }
    }

    /** ウィジェットの中の「押せる部分」を探す */
    private fun findClickable(v: View): View? {
        if (v.hasOnClickListeners()) return v
        if (v is ViewGroup) {
            for (i in 0 until v.childCount) {
                findClickable(v.getChildAt(i))?.let { return it }
            }
        }
        return null
    }

    /** 見えないウィジェットを代わりに押して、ドライブのスキャン画面を開く */
    private fun driveScan(dest: String) {
        val v = widgetViews[dest]
        if (v == null) { jsCall("onDriveScan", dest, "notconnected"); return }
        val target = findClickable(v)
        if (target == null) { jsCall("onDriveScan", dest, "noclick"); return }
        waitOpenDest = dest
        ui.removeCallbacks(openTimeout)
        ui.postDelayed(openTimeout, OPEN_TIMEOUT_MS)
        try {
            target.performClick()
        } catch (e: Exception) {
            waitOpenDest = null
            ui.removeCallbacks(openTimeout)
            jsCall("onDriveScan", dest, "noclick")
        }
    }

    /** 画面（ホームページ）から呼べる機能 */
    inner class Bridge {
        @JavascriptInterface
        fun version(): String = "2"

        @JavascriptInterface
        fun scan(dest: String) {
            runOnUiThread { startScan(dest) }
        }

        /** 直前にスキャンしたPDF（base64）を1回だけ渡す */
        @JavascriptInterface
        fun takeScan(): String {
            val s = lastPdfB64 ?: ""
            lastPdfB64 = null
            return s
        }

        /** 直接送れなかったときに、Androidの共有メニューでPDFを渡す */
        @JavascriptInterface
        fun sharePdf(b64: String, name: String) {
            try {
                val dir = File(cacheDir, "share").apply { mkdirs() }
                dir.listFiles()?.forEach { it.delete() }
                val safe = name.replace(Regex("[\\\\/:*?\"<>|]"), "_").ifEmpty { "scan.pdf" }
                val file = File(dir, safe)
                file.writeBytes(Base64.decode(b64, Base64.DEFAULT))
                val uri = FileProvider.getUriForFile(this@MainActivity, "$packageName.files", file)
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "application/pdf"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                runOnUiThread { startActivity(Intent.createChooser(send, "ドライブに送る")) }
            } catch (e: Exception) {
                runOnUiThread { js("window.onNativeScanError && window.onNativeScanError(${JSONObject.quote("share: " + (e.message ?: ""))})") }
            }
        }

        /** ドライブのスキャンとのつながり: {"provider":"ドライブ スキャン","connected":{"yuri-print":true,…}} */
        @JavascriptInterface
        fun driveStatus(): String {
            val o = JSONObject()
            val p = findScanProvider()
            o.put("provider", if (p == null) "" else (try { p.loadLabel(packageManager) } catch (_: Exception) { null } ?: "ドライブ スキャン"))
            val c = JSONObject()
            for ((dest, v) in prefs.all) if (v is Int) c.put(dest, true)
            o.put("connected", c)
            return o.toString()
        }

        @JavascriptInterface
        fun driveConnect(dest: String) {
            runOnUiThread { connect(dest) }
        }

        @JavascriptInterface
        fun driveDisconnect(dest: String) {
            runOnUiThread { disconnect(dest) }
        }

        @JavascriptInterface
        fun driveScan(dest: String) {
            runOnUiThread { this@MainActivity.driveScan(dest) }
        }
    }
}
