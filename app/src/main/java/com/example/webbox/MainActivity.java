package com.example.webbox; // 确保包名一致

import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.speech.tts.TextToSpeech;
import android.webkit.DownloadListener;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.Toast;
import android.view.View;
import android.view.ViewGroup;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public class MainActivity extends AppCompatActivity {

    private WebView webView;
    private TextToSpeech textToSpeech;
    private FrameLayout fullscreenContainer;
    private WebChromeClient.CustomViewCallback customViewCallback;

    // 处理文件上传
    private ValueCallback<Uri[]> uploadMessage;
    private ActivityResultLauncher<Intent> fileChooserLauncher;

    private static final String[] VALID_KEYS = {
            "djcunekckeucnfsddjducje",
            "cjfneuchndchjsudysjfjwjf",
            "djfjmdichejchejcyhwjcysnj"
    };
    private static final Set<String> validKeySet = new HashSet<>(Arrays.asList(VALID_KEYS));

    public class JsBridge {
        @JavascriptInterface
        public String checkKey(String inputKey) {
            if (inputKey == null) return "false";
            return validKeySet.contains(inputKey.trim()) ? "true" : "false";
        }

        @JavascriptInterface
        public void speak(String text, float rate, float pitch) {
            if (textToSpeech != null && textToSpeech.isLanguageAvailable(Locale.CHINA) >= 0) {
                textToSpeech.setSpeechRate(rate);
                textToSpeech.setPitch(pitch);
                textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, "tts_utterance");
            }
        }

        // 原生下载接口
        @JavascriptInterface
        public void nativeDownload(String url, String fileName) {
            try {
                DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
                request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName);
                DownloadManager dm = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
                if (dm != null) {
                    dm.enqueue(request);
                    runOnUiThread(() -> Toast.makeText(MainActivity.this, "已加入下载队列", Toast.LENGTH_SHORT).show());
                }
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "下载失败", Toast.LENGTH_SHORT).show());
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 初始化文件选择器
        fileChooserLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (uploadMessage == null) return;
            Uri[] results = null;
            if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                String dataString = result.getData().getDataString();
                if (dataString != null) results = new Uri[]{Uri.parse(dataString)};
            }
            uploadMessage.onReceiveValue(results);
            uploadMessage = null;
        });

        textToSpeech = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) textToSpeech.setLanguage(Locale.CHINA);
        });

        webView = findViewById(R.id.webview);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false); 
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW); 

        webView.addJavascriptInterface(new JsBridge(), "androidBridge");

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onShowCustomView(View view, CustomViewCallback callback) {
                if (customViewCallback != null) callback.onCustomViewHidden();
                customViewCallback = callback;
                fullscreenContainer = new FrameLayout(MainActivity.this);
                fullscreenContainer.setBackgroundColor(0xFF000000);
                fullscreenContainer.addView(view, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                FrameLayout decor = (FrameLayout) getWindow().getDecorView();
                decor.addView(fullscreenContainer, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                webView.setVisibility(View.GONE);
            }

            @Override
            public void onHideCustomView() {
                if (fullscreenContainer != null) {
                    FrameLayout decor = (FrameLayout) getWindow().getDecorView();
                    decor.removeView(fullscreenContainer);
                    fullscreenContainer = null;
                    webView.setVisibility(View.VISIBLE);
                    if (customViewCallback != null) {
                        customViewCallback.onCustomViewHidden();
                        customViewCallback = null;
                    }
                }
            }

            // 处理网页里的 <input type="file">
            @Override
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback, FileChooserParams fileChooserParams) {
                if (uploadMessage != null) uploadMessage.onReceiveValue(null);
                uploadMessage = filePathCallback;
                try {
                    fileChooserLauncher.launch(fileChooserParams.createIntent());
                } catch (Exception e) {
                    uploadMessage = null;
                    return false;
                }
                return true;
            }
        });

        webView.setDownloadListener((url, userAgent, contentDisposition, mimetype, contentLength) -> {
            // 如果网页里有普通的 a 标签下载，转交给原生下载器
            try {
                DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
                request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                DownloadManager dm = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
                if (dm != null) {
                    dm.enqueue(request);
                    Toast.makeText(MainActivity.this, "开始下载...", Toast.LENGTH_SHORT).show();
                }
            } catch (Exception e) {
                Toast.makeText(MainActivity.this, "下载失败", Toast.LENGTH_SHORT).show();
            }
        });

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                view.loadUrl(request.getUrl().toString());
                return true;
            }
        });

        webView.loadUrl("file:///android_asset/index.html?v=5");

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (fullscreenContainer != null) {
                    webView.getWebChromeClient().onHideCustomView();
                } else if (webView.canGoBack()) {
                    webView.goBack();
                } else {
                    webView.evaluateJavascript(
                        "if(location.hash !== '#home' && location.hash !== '') { window.location.hash = 'home'; 'handled'; } else { 'exit'; }",
                        value -> { if ("\"exit\"".equals(value)) finish(); }
                    );
                }
            }
        });
    }

    @Override
    protected void onDestroy() {
        if (textToSpeech != null) { textToSpeech.stop(); textToSpeech.shutdown(); }
        super.onDestroy();
    }
}


