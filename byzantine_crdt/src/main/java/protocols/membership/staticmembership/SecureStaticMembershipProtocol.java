package protocols.membership.staticmembership;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Properties;
import java.util.Random;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import protocols.common.events.SecureChannelAvailable;
import protocols.common.events.SecureNeighborUp;
import protocols.membership.staticmembership.messages.HelloMessage;
import protocols.membership.staticmembership.messages.HelloReplyMessage;
import protocols.membership.staticmembership.messages.ReplyMessage;
import pt.unl.fct.di.novasys.babel.core.GenericProtocol;
import pt.unl.fct.di.novasys.babel.exceptions.HandlerRegistrationException;
import pt.unl.fct.di.novasys.channel.tcp.TCPChannel;
import pt.unl.fct.di.novasys.channel.tcp.events.InConnectionDown;
import pt.unl.fct.di.novasys.channel.tcp.events.InConnectionUp;
import pt.unl.fct.di.novasys.channel.tcp.events.OutConnectionDown;
import pt.unl.fct.di.novasys.channel.tcp.events.OutConnectionFailed;
import pt.unl.fct.di.novasys.channel.tcp.events.OutConnectionUp;
import pt.unl.fct.di.novasys.network.data.Host;
import utils.Crypto;
import utils.SignaturesHelper;
/** @author Professor Joao Leitao (from Reliable Distributed Systems 2025 course) **/
public class SecureStaticMembershipProtocol extends GenericProtocol {

	public final static String PAR_MYHOST = "membership.myhost";
	public final static String PAR_NEIGHBORS = "membership.neighbors";

	public final static short PROTO_ID = 200;
	public final static String PROTO_NAME = "SecureStaticMembershipProtocol";

	private final HashMap<Host, PublicKey> neighbors;
	private final HashSet<Host> candidates;
	private final HashMap<Host, PublicKey> publicKeys;

	private Host myself;

    private final Logger logger = LogManager.getLogger(SecureStaticMembershipProtocol.class);

	private PrivateKey privateKey;
	private KeyStore truststore;
	private PublicKey publicKey;
	private String myIdentifier;

	private final HashMap<Host, Long> issuedChallenges;

	public SecureStaticMembershipProtocol() {
		super(PROTO_NAME, PROTO_ID);

		this.neighbors = new HashMap<>();
		this.candidates = new HashSet<>();
		this.publicKeys = new HashMap<>();

		this.issuedChallenges = new HashMap<>();

		this.myself = null;

		this.privateKey = null;
		this.truststore = null;
		this.publicKey = null;

		this.myIdentifier = null;

	}

	@Override
	public void init(Properties props) throws HandlerRegistrationException, IOException {

		if (!props.containsKey(PAR_MYHOST) || !props.containsKey(PAR_NEIGHBORS) || ! props.containsKey(Crypto.CRYPTO_NAME_KEY)) {
			if (!props.contains(PAR_MYHOST))
				System.err.println("Protocol " + PROTO_NAME + " requires mandatory parameter: " + PAR_MYHOST
						+ " (format: IP:PORT)");
			if (!props.containsKey(PAR_NEIGHBORS))
				System.err.println("Protocol " + PROTO_NAME + " requires mandatory parameter: " + PAR_NEIGHBORS
						+ " (format: IP:PORT,IP:PORT,...,IP:PORT");	
			if(!props.containsKey(Crypto.CRYPTO_NAME_KEY)) {
				System.err.println("Protocol " + PROTO_NAME + " requires mandatory parameter " + Crypto.CRYPTO_NAME_KEY);
			}
			System.exit(1);
		}
		
		myIdentifier = props.getProperty(Crypto.CRYPTO_NAME_KEY);

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

		/*-------------------- Register Message Serializers ------------------------------- */
		registerMessageSerializer(channelID, HelloMessage.MESSAGE_ID, HelloMessage.serializer);
		registerMessageSerializer(channelID, HelloReplyMessage.MESSAGE_ID, HelloReplyMessage.serializer);
		registerMessageSerializer(channelID, ReplyMessage.MESSAGE_ID, ReplyMessage.serializer);
		
		/*-------------------- Register Message Handlers ------------------------------- */
		registerMessageHandler(channelID, HelloMessage.MESSAGE_ID, this::uponReceiveHelloMessage);
		registerMessageHandler(channelID, HelloReplyMessage.MESSAGE_ID, this::uponReceiveHelloReplyMessage);
		registerMessageHandler(channelID, ReplyMessage.MESSAGE_ID, this::uponReceiveReplyMessage);
		
		// Process neighbors and open connection to them

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

		try {
			this.privateKey = Crypto.getPrivateKey(myIdentifier, props);
			this.truststore = Crypto.getTruststore(props);
			this.publicKey = this.truststore.getCertificate(myIdentifier).getPublicKey();
		} catch (Exception e) {
			System.err.println("Could not retrieve cryptographic material");
			e.printStackTrace();
			System.exit(1);
		}
		
		triggerNotification(new SecureChannelAvailable(channelID, myself, privateKey, publicKey));
	}


