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

import java.util.HashSet;
import java.util.Properties;
import java.util.UUID;

/** @author Ricardo Bessa **/
public class CrashFaultReliableBroadcastProtocol extends GenericProtocol {

	public static final String PROTO_NAME = "BestEffortBroadcast";
	public static final short PROTO_ID = 301;

	private final HashSet<UUID> delivered;
	private final HashSet<Host> neighbors;
	private Host mySelf;


    private final Logger logger = LogManager.getLogger(CrashFaultReliableBroadcastProtocol.class);

	public CrashFaultReliableBroadcastProtocol() {
		super(PROTO_NAME, PROTO_ID);
		
		delivered = new HashSet<>();
		neighbors = new HashSet<>();
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
            BroadcastMessage bm = new BroadcastMessage(mySelf, req.encode());
            for (Host h : neighbors)
                sendMessage(bm, h);

            deliverMessage(bm, mySelf);

        } catch (Exception e) {
            e.printStackTrace();
        }
	}
	
	public void uponReceiveBroadcastMessage(BroadcastMessage msg, Host sender, short protoID, int channel) {
		if(deliverMessage(msg, sender)) {
			msg.setSender(mySelf);
            for(Host h: this.neighbors)
                sendMessage(msg, h);
		}
	}
	
	private boolean deliverMessage(BroadcastMessage msg, Host sender) {
        try {
            if (!this.delivered.contains(msg.getMessageID())) {
                this.delivered.add(msg.getMessageID());
                triggerNotification(DeliveryNotification.fromMessage(msg.getPayload()));
                return true;
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }
	
	public void uponNeighborUpNotification(NeighborUp notification, short sourceProtoID) {
        logger.debug("Received NeighborUp notification for: {}", notification.getNeighbor());
		this.neighbors.add(notification.getNeighbor());
	}
	
	public void uponNeighborDownNotification(NeighborDown notification, short sourceProtoID) {
        logger.debug("Received NeighborDown notification for: {}", notification.getNeighbor());
		this.neighbors.remove(notification.getNeighbor());
	}
}
