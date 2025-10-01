package app;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import protocols.common.events.ChannelAvailable;
import protocols.common.events.NeighborUp;
import protocols.crdt.AWSet;
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

/** @author Ricardo Bessa **/
public class InteractiveApp extends GenericProtocol {
    private static final Logger logger = LogManager.getLogger(InteractiveApp.class);

    //Protocol debug() Information, to register in babel
    public static final String PROTO_NAME = "InteractiveApp";
    public static final short PROTO_ID = 402;

    private Host self;


    public InteractiveApp() {
        super(PROTO_NAME, PROTO_ID);
    }

    @Override
    public void init(Properties props) throws HandlerRegistrationException {

        /* ------------------------------- Subscribe Notifications ----------------------------------- */
        subscribeNotification(ChannelAvailable.NOTIFICATION_ID, this::uponChannelAvailable);
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
                    case "add":
                        if(components.length != 2) {
                            logger.error("Usage: add <member_name>");
                        } else {
                            sendRequest(new AddRequest(self, components[1]), AWSet.PROTO_ID);
                        }
                        break;
                    case "remove":
                        if(components.length != 3) {
                            logger.error("Usage: remove <member_name> <add_id>");
                        } else {
                            try {
                                sendRequest(new RemoveRequest(self, components[1], UUID.fromString(components[2])), AWSet.PROTO_ID);
                            } catch (Exception e) {logger.error("Invalid UUID");}
                        }
                        break;
                    case "read":
                        if(components.length != 1) {
                            logger.error("Usage: read");
                        } else {
                            sendRequest(new ReadRequest(self), AWSet.PROTO_ID);
                        }
                        break;
                    case "exit":
                        if(components.length != 1) {
                            logger.error("Usage: exit");
                        } else {
                            sc.close();
                            System.exit(0);
                        }
                        break;
                    case "help":
                    default:
                        logger.error("Commands:");
                        logger.error("add <member_name>");
                        logger.error("remove <add_id>");
                        logger.error("read");
                        logger.error("exit");
                        break;
                }
            }
        });
        interactiveThread.start();
    }


    /* ------------------------------- Notification Handlers ----------------------------------- */

    public void uponChannelAvailable(ChannelAvailable notification, short protoSource) {
        logger.debug("Communication Channel is ready.");
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
