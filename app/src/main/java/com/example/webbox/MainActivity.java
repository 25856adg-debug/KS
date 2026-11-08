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
import android.os.SystemClock;
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
    private static final String HOME_URL = "https://covv.online";
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

    // 页面加载计时
    private long pageStartTime = 0;
    private String pageStartUrl = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        webView = findViewById(R.id.webView);
        progressBar = findViewById(R.id.progressBar);

        progressBar.setVisibility(View.GONE);

        configureWebView(webView);
        setupWebViewClient(webView);
        setupChromeClient(webView);

        // 下载功能
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

        // 长按图片下载
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

        // 触摸响应
        webView.setOnTouchListener((v, event) -> {

            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                v.requestFocus();
            }

            return false;
        });

        // 恢复网页
        if (savedInstanceState != null) {

            if (webView.restoreState(savedInstanceState) == null) {
                webView.loadUrl(HOME_URL);
            }

        } else {

            webView.loadUrl(HOME_URL);
        }
    }

    // =====================================
    // WebView 性能和基础配置
    // =====================================

    private void configureWebView(WebView view) {

        WebSettings settings = view.getSettings();

        // JavaScript
        settings.setJavaScriptEnabled(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(true);

        // 网页存储
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);

        // 文件访问
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(true);

        // 多窗口
        settings.setSupportMultipleWindows(true);

        // 页面显示
        settings.setLoadWithOverviewMode(false);
        settings.setUseWideViewPort(true);

        // 缩放
        settings.setSupportZoom(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);

        // 视频
        settings.setMediaPlaybackRequiresUserGesture(false);

        // HTTP 和 HTTPS 兼容
        settings.setMixedContentMode(
                WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        );

        // 图片
        settings.setLoadsImagesAutomatically(true);
        settings.setBlockNetworkImage(false);

        // 默认缓存策略
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);

        // 编码
        settings.setDefaultTextEncodingName("UTF-8");

        // 初始焦点
        settings.setNeedInitialFocus(true);

        // 预先绘制屏幕外区域，改善部分页面滚动表现
        settings.setOffscreenPreRaster(true);

        // 不设置强制硬件图层，使用系统默认硬件加速
        view.setFocusable(true);
        view.setFocusableInTouchMode(true);
        view.setClickable(true);
        view.setLongClickable(true);

        // Cookie
        CookieManager cookies = CookieManager.getInstance();

        cookies.setAcceptCookie(true);
        cookies.setAcceptThirdPartyCookies(view, true);
    }

    // =====================================
    // 页面控制
    // =====================================

    private void setupWebViewClient(WebView view) {

        view.setWebViewClient(new WebViewClient() {

            @Override
            public boolean shouldOverrideUrlLoading(
                    WebView view,
                    WebResourceRequest request) {

                return handleUrl(
                        request.getUrl().toString()
                );
            }

            @Override
            public boolean shouldOverrideUrlLoading(
                    WebView view,
                    String url) {

                return handleUrl(url);
            }

            @Override
            public void onPageStarted(
                    WebView view,
                    String url,
                    Bitmap favicon) {

                if (view == webView) {

                    pageStartTime = SystemClock.elapsedRealtime();
                    pageStartUrl = url;

                    progressBar.setProgress(0);
                    progressBar.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onPageFinished(
                    WebView view,
                    String url) {

                if (view == webView) {

                    progressBar.setVisibility(View.GONE);

                    if (pageStartTime > 0) {

                        long elapsed =
                                SystemClock.elapsedRealtime()
                                        - pageStartTime;

                        Log.i(TAG,
                                "页面加载完成，耗时："
                                        + elapsed
                                        + " ms，地址："
                                        + pageStartUrl);

                        pageStartTime = 0;
                    }
                }
            }

            @Override
            public void onReceivedError(
                    WebView view,
                    WebResourceRequest request,
                    android.webkit.WebResourceError error) {

                if (request.isForMainFrame()) {

                    Log.e(TAG,
                            "网页加载错误："
                                    + error.getDescription());

                    if (view == webView) {

                        progressBar.setVisibility(View.GONE);

                        Toast.makeText(
                                MainActivity.this,
                                "网页加载失败："
                                        + error.getDescription(),
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
                        "HTTP错误："
                                + response.getStatusCode()
                                + " "
                                + request.getUrl());
            }
        });
    }

    // =====================================
    // Chrome 功能
    // =====================================

    private void setupChromeClient(WebView view) {

        view.setWebChromeClient(new WebChromeClient() {

            @Override
            public void onProgressChanged(
                    WebView view,
                    int progress) {

                if (view == webView) {

                    progressBar.setProgress(progress);

                    if (progress >= 100) {
                        progressBar.setVisibility(View.GONE);
                    } else {
                        progressBar.setVisibility(View.VISIBLE);
                    }
                }
            }

            // 只记录警告和错误，减少日志干扰
            @Override
            public boolean onConsoleMessage(ConsoleMessage message) {

                if (message.messageLevel()
                        == ConsoleMessage.MessageLevel.ERROR) {

                    Log.e(TAG,
                            "JS错误："
                                    + message.message()
                                    + "，行号："
                                    + message.lineNumber());

                } else if (message.messageLevel()
                        == ConsoleMessage.MessageLevel.WARNING) {

                    Log.w(TAG,
                            "JS警告："
                                    + message.message());
                }

                return false;
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

            // 新窗口统一交给主 WebView
            @Override
            public boolean onCreateWindow(
                    WebView view,
                    boolean dialog,
                    boolean userGesture,
                    Message resultMsg) {

                if (view != webView) {
                    return false;
                }

                WebView popup = new WebView(MainActivity.this);

                configureWebView(popup);

                popup.setWebViewClient(new WebViewClient() {

                    private boolean transferred = false;

                    private boolean transferToMain(String url) {

                        if (transferred || url == null) {
                            return true;
                        }

                        Uri uri = Uri.parse(url);
                        String scheme = uri.getScheme();

                        if ("about".equalsIgnoreCase(scheme)
                                || "javascript".equalsIgnoreCase(scheme)
                                || "data".equalsIgnoreCase(scheme)
                                || "blob".equalsIgnoreCase(scheme)) {

                            return false;
                        }

                        transferred = true;

                        if ("http".equalsIgnoreCase(scheme)
                                || "https".equalsIgnoreCase(scheme)) {

                            webView.post(() -> webView.loadUrl(url));

                        } else {

                            handleUrl(url);
                        }

                        destroyPopup(popup);

                        return true;
                    }

                    @Override
                    public boolean shouldOverrideUrlLoading(
                            WebView view,
                            WebResourceRequest request) {

                        String url =
                                request.getUrl().toString();

                        Uri uri = request.getUrl();
                        String scheme = uri.getScheme();

                        if ("about".equalsIgnoreCase(scheme)
                                || "javascript".equalsIgnoreCase(scheme)
                                || "data".equalsIgnoreCase(scheme)
                                || "blob".equalsIgnoreCase(scheme)) {

                            return false;
                        }

                        return transferToMain(url);
                    }

                    @Override
                    public boolean shouldOverrideUrlLoading(
                            WebView view,
                            String url) {

                        Uri uri = Uri.parse(url);
                        String scheme = uri.getScheme();

                        if ("about".equalsIgnoreCase(scheme)
                                || "javascript".equalsIgnoreCase(scheme)
                                || "data".equalsIgnoreCase(scheme)
                                || "blob".equalsIgnoreCase(scheme)) {

                            return false;
                        }

                        return transferToMain(url);
                    }

                    @Override
                    public void onPageStarted(
                            WebView view,
                            String url,
                            Bitmap favicon) {

                        if (!transferred) {

                            Uri uri = Uri.parse(url);
                            String scheme = uri.getScheme();

                            if ("http".equalsIgnoreCase(scheme)
                                    || "https".equalsIgnoreCase(scheme)) {

                                transferToMain(url);
                            }
                        }
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
                destroyPopup(window);
            }
        });
    }

    // =====================================
    // 清理弹出窗口
    // =====================================

    private void destroyPopup(WebView popup) {

        if (popup == null) {
            return;
        }

        if (popupViews.remove(popup)) {

            try {
                popup.stopLoading();
                popup.setWebChromeClient(null);
                popup.setWebViewClient(null);
                popup.destroy();

            } catch (Exception e) {

                Log.e(TAG, "关闭弹出窗口失败", e);
            }
        }
    }

    // =====================================
    // URL 处理
    // =====================================

    private boolean handleUrl(String rawUrl) {

        if (rawUrl == null || rawUrl.trim().isEmpty()) {
            return false;
        }

        String url = rawUrl.trim();

        // 识别直接写入的 IPv4:端口
        if (url.matches(
                "^(\\d{1,3}\\.){3}\\d{1,3}:\\d+(/.*)?$"
        )) {

            url = "http://" + url;
        }

        Uri uri = Uri.parse(url);

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

    // =====================================
    // 下载文件
    // =====================================

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

    // =====================================
    // 退出视频全屏
    // =====================================

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

    // =====================================
    // 文件选择返回
    // =====================================

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

                    int count =
                            data.getClipData().getItemCount();

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

    // =====================================
    // 返回键
    // =====================================

    @Override
    public void onBackPressed() {

        if (customView != null) {
            hideCustomView(true);
            return;
        }

        if (webView != null && webView.canGoBack()) {
            webView.goBack();
            return;
        }

        super.onBackPressed();
    }

    // =====================================
    // 保存状态
    // =====================================

    @Override
    protected void onSaveInstanceState(Bundle outState) {

        if (webView != null) {
            webView.saveState(outState);
        }

        super.onSaveInstanceState(outState);
    }

    // =====================================
    // 前后台
    // =====================================

    @Override
    protected void onResume() {
        super.onResume();

        if (webView != null) {
            webView.onResume();
        }
    }

    @Override
    protected void onPause() {

        if (webView != null) {
            webView.onPause();
        }

        super.onPause();
    }

    // =====================================
    // 释放资源
    // =====================================

    @Override
    protected void onDestroy() {

        if (uploadCallback != null) {
            uploadCallback.onReceiveValue(null);
            uploadCallback = null;
        }

        if (customView != null) {
            hideCustomView(false);
        }

        while (!popupViews.isEmpty()) {

            WebView popup =
                    popupViews.remove(popupViews.size() - 1);

            try {
                popup.stopLoading();
                popup.destroy();

            } catch (Exception e) {
                Log.e(TAG, "释放弹出窗口失败", e);
            }
        }

        if (webView != null) {

            webView.stopLoading();
            webView.setWebChromeClient(null);
            webView.setWebViewClient(null);
            webView.destroy();

            webView = null;
        }

        super.onDestroy();
    }
}