    /* --------------------------------- Message Handlers ----------------------------------------------------------- */

	private void uponReceiveHelloMessage(HelloMessage msg, Host sender, short protoID, int channel) {
		if (!sender.equals(msg.getSender())) {
            logger.error("Received a HelloMessage from the socket of {} issued by {}", sender, msg.getSender());
			return;
		}

        try {
            logger.debug("Verifying HelloMessage authenticity from sender {}", msg.getSender());
            if(!msg.checkSignature(msg.getPubKey())) {
                logger.error("The received public key does not belong to sender {}", msg.getSender());
                return;
            }

        } catch (Exception e) {
            logger.error("Could not verify the HelloMessage signature from {}", msg.getSender());
            e.printStackTrace();
        }

		this.publicKeys.put(msg.getSender(), msg.getPubKey());
		long hisChallenge = msg.getChallenge();
		long myChallenge = new Random(System.currentTimeMillis()).nextLong();
		this.issuedChallenges.put(msg.getSender(), myChallenge);

		byte[] challengeAnswer = null;
		try {
            logger.debug("Computing answer for challenge put forward by {} which is: {}", msg.getSender(), msg.getChallenge());
			Signature sig = Signature.getInstance(SignaturesHelper.SignatureAlgorithm);
			sig.initSign(privateKey);
			sig.update(ByteBuffer.allocate(Long.BYTES).putLong(hisChallenge).array());
			challengeAnswer = sig.sign();
		} catch (Exception e) {
            logger.error("Could not generate a valid answer to the challenge of {}", msg.getSender());
			e.printStackTrace();
		}

        logger.debug("Answering challenge put forwards by {} sending my Challenge: {}", msg.getSender(), myChallenge);
		HelloReplyMessage hrm = new HelloReplyMessage(myself, publicKey, challengeAnswer, myChallenge);
		
		try {
			hrm.signMessage(privateKey);
			sendMessage(hrm , msg.getSender());
		} catch (Exception e) {
			e.printStackTrace();
		}
		
	}

	private void uponReceiveHelloReplyMessage(HelloReplyMessage msg, Host sender, short protoID, int channel) {
		if (!sender.equals(msg.getSender())) {
            logger.error("Received a HelloReplyMessage from the socker of {} issued by {}", sender, msg.getSender());
			return;
		}

        try {
            logger.debug("Verifying HelloReplyMessage authenticity from sender {}", msg.getSender());
            if(!msg.checkSignature(msg.getPubKey())) {
                logger.error("The received public key does not belong to sender {}", msg.getSender());
                return;
            }

        } catch (Exception e) {
            logger.error("Could not verify the HelloReplyMessage signature from {}", msg.getSender());
            e.printStackTrace();
        }

		// Validate his answer to my challenge
		try {
            logger.debug("Verifying answer for my challenge to {} which is: {}", msg.getSender(), issuedChallenges.get(msg.getSender()));
			Signature sig = Signature.getInstance(SignaturesHelper.SignatureAlgorithm);
			sig.initVerify(msg.getPubKey());
			sig.update(ByteBuffer.allocate(Long.BYTES).putLong(issuedChallenges.get(msg.getSender())).array());
			if (sig.verify(msg.getChallengeReply())) {
                logger.debug("Challenge has been correctly answered by {}", msg.getSender());
				issuedChallenges.remove(msg.getSender());
				this.candidates.remove(msg.getSender());
				this.neighbors.put(msg.getSender(), msg.getPubKey());
				this.publicKeys.put(msg.getSender(), msg.getPubKey());
				triggerNotification(new SecureNeighborUp(msg.getSender(), this.neighbors.get(msg.getSender())));
			} else {
                logger.error("Challenge was incorrectly answered by {}", msg.getSender());
				closeConnection(msg.getSender());
				return;
			}
		} catch (Exception e) {
            logger.debug("Error while validating the challenge answer of {}", msg.getSender());
			e.printStackTrace();
			closeConnection(msg.getSender());
			return;
		}

		// Generate an answer to his challenge
		byte[] challengeAnswer = null;
		try {
            logger.debug("Computing answer for challenge put forward by {} which is: {}", msg.getSender(), msg.getChallenge());
			Signature sig = Signature.getInstance(SignaturesHelper.SignatureAlgorithm);
			sig.initSign(privateKey);
			sig.update(ByteBuffer.allocate(Long.BYTES).putLong(msg.getChallenge()).array());
			challengeAnswer = sig.sign();
		} catch (Exception e) {
            logger.error("Could not generate a valid answer to the challenge of {}", msg.getSender());
			e.printStackTrace();
		}

		// send the reply
		ReplyMessage rm = new ReplyMessage(myself, challengeAnswer);
		try { 
			rm.signMessage(privateKey);
			sendMessage(rm, msg.getSender());
		} catch (Exception e) {
			e.printStackTrace();
		}
		
	}

