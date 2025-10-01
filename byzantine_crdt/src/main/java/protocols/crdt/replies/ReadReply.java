package protocols.crdt.replies;

import pt.unl.fct.di.novasys.babel.generic.ProtoReply;
import pt.unl.fct.di.novasys.network.data.Host;

import java.util.Set;

public class ReadReply extends ProtoReply {
    public final static short REPLY_ID = 503;

    private final Host sender;
    private final Set<String> state;

    public ReadReply(Host sender, Set<String> state)  {
        super(REPLY_ID);
        this.sender = sender;
        this.state = state;
    }

    public Host getSender() {
        return sender;
    }

    public Set<String> getState() {
        return state;
    }
}
