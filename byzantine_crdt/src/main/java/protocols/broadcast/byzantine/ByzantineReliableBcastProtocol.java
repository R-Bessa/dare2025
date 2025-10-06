package protocols.broadcast.byzantine;

import java.security.*;
import java.util.*;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import protocols.broadcast.byzantine.messages.EchoMessage;
import protocols.broadcast.byzantine.messages.ReadyMessage;
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

public class ByzantineReliableBcastProtocol extends GenericProtocol {
    private final Logger logger = LogManager.getLogger(ByzantineReliableBcastProtocol.class);

	public static final String PROTO_NAME = "ByzantineReliableBroadcast";
	public static final short PROTO_ID = 301;

    private int f;
	
	private final Set<UUID> delivered;
	private final Set<Host> neighbors;
	private final Map<Host, PublicKey> publicKeys;

    private final Map<UUID, Set<EchoMessage>> echos;
    private final Map<UUID, Boolean> sentReady;
    private final Map<UUID, Set<ReadyMessage>> readys;

	private Host mySelf;
	private PublicKey myPublicKey;
	private PrivateKey myPrivateKey;


	public ByzantineReliableBcastProtocol() {
		super(PROTO_NAME, PROTO_ID);
		
		this.delivered = new HashSet<>();
		this.neighbors = new HashSet<>();
		this.publicKeys = new HashMap<>();

        this.echos = new HashMap<>();
        this.sentReady = new HashMap<>();
        this.readys = new HashMap<>();
		
		this.mySelf = null;
		this.myPublicKey = null;
		this.myPrivateKey = null;
	}

	@Override
	public void init(Properties props) throws HandlerRegistrationException {

        this.f = Integer.parseInt(props.getProperty("f"));

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
        registerMessageSerializer(channelID, EchoMessage.MESSAGE_ID, EchoMessage.serializer);
        registerMessageSerializer(channelID, ReadyMessage.MESSAGE_ID, ReadyMessage.serializer);

        /* ------------------------------ Register Message Handlers -------------------------------- */
		try {
			registerMessageHandler(channelID, SignedBroadcastMessage.MESSAGE_ID, this::uponReceiveBroadcastMessage);
            registerMessageHandler(channelID, EchoMessage.MESSAGE_ID, this::uponEchoMessage);
            registerMessageHandler(channelID, ReadyMessage.MESSAGE_ID, this::uponReadyMessage);
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

            processBroadcastMessage(bm, mySelf);
			
		} catch (Exception e) {
			e.printStackTrace();
		}
	}


    /* ------------------------------------- Message Handlers ------------------------------------- */

	public void uponReceiveBroadcastMessage(SignedBroadcastMessage msg, Host sender, short protoID, int channel) {
        processBroadcastMessage(msg, sender);
	}


    public void uponEchoMessage(EchoMessage echo, Host sender, short protoID, int channel) {
        try {
            if (!publicKeys.containsKey(sender) || !echo.checkSignature(publicKeys.get(sender)))
                return;

        } catch (Exception e) {
            logger.error("Could not verify the signature from sender.");
            e.printStackTrace();
        }

        echos.computeIfAbsent(echo.getMessageID(), msg_id -> new HashSet<>()).add(echo);
        int echos_threshold = (int) Math.ceil((neighbors.size() + 1 + f) / 2.0);

        if(!sentReady.getOrDefault(echo.getMessageID(), false)
                && echos.get(echo.getMessageID()).size() >= echos_threshold) {

            try {
                if (!publicKeys.containsKey(echo.getOriginalSender()) || !echo.verifyOriginalSignature(publicKeys.get(echo.getOriginalSender()))) {
                    return;
                }

            } catch (Exception e) {
                logger.error("Could not verify the signature from original sender.");
                e.printStackTrace();
            }

            sentReady.put(echo.getMessageID(), true);
            ReadyMessage ready = new ReadyMessage(mySelf, echo.getMessageID(), echo.getPayload());
            readys.computeIfAbsent(ready.getMessageID(), msg_id -> new HashSet<>()).add(ready);

            try {
                ready.signMessage(myPrivateKey);

                for(Host h: this.neighbors)
                    sendMessage(ready, h);

            } catch (Exception e) {
                logger.error("Could not sign my ready message.");
                e.printStackTrace();
            }

        }
    }


    public void uponReadyMessage(ReadyMessage ready, Host sender, short protoID, int channel) {
        try {
            if (!publicKeys.containsKey(sender) || !ready.checkSignature(publicKeys.get(sender)))
                return;

        } catch (Exception e) {
            logger.error("Could not verify the signature from sender.");
            e.printStackTrace();
        }

        readys.computeIfAbsent(ready.getMessageID(), msg_id -> new HashSet<>()).add(ready);

        if (!sentReady.getOrDefault(ready.getMessageID(), false)
                && readys.get(ready.getMessageID()).size() > this.f) {

            sentReady.put(ready.getMessageID(), true);
            ReadyMessage my_ready = new ReadyMessage(mySelf, ready.getMessageID(), ready.getPayload());
            readys.get(my_ready.getMessageID()).add(my_ready);

            try {
                my_ready.signMessage(myPrivateKey);

                for (Host h : this.neighbors)
                    sendMessage(my_ready, h);

            } catch (Exception e) {
                logger.error("Could not sign my ready message.");
                e.printStackTrace();
            }

        }

        if (!delivered.contains(ready.getMessageID()) && readys.get(ready.getMessageID()).size() > 2 * this.f) {
            try {
                this.delivered.add(ready.getMessageID());
                triggerNotification(DeliveryNotification.fromMessage(ready.getPayload()));

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }


    /* ------------------------------------- Procedures ------------------------------------- */

    private void processBroadcastMessage(SignedBroadcastMessage msg, Host sender) {
        try {
            if (!sender.equals(mySelf)) {
                if(!publicKeys.containsKey(sender) || !msg.checkSignature(publicKeys.get(sender)))
                    return;
            }

        } catch (Exception e) {
            logger.error("Could not verify the signature from sender.");
            e.printStackTrace();
        }

        EchoMessage echo = new EchoMessage(msg.getOriginalSender(), mySelf, msg.getMessageID(), msg.getPayload(), msg.getOriginalSignature());
        echos.computeIfAbsent(msg.getMessageID(), msg_id -> new HashSet<>()).add(echo);

        try {
            echo.signMessage(myPrivateKey);

            for(Host h: this.neighbors)
                sendMessage(echo, h);

        } catch (Exception e) {
            logger.error("Could not sign my echo message.");
            e.printStackTrace();
        }

    }

}
