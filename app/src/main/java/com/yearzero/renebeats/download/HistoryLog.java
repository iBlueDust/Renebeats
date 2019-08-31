package com.yearzero.renebeats.download;

import com.yearzero.renebeats.BuildConfig;
import com.yearzero.renebeats.preferences.Preferences;

import java.io.Serializable;
import java.util.Date;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter(AccessLevel.PACKAGE)
public class HistoryLog implements Serializable {
    // Appcode (EA50) - "Class" (ClA5) - Class ID (415C_1066)
    private static long serialVersionUID = 0xEA50_C1A5_415C_1066L;

    //TODO: Merge with Download and use custom Serializer or Annotations?

    String version;
    int versionCode = 0;
    boolean convert = false;
    boolean normalize = false;
    boolean overwrite = false;
    short bitrate = 0;
    int track = 0;
    int year = 0;
    int id = 0;
    Integer start;
    Integer end;
    String album;
    String artist;
    String availableFormat;
    String conv;
    String down;
    String format;
    String mtdt;
    String title;
    String url;
    String youtubeID;
    String genres;
    Date assigned;
    Date completed;
    String status_download;
    String status_convert;
    Boolean status_meta = null;
    boolean invalid = false;
    Exception exception;


    public Status getStatus() {
        return new Status(Status.Download.fromValue(status_download), Status.Convert.fromValue(status_convert), status_meta);
    }

    public Download uncast() {
        Download d = new Download(new Query(youtubeID, title, artist, album, year, track, genres),
                bitrate,
                format == null ? Preferences.getFormat() : format,
                new YouTubeExtractor.YtFile[0],
                this.start,
                end,
                normalize,
                0L
        );
        d.setConvert(convert);
        d.setOverwrite(overwrite);

        d.setId(id);

        d.setAvailableFormat(availableFormat);
        d.setConv(conv);
        d.setDown(down);
        d.setMtdt(mtdt);
        d.setUrl(url);

        d.setAssigned(assigned);
        d.setCompleteDate(completed);

        d.setStatus(getStatus());
        d.setException(exception);

        return d;
    }

    public static HistoryLog generate(Download data) {
        HistoryLog log = cast(data);
        log.version = BuildConfig.VERSION_NAME;
        log.versionCode = BuildConfig.VERSION_CODE;
        return log;
    }

    static HistoryLog cast(Download data) {
        HistoryLog log = new HistoryLog();

        log.convert = data.isConvert();
        log.normalize = data.isNormalize();
        log.overwrite = data.getOverwrite();

        log.bitrate = data.getBitrate();

        log.track = data.getTrack();
        log.year = data.getYear();

        log.id = data.getId();

        log.start = data.getStart();
        log.end = data.getEnd();

        log.album = data.getAlbum();
        log.artist = data.getArtist();
        log.availableFormat = data.getAvailableFormat();
        log.conv = data.getConv();
        log.down = data.getDown();
        log.format = data.getFormat();
        log.mtdt = data.getMtdt();
        log.title = data.getTitle();
        log.url = data.getUrl();
        log.youtubeID = data.getYoutubeID();

        log.genres = data.getGenres();

        log.assigned = data.getAssigned();
        log.completed = data.getCompleteDate();

        log.status_download = data.getStatus().getDownload() == null ? null : data.getStatus().getDownload().getValue();
        log.status_convert = data.getStatus().getConvert() == null ? null : data.getStatus().getConvert().getValue();
        log.status_meta = data.getStatus().getMetadata();
        log.invalid = data.getStatus().isInvalid();

        log.exception = data.getException();

        return log;
    }
}
