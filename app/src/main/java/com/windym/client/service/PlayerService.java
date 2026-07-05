package com.windym.client.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.support.v4.media.session.MediaSessionCompat;
import android.util.Log;
import android.widget.RemoteViews;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.ext.mediasession.MediaSessionConnector;
import com.squareup.picasso.Picasso;

import com.windym.client.R;
import com.windym.client.api.AppState;
import com.windym.client.api.YandexMusicApiClient;
import com.windym.client.model.Track;
import com.windym.client.ui.MainActivity;
import com.windym.client.ui.NowPlayingActivity;
import com.windym.client.ui.PlayerWidgetProvider;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Foreground Media Player Service.
 * Использует ExoPlayer для воспроизведения аудио.
 * Показывает уведомление с элементами управления.
 */
public class PlayerService extends Service {

    private static final String TAG = "PlayerService";
    private static final String CHANNEL_ID = "windym_player";
    private static final int NOTIFICATION_ID = 1;

    public static final String ACTION_PLAY = "com.windym.PLAY";
    public static final String ACTION_PAUSE = "com.windym.PAUSE";
    public static final String ACTION_NEXT = "com.windym.NEXT";
    public static final String ACTION_PREV = "com.windym.PREV";
    public static final String ACTION_STOP = "com.windym.STOP";

    private ExoPlayer player;
    private MediaSessionCompat mediaSession;
    private ExecutorService executor;
    private PlayerBinder binder = new PlayerBinder();

    // Callbacks
    private final java.util.List<PlaybackListener> listeners = new java.util.concurrent.CopyOnWriteArrayList<>();
    private Bitmap currentCoverBitmap = null;

    @Override
    public void onCreate() {
        super.onCreate();
        executor = Executors.newSingleThreadExecutor();
        player = new SimpleExoPlayer.Builder(this).build();
        player.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int state) {
                if (state == Player.STATE_ENDED) {
                    onTrackEnded();
                }
                for (PlaybackListener listener : listeners) {
                    listener.onPlaybackStateChanged(state, player.isPlaying());
                }
            }

