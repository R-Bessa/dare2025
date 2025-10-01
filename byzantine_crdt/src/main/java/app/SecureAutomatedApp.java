package app;

import app.timers.DisseminationTimer;
import app.timers.ExitTimer;
import app.timers.StartTimer;
import app.timers.StopTimer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import protocols.broadcast.crashreliablebcast.SignedCrashFaultReliableBroadcastProtocol;
import protocols.broadcast.notification.DeliveryNotification;
import protocols.broadcast.request.BroadcastRequest;
import protocols.common.events.SecureChannelAvailable;
import protocols.common.events.SecureNeighborUp;
import pt.unl.fct.di.novasys.babel.core.GenericProtocol;
import pt.unl.fct.di.novasys.babel.exceptions.HandlerRegistrationException;
import pt.unl.fct.di.novasys.network.data.Host;

import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.util.Properties;
import java.util.Random;

/** @author Ricardo Bessa **/
public class SecureAutomatedApp extends GenericProtocol {
    private static final Logger logger = LogManager.getLogger(SecureAutomatedApp.class);

    //Protocol debug() Information, to register in babel
    public static final String PROTO_NAME = "SecureAutomatedApp";
    public static final short PROTO_ID = 401;

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
    private PrivateKey myPrivateKey;

    public SecureAutomatedApp() {
        super(PROTO_NAME, PROTO_ID);
        messageIndex = 0;
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
        
        subscribeNotification(SecureChannelAvailable.NOTIFICATION_ID, this::uponChannelAvailable);
        subscribeNotification(SecureNeighborUp.NOTIFICATION_ID, this::uponNeighborUp);
        
        registerTimerHandler(DisseminationTimer.TIMER_ID, this::uponBroadcastTimer);
        registerTimerHandler(StartTimer.TIMER_ID, this::uponStartTimer);
        registerTimerHandler(StopTimer.TIMER_ID, this::uponStopTimer);
        registerTimerHandler(ExitTimer.TIMER_ID, this::uponExitTimer);
    	
    	
        //Wait prepareTime seconds before starting
        logger.debug("Waiting...");
    }

    public void uponChannelAvailable(SecureChannelAvailable notification, short protoSource) {
    	this.self = notification.getMyHost();
    	this.myPrivateKey = notification.getMyPrivateKey();

        logger.debug("Communication Channel is ready... starting wait time to start broadcasting ({}s)", prepareTime);
    	setupTimer(new StartTimer(), prepareTime * 1000L);
    }
    
    public void uponNeighborUp(SecureNeighborUp notification, short protoSource) {
        logger.debug("Received NeighborUp notification for: {}", notification.getNeighbor());
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
        sendRequest(request, SignedCrashFaultReliableBroadcastProtocol.PROTO_ID);
    }

    private void uponDeliver(DeliveryNotification notification, short sourceProto) {
    	String payload = new String(notification.getPayload(), StandardCharsets.US_ASCII);
    	logger.info("Received: {} ", payload);
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
