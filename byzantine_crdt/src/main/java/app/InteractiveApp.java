package app;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import protocols.events.ChannelAvailable;
import protocols.events.NeighborUp;
import protocols.events.SecureChannelAvailable;
import protocols.crdt.ORSet;
import protocols.crdt.ByzantineORSet;
import protocols.crdt.replies.AddReply;
import protocols.crdt.replies.ReadReply;
import protocols.crdt.replies.RemoveReply;
import protocols.crdt.requests.AddRequest;
import protocols.crdt.requests.ReadRequest;
import protocols.crdt.requests.RemoveRequest;
import pt.unl.fct.di.novasys.babel.core.GenericProtocol;
import pt.unl.fct.di.novasys.babel.exceptions.HandlerRegistrationException;
import pt.unl.fct.di.novasys.network.data.Host;
import utils.HashProducer;

import java.util.*;

public class InteractiveApp extends GenericProtocol {
    private static final Logger logger = LogManager.getLogger(InteractiveApp.class);

    public static final String PROTO_NAME = "InteractiveApp";
    public static final short PROTO_ID = 401;

    public static final String FAULT_MODEL = "fault_model";

    public static final String COMMANDS_HELPER = "Commands:";
    public final static String ADD_OP = "add";
    public final static String ADD_OP_USAGE = "Usage: add <value>";
    public final static String REMOVE_OP = "remove";
    public final static String REMOVE_OP_USAGE = "Usage: remove <value> <add_id>";
    public final static String READ_OP = "read";
    public final static String READ_OP_USAGE = "Usage: read";
    public final static String EXIT = "exit";
    public final static String EXIT_USAGE = "Usage: exit";
    public final static String HELP = "help";

    public final static String INVALID_UUID = "Invalid UUID";

    private Host self;
    private short crdtProtoId;


    public InteractiveApp() {
        super(PROTO_NAME, PROTO_ID);
    }

    @Override
    public void init(Properties props) throws HandlerRegistrationException {

        crdtProtoId = props.getProperty(FAULT_MODEL).equals("crash") ? ORSet.PROTO_ID : ByzantineORSet.PROTO_ID;

        /* ------------------------------- Subscribe Notifications ----------------------------------- */
        subscribeNotification(ChannelAvailable.NOTIFICATION_ID, this::uponChannelAvailable);
        subscribeNotification(SecureChannelAvailable.NOTIFICATION_ID, this::uponSecureChannelAvailable);
        subscribeNotification(NeighborUp.NOTIFICATION_ID, this::uponNeighborUp);

        /* ------------------------------- Register Reply Handlers ----------------------------------- */
        registerReplyHandler(AddReply.REPLY_ID, this::handleAddReply);
        registerReplyHandler(RemoveReply.REPLY_ID, this::handleRemoveReply);
        registerReplyHandler(ReadReply.REPLY_ID, this::handleReadReply);

        Thread interactiveThread = new Thread(() -> {
            String line;
            String[] components;
            Scanner sc = new Scanner(System.in);
            while(true) {
                System.out.print("> ");
                System.out.flush();
                line = sc.nextLine();
                components = line.split(" ");

                switch(components[0]) {
                    case ADD_OP:
                        if(components.length != 2)
                            logger.error(ADD_OP_USAGE);
                        else
                            sendRequest(new AddRequest(self, components[1]), crdtProtoId);
                        break;

                    case REMOVE_OP:
                        if(components.length != 3)
                            logger.error(REMOVE_OP_USAGE);
                        else {
                            try {
                                RemoveRequest req = new RemoveRequest(self, components[1], UUID.fromString(components[2]));
                                sendRequest(req, crdtProtoId);
                            } catch (Exception e) {
                                logger.error(INVALID_UUID);
                            }
                        }
                        break;

                    case READ_OP:
                        if(components.length != 1)
                            logger.error(READ_OP_USAGE);
                        else
                            sendRequest(new ReadRequest(self), crdtProtoId);
                        break;

                    case EXIT:
                        if(components.length != 1)
                            logger.error(EXIT_USAGE);
                        else {
                            sc.close();
                            System.exit(0);
                        }
                        break;

                    case HELP:
                    default:
                        logger.error(COMMANDS_HELPER);
                        logger.error(ADD_OP_USAGE);
                        logger.error(READ_OP_USAGE);
                        logger.error(READ_OP_USAGE);
                        logger.error(EXIT_USAGE);
                        break;
                }
            }
        });
        interactiveThread.start();
    }



    /* ------------------------------- Notification Handlers ----------------------------------- */

    public void uponChannelAvailable(ChannelAvailable notification, short protoSource) {
        logger.debug("Communication Channel is ready...");
        this.self = notification.getMyHost();
    }

    public void uponSecureChannelAvailable(SecureChannelAvailable notification, short protoSource) {
        logger.debug("Secure Communication Channel is ready...");
        this.self = notification.getMyHost();
    }
    
    public void uponNeighborUp(NeighborUp notification, short protoSource) {
        logger.debug("Received NeighborUp notification for: {}", notification.getNeighbor());
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
