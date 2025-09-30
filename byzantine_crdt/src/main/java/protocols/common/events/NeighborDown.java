package protocols.common.events;

import pt.unl.fct.di.novasys.babel.generic.ProtoNotification;
import pt.unl.fct.di.novasys.network.data.Host;


public class NeighborDown extends ProtoNotification {

	public static final short NOTIFICATION_ID = 102;
	
	private final Host neighbor;

    public NeighborDown(Host neighbor) {
		super(NOTIFICATION_ID);
		this.neighbor = neighbor;
    }

	public Host getNeighbor() {
		return this.neighbor;
	}

}
