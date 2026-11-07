package com.example.webbox;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Message;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebStorage;
import android.widget.ProgressBar;
import android.widget.Toast;

import java.util.ArrayList;

public class MainActivity extends Activity {

    private static final String HOME_URL =
            "https://voao.online";

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

        webView.setWebViewClient(new WebViewClient() {

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

                progressBar.setVisibility(View.VISIBLE);
            }

            @Override
            public void onPageFinished(
                    WebView view,
                    String url) {

                progressBar.setVisibility(View.GONE);
                CookieManager.getInstance().flush();
            }

            @Override
            public void onReceivedError(
                    WebView view,
                    WebResourceRequest request,
                    android.webkit.WebResourceError error) {

                if (request.isForMainFrame()) {
                    progressBar.setVisibility(View.GONE);

                    Toast.makeText(
                            MainActivity.this,
                            "网页加载失败，请检查网络",
                            Toast.LENGTH_SHORT
                    ).show();
                }
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {

            // 页面加载进度
            @Override
            public void onProgressChanged(
                    WebView view,
                    int progress) {

                progressBar.setProgress(progress);

                progressBar.setVisibility(
                        progress >= 100
                                ? View.GONE
                                : View.VISIBLE
                );
            }

            // 网页文件上传
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

            // 网页视频全屏
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

            // 网页 target=_blank 和 window.open
            @Override
            public boolean onCreateWindow(
                    WebView view,
                    boolean dialog,
                    boolean userGesture,
                    Message resultMsg) {

                WebView popup = new WebView(MainActivity.this);

                configureWebView(popup);

                popup.setWebViewClient(new WebViewClient() {

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
                });

                popup.setWebChromeClient(new WebChromeClient());

                popupViews.add(popup);

                WebView.WebViewTransport transport =
                        (WebView.WebViewTransport)
                                resultMsg.obj;

                transport.setWebView(popup);

                resultMsg.sendToTarget();

                return true;
            }

            @Override
            public void onCloseWindow(WebView window) {

                popupViews.remove(window);
                window.destroy();
            }
        });

        // 文件下载
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

        // 打开固定首页
        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState);
        } else {
            webView.loadUrl(HOME_URL);
        }
    }

    // WebView 通用配置
    private void configureWebView(WebView view) {

        WebSettings settings = view.getSettings();

        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);

        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(true);

        settings.setJavaScriptCanOpenWindowsAutomatically(true);
        settings.setSupportMultipleWindows(true);

        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);

        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);

        settings.setMediaPlaybackRequiresUserGesture(false);

        settings.setMixedContentMode(
                WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        );

        settings.setLoadsImagesAutomatically(true);
        settings.setBlockNetworkImage(false);

        view.setLayerType(
                View.LAYER_TYPE_HARDWARE,
                null
        );

        CookieManager cookies = CookieManager.getInstance();

        cookies.setAcceptCookie(true);

        cookies.setAcceptThirdPartyCookies(
                view,
                true
        );
    }

    // HTTP、HTTPS 全部留在 APK 内
    private boolean handleUrl(Uri uri) {

        String scheme = uri.getScheme();

        if ("http".equalsIgnoreCase(scheme)
                || "https".equalsIgnoreCase(scheme)) {

            return false;
        }

        if ("about".equalsIgnoreCase(scheme)
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
                request.addRequestHeader(
                        "User-Agent",
                        userAgent
                );
            }

            String cookie =
                    CookieManager.getInstance()
                            .getCookie(url);

            if (cookie != null) {
                request.addRequestHeader(
                        "Cookie",
                        cookie
                );
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

        decor.setSystemUiVisibility(
                oldSystemUiVisibility
        );

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

                    int count =
                            data.getClipData().getItemCount();

                    results = new Uri[count];

                    for (int i = 0; i < count; i++) {

                        results[i] =
                                data.getClipData()
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
            hideCustomView(true);
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
