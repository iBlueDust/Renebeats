package com.yearzero.renebeats.download;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.api.services.youtube.model.SearchResult;
import com.google.api.services.youtube.model.SearchResultSnippet;
import com.google.api.services.youtube.model.ThumbnailDetails;
import com.yearzero.renebeats.preferences.Preferences;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.StringEscapeUtils;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import javax.annotation.ParametersAreNullableByDefault;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

@ParametersAreNullableByDefault
public class Query implements Serializable {
    // App code (EA50) - "Class" (ClA5) - Class ID (0E10_llE1)
    private static long serialVersionUID = 0xEA50_C1A5_0E10_11E1L;

    @Getter @Setter(AccessLevel.PACKAGE) private String youtubeID;
    @Getter @Setter(AccessLevel.PACKAGE) @NonNull private String title = "";
    @Getter @Setter(AccessLevel.PACKAGE) @NonNull private String album = "";
    @Getter @Setter(AccessLevel.PACKAGE) @NonNull private String artist = "";
    @Getter @Setter(AccessLevel.PACKAGE) private int year = 0;
    @Getter @Setter(AccessLevel.PACKAGE) private int track = 0;
    @Getter @Setter(AccessLevel.PACKAGE) @NonNull private String genres = "";

    //region Thumbnail Getters
    @Getter @Setter(AccessLevel.PACKAGE) private String thumbMax;
    @Getter @Setter(AccessLevel.PACKAGE) private String thumbHigh;

    @Getter @Setter(AccessLevel.PACKAGE) private String thumbMedium;
    @Getter @Setter(AccessLevel.PACKAGE) private String thumbDefault;
    @Getter @Setter(AccessLevel.PACKAGE) private String thumbStandard;

    Query() {}

    Query(@Nullable String id) {
        this.youtubeID = id;
    }

    Query(@Nullable String id, @NonNull String title, @NonNull String artist, @NonNull String album, int year, int track, @NonNull String genres) {
        this.youtubeID = id;
        this.title = title;
        this.artist = artist;
        this.album = album;
        this.year = year;
        this.track = track;
        this.genres = genres;
    }

    private Query(@Nullable String id, @NonNull String title, @NonNull String artist, @NonNull String album, int year, int track, @NonNull String genres, @NonNull ThumbnailDetails thumbnail) {
        this(id, title, artist, album, year, track, genres);
        if (thumbnail.getMaxres() != null) thumbMax = thumbnail.getMaxres().getUrl();
        if (thumbnail.getHigh() != null) thumbHigh = thumbnail.getHigh().getUrl();
        if (thumbnail.getMedium() != null) thumbMedium = thumbnail.getMedium().getUrl();
        if (thumbnail.getDefault() != null) thumbDefault = thumbnail.getDefault().getUrl();
        if (thumbnail.getStandard() != null) thumbStandard = thumbnail.getStandard().getUrl();
    }

    Query(@Nullable String id, @NonNull String title, @NonNull String artist, @NonNull String album, int year, int track, @NonNull String genres, @Nullable String thumbMax, @Nullable String thumbHigh, @Nullable String thumbMedium, @Nullable String thumbDefault, @Nullable String thumbStandard) {
        this(id, title, artist, album, year, track, genres);
        this.thumbMax = thumbMax;
        this.thumbHigh = thumbHigh;
        this.thumbMedium = thumbMedium;
        this.thumbDefault = thumbDefault;
        this.thumbStandard = thumbStandard;
    }

    @Nullable
    String getFilename() {
        String result;
        if (artist.isEmpty()) {
            if (title.isEmpty())
                return null;
            else
                result = title.trim();
        } else {
            if (title.isEmpty())
                result = artist.trim();
            else if (Preferences.getArtist_first())
                result = artist.trim() + " - " + title.trim();
            else
                result = title.trim() + " - " + artist.trim();
        }
        return result.replaceAll("(?:\\||\\\\|\\?|\\*|<|\"|:|>|\\+|\\[|]|/')", "_");
    }

