package com.example.webbox;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Message;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.ConsoleMessage;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;
import android.widget.Toast;

import java.util.ArrayList;

public class MainActivity extends Activity {

    private static final String TAG = "WebBox";
    private static final String HOME_URL = "https://voao.online";
    private static final int FILE_REQUEST = 1001;

    private WebView webView;
    private ProgressBar progressBar;

    private ValueCallback<Uri[]> uploadCallback;

    private View customView;
    private WebChromeClient.CustomViewCallback customViewCallback;

    private int oldSystemUiVisibility;
    private int oldOrientation;

    private final ArrayList<WebView> popupViews =
            new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        webView = findViewById(R.id.webView);
        progressBar = findViewById(R.id.progressBar);

        configureWebView(webView);
        setupWebViewClient(webView);
        setupChromeClient(webView);

        webView.setDownloadListener(
                (url, userAgent, contentDisposition,
                 mimeType, contentLength) -> {

                    downloadFile(
                            url,
                            userAgent,
                            contentDisposition,
                            mimeType
                    );
                }
        );

        webView.setOnLongClickListener(v -> {

            WebView.HitTestResult result =
                    webView.getHitTestResult();

            int type = result.getType();

            if (type == WebView.HitTestResult.IMAGE_TYPE
                    || type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE) {

                String url = result.getExtra();

                if (url != null) {
                    downloadFile(
                            url,
                            webView.getSettings().getUserAgentString(),
                            null,
                            null
                    );
                    return true;
                }
            }

            return false;
        });

