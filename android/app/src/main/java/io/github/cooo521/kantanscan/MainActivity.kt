package io.github.cooo521.kantanscan

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ResolveInfo
import android.graphics.Color
import android.net.Uri
import android.os.BaseBundle
import android.os.Bundle
import android.os.PersistableBundle
import android.util.Base64
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
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
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * かんたんスキャン（Androidアプリ）
 * 画面はホームページ（GitHub Pages）をそのまま表示する。
 *
 * ボタンが押されたときの動き:
 *  1. そのボタンが「ドライブのスキャン」とつながっていれば、ドライブのスキャン画面を開く。
 *     つなぐときは、ホーム画面に「ドライブ スキャン」を置くときと同じ画面（ドライブのフォルダ選び）を
 *     アプリから開き、ドライブが返してきた「開き方」を覚えておく。
 *  2. つながっていない・開けなかったときは、Google のドキュメントスキャナー（ML Kit）を開き、
 *     できた PDF を画面側に渡す（送り先のフォルダへの保存は画面側＝受け取り係が行う）。
 */
class MainActivity : ComponentActivity() {

    companion object {
        const val START_URL = "https://cooo521.github.io/scan/?app=1"
        const val DRIVE_PKG = "com.google.android.apps.docs"
        const val PREFS = "drive_shortcuts"
    }

    private lateinit var web: WebView
    private var pendingDest: String? = null
    private var pendingImage = false               // true: 丸付け用に写真（JPEG）を受け取る
    @Volatile private var lastPdfB64: String? = null
    @Volatile private var lastImgB64: String? = null

    // ドライブのスキャンとつなぐ
    private val prefs by lazy { getSharedPreferences(PREFS, MODE_PRIVATE) }
    private var connectDest: String? = null

