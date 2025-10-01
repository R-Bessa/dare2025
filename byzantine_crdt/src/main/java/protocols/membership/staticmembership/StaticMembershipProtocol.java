package protocols.membership.staticmembership;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import protocols.common.events.ChannelAvailable;
import protocols.common.events.NeighborDown;
import protocols.common.events.NeighborUp;
import pt.unl.fct.di.novasys.babel.core.GenericProtocol;
import pt.unl.fct.di.novasys.babel.exceptions.HandlerRegistrationException;
import pt.unl.fct.di.novasys.channel.tcp.TCPChannel;
import pt.unl.fct.di.novasys.channel.tcp.events.*;
import pt.unl.fct.di.novasys.network.data.Host;

import java.io.IOException;
import java.net.InetAddress;
import java.util.*;

/** @author Ricardo Bessa **/
public class StaticMembershipProtocol extends GenericProtocol {

	public final static String PAR_MYHOST = "membership.myhost";
	public final static String PAR_NEIGHBORS = "membership.neighbors";

	public final static short PROTO_ID = 200;
	public final static String PROTO_NAME = "StaticMembershipProtocol";


	private Host myself;
    private final Set<Host> candidates;
    private final Set<Host> neighbors;

    private final Logger logger = LogManager.getLogger(StaticMembershipProtocol.class);


	public StaticMembershipProtocol() {
		super(PROTO_NAME, PROTO_ID);

        this.myself = null;
        this.candidates = new HashSet<>();
		this.neighbors = new HashSet<>();
	}

	@Override
	public void init(Properties props) throws HandlerRegistrationException, IOException {

		if (!props.containsKey(PAR_MYHOST) || !props.containsKey(PAR_NEIGHBORS)) {
			if (!props.contains(PAR_MYHOST))
				System.err.println("Protocol " + PROTO_NAME + " requires mandatory parameter: " + PAR_MYHOST
						+ " (format: IP:PORT)");
			if (!props.containsKey(PAR_NEIGHBORS))
				System.err.println("Protocol " + PROTO_NAME + " requires mandatory parameter: " + PAR_NEIGHBORS
						+ " (format: IP:PORT,IP:PORT,...,IP:PORT");
			System.exit(1);
		}

		String[] hostElements = props.getProperty(PAR_MYHOST).split(":");

		this.myself = new Host(InetAddress.getByName(hostElements[0]), Short.parseShort(hostElements[1]));

		Properties channelProps = new Properties();
		channelProps.put(TCPChannel.ADDRESS_KEY, this.myself.getAddress().toString().replace("/", ""));
		channelProps.put(TCPChannel.PORT_KEY, this.myself.getPort() + "");

        int channelID = createChannel(TCPChannel.NAME, channelProps);
		
		/*-------------------- Register Channel Event ------------------------------- */
		registerChannelEventHandler(channelID, OutConnectionDown.EVENT_ID, this::uponOutConnectionDown);
		registerChannelEventHandler(channelID, OutConnectionFailed.EVENT_ID, this::uponOutConnectionFailed);
		registerChannelEventHandler(channelID, OutConnectionUp.EVENT_ID, this::uponOutConnectionUp);
		registerChannelEventHandler(channelID, InConnectionUp.EVENT_ID, this::uponInConnectionUp);
		registerChannelEventHandler(channelID, InConnectionDown.EVENT_ID, this::uponInConnectionDown);


        String[] neighborsCandidates = props.getProperty(PAR_NEIGHBORS).split(",");
        for (String neighborsCandidate : neighborsCandidates) {
            hostElements = neighborsCandidate.split(":");
            Host candidate = new Host(InetAddress.getByName(hostElements[0]), Short.parseShort(hostElements[1]));
            if (!candidate.equals(myself)) {
                this.candidates.add(candidate);
            }
        }

        for (Host h : this.candidates) {
            //This is a simple hack to ensure that only the node with the highest port starts establishing the protocol
            if(h.getPort() < myself.getPort()) {
                openConnection(h);
            }
        }
        
		triggerNotification(new ChannelAvailable(channelID, myself));
	}



    /* --------------------------------- Channel Events ----------------------------  */
	

	private void uponOutConnectionDown(OutConnectionDown event, int channelId) {
		logger.debug("Host {} is down, cause: {}", event.getNode(), event.getCause());
		if (this.candidates.contains(event.getNode()))
			this.candidates.remove(event.getNode());
		else {
            this.neighbors.remove(event.getNode());
            triggerNotification(new NeighborDown(event.getNode()));
        }
	}

	private void uponOutConnectionFailed(OutConnectionFailed<?> event, int channelId) {
		logger.debug("Connection to host {} failed, cause: {}", event.getNode(), event.getCause());
		this.candidates.remove(event.getNode());
	}

	private void uponOutConnectionUp(OutConnectionUp event, int channelId) {
		logger.info("Host (out) {} is up", event.getNode());
		this.candidates.remove(event.getNode());
        if(this.neighbors.add(event.getNode()))
            triggerNotification(new NeighborUp(event.getNode()));
	}

	private void uponInConnectionUp(InConnectionUp event, int channelId) {
		logger.debug("Host (in) {} is up", event.getNode());
		if (!this.neighbors.contains(event.getNode())) {
			openConnection(event.getNode());
		}
	}

	private void uponInConnectionDown(InConnectionDown event, int channelId) {
		logger.debug("Connection from host {} is down, cause: {}", event.getNode(), event.getCause());
	}

}
