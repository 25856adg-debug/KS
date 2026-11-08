package com.example.webbox; // 确保包名一致

import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import androidx.appcompat.app.AppCompatActivity;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class MainActivity extends AppCompatActivity {

    private WebView webView;

    // 👇 直接写死你的三个明文密钥 👇
    private static final String[] VALID_KEYS = {
            "djcunekckeucnfsddjducje",
            "cjfneuchndchjsudysjfjwjf",
            "djfjmdichejchejcyhwjcysnj"
    };

    private static final Set<String> validKeySet = new HashSet<>(Arrays.asList(VALID_KEYS));

    public static class JsBridge {
        @JavascriptInterface
        public String checkKey(String inputKey) {
            if (inputKey == null) return "false";
            // 纯纯的字符串比对
            return validKeySet.contains(inputKey.trim()) ? "true" : "false";
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        webView = findViewById(R.id.webview);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.addJavascriptInterface(new JsBridge(), "androidBridge");
        webView.loadUrl("file:///android_asset/index.html");
    }
}


