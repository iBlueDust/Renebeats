package com.yearzero.renebeats.preferences.enums;

public enum ProcessSpeed {

    ULTRAFAST(-5),
    SUPERFAST(-4),
    VERYFAST(-3),
    FASTER(-2),
    FAST(-1),
    MEDIUM(0),
    SLOW(1),
    SLOWER(2),
    VERYSLOW(3);

    private int value;

    ProcessSpeed(int value) {
        this.value = value;
    }

    public String getValue() {
        switch (value) {
            case -5:
                return "ultrafast";
            case -4:
                return "superfast";
            case -3:
                return "veryfast";
            case -2:
                return "faster";
            case -1:
                return "fast";
            case 1:
                return "slow";
            case 2:
                return "slower";
            case 3:
                return "veryslow";
            default:
                return "medium";
        }
    }

}