        webView.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                v.requestFocus();
            }
            return false;
        });

        if (savedInstanceState != null) {
            if (webView.restoreState(savedInstanceState) == null) {
                webView.loadUrl(HOME_URL);
            }
        } else {
            webView.loadUrl(HOME_URL);
        }
    }

    // WebView 基础配置
    private void configureWebView(WebView view) {

        WebSettings settings = view.getSettings();

        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);

        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(true);

        settings.setJavaScriptCanOpenWindowsAutomatically(true);
        settings.setSupportMultipleWindows(true);

        settings.setLoadWithOverviewMode(false);
        settings.setUseWideViewPort(true);

        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);

        settings.setMediaPlaybackRequiresUserGesture(false);

        settings.setMixedContentMode(
                WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        );

        settings.setLoadsImagesAutomatically(true);
        settings.setBlockNetworkImage(false);

        settings.setDefaultTextEncodingName("UTF-8");

        settings.setCacheMode(WebSettings.LOAD_DEFAULT);

        settings.setNeedInitialFocus(true);

        settings.setJavaScriptCanOpenWindowsAutomatically(true);

        settings.setSupportZoom(true);

        view.setFocusable(true);
        view.setFocusableInTouchMode(true);

        view.setClickable(true);
        view.setLongClickable(true);

        view.setLayerType(View.LAYER_TYPE_HARDWARE, null);

        CookieManager cookies = CookieManager.getInstance();

        cookies.setAcceptCookie(true);
        cookies.setAcceptThirdPartyCookies(view, true);
    }

    // WebView 页面控制
    private void setupWebViewClient(WebView view) {

        view.setWebViewClient(new WebViewClient() {

            @Override
            public boolean shouldOverrideUrlLoading(
                    WebView view,
                    WebResourceRequest request) {

                return handleUrl(request.getUrl());
            }

            @Override
            public boolean shouldOverrideUrlLoading(
                    WebView view,
                    String url) {

                return handleUrl(Uri.parse(url));
            }

            @Override
            public void onPageStarted(
                    WebView view,
                    String url,
                    Bitmap favicon) {

                if (view == webView) {
                    progressBar.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onPageFinished(
                    WebView view,
                    String url) {

                if (view == webView) {
                    progressBar.setVisibility(View.GONE);
                    CookieManager.getInstance().flush();
                }
            }

            @Override
            public void onReceivedError(
                    WebView view,
                    WebResourceRequest request,
                    android.webkit.WebResourceError error) {

                if (request.isForMainFrame()) {

                    Log.e(TAG,
                            "网页加载错误：" + error.getDescription());

                    if (view == webView) {
                        progressBar.setVisibility(View.GONE);

                        Toast.makeText(
                                MainActivity.this,
                                "网页加载失败：" + error.getDescription(),
                                Toast.LENGTH_LONG
                        ).show();
                    }
                }
            }

            @Override
            public void onReceivedHttpError(
                    WebView view,
                    WebResourceRequest request,
                    android.webkit.WebResourceResponse response) {

                Log.e(TAG,
                        "HTTP错误：" + response.getStatusCode()
                                + " " + request.getUrl());
            }
        });
    }

    // 网页浏览器功能
    private void setupChromeClient(WebView view) {

        view.setWebChromeClient(new WebChromeClient() {

            @Override
            public void onProgressChanged(
                    WebView view,
                    int progress) {

                if (view == webView) {
                    progressBar.setProgress(progress);

                    progressBar.setVisibility(
                            progress >= 100
                                    ? View.GONE
                                    : View.VISIBLE
                    );
                }
            }

            // JavaScript 控制台日志
            @Override
            public boolean onConsoleMessage(ConsoleMessage message) {

                Log.d(TAG,
                        "JS " + message.messageLevel()
                                + "：" + message.message()
                                + "，行号：" + message.lineNumber()
                                + "，来源：" + message.sourceId());

                return true;
            }

            // 文件上传
            @Override
            public boolean onShowFileChooser(
                    WebView view,
                    ValueCallback<Uri[]> callback,
                    FileChooserParams params) {

                if (uploadCallback != null) {
                    uploadCallback.onReceiveValue(null);
                }

                uploadCallback = callback;

                try {

                    Intent intent = params.createIntent();

                    startActivityForResult(
                            intent,
                            FILE_REQUEST
                    );

                } catch (Exception e) {

                    uploadCallback = null;
                    callback.onReceiveValue(null);

                    Toast.makeText(
                            MainActivity.this,
                            "无法打开文件选择器",
                            Toast.LENGTH_SHORT
                    ).show();
                }

                return true;
            }

            // 视频全屏
            @Override
            public void onShowCustomView(
                    View view,
                    CustomViewCallback callback) {

                if (customView != null) {
                    callback.onCustomViewHidden();
                    return;
                }

                customView = view;
                customViewCallback = callback;

                Window window = getWindow();
                View decor = window.getDecorView();

                oldSystemUiVisibility =
                        decor.getSystemUiVisibility();

                oldOrientation = getRequestedOrientation();

                window.addFlags(
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                );

                ((ViewGroup) decor).addView(
                        customView,
                        new ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                        )
                );

                webView.setVisibility(View.GONE);

                decor.setSystemUiVisibility(
                        View.SYSTEM_UI_FLAG_FULLSCREEN
                                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                );
            }

            @Override
            public void onHideCustomView() {
                hideCustomView(true);
            }

            // 新窗口转入主 WebView
            @Override
            public boolean onCreateWindow(
                    WebView view,
                    boolean dialog,
                    boolean userGesture,
                    Message resultMsg) {

                WebView popup = new WebView(MainActivity.this);

                configureWebView(popup);

                popup.setWebViewClient(new WebViewClient() {

                    private boolean openInMain(String url) {

                        if (url == null) {
                            return false;
                        }

                        Uri uri = Uri.parse(url);
                        String scheme = uri.getScheme();

                        if ("http".equalsIgnoreCase(scheme)
                                || "https".equalsIgnoreCase(scheme)) {

                            webView.post(() -> webView.loadUrl(url));

                            popupViews.remove(popup);
                            popup.stopLoading();
                            popup.destroy();

                            return true;
                        }

                        return handleUrl(uri);
                    }

                    @Override
                    public boolean shouldOverrideUrlLoading(
                            WebView view,
                            WebResourceRequest request) {

                        return openInMain(
                                request.getUrl().toString()
                        );
                    }

                    @Override
                    public boolean shouldOverrideUrlLoading(
                            WebView view,
                            String url) {

                        return openInMain(url);
                    }

                    @Override
                    public void onPageStarted(
                            WebView view,
                            String url,
                            Bitmap favicon) {

                        openInMain(url);
                    }
                });

                popup.setWebChromeClient(new WebChromeClient());

                popupViews.add(popup);

                WebView.WebViewTransport transport =
                        (WebView.WebViewTransport) resultMsg.obj;

                transport.setWebView(popup);
                resultMsg.sendToTarget();

                return true;
            }

            @Override
            public void onCloseWindow(WebView window) {

                popupViews.remove(window);

                window.stopLoading();
                window.destroy();
            }
        });
    }

    // URL 处理
    private boolean handleUrl(Uri uri) {

        String scheme = uri.getScheme();

        if ("http".equalsIgnoreCase(scheme)
                || "https".equalsIgnoreCase(scheme)
                || "about".equalsIgnoreCase(scheme)
                || "javascript".equalsIgnoreCase(scheme)
                || "data".equalsIgnoreCase(scheme)
                || "blob".equalsIgnoreCase(scheme)) {

            return false;
        }

        try {

            Intent intent = new Intent(
                    Intent.ACTION_VIEW,
                    uri
            );

            startActivity(intent);

        } catch (Exception e) {

            Toast.makeText(
                    this,
                    "没有找到可打开此链接的应用",
                    Toast.LENGTH_SHORT
            ).show();
        }

        return true;
    }

    // 下载文件
    private void downloadFile(
            String url,
            String userAgent,
            String contentDisposition,
            String mimeType) {

        try {

            Uri uri = Uri.parse(url);

            String scheme = uri.getScheme();

            if (!"http".equalsIgnoreCase(scheme)
                    && !"https".equalsIgnoreCase(scheme)) {

                Toast.makeText(
                        this,
                        "暂不支持此类下载地址",
                        Toast.LENGTH_SHORT
                ).show();

                return;
            }

            DownloadManager.Request request =
                    new DownloadManager.Request(uri);

            request.setNotificationVisibility(
                    DownloadManager.Request
                            .VISIBILITY_VISIBLE_NOTIFY_COMPLETED
            );

            if (userAgent != null) {
                request.addRequestHeader("User-Agent", userAgent);
            }

            String cookie =
                    CookieManager.getInstance().getCookie(url);

            if (cookie != null) {
                request.addRequestHeader("Cookie", cookie);
            }

            if (mimeType != null) {
                request.setMimeType(mimeType);
            }

            String fileName = URLUtil.guessFileName(
                    url,
                    contentDisposition,
                    mimeType
            );

            request.setDestinationInExternalPublicDir(
                    Environment.DIRECTORY_DOWNLOADS,
                    fileName
            );

            DownloadManager manager =
                    (DownloadManager) getSystemService(
                            Context.DOWNLOAD_SERVICE
                    );

            manager.enqueue(request);

            Toast.makeText(
                    this,
                    "下载任务已创建：" + fileName,
                    Toast.LENGTH_SHORT
            ).show();

        } catch (Exception e) {

            Toast.makeText(
                    this,
                    "下载失败：" + e.getMessage(),
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    // 退出视频全屏
    private void hideCustomView(boolean notify) {

        if (customView == null) {
            return;
        }

        ViewGroup decor =
                (ViewGroup) getWindow().getDecorView();

        decor.removeView(customView);

        customView = null;

        webView.setVisibility(View.VISIBLE);

        decor.setSystemUiVisibility(oldSystemUiVisibility);

        getWindow().clearFlags(
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        );

        setRequestedOrientation(oldOrientation);

        WebChromeClient.CustomViewCallback callback =
                customViewCallback;

        customViewCallback = null;

        if (notify && callback != null) {
            callback.onCustomViewHidden();
        }
    }

    // 文件选择返回
    @Override
    protected void onActivityResult(
            int requestCode,
            int resultCode,
            Intent data) {

        super.onActivityResult(
                requestCode,
                resultCode,
                data
        );

        if (requestCode == FILE_REQUEST
                && uploadCallback != null) {

            Uri[] results = null;

            if (resultCode == RESULT_OK && data != null) {

                if (data.getClipData() != null) {

                    int count = data.getClipData().getItemCount();

                    results = new Uri[count];

                    for (int i = 0; i < count; i++) {
                        results[i] = data.getClipData()
                                .getItemAt(i)
                                .getUri();
                    }

                } else if (data.getData() != null) {

                    results = new Uri[]{
                            data.getData()
                    };
                }
            }

            uploadCallback.onReceiveValue(results);
            uploadCallback = null;
        }
    }

    // 返回键
    @Override
    public void onBackPressed() {

        if (customView != null) {
            hideCustomView(true);
            return;
        }

        if (webView.canGoBack()) {
            webView.goBack();
            return;
        }

        super.onBackPressed();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {

        if (webView != null) {
            webView.saveState(outState);
        }

        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onDestroy() {

        if (uploadCallback != null) {
            uploadCallback.onReceiveValue(null);
            uploadCallback = null;
        }

        if (customView != null) {
            hideCustomView(false);
        }

        for (WebView popup : popupViews) {
            popup.stopLoading();
            popup.destroy();
        }

        popupViews.clear();

        if (webView != null) {
            webView.stopLoading();
            webView.destroy();
        }

        super.onDestroy();
    }
}

