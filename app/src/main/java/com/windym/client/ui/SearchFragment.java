package com.windym.client.ui;

import android.os.Bundle;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.widget.EditText;
import android.widget.ProgressBar;
import com.windym.client.R;
import com.windym.client.api.AppState;
import com.windym.client.api.YandexMusicApiClient;
import com.windym.client.model.Track;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Фрагмент поиска треков.
 */
public class SearchFragment extends Fragment implements TrackAdapter.OnTrackClickListener {

    private EditText searchInput;
    private ProgressBar searchProgress;
    private TextView searchInfo;
    private RecyclerView searchRecycler;
    private TrackAdapter adapter;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_search, container, false);

        searchInput = view.findViewById(R.id.searchInput);
        searchProgress = view.findViewById(R.id.searchProgress);
        searchInfo = view.findViewById(R.id.searchInfo);
        searchRecycler = view.findViewById(R.id.searchRecycler);

        adapter = new TrackAdapter(new ArrayList<>(), this);
        searchRecycler.setLayoutManager(new LinearLayoutManager(getContext()));
        searchRecycler.setAdapter(adapter);
        searchRecycler.setHasFixedSize(true);

        searchInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH ||
                    (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                doSearch();
                return true;
            }
            return false;
        });

        return view;
    }

    private void doSearch() {
        String query = searchInput.getText() != null ?
                searchInput.getText().toString().trim() : "";
        if (query.isEmpty()) return;

        searchProgress.setVisibility(View.VISIBLE);
        searchInfo.setText("Поиск…");
        adapter.setTracks(new ArrayList<>());

        YandexMusicApiClient api = AppState.get().getApiClient();
        if (api == null) return;

        executor.execute(() -> {
            List<Track> results = api.searchTracks(query, 0);
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    searchProgress.setVisibility(View.GONE);
                    adapter.setTracks(results);
                    if (results.isEmpty()) {
                        searchInfo.setText(getString(R.string.no_results));
                    } else {
                        searchInfo.setText(String.format("Найдено: %d", results.size()));
                    }
                });
            }
        });
    }

    @Override
    public void onTrackClick(Track track, int position) {
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).playTrack(
                    track, adapter.getTracks(), position);
        }
    }

    @Override
    public void onLikeClick(Track track, int position) {
        track.liked = !track.liked;
        adapter.notifyItemChanged(position);
        YandexMusicApiClient api = AppState.get().getApiClient();
        if (api == null) return;
        executor.execute(() -> {
            if (track.liked) api.likeTrack(track.id);
            else api.unlikeTrack(track.id);
        });
    }

    @Override
    public void onMoreClick(Track track, int position) {
        // TODO: show bottom sheet with options (download, add to playlist, etc.)
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }
}
