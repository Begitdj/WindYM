package com.windym.client.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import com.windym.client.R;
import com.windym.client.api.AppState;
import com.windym.client.api.YandexMusicApiClient;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Экран аутентификации.
 * Поддерживает:
 * - Ввод OAuth-токена вручную
 * - Открытие браузера для получения токена
 * - Автоматический вход при сохранённом токене
 */
public class AuthActivity extends AppCompatActivity {

    private static final String OAUTH_URL =
            "https://oauth.yandex.ru/authorize?response_type=token&client_id=97fe03033fa34407ac9bcf91d5afed5b";

    private EditText tokenInput;
    private Button btnLogin;
    private Button btnBrowser;
    private TextView authMessage;
    private ProgressBar authProgress;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_auth);

        tokenInput = findViewById(R.id.tokenInput);
        btnLogin = findViewById(R.id.btnLogin);
        btnBrowser = findViewById(R.id.btnBrowser);
        authMessage = findViewById(R.id.authMessage);
        authProgress = findViewById(R.id.authProgress);

        btnLogin.setOnClickListener(v -> doLogin());
        btnBrowser.setOnClickListener(v -> openBrowser());

        tokenInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                doLogin();
                return true;
            }
            return false;
        });

        // Auto-login if token saved
        String savedToken = AppState.loadToken(this);
        if (!savedToken.isEmpty()) {
            tokenInput.setText(savedToken);
            doLogin();
        }

        // Handle OAuth redirect (windym://oauth#access_token=...)
        handleOAuthRedirect(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleOAuthRedirect(intent);
    }

    private void handleOAuthRedirect(Intent intent) {
        if (intent == null) return;
        Uri data = intent.getData();
        if (data == null) return;
        String fragment = data.getFragment();
        if (fragment != null && fragment.contains("access_token=")) {
            // Extract token from URL fragment
            String token = null;
            for (String part : fragment.split("&")) {
                if (part.startsWith("access_token=")) {
                    token = part.substring("access_token=".length());
                    break;
                }
            }
            if (token != null && !token.isEmpty()) {
                tokenInput.setText(token);
                doLogin();
            }
        }
    }

    private void doLogin() {
        String token = tokenInput.getText() != null ? tokenInput.getText().toString().trim() : "";
        if (TextUtils.isEmpty(token)) {
            authMessage.setText("Введите токен");
            authMessage.setTextColor(getResources().getColor(R.color.error));
            return;
        }

        setLoading(true);
        authMessage.setText("");

        String deviceId = AppState.loadDeviceId(this);
        String appUuid = AppState.loadAppUuid(this);

        executor.execute(() -> {
            try {
                YandexMusicApiClient client = new YandexMusicApiClient(token, deviceId, appUuid);
                Long uid = client.fetchUid();

                runOnUiThread(() -> {
                    setLoading(false);
                    if (uid != null) {
                        // Success
                        AppState.get().setApiClient(client);
                        AppState.saveToken(this, token,
                                client.getDeviceId(), client.getAppUuid());

                        authMessage.setText("✓ Вход выполнен (uid: " + uid + ")");
                        authMessage.setTextColor(getResources().getColor(R.color.success));

                        // Go to main
                        startActivity(new Intent(AuthActivity.this, MainActivity.class));
                        finish();
                    } else {
                        authMessage.setText("✗ Неверный токен или ошибка сети");
                        authMessage.setTextColor(getResources().getColor(R.color.error));
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    setLoading(false);
                    authMessage.setText("✗ Ошибка: " + e.getMessage());
                    authMessage.setTextColor(getResources().getColor(R.color.error));
                });
            }
        });
    }

    private void openBrowser() {
        authMessage.setText("Браузер открыт. Скопируйте access_token из адресной строки.");
        authMessage.setTextColor(getResources().getColor(R.color.text_secondary));
        Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(OAUTH_URL));
        startActivity(browserIntent);
    }

    private void setLoading(boolean loading) {
        authProgress.setVisibility(loading ? View.VISIBLE : View.GONE);
        btnLogin.setEnabled(!loading);
        btnBrowser.setEnabled(!loading);
        tokenInput.setEnabled(!loading);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }
}
