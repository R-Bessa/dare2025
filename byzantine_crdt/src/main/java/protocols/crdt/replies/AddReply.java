package protocols.crdt.replies;

import pt.unl.fct.di.novasys.babel.generic.ProtoReply;

public class AddReply extends ProtoReply {
    public final static short REPLY_ID = 502;

    private final String element;

    public AddReply(String element)  {
        super(REPLY_ID);
        this.element = element;
    }

    public String getElement() {
        return element;
    }

}
