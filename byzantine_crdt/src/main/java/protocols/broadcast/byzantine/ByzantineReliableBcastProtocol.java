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


    //TODO better check equivocation ids in other steps and original sender
    //TODO introduce causality and protect with hash graph
    //TODO app based filter for trust levels? how to handle state transfer?
    // String str = new String(bytes, StandardCharsets.UTF_8);
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

            processBroadcastMessage(bm, mySelf);

            for(Host h: neighbors)
                sendMessage(bm, h);

        } catch (Exception e) {
            logger.error("Failed to generate signatures for the broadcast message.");
            e.printStackTrace();
        }
	}


    /* ------------------------------------- Message Handlers ------------------------------------- */

	public void uponReceiveBroadcastMessage(SignedBroadcastMessage msg, Host sender, short protoID, int channel) {
        processBroadcastMessage(msg, sender);
	}


    public void uponEchoMessage(EchoMessage echo, Host sender, short protoID, int channel) {
        try {
            if (!echo.checkSignature(publicKeys.get(sender))) {
                logger.error("Invalid signature from the sender.");
                return;
            }

        } catch (Exception e) {
            logger.error("Could not verify the signature from the sender.");
            return;
        }

        Set<EchoMessage> my_echos = echos.getOrDefault(echo.getMessageID(), new HashSet<>());
        if(!my_echos.isEmpty()) {
            EchoMessage prev_echo = my_echos.iterator().next();
            if (!Arrays.equals(echo.getPayload(), prev_echo.getPayload())) {
                logger.error("Equivocation for msg id {}", echo.getMessageID());
                return;
            }
        }

        my_echos.add(echo);
        echos.put(echo.getMessageID(), my_echos);

        int echos_threshold = (int) Math.ceil((neighbors.size() + 1 + f) / 2.0);

        if(!sentReady.getOrDefault(echo.getMessageID(), false) && my_echos.size() >= echos_threshold) {

            try {
                if(!echo.verifyOriginalSignature(publicKeys.get(echo.getOriginalSender()))) {
                    logger.error("Invalid signature from the original sender.");
                    return;
                }

            } catch (Exception e) {
                logger.error("Could not verify the signature from the original sender.");
                return;
            }

            sentReady.put(echo.getMessageID(), true);
            ReadyMessage ready = new ReadyMessage(mySelf, echo.getMessageID(), echo.getPayload());

            try {
                ready.signMessage(myPrivateKey);

            } catch (Exception e) {
                logger.error("Could not sign my ready message.");
                return;
            }

            Set<ReadyMessage> my_readys = readys.getOrDefault(echo.getMessageID(), new HashSet<>());
            my_readys.add(ready);
            readys.put(echo.getMessageID(), my_readys);

            for(Host h: this.neighbors)
                sendMessage(ready, h);
        }
    }


    public void uponReadyMessage(ReadyMessage ready, Host sender, short protoID, int channel) {
        try {
            if (!ready.checkSignature(publicKeys.get(sender))) {
                logger.error("Invalid signature from the sender.");
                return;
            }

        } catch (Exception e) {
            logger.error("Could not verify the signature from sender.");
            return;
        }

        Set<ReadyMessage> my_readys = readys.getOrDefault(ready.getMessageID(), new HashSet<>());
        if(!my_readys.isEmpty()) {
            ReadyMessage prev_ready = my_readys.iterator().next();
            if (!Arrays.equals(ready.getPayload(), prev_ready.getPayload())) {
                logger.debug("Equivocation for msg id {}", ready.getMessageID());
                return;
            }
        }

        my_readys.add(ready);
        readys.put(ready.getMessageID(), my_readys);

        if (!sentReady.getOrDefault(ready.getMessageID(), false) && my_readys.size() > this.f) {
            sentReady.put(ready.getMessageID(), true);
            ReadyMessage my_ready = new ReadyMessage(mySelf, ready.getMessageID(), ready.getPayload());

            try {
                my_ready.signMessage(myPrivateKey);

            } catch (Exception e) {
                logger.error("Could not sign my ready message.");
                return;
            }

            readys.get(my_ready.getMessageID()).add(my_ready);

            for (Host h : this.neighbors)
                sendMessage(my_ready, h);
        }

        if (!delivered.contains(ready.getMessageID()) && my_readys.size() > 2 * this.f) {
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
            if (!sender.equals(mySelf) && !msg.checkSignature(publicKeys.get(sender))) {
                logger.error("Invalid signature from the sender.");
                return;
            }

        } catch (Exception e) {
            logger.error("Could not verify the signature from the sender.");
            return;
        }

        EchoMessage echo = new EchoMessage(msg.getOriginalSender(), mySelf, msg.getMessageID(), msg.getPayload(), msg.getOriginalSignature());

        try {
            echo.signMessage(myPrivateKey);

        } catch (Exception e) {
            logger.error("Could not sign my echo message.");
            return;
        }

        Set<EchoMessage> my_echos = echos.getOrDefault(echo.getMessageID(), new HashSet<>());
        my_echos.add(echo);
        echos.put(echo.getMessageID(), my_echos);

        for(Host h: this.neighbors)
            sendMessage(echo, h);
    }

}