    private val shortcutLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            onShortcutResult(result.resultCode, result.data)
        }

    private val scanLauncher =
        registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            val dest = pendingDest ?: ""
            pendingDest = null
            val asImage = pendingImage
            pendingImage = false
            if (result.resultCode != RESULT_OK) {
                js("window.onNativeScanCancel && window.onNativeScanCancel()")
                return@registerForActivityResult
            }
            if (asImage) {
                val page = GmsDocumentScanningResult.fromActivityResultIntent(result.data)?.pages?.firstOrNull()
                if (page == null) {
                    js("window.onNativeScanError && window.onNativeScanError('noimage')")
                    return@registerForActivityResult
                }
                Thread {
                    try {
                        val bytes = contentResolver.openInputStream(page.imageUri)!!.use { it.readBytes() }
                        lastImgB64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                        runOnUiThread { jsCall("onNativeImage", dest) }
                    } catch (e: Exception) {
                        runOnUiThread { js("window.onNativeScanError && window.onNativeScanError(${JSONObject.quote(e.message ?: "read")})") }
                    }
                }.start()
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
        connectDest = savedInstanceState?.getString("connectDest")

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
        setContentView(web, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        if (savedInstanceState != null) web.restoreState(savedInstanceState) else web.loadUrl(START_URL)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                web.evaluateJavascript("(window.appBack ? window.appBack() : 'exit')") { r ->
                    if (r == null || r.contains("exit")) finish()
                }
            }
        })
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        web.saveState(outState)
        outState.putString("connectDest", connectDest)
    }

    private fun js(code: String) {
        web.evaluateJavascript(code, null)
    }

    private fun jsCall(fn: String, vararg args: String) {
        val a = args.joinToString(",") { JSONObject.quote(it) }
        js("window.$fn && window.$fn($a)")
    }

    // ---------------- Google のドキュメントスキャナー（予備） ----------------

    private fun startScan(dest: String, asImage: Boolean = false) {
        val b = GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(true)
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
        if (asImage) {
            b.setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG).setPageLimit(1)
        } else {
            b.setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_PDF)
        }
        GmsDocumentScanning.getClient(b.build())
            .getStartScanIntent(this)
            .addOnSuccessListener { sender ->
                pendingDest = dest
                pendingImage = asImage
                scanLauncher.launch(IntentSenderRequest.Builder(sender).build())
            }
            .addOnFailureListener { e ->
                js("window.onNativeScanError && window.onNativeScanError(${JSONObject.quote(e.message ?: "start")})")
            }
    }

    // ---------------- ドライブの「ドライブ スキャン」（ショートカット） ----------------

    /** ドライブのアプリが持っている「ホーム画面に置けるもの」の一覧 */
    @Suppress("DEPRECATION")
    private fun driveShortcutMakers(): List<ResolveInfo> = try {
        packageManager.queryIntentActivities(Intent(Intent.ACTION_CREATE_SHORTCUT).setPackage(DRIVE_PKG), 0)
    } catch (_: Exception) {
        emptyList()
    }

    private fun labelOf(r: ResolveInfo): String =
        try { r.loadLabel(packageManager)?.toString() ?: "" } catch (_: Exception) { "" }

    /** その中から「スキャン」を探す */
    private fun findScanMaker(): ResolveInfo? {
        val list = driveShortcutMakers()
        return list.firstOrNull { labelOf(it).contains("スキャン") || labelOf(it).contains("scan", ignoreCase = true) }
            ?: list.firstOrNull { it.activityInfo.name.contains("scan", ignoreCase = true) }
    }

    /** つなぐ: ホーム画面に置くときと同じ「フォルダ選び」の画面を開く */
    private fun connect(dest: String) {
        if (connectDest != null) return
        val maker = findScanMaker()
        if (maker == null) {
            val found = driveShortcutMakers().joinToString(" / ") { labelOf(it) + "(" + it.activityInfo.name.substringAfterLast('.') + ")" }
            jsCall("onDriveConnected", dest, "noprovider", "見つかったもの: " + found.ifEmpty { "なし" })
            return
        }
        val i = Intent(Intent.ACTION_CREATE_SHORTCUT)
            .setClassName(maker.activityInfo.packageName, maker.activityInfo.name)
        connectDest = dest
        try {
            shortcutLauncher.launch(i)
        } catch (e: Exception) {
            connectDest = null
            jsCall("onDriveConnected", dest, "start", e.javaClass.simpleName + ": " + (e.message ?: ""))
        }
    }

    /** ドライブから返ってきた「開き方」を覚える */
    @Suppress("DEPRECATION")
    private fun onShortcutResult(code: Int, data: Intent?) {
        val dest = connectDest ?: return
        connectDest = null
        if (code != RESULT_OK || data == null) {
            jsCall("onDriveConnected", dest, "cancel", "")
            return
        }
        val target: Intent? = try { data.getParcelableExtra(Intent.EXTRA_SHORTCUT_INTENT) } catch (_: Exception) { null }
        if (target == null) {
            val keys = try { data.extras?.keySet()?.joinToString(", ") } catch (_: Exception) { null } ?: "なし"
            jsCall("onDriveConnected", dest, "nointent", "返ってきたもの: $keys")
            return
        }
        val name = data.getStringExtra(Intent.EXTRA_SHORTCUT_NAME) ?: ""
        val o = JSONObject().put("name", name).put("intent", intentToJson(target))
        prefs.edit().putString(dest, o.toString()).apply()
        jsCall("onDriveConnected", dest, "ok", describe(target))
    }

    private fun disconnect(dest: String) {
        prefs.edit().remove(dest).apply()
    }

    /** 覚えておいた開き方で、ドライブのスキャン画面を開く */
    private fun driveScan(dest: String) {
        val s = prefs.getString(dest, null)
        if (s == null) { jsCall("onDriveScan", dest, "notconnected", ""); return }
        try {
            val i = jsonToIntent(JSONObject(s).getJSONObject("intent"))
            startActivity(i)
            jsCall("onDriveScan", dest, "opened", "")
        } catch (e: Exception) {
            jsCall("onDriveScan", dest, "blocked", e.javaClass.simpleName + ": " + (e.message ?: ""))
        }
    }

    private fun describe(i: Intent): String {
        val ex = try { i.extras?.keySet()?.joinToString(",") } catch (_: Exception) { null } ?: ""
        return listOfNotNull(i.action, i.component?.shortClassName, i.dataString?.take(60), if (ex.isEmpty()) null else "extras=$ex")
            .joinToString(" ")
    }

    // ---- 開き方（Intent）を文字にして保存する／文字から戻す ----

    private fun intentToJson(i: Intent): JSONObject {
        val o = JSONObject()
        i.action?.let { o.put("action", it) }
        i.dataString?.let { o.put("data", it) }
        i.type?.let { o.put("type", it) }
        i.component?.let { o.put("component", it.flattenToString()) }
        i.`package`?.let { o.put("package", it) }
        i.categories?.let { o.put("categories", JSONArray(it.toList())) }
        o.put("flags", i.flags)
        i.extras?.let { o.put("extras", bundleToJson(it)) }
        return o
    }

    private fun jsonToIntent(o: JSONObject): Intent {
        val i = Intent()
        o.optString("action").takeIf { it.isNotEmpty() }?.let { i.action = it }
        val data = o.optString("data").takeIf { it.isNotEmpty() }
        val type = o.optString("type").takeIf { it.isNotEmpty() }
        when {
            data != null && type != null -> i.setDataAndType(Uri.parse(data), type)
            data != null -> i.data = Uri.parse(data)
            type != null -> i.type = type
        }
        o.optString("component").takeIf { it.isNotEmpty() }?.let { i.component = ComponentName.unflattenFromString(it) }
        o.optString("package").takeIf { it.isNotEmpty() }?.let { i.`package` = it }
        o.optJSONArray("categories")?.let { a -> for (k in 0 until a.length()) i.addCategory(a.getString(k)) }
        i.flags = o.optInt("flags", 0)
        o.optJSONObject("extras")?.let { i.putExtras(jsonToBundle(it)) }
        return i
    }

    @Suppress("DEPRECATION")
    private fun bundleToJson(b: BaseBundle): JSONObject {
        val o = JSONObject()
        for (k in b.keySet()) {
            val v = try { b.get(k) } catch (_: Exception) { null } ?: continue
            val e = JSONObject()
            when (v) {
                is String -> e.put("t", "s").put("v", v)
                is Boolean -> e.put("t", "z").put("v", v)
                is Int -> e.put("t", "i").put("v", v)
                is Long -> e.put("t", "l").put("v", v)
                is Double -> e.put("t", "d").put("v", v)
                is Float -> e.put("t", "f").put("v", v.toDouble())
                is CharSequence -> e.put("t", "s").put("v", v.toString())
                is IntArray -> e.put("t", "I").put("v", JSONArray(v.toList()))
                is LongArray -> e.put("t", "L").put("v", JSONArray(v.toList()))
                is BooleanArray -> e.put("t", "Z").put("v", JSONArray(v.toList()))
                is DoubleArray -> e.put("t", "D").put("v", JSONArray(v.toList()))
                is Array<*> -> if (v.all { it is String }) e.put("t", "S").put("v", JSONArray(v.toList())) else continue
                is PersistableBundle -> e.put("t", "P").put("v", bundleToJson(v))
                is Bundle -> e.put("t", "B").put("v", bundleToJson(v))
                else -> continue
            }
            o.put(k, e)
        }
        return o
    }

    private fun jsonToBundle(o: JSONObject): Bundle {
        val b = Bundle()
        for (k in o.keys()) {
            val e = o.getJSONObject(k)
            when (e.getString("t")) {
                "s" -> b.putString(k, e.getString("v"))
                "z" -> b.putBoolean(k, e.getBoolean("v"))
                "i" -> b.putInt(k, e.getInt("v"))
                "l" -> b.putLong(k, e.getLong("v"))
                "d" -> b.putDouble(k, e.getDouble("v"))
                "f" -> b.putFloat(k, e.getDouble("v").toFloat())
                "I" -> b.putIntArray(k, e.getJSONArray("v").let { a -> IntArray(a.length()) { a.getInt(it) } })
                "L" -> b.putLongArray(k, e.getJSONArray("v").let { a -> LongArray(a.length()) { a.getLong(it) } })
                "Z" -> b.putBooleanArray(k, e.getJSONArray("v").let { a -> BooleanArray(a.length()) { a.getBoolean(it) } })
                "D" -> b.putDoubleArray(k, e.getJSONArray("v").let { a -> DoubleArray(a.length()) { a.getDouble(it) } })
                "S" -> b.putStringArray(k, e.getJSONArray("v").let { a -> Array(a.length()) { a.getString(it) } })
                "P" -> b.putParcelable(k, jsonToPersistable(e.getJSONObject("v")))
                "B" -> b.putBundle(k, jsonToBundle(e.getJSONObject("v")))
            }
        }
        return b
    }

    private fun jsonToPersistable(o: JSONObject): PersistableBundle {
        val b = PersistableBundle()
        for (k in o.keys()) {
            val e = o.getJSONObject(k)
            when (e.getString("t")) {
                "s" -> b.putString(k, e.getString("v"))
                "z" -> b.putBoolean(k, e.getBoolean("v"))
                "i" -> b.putInt(k, e.getInt("v"))
                "l" -> b.putLong(k, e.getLong("v"))
                "d", "f" -> b.putDouble(k, e.getDouble("v"))
                "I" -> b.putIntArray(k, e.getJSONArray("v").let { a -> IntArray(a.length()) { a.getInt(it) } })
                "L" -> b.putLongArray(k, e.getJSONArray("v").let { a -> LongArray(a.length()) { a.getLong(it) } })
                "Z" -> b.putBooleanArray(k, e.getJSONArray("v").let { a -> BooleanArray(a.length()) { a.getBoolean(it) } })
                "D" -> b.putDoubleArray(k, e.getJSONArray("v").let { a -> DoubleArray(a.length()) { a.getDouble(it) } })
                "S" -> b.putStringArray(k, e.getJSONArray("v").let { a -> Array(a.length()) { a.getString(it) } })
                "P", "B" -> b.putPersistableBundle(k, jsonToPersistable(e.getJSONObject("v")))
            }
        }
        return b
    }

    /** 画面（ホームページ）から呼べる機能 */
    inner class Bridge {
        @JavascriptInterface
        fun version(): String = "4"

        @JavascriptInterface
        fun scan(dest: String) {
            runOnUiThread { startScan(dest) }
        }

        /** 丸付け用：Googleのスキャナーで1枚撮って、写真（JPEG）で受け取る */
        @JavascriptInterface
        fun scanImage(tag: String) {
            runOnUiThread { startScan(tag, asImage = true) }
        }

        /** 直前に撮った写真（base64）を1回だけ渡す */
        @JavascriptInterface
        fun takeImage(): String {
            val s = lastImgB64 ?: ""
            lastImgB64 = null
            return s
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

        /** ドライブのスキャンとのつながり: {"provider":"ドライブ スキャン","connected":{"yuri-print":"【取込】塾プリント",…}} */
        @JavascriptInterface
        fun driveStatus(): String {
            val o = JSONObject()
            val m = findScanMaker()
            o.put("provider", if (m == null) "" else labelOf(m).ifEmpty { "ドライブ スキャン" })
            val c = JSONObject()
            for ((dest, v) in prefs.all) {
                if (v !is String) continue
                val name = try { JSONObject(v).optString("name") } catch (_: Exception) { "" }
                c.put(dest, name.ifEmpty { "つながっています" })
            }
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
