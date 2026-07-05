package com.windym.client.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.squareup.picasso.Picasso;
import com.windym.client.R;
import com.windym.client.model.Track;

import java.util.List;

/**
 * RecyclerView адаптер для списка треков.
 */
public class TrackAdapter extends RecyclerView.Adapter<TrackAdapter.TrackViewHolder> {

    public interface OnTrackClickListener {
        void onTrackClick(Track track, int position);
        void onLikeClick(Track track, int position);
        void onMoreClick(Track track, int position);
    }

    private List<Track> tracks;
    private OnTrackClickListener listener;

    public TrackAdapter(List<Track> tracks, OnTrackClickListener listener) {
        this.tracks = tracks;
        this.listener = listener;
    }

    public void setTracks(List<Track> tracks) {
        this.tracks = tracks;
        notifyDataSetChanged();
    }

    public List<Track> getTracks() { return tracks; }

    @NonNull
    @Override
    public TrackViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_track, parent, false);
        return new TrackViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull TrackViewHolder holder, int position) {
        Track track = tracks.get(position);
        holder.bind(track, position, listener);
    }

    @Override
    public int getItemCount() {
        return tracks != null ? tracks.size() : 0;
    }

    static class TrackViewHolder extends RecyclerView.ViewHolder {
        ImageView cover;
        TextView title;
        TextView artist;
        TextView duration;
        ImageButton like;
        ImageButton more;

        TrackViewHolder(@NonNull View itemView) {
            super(itemView);
            cover = itemView.findViewById(R.id.trackCover);
            title = itemView.findViewById(R.id.trackTitle);
            artist = itemView.findViewById(R.id.trackArtist);
            duration = itemView.findViewById(R.id.trackDuration);
            like = itemView.findViewById(R.id.trackLike);
            more = itemView.findViewById(R.id.trackMore);
        }

        void bind(Track track, int position, OnTrackClickListener listener) {
            title.setText(track.title);
            artist.setText(track.artist.isEmpty() ? "—" : track.artist);
            duration.setText(track.getDurationFormatted());

            // Cover image
            String coverUrl = track.getCoverUrl("100x100");
            if (coverUrl != null && !coverUrl.isEmpty()) {
                Picasso.get()
                        .load(coverUrl)
                        .placeholder(R.drawable.ic_music_note)
                        .error(R.drawable.ic_music_note)
                        .into(cover);
            } else {
                cover.setImageResource(R.drawable.ic_music_note);
            }

            // Like state
            like.setImageResource(track.liked ?
                    android.R.drawable.btn_star_big_on : android.R.drawable.btn_star_big_off);
            like.setColorFilter(track.liked ?
                    itemView.getContext().getResources().getColor(R.color.brand_red) :
                    itemView.getContext().getResources().getColor(R.color.text_disabled));

            // Click handlers
            itemView.setOnClickListener(v -> {
                if (listener != null) listener.onTrackClick(track, position);
            });
            like.setOnClickListener(v -> {
                if (listener != null) listener.onLikeClick(track, position);
            });
            more.setOnClickListener(v -> {
                if (listener != null) listener.onMoreClick(track, position);
            });
        }
    }
}