            @Override
            public void onIsPlayingChanged(boolean isPlaying) {
                updateNotification();
                for (PlaybackListener listener : listeners) {
                    listener.onPlaybackStateChanged(player.getPlaybackState(), isPlaying);
                }
            }
        });

        createNotificationChannel();

        // Media session for hardware keys / headset
        mediaSession = new MediaSessionCompat(this, "WindYM");
        mediaSession.setActive(true);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getAction() != null) {
            switch (intent.getAction()) {
                case ACTION_PLAY:
                    resume();
                    break;
                case ACTION_PAUSE:
                    pause();
                    break;
                case ACTION_NEXT:
                    nextTrack();
                    break;
                case ACTION_PREV:
                    prevTrack();
                    break;
                case ACTION_STOP:
                    stopSelf();
                    break;
                case PlayerWidgetProvider.ACTION_UPDATE_WIDGET:
                    updateAppWidgets();
                    break;
            }
        }
        startForeground(NOTIFICATION_ID, buildNotification());
        return START_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void onDestroy() {
        if (player != null) {
            player.release();
            player = null;
        }
        if (mediaSession != null) {
            mediaSession.release();
        }
        if (executor != null) {
            executor.shutdown();
        }
        super.onDestroy();
    }

    // ─── Playback control ────────────────────────────────────────────

    /**
     * Загружает URL и начинает воспроизведение.
     */
    public void playUrl(String url) {
        if (player == null) return;
        player.setMediaItem(MediaItem.fromUri(url));
        player.prepare();
        player.play();
        startForeground(NOTIFICATION_ID, buildNotification());
    }

    /**
     * Асинхронно получает stream URL и запускает воспроизведение.
     */
    public void playTrack(Track track) {
        AppState state = AppState.get();
        state.setCurrentTrack(track);
        YandexMusicApiClient api = state.getApiClient();
        if (api == null) return;

        // Clear previous bitmap
        currentCoverBitmap = null;
        // Update notification immediately with track info
        updateNotification();
        updateAppWidgets();

        executor.execute(() -> {
            try {
                // Fetch cover bitmap synchronously in the background thread
                String coverUrl = track.getCoverUrl("200x200");
                if (coverUrl != null && !coverUrl.isEmpty()) {
                    try {
                        currentCoverBitmap = Picasso.get().load(coverUrl).get();
                        updateNotification();
                        updateAppWidgets();
                    } catch (Exception ignore) {}
                }

                String url = api.getStreamUrl(track.id);
                if (url != null && !url.isEmpty()) {
                    // Play on main thread
                    android.os.Handler mainHandler = new android.os.Handler(
                            android.os.Looper.getMainLooper());
                    mainHandler.post(() -> playUrl(url));
                } else {
                    Log.e(TAG, "No stream URL for track: " + track.id);
                    android.os.Handler mainHandler = new android.os.Handler(
                            android.os.Looper.getMainLooper());
                    mainHandler.post(() -> {
                        for (PlaybackListener listener : listeners) {
                            listener.onError("Не удалось получить ссылку");
                        }
                    });
                }
            } catch (Exception e) {
                Log.e(TAG, "playTrack executor error", e);
            }
        });
    }

    public void pause() {
        if (player != null) { player.pause(); updateNotification(); updateAppWidgets(); }
    }

    public void resume() {
        if (player != null) { player.play(); updateNotification(); updateAppWidgets(); }
    }

    public void stop() {
        if (player != null) {
            player.stop();
            player.clearMediaItems();
            updateAppWidgets();
        }
    }

    public boolean isPlaying() {
        return player != null && player.isPlaying();
    }

    public long getPosition() {
        return player != null ? player.getCurrentPosition() : 0;
    }

    public long getDuration() {
        return player != null ? player.getDuration() : 0;
    }

    public void seekTo(long posMs) {
        if (player != null) player.seekTo(posMs);
    }

    public void setVolume(float vol) {
        if (player != null) player.setVolume(vol);
    }

    public int getAudioSessionId() {
        return player instanceof SimpleExoPlayer ? ((SimpleExoPlayer) player).getAudioSessionId() : 0;
    }

    public void downloadCurrentTrack() {
        AppState state = AppState.get();
        Track track = state.getCurrentTrack();
        if (track == null) return;
        
        executor.execute(() -> {
            YandexMusicApiClient api = state.getApiClient();
            if (api == null) return;
            try {
                String url = api.getStreamUrl(track.id);
                if (url != null && !url.isEmpty()) {
                    android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
                    mainHandler.post(() -> {
                        try {
                            android.app.DownloadManager.Request request = new android.app.DownloadManager.Request(android.net.Uri.parse(url));
                            request.setTitle(track.title);
                            request.setDescription(track.artist);
                            request.setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                            request.setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_MUSIC, "YandexMusic/" + track.artist + " - " + track.title + ".mp3");
                            android.app.DownloadManager manager = (android.app.DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
                            if (manager != null) manager.enqueue(request);
                            android.widget.Toast.makeText(this, "Скачивание началось...", android.widget.Toast.LENGTH_SHORT).show();
                        } catch (Exception e) {
                            android.widget.Toast.makeText(this, "Ошибка скачивания: " + e.getMessage(), android.widget.Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            } catch (Exception e) {}
        });
    }

    // ─── Track navigation ────────────────────────────────────────────

    private void onTrackEnded() {
        nextTrack(false);
    }

    public void nextTrack() {
        nextTrack(true);
    }

    public void nextTrack(boolean isSkip) {
        AppState state = AppState.get();
        if (state.isWaveMode()) {
            Track currentTrack = state.getCurrentTrack();
            // Request next wave track async
            executor.execute(() -> {
                YandexMusicApiClient api = state.getApiClient();
                if (api == null) return;
                YandexMusicApiClient.WaveSession session = api.nextWaveTrack(state.getWaveSid(), state.getWaveBatchId(), currentTrack, isSkip, state.getWaveHistory());
                if (session != null && session.track != null) {
                    if (currentTrack != null) {
                        String curItem = currentTrack.id + (currentTrack.albumId != null && !currentTrack.albumId.isEmpty() ? ":" + currentTrack.albumId : "");
                        state.addWaveHistory(curItem);
                    }
                    state.setWaveBatchId(session.batchId);
                    Track next = session.track;
                    state.setCurrentTrack(next);
                    android.os.Handler h = new android.os.Handler(android.os.Looper.getMainLooper());
                    h.post(() -> {
                        for (PlaybackListener listener : listeners) {
                            listener.onTrackChanged(next);
                        }
                        playTrack(next);
                    });
                }
            });
            return;
        }

        java.util.List<Track> queue = state.getCurrentQueue();
        if (queue == null || queue.isEmpty()) return;
        int next = state.getCurrentIndex() + 1;
        if (next >= queue.size()) next = 0;
        state.setCurrentIndex(next);
        Track t = queue.get(next);
        state.setCurrentTrack(t);
        for (PlaybackListener listener : listeners) {
            listener.onTrackChanged(t);
        }
        playTrack(t);
    }

    public void prevTrack() {
        AppState state = AppState.get();
        if (state.isWaveMode()) {
            seekTo(0);
            return;
        }

        java.util.List<Track> queue = state.getCurrentQueue();
        if (queue == null || queue.isEmpty()) return;
        int prev = state.getCurrentIndex() - 1;
        if (prev < 0) prev = queue.size() - 1;
        state.setCurrentIndex(prev);
        Track t = queue.get(prev);
        state.setCurrentTrack(t);
        for (PlaybackListener listener : listeners) {
            listener.onTrackChanged(t);
        }
        playTrack(t);
    }

    // ─── Notification ────────────────────────────────────────────────

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.player_notification_channel),
                    NotificationManager.IMPORTANCE_LOW);
            channel.setDescription(getString(R.string.player_notification_channel_desc));
            channel.setShowBadge(false);
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) nm.createNotificationChannel(channel);
        }
    }

    private Notification buildNotification() {
        Track track = AppState.get().getCurrentTrack();
        String title = track != null ? track.title : "WindYM";
        String artist = track != null ? track.artist : "";

        Intent openIntent = new Intent(this, NowPlayingActivity.class);
        PendingIntent openPending = PendingIntent.getActivity(this, 0, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        PendingIntent playPending = makeActionPending(ACTION_PLAY, 1);
        PendingIntent pausePending = makeActionPending(ACTION_PAUSE, 2);
        PendingIntent nextPending = makeActionPending(ACTION_NEXT, 3);
        PendingIntent prevPending = makeActionPending(ACTION_PREV, 4);

        boolean playing = isPlaying();

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_music_note)
                .setContentTitle(title)
                .setContentText(artist)
                .setContentIntent(openPending)
                .addAction(R.drawable.ic_skip_prev, "Prev", prevPending)
                .addAction(playing ? R.drawable.ic_pause : R.drawable.ic_play,
                        playing ? "Pause" : "Play",
                        playing ? pausePending : playPending)
                .addAction(R.drawable.ic_skip_next, "Next", nextPending)
                .setStyle(new androidx.media.app.NotificationCompat.MediaStyle()
                        .setShowActionsInCompactView(0, 1, 2))
                .setOngoing(playing)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_LOW);

        if (currentCoverBitmap != null) {
            builder.setLargeIcon(currentCoverBitmap);
        }

        return builder.build();
    }

    private PendingIntent makeActionPending(String action, int requestCode) {
        Intent intent = new Intent(this, PlayerService.class);
        intent.setAction(action);
        return PendingIntent.getService(this, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private void updateNotification() {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(NOTIFICATION_ID, buildNotification());
    }

    private void updateAppWidgets() {
        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(this);
        ComponentName widgetComponent = new ComponentName(this, PlayerWidgetProvider.class);
        int[] widgetIds = appWidgetManager.getAppWidgetIds(widgetComponent);
        if (widgetIds == null || widgetIds.length == 0) return;

        Track track = AppState.get().getCurrentTrack();
        boolean playing = isPlaying();

        for (int id : widgetIds) {
            RemoteViews views = new RemoteViews(getPackageName(), R.layout.widget_player);

            if (track != null) {
                views.setTextViewText(R.id.widget_title, track.title);
                views.setTextViewText(R.id.widget_artist, track.artist);
                if (currentCoverBitmap != null) {
                    views.setImageViewBitmap(R.id.widget_cover, currentCoverBitmap);
                } else {
                    views.setImageViewResource(R.id.widget_cover, R.drawable.ic_music_note);
                }
            } else {
                views.setTextViewText(R.id.widget_title, "WindYM");
                views.setTextViewText(R.id.widget_artist, "Нет музыки");
                views.setImageViewResource(R.id.widget_cover, R.drawable.ic_music_note);
            }

            views.setImageViewResource(R.id.widget_btn_play_pause, 
                    playing ? R.drawable.ic_pause : R.drawable.ic_play);

            views.setOnClickPendingIntent(R.id.widget_btn_prev, makeActionPending(ACTION_PREV, 10));
            views.setOnClickPendingIntent(R.id.widget_btn_play_pause, makeActionPending(playing ? ACTION_PAUSE : ACTION_PLAY, 11));
            views.setOnClickPendingIntent(R.id.widget_btn_next, makeActionPending(ACTION_NEXT, 12));

            Intent openIntent = new Intent(this, NowPlayingActivity.class);
            PendingIntent openPending = PendingIntent.getActivity(this, 0, openIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            views.setOnClickPendingIntent(R.id.widget_cover, openPending);
            views.setOnClickPendingIntent(R.id.widget_title, openPending);

            appWidgetManager.updateAppWidget(id, views);
        }
    }

    // ─── Binder ──────────────────────────────────────────────────────

    public class PlayerBinder extends Binder {
        public PlayerService getService() { return PlayerService.this; }
    }

    public void addPlaybackListener(PlaybackListener listener) {
        if (!listeners.contains(listener)) listeners.add(listener);
    }

    public void removePlaybackListener(PlaybackListener listener) {
        listeners.remove(listener);
    }

    public interface PlaybackListener {
        void onPlaybackStateChanged(int state, boolean isPlaying);
        void onTrackChanged(Track track);
        void onError(String message);
    }
}
