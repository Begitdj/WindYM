package com.windym.client.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

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
 * Фрагмент "Избранное" — понравившиеся треки.
 */
public class FavoritesFragment extends Fragment implements TrackAdapter.OnTrackClickListener {

    private ProgressBar favProgress;
    private TextView favCount;
    private SwipeRefreshLayout swipeRefresh;
    private RecyclerView favRecycler;
    private TrackAdapter adapter;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_favorites, container, false);

        favProgress = view.findViewById(R.id.favProgress);
        favCount = view.findViewById(R.id.favCount);
        swipeRefresh = view.findViewById(R.id.favSwipeRefresh);
        favRecycler = view.findViewById(R.id.favRecycler);

        adapter = new TrackAdapter(new ArrayList<>(), this);
        favRecycler.setLayoutManager(new LinearLayoutManager(getContext()));
        favRecycler.setAdapter(adapter);
        favRecycler.setHasFixedSize(true);

        swipeRefresh.setColorSchemeResources(R.color.brand_red);
        swipeRefresh.setOnRefreshListener(this::loadFavorites);

        loadFavorites();

        return view;
    }

    private void loadFavorites() {
        favProgress.setVisibility(View.VISIBLE);
        favCount.setText("Загрузка…");

        YandexMusicApiClient api = AppState.get().getApiClient();
        if (api == null) {
            favProgress.setVisibility(View.GONE);
            swipeRefresh.setRefreshing(false);
            return;
        }

        executor.execute(() -> {
            List<Track> tracks = api.getLikedTracks();
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    favProgress.setVisibility(View.GONE);
                    swipeRefresh.setRefreshing(false);
                    adapter.setTracks(tracks);
                    if (tracks.isEmpty()) {
                        favCount.setText("Нет понравившихся треков");
                    } else {
                        favCount.setText(
                                String.format(getString(R.string.tracks_count), tracks.size()));
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
    public void onMoreClick(Track track, int position) {}

    @Override
    public void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }
}
