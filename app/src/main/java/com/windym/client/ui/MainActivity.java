package com.windym.client.ui;

import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import android.widget.Button;
import com.squareup.picasso.Picasso;
import com.windym.client.R;
import com.windym.client.api.AppState;
import com.windym.client.model.Track;
import com.windym.client.service.PlayerService;

/**
 * Главный экран приложения.
 * Содержит нижнюю навигацию и мини-плеер.
 */
public class MainActivity extends AppCompatActivity implements PlayerService.PlaybackListener {

    private Button navSearch, navFavorites, navWave;
    private View playerBar;
    private ImageView playerCover;
    private TextView playerTitle;
    private TextView playerArtist;
    private ImageButton playerPlayPause;
    private ImageButton playerPrev;
    private ImageButton playerNext;

    private PlayerService playerService;
    private boolean serviceBound = false;

    private final Handler handler = new Handler(Looper.getMainLooper());

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            PlayerService.PlayerBinder binder = (PlayerService.PlayerBinder) service;
            playerService = binder.getService();
            playerService.addPlaybackListener(MainActivity.this);
            serviceBound = true;
            updateMiniPlayer();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            if (playerService != null) {
                playerService.removePlaybackListener(MainActivity.this);
            }
            serviceBound = false;
            playerService = null;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Check login
        if (!AppState.get().isLoggedIn()) {
            startActivity(new Intent(this, AuthActivity.class));
            finish();
            return;
        }

        setContentView(R.layout.activity_main);

        navSearch = findViewById(R.id.navSearch);
        navFavorites = findViewById(R.id.navFavorites);
        navWave = findViewById(R.id.navWave);
        playerBar = findViewById(R.id.playerBar);
        playerCover = findViewById(R.id.playerCover);
        playerTitle = findViewById(R.id.playerTitle);
        playerArtist = findViewById(R.id.playerArtist);
        playerPlayPause = findViewById(R.id.playerPlayPause);
        playerPrev = findViewById(R.id.playerPrev);
        playerNext = findViewById(R.id.playerNext);

        // Player bar controls
        playerPlayPause.setOnClickListener(v -> {
            if (playerService != null) {
                if (playerService.isPlaying()) playerService.pause();
                else playerService.resume();
            }
        });
        playerPrev.setOnClickListener(v -> {
            if (playerService != null) playerService.prevTrack();
        });
        playerNext.setOnClickListener(v -> {
            if (playerService != null) playerService.nextTrack();
        });
        playerBar.setOnClickListener(v -> openNowPlaying());

        // Bottom navigation (Holo style buttons)
        navSearch.setOnClickListener(v -> {
            showFragment(new SearchFragment());
            updateNavSelection(navSearch);
        });
        navFavorites.setOnClickListener(v -> {
            showFragment(new FavoritesFragment());
            updateNavSelection(navFavorites);
        });
        navWave.setOnClickListener(v -> {
            showFragment(new WaveFragment());
            updateNavSelection(navWave);
        });

        // Default fragment
        showFragment(new SearchFragment());
        updateNavSelection(navSearch);

        // Bind player service
        Intent serviceIntent = new Intent(this, PlayerService.class);
        startService(serviceIntent);
        bindService(serviceIntent, serviceConnection, BIND_AUTO_CREATE);
    }

    private void showFragment(Fragment fragment) {
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragmentContainer, fragment)
                .commit();
    }

    private void updateNavSelection(Button selected) {
        navSearch.setTextColor(getResources().getColor(R.color.text_primary));
        navFavorites.setTextColor(getResources().getColor(R.color.text_primary));
        navWave.setTextColor(getResources().getColor(R.color.text_primary));
        selected.setTextColor(getResources().getColor(R.color.brand_red));
    }

    private void openNowPlaying() {
        startActivity(new Intent(this, NowPlayingActivity.class));
    }

    /** Called by fragments when user taps a track */
    public void playTrack(Track track, java.util.List<Track> queue, int index) {
        AppState.get().setCurrentQueue(queue);
        AppState.get().setCurrentIndex(index);
        AppState.get().setCurrentTrack(track);
        AppState.get().setWaveMode(false);

        if (playerService != null) {
            playerService.playTrack(track);
        } else {
            // Start and bind service
            Intent serviceIntent = new Intent(this, PlayerService.class);
            startService(serviceIntent);
            bindService(serviceIntent, serviceConnection, BIND_AUTO_CREATE);
        }

        updateMiniPlayer();
    }

    /** Called by WaveFragment when wave is active */
    public void playWaveTrack(Track track, String sessionId) {
        AppState state = AppState.get();
        state.setCurrentTrack(track);
        state.setWaveMode(true);
        state.setWaveSid(sessionId);

        if (playerService != null) {
            playerService.playTrack(track);
        }
        updateMiniPlayer();
    }

    private void updateMiniPlayer() {
        Track track = AppState.get().getCurrentTrack();
        if (track == null) {
            playerBar.setVisibility(View.GONE);
            return;
        }
        playerBar.setVisibility(View.VISIBLE);
        playerTitle.setText(track.title);
        playerArtist.setText(track.artist);

        String coverUrl = track.getCoverUrl("100x100");
        if (coverUrl != null) {
            Picasso.get().load(coverUrl)
                    .placeholder(R.drawable.ic_music_note)
                    .into(playerCover);
        }

        boolean playing = playerService != null && playerService.isPlaying();
        playerPlayPause.setImageResource(playing ?
                android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play);
    }

    // ─── PlayerService.PlaybackListener ──────────────────────────────

    @Override
    public void onPlaybackStateChanged(int state, boolean isPlaying) {
        runOnUiThread(() -> {
            playerPlayPause.setImageResource(isPlaying ?
                    android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play);
        });
    }

    @Override
    public void onTrackChanged(Track track) {
        runOnUiThread(() -> {
            updateMiniPlayer();
            Fragment f = getSupportFragmentManager().findFragmentById(R.id.fragmentContainer);
            if (f instanceof WaveFragment) {
                ((WaveFragment) f).updateUI();
            }
        });
    }

    @Override
    public void onError(String message) {
        runOnUiThread(() -> Toast.makeText(this, message, Toast.LENGTH_SHORT).show());
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
