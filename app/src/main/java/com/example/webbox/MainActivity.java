package com.example.webbox; // 请确保这里和你的工程包名一致

import android.os.Bundle;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity {

    private WebView webView;
    private final OkHttpClient httpClient = new OkHttpClient();
    
    // 改为 static，方便静态内部类 JsBridge 访问
    private static final Set<String> validKeySet = new HashSet<>();
    private static boolean keyListReady = false;

    // GitHub raw：存放【AES加密后的base64密文数组】，不再是明文密钥
    private static final String GITHUB_KEYS_URL =
            "https://raw.githubusercontent.com/oceeax/dk/main/sdd2t4tsfh5ghdsh5w/ffgdsf445%40wyg/asggtrgeg53y46ehadsgdj/keys.json";

    // ========= AES‑CBC 加密参数 =========
    private static final String AES_KEY_RAW = "abcdefgh12345678";   // 16字符
    private static final String AES_IV_RAW  = "87654321hgfedcba"; // 16字符

    // AES 解密方法
    public static String aesCbcDecrypt(String base64CipherText, String keyRaw, String ivRaw) throws Exception {
        byte[] keyBytes = keyRaw.getBytes(StandardCharsets.UTF_8);
        byte[] ivBytes  = ivRaw.getBytes(StandardCharsets.UTF_8);
        byte[] cipherBytes = Base64.decode(base64CipherText, Base64.NO_WRAP);

        SecretKeySpec keySpec = new SecretKeySpec(keyBytes, "AES");
        IvParameterSpec ivSpec = new IvParameterSpec(ivBytes);

        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);
        byte[] plain = cipher.doFinal(cipherBytes);
        return new String(plain, StandardCharsets.UTF_8);
    }

    // 新增：计算 SHA-256 的方法（与网页端算法保持一致）
    public static String toSHA256(String str) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(str.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            return str; // 出错则返回原值，理论上不会发生
        }
    }

    // 改为 static 内部类，避免内存泄漏
    public static class JsBridge {
        @JavascriptInterface
        public String checkKey(String inputHash) {
            if (!keyListReady) {
                return "loading";
            }
            if (inputHash == null) return "false";
            // 网页端传来的已经是 SHA-256 哈希值，直接比对
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

        fetchRemoteKeyList();
        webView.loadUrl("file:///android_asset/index.html");
    }

    private void fetchRemoteKeyList() {
        Request request = new Request.Builder()
                .url(GITHUB_KEYS_URL)
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this,
                        "密钥库加载失败，请检查网络", Toast.LENGTH_LONG).show());
                keyListReady = false;
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful() || response.body() == null) {
                    keyListReady = false;
                    return;
                }
                String jsonStr = response.body().string();
                Gson gson = new Gson();
                Type listType = new TypeToken<List<String>>() {}.getType();
                
                try {
                    List<String> cipherBase64List = gson.fromJson(jsonStr, listType);
                    validKeySet.clear();
                    for(String b64 : cipherBase64List){
                        // 1. 解密拿到明文
                        String plainKey = aesCbcDecrypt(b64, AES_KEY_RAW, AES_IV_RAW);
                        // 2. 将明文转换为 SHA-256 哈希后存入集合 👇
                        validKeySet.add(toSHA256(plainKey.trim()));
                    }
                    keyListReady = true;
                } catch (Exception e) {
                    runOnUiThread(()->Toast.makeText(MainActivity.this,"密文解密失败，AES参数不匹配",Toast.LENGTH_LONG).show());
                    keyListReady = false;
                }
            }
        });
    }
}


