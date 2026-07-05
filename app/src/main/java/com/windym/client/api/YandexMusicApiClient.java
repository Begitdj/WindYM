package com.windym.client.api;

import android.util.Log;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.windym.client.model.Playlist;
import com.windym.client.model.Track;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import okhttp3.ConnectionSpec;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.TlsVersion;

/**
 * Клиент API Яндекс Музыки.
 * Имитирует Electron-клиент v5.78.7 для обхода API-ограничений.
 *
 * Endpoint: api.music.yandex.net
 * Auth: OAuth token (y0_Ag...)
 */
public class YandexMusicApiClient {

    private static final String TAG = "YandexMusicApi";
    private static final String BASE_HOST = "api.music.yandex.net";
    private static final String BASE_URL = "https://" + BASE_HOST;

    // Electron client UA — imitation of desktop app
    private static final String ELECTRON_UA =
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "YandexMusic/5.78.7 Chrome/126.0.6478.234 Electron/31.7.7 Safari/537.36";

    private final OkHttpClient httpClient;
    private final String token;
    private final String deviceId;
    private final String appUuid;

    private Long cachedUid = null;

    public YandexMusicApiClient(String token, String deviceId, String appUuid) {
        this.token = token;
        this.deviceId = deviceId != null && !deviceId.isEmpty() ? deviceId : generateDeviceId();
        this.appUuid = appUuid != null && !appUuid.isEmpty() ? appUuid : UUID.randomUUID().toString();
        this.httpClient = buildHttpClient();
    }

    // ─── HTTP Client with TLS 1.2/1.3 ────────────────────────────────

    private OkHttpClient buildHttpClient() {
        // Initialize Conscrypt for modern TLS on old Android
        try {
            java.security.Security.insertProviderAt(org.conscrypt.Conscrypt.newProvider(), 1);
        } catch (Throwable ignored) {}

        // Enforce TLS 1.2 and 1.3 (modern certificates only)
        ConnectionSpec modernTls = new ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
                .tlsVersions(TlsVersion.TLS_1_3, TlsVersion.TLS_1_2)
                .build();

        return new OkHttpClient.Builder()
                .connectionSpecs(java.util.Arrays.asList(modernTls))
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .followRedirects(true)
                .build();
    }

    // ─── Request helpers ─────────────────────────────────────────────

    private Request.Builder baseRequest(String url) {
        String deviceInfo = "os=Linux; os_version=; manufacturer=; model=; clid=0; " +
                "device_id=" + deviceId + "; uuid=" + appUuid;
        return new Request.Builder()
                .url(url)
                .header("User-Agent", ELECTRON_UA)
                .header("Accept", "application/json; charset=utf-8")
                .header("Accept-Language", "ru-RU,ru;q=0.9,en-US;q=0.8,en;q=0.7")
                .header("Origin", "https://music.yandex.ru")
                .header("Referer", "https://music.yandex.ru/")
                .header("Sec-Fetch-Dest", "empty")
                .header("Sec-Fetch-Mode", "cors")
                .header("Sec-Fetch-Site", "same-site")
                .header("X-Yandex-Music-Client", "YandexMusicDesktopApp/Linux")
                .header("X-Yandex-Music-Device", deviceInfo)
                .header("Authorization", "OAuth " + token);
    }

    private JsonObject getJson(String url) throws IOException {
        Request req = baseRequest(url).get().build();
        try (Response resp = httpClient.newCall(req).execute()) {
            if (!resp.isSuccessful()) {
                Log.e(TAG, "HTTP error: " + resp.code() + " for " + url);
                return null;
            }
            String body = resp.body() != null ? resp.body().string() : null;
            if (body == null || body.isEmpty()) return null;
            return JsonParser.parseString(body).getAsJsonObject();
        }
    }

