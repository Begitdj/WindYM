package com.windym.client.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import android.widget.Button;
import android.widget.ProgressBar;
import com.squareup.picasso.Picasso;
import com.windym.client.R;
import com.windym.client.api.AppState;
import com.windym.client.api.YandexMusicApiClient;
import com.windym.client.model.Track;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Фрагмент "Моя волна" — персонализированное радио.
 */
public class WaveFragment extends Fragment {

    private ProgressBar waveProgress;
    private TextView waveStatus;
    private View waveCurrentCard;
    private ImageView waveCover;
    private TextView waveTitle;
    private TextView waveArtist;
    private Button waveStartBtn;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_wave, container, false);

        waveProgress = view.findViewById(R.id.waveProgress);
        waveStatus = view.findViewById(R.id.waveStatus);
        waveCurrentCard = view.findViewById(R.id.waveCurrentCard);
        waveCover = view.findViewById(R.id.waveCover);
        waveTitle = view.findViewById(R.id.waveTitle);
        waveArtist = view.findViewById(R.id.waveArtist);
        waveStartBtn = view.findViewById(R.id.waveStartBtn);

        waveStartBtn.setOnClickListener(v -> startWave());

        if (AppState.get().isWaveMode() && AppState.get().getCurrentTrack() != null) {
            waveProgress.setVisibility(View.GONE);
            waveStartBtn.setVisibility(View.GONE);
            showTrack(AppState.get().getCurrentTrack());
        } else {
            startWave();
        }

        return view;
    }

    private void startWave() {
        waveProgress.setVisibility(View.VISIBLE);
        waveStatus.setText(getString(R.string.wave_loading));
        waveCurrentCard.setVisibility(View.GONE);
        waveStartBtn.setVisibility(View.GONE);

        YandexMusicApiClient api = AppState.get().getApiClient();
        if (api == null) {
            waveProgress.setVisibility(View.GONE);
            waveStatus.setText("Требуется авторизация");
            waveStartBtn.setVisibility(View.VISIBLE);
            return;
        }

        executor.execute(() -> {
            YandexMusicApiClient.WaveSession session = api.startWave();
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    waveProgress.setVisibility(View.GONE);
                    if (session == null || session.track == null) {
                        waveStatus.setText("Не удалось запустить волну");
                        waveStartBtn.setVisibility(View.VISIBLE);
                        return;
                    }

                    // Update state
                    AppState.get().setWaveMode(true);
                    AppState.get().setWaveSid(session.sessionId);
                    AppState.get().setWaveBatchId(session.batchId);
                    AppState.get().setCurrentTrack(session.track);

                    // Show current track
                    showTrack(session.track);

                    // Start playback
                    if (getActivity() instanceof MainActivity) {
                        ((MainActivity) getActivity()).playWaveTrack(
                                session.track, session.sessionId);
                    }
                });
            }
        });
    }

    private void showTrack(Track track) {
        waveCurrentCard.setVisibility(View.VISIBLE);
        waveTitle.setText(track.title);
        waveArtist.setText(track.artist.isEmpty() ? getString(R.string.unknown_artist) : track.artist);
        waveStatus.setText(getString(R.string.now_playing));

        String coverUrl = track.getCoverUrl("200x200");
        if (coverUrl != null && !coverUrl.isEmpty()) {
            Picasso.get()
                    .load(coverUrl)
                    .placeholder(R.drawable.ic_music_note)
                    .into(waveCover);
        }
    }

    public void updateUI() {
        if (AppState.get().isWaveMode() && AppState.get().getCurrentTrack() != null) {
            showTrack(AppState.get().getCurrentTrack());
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }
}
