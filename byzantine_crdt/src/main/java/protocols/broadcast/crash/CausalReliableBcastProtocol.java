package protocols.broadcast.crash;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import protocols.broadcast.notifications.DeliveryNotification;
import protocols.broadcast.request.BroadcastRequest;
import protocols.broadcast.messages.BroadcastMessage;
import protocols.events.ChannelAvailable;
import protocols.events.NeighborDown;
import protocols.events.NeighborUp;
import pt.unl.fct.di.novasys.babel.core.GenericProtocol;
import pt.unl.fct.di.novasys.babel.exceptions.HandlerRegistrationException;
import pt.unl.fct.di.novasys.network.data.Host;

import java.util.*;

public class CausalReliableBcastProtocol extends GenericProtocol {
    private final Logger logger = LogManager.getLogger(CausalReliableBcastProtocol.class);

	public static final String PROTO_NAME = "BestEffortCausalBroadcast";
	public static final short PROTO_ID = 300;

	private final HashSet<Host> neighbors;
    private final Map<Host, Integer> version_vector;

    private final HashSet<UUID> delivered;
    private final HashSet<BroadcastMessage> pending;
	private Host mySelf;

    //TODO test causality
    //TODO byzantine_rep flag, test/screenshot equivocation, causality attack (send wrong vv) and impersonation
    //TODO remove authors, improve crdt automatic remove


	public CausalReliableBcastProtocol() {
		super(PROTO_NAME, PROTO_ID);

        neighbors = new HashSet<>();
        version_vector = new HashMap<>();

        delivered = new HashSet<>();
        pending = new HashSet<>();

		this.mySelf = null;
	}

	@Override
	public void init(Properties props) throws HandlerRegistrationException {

        /* ------------------------------- Subscribe Notifications ----------------------------------- */
        subscribeNotification(ChannelAvailable.NOTIFICATION_ID, this::handleChannelAvailableNotification);
		subscribeNotification(NeighborUp.NOTIFICATION_ID, this::uponNeighborUpNotification);
		subscribeNotification(NeighborDown.NOTIFICATION_ID, this::uponNeighborDownNotification);

        /* ------------------------------- Register Request Handlers --------------------------------- */
        registerRequestHandler(BroadcastRequest.REQUEST_ID, this::handleBroadcastRequest);
	}


    /* ------------------------------- Notification Handlers ----------------------------------------- */

	public void handleChannelAvailableNotification(ChannelAvailable notification, short sourceProto) {
		this.mySelf = notification.getMyHost();
        this.version_vector.put(mySelf, 0);
        int channelID = notification.getChannelID();
		
		registerSharedChannel(channelID);
		setDefaultChannel(channelID);

        /* ------------------------------ Register Message Serializers ------------------------------ */
        registerMessageSerializer(channelID, BroadcastMessage.MESSAGE_ID, BroadcastMessage.serializer);

        /* ------------------------------ Register Message Handlers -------------------------------- */
		try {
			registerMessageHandler(channelID, BroadcastMessage.MESSAGE_ID, this::uponReceiveBroadcastMessage);
		} catch (HandlerRegistrationException e) {
			e.printStackTrace();
		}
	}


    public void uponNeighborUpNotification(NeighborUp notification, short sourceProtoID) {
        logger.debug("Received NeighborUp notification for: {}", notification.getNeighbor());

        this.neighbors.add(notification.getNeighbor());
        this.version_vector.put(notification.getNeighbor(), 0);
    }


    public void uponNeighborDownNotification(NeighborDown notification, short sourceProtoID) {
        logger.debug("Received NeighborDown notification for: {}", notification.getNeighbor());

        this.neighbors.remove(notification.getNeighbor());
        this.version_vector.remove(notification.getNeighbor());
    }


    /* ------------------------------------- Request Handlers ------------------------------------- */

	public void handleBroadcastRequest(BroadcastRequest req, short sourceProto) {
        try {
            int curr = version_vector.getOrDefault(mySelf, 0);
            version_vector.put(mySelf, curr + 1);

            BroadcastMessage bm = new BroadcastMessage(mySelf, req.encode(), version_vector);
            for (Host h : neighbors)
                sendMessage(bm, h);

            deliverMessage(bm, mySelf);

        } catch (Exception e) {
            e.printStackTrace();
        }
	}


    /* ------------------------------------- Message Handlers ------------------------------------- */

	public void uponReceiveBroadcastMessage(BroadcastMessage msg, Host sender, short protoID, int channel) {
		if(deliverMessage(msg, msg.getSender())) {
            for(Host h: this.neighbors)
                sendMessage(msg, h);
		}
	}


    /* ------------------------------------- Procedures ----------------------------------------- */

	private boolean deliverMessage(BroadcastMessage msg, Host sender) {
        try {
            if (!this.delivered.contains(msg.getMessageID())) {
                if(msg.getSender().equals(sender)) {
                    this.delivered.add(msg.getMessageID());
                    triggerNotification(DeliveryNotification.fromMessage(msg.getPayload()));
                    return true;
                }

                if(verifyCausality(msg.getSender(), msg.getVersion_vector())) {
                    this.delivered.add(msg.getMessageID());
                    int curr = version_vector.getOrDefault(msg.getSender(), 0);
                    version_vector.put(msg.getSender(), curr + 1);
                    triggerNotification(DeliveryNotification.fromMessage(msg.getPayload()));
                    processPendingMessages();
                    return true;
                }

                pending.add(msg);
                return false;
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }


    private void processPendingMessages() {
        try {
            Set<BroadcastMessage> toRemove = new HashSet<>();
            for (BroadcastMessage msg : pending) {
                if (verifyCausality(msg.getSender(), msg.getVersion_vector())) {
                    toRemove.add(msg);
                    this.delivered.add(msg.getMessageID());
                    int curr = version_vector.getOrDefault(msg.getSender(), 0);
                    version_vector.put(msg.getSender(), curr + 1);
                    triggerNotification(DeliveryNotification.fromMessage(msg.getPayload()));
                }
            }
            pending.removeAll(toRemove);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    private boolean verifyCausality(Host sender, Map<Host, Integer> vv) {
        for (Map.Entry<Host, Integer> entry : vv.entrySet()) {
            Host h = entry.getKey();
            int received_version = entry.getValue();
            int local_version = this.version_vector.getOrDefault(h, 0);

            if ((h.equals(sender) && received_version != local_version + 1))
                return false;

            if (!h.equals(sender) && received_version > local_version)
                return false;
        }

        return true;
    }

}
