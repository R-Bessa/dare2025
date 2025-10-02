package app.timers;

import pt.unl.fct.di.novasys.babel.generic.ProtoTimer;

public class StopTimer extends ProtoTimer {
    public static final short TIMER_ID = 405;

    public StopTimer() {
        super(TIMER_ID);
    }

    @Override
    public ProtoTimer clone() {
        return this;
    }
}
