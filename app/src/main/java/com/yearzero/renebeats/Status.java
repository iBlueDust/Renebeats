package com.yearzero.renebeats;

@Deprecated
public enum Status {

    NONE(0),
    DOWN_QUEUE(1),
    DOWNLOADING(2),
    DOWNLOAD_PAUSED(3),
    PRE_CONV_PAUSE(4),
    CONV_QUEUE(5),
    CONVERTING(6),
    METADATA(7),
    COMPLETED(8),
    CANCELLED(9),
    FAILED(10);

    private int state;

    Status(int i) {
        state = i;
    }

    public int getValue() {
        return state;
    }

    public boolean isDownloadComplete() {
        return state > PRE_CONV_PAUSE.getValue() && state != FAILED.getValue();
    }
}
