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

        // 👇 新增：接收网页传来的试听请求，调用系统原生 TTS 👇
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

        // 初始化原生语音引擎
        textToSpeech = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                textToSpeech.setLanguage(Locale.CHINA);
            }
        });

        webView = findViewById(R.id.webview);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true); // 解决卡在验证成功
        settings.setMediaPlaybackRequiresUserGesture(false); // 👈 允许网页音视频自动播放
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW); // 允许HTTPS加载HTTP资源

        webView.addJavascriptInterface(new JsBridge(), "androidBridge");

        // 处理网页视频全屏播放
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

        // 强制所有链接在 WebView 内部打开
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                view.loadUrl(request.getUrl().toString());
                return true;
            }
        });

        webView.loadUrl("file:///android_asset/index.html");

        // 接管侧滑/返回键
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (fullscreenContainer != null) {
                    webView.getWebChromeClient().onHideCustomView(); // 退出全屏
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


