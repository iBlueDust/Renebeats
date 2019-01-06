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

    public enum DownloadStatus {
        QUEUED(0),
        RUNNING(1),
        NETWORK_PENDING(2),
        PAUSED(3),
        COMPLETE(4),
        CANCELLED(5),
        FAILED(6);

        private int state;

        DownloadStatus(int state) {
            this.state = state;
        }

        public int getValue() {
            return state;
        }
    }
    private static final long serialVersionUID = 1000L;

    public enum ConvertStatus {
        SKIPPED(0),
        PAUSED(1),
        QUEUED(2),
        RUNNING(3),
        CANCELLED(4),
        COMPLETE(5),
        FAILED(6);

        private int state;

        ConvertStatus(int state) {
            this.state = state;
        }

        public int getValue() {
            return state;
        }
    }

    public short bitrate;
    public Integer start, end;
    public boolean indeterminate = true;
    public int current, total;
    public String format;
    public Exception exception;

    boolean normalize, convert;
    int downloadId;
    String url, down, availformat, conv, mtdt;

    public Commons.Pref.OverwriteMode overwrite;
    public DownloadStatus downloadStatus;
    public ConvertStatus convertStatus;
    public Boolean metadataSuccess = null;

    public Date assigned, completed;

    YouTubeExtractor.YtFile[] sparseArray;

    public Download(@NonNull Query query, short bitrate, String format) {
        super(query.id, query.title, query.artist, query.album, query.year, query.track, query.genres, query.getThumbMax(), query.getThumbHigh(), query.getThumbMedium(), query.getThumbDefault(), query.getThumbStandard(), query.thumbmap);
        this.bitrate = bitrate;
        this.format = format;
        this.assigned = new Date();
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

    private Download(DownloadStatus downloadStatus, ConvertStatus convertStatus, Boolean metadataSuccess) {
        this.downloadStatus = downloadStatus;
        this.convertStatus = convertStatus;
        this.metadataSuccess = metadataSuccess;
    }

    public Date getCompleteDate() {
        return completed;
    }

    public boolean isSuccessful() {
        return downloadStatus == DownloadStatus.COMPLETE && (convertStatus == ConvertStatus.COMPLETE || convertStatus == ConvertStatus.SKIPPED) && metadataSuccess != null && metadataSuccess;
    }

    public boolean isQueued() {
        return (downloadStatus == DownloadStatus.QUEUED || convertStatus == ConvertStatus.QUEUED) && metadataSuccess == null;
    }

    public boolean isCancelled() {
        return (downloadStatus == DownloadStatus.CANCELLED || convertStatus == ConvertStatus.CANCELLED) && metadataSuccess == null;
    }

    public boolean isFailed() {
        return downloadStatus == DownloadStatus.FAILED || convertStatus == ConvertStatus.FAILED || (metadataSuccess != null && !metadataSuccess);
    }

    public int statusPack() {
        int metadata = 0;
        if (metadataSuccess != null) {
            if (metadataSuccess)
                metadata = 2;
            else metadata = 1;
        }

        return ((downloadStatus == null ? 0 : downloadStatus.getValue() + 1) << 20) | (convertStatus == null ? 0 : (convertStatus.getValue() + 1) << 10) | metadata;
    }

    
    public static Download statusUnpack(int pkg) {
        Boolean md = null;
        int pmd = pkg & 0x3FF;
        if (pmd == 1)
            md = false;
        else if (pmd == 2)
            md = true;

        int downshift = pkg >> 20;
        int convshift = (pkg >> 10) & 0x3FF;
        
        return new Download(downshift <= 0 || downshift >= Download.DownloadStatus.values().length ? null : Download.DownloadStatus.values()[downshift - 1],
                convshift <= 0 || convshift >= Download.ConvertStatus.values().length ? null : Download.ConvertStatus.values()[(convshift - 1)],
                md);
    }


    public Download clone() {
        Download clone = new Download(new Query(id, title, artist, album, year, track, genres, thumbMax, thumbHigh, thumbMedium, thumbDefault, thumbStandard, thumbmap), bitrate, format, sparseArray, start, end, normalize);
        clone.convert = convert;
        clone.downloadId = downloadId;
        clone.url = url;
        clone.down = down;
        clone.conv = conv;
        clone.exception = exception;
        clone.downloadStatus = downloadStatus;
        clone.convertStatus = convertStatus;
        clone.metadataSuccess = metadataSuccess;
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
        int result = 37;

        result = 37 * result + (int)(assigned.getTime() * (assigned.getTime() >>> 32));
        result = 37 * result + (availformat == null ? 0 : availformat.hashCode());
        result = 37 * result + bitrate;
        result = 37 * result + (conv == null ? 0 : conv.hashCode());
        result = 37 * result + (convert ? 0 : 1);
        result = 37 * result + (convertStatus == null ? 0 : 1 + convertStatus.getValue());
        result = 37 * result + current;
        result = 37 * result + (completed == null ? 0 : (int)(completed.getTime() * (completed.getTime() >>> 32)));
        result = 37 * result + (down == null ? 0 : down.hashCode());
        result = 37 * result + downloadId;
        result = 37 * result + (downloadStatus == null ? 0 : 1 + downloadStatus.getValue());
        result = 37 * result + (end == null ? 0 : end + 1);
        result = 37 * result + (exception == null ? 0 : exception.hashCode());
        result = 37 * result + (format == null ? 0 : format.hashCode());
        result = 37 * result + (indeterminate ? 0 : 1);
        result = 37 * result + (metadataSuccess == null ? 0 : metadataSuccess.hashCode());
        result = 37 * result + (mtdt == null ? 0 : mtdt.hashCode());
        result = 37 * result + (normalize ? 0 : 1);
        result = 37 * result + (sparseArray == null ? 0 : Arrays.hashCode(sparseArray));
        result = 37 * result + (start == null ? 0 : start + 1);
        result = 37 * result + total;
        result = 37 * result + (url == null ? 0 : url.hashCode());

        return result;
    }
}