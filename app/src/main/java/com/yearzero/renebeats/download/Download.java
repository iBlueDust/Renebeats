package com.yearzero.renebeats.download;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yearzero.renebeats.preferences.Preferences;
import com.yearzero.renebeats.preferences.enums.OverwriteMode;

import java.io.Serializable;
import java.util.Date;
import java.util.UUID;

import javax.annotation.ParametersAreNullableByDefault;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

@ParametersAreNullableByDefault
@Getter @Setter(AccessLevel.PACKAGE)
public class Download extends Query implements Serializable {
    // App code (EA50) - "Class" (ClA5) - Class ID (D010_AD00)
    private final static long serialVersionUID = 0xEA50_C1A5_D010_AD00L;

              private short bitrate;
              private boolean indeterminate = true;
              private boolean normalize = false;
              private boolean convert = false;
              private long current;
              private long total;
              private long id = UUID.randomUUID().getLeastSignificantBits();
              private Exception exception;
              private int downloadId = 0;
    @NonNull  private String format = Preferences.getFormat();
              private String url;
              private String down;
              private String availableFormat;
              private String conv;
              private String mtdt;
              private Boolean overwrite = Preferences.getOverwrite() == OverwriteMode.OVERWRITE;
    @NonNull  private Status status = new Status();
              private Date assigned;
              private Date completeDate;

    //Note: the field names are linked to Commons.java:112

    Download() {}

    Download(@NonNull Query query, short bitrate, @NonNull String format) {
        super(query.getYoutubeID(), query.getTitle(), query.getArtist(), query.getAlbum(), query.getYear(), query.getTrack(), query.getGenres(), query.getThumbMax(), query.getThumbHigh(), query.getThumbMedium(), query.getThumbDefault(), query.getThumbStandard());
        this.bitrate = bitrate;
        this.format = format;
        this.assigned = new Date();
        this.status = new Status();
    }

    Download(@NonNull Query query, short bitrate, @NonNull String format, boolean normalize) {
        this(query, bitrate, format);
        this.normalize = normalize;
    }

//    Download(@NonNull Query query, short bitrate, @NonNull String format, @Nullable SparseArray<YouTubeExtractor.YtFile> sparseArray, Integer start, Integer end, boolean normalize, long size) {
//        this(query, bitrate, format, (YouTubeExtractor.YtFile[]) null, start, end, normalize, size);
//        if (sparseArray != null) {
//            this.sparseArray = new YouTubeExtractor.YtFile[sparseArray.size()];
//            for (int i = 0; i < this.sparseArray.length; i++)
//                this.sparseArray[i] = sparseArray.valueAt(i);
//        }
//    }

    String getFilenameWithExt() {
        return getFilename() + '.' + getFormat();
    }

    @Override
    public int hashCode() {
        int result = 1369 + super.hashCode();

        long assign = assigned.getTime();
        result = 37 * result + (int) (assigned == null ? 0 : assign ^ assign >>> 32);
        result = 37 * result + (availableFormat == null ? 0 : availableFormat.hashCode());
        result = 37 * result + bitrate;
        result = 37 * result + (conv == null ? 0 : conv.hashCode());
        result = 37 * result + (convert ? 0 : 1);
        result = 37 * result + (int) (current ^ current >>> 32);
        long complete = completeDate.getTime();
        result = 37 * result + (int) (completeDate == null ? 0 : complete ^ complete >>> 32);
        result = 37 * result + (down == null ? 0 : down.hashCode());
        result = 37 * result + downloadId;
        result = 37 * result + (exception == null ? 0 : exception.hashCode());
        result = 37 * result + format.hashCode();
        result = 37 * result + (int) (id ^ id >>> 32);
        result = 37 * result + (indeterminate ? 0 : 1);
        result = 37 * result + (mtdt == null ? 0 : mtdt.hashCode());
        result = 37 * result + (normalize ? 0 : 1);
        result = 37 * result + status.pack();
        result = 37 * result + (int) (total ^ total >>> 32);
        result = 37 * result + (url == null ? 0 : url.hashCode());
        return result;
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) return true;
        if (o instanceof Download) return ((Download) o).id == id;
        else if (o instanceof HistoryLog) return ((HistoryLog) o).getId() == id;
        else return false;
    }

    //    @Override
//    public boolean equals(Object o) {
//        if (this == o) return true;
//        if (!(o instanceof Download)) return false;
//        if (!super.equals(o)) return false;
//        Download download = (Download) o;
//        return bitrate == download.bitrate &&
//                indeterminate == download.indeterminate &&
//                normalize == download.normalize &&
//                convert == download.convert &&
//                current == download.current &&
//                total == download.total &&
//                size == download.size &&
//                downloadId == download.downloadId &&
//                equals(start, download.start) &&
//                equals(end, download.end) &&
//                equals(exception, download.exception) &&
//                format.equals(download.format) &&
//                equals(url, download.url) &&
//                equals(down, download.down) &&
//                equals(availableFormat, download.availableFormat) &&
//                equals(conv, download.conv) &&
//                equals(mtdt, download.mtdt) &&
//                equals(overwrite, download.overwrite) &&
//                status.equals(download.status) &&
//                equals(assigned, download.assigned) &&
//                equals(completeDate, download.completeDate) &&
//                Arrays.equals(sparseArray, download.sparseArray);
//    }
//
//    private boolean equals(Object a, Object b) {
//        if (a == b) return true;
//        if (a == null || b == null) return false;
//        return a.equals(b);
//    }
}