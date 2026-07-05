package com.windym.client.api;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;
import org.conscrypt.Conscrypt;
import java.security.Security;

import com.windym.client.model.Track;

/**
 * Singleton-хранилище состояния приложения.
 * Хранит API-клиент, текущий трек, токен.
 */
public class AppState {

    private static final String PREFS = "windym_prefs";
    private static final String KEY_TOKEN = "token";
    private static final String KEY_DEVICE_ID = "device_id";
    private static final String KEY_APP_UUID = "app_uuid";

    private static AppState instance;

    private YandexMusicApiClient apiClient;
    private Track currentTrack;
    private java.util.List<Track> currentQueue;
    private int currentIndex = -1;
    private boolean isWaveMode = false;
    private String waveSid = "";
    private String waveBatchId = "";
    private java.util.List<String> waveHistory = new java.util.ArrayList<>();

    private AppState() {}

    public static synchronized AppState get() {
        if (instance == null) {
            try {
                Security.insertProviderAt(Conscrypt.newProvider(), 1);
            } catch (Throwable ignored) {}
            instance = new AppState();
        }
        return instance;
    }

    // ─── Persistence ─────────────────────────────────────────────────

    public static void saveToken(Context ctx, String token, String deviceId, String appUuid) {
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        prefs.edit()
                .putString(KEY_TOKEN, obfuscate(token))
                .putString(KEY_DEVICE_ID, deviceId)
                .putString(KEY_APP_UUID, appUuid)
                .apply();
    }

    public static String loadToken(Context ctx) {
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String enc = prefs.getString(KEY_TOKEN, "");
        return deobfuscate(enc);
    }

    public static String loadDeviceId(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_DEVICE_ID, "");
    }

    public static String loadAppUuid(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_APP_UUID, "");
    }

    public static void clearToken(Context ctx) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().remove(KEY_TOKEN).apply();
    }

    private static String obfuscate(String s) {
        if (s == null || s.isEmpty()) return "";
        return Base64.encodeToString(s.getBytes(), Base64.DEFAULT);
    }

    private static String deobfuscate(String s) {
        if (s == null || s.isEmpty()) return "";
        try {
            return new String(Base64.decode(s, Base64.DEFAULT));
        } catch (Exception e) {
            return "";
        }
    }

    // ─── API Client ──────────────────────────────────────────────────

    public YandexMusicApiClient getApiClient() { return apiClient; }

    public void setApiClient(YandexMusicApiClient client) { this.apiClient = client; }

    public boolean isLoggedIn() { return apiClient != null && apiClient.getUid() != null; }

    // ─── Playback state ──────────────────────────────────────────────

    public Track getCurrentTrack() { return currentTrack; }
    public void setCurrentTrack(Track t) { this.currentTrack = t; }

    public java.util.List<Track> getCurrentQueue() { return currentQueue; }
    public void setCurrentQueue(java.util.List<Track> q) { this.currentQueue = q; }

    public int getCurrentIndex() { return currentIndex; }
    public void setCurrentIndex(int i) { this.currentIndex = i; }

    public boolean isWaveMode() { return isWaveMode; }
    public void setWaveMode(boolean wave) { this.isWaveMode = wave; }

    public String getWaveSid() { return waveSid; }
    public void setWaveSid(String s) { waveSid = s; }
    
    public String getWaveBatchId() { return waveBatchId; }
    public void setWaveBatchId(String b) { waveBatchId = b; }
    
    public java.util.List<String> getWaveHistory() { return waveHistory; }
    public void clearWaveHistory() { waveHistory.clear(); }
    public void addWaveHistory(String trackId) { if (!waveHistory.contains(trackId)) waveHistory.add(trackId); }
}
