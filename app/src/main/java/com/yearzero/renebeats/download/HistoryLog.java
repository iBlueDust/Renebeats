package com.yearzero.renebeats.download;

import androidx.annotation.Nullable;

import com.yearzero.renebeats.BuildConfig;
import com.yearzero.renebeats.preferences.Preferences;

import java.io.Serializable;
import java.util.Date;

import javax.annotation.ParametersAreNullableByDefault;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

@ParametersAreNullableByDefault
@Getter @Setter(AccessLevel.PACKAGE)
public class HistoryLog implements Serializable {
    // Appcode (EA50) - "Class" (ClA5) - Class ID (415C_1066)
    private static long serialVersionUID = 0xEA50_C1A5_415C_1066L;

    //TODO: Merge with Download and use custom Serializer or Annotations?

    private String version;
    private int versionCode = 0;
    private boolean convert = false;
    private boolean normalize = false;
    private boolean overwrite = false;
    private short bitrate = 0;
    private int track = 0;
    private int year = 0;
    private int downloadId = 0;
    private long id = 0;
    private Integer start;
    private Integer end;
    private String album;
    private String artist;
    private String availableFormat;
    private String conv;
    private String down;
    private String format;
    private String mtdt;
    private String title;
    private String url;
    private String youtubeID;
    private String genres;
    private Date assigned;
    private Date completed;
    private String status_download;
    private String status_convert;
    private Boolean status_meta = null;
    private boolean invalid = false;
    private Exception exception;

    @Nullable
    public String getFilename(String sym_separator) {
        if (artist != null && title != null)
            return Preferences.getArtist_first() ?
                    artist + ' ' + sym_separator + ' ' + title :
                    title + ' ' + sym_separator + ' ' + artist;
        else if (artist != null) return artist;
        else if (title != null) return title;
        else return null;
    }

    public Date getDate() {
        return assigned;
    }

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

        d.setDownloadId(downloadId);
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

    private static HistoryLog cast(Download data) {
        HistoryLog log = new HistoryLog();

        log.convert = data.isConvert();
        log.normalize = data.isNormalize();
        log.overwrite = data.getOverwrite();

        log.bitrate = data.getBitrate();

        log.track = data.getTrack();
        log.year = data.getYear();

        log.downloadId = data.getDownloadId();
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

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) return true;
        if (o instanceof Download) return ((Download) o).getId() == id;
        else if (o instanceof HistoryLog) return ((HistoryLog) o).id == id;
        else return false;
    }

        public static long getSerialVersionUID() {
            return serialVersionUID;
        }

        public String getVersion() {
            return version;
        }

        public int getVersionCode() {
            return versionCode;
        }

        public boolean isConvert() {
            return convert;
        }

        public boolean isNormalize() {
            return normalize;
        }

        public boolean isOverwrite() {
            return overwrite;
        }

        public short getBitrate() {
            return bitrate;
        }

        public int getTrack() {
            return track;
        }

        public int getYear() {
            return year;
        }

        public int getDownloadId() {
            return downloadId;
        }

        public long getId() {
            return id;
        }

        public Integer getStart() {
            return start;
        }

        public Integer getEnd() {
            return end;
        }

        public String getAlbum() {
            return album;
        }

        public String getArtist() {
            return artist;
        }

        public String getAvailableFormat() {
            return availableFormat;
        }

        public String getConv() {
            return conv;
        }

        public String getDown() {
            return down;
        }

        public String getFormat() {
            return format;
        }

        public String getMtdt() {
            return mtdt;
        }

        public String getTitle() {
            return title;
        }

        public String getUrl() {
            return url;
        }

        public String getYoutubeID() {
            return youtubeID;
        }

        public String getGenres() {
            return genres;
        }

        public Date getAssigned() {
            return assigned;
        }

        public Date getCompleted() {
            return completed;
        }

        public String getStatus_download() {
            return status_download;
        }

        public String getStatus_convert() {
            return status_convert;
        }

        public Boolean getStatus_meta() {
            return status_meta;
        }

        public boolean isInvalid() {
            return invalid;
        }

        public Exception getException() {
            return exception;
        }
}
