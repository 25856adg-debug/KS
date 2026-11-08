package com.example.webbox;
import android.os.Bundle;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
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
    private final Set<String> validKeySet = new HashSet<>();
    private boolean keyListReady = false;

    // GitHub raw：存放【AES加密后的base64密文数组】，不再是明文密钥
    private static final String GITHUB_KEYS_URL =
            "https://raw.githubusercontent.com/oceeax/dk/main/sdd2t4tsfh5ghdsh5w/ffgdsf445%40wyg/asggtrgeg53y46ehadsgdj/keys.json";

    // ========= AES‑CBC 加密参数，写在APP代码，不要放到github、不要放到网页 =========
    // AES‑128：key必须16字节；iv必须16字节
    private static final String AES_KEY_RAW = "abcdefgh12345678";   // 16字符
    private static final String AES_IV_RAW  = "87654321hgfedcba"; // 16字符

    /** AES‑CBC PKCS5Padding 解密，返回原始字符串 */
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

    // 工具：加密，电脑上也可以用这个规则批量生成密文
    public static String aesCbcEncrypt(String plainText, String keyRaw, String ivRaw) throws Exception {
        byte[] keyBytes = keyRaw.getBytes(StandardCharsets.UTF_8);
        byte[] ivBytes  = ivRaw.getBytes(StandardCharsets.UTF_8);
        byte[] dataBytes = plainText.getBytes(StandardCharsets.UTF_8);

        SecretKeySpec keySpec = new SecretKeySpec(keyBytes, "AES");
        IvParameterSpec ivSpec = new IvParameterSpec(ivBytes);

        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec);
        byte[] cipherOut = cipher.doFinal(dataBytes);
        return Base64.encodeToString(cipherOut, Base64.NO_WRAP);
    }

    public class JsBridge {
        @JavascriptInterface
        public String checkKey(String inputKey) {
            if (!keyListReady) {
                return "loading";
            }
            if (inputKey == null) return "false";
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
                        "GitHub远程密钥库加载失败，网络异常", Toast.LENGTH_LONG).show());
                keyListReady = false;
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    keyListReady = false;
                    return;
                }
                String jsonStr = response.body().string();
                Gson gson = new Gson();
                Type listType = new TypeToken<List<String>>() {}.getType();
                // 拿到：base64密文字符串数组
                List<String> cipherBase64List = gson.fromJson(jsonStr, listType);

                validKeySet.clear();
                try {
                    for(String b64 : cipherBase64List){
                        String plainKey = aesCbcDecrypt(b64, AES_KEY_RAW, AES_IV_RAW);
                        validKeySet.add(plainKey);
                    }
                    keyListReady = true;
                }catch (Exception e){
                    runOnUiThread(()->Toast.makeText(MainActivity.this,"密文解密失败，AES参数不匹配",Toast.LENGTH_LONG).show());
                    keyListReady = false;
                }
            }
        });
    }
}