    private JsonObject postJson(String url, String jsonBody) throws IOException {
        RequestBody body = RequestBody.create(
                MediaType.parse("application/json; charset=utf-8"), jsonBody);
        Request req = baseRequest(url).post(body).build();
        try (Response resp = httpClient.newCall(req).execute()) {
            if (!resp.isSuccessful()) {
                Log.e(TAG, "POST error: " + resp.code() + " for " + url);
                return null;
            }
            String respBody = resp.body() != null ? resp.body().string() : null;
            if (respBody == null || respBody.isEmpty()) return null;
            return JsonParser.parseString(respBody).getAsJsonObject();
        }
    }

    private JsonObject postForm(String url, String formData) throws IOException {
        RequestBody body = RequestBody.create(
                MediaType.parse("application/x-www-form-urlencoded; charset=utf-8"), formData);
        Request req = baseRequest(url).post(body).build();
        try (Response resp = httpClient.newCall(req).execute()) {
            if (!resp.isSuccessful()) {
                Log.e(TAG, "POST form error: " + resp.code() + " for " + url);
                return null;
            }
            String respBody = resp.body() != null ? resp.body().string() : null;
            if (respBody == null || respBody.isEmpty()) return null;
            return JsonParser.parseString(respBody).getAsJsonObject();
        }
    }

    // ─── Account ──────────────────────────────────────────────────────

    /**
     * Получает UID аккаунта из /account/status.
     * Возвращает uid или null при ошибке.
     */
    public Long fetchUid() {
        if (cachedUid != null) return cachedUid;
        try {
            JsonObject resp = getJson(BASE_URL + "/account/status");
            if (resp == null) return null;
            JsonObject result = safeObj(resp, "result");
            JsonObject account = safeObj(result, "account");
            if (account != null && account.has("uid")) {
                cachedUid = account.get("uid").getAsLong();
                return cachedUid;
            }
        } catch (Exception e) {
            Log.e(TAG, "fetchUid error", e);
        }
        return null;
    }

    public Long getUid() {
        return cachedUid != null ? cachedUid : fetchUid();
    }

    // ─── Search ───────────────────────────────────────────────────────

    /**
     * Поиск треков.
     * Endpoint: GET /search?text=...&type=track&page=0
     */
    public List<Track> searchTracks(String query, int page) {
        List<Track> result = new ArrayList<>();
        try {
            String url = BASE_URL + "/search?text=" + urlEncode(query)
                    + "&type=track&page=" + page + "&nocorrect=false";
            JsonObject resp = getJson(url);
            if (resp == null) return result;
            JsonObject res = safeObj(resp, "result");
            JsonObject tracks = safeObj(res, "tracks");
            JsonArray arr = safeArr(tracks, "results");
            if (arr == null) return result;
            for (JsonElement el : arr) {
                Track t = parseTrack(el.getAsJsonObject());
                if (t != null) result.add(t);
            }
        } catch (Exception e) {
            Log.e(TAG, "searchTracks error", e);
        }
        return result;
    }

    // ─── Liked Tracks ────────────────────────────────────────────────

