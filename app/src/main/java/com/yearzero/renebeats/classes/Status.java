package com.yearzero.renebeats.classes;

import java.io.Serializable;

public class Status implements Serializable {
    // Appcode (EA50) - Class code (Status: 55A5) - Gradle version (3) - Iteration
    private static final long serialVersionUID = 0xEA50_55A5_0003_0000L;

    public enum Download {
        QUEUED(0),
        RUNNING(1),
        NETWORK_PENDING(2),
        PAUSED(3),
        COMPLETE(4),
        CANCELLED(5),
        FAILED(6);

        private int value;

        Download(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }
    
    public enum Convert {
        SKIPPED(0),
        PAUSED(1),
        QUEUED(2),
        RUNNING(3),
        CANCELLED(4),
        COMPLETE(5),
        FAILED(6);

        private int value;

        Convert(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }
    
    public Download download;
    public Convert convert;
    public Boolean metadata;
    public boolean invalid;

    public Status() { }

    public Status(Download download, Convert convert, Boolean metadata) {
        this.download = download;
        this.convert = convert;
        this.metadata = metadata;
    }

    public boolean isSuccessful() {
        return download == Download.COMPLETE && (convert == Convert.COMPLETE || convert == Convert.SKIPPED) && metadata != null && metadata;
    }

    public boolean isQueued() {
        return (download == Download.QUEUED || convert == Convert.QUEUED) && metadata == null;
    }

    public boolean isCancelled() {
        return (download == Download.CANCELLED || convert == Convert.CANCELLED) && metadata == null;
    }

    public boolean isFailed() {
        return download == Download.FAILED || convert == Convert.FAILED || (metadata != null && !metadata);
    }

    public boolean isInvalid() {
        return invalid;
    }

    public int Pack() {
        int mtdt = 0;
        if (metadata != null) {
            if (metadata)
                mtdt = 2;
            else mtdt = 1;
        }

        return ((download == null ? 0 : download.getValue() + 1) << 20) | (convert == null ? 0 : (convert.getValue() + 1) << 10) | mtdt;
    }

    public static Status Unpack(int pkg) {
        Boolean md = null;
        int pmd = pkg & 0x3FF;
        if (pmd == 1)
            md = false;
        else if (pmd == 2)
            md = true;

        int downshift = pkg >> 20;
        int convshift = (pkg >> 10) & 0x3FF;

        return new Status(downshift <= 0 || downshift > Download.values().length ? null : Download.values()[downshift - 1],
                convshift <= 0 || convshift > Convert.values().length ? null : Convert.values()[(convshift - 1)],
                md);
    }
}
