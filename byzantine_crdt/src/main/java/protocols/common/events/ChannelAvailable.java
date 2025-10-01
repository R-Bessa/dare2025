package protocols.common.events;

import pt.unl.fct.di.novasys.babel.generic.ProtoNotification;
import pt.unl.fct.di.novasys.network.data.Host;

import java.security.PrivateKey;
import java.security.PublicKey;

public class ChannelAvailable extends ProtoNotification {

	public static final short NOTIFICATION_ID = 104;

    private final int channelID;
	private final Host myHost;


	public ChannelAvailable(int chID, Host myHost) {
		super(NOTIFICATION_ID);
		this.channelID = chID;
        this.myHost = myHost;
	}

    public int getChannelID() {
		return channelID;
	}
	
	public Host getMyHost() {
		return this.myHost;
	}

}
