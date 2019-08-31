package com.yearzero.renebeats;

public class InternalArgs {
    public static final int SUCCESS = 10;
    public static final int FAILED = 0;
    public static final int PROGRESS = 1;
    public static final int CANCELLED = 9;

    public static final String REMAINING = "remaining";
    public static final String REQUEST = "request";
    public static final String DATA = "data";
    public static final String RESULT = "result";
    public static final String TOTAL = "total";
    public static final String CURRENT = "current";
    public static final String INDETERMINATE = "indeterminate";
    public static final String EXCEPTION = "exception";
    public static final String INDEX = "index";
    public static final String PAUSED = "paused";
    public static final String SIZE = "size";
    //        public static final String NOTIF_CANCEL = "notifications.cancel";

    public static final int DESTROY = 0xDDDFF;
    public static final int ERR_LOAD = 0xEE0001;

    public static final int FLAG_COMPLETED = 0x1;
    public static final int FLAG_RUNNING = 0x10;
    public static final int FLAG_QUEUE = 0x100;

    public static final String REQ_ID = "request/id";
    public static final String REQ_COMPLETED = "request/completed";
    public static final String REQ_RUNNING = "request/running";
    public static final String REQ_QUEUE = "request/queue";
}