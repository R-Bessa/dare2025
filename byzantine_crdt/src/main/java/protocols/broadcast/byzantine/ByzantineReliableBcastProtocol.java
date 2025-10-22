package protocols.broadcast.byzantine;

import java.nio.charset.StandardCharsets;
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

    private final Map<UUID, Map<String, Set<EchoMessage>>> echos;
    private final Map<UUID, Boolean> sentReady;
    private final Map<UUID, Map<String, Set<ReadyMessage>>> readys;

	private Host mySelf;
	private PublicKey myPublicKey;
	private PrivateKey myPrivateKey;


    //TODO introduce causality and protect with hash graph
    //TODO app based filter for trust levels? how to handle state transfer?
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
			SignedBroadcastMessage bm = new SignedBroadcastMessage(mySelf, mySelf, req.encode(), originalSenderSig, null);
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

        String payload = new String(echo.getPayload(), StandardCharsets.UTF_8);
        Map<String, Set<EchoMessage>> echos_per_payload = echos.computeIfAbsent(echo.getMessageID(), mid -> new HashMap<>());

        Set<EchoMessage> my_echos = echos_per_payload.computeIfAbsent(payload, m -> new HashSet<>());
        my_echos.add(echo);

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

            Map<String, Set<ReadyMessage>> my_readys = readys.computeIfAbsent(ready.getMessageID(), mid -> new HashMap<>());
            my_readys.computeIfAbsent(payload, m -> new HashSet<>()).add(ready);

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

        Map<String, Set<ReadyMessage>> readys_per_payload = readys.computeIfAbsent(ready.getMessageID(), mid -> new HashMap<>());
        String payload = new String(ready.getPayload(), StandardCharsets.UTF_8);

        Set<ReadyMessage> my_readys = readys_per_payload.computeIfAbsent(payload, m -> new HashSet<>());
        my_readys.add(ready);

        if (!sentReady.getOrDefault(ready.getMessageID(), false) && my_readys.size() > this.f) {
            sentReady.put(ready.getMessageID(), true);
            ReadyMessage my_ready = new ReadyMessage(mySelf, ready.getMessageID(), ready.getPayload());

            try {
                my_ready.signMessage(myPrivateKey);

            } catch (Exception e) {
                logger.error("Could not sign my ready message.");
                return;
            }

            my_readys.add(ready);

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

        String payload = new String(echo.getPayload(), StandardCharsets.UTF_8);
        Map<String, Set<EchoMessage>> my_echos = echos.computeIfAbsent(echo.getMessageID(), mid -> new HashMap<>());
        my_echos.computeIfAbsent(payload, m -> new HashSet<>()).add(echo);

        for(Host h: this.neighbors)
            sendMessage(echo, h);
    }

}
