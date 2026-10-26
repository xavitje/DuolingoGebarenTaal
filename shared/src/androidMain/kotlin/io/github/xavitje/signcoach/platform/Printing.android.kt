package io.github.xavitje.signcoach.platform

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient

// De WebView moet blijven bestaan tot het printen klaar is; zo houdt de GC hem niet weg.
private var printWebView: WebView? = null

actual fun printHtml(jobName: String, html: String): Boolean {
    val activity = AndroidCameraPlatform.currentActivity ?: return false
    Handler(Looper.getMainLooper()).post {
        val webView = WebView(activity)
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String?) {
                val printManager = activity.getSystemService(Context.PRINT_SERVICE) as PrintManager
                val attributes = PrintAttributes.Builder()
                    .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                    .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                    .build()
                printManager.print(jobName, view.createPrintDocumentAdapter(jobName), attributes)
            }
        }
        webView.loadDataWithBaseURL(null, html, "text/html", "utf-8", null)
        printWebView = webView
    }
    return true
}
