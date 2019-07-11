package com.yearzero.renebeats.download;

import android.util.SparseArray;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yearzero.renebeats.preferences.Preferences;
import com.yearzero.renebeats.preferences.enums.OverwriteMode;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Date;

import javax.annotation.ParametersAreNullableByDefault;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

@ParametersAreNullableByDefault
public
class Download extends Query implements Serializable {
    // App code (EA50) - "Class" (ClA5) - Class ID (D010_AD00)
    private final static long serialVersionUID = 0xEA50_C1A5_D010_AD00L;
    // Awaiting Kotlin to fix and allow negative hex declarations

    @Getter @Setter(AccessLevel.PACKAGE) private short bitrate;
    @Getter @Setter(AccessLevel.PACKAGE) private Integer start;
    @Getter @Setter(AccessLevel.PACKAGE) private Integer end;
    @Getter @Setter(AccessLevel.PACKAGE) private boolean indeterminate = true;
    @Getter @Setter(AccessLevel.PACKAGE) private boolean normalize = false;
    @Getter @Setter(AccessLevel.PACKAGE) private boolean convert = false;
    @Getter @Setter(AccessLevel.PACKAGE) private long current;
    @Getter @Setter(AccessLevel.PACKAGE) private long total;
    @Getter @Setter(AccessLevel.PACKAGE) private long size;
    @Getter @Setter(AccessLevel.PACKAGE)           private Exception exception;
    @Getter @Setter(AccessLevel.PACKAGE) private int id = 0;
    @Getter @Setter(AccessLevel.PACKAGE) @NonNull  private String format = Preferences.getFormat();
    @Getter @Setter(AccessLevel.PACKAGE)           private String url;
    @Getter @Setter(AccessLevel.PACKAGE)           private String down;
    @Getter @Setter(AccessLevel.PACKAGE)           private String availableFormat;
    @Getter @Setter(AccessLevel.PACKAGE)           private String conv;
    @Getter @Setter(AccessLevel.PACKAGE)           private String mtdt;
    @Getter @Setter(AccessLevel.PACKAGE)           private Boolean overwrite = Preferences.getOverwrite() == OverwriteMode.OVERWRITE;
    @Getter @Setter(AccessLevel.PACKAGE) @NonNull  private Status status = new Status();
    @Getter @Setter(AccessLevel.PACKAGE)           private Date assigned;
    @Getter @Setter(AccessLevel.PACKAGE)           private Date completeDate;
    @Getter @Setter(AccessLevel.PACKAGE)           private YouTubeExtractor.YtFile[] sparseArray;

    //Note: the field names are linked to Commons.java:112

    Download() {}

    Download(@NonNull Query query, short bitrate, @NonNull String format) {
        super(query.getYoutubeID(), query.getTitle(), query.getArtist(), query.getAlbum(), query.getYear(), query.getTrack(), query.getGenres(), query.getThumbMax(), query.getThumbHigh(), query.getThumbMedium(), query.getThumbDefault(), query.getThumbStandard());
        this.bitrate = bitrate;
        this.format = format;
        this.assigned = new Date();
        this.status = new Status();
    }

    Download(@NonNull Query query, short bitrate, @NonNull String format, @Nullable YouTubeExtractor.YtFile[] sparseArray, Integer start, Integer end, boolean normalize, long size) {
        this(query, bitrate, format);
        this.sparseArray = sparseArray;
        this.start = start;
        this.end = end;
        this.normalize = normalize;
        this.size = size;
    }

    Download(@NonNull Query query, short bitrate, @NonNull String format, @Nullable SparseArray<YouTubeExtractor.YtFile> sparseArray, Integer start, Integer end, boolean normalize, long size) {
        this(query, bitrate, format, (YouTubeExtractor.YtFile[]) null, start, end, normalize, size);
        if (sparseArray != null) {
            this.sparseArray = new YouTubeExtractor.YtFile[sparseArray.size()];
            for (int i = 0; i < this.sparseArray.length; i++)
                this.sparseArray[i] = sparseArray.valueAt(i);
        }
    }

