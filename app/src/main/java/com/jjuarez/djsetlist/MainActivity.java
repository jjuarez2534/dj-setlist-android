package com.jjuarez.djsetlist;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.CookieManager;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebChromeClient;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient.FileChooserParams;
import android.view.Window;
import android.view.WindowManager;
import android.graphics.Color;

public class MainActivity extends Activity {

    private WebView webView;
    private ValueCallback<Uri[]> filePathCallback;
    private static final int FILE_CHOOSER_REQUEST = 1;
    private static final String APP_URL = "https://jjuarez2534.github.io/dj-setlist";
    private static final String WORKER_URL = "https://white-cell-c418.jjuarez2534.workers.dev";

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setStatusBarColor(Color.parseColor("#FF5500"));

        webView = new WebView(this);
        setContentView(webView);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setUserAgentString(
            "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
        );

        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                if (url.contains("soundcloud.com") && !url.contains("github.io")) {
                    injectScript(view);
                }
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                if (url.contains("soundcloud.com") ||
                    url.contains("github.io") ||
                    url.contains("workers.dev")) {
                    return false;
                }
                try {
                    Intent intent = new Intent(Intent.ACTION_VIEW, request.getUrl());
                    startActivity(intent);
                } catch (Exception e) {}
                return true;
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> callback,
                    FileChooserParams params) {
                filePathCallback = callback;
                Intent intent = params.createIntent();
                startActivityForResult(intent, FILE_CHOOSER_REQUEST);
                return true;
            }

            @Override
            public void onPermissionRequest(PermissionRequest request) {
                request.grant(request.getResources());
            }
        });

        webView.loadUrl(APP_URL);
    }

    private void injectScript(WebView view) {
        String script =
            "(function() {" +
            "  if (window._djInjected) return;" +
            "  window._djInjected = true;" +
            "  function showToast(msg, ok) {" +
            "    var el = document.createElement('div');" +
            "    el.textContent = msg;" +
            "    el.style.cssText = 'position:fixed;bottom:24px;left:50%;transform:translateX(-50%);background:' + (ok ? '#1db954' : '#FF5500') + ';color:#fff;padding:14px 24px;border-radius:12px;font-size:14px;font-weight:500;z-index:99999;max-width:90vw;text-align:center';" +
            "    document.body.appendChild(el);" +
            "    setTimeout(function() { el.remove(); }, 6000);" +
            "  }" +
            "  fetch('" + WORKER_URL + "/get-job')" +
            "  .then(function(r) { return r.json(); })" +
            "  .then(function(p) {" +
            "    if (p.error) { showToast('No job found - add tracks first'); return; }" +
            "    showToast('Adding ' + p.count + ' tracks...');" +
            "    return fetch('https://api-v2.soundcloud.com/playlists/' + p.playlistId + '?client_id=' + p.clientId + '&app_version=1775786406&app_locale=en', {" +
            "      method: 'PUT'," +
            "      headers: { 'Authorization': 'OAuth ' + p.oauth, 'Content-Type': 'application/json' }," +
            "      body: JSON.stringify({ playlist: { tracks: p.tracks } })" +
            "    });" +
            "  })" +
            "  .then(function(r) {" +
            "    if (!r) return;" +
            "    if (r.ok) {" +
            "      showToast('Done! Tracks added!', true);" +
            "      fetch('" + WORKER_URL + "/clear-job', { method: 'POST' });" +
            "    } else {" +
            "      r.text().then(function(e) { showToast('Error ' + r.status + ': ' + e.substring(0, 80)); });" +
            "    }" +
            "  })" +
            "  .catch(function(e) { showToast('Error: ' + e.message); });" +
            "})();";

        view.evaluateJavascript(script, null);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == FILE_CHOOSER_REQUEST) {
            if (filePathCallback != null) {
                Uri[] results = null;
                if (resultCode == Activity.RESULT_OK && data != null) {
                    results = new Uri[]{data.getData()};
                }
                filePathCallback.onReceiveValue(results);
                filePathCallback = null;
            }
        }
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}
