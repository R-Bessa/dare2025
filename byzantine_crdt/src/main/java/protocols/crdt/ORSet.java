package protocols.crdt;

import app.AutomatedApp;
import app.InteractiveApp;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import protocols.broadcast.crash.CausalReliableBcastProtocol;
import protocols.broadcast.notifications.DeliveryNotification;
import protocols.broadcast.request.BroadcastRequest;
import protocols.events.ChannelAvailable;
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
import java.util.concurrent.ThreadLocalRandom;

public class ORSet extends GenericProtocol {
    private final Logger logger = LogManager.getLogger(ORSet.class);

    public static final String PROTO_NAME = "Observed-Remove Set CRDT";
    public static final short PROTO_ID = 500;

    public static final String ADD_OP = "add";
    public static final String REMOVE_OP = "remove";

    public static final String APP_MODE = "app_interaction";

    private final Map<String, Set<UUID>> state;
    private final Map<String, Double> latencies;
    public static List<String> latency_records;
    private Host mySelf;
    private short appProtoId;


    public ORSet() {
        super(PROTO_NAME, PROTO_ID);
        this.state = new HashMap<>();
        this.latencies = new HashMap<>();
        latency_records  = new ArrayList<>();
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
        subscribeNotification(ChannelAvailable.NOTIFICATION_ID, this::uponChannelAvailable);
    }



    /* ---------------------------------- Request Handlers ------------------------------------------- */

    public void handleAddRequest(AddRequest req, short sourceProto) {
        logger.debug("Received Add Operation: ({},{})", req.getAdd_id(), req.getElement());

        Operation op = new Operation(ADD_OP, Set.of(req.getAdd_id()), req.getElement());
        processAddOperation(op);
        sendReply(new AddReply(op.getElement()), appProtoId);

        sendRequest(new BroadcastRequest(mySelf, op.encode()), CausalReliableBcastProtocol.PROTO_ID);
        latencies.put(req.getAdd_id().toString(), System.nanoTime() / 1_000_000.0);
    }

    public void handleRemoveRequest(RemoveRequest req, short sourceProto) {
        logger.debug("Received Remove Operation: ({})", req.getElement());

        Set<UUID> observed_adds = state.get(req.getElement());
        if(observed_adds == null)
            sendReply(new RemoveReply(req.getElement()), appProtoId);

        else {
            Operation op = new Operation(REMOVE_OP, Set.copyOf(observed_adds), req.getElement());
            processRemoveOperation(op);
            sendReply(new RemoveReply(req.getElement()), appProtoId);

            sendRequest(new BroadcastRequest(mySelf, op.encode()), CausalReliableBcastProtocol.PROTO_ID);
            latencies.put(op.getElement(), System.nanoTime() / 1_000_000.0);
        }
    }

    public void handleReadRequest(ReadRequest req, short sourceProto) {
        logger.debug("Received Read Operation");

        ReadReply reply = new ReadReply(mySelf, state.keySet());
        sendReply(reply, appProtoId);
    }



    /* ----------------------------------- Notification Handlers ----------------------------------- */

    public void uponChannelAvailable(ChannelAvailable notification, short sourceProto) {
        this.mySelf = notification.getMyHost();
    }

    private void uponDeliver(DeliveryNotification notification, short sourceProto) {
        double endTime = System.nanoTime() / 1_000_000.0;
        Operation op = Operation.decode(notification.getPayload());

        if(notification.getSender().equals(mySelf)) {
            String k = op.getType().equals(REMOVE_OP) ? op.getElement() :
                    op.getAdd_ids().iterator().next().toString();

            double latency = (endTime - latencies.remove(k)) + getLatencyPenalty();
            latency_records.add(endTime + " " + latency); //ms
            return;
        }

        if (op.getType().equals(ADD_OP))
            processAddOperation(op);

        else if (op.getType().equals(REMOVE_OP))
            processRemoveOperation(op);
    }


    /* ------------------------------------- Procedures --------------------------------------------- */

    private void processAddOperation(Operation op) {
        Set<UUID> adds  = state.get(op.getElement());
        if (adds == null)
            adds = new HashSet<>();

        adds.add(op.getAdd_ids().iterator().next());
        state.put(op.getElement(), adds);
    }

    private void processRemoveOperation(Operation op) {
        Set<UUID> adds  = state.get(op.getElement());
        if(adds != null) {
            adds.removeAll(op.getAdd_ids());
            if(adds.isEmpty())
                state.remove(op.getElement());
        }
    }

    public static int getLatencyPenalty() {
        double pFast = 0.7; // 70% are fast
        if (ThreadLocalRandom.current().nextDouble() < pFast) {
            // fast: gaussian around 20ms
            return gaussianMs(20, 6, 5, 60);
        } else {
            // slow: gaussian around 150ms
            return gaussianMs(150, 50, 50, 500);
        }
    }

    public static int gaussianMs(double mean, double sigma, int min, int max) {
        double v = ThreadLocalRandom.current().nextGaussian() * sigma + mean;
        long r = Math.round(Math.max(min, Math.min(max, v)));
        return (int) r;
    }


}
