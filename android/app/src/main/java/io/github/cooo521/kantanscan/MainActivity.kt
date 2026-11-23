package io.github.cooo521.kantanscan

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Base64
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
import org.json.JSONObject
import java.io.File

/**
 * かんたんスキャン（Androidアプリ）
 * 画面はホームページ（GitHub Pages）をそのまま表示し、
 * ボタンが押されたら Google のドキュメントスキャナーを開いて、できた PDF を画面側に渡す。
 * 送り先のフォルダへの保存は、これまでどおり画面側（受け取り係）が行う。
 */
class MainActivity : ComponentActivity() {

    companion object {
        const val START_URL = "https://cooo521.github.io/scan/?app=1"
    }

    private lateinit var web: WebView
    private var pendingDest: String? = null
    @Volatile private var lastPdfB64: String? = null

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
        setContentView(web)
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
    }

    private fun js(code: String) {
        web.evaluateJavascript(code, null)
    }

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

    /** 画面（ホームページ）から呼べる機能 */
    inner class Bridge {
        @JavascriptInterface
        fun version(): String = "1"

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
    }
}
