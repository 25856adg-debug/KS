package com.example.webbox;

import android.app.*;
import android.os.*;
import android.content.*;
import android.net.Uri;
import android.webkit.*;
import android.view.*;
import android.widget.*;
import android.graphics.Bitmap;
import android.provider.OpenableColumns;
import java.util.*;

public class MainActivity extends Activity {

    private WebView webView;
    private ProgressBar progressBar;
    private ValueCallback<Uri[]> uploadCallback;
    private static final int FILE_REQUEST = 1001;
    private View customView;
    private WebChromeClient.CustomViewCallback customViewCallback;
    private int oldSystemUiVisibility;

    private static final String HOME_URL =
            "https://voao.online";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        webView = findViewById(R.id.webView);
        progressBar = findViewById(R.id.progressBar);

        WebSettings settings = webView.getSettings();

        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(true);
        settings.setSupportMultipleWindows(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setMixedContentMode(
                WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        );

        CookieManager cookies = CookieManager.getInstance();
        cookies.setAcceptCookie(true);
        cookies.setAcceptThirdPartyCookies(webView, true);

        webView.setWebViewClient(new WebViewClient() {

            @Override
            public boolean shouldOverrideUrlLoading(
                    WebView view, WebResourceRequest request) {
                return loadInsideApp(view, request.getUrl());
            }

            @Override
            public boolean shouldOverrideUrlLoading(
                    WebView view, String url) {
                return loadInsideApp(view, Uri.parse(url));
            }

            @Override
            public void onPageStarted(
                    WebView view, String url, Bitmap favicon) {
                progressBar.setVisibility(View.VISIBLE);
            }

            @Override
            public void onPageFinished(
                    WebView view, String url) {
                progressBar.setVisibility(View.GONE);
                CookieManager.getInstance().flush();
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {

            @Override
            public void onProgressChanged(
                    WebView view, int progress) {
                progressBar.setProgress(progress);
                progressBar.setVisibility(
                        progress >= 100 ? View.GONE : View.VISIBLE
                );
            }

            @Override
            public boolean onShowFileChooser(
                    WebView view,
                    ValueCallback<Uri[]> callback,
                    FileChooserParams params) {

                if (uploadCallback != null) {
                    uploadCallback.onReceiveValue(null);
                }

                uploadCallback = callback;

                Intent intent = params.createIntent();
                try {
                    startActivityForResult(intent, FILE_REQUEST);
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

            @Override
            public boolean onCreateWindow(
                    WebView view,
                    boolean dialog,
                    boolean userGesture,
                    Message resultMsg) {

                WebView.HitTestResult hit =
                        view.getHitTestResult();

                String url = hit.getExtra();

                if (url != null) {
                    view.loadUrl(url);
                }

                return false;
            }
        });

        webView.setDownloadListener(
                (url, userAgent, contentDisposition,
                 mimeType, contentLength) -> {

                    try {
                        DownloadManager.Request request =
                                new DownloadManager.Request(
                                        Uri.parse(url)
                                );

                        request.setMimeType(mimeType);
                        request.addRequestHeader(
                                "User-Agent", userAgent
                        );

                        String cookie =
                                CookieManager.getInstance()
                                .getCookie(url);

                        if (cookie != null) {
                            request.addRequestHeader(
                                    "Cookie", cookie
                            );
                        }

                        request.setNotificationVisibility(
                                DownloadManager.Request
                                .VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                        );

                        request.setDestinationInExternalPublicDir(
                                Environment.DIRECTORY_DOWNLOADS,
                                URLUtil.guessFileName(
                                        url,
                                        contentDisposition,
                                        mimeType
                                )
                        );

                        DownloadManager manager =
                                (DownloadManager) getSystemService(
                                        DOWNLOAD_SERVICE
                                );

                        manager.enqueue(request);

                        Toast.makeText(
                                this,
                                "已加入下载任务",
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
        );

        webView.loadUrl(HOME_URL);
    }

    private boolean loadInsideApp(
            WebView view, Uri uri) {

        String scheme = uri.getScheme();

        if ("http".equalsIgnoreCase(scheme)
                || "https".equalsIgnoreCase(scheme)) {
            view.loadUrl(uri.toString());
            return true;
        }

        if ("about".equalsIgnoreCase(scheme)
                || "blob".equalsIgnoreCase(scheme)
                || "javascript".equalsIgnoreCase(scheme)
                || "data".equalsIgnoreCase(scheme)) {
            return false;
        }

        try {
            Intent intent = new Intent(
                    Intent.ACTION_VIEW, uri
            );
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(
                    this,
                    "无法打开此链接",
                    Toast.LENGTH_SHORT
            ).show();
        }

        return true;
    }

    @Override
    protected void onActivityResult(
            int requestCode,
            int resultCode,
            Intent data) {

        super.onActivityResult(
                requestCode, resultCode, data
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
                                .getItemAt(i).getUri();
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

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        if (uploadCallback != null) {
            uploadCallback.onReceiveValue(null);
            uploadCallback = null;
        }

        webView.destroy();
        super.onDestroy();
    }
}
