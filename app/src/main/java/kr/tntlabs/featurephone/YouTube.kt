package kr.tntlabs.featurephone

import android.annotation.SuppressLint
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

// LCD 안에서 도는 데스크톱 유튜브. 키패드: 2/8 스크롤, 4 뒤로, MENU 홈
private const val YT_HOME = "https://www.youtube.com/?app=desktop"
// 유튜브의 viewport 메타를 PC 폭으로 덮어써 데스크톱 레이아웃 강제
private const val FORCE_DESKTOP = """
(function(){var m=document.querySelector('meta[name=viewport]');
if(!m){m=document.createElement('meta');m.name='viewport';document.head&&document.head.appendChild(m);}
m.setAttribute('content','width=1280, initial-scale=0.29, minimum-scale=0.2, maximum-scale=3');})();
"""
private const val DESKTOP_UA =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
class WebHolder { var view: WebView? = null }

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LcdYouTube(holder: WebHolder) {
    Column(Modifier.fillMaxSize()) {
        LcdHeader("유튜브", "PC")
        AndroidView(
            modifier = Modifier.fillMaxWidth().weight(1f),
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.mediaPlaybackRequiresUserGesture = false
                    settings.userAgentString = DESKTOP_UA
                    settings.useWideViewPort = true
                    settings.loadWithOverviewMode = true
                    settings.setSupportZoom(true)
                    settings.builtInZoomControls = true
                    settings.displayZoomControls = false
                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(v: WebView, url: String?, favicon: android.graphics.Bitmap?) { v.evaluateJavascript(FORCE_DESKTOP, null) }
                        override fun onPageFinished(v: WebView, url: String?) { v.evaluateJavascript(FORCE_DESKTOP, null) }
                        override fun doUpdateVisitedHistory(v: WebView, url: String?, isReload: Boolean) { v.evaluateJavascript(FORCE_DESKTOP, null) }
                    }
                    loadUrl(YT_HOME)
                    holder.view = this
                }
            },
            onRelease = { it.destroy(); holder.view = null }
        )
        SoftkeyRow("홈", "", "뒤로")
    }
}

fun WebHolder.scroll(dy: Int) { view?.evaluateJavascript("window.scrollBy({top:$dy,behavior:'smooth'})", null) }
fun WebHolder.back(): Boolean { val v = view ?: return false; return if (v.canGoBack()) { v.goBack(); true } else false }
fun WebHolder.home() { view?.loadUrl(YT_HOME) }
