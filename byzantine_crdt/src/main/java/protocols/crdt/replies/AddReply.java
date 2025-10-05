package protocols.crdt.replies;

import pt.unl.fct.di.novasys.babel.generic.ProtoReply;
import pt.unl.fct.di.novasys.network.data.Host;

import java.util.UUID;

public class AddReply extends ProtoReply {
    public final static short REPLY_ID = 502;

    private final Host sender;
    private final UUID add_id;
    private final String element;

    public AddReply(Host sender, UUID add_id, String element)  {
        super(REPLY_ID);
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
