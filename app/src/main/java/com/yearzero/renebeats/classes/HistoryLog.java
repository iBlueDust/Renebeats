package com.yearzero.renebeats.classes;

import java.io.Serializable;
import java.util.Date;

public class HistoryLog implements Serializable {
    // Appcode (EA50) - Class code (HistoryLog: 415C) - Gradle version (3) - Iteration
    private static final long serialVersionUID = 0xEA50_415C_0003_0000L;

    public boolean convert, normalize, overwrite;
    public short bitrate;
    public int track, year;
    public long id;
    public Integer start, end;
    public String album, artist, availformat, conv, down, format, mtdt, title, url, youtubeID;
    public String[] genres;
    public Date assigned, completed;
    public Status status;
    public Exception exception;

    public HistoryLog() { }

    public static HistoryLog Cast(Download data) {
        HistoryLog log = new HistoryLog();

        log.convert = data.convert;
        log.normalize = data.normalize;
        log.overwrite = data.overwrite;

        log.bitrate = data.bitrate;

        log.track = data.track;
        log.year = data.year;

        log.id = data.id;

        log.start = data.start;
        log.end = data.end;

        log.album = data.album;
        log.artist = data.artist;
        log.availformat = data.availformat;
        log.conv = data.conv;
        log.down = data.down;
        log.format = data.format;
        log.mtdt = data.mtdt;
        log.title = data.title;
        log.url = data.url;
        log.youtubeID = data.youtubeID;

        log.genres = data.genres;

        log.assigned = data.assigned;
        log.completed = data.completed;

        log.status = data.status;

        log.exception = data.exception;

        return log;
    }
}
