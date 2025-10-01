import java.util.Properties;

import app.dissemination.AutomatedDisseminationApp;
import app.dissemination.SignedAutomatedDisseminationApp;
import protocols.broadcast.crashreliablebcast.CrashFaultReliableBroadcastProtocol;
import protocols.broadcast.crashreliablebcast.SignedCrashFaultReliableBroadcastProtocol;
import protocols.membership.staticmembership.SecureStaticMembershipProtocol;
import protocols.membership.staticmembership.StaticMembershipProtocol;
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
	
	public static void main(String[] args) {
		
		try {
			Babel babel = Babel.getInstance();
			Properties props = Babel.loadConfig(args, DEFAULT_CONFIG_FILE);
            GenericProtocol application, bcast, membership;

            if(props.getProperty(FAULT_MODEL).equals("crash")) {
                application = new AutomatedDisseminationApp();
                bcast = new CrashFaultReliableBroadcastProtocol();
                membership = new StaticMembershipProtocol();
            } else { // BYZANTINE FAULT TOLERANCE
                application = new SignedAutomatedDisseminationApp();
                bcast = new SignedCrashFaultReliableBroadcastProtocol();
                membership = new SecureStaticMembershipProtocol();
            }

            babel.registerProtocol(application);
            babel.registerProtocol(bcast);
            babel.registerProtocol(membership);

            application.init(props);
            bcast.init(props);
            membership.init(props);

            babel.start();
            System.out.println("Starting...");

		} catch (Exception e) {
			e.printStackTrace();
			System.exit(1);
		}
		
	}
}
