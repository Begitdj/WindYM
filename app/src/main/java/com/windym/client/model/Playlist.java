package com.windym.client.model;

/**
 * Модель плейлиста Яндекс Музыки.
 */
public class Playlist {
    public final int kind;
    public final String title;
    public final long ownerUid;
    public final int trackCount;
    public final String coverUri;

    public Playlist(int kind, String title, long ownerUid, int trackCount, String coverUri) {
        this.kind = kind;
        this.title = title;
        this.ownerUid = ownerUid;
        this.trackCount = trackCount;
        this.coverUri = coverUri;
    }

    public String getCoverUrl(String size) {
        if (coverUri == null || coverUri.isEmpty()) return null;
        if (coverUri.startsWith("http")) return coverUri;
        return "https://" + coverUri.replace("%%", size);
    }
}
