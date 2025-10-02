package protocols.broadcast.crashreliablebcast;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import protocols.broadcast.notification.DeliveryNotification;
import protocols.broadcast.request.BroadcastRequest;
import protocols.broadcast.request.message.BroadcastMessage;
import protocols.common.events.*;
import pt.unl.fct.di.novasys.babel.core.GenericProtocol;
import pt.unl.fct.di.novasys.babel.exceptions.HandlerRegistrationException;
import pt.unl.fct.di.novasys.network.data.Host;

import java.util.*;

/** @author Ricardo Bessa **/
public class CrashReliableCausalBroadcastProtocol extends GenericProtocol {

	public static final String PROTO_NAME = "BestEffortCausalBroadcast";
	public static final short PROTO_ID = 301;

	private final HashSet<UUID> delivered;
	private final HashSet<Host> neighbors;
    private final Map<Host, Integer> version_vector;
    private final HashSet<BroadcastMessage> pending_msgs;
	private Host mySelf;


    private final Logger logger = LogManager.getLogger(CrashReliableCausalBroadcastProtocol.class);

	public CrashReliableCausalBroadcastProtocol() {
		super(PROTO_NAME, PROTO_ID);
		
		delivered = new HashSet<>();
		neighbors = new HashSet<>();
        pending_msgs = new HashSet<>();
        version_vector = new HashMap<>();
		this.mySelf = null;
	}

	@Override
	public void init(Properties props) throws HandlerRegistrationException {
		
		subscribeNotification(ChannelAvailable.NOTIFICATION_ID, this::handleChannelAvailableNotification);
		subscribeNotification(NeighborUp.NOTIFICATION_ID, this::uponNeighborUpNotification);
		subscribeNotification(NeighborDown.NOTIFICATION_ID, this::uponNeighborDownNotification);
		
		registerRequestHandler(BroadcastRequest.REQUEST_ID, this::handleBroadcastRequest);
	}
	
	public void handleChannelAvailableNotification(ChannelAvailable notification, short sourceProto) {
		this.mySelf = notification.getMyHost();
        int channelID = notification.getChannelID();
		
		registerSharedChannel(channelID);
		setDefaultChannel(channelID);
		
		//Register Message Serializers
		registerMessageSerializer(channelID, BroadcastMessage.MESSAGE_ID, BroadcastMessage.serializer);
		
		//Setup Message Handlers
		try {
			registerMessageHandler(channelID, BroadcastMessage.MESSAGE_ID, this::uponReceiveBroadcastMessage);
		} catch (HandlerRegistrationException e) {
			//This should never happen
			e.printStackTrace();
		}
	}


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


	public void uponReceiveBroadcastMessage(BroadcastMessage msg, Host sender, short protoID, int channel) {
		if(deliverMessage(msg, msg.getSender())) {
            for(Host h: this.neighbors)
                sendMessage(msg, h);
		}
	}


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

                } else {
                    pending_msgs.add(msg);
                    return false;
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }


    private void processPendingMessages() {
        try {
            Set<BroadcastMessage> toRemove = new HashSet<>();
            for (BroadcastMessage msg : pending_msgs) {
                if (verifyCausality(msg.getSender(), msg.getVersion_vector())) {
                    toRemove.add(msg);
                    this.delivered.add(msg.getMessageID());
                    int curr = version_vector.getOrDefault(msg.getSender(), 0);
                    version_vector.put(msg.getSender(), curr + 1);
                    triggerNotification(DeliveryNotification.fromMessage(msg.getPayload()));
                }
            }
            pending_msgs.removeAll(toRemove);

        } catch (Exception e) {e.printStackTrace();}
    }

    private boolean verifyCausality(Host sender, Map<Host, Integer> vv) {
        for (Map.Entry<Host, Integer> entry : vv.entrySet()) {
            Host h = entry.getKey();
            int received_version = entry.getValue();
            int local_version = this.version_vector.getOrDefault(h, 0);

            if (h.equals(sender)) {
                if (received_version != local_version + 1) {
                    return false;
                }
            } else {
                if (received_version > local_version) {
                    return false;
                }
            }
        }
        return true;
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
}