    /**
     * Получает список понравившихся треков.
     * Endpoint: GET /users/{uid}/likes/tracks
     */
    public List<Track> getLikedTracks() {
        List<Track> result = new ArrayList<>();
        Long uid = getUid();
        if (uid == null) return result;
        try {
            String url = BASE_URL + "/users/" + uid + "/likes/tracks?pageSize=2000";
            JsonObject resp = getJson(url);
            if (resp == null) return result;

            JsonObject res = safeObj(resp, "result");
            JsonObject library = safeObj(res, "library");
            JsonArray items = safeArr(library, "tracks");
            if (items == null || items.size() == 0) return result;

            // Collect IDs
            List<String> ids = new ArrayList<>();
            for (JsonElement el : items) {
                JsonObject obj = el.getAsJsonObject();
                if (obj.has("id")) ids.add(obj.get("id").getAsString());
            }

            // Batch fetch track details (up to 300 per request)
            for (int i = 0; i < ids.size(); i += 300) {
                int end = Math.min(i + 300, ids.size());
                List<String> batch = ids.subList(i, end);
                StringBuilder sb = new StringBuilder();
                for (int j = 0; j < batch.size(); j++) {
                    if (j > 0) sb.append(",");
                    sb.append(batch.get(j));
                }
                String tracksUrl = BASE_URL + "/tracks?trackIds=" + sb;
                JsonObject tracksResp = getJson(tracksUrl);
                if (tracksResp == null) continue;
                JsonArray tracksArr = safeArr(tracksResp, "result");
                if (tracksArr == null) continue;
                for (JsonElement el : tracksArr) {
                    Track t = parseTrack(el.getAsJsonObject());
                    if (t != null) {
                        t.liked = true;
                        result.add(t);
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "getLikedTracks error", e);
        }
        return result;
    }

    /**
     * Поставить лайк треку.
     * Endpoint: POST /users/{uid}/likes/tracks/{tid}/add
     */
    public boolean likeTrack(String trackId) {
        Long uid = getUid();
        if (uid == null) return false;
        try {
            String url = BASE_URL + "/users/" + uid + "/likes/tracks/add";
            JsonObject resp = postForm(url, "track-id=" + trackId);
            return resp != null;
        } catch (Exception e) {
            Log.e(TAG, "likeTrack error", e);
            return false;
        }
    }

    /**
     * Убрать лайк с трека.
     * Endpoint: POST /users/{uid}/likes/tracks/{tid}/remove
     */
    public boolean unlikeTrack(String trackId) {
        Long uid = getUid();
        if (uid == null) return false;
        try {
            String url = BASE_URL + "/users/" + uid + "/likes/tracks/remove";
            JsonObject resp = postForm(url, "track-ids=" + trackId);
            return resp != null;
        } catch (Exception e) {
            Log.e(TAG, "unlikeTrack error", e);
            return false;
        }
    }

    // ─── Playlists ───────────────────────────────────────────────────

    /**
     * Список плейлистов пользователя.
     * Endpoint: GET /users/{uid}/playlists/list
     */
    public List<Playlist> getPlaylists() {
        List<Playlist> result = new ArrayList<>();
        Long uid = getUid();
        if (uid == null) return result;
        try {
            String url = BASE_URL + "/users/" + uid + "/playlists/list";
            JsonObject resp = getJson(url);
            if (resp == null) return result;
            JsonArray arr = safeArr(resp, "result");
            if (arr == null) return result;
            for (JsonElement el : arr) {
                Playlist p = parsePlaylist(el.getAsJsonObject());
                if (p != null) result.add(p);
            }
        } catch (Exception e) {
            Log.e(TAG, "getPlaylists error", e);
        }
        return result;
    }

    /**
     * Треки плейлиста.
     * Endpoint: GET /users/{uid}/playlists/{kind}?richTracks=true
     */
    public List<Track> getPlaylistTracks(int kind, long ownerUid) {
        List<Track> result = new ArrayList<>();
        try {
            String url = BASE_URL + "/users/" + ownerUid + "/playlists/" + kind + "?richTracks=true";
            JsonObject resp = getJson(url);
            if (resp == null) return result;
            JsonObject res = safeObj(resp, "result");
            JsonArray arr = safeArr(res, "tracks");
            if (arr == null) return result;
            for (JsonElement el : arr) {
                JsonObject item = el.getAsJsonObject();
                JsonObject trackObj = safeObj(item, "track");
                Track t = parseTrack(trackObj);
                if (t != null) result.add(t);
            }
        } catch (Exception e) {
            Log.e(TAG, "getPlaylistTracks error", e);
        }
        return result;
    }

    // ─── My Wave (Rotor) ─────────────────────────────────────────────

    /**
     * Запускает сессию Моей волны.
     * Endpoint: POST /rotor/session/new
     * Body: {"seeds":["user:onyourwave"],"queue":[],"includeTracksInResponse":true,"language":"ru"}
     */
    public WaveSession startWave() {
        try {
            String body = "{\"seeds\":[\"user:onyourwave\"]," +
                    "\"queue\":[]," +
                    "\"includeTracksInResponse\":true," +
                    "\"language\":\"ru\"}";
            JsonObject resp = postJson(BASE_URL + "/rotor/session/new", body);
            if (resp == null) return null;
            JsonObject result = safeObj(resp, "result");
            if (result == null) return null;

            String sessionId = null;
            if (result.has("radioSessionId"))
                sessionId = result.get("radioSessionId").getAsString();
            else if (result.has("sessionId"))
                sessionId = result.get("sessionId").getAsString();

            String batchId = "";
            if (result.has("batchId"))
                batchId = result.get("batchId").getAsString();

            Track track = extractWaveTrack(result, "");
            return new WaveSession(sessionId, batchId, track);
        } catch (Exception e) {
            Log.e(TAG, "startWave error", e);
            return null;
        }
    }

    /**
     * Следующий трек волны.
     * Endpoint: POST /rotor/session/{sid}/tracks
     */
    public WaveSession nextWaveTrack(String sessionId, String batchId, Track currentTrack, boolean isSkip, java.util.List<String> waveHistory) {
        if (sessionId == null || sessionId.isEmpty()) return null;
        try {
            String feedbackType = isSkip ? "skip" : "trackFinished";
            String trackId = (currentTrack != null && currentTrack.id != null) ? currentTrack.id : "";
            String albumId = (currentTrack != null && currentTrack.albumId != null) ? currentTrack.albumId : "";
            
            StringBuilder queueStr = new StringBuilder("[");
            for (int i = 0; i < waveHistory.size(); i++) {
                queueStr.append("\"").append(waveHistory.get(i)).append("\"");
                if (i < waveHistory.size() - 1) queueStr.append(",");
            }
            if (!trackId.isEmpty()) {
                String curItem = trackId + (albumId.isEmpty() ? "" : ":" + albumId);
                if (waveHistory.size() > 0) queueStr.append(",");
                queueStr.append("\"").append(curItem).append("\"");
            }
            queueStr.append("]");
            
            String body = "{\"queue\":" + queueStr + ",\"language\":\"ru\"," +
                    "\"feedback\":{\"type\":\"" + feedbackType + "\",\"batchId\":\"" + batchId + "\"" +
                    (trackId.isEmpty() ? "" : ",\"trackId\":\"" + trackId + "\"") +
                    (albumId.isEmpty() ? "" : ",\"albumId\":\"" + albumId + "\"") +
                    ",\"totalPlayedSeconds\":10}}";
            JsonObject resp = postJson(BASE_URL + "/rotor/session/" + sessionId + "/tracks", body);
            if (resp == null) return null;
            JsonObject result = safeObj(resp, "result");
            
            String newBatchId = batchId;
            if (result != null && result.has("batchId")) {
                newBatchId = result.get("batchId").getAsString();
            }
            
            Track nextTrack = extractWaveTrack(result, trackId);
            return new WaveSession(sessionId, newBatchId, nextTrack);
        } catch (Exception e) {
            Log.e(TAG, "nextWaveTrack error", e);
            return null;
        }
    }

    private Track extractWaveTrack(JsonObject data, String currentTrackId) {
        if (data == null) return null;
        // sequence[i].track
        JsonArray seq = safeArr(data, "sequence");
        if (seq != null && seq.size() > 0) {
            for (int i = 0; i < seq.size(); i++) {
                JsonObject item = seq.get(i).getAsJsonObject();
                JsonObject trackObj = safeObj(item, "track");
                if (trackObj != null) {
                    Track t = parseTrack(trackObj);
                    if (t != null) {
                        t.liked = item.has("liked") && item.get("liked").getAsBoolean();
                        if (t.id != null && !t.id.equals(currentTrackId)) return t;
                    }
                }
            }
        }
        // tracks[i].track
        JsonArray tracks = safeArr(data, "tracks");
        if (tracks != null && tracks.size() > 0) {
            for (int i = 0; i < tracks.size(); i++) {
                JsonObject item = tracks.get(i).getAsJsonObject();
                JsonObject trackObj = safeObj(item, "track");
                if (trackObj != null) {
                    Track t = parseTrack(trackObj);
                    if (t != null && t.id != null && !t.id.equals(currentTrackId)) return t;
                }
            }
        }
        return null;
    }

    // ─── Stream URL ──────────────────────────────────────────────────

    /**
     * Получает прямую ссылку для стриминга трека.
     * Endpoint: GET /tracks/{id}/download-info
     * Возвращает лучшее качество (максимальный битрейт).
     */
    public String getStreamUrl(String trackId) {
        try {
            String url = BASE_URL + "/tracks/" + trackId + "/download-info";
            JsonObject resp = getJson(url);
            if (resp == null) return null;
            JsonArray arr = safeArr(resp, "result");
            if (arr == null || arr.size() == 0) return null;

            // Find best quality (max bitrate)
            JsonObject best = null;
            int bestBitrate = 0;
            for (JsonElement el : arr) {
                JsonObject info = el.getAsJsonObject();
                int bitrate = info.has("bitrateInKbps") ? info.get("bitrateInKbps").getAsInt() : 0;
                if (bitrate > bestBitrate) {
                    bestBitrate = bitrate;
                    best = info;
                }
            }
            if (best == null) best = arr.get(0).getAsJsonObject();

            // Try direct link first
            if (best.has("directLink") && !best.get("directLink").isJsonNull()) {
                String dl = best.get("directLink").getAsString();
                if (!dl.isEmpty()) return dl;
            }

            // Resolve via downloadInfoUrl (XML response with XOR-signed URL)
            if (best.has("downloadInfoUrl")) {
                return resolveDownloadUrl(best.get("downloadInfoUrl").getAsString());
            }
        } catch (Exception e) {
            Log.e(TAG, "getStreamUrl error", e);
        }
        return null;
    }

    /**
     * Разрешает URL из XML-ответа download-info.
     * Подпись: md5("XGRlBW9FXlekgbPrRHuSiA" + path[1:] + s)
     */
    private String resolveDownloadUrl(String infoUrl) {
        try {
            Request req = new Request.Builder()
                    .url(infoUrl)
                    .header("User-Agent", ELECTRON_UA)
                    .build();
            String xml;
            try (Response resp = httpClient.newCall(req).execute()) {
                if (!resp.isSuccessful() || resp.body() == null) return null;
                xml = resp.body().string();
            }

            if (xml.startsWith("http")) return xml.trim();

            // Parse XML manually (no XML parser needed for this simple format)
            String host = extractXmlTag(xml, "host");
            String path = extractXmlTag(xml, "path");
            String ts = extractXmlTag(xml, "ts");
            String s = extractXmlTag(xml, "s");

            if (host == null || path == null || ts == null || s == null) return null;

            String sign = md5("XGRlBW9FXlekgbPrRHuSiA" + path.substring(1) + s);
            return "https://" + host + "/get-mp3/" + sign + "/" + ts + path;
        } catch (Exception e) {
            Log.e(TAG, "resolveDownloadUrl error", e);
            return null;
        }
    }

    private String extractXmlTag(String xml, String tag) {
        String open = "<" + tag + ">";
        String close = "</" + tag + ">";
        int start = xml.indexOf(open);
        int end = xml.indexOf(close);
        if (start < 0 || end < 0) return null;
        return xml.substring(start + open.length(), end);
    }

    // ─── Parsers ─────────────────────────────────────────────────────

    private Track parseTrack(JsonObject obj) {
        if (obj == null) return null;
        String id = null;
        if (obj.has("id")) id = obj.get("id").getAsString();
        else if (obj.has("trackId")) id = obj.get("trackId").getAsString();
        if (id == null || id.isEmpty()) return null;

        String title = obj.has("title") ? obj.get("title").getAsString() : "?";
        if (obj.has("trackTitle")) title = obj.get("trackTitle").getAsString();

        // Artists: first artist name
        String artist = "";
        JsonArray artists = safeArr(obj, "artists");
        if (artists != null && artists.size() > 0) {
            JsonObject a = artists.get(0).getAsJsonObject();
            artist = a.has("name") ? a.get("name").getAsString() : "";
            // Concat all artists
            StringBuilder sb = new StringBuilder(artist);
            for (int i = 1; i < Math.min(artists.size(), 3); i++) {
                JsonObject ai = artists.get(i).getAsJsonObject();
                if (ai.has("name")) sb.append(", ").append(ai.get("name").getAsString());
            }
            artist = sb.toString();
        }

        // Album
        String album = "";
        String albumId = "";
        String coverUri = obj.has("coverUri") ? getString(obj, "coverUri") : "";
        JsonArray albums = safeArr(obj, "albums");
        if (albums != null && albums.size() > 0) {
            JsonObject al = albums.get(0).getAsJsonObject();
            album = al.has("title") ? al.get("title").getAsString() : "";
            if (al.has("id")) {
                albumId = al.get("id").getAsString();
            }
            if ((coverUri == null || coverUri.isEmpty()) && al.has("coverUri")) {
                coverUri = al.get("coverUri").getAsString();
            }
        }

        long durationMs = obj.has("durationMs") ? obj.get("durationMs").getAsLong() : 0;

        return new Track(id, title, artist, album, albumId, durationMs, coverUri);
    }

    private Playlist parsePlaylist(JsonObject obj) {
        if (obj == null) return null;
        int kind = obj.has("kind") ? obj.get("kind").getAsInt() : 0;
        String title = obj.has("title") ? obj.get("title").getAsString() : "?";
        int trackCount = obj.has("trackCount") ? obj.get("trackCount").getAsInt() : 0;
        String coverUri = obj.has("coverUri") ? obj.get("coverUri").getAsString() : "";

        long ownerUid = 0;
        JsonObject owner = safeObj(obj, "owner");
        if (owner != null && owner.has("uid")) ownerUid = owner.get("uid").getAsLong();

        return new Playlist(kind, title, ownerUid, trackCount, coverUri);
    }

    // ─── Utilities ───────────────────────────────────────────────────

    private static String generateDeviceId() {
        try {
            SecureRandom rng = new SecureRandom();
            byte[] bytes = new byte[32];
            rng.nextBytes(bytes);
            return bytesToHex(bytes);
        } catch (Exception e) {
            return UUID.randomUUID().toString().replace("-", "");
        }
    }

    public static String md5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] bytes = md.digest(input.getBytes("UTF-8"));
            return bytesToHex(bytes);
        } catch (Exception e) {
            return "";
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    private static String urlEncode(String s) {
        try {
            return java.net.URLEncoder.encode(s, "UTF-8");
        } catch (Exception e) {
            return s;
        }
    }

    private static JsonObject safeObj(JsonObject obj, String key) {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) return null;
        try { return obj.getAsJsonObject(key); } catch (Exception e) { return null; }
    }

    private static JsonArray safeArr(JsonObject obj, String key) {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) return null;
        try { return obj.getAsJsonArray(key); } catch (Exception e) { return null; }
    }

    private static String getString(JsonObject obj, String key) {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) return "";
        return obj.get(key).getAsString();
    }

    public String getDeviceId() { return deviceId; }
    public String getAppUuid() { return appUuid; }

    // Inner class for Wave session
    public static class WaveSession {
        public String sessionId;
        public String batchId;
        public Track track;
        public WaveSession(String sessionId, String batchId, Track track) {
            this.sessionId = sessionId;
            this.batchId = batchId;
            this.track = track;
        }
    }
}
