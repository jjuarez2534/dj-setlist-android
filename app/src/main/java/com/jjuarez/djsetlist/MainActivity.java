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
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;
import org.json.JSONArray;

public class MainActivity extends Activity {

    private WebView webView;
    private FrameLayout container;
    private View loginOverlay;
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
    private boolean isClickingPlaylist = false;
    private boolean isLoggedIn = false;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.parseColor("#FF5500"));

        container = new FrameLayout(this);
        setContentView(container);

        webView = createWebView();
        container.addView(webView, new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ));

        webView.addJavascriptInterface(new Object() {
            @android.webkit.JavascriptInterface
            public void openSoundCloudLogin() {
                handler.post(new Runnable() {
                    @Override
                    public void run() { showLoginScreen(); }
                });
            }

            @android.webkit.JavascriptInterface
            public void onPlaylistAdded() {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        successCount++;
                        handler.postDelayed(new Runnable() {
                            @Override
                            public void run() { loadNextTrack(); }
                        }, 5000);
                    }
                });
            }

            @android.webkit.JavascriptInterface
            public void onError(String msg) {
                handler.post(new Runnable() {
                    @Override
                    public void run() { loadNextTrack(); }
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

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);

                if (!isLoggedIn && url.contains("soundcloud.com") && !url.contains("github.io")) {
                    view.evaluateJavascript(
                        "(function(){ return document.cookie; })()",
                        new android.webkit.ValueCallback<String>() {
                            @Override
                            public void onReceiveValue(String cookies) {
                                if (cookies != null && cookies.contains("oauth_token")) {
                                    isLoggedIn = true;
                                    CookieManager.getInstance().flush();
                                    handler.post(new Runnable() {
                                        @Override
                                        public void run() {
                                            hideLoginOverlay();
                                            if (!url.contains("github.io")) {
                                                view.loadUrl(APP_URL);
                                            }
                                        }
                                    });
                                }
                            }
                        }
                    );
                }

                if (isProcessing && !isClickingPlaylist && url.contains("soundcloud.com") && !url.contains("github.io")) {
                    isClickingPlaylist = true;
                    handler.postDelayed(new Runnable() {
                        @Override
                        public void run() { clickAddToPlaylist(view); }
                    }, 6000);
                }
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                if (url.contains("github.io") || url.contains("workers.dev") || url.contains("soundcloud.com")) {
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
            public boolean onShowFileChooser(WebView wv, ValueCallback<Uri[]> callback, FileChooserParams params) {
                filePathCallback = callback;
                startActivityForResult(params.createIntent(), FILE_CHOOSER_REQUEST);
                return true;
            }
            @Override
            public void onPermissionRequest(PermissionRequest request) { request.grant(request.getResources()); }
        });

        checkLoginAndStart();
    }

    private void checkLoginAndStart() {
        webView.loadUrl("https://soundcloud.com");
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                webView.evaluateJavascript(
                    "(function(){ return document.cookie; })()",
                    new android.webkit.ValueCallback<String>() {
                        @Override
                        public void onReceiveValue(String cookies) {
                            if (cookies != null && cookies.contains("oauth_token")) {
                                isLoggedIn = true;
                                webView.loadUrl(APP_URL);
                            } else {
                                showLoginScreen();
                            }
                        }
                    }
                );
            }
        }, 3000);
    }

    private void showLoginScreen() {
        if (loginOverlay != null) return;

        FrameLayout overlay = new FrameLayout(this);
        overlay.setBackgroundColor(Color.parseColor("#0a0a0a"));

        FrameLayout.LayoutParams centerParams = new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER
        );

        android.widget.LinearLayout content = new android.widget.LinearLayout(this);
        content.setOrientation(android.widget.LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER);
        content.setPadding(60, 60, 60, 60);

        TextView title = new TextView(this);
        title.setText("DJ Setlist Loader");
        title.setTextSize(24);
        title.setTextColor(Color.WHITE);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 0, 0, 16);

        TextView subtitle = new TextView(this);
        subtitle.setText("Sign in to SoundCloud to add tracks to your playlists");
        subtitle.setTextSize(14);
        subtitle.setTextColor(Color.parseColor("#999999"));
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setPadding(0, 0, 0, 40);

        Button loginBtn = new Button(this);
        loginBtn.setText("Sign in to SoundCloud");
        loginBtn.setBackgroundColor(Color.parseColor("#FF5500"));
        loginBtn.setTextColor(Color.WHITE);
        loginBtn.setTextSize(16);
        loginBtn.setPadding(40, 20, 40, 20);
        loginBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                webView.loadUrl("https://soundcloud.com/signin");
                hideLoginOverlay();
            }
        });

        Button skipBtn = new Button(this);
        skipBtn.setText("Skip (search only)");
        skipBtn.setBackgroundColor(Color.TRANSPARENT);
        skipBtn.setTextColor(Color.parseColor("#666666"));
        skipBtn.setTextSize(13);
        skipBtn.setPadding(40, 10, 40, 10);
        skipBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                isLoggedIn = true;
                hideLoginOverlay();
                webView.loadUrl(APP_URL);
            }
        });

        content.addView(title);
        content.addView(subtitle);
        content.addView(loginBtn);
        content.addView(skipBtn);
        overlay.addView(content, centerParams);

        loginOverlay = overlay;
        container.addView(overlay, new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ));
    }

    private void hideLoginOverlay() {
        if (loginOverlay != null) {
            container.removeView(loginOverlay);
            loginOverlay = null;
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private WebView createWebView() {
        WebView wv = new WebView(this);
        WebSettings settings = wv.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setUserAgentString(
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        );
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(wv, true);

        wv.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> callback, FileChooserParams params) {
                filePathCallback = callback;
                startActivityForResult(params.createIntent(), FILE_CHOOSER_REQUEST);
                return true;
            }
            @Override
            public void onPermissionRequest(PermissionRequest request) { request.grant(request.getResources()); }
        });

        return wv;
    }

    private void loadNextTrack() {
        isClickingPlaylist = false;
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
            "  function tryAdd(attempt) {" +
            "    attempt = attempt || 0;" +
            "    if (attempt > 8) {" +
            "      var allBtns = document.querySelectorAll('button');" +
            "      var info = [];" +
            "      for(var x=0;x<Math.min(allBtns.length,8);x++) info.push((allBtns[x].className||'').substring(0,30)+'|'+(allBtns[x].title||'')+'|'+(allBtns[x].getAttribute('aria-label')||''));" +
            "      toast('Btns: '+info.join(' ## '));" +
            "      AndroidBridge.onError('timeout'); return;" +
            "    }" +
            "    var moreBtn = null;" +
            "    var allBtns = document.querySelectorAll('button');" +
            "    for (var i=0; i<allBtns.length; i++) {" +
            "      var cls = (allBtns[i].className||'').toLowerCase();" +
            "      var ttl = (allBtns[i].title||'').toLowerCase();" +
            "      var lbl = (allBtns[i].getAttribute('aria-label')||'').toLowerCase();" +
            "      if (cls.includes('more') || ttl==='more' || lbl==='more') { moreBtn=allBtns[i]; break; }" +
            "    }" +
            "    if (!moreBtn) { window.scrollTo(0,100); setTimeout(function(){ tryAdd(attempt+1); }, 1200); return; }" +
            "    moreBtn.click();" +
            "    setTimeout(function() {" +
            "      var addBtn = null;" +
            "      var items = document.querySelectorAll('button, [role=menuitem], li');" +
            "      for (var j=0; j<items.length; j++) {" +
            "        var txt = (items[j].textContent||'').toLowerCase();" +
            "        if (txt.includes('add to playlist') || txt.includes('add to set')) { addBtn=items[j]; break; }" +
            "      }" +
            "      if (!addBtn) { toast('Add to playlist not found'); AndroidBridge.onError('no add btn'); return; }" +
            "      addBtn.click();" +
            "      setTimeout(function() {" +
            "        var plTitle = '" + pl + "';" +
            "        var target = null;" +
            "        var rows = document.querySelectorAll('li, [class*=item], [class*=row]');" +
            "        for (var k=0; k<rows.length; k++) {" +
            "          if ((rows[k].textContent||'').includes(plTitle)) {" +
            "            var btn = rows[k].querySelector('button');" +
            "            if (btn) { target=btn; break; }" +
            "          }" +
            "        }" +
            "        if (!target) {" +
            "          var allBtns2 = document.querySelectorAll('button');" +
            "          for (var m=0; m<allBtns2.length; m++) {" +
            "            if ((allBtns2[m].textContent||'').toLowerCase().includes('add to playlist')) { target=allBtns2[m]; break; }" +
            "          }" +
            "        }" +
            "        if (target) {" +
            "          target.click();" +
            "          toast('Track " + trackNum + " added!', true);" +
            "          setTimeout(function(){" +
            "            window.location.href='https://soundcloud.com';" +
            "            setTimeout(function(){ AndroidBridge.onPlaylistAdded(); }, 2000);" +
            "          }, 1500);" +
            "        } else {" +
            "          toast('Playlist not found');" +
            "          AndroidBridge.onError('no playlist');" +
            "        }" +
            "      }, 2000);" +
            "    }, 1000);" +
            "  }" +
            "  tryAdd(0);" +
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
        if (requestCode == FILE_CHOOSER_REQUEST && filePathCallback != null) {
            Uri[] results = null;
            if (resultCode == Activity.RESULT_OK && data != null) results = new Uri[]{data.getData()};
            filePathCallback.onReceiveValue(results);
            filePathCallback = null;
        }
    }

    @Override
    public void onBackPressed() {
        if (loginOverlay != null) {
            hideLoginOverlay();
            webView.loadUrl(APP_URL);
        } else if (isProcessing) {
            isProcessing = false;
            webView.loadUrl(APP_URL);
        } else if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}
