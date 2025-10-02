package protocols.crdt.replies;

import pt.unl.fct.di.novasys.babel.generic.ProtoReply;
import pt.unl.fct.di.novasys.network.data.Host;

import java.util.Set;
import java.util.UUID;

public class RemoveReply extends ProtoReply {
    public final static short REPLY_ID = 504;

    private final Host sender;
    private final UUID add_id;
    private final String element;
    private final Set<String> state;

    public RemoveReply(Host sender, UUID add_id, String element, Set<String> state)  {
        super(REPLY_ID);
        this.sender = sender;
        this.add_id = add_id;
        this.element = element;
        this.state = state;
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

    public Set<String> getState() {
        return state;
    }
}