    //In Java use overflow/no break switch case
    @Nullable
    String getThumbnail(@NonNull ThumbnailQuality quality) {
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

    //  public void setThumbnail(ThumbnailDetails thumbnail) {
    //    if (thumbnail.getMaxres() != null) thumbMax = thumbnail.getMaxres().getUrl();
    //    if (thumbnail.getHigh() != null) thumbHigh = thumbnail.getHigh().getUrl();
    //    if (thumbnail.getMedium() != null) thumbHigh = thumbnail.getMedium().getUrl();
    //    if (thumbnail.getDefault() != null) thumbHigh = thumbnail.getDefault().getUrl();
    //    if (thumbnail.getStandard() != null) thumbHigh = thumbnail.getStandard().getUrl();
    //  }

    //    public String getFileNoExt() {
    //        boolean t = title == null || title.isEmpty();
    //        boolean a = artist == null || artist.isEmpty();
    //        if (!(t || a)) return title + '-' + artist;
    //        else if (t && a) return "-";
    //        else if (t) return title;
    //        else return artist;
    //    }

    public enum ThumbnailQuality {
        MaxRes("maxres"),
        High("high"),
        Medium("medium"),
        Default("default"),
        Standard("standard");
        @NonNull private String value;

        ThumbnailQuality(@NonNull String value) {
            this.value = value;
        }

        public String toValue() {
            return value;
        }

        @NonNull
        public static ThumbnailQuality fromValue(@NonNull String value) {
            switch (value.toLowerCase()) {
                case "maxres":
                    return MaxRes;
                case "high":
                    return High;
                case "medium":
                    return Medium;
                case "standard":
                    return Standard;
                default:
                    return Default;
            }
        }
    }

    @Override
    public int hashCode() {
        int result = 37;

        result = 37 * result + album.hashCode();
        result = 37 * result + artist.hashCode();
        result = 37 * result + genres.hashCode();
        result = 37 * result + (thumbDefault == null ? 0 : thumbDefault.hashCode());
        result = 37 * result + (thumbHigh == null ? 0 : thumbHigh.hashCode());
        result = 37 * result + (thumbMax == null ? 0 : thumbMax.hashCode());
        result = 37 * result + (thumbMedium == null ? 0 : thumbMedium.hashCode());
        result = 37 * result + (thumbStandard == null ? 0 : thumbStandard.hashCode());
        result = 37 * result + title.hashCode();
        result = 37 * result + track;
        result = 37 * result + year;
        result = 37 * result + (youtubeID == null ? 0 : youtubeID.hashCode());

        return result;
    }

    @Override
    public boolean equals(@Nullable Object other) {
        if (this == other) return true;
        if (!(other instanceof Query)) return false;
        Query cast = (Query) other;

        if (!(StringUtils.equals(youtubeID, cast.youtubeID) &&
                StringUtils.equals(title, cast.title) &&
                StringUtils.equals(album, cast.album) &&
                StringUtils.equals(artist, cast.artist) &&
                year == cast.year && track == cast.track &&
                StringUtils.equals(genres, cast.genres) &&
                StringUtils.equals(thumbMax, cast.thumbMax) &&
                StringUtils.equals(thumbHigh, cast.thumbHigh) &&
                StringUtils.equals(thumbMedium, cast.thumbMedium) &&
                StringUtils.equals(thumbDefault, cast.thumbDefault))) return false;
        return StringUtils.equals(thumbStandard, cast.thumbStandard);
    }

    static List<Query> castListXML(@NonNull List<SearchResult> list) {
        ArrayList<Query> result = new ArrayList<>();

        for (SearchResult r : list) {
            SearchResultSnippet s = r.getSnippet();

            Calendar cal = Calendar.getInstance();
            cal.setTime(new Date(s.getPublishedAt().getValue()));

            result.add(new Query(
                    r.getId().getVideoId(),
                    StringEscapeUtils.unescapeXml(s.getTitle()),
                    StringEscapeUtils.unescapeXml(s.getChannelTitle()), "",
                    cal.get(Calendar.YEAR),
                    0,
                    "",
                    s.getThumbnails()
            ));
        }
        return result;
    }

//    //region Getters and Setters
//    @Nullable
//    public String getYoutubeID() {
//        return youtubeID;
//    }
//
////    public Query setYoutubeID(@Nullable String youtubeID) {
////        this.youtubeID = youtubeID;
////        return this;
////    }
//
//    @Nullable
//    public String getTitle() {
//        return title;
//    }
//
//    public Query setTitle(@Nullable String title) {
//        this.title = title;
//        return this;
//    }
//
//    @Nullable
//    String getAlbum() {
//        return album;
//    }
//
//    Query setAlbum(@Nullable String album) {
//        this.album = album;
//        return this;
//    }
//
//    @Nullable
//    public String getArtist() {
//        return artist;
//    }
//
//    public Query setArtist(@Nullable String artist) {
//        this.artist = artist;
//        return this;
//    }
//
//    public int getYear() {
//        return year;
//    }
//
//    public Query setYear(int year) {
//        this.year = year;
//        return this;
//    }
//
//    public int getTrack() {
//        return track;
//    }
//
//    Query setTrack(int track) {
//        this.track = track;
//        return this;
//    }
//
//    @Nullable
//    public String getGenres() {
//        return genres;
//    }
//
//    Query setGenres(@Nullable String genres) {
//        this.genres = genres;
//        return this;
//    }
//
//    @Nullable
//    public String getThumbMax() {
//        return thumbMax;
//    }
//
//    void setThumbMax(@Nullable String thumbMax) {
//        this.thumbMax = thumbMax;
//    }
//
//    @Nullable
//    public String getThumbHigh() {
//        return thumbHigh;
//    }
//
//    void setThumbHigh(@Nullable String thumbHigh) {
//        this.thumbHigh = thumbHigh;
//    }
//
//    @Nullable
//    public String getThumbMedium() {
//        return thumbMedium;
//    }
//
//    void setThumbMedium(@Nullable String thumbMedium) {
//        this.thumbMedium = thumbMedium;
//    }
//
//    @Nullable
//    public String getThumbDefault() {
//        return thumbDefault;
//    }
//
//    void setThumbDefault(@Nullable String thumbDefault) {
//        this.thumbDefault = thumbDefault;
//    }
//
//    @Nullable
//    public String getThumbStandard() {
//        return thumbStandard;
//    }
//
//    void setThumbStandard(@Nullable String thumbStandard) {
//        this.thumbStandard = thumbStandard;
//    }
//    //endregion
}