	private void uponReceiveReplyMessage(ReplyMessage msg, Host sender, short protoID, int channel) {
		if (!sender.equals(msg.getSender())) {
            logger.error("Received a ReplyMessage from the socker of {} issued by {}", sender, msg.getSender());
			return;
		}

        try {
            logger.debug("Verifying ReplyMessage authenticity from sender {}", msg.getSender());
            if(!msg.checkSignature(publicKeys.get(msg.getSender()))) {
                logger.error("The received public key does not belong to sender {}", msg.getSender());
                return;
            }

        } catch (Exception e) {
            logger.error("Could not verify the ReplyMessage signature from {}", msg.getSender());
            e.printStackTrace();
        }

		// Validate his answer to my challenge
		try {
            logger.debug("Verifying answer for my challenge to {} which is: {}", msg.getSender(), issuedChallenges.get(msg.getSender()));
			Signature sig = Signature.getInstance(SignaturesHelper.SignatureAlgorithm);
			sig.initVerify(publicKeys.get(msg.getSender()));
			sig.update(ByteBuffer.allocate(Long.BYTES).putLong(issuedChallenges.get(msg.getSender())).array());
			if (sig.verify(msg.getChallengeReply())) {
                logger.debug("Challenge has been correctly answered by {}", msg.getSender());
				issuedChallenges.remove(msg.getSender());
				this.candidates.remove(msg.getSender());
				this.neighbors.put(msg.getSender(), publicKeys.get(msg.getSender()));
				triggerNotification(new SecureNeighborUp(msg.getSender(), this.neighbors.get(msg.getSender())));
			} else {
                logger.error("Challenge was incorrectly answered by {}", msg.getSender());
				closeConnection(msg.getSender());
            }
		} catch (Exception e) {
            logger.debug("Error while validating the challenge answer of {}", msg.getSender());
			e.printStackTrace();
			closeConnection(msg.getSender());
        }
	}

	/*
	 * --------------------------------- Channel Events ----------------------------
	 */

	private void uponOutConnectionDown(OutConnectionDown event, int channelId) {
		logger.debug("Host {} is down, cause: {}", event.getNode(), event.getCause());
		if (this.candidates.contains(event.getNode()))
			this.candidates.remove(event.getNode());
		else this.neighbors.remove(event.getNode());
	}

	private void uponOutConnectionFailed(OutConnectionFailed<?> event, int channelId) {
		logger.debug("Connection to host {} failed, cause: {}", event.getNode(), event.getCause());
		this.candidates.remove(event.getNode());
	}

	private void uponOutConnectionUp(OutConnectionUp event, int channelId) {
		logger.debug("Host (out) {} is up", event.getNode());
		if (this.candidates.remove(event.getNode())) {
			this.neighbors.put(event.getNode(), null);
			this.issuedChallenges.put(event.getNode(), new Random(System.currentTimeMillis()).nextLong());

            logger.debug("Sending a challenge to {}: {}", event.getNode(), this.issuedChallenges.get(event.getNode()));
			
			HelloMessage hm = new HelloMessage(myself, publicKey, this.issuedChallenges.get(event.getNode()));
			try {
				hm.signMessage(privateKey);
				sendMessage(hm,event.getNode());
                logger.debug("Out connection is up for {} sent challenge...", event.getNode());
			} catch (Exception e) {
				e.printStackTrace();
			}
		} else {
            logger.debug("Out connection is up for {} but node is not part of candidates.", event.getNode());
		}
	}

	private void uponInConnectionUp(InConnectionUp event, int channelId) {
		logger.debug("Host (in) {} is up", event.getNode());
		if (!this.neighbors.containsKey(event.getNode())) {
			this.candidates.remove(event.getNode());
			this.neighbors.put(event.getNode(), null);
			openConnection(event.getNode());
		}
	}

	private void uponInConnectionDown(InConnectionDown event, int channelId) {
		logger.debug("Connection from host {} is down, cause: {}", event.getNode(), event.getCause());
	}

}