    String getFilenameWithExt() {
        return getFilename() + '.' + getFormat();
    }

    void extractFromSparse() {
        int high_bit = -1;

        for (YouTubeExtractor.YtFile file : sparseArray) {
            if (file == null || file.getFormat().getAudioBitrate() <= high_bit || high_bit >= bitrate)
                continue;
            high_bit = file.getFormat().getAudioBitrate();
            url = file.getUrl();
            availableFormat = file.getFormat().getExt();
            convert = !(format.toLowerCase().equals(file.getFormat().getExt().toLowerCase()) && high_bit == bitrate);
        }
    }

//    void extractFromSparse() {
//        if (sparseArray == null) throw new IllegalStateException("SparseArray is null");
//        int lowBit = Integer.MAX_VALUE;
//        int j = -1;
//
//        for (int i = 0; i < sparseArray.length; i++) {
//            int bit = sparseArray[i].getFormat().getAudioBitrate();
//            if (bit > bitrate && bit < lowBit) {
//                lowBit = bit;
//                j = i;
//            }
//        }
//        if (j < 0) {
//            if (sparseArray.length > 0) j = 1;
//            else return;
//        }
//
//        url = sparseArray[j].getUrl();
//        availableFormat = sparseArray[j].getFormat().getExt().toLowerCase();
//        convert = !format.toLowerCase().equals(availableFormat) || lowBit != bitrate;
//    }

    @Override
    public int hashCode() {
        int result = 1369 + super.hashCode();

        result = 37 * result + (int) (assigned == null ? 0 : assigned.getTime() ^ assigned.getTime() >>> 32);
        result = 37 * result + (availableFormat == null ? 0 : availableFormat.hashCode());
        result = 37 * result + bitrate;
        result = 37 * result + (conv == null ? 0 : conv.hashCode());
        result = 37 * result + (convert ? 0 : 1);
        result = 37 * result + (int) (current ^ current >>> 32);
        result = 37 * result + (int) (completeDate == null ? 0 : completeDate.getTime() ^ completeDate.getTime() >>> 32);
        result = 37 * result + (down == null ? 0 : down.hashCode());
        result = 37 * result + (end == null ? 0 : end + 1);
        result = 37 * result + (exception == null ? 0 : exception.hashCode());
        result = 37 * result + format.hashCode();
        result = 37 * result + id;
        result = 37 * result + (indeterminate ? 0 : 1);
        result = 37 * result + (mtdt == null ? 0 : mtdt.hashCode());
        result = 37 * result + (normalize ? 0 : 1);
        result = 37 * result + (int) (size ^ size >>> 32);
        result = 37 * result + (sparseArray == null ? 0 : Arrays.hashCode(sparseArray));
        result = 37 * result + (start == null ? 0 : start + 1);
        result = 37 * result + status.pack();
        result = 37 * result + (int) (total ^ total >>> 32);
        result = 37 * result + (url == null ? 0 : url.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Download)) return false;
        if (!super.equals(o)) return false;
        Download download = (Download) o;
        return bitrate == download.bitrate &&
                indeterminate == download.indeterminate &&
                normalize == download.normalize &&
                convert == download.convert &&
                current == download.current &&
                total == download.total &&
                size == download.size &&
                id == download.id &&
                equals(start, download.start) &&
                equals(end, download.end) &&
                equals(exception, download.exception) &&
                format.equals(download.format) &&
                equals(url, download.url) &&
                equals(down, download.down) &&
                equals(availableFormat, download.availableFormat) &&
                equals(conv, download.conv) &&
                equals(mtdt, download.mtdt) &&
                equals(overwrite, download.overwrite) &&
                status.equals(download.status) &&
                equals(assigned, download.assigned) &&
                equals(completeDate, download.completeDate) &&
                Arrays.equals(sparseArray, download.sparseArray);
    }

    private boolean equals(Object a, Object b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        return a.equals(b);
    }
}