package app.dissemination;

import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.HashMap;
import java.util.Properties;
import java.util.Random;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import app.dissemination.timers.DisseminationTimer;
import app.dissemination.timers.ExitTimer;
import app.dissemination.timers.StartTimer;
import app.dissemination.timers.StopTimer;
import protocols.broadcast.crashreliablebcast.SecureCrashFaultReliableBroadcastProtocol;
import protocols.broadcast.notification.DeliveryNotification;
import protocols.broadcast.request.BroadcastRequest;
import protocols.common.events.ChannelAvailable;
import protocols.common.events.NeighborUp;
import pt.unl.fct.di.novasys.babel.core.GenericProtocol;
import pt.unl.fct.di.novasys.babel.exceptions.HandlerRegistrationException;
import pt.unl.fct.di.novasys.network.data.Host;
import utils.SignaturesHelper;
/** @author Professor Joao Leitao (from Reliable Distributed Systems 2025 course) **/
public class AutomatedDisseminationApp extends GenericProtocol {
    private static final Logger logger = LogManager.getLogger(AutomatedDisseminationApp.class);

    //Protocol debug();rmation, to register in babel
    public static final String PROTO_NAME = "AutomatedApp";
    public static final short PROTO_ID = 400;

    //Size of the payload of each message (in bytes)
    private int payloadSize;
    //Time to wait until starting sending messages
    private int prepareTime;
    //Time to run before shutting down
    private int runTime;
    //Time to wait until starting sending messages
    private int cooldownTime;
    //Interval between each broadcast
    private int disseminationInterval;
    
    private Host self;

    private long broadCastTimer;
    
    private final int messageIndex;
    private final HashMap<Host, PublicKey> publicKeys;
    private PrivateKey myPrivateKey;

    public AutomatedDisseminationApp() {
        super(PROTO_NAME, PROTO_ID);
        messageIndex = 0;
        this.publicKeys = new HashMap<>();
    }

    @Override
    public void init(Properties props) throws HandlerRegistrationException {
    	//Read configurations
        this.payloadSize = Integer.parseInt(props.getProperty("payload_size"));
        this.prepareTime = Integer.parseInt(props.getProperty("prepare_time")); //in seconds
        this.cooldownTime = Integer.parseInt(props.getProperty("cooldown_time")); //in seconds
        this.runTime = Integer.parseInt(props.getProperty("run_time")); //in seconds
        this.disseminationInterval = Integer.parseInt(props.getProperty("broadcast_interval")); //in milliseconds

         
        //Setup handlers
        subscribeNotification(DeliveryNotification.NOTIFICATION_ID, this::uponDeliver);
        
        subscribeNotification(ChannelAvailable.NOTIFICATION_ID, this::uponChannelAvailable);
        subscribeNotification(NeighborUp.NOTIFICATION_ID, this::uponNeighborUp);
        
        registerTimerHandler(DisseminationTimer.TIMER_ID, this::uponBroadcastTimer);
        registerTimerHandler(StartTimer.TIMER_ID, this::uponStartTimer);
        registerTimerHandler(StopTimer.TIMER_ID, this::uponStopTimer);
        registerTimerHandler(ExitTimer.TIMER_ID, this::uponExitTimer);
    	
    	
        //Wait prepareTime seconds before starting
        logger.debug("Waiting...");
    }

    public void uponChannelAvailable(ChannelAvailable notification, short protoSource) {
    	this.self = notification.getMyHost();
    	this.myPrivateKey = notification.getMyPrivateKey();
    	this.publicKeys.put(self,notification.getMyPublicKey());

        logger.debug("Communication Channel is ready... starting wait time to start broadcasting ({}s)", prepareTime);
    	setupTimer(new StartTimer(), prepareTime * 1000L);
    }
    
    public void uponNeighborUp(NeighborUp notification, short protoSource) {
        logger.debug("Received NeighborUp notification for: {}", notification.getNeighbor());
    	this.publicKeys.put(notification.getNeighbor(), notification.getPublicKey());
    }
    
    private void uponStartTimer(StartTimer startTimer, long timerId) {
        logger.debug("Starting Broadcasting Messages... (every {}s)", disseminationInterval / 1000);
        //Start broadcasting periodically
        broadCastTimer = setupPeriodicTimer(new DisseminationTimer(), 0, disseminationInterval);
        //And setup the stop timer
        logger.debug("Will stop in {}s...", runTime);
        setupTimer(new StopTimer(), runTime * 1000L);
    }

    private void uponBroadcastTimer(DisseminationTimer broadcastTimer, long timerId) {
        //Upon triggering the broadcast timer, create a new message
        String toSend = this.self.toString() + " MSG " + messageIndex + " " + randomCapitalLetters(Math.max(0, payloadSize));
        //ASCII encodes each character as 1 byte
        byte[] payload = toSend.getBytes(StandardCharsets.US_ASCII);

        BroadcastRequest request = new BroadcastRequest(self, payload, myPrivateKey);
        logger.debug("Sending: {} MSG {} ({} bytes total)", self.toString(), messageIndex, payload.length);
        //And send it to the broadcast protocol
        sendRequest(request, SecureCrashFaultReliableBroadcastProtocol.PROTO_ID);
    }

    private void uponDeliver(DeliveryNotification notification, short sourceProto) {
        //Upon receiving a message, check signature and simply print it
    	String payload = new String(notification.getPayload(), StandardCharsets.US_ASCII);
    	
    	logger.info("Received: {} ", payload);
    	
    	if(this.publicKeys.containsKey(notification.getOriginalSender())) {
    		try {
	    		if(notification.checkSignature(this.publicKeys.get(notification.getOriginalSender()), SignaturesHelper.SignatureAlgorithm)) {
	    			logger.debug("Signature of the message was verified for original sender.");
	    		} else {
	    			logger.debug("Could not verify the signature of the original sender (invalid signature)");
	    		}
    		} catch (Exception e) {
    			logger.debug("Could not verify the signature of the original sender (error verifying signature)");
    			e.printStackTrace();
    		}
    	} else {
    		logger.debug("Could not verify the signature of the original sender (unknown public key)");
    	}
    }

    private void uponStopTimer(StopTimer stopTimer, long timerId) {
        logger.debug("Stopping publications");
        this.cancelTimer(broadCastTimer);
        logger.debug("Stopping sending messages...");
        setupTimer(new ExitTimer(), cooldownTime * 1000L);
        logger.debug("Will terminate in {}s", cooldownTime);
    }
    
    private void uponExitTimer(ExitTimer exitTimer, long timerId) {
        logger.debug("Exiting...");
        System.exit(0);
    }
 
    public static String randomCapitalLetters(int length) {
        int leftLimit = 65; // letter 'A'
        int rightLimit = 90; // letter 'Z'
        Random random = new Random();
        return random.ints(leftLimit, rightLimit + 1).limit(length)
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append).toString();
    }
}
