package app;

import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import app.timers.DisseminationTimer;
import app.timers.ExitTimer;
import app.timers.StartTimer;
import app.timers.StopTimer;
import protocols.crdt.requests.ReadRequest;
import protocols.crdt.requests.RemoveRequest;
import protocols.events.ChannelAvailable;
import protocols.events.NeighborUp;
import protocols.events.SecureChannelAvailable;
import protocols.crdt.ORSet;
import protocols.crdt.ByzantineORSet;
import protocols.crdt.replies.AddReply;
import protocols.crdt.replies.ReadReply;
import protocols.crdt.replies.RemoveReply;
import protocols.crdt.requests.AddRequest;
import pt.unl.fct.di.novasys.babel.core.GenericProtocol;
import pt.unl.fct.di.novasys.babel.exceptions.HandlerRegistrationException;
import pt.unl.fct.di.novasys.network.data.Host;
import utils.HashProducer;

public class AutomatedApp extends GenericProtocol {
    private static final Logger logger = LogManager.getLogger(AutomatedApp.class);

    public static final String PROTO_NAME = "AutomatedApp";
    public static final short PROTO_ID = 400;
    public final static String FAULT_MODEL = "fault_model";

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
    private short crdtProtoId;
    private long broadCastTimer;

    private int nAdds;
    private int nRemoves;
    private int currAdd;
    private int currRemove;
    private int totalAdds;
    private int totalRemoves;
    private int idx;
    private Set<String> state;


    public AutomatedApp() { super(PROTO_NAME, PROTO_ID); }

    @Override
    public void init(Properties props) throws HandlerRegistrationException {
    	//Read configurations
        this.payloadSize = Integer.parseInt(props.getProperty("payload_size"));
        this.prepareTime = Integer.parseInt(props.getProperty("prepare_time")); //in seconds
        this.cooldownTime = Integer.parseInt(props.getProperty("cooldown_time")); //in seconds
        this.runTime = Integer.parseInt(props.getProperty("run_time")); //in seconds
        this.disseminationInterval = Integer.parseInt(props.getProperty("broadcast_interval")); //in milliseconds

        this.nAdds = Integer.parseInt(props.getProperty("n_adds"));
        this.nRemoves = nAdds / 2;
        this.currAdd = 0;
        this.currRemove = 0;
        this.totalAdds = 0;
        this.totalRemoves = 0;
        this.idx = 0;
        this.state = new HashSet<>();

        crdtProtoId = props.getProperty(FAULT_MODEL).equals("crash") ? ORSet.PROTO_ID : ORSet.PROTO_ID;


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
        if(currAdd == nAdds) {
            if(!state.iterator().hasNext())
                return;

            sendRequest(new RemoveRequest(self, state.iterator().next()), crdtProtoId);
            currRemove++;

            if(currRemove == nRemoves) {
                currRemove = 0;
                currAdd = 0;
            }
        }
        else {
            String payload = this.self.toString() + " MSG" + idx + randomCapitalLetters(Math.max(0, payloadSize));
            sendRequest(new AddRequest(self, payload), crdtProtoId);
            currAdd++;
            idx++;
        }

    }

    private void uponStopTimer(StopTimer stopTimer, long timerId) {
        sendRequest(new ReadRequest(self), crdtProtoId);
        logger.debug("Stopping publications");
        this.cancelTimer(broadCastTimer);
        logger.debug("Stopping sending messages...");
        setupTimer(new ExitTimer(), cooldownTime * 1000L);
        logger.debug("Will terminate in {}s", cooldownTime);
    }
    
    private void uponExitTimer(ExitTimer exitTimer, long timerId) {
        logger.info("Exiting...");

        try (FileWriter writer = new FileWriter("src/main/java/app/simulation/logs/byzantine/log" + self.getPort() + ".txt", true)) {
            writer.write("Total adds: " + totalAdds + "\n");
            writer.write("Total removes: " + totalRemoves + "\n");
            writer.write("State: " + HashProducer.hashSet(state) + "\n");

            writer.write("Latencies:\n");
            for (String latency : ORSet.latency_records)
                writer.write(latency + "\n");

            writer.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }

        System.exit(0);
    }


    /* ------------------------------- Reply Handlers ----------------------------------- */

    public void handleAddReply(AddReply reply, short sourceProto) {
        logger.debug("Successfully added member: ({})", reply.getElement());

        state.add(reply.getElement());
        totalAdds++;
    }

    public void handleRemoveReply(RemoveReply reply, short sourceProto) {
        logger.debug("Successfully removed member: ({})", reply.getElement());

        state.remove(reply.getElement());
        totalRemoves++;
    }

    public void handleReadReply(ReadReply reply, short sourceProto) {
        logger.debug("Read State: {}", reply.getState());
        logger.debug("State Hash: {}", HashProducer.hashSet(reply.getState()));

        this.state = reply.getState();
    }


    /* ------------------------------- Procedures ----------------------------------- */

    public static String randomCapitalLetters(int length) {
        int leftLimit = 65; // letter 'A'
        int rightLimit = 90; // letter 'Z'
        Random random = new Random();
        return random.ints(leftLimit, rightLimit + 1).limit(length)
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append).toString();
    }

}
