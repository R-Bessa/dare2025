package protocols.crdt.requests;

import pt.unl.fct.di.novasys.babel.generic.ProtoRequest;
import pt.unl.fct.di.novasys.network.data.Host;

import java.util.UUID;

public class RemoveRequest extends ProtoRequest {
    public final static short REQUEST_ID = 504;

    private final Host sender;
    private final UUID add_id;
    private final String element;

    public RemoveRequest(Host sender, String element, UUID add_id) {
        super(REQUEST_ID);
        this.sender = sender;
        this.add_id = add_id;
        this.element = element;
    }

    public Host getSender() {
        return sender;
    }

    public UUID getAdd_id() {
        return add_id;
    }

    public String getElement() {
        return element;
    }
}
