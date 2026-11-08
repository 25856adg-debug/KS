package com.example.webbox; // 确保包名一致

import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.view.View;
import android.view.ViewGroup;
import androidx.appcompat.app.AppCompatActivity;
import androidx.activity.OnBackPressedCallback;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public class MainActivity extends AppCompatActivity {

    private WebView webView;
    private TextToSpeech textToSpeech;
    private FrameLayout fullscreenContainer;
    private WebChromeClient.CustomViewCallback customViewCallback;

    // 你的三个明文密码
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

        // 原生语音引擎
        @JavascriptInterface
        public void speak(String text, float rate, float pitch) {
            if (textToSpeech != null && textToSpeech.isLanguageAvailable(Locale.CHINA) >= 0) {
                textToSpeech.setSpeechRate(rate);
                textToSpeech.setPitch(pitch);
                textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, "tts_utterance");
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 初始化原生语音
        textToSpeech = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                textToSpeech.setLanguage(Locale.CHINA);
            }
        });

        webView = findViewById(R.id.webview);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true); // 开启本地存储，防卡死
        settings.setMediaPlaybackRequiresUserGesture(false); // 允许网页视频音频自动播放
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW); 

        webView.addJavascriptInterface(new JsBridge(), "androidBridge");

        // 视频全屏处理
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
        });

        // 强制网页在内部打开
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                view.loadUrl(request.getUrl().toString());
                return true;
            }
        });

        // 加个 ?v=3 防止手机加载旧版缓存的网页
        webView.loadUrl("file:///android_asset/index.html?v=3");

        // 接管返回键
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
                        value -> {
                            if ("\"exit\"".equals(value)) finish();
                        }
                    );
                }
            }
        });
    }

    @Override
    protected void onDestroy() {
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
        }
        super.onDestroy();
    }
}

