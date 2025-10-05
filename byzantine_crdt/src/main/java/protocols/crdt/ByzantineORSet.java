package protocols.crdt;

import app.AutomatedApp;
import app.InteractiveApp;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import protocols.broadcast.crash.SignedReliableBcastProtocol;
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

        Operation op = new Operation(ADD_OP, req.getAdd_id(), req.getElement());
        BroadcastRequest bcast_req = new BroadcastRequest(mySelf, op.encode());
        sendRequest(bcast_req, SignedReliableBcastProtocol.PROTO_ID);
    }

    public void handleRemoveRequest(RemoveRequest req, short sourceProto) {
        logger.debug("Received Remove Operation: ({},{})", req.getAdd_id(), req.getElement());

        Operation op = new Operation(REMOVE_OP, req.getAdd_id(), req.getElement());
        BroadcastRequest bcast_req = new BroadcastRequest(mySelf, op.encode());
        sendRequest(bcast_req, SignedReliableBcastProtocol.PROTO_ID);
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
        if(op == null) return;

        if(op.getType().equals(ADD_OP))
            processAddOperation(op);

        else if(op.getType().equals(REMOVE_OP))
            processRemoveRequest(op);

        if(notification.getSender().equals(mySelf)) {
            if(op.getType().equals(ADD_OP)) {
                AddReply reply = new AddReply(mySelf, op.getAdd_id(), op.getElement());
                sendReply(reply, appProtoId);
            }

            else if(op.getType().equals(REMOVE_OP)) {
                RemoveReply reply = new RemoveReply(mySelf, op.getAdd_id(), op.getElement());
                sendReply(reply, appProtoId);
            }
        }
    }


    /* ------------------------------------- Procedures --------------------------------------------- */

    private void processAddOperation(Operation add) {
        Set<UUID> adds  = state.get(add.getElement());
        if (adds == null)
            adds = new HashSet<>();

        adds.add(add.getAdd_id());
        state.put(add.getElement(), adds);
    }

    private void processRemoveRequest(Operation remove) {
        Set<UUID> adds  = state.get(remove.getElement());
        if(adds != null) {
            adds.remove(remove.getAdd_id());
            if(adds.isEmpty())
                state.remove(remove.getElement());
        }
    }

}
