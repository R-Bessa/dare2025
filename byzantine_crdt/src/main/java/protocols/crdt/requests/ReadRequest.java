package protocols.crdt.requests;

import pt.unl.fct.di.novasys.babel.generic.ProtoRequest;
import pt.unl.fct.di.novasys.network.data.Host;

public class ReadRequest extends ProtoRequest {

	public final static short REQUEST_ID = 501;

	private final Host sender;


	public ReadRequest(Host sender) {
		super(REQUEST_ID);
		this.sender = sender;
	}

    public Host getSender() {
        return sender;
    }
}
