package com.windym.client.model;

/**
 * Модель трека Яндекс Музыки.
 */
public class Track {
    public final String id;
    public final String title;
    public final String artist;
    public final String album;
    public final String albumId;
    public final long durationMs;
    public final String coverUri;
    public boolean liked;

    public Track(String id, String title, String artist, String album, String albumId,
                 long durationMs, String coverUri) {
        this.id = id;
        this.title = title;
        this.artist = artist;
        this.album = album;
        this.albumId = albumId;
        this.durationMs = durationMs;
        this.coverUri = coverUri;
        this.liked = false;
    }

    /** Форматирует длительность как M:SS */
    public String getDurationFormatted() {
        long totalSec = durationMs / 1000;
        return String.format("%d:%02d", totalSec / 60, totalSec % 60);
    }

    /** Строит URL обложки нужного размера */
    public String getCoverUrl(String size) {
        if (coverUri == null || coverUri.isEmpty()) return null;
        if (coverUri.startsWith("http")) return coverUri;
        return "https://" + coverUri.replace("%%", size);
    }
}
