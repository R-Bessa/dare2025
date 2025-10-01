package protocols.common.events;

import pt.unl.fct.di.novasys.babel.generic.ProtoNotification;
import pt.unl.fct.di.novasys.network.data.Host;

import java.security.PublicKey;

public class NeighborUp extends ProtoNotification {

	public static final short NOTIFICATION_ID = 101;

	private final Host neighbor;


	public NeighborUp(Host neighbor) {
		super(NOTIFICATION_ID);
		this.neighbor = neighbor;
	}

	public Host getNeighbor() {
		return this.neighbor;
	}

}
