package protocols.broadcast.crash;

import java.security.*;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Properties;
import java.util.UUID;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import protocols.broadcast.notifications.DeliveryNotification;
import protocols.broadcast.request.BroadcastRequest;
import protocols.broadcast.messages.SignedBroadcastMessage;
import protocols.events.SecureChannelAvailable;
import protocols.events.NeighborDown;
import protocols.events.SecureNeighborUp;
import pt.unl.fct.di.novasys.babel.core.GenericProtocol;
import pt.unl.fct.di.novasys.babel.exceptions.HandlerRegistrationException;
import pt.unl.fct.di.novasys.network.data.Host;
import utils.SignaturesHelper;

public class SignedReliableBcastProtocol extends GenericProtocol {
    private final Logger logger = LogManager.getLogger(SignedReliableBcastProtocol.class);

	public static final String PROTO_NAME = "SignedBestEffortBroadcast";
	public static final short PROTO_ID = 301;
	
	private final HashSet<UUID> delivered;
	private final HashSet<Host> neighbors;
	private final HashMap<Host, PublicKey> publicKeys;
	
	private Host mySelf;
	private PublicKey myPublicKey;
	private PrivateKey myPrivateKey;

	
	public SignedReliableBcastProtocol() {
		super(PROTO_NAME, PROTO_ID);
		
		delivered = new HashSet<>();
		neighbors = new HashSet<>();
		publicKeys = new HashMap<>();
		
		this.mySelf = null;
		this.myPublicKey = null;
		this.myPrivateKey = null;
	}

	@Override
	public void init(Properties props) throws HandlerRegistrationException {

        /* ------------------------------- Subscribe Notifications ------------------------------------------- */
		subscribeNotification(SecureChannelAvailable.NOTIFICATION_ID, this::handleChannelAvailableNotification);
		subscribeNotification(SecureNeighborUp.NOTIFICATION_ID, this::uponNeighborUpNotification);
		subscribeNotification(NeighborDown.NOTIFICATION_ID, this::uponNeighborDownNotification);

        /* ------------------------------- Register Request Handlers ---------------------------------------- */
		registerRequestHandler(BroadcastRequest.REQUEST_ID, this::handleBroadcastRequest);
	}


    /* -------------------------------------- Notification Handlers ----------------------------------------- */

    public void handleChannelAvailableNotification(SecureChannelAvailable notification, short sourceProto) {
		this.mySelf = notification.getMyHost();
		this.myPublicKey = notification.getMyPublicKey();
		this.myPrivateKey = notification.getMyPrivateKey();
				
		this.publicKeys.put(mySelf, myPublicKey);

        int channelID = notification.getChannelID();
		
		registerSharedChannel(channelID);
		setDefaultChannel(channelID);

        /* ------------------------------ Register Message Serializers ------------------------------ */
		registerMessageSerializer(channelID, SignedBroadcastMessage.MESSAGE_ID, SignedBroadcastMessage.serializer);

        /* ------------------------------ Register Message Handlers -------------------------------- */
		try {
			registerMessageHandler(channelID, SignedBroadcastMessage.MESSAGE_ID, this::uponReceiveBroadcastMessage);
		} catch (HandlerRegistrationException e) {
			e.printStackTrace();
		}
	}


    public void uponNeighborUpNotification(SecureNeighborUp notification, short sourceProtoID) {
        logger.debug("Received NeighborUp notification for: {}", notification.getNeighbor());

        this.neighbors.add(notification.getNeighbor());
        this.publicKeys.put(notification.getNeighbor(), notification.getPublicKey());
    }


    public void uponNeighborDownNotification(NeighborDown notification, short sourceProtoID) {
        logger.debug("Received NeighborDown notification for: {}", notification.getNeighbor());

        this.neighbors.remove(notification.getNeighbor());
    }


    /* ------------------------------------- Request Handlers ------------------------------------- */

	public void handleBroadcastRequest(BroadcastRequest req, short sourceProto) {
		
		try {
            byte[] originalSenderSig = SignaturesHelper.generateSignature(req.encode(), myPrivateKey);
			SignedBroadcastMessage bm = new SignedBroadcastMessage(mySelf, req.encode(), originalSenderSig);
			bm.signMessage(myPrivateKey);

			for(Host h: neighbors)
				sendMessage(bm, h);

			deliverMessage(bm, mySelf);
			
		} catch (Exception e) {
			e.printStackTrace();
		}
	}


    /* ------------------------------------- Message Handlers ------------------------------------- */

	public void uponReceiveBroadcastMessage(SignedBroadcastMessage msg, Host sender, short protoID, int channel) {
		if(deliverMessage(msg, sender)) {
			try {
				msg.signMessage(myPrivateKey);

				for(Host h: this.neighbors)
					sendMessage(msg, h);

			} catch (Exception e) {
				logger.error("Could not sign the message for retransmission.");
				e.printStackTrace();
			}
		}
	}


    /* ------------------------------------- Procedures ----------------------------------------- */

	private boolean deliverMessage(SignedBroadcastMessage msg, Host sender) {
        try {
            if(!this.delivered.contains(msg.getMessageID())) {
                if (sender.equals(mySelf)) {
                    this.delivered.add(msg.getMessageID());
                    triggerNotification(DeliveryNotification.fromMessage(msg.getPayload()));
                    return true;
                }

                if (!publicKeys.containsKey(sender) || !publicKeys.containsKey(msg.getOriginalSender()))
                    return false;

                if(msg.checkSignature(publicKeys.get(sender)) && msg.verifyOriginalSignature(publicKeys.get(msg.getOriginalSender()))) {
                    this.delivered.add(msg.getMessageID());
                    triggerNotification(DeliveryNotification.fromMessage(msg.getPayload()));
                    return true;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return false;
	}

}
