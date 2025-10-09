package protocols.crdt;

import app.AutomatedApp;
import app.InteractiveApp;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import protocols.broadcast.byzantine.ByzantineReliableBcastProtocol;
import protocols.broadcast.notifications.DeliveryNotification;
import protocols.broadcast.request.BroadcastRequest;
import protocols.events.SecureChannelAvailable;
import protocols.crdt.replies.AddReply;
import protocols.crdt.replies.ReadReply;
import protocols.crdt.replies.RemoveReply;
import protocols.crdt.requests.AddRequest;
import protocols.crdt.requests.ReadRequest;
import protocols.crdt.requests.RemoveRequest;
import pt.unl.fct.di.novasys.babel.core.GenericProtocol;
import pt.unl.fct.di.novasys.babel.exceptions.HandlerRegistrationException;
import pt.unl.fct.di.novasys.network.data.Host;

import java.util.*;

public class ByzantineORSet extends GenericProtocol {
    private final Logger logger = LogManager.getLogger(ByzantineORSet.class);

    public static final String PROTO_NAME = "Byzantine Observed-Remove Set CRDT";
    public static final short PROTO_ID = 501;

    public static final String ADD_OP = "add";
    public static final String REMOVE_OP = "remove";

    public static final String APP_MODE = "app_interaction";

    private final Map<String, Set<UUID>> state;
    private Host mySelf;
    private short appProtoId;


    public ByzantineORSet() {
        super(PROTO_NAME, PROTO_ID);
        this.state = new HashMap<>();
        this.mySelf = null;
    }

    @Override
    public void init(Properties props) throws HandlerRegistrationException {

        appProtoId = props.getProperty(APP_MODE).equals("interactive") ? InteractiveApp.PROTO_ID : AutomatedApp.PROTO_ID;

        /* -------------------------------- Register Request Handlers -------------------------------- */
        registerRequestHandler(AddRequest.REQUEST_ID, this::handleAddRequest);
        registerRequestHandler(RemoveRequest.REQUEST_ID, this::handleRemoveRequest);
        registerRequestHandler(ReadRequest.REQUEST_ID, this::handleReadRequest);

        /* ----------------------------- Register Notification Handlers ------------------------------ */
        subscribeNotification(DeliveryNotification.NOTIFICATION_ID, this::uponDeliver);
        subscribeNotification(SecureChannelAvailable.NOTIFICATION_ID, this::uponChannelAvailable);
    }



    /* ---------------------------------- Request Handlers ------------------------------------------- */

    public void handleAddRequest(AddRequest req, short sourceProto) {
        logger.debug("Received Add Operation: ({},{})", req.getAdd_id(), req.getElement());

        Operation op = new Operation(ADD_OP, Set.of(req.getAdd_id()), req.getElement());
        BroadcastRequest bcast_req = new BroadcastRequest(mySelf, op.encode());
        sendRequest(bcast_req, ByzantineReliableBcastProtocol.PROTO_ID);
    }

    public void handleRemoveRequest(RemoveRequest req, short sourceProto) {
        logger.debug("Received Remove Operation: ({})", req.getElement());

        Set<UUID> observed_adds = state.get(req.getElement());
        if(observed_adds == null)
            sendReply(new RemoveReply(req.getElement()), appProtoId);

        else {
            Operation op = new Operation(REMOVE_OP, state.get(req.getElement()), req.getElement());
            BroadcastRequest bcast_req = new BroadcastRequest(mySelf, op.encode());
            sendRequest(bcast_req, ByzantineReliableBcastProtocol.PROTO_ID);
        }
    }

    public void handleReadRequest(ReadRequest req, short sourceProto) {
        logger.debug("Received Read Operation");

        ReadReply reply = new ReadReply(mySelf, state.keySet());
        sendReply(reply, appProtoId);
    }



    /* ----------------------------------- Notification Handlers ----------------------------------- */

    public void uponChannelAvailable(SecureChannelAvailable notification, short sourceProto) {
        this.mySelf = notification.getMyHost();
    }

    private void uponDeliver(DeliveryNotification notification, short sourceProto) {
        Operation op = Operation.decode(notification.getPayload());

        if(op.getType().equals(ADD_OP))
            processAddOperation(op);

        else if(op.getType().equals(REMOVE_OP))
            processRemoveRequest(op);

        if(notification.getSender().equals(mySelf)) {
            if(op.getType().equals(ADD_OP))
                sendReply(new AddReply(op.getElement()), appProtoId);

            else if(op.getType().equals(REMOVE_OP))
                sendReply(new RemoveReply(op.getElement()), appProtoId);
        }
    }


    /* ------------------------------------- Procedures --------------------------------------------- */

    private void processAddOperation(Operation op) {
        Set<UUID> adds  = state.get(op.getElement());
        if (adds == null)
            adds = new HashSet<>();

        adds.add(op.getAdd_ids().iterator().next());
        state.put(op.getElement(), adds);
    }

    private void processRemoveRequest(Operation op) {
        Set<UUID> adds  = state.get(op.getElement());
        if(adds != null) {
            adds.removeAll(op.getAdd_ids());
            if(adds.isEmpty())
                state.remove(op.getElement());
        }
    }

}
