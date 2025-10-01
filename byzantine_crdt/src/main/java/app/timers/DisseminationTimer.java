package app.timers;

import pt.unl.fct.di.novasys.babel.generic.ProtoTimer;

public class DisseminationTimer extends ProtoTimer {
    public static final short TIMER_ID = 401;

    public DisseminationTimer() {
        super(TIMER_ID);
    }

    @Override
    public ProtoTimer clone() {
        return this;
    }
}
