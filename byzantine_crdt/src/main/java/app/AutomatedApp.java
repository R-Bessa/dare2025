package app;

import java.util.*;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import app.timers.DisseminationTimer;
import app.timers.ExitTimer;
import app.timers.StartTimer;
import app.timers.StopTimer;
import protocols.common.events.ChannelAvailable;
import protocols.common.events.NeighborUp;
import protocols.common.events.SecureChannelAvailable;
import protocols.crdt.AWSet;
import protocols.crdt.ByzantineAWSet;
import protocols.crdt.replies.AddReply;
import protocols.crdt.replies.ReadReply;
import protocols.crdt.replies.RemoveReply;
import protocols.crdt.requests.AddRequest;
import pt.unl.fct.di.novasys.babel.core.GenericProtocol;
import pt.unl.fct.di.novasys.babel.exceptions.HandlerRegistrationException;
import pt.unl.fct.di.novasys.network.data.Host;
import utils.ChatMembers;
import utils.HashProducer;

/** @author Ricardo Bessa **/
public class AutomatedApp extends GenericProtocol {
    private static final Logger logger = LogManager.getLogger(AutomatedApp.class);

    //Protocol debug() Information, to register in babel
    public static final String PROTO_NAME = "AutomatedApp";
    public static final short PROTO_ID = 400;
    public final static String FAULT_MODEL = "fault_model";

    //Time to wait until starting sending messages
    private int prepareTime;
    //Time to run before shutting down
    private int runTime;
    //Time to wait until starting sending messages
    private int cooldownTime;
    //Interval between each broadcast
    private int disseminationInterval;
    
    private Host self;
    private short crdtProtoId;

    private long broadCastTimer;

    private List<String> chat_members;


    public AutomatedApp() {
        super(PROTO_NAME, PROTO_ID);
    }

    @Override
    public void init(Properties props) throws HandlerRegistrationException {
    	//Read configurations
        this.prepareTime = Integer.parseInt(props.getProperty("prepare_time")); //in seconds
        this.cooldownTime = Integer.parseInt(props.getProperty("cooldown_time")); //in seconds
        this.runTime = Integer.parseInt(props.getProperty("run_time")); //in seconds
        this.disseminationInterval = Integer.parseInt(props.getProperty("broadcast_interval")); //in milliseconds

        this.chat_members = new ArrayList<>();
        chat_members.addAll(Arrays.asList(ChatMembers.members));

        /* ------------------------------- Subscribe Notifications ----------------------------------- */
        subscribeNotification(ChannelAvailable.NOTIFICATION_ID, this::uponChannelAvailable);
        subscribeNotification(SecureChannelAvailable.NOTIFICATION_ID, this::uponSecureChannelAvailable);
        subscribeNotification(NeighborUp.NOTIFICATION_ID, this::uponNeighborUp);

        /* ------------------------------- Register Timers ------------------------------------------- */
        registerTimerHandler(DisseminationTimer.TIMER_ID, this::uponBroadcastTimer);
        registerTimerHandler(StartTimer.TIMER_ID, this::uponStartTimer);
        registerTimerHandler(StopTimer.TIMER_ID, this::uponStopTimer);
        registerTimerHandler(ExitTimer.TIMER_ID, this::uponExitTimer);

        /* ------------------------------- Register Reply Handlers ----------------------------------- */
        registerReplyHandler(AddReply.REPLY_ID, this::handleAddReply);
        registerReplyHandler(RemoveReply.REPLY_ID, this::handleRemoveReply);
        registerReplyHandler(ReadReply.REPLY_ID, this::handleReadReply);

        if(props.getProperty(FAULT_MODEL).equals("crash"))
            crdtProtoId = AWSet.PROTO_ID;
        else crdtProtoId = ByzantineAWSet.PROTO_ID;

        //Wait prepareTime seconds before starting
        logger.debug("Waiting...");
    }


    /* ------------------------------- Notification Handlers ----------------------------------- */

    public void uponChannelAvailable(ChannelAvailable notification, short protoSource) {
    	this.self = notification.getMyHost();

        logger.debug("Communication Channel is ready... starting wait time to start broadcasting ({}s)", prepareTime);
    	setupTimer(new StartTimer(), prepareTime * 1000L);
    }

    public void uponSecureChannelAvailable(SecureChannelAvailable notification, short protoSource) {
        this.self = notification.getMyHost();

        logger.debug("Secure Communication Channel is ready... starting wait time to start broadcasting ({}s)", prepareTime);
        setupTimer(new StartTimer(), prepareTime * 1000L);
    }
    
    public void uponNeighborUp(NeighborUp notification, short protoSource) {
        logger.debug("Received NeighborUp notification for: {}", notification.getNeighbor());
    }


    /* ------------------------------- Timer Handlers ----------------------------------- */

    private void uponStartTimer(StartTimer startTimer, long timerId) {
        logger.debug("Starting Broadcasting Messages... (every {}s)", disseminationInterval / 1000);
        //Start broadcasting periodically
        broadCastTimer = setupPeriodicTimer(new DisseminationTimer(), 0, disseminationInterval);
        //And setup the stop timer
        logger.debug("Will stop in {}s...", runTime);
        setupTimer(new StopTimer(), runTime * 1000L);
    }

    private void uponBroadcastTimer(DisseminationTimer broadcastTimer, long timerId) {
        Collections.shuffle(chat_members);
        sendRequest(new AddRequest(self, chat_members.get(0)), crdtProtoId);
        chat_members.remove(0);
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


    /* ------------------------------- Reply Handlers ----------------------------------- */

    public void handleAddReply(AddReply reply, short sourceProto) {
        logger.info("Successfully added member: ({}, {})", reply.getAdd_id(), reply.getElement());
    }

    public void handleRemoveReply(RemoveReply reply, short sourceProto) {
        logger.info("Successfully removed member: ({}, {})", reply.getAdd_id(), reply.getElement());
    }

    public void handleReadReply(ReadReply reply, short sourceProto) {
        logger.info("Read State: {}", reply.getState());
        logger.info("State Hash: {}", HashProducer.hashSet(reply.getState()));
    }

}
