package jwtc.android.chess.ics;

import jwtc.android.chess.services.ClockApi;

public class ICSClockApi extends ClockApi {
    public void setBlackRemaining(long remaining) {
        this.blackRemaining = remaining;
        dispatchClockTime();
    }

    public void setWhiteRemaining(long remaining) {
        this.whiteRemaining = remaining;
        dispatchClockTime();
    }

    public long getBlackRemaining() {
        return blackRemaining;
    }

    public long getWhiteRemaining() {
        return whiteRemaining;
    }
}
