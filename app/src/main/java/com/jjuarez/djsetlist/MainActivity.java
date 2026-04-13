package com.jjuarez.djsetlist;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.webkit.CookieManager;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebChromeClient;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient.FileChooserParams;
import android.graphics.Color;
import org.json.JSONArray;

public class MainActivity extends Activity {

    private WebView webView;
    private ValueCallback<Uri[]> filePathCallback;
    private static final int FILE_CHOOSER_REQUEST = 1;
    private static final String APP_URL = "https://jjuarez2534.github.io/dj-setlist";
    private static final String WORKER_URL = "https://white-cell-c418.jjuarez2534.workers.dev";
    private Handler handler = new Handler();
    private String[] pendingTrackUrls;
    private String pendingPlaylistTitle;
    private int currentTrackIndex = 0;
    private int successCount = 0;
    private boolean isProcessing = false;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
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
                if (isProcessing && url.contains("soundcloud.com") && !url.contains("github.io")) {
                    handler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            clickAddToPlaylist(view);
                        }
                    }, 2500);
                }
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                if (url.contains("github.io") || url.contains("workers.dev") ||
                    url.contains("soundcloud.com")) {
                    return false;
                }
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, request.getUrl()));
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

        webView.addJavascriptInterface(new Object() {
            @android.webkit.JavascriptInterface
            public void onPlaylistAdded() {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        successCount++;
                        handler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                loadNextTrack();
                            }
                        }, 2000);
                    }
                });
            }

            @android.webkit.JavascriptInterface
            public void onError(String msg) {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        loadNextTrack();
                    }
                });
            }

            @android.webkit.JavascriptInterface
            public void startProcessing(String trackUrlsJson, String playlistTitle) {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            JSONArray arr = new JSONArray(trackUrlsJson);
                            pendingTrackUrls = new String[arr.length()];
                            for (int i = 0; i < arr.length(); i++) {
                                pendingTrackUrls[i] = arr.getString(i);
                            }
                            pendingPlaylistTitle = playlistTitle;
                            currentTrackIndex = 0;
                            successCount = 0;
                            isProcessing = true;
                            loadNextTrack();
                        } catch (Exception e) {}
                    }
                });
            }
        }, "AndroidBridge");

        webView.loadUrl(APP_URL);
    }

    private void loadNextTrack() {
        if (pendingTrackUrls == null || currentTrackIndex >= pendingTrackUrls.length) {
            isProcessing = false;
            showCompletionAndReturn();
            return;
        }
        String url = pendingTrackUrls[currentTrackIndex];
        currentTrackIndex++;
        webView.loadUrl(url);
    }

    private void clickAddToPlaylist(WebView view) {
        String pl = pendingPlaylistTitle != null ? pendingPlaylistTitle.replace("'", "\\'") : "";
        int trackNum = currentTrackIndex;
        int total = pendingTrackUrls != null ? pendingTrackUrls.length : 0;

        String script =
            "(function() {" +
            "  function toast(msg, ok) {" +
            "    var ex = document.getElementById('dj-toast');" +
            "    if (ex) ex.remove();" +
            "    var el = document.createElement('div');" +
            "    el.id = 'dj-toast';" +
            "    el.textContent = msg;" +
            "    el.style.cssText = 'position:fixed;top:70px;left:50%;transform:translateX(-50%);background:' + (ok?'#1db954':'#FF5500') + ';color:#fff;padding:12px 20px;border-radius:10px;font-size:13px;font-weight:500;z-index:99999;max-width:85vw;text-align:center';" +
            "    document.body.appendChild(el);" +
            "  }" +
            "  toast('Adding track " + trackNum + " of " + total + "...');" +
            "  function findAndClick(selector, fallbackText) {" +
            "    var el = document.querySelector(selector);" +
            "    if (!el && fallbackText) {" +
            "      var all = document.querySelectorAll('button, [role=menuitem], li');" +
            "      for (var i = 0; i < all.length; i++) {" +
            "        if (all[i].textContent.trim().toLowerCase().includes(fallbackText.toLowerCase())) {" +
            "          el = all[i]; break;" +
            "        }" +
            "      }" +
            "    }" +
            "    return el;" +
            "  }" +
            "  function tryAddToPlaylist(attempt) {" +
            "    attempt = attempt || 0;" +
            "    if (attempt > 5) { toast('Could not find controls'); AndroidBridge.onError('timeout'); return; }" +
            "    var moreBtn = findAndClick('.sc-button-more', 'more');" +
            "    if (!moreBtn) { setTimeout(function(){ tryAddToPlaylist(attempt+1); }, 1000); return; }" +
            "    moreBtn.click();" +
            "    setTimeout(function() {" +
            "      var addBtn = findAndClick('[class*=addTo]', 'add to playlist');" +
            "      if (!addBtn) addBtn = findAndClick('[class*=playlist]', 'playlist');" +
            "      if (!addBtn) { toast('Add to playlist not found'); AndroidBridge.onError('no add btn'); return; }" +
            "      addBtn.click();" +
            "      setTimeout(function() {" +
            "        var plItems = document.querySelectorAll('[class*=playlist] li, [class*=addTo] li, .sc-list-item');" +
            "        var target = null;" +
            "        var plTitle = '" + pl + "';" +
            "        for (var i = 0; i < plItems.length; i++) {" +
            "          if (plItems[i].textContent.includes(plTitle)) { target = plItems[i]; break; }" +
            "        }" +
            "        if (!target && plItems.length > 0) target = plItems[0];" +
            "        if (target) {" +
            "          target.click();" +
            "          toast('Track " + trackNum + " added!', true);" +
            "          setTimeout(function(){ AndroidBridge.onPlaylistAdded(); }, 1000);" +
            "        } else {" +
            "          toast('Playlist list not found');" +
            "          AndroidBridge.onError('no playlist items');" +
            "        }" +
            "      }, 1500);" +
            "    }, 800);" +
            "  }" +
            "  tryAddToPlaylist(0);" +
            "})();";

        view.evaluateJavascript(script, null);
    }

    private void showCompletionAndReturn() {
        webView.loadUrl(APP_URL);
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                webView.evaluateJavascript(
                    "if(window.showToast) showToast('Done! Added " + successCount + " tracks!', 'success');",
                    null
                );
            }
        }, 2000);
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
        if (isProcessing) {
            isProcessing = false;
            webView.loadUrl(APP_URL);
        } else if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}
