package com.yearzero.renebeats.classes;

import android.net.Uri;

import com.google.api.services.youtube.model.SearchResult;
import com.google.api.services.youtube.model.SearchResultSnippet;
import com.google.api.services.youtube.model.ThumbnailDetails;

import org.apache.commons.text.StringEscapeUtils;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

public class Query implements Serializable {
    // Appcode (EA50) - "Class" (ClA5) - Class ID (0E10_llE1)
    private static final long serialVersionUID = 0xEA50_C1A5_0E10_11E1L;

    public String youtubeID, title, album, artist;
    public int year, track;
    public String[] genres;

    public String thumbMax, thumbHigh, thumbMedium, thumbDefault, thumbStandard;
    public Uri thumbmap;

    public Query() { }

    public Query(String id) {
        this.youtubeID = id;
        year = Calendar.getInstance().get(Calendar.YEAR);
        track = 1;
        album = "";
    }

    public Query(String id, String title, String artist, String album, int year, int track, String[] genres) {
        this.youtubeID = id;
        this.title = title;
        this.artist = artist;
        this.album = album;
        this.year = year;
        this.track = track;
        this.genres = genres;
    }

    public Query(String id, String title, String artist, String album, int year, int track, String[] genres, ThumbnailDetails thumbnail) {
        this(id, title, artist, album, year, track, genres);

        if (thumbnail.getMaxres() != null) thumbMax = thumbnail.getMaxres().getUrl();
        if (thumbnail.getHigh() != null) thumbHigh = thumbnail.getHigh().getUrl();
        if (thumbnail.getMedium() != null) thumbMedium = thumbnail.getMedium().getUrl();
        if (thumbnail.getDefault() != null) thumbDefault = thumbnail.getDefault().getUrl();
        if (thumbnail.getStandard() != null) thumbStandard = thumbnail.getStandard().getUrl();
    }

    public Query(String id, String title, String artist, String album, int year, int track, String[] genres, String thumbMax, String thumbHigh, String thumbMedium, String thumbDefault, String thumbStandard, Uri thumbmap) {
        this(id, title, artist, album, year, track, genres);

        this.thumbMax = thumbMax;
        this.thumbHigh = thumbHigh;
        this.thumbMedium = thumbMedium;
        this.thumbDefault = thumbDefault;
        this.thumbStandard = thumbStandard;
        this.thumbmap = thumbmap;
    }

    //region Thumbnail Getters
    public String getThumbMax() {
        return thumbMax;
    }

    public String getThumbHigh() {
        return thumbHigh;
    }

    public String getThumbMedium() {
        return thumbMedium;
    }

    public String getThumbDefault() {
        return thumbDefault;
    }

    public String getThumbStandard() {
        return thumbStandard;
    }
    //endregion

    public static List<Query> CastListXML(List<SearchResult> list) {
        List<Query> result = new ArrayList<>();

        for (SearchResult r : list) {
            SearchResultSnippet s = r.getSnippet();

            Calendar cal = Calendar.getInstance();
            cal.setTime(new Date(s.getPublishedAt().getValue()));

            result.add(new Query(
                    r.getId().getVideoId(),
                    StringEscapeUtils.unescapeXml(s.getTitle()),
                    StringEscapeUtils.unescapeXml(s.getChannelTitle()),
                    null,
                    cal.get(Calendar.YEAR),
                    0,
                    new String[0],
                    s.getThumbnails()
            ));
        }
        return result;
    }

    public String getThumbnail(ThumbnailQuality quality) {
        switch (quality) {
            case MaxRes:
                if (thumbMax != null) return thumbMax;
            case High:
                if (thumbHigh != null) return thumbHigh;
            case Medium:
                if (thumbMedium != null) return thumbMedium;
            case Standard:
                if (thumbStandard != null) return thumbStandard;
            default:
                return thumbDefault;
        }
    }

    public void setThumbnail(ThumbnailDetails thumbnail) {
        if (thumbnail.getMaxres() != null) thumbMax = thumbnail.getMaxres().getUrl();
        if (thumbnail.getHigh() != null) thumbHigh = thumbnail.getHigh().getUrl();
        if (thumbnail.getMedium() != null) thumbHigh = thumbnail.getMedium().getUrl();
        if (thumbnail.getDefault() != null) thumbHigh = thumbnail.getDefault().getUrl();
        if (thumbnail.getStandard() != null) thumbHigh = thumbnail.getStandard().getUrl();
    }

    public enum ThumbnailQuality {MaxRes, High, Medium, Default, Standard;}

    public int hashCode() {
        int result = 37;

        result = 37 * result + (album == null ? 0 : album.hashCode());
        result = 37 * result + (artist == null ? 0 : artist.hashCode());
        result = 37 * result + (genres == null ? 0 : Arrays.hashCode(genres));
        result = 37 * result + (thumbDefault == null ? 0 : thumbDefault.hashCode());
        result = 37 * result + (thumbHigh == null ? 0 : thumbHigh.hashCode());
        result = 37 * result + (thumbmap == null ? 0 : thumbmap.hashCode());
        result = 37 * result + (thumbMax == null ? 0 : thumbMax.hashCode());
        result = 37 * result + (thumbMedium == null ? 0 : thumbMedium.hashCode());
        result = 37 * result + (thumbStandard == null ? 0 : thumbStandard.hashCode());
        result = 37 * result + (title == null ? 0 : title.hashCode());
        result = 37 * result + track;
        result = 37 * result + year;
        result = 37 * result + (youtubeID == null ? 0 : youtubeID.hashCode());

        return result;
    }

    public String makeFile() {
        return ((artist == null ? "" : artist.trim() + " - ") + title.trim()).replaceAll("(?:\\||\\\\|\\?|\\*|<|\"|:|>|\\+|\\[|]|/')", "_");
    }
}
