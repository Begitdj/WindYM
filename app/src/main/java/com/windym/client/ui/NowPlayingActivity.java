package com.windym.client.ui;

import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.squareup.picasso.Picasso;
import com.windym.client.R;
import com.windym.client.api.AppState;
import com.windym.client.model.Track;
import com.windym.client.service.PlayerService;

/**
 * Экран "Сейчас играет" — полноэкранный плеер.
 */
public class NowPlayingActivity extends AppCompatActivity implements PlayerService.PlaybackListener {

    private ImageView nowCover;
    private TextView nowTrackTitle;
    private TextView nowArtist;
    private SeekBar nowSeekBar;
    private TextView nowCurrentTime;
    private TextView nowTotalTime;
    private ImageButton nowPlayPause;
    private ImageButton nowPrev;
    private ImageButton nowNext;
    private ImageButton nowLike;
    private SeekBar nowVolume;

    private PlayerService playerService;
    private boolean serviceBound = false;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable progressUpdater = new Runnable() {
        @Override
        public void run() {
            updateProgress();
            handler.postDelayed(this, 500);
        }
    };

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            PlayerService.PlayerBinder binder = (PlayerService.PlayerBinder) service;
            playerService = binder.getService();
            playerService.addPlaybackListener(NowPlayingActivity.this);
            serviceBound = true;
            updateUI();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            if (playerService != null) {
                playerService.removePlaybackListener(NowPlayingActivity.this);
            }
            serviceBound = false;
            playerService = null;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_now_playing);

        nowCover = findViewById(R.id.nowCover);
        nowTrackTitle = findViewById(R.id.nowTrackTitle);
        nowArtist = findViewById(R.id.nowArtist);
        nowSeekBar = findViewById(R.id.nowSeekBar);
        nowCurrentTime = findViewById(R.id.nowCurrentTime);
        nowTotalTime = findViewById(R.id.nowTotalTime);
        nowPlayPause = findViewById(R.id.nowPlayPause);
        nowPrev = findViewById(R.id.nowPrev);
        nowNext = findViewById(R.id.nowNext);
        nowLike = findViewById(R.id.nowLike);
        nowVolume = findViewById(R.id.nowVolume);
        ImageButton nowEq = findViewById(R.id.nowEq);
        ImageButton nowDownload = findViewById(R.id.nowDownload);

        // Back button
        ImageButton backBtn = findViewById(R.id.backBtn);
        backBtn.setOnClickListener(v -> finish());

        // Controls
        nowPlayPause.setOnClickListener(v -> {
            if (playerService != null) {
                if (playerService.isPlaying()) playerService.pause();
                else playerService.resume();
            }
        });
        nowPrev.setOnClickListener(v -> {
            if (playerService != null) playerService.prevTrack();
        });
        nowNext.setOnClickListener(v -> {
            if (playerService != null) playerService.nextTrack();
        });
        nowLike.setOnClickListener(v -> toggleLike());
        
        nowEq.setOnClickListener(v -> {
            if (playerService != null) {
                try {
                    Intent intent = new Intent(android.media.audiofx.AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL);
                    intent.putExtra(android.media.audiofx.AudioEffect.EXTRA_AUDIO_SESSION, playerService.getAudioSessionId());
                    intent.putExtra(android.media.audiofx.AudioEffect.EXTRA_PACKAGE_NAME, getPackageName());
                    intent.putExtra(android.media.audiofx.AudioEffect.EXTRA_CONTENT_TYPE, android.media.audiofx.AudioEffect.CONTENT_TYPE_MUSIC);
                    startActivityForResult(intent, 0);
                } catch (Exception e) {
                    android.widget.Toast.makeText(this, "Эквалайзер не поддерживается устройством", android.widget.Toast.LENGTH_SHORT).show();
                }
            }
        });

        nowDownload.setOnClickListener(v -> {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M &&
                android.os.Build.VERSION.SDK_INT <= android.os.Build.VERSION_CODES.P) {
                if (checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(new String[]{android.Manifest.permission.WRITE_EXTERNAL_STORAGE}, 101);
                    return;
                }
            }
            if (playerService != null) playerService.downloadCurrentTrack();
        });
        
        nowDownload.setOnLongClickListener(v -> {
            startActivity(new Intent(android.app.DownloadManager.ACTION_VIEW_DOWNLOADS));
            return true;
        });

        // Seek bar
        nowSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && playerService != null) {
                    long duration = playerService.getDuration();
                    if (duration > 0) {
                        playerService.seekTo((long) (progress / 1000.0 * duration));
                    }
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // Volume
        nowVolume.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && playerService != null) {
                    playerService.setVolume(progress / 100.0f);
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // Bind service
        Intent serviceIntent = new Intent(this, PlayerService.class);
        bindService(serviceIntent, serviceConnection, BIND_AUTO_CREATE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.post(progressUpdater);
        updateUI();
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(progressUpdater);
    }

    private void updateUI() {
        Track track = AppState.get().getCurrentTrack();
        if (track == null) return;

        nowTrackTitle.setText(track.title);
        nowArtist.setText(track.artist.isEmpty() ?
                getString(R.string.unknown_artist) : track.artist);

        // Cover
        String coverUrl = track.getCoverUrl("400x400");
        if (coverUrl != null && !coverUrl.isEmpty()) {
            Picasso.get()
                    .load(coverUrl)
                    .placeholder(R.drawable.ic_music_note)
                    .into(nowCover);
        }

        // Like state
        nowLike.setImageResource(track.liked ?
                android.R.drawable.btn_star_big_on : android.R.drawable.btn_star_big_off);
        nowLike.setColorFilter(track.liked ?
                getResources().getColor(R.color.brand_red) :
                getResources().getColor(R.color.text_disabled));

        // Play/pause button
        boolean playing = playerService != null && playerService.isPlaying();
        nowPlayPause.setImageResource(playing ?
                android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play);
    }

    private void updateProgress() {
        if (playerService == null) return;
        long pos = playerService.getPosition();
        long dur = playerService.getDuration();
        if (dur > 0) {
            nowSeekBar.setProgress((int) (pos * 1000 / dur));
        }
        nowCurrentTime.setText(formatTime(pos));
        nowTotalTime.setText(formatTime(dur));
    }

    private String formatTime(long ms) {
        long sec = ms / 1000;
        return String.format("%d:%02d", sec / 60, sec % 60);
    }

    private void toggleLike() {
        Track track = AppState.get().getCurrentTrack();
        if (track == null) return;
        track.liked = !track.liked;
        nowLike.setImageResource(track.liked ?
                android.R.drawable.btn_star_big_on : android.R.drawable.btn_star_big_off);
        nowLike.setColorFilter(track.liked ?
                getResources().getColor(R.color.brand_red) :
                getResources().getColor(R.color.text_disabled));

        // Async API call
        java.util.concurrent.Executors.newSingleThreadExecutor().execute(() -> {
            var api = AppState.get().getApiClient();
            if (api == null) return;
            if (track.liked) api.likeTrack(track.id);
            else api.unlikeTrack(track.id);
        });
    }

    // ─── PlayerService.PlaybackListener ──────────────────────────────

    @Override
    public void onPlaybackStateChanged(int state, boolean isPlaying) {
        runOnUiThread(() -> {
            nowPlayPause.setImageResource(isPlaying ?
                    android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play);
        });
    }

    @Override
    public void onTrackChanged(Track track) {
        runOnUiThread(this::updateUI);
    }

    @Override
    public void onError(String message) {
        runOnUiThread(() ->
                android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_SHORT).show());
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @androidx.annotation.NonNull String[] permissions, @androidx.annotation.NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 101 && grantResults.length > 0 && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            if (playerService != null) playerService.downloadCurrentTrack();
        } else if (requestCode == 101) {
            android.widget.Toast.makeText(this, "Для скачивания требуется разрешение на память", android.widget.Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onDestroy() {
        if (serviceBound) {
            if (playerService != null) {
                playerService.removePlaybackListener(this);
            }
            unbindService(serviceConnection);
            serviceBound = false;
        }
        super.onDestroy();
    }
}
