package com.example.webbox; // 确保和你的包名一致

import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity {

    private WebView webView;
    private final OkHttpClient httpClient = new OkHttpClient();
    
    // 存放从网络拉取下来的哈希值
    private static final Set<String> validKeySet = new HashSet<>();
    private static boolean keyListReady = false;

    // 👇 已经替换为你提供的腾讯云 COS 链接 👇
    private static final String REMOTE_KEYS_URL =
            "https://wikwkwwkiowkwk-1318545521.cos.ap-guangzhou.myqcloud.com/gjfncjhxjsjrky/keys.json?q-sign-algorithm=sha1&q-ak=AKIDBAwipQpAJNL7k9RRFo9F7kpgIgpkTyEJ&q-sign-time=1794153155;1794156755&q-key-time=1794153155;1794156755&q-header-list=host&q-url-param-list=&q-signature=5c0c8fe2d1207c91a799efcb591b0d7597c6ade4";

    // 静态内部类，防止内存泄漏
    public static class JsBridge {
        @JavascriptInterface
        public String checkKey(String inputHash) {
            if (!keyListReady) {
                return "loading"; // 告诉网页，列表还没下载好
            }
            if (inputHash == null) return "false";
            
            // 网页传过来的是 SHA-256，直接比对
            boolean ok = validKeySet.contains(inputHash.trim());
            return ok ? "true" : "false";
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        webView = findViewById(R.id.webview);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.addJavascriptInterface(new JsBridge(), "androidBridge");

        fetchRemoteKeyList(); // 启动时去腾讯云拉取哈希列表
        webView.loadUrl("file:///android_asset/index.html");
    }

    private void fetchRemoteKeyList() {
        Request request = new Request.Builder()
                .url(REMOTE_KEYS_URL)
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this,
                        "网络异常，无法连接到密钥库", Toast.LENGTH_LONG).show());
                keyListReady = false;
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                // 如果链接过期（403）或不存在（404），这里就会捕获到
                if (!response.isSuccessful() || response.body() == null) {
                    runOnUiThread(() -> Toast.makeText(MainActivity.this,
                            "密钥库下载失败，请检查链接是否过期", Toast.LENGTH_LONG).show());
                    keyListReady = false;
                    return;
                }
                
                String jsonStr = response.body().string();
                Gson gson = new Gson();
                Type listType = new TypeToken<List<String>>() {}.getType();
                
                try {
                    // 直接解析 JSON 数组，不需要解密了
                    List<String> hashList = gson.fromJson(jsonStr, listType);
                    
                    validKeySet.clear();
                    if (hashList != null) {
                        for (String hash : hashList) {
                            validKeySet.add(hash.trim()); // 把哈希值丢进集合
                        }
                        keyListReady = true;
                    }
                } catch (Exception e) {
                    runOnUiThread(() -> Toast.makeText(MainActivity.this, "JSON 格式错误，请检查 keys.json", Toast.LENGTH_LONG).show());
                    keyListReady = false;
                }
            }
        });
    }
}

