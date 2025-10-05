package protocols.events;

import java.security.PrivateKey;
import java.security.PublicKey;

import pt.unl.fct.di.novasys.babel.generic.ProtoNotification;
import pt.unl.fct.di.novasys.network.data.Host;

public class SecureChannelAvailable extends ProtoNotification {

	public static final short NOTIFICATION_ID = 103;

    private final int channelID;
	private final Host myHost;
	private final PrivateKey myPrivateKey;
	private final PublicKey myPublicKey;
	
	public SecureChannelAvailable(int chID, Host myHost, PrivateKey key, PublicKey pKey) {
		super(NOTIFICATION_ID);
		this.channelID = chID;
        this.myHost = myHost;
		this.myPrivateKey = key;
		this.myPublicKey = pKey;
	}

    public int getChannelID() {
		return channelID;
	}
	
	public Host getMyHost() {
		return this.myHost;
	}

	public PrivateKey getMyPrivateKey() {
		return this.myPrivateKey;
	}
	
	public PublicKey getMyPublicKey() {
		return this.myPublicKey;
	}
}
