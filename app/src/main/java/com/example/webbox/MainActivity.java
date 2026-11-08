package com.example.webbox; // 确保包名和你的一致

import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import androidx.appcompat.app.AppCompatActivity;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class MainActivity extends AppCompatActivity {

    private WebView webView;

    // 👇 直接把你要的三个明文密码写在这里 👇
    private static final String[] VALID_KEYS = {
            "djcunekckeucnfsddjducje",
            "cjfneuchndchjsudysjfjwjf",
            "djfjmdichejchejcyhwjcysnj"
    };

    // 用 Set 存储，查找速度极快
    private static final Set<String> validKeySet = new HashSet<>(Arrays.asList(VALID_KEYS));

    // 静态内部类，负责给网页提供验证接口
    public static class JsBridge {
        @JavascriptInterface
        public String checkKey(String inputKey) {
            if (inputKey == null) return "false";
            
            // 直接拿网页传来的明文去比对
            boolean ok = validKeySet.contains(inputKey.trim());
            return ok ? "true" : "false";
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        webView = findViewById(R.id.webview);
        webView.getSettings().setJavaScriptEnabled(true);
        
        // 把接口注入给网页，名字还是叫 androidBridge
        webView.addJavascriptInterface(new JsBridge(), "androidBridge");

        // 直接加载本地网页
        webView.loadUrl("file:///android_asset/index.html");
    }
}


