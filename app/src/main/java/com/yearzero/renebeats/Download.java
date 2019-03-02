package com.yearzero.renebeats;

import android.util.SparseArray;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Date;

import javax.annotation.ParametersAreNullableByDefault;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

@ParametersAreNullableByDefault
public class Download extends Query implements Serializable {
    // Appcode (EA50) - Class code (Download: D01D) - Gradle version (3) - Iteration
    private static final long serialVersionUID = 0xEA50_D01D_0003_0002L;

    public short bitrate;
    public Integer start, end;
    public boolean indeterminate = true;
    public int current, total;
    public String format;
    public Exception exception;

    boolean normalize, convert;
    long id;
    String url, down, availformat, conv, mtdt;

    public boolean overwrite;
    public Status status;
    public Date assigned, completed;

    YouTubeExtractor.YtFile[] sparseArray;

    public Download(@NonNull Query query, short bitrate, String format) {
        super(query.youtubeID, query.title, query.artist, query.album, query.year, query.track, query.genres, query.getThumbMax(), query.getThumbHigh(), query.getThumbMedium(), query.getThumbDefault(), query.getThumbStandard(), query.thumbmap);
        this.bitrate = bitrate;
        this.format = format;
        this.assigned = new Date();
        this.status = new Status();
    }

    public Download(@NonNull Query query, short bitrate, String format, YouTubeExtractor.YtFile[] sparseArray, @Nullable Integer start, @Nullable Integer end, boolean normalize) {
        this(query, bitrate, format);
        this.sparseArray = sparseArray;
        this.start = start;
        this.end = end;
        this.normalize = normalize;
    }

    public Download(@NonNull Query query, short bitrate, String format, SparseArray<YouTubeExtractor.YtFile> sparseArray, @Nullable Integer start, @Nullable Integer end, boolean normalize) {
        this(query, bitrate, format, new YouTubeExtractor.YtFile[0], start, end, normalize);
        if (sparseArray != null) {
            this.sparseArray = new YouTubeExtractor.YtFile[sparseArray.size()];
            for (int i = 0; i < sparseArray.size(); i++)
                this.sparseArray[i] = sparseArray.get(sparseArray.keyAt(i));
        }
    }

    public Date getCompleteDate() {
        return completed;
    }

    public Download clone() {
        Download clone = new Download(new Query(youtubeID, title, artist, album, year, track, genres, thumbMax, thumbHigh, thumbMedium, thumbDefault, thumbStandard, thumbmap), bitrate, format, sparseArray, start, end, normalize);
        clone.convert = convert;
        clone.id = id;
        clone.url = url;
        clone.down = down;
        clone.conv = conv;
        clone.exception = exception;
        clone.status = status;
        clone.completed = completed;
        return clone;
    }

    void extractFromSparse() {
        int high_bit = -1;
        int i = 0;

        while (i < sparseArray.length) {
            YouTubeExtractor.YtFile file = sparseArray[i];
            if (file == null) continue;
            if (file.getFormat().getAudioBitrate() > high_bit && high_bit < bitrate) {
                high_bit = file.getFormat().getAudioBitrate();
                url = file.getUrl();
                availformat = file.getFormat().getExt();
                convert = !(format.toLowerCase().equals(file.getFormat().getExt().toLowerCase()) && high_bit == bitrate);
            }
            i++;
        }
    }

    public int hashCode() {
        int result = 1369 + super.hashCode();

        result = 37 * result + (int)(assigned.getTime() ^ (assigned.getTime() >>> 32));
        result = 37 * result + (availformat == null ? 0 : availformat.hashCode());
        result = 37 * result + bitrate;
        result = 37 * result + (conv == null ? 0 : conv.hashCode());
        result = 37 * result + (convert ? 0 : 1);
        result = 37 * result + current;
        result = 37 * result + (completed == null ? 0 : (int)(completed.getTime() ^ (completed.getTime() >>> 32)));
        result = 37 * result + (down == null ? 0 : down.hashCode());
        result = 37 * result + (end == null ? 0 : end + 1);
        result = 37 * result + (exception == null ? 0 : exception.hashCode());
        result = 37 * result + (format == null ? 0 : format.hashCode());
        result = 37 * result + (int) (id ^ (id >>> 32));
        result = 37 * result + (indeterminate ? 0 : 1);
        result = 37 * result + (mtdt == null ? 0 : mtdt.hashCode());
        result = 37 * result + (normalize ? 0 : 1);
        result = 37 * result + (sparseArray == null ? 0 : Arrays.hashCode(sparseArray));
        result = 37 * result + (start == null ? 0 : start + 1);
        result = 37 * result + (status == null ? 0 : status.Pack());
        result = 37 * result + total;
        result = 37 * result + (url == null ? 0 : url.hashCode());

        return result;
    }
}