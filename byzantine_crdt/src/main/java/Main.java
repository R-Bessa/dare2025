import java.util.Properties;

import app.AutomatedApp;
import app.InteractiveApp;
import protocols.broadcast.crash.CausalReliableBcastProtocol;
import protocols.broadcast.byzantine.ByzantineReliableBcastProtocol;
import protocols.broadcast.crash.SignedCausalReliableBcastProtocol;
import protocols.crdt.ORSet;
import protocols.crdt.ByzantineORSet;
import protocols.membership.SecureStaticMembershipProtocol;
import protocols.membership.StaticMembershipProtocol;
import pt.unl.fct.di.novasys.babel.core.Babel;
import pt.unl.fct.di.novasys.babel.core.GenericProtocol;


public class Main {
	
	// Sets the log4j (logging library) configuration file and forces IPV4
	static {
		System.setProperty("log4j.configurationFile", "log4j2.xml");
		System.setProperty("java.net.preferIPv4Stack" , "true");
	}
	
	public final static String DEFAULT_CONFIG_FILE = "babel-conf.txt";
    public final static String FAULT_MODEL = "fault_model";
    public static final String APP_INTERACTION_MODE = "app_interaction";
	
	public static void main(String[] args) {
		
		try {
			Babel babel = Babel.getInstance();
			Properties props = Babel.loadConfig(args, DEFAULT_CONFIG_FILE);
            GenericProtocol application, crdt, bcast, membership;

            if(props.getProperty(APP_INTERACTION_MODE).equals("interactive"))
                application = new InteractiveApp();
            else application = new AutomatedApp();

            if(props.getProperty(FAULT_MODEL).equals("crash")) {
                crdt = new ORSet();
                bcast = new CausalReliableBcastProtocol();
                //bcast = new SignedCausalReliableBcastProtocol();
                membership = new StaticMembershipProtocol();
                //membership = new SecureStaticMembershipProtocol();

            } else { // BYZANTINE FAULT TOLERANCE
                //crdt = new ByzantineORSet();
                crdt = new ORSet();
                bcast = new ByzantineReliableBcastProtocol();
                membership = new SecureStaticMembershipProtocol();
            }

            babel.registerProtocol(application);
            babel.registerProtocol(crdt);
            babel.registerProtocol(bcast);
            babel.registerProtocol(membership);

            application.init(props);
            crdt.init(props);
            bcast.init(props);
            membership.init(props);

            babel.start();
            System.out.println("Ready...");

		} catch (Exception e) {
			e.printStackTrace();
			System.exit(1);
		}
		
	}
}
