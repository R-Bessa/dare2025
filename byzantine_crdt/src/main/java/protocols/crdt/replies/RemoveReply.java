package protocols.crdt.replies;

import pt.unl.fct.di.novasys.babel.generic.ProtoReply;

public class RemoveReply extends ProtoReply {
    public final static short REPLY_ID = 504;

    private final String element;


    public RemoveReply(String element)  {
        super(REPLY_ID);
        this.element = element;
    }

    public String getElement() {
        return element;
    }

}
