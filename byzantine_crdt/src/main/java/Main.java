import java.util.Properties;

import app.dissemination.AutomatedDisseminationApp;
import protocols.broadcast.crashreliablebcast.SecureCrashFaultReliableBroadcastProtocol;
import protocols.membership.staticmembership.SecureStaticMembershipProtocol;
import pt.unl.fct.di.novasys.babel.core.Babel;


public class Main {
	
	// Sets the log4j (logging library) configuration file and forces IPV4
	static {
		System.setProperty("log4j.configurationFile", "log4j2.xml");
		System.setProperty("java.net.preferIPv4Stack" , "true");
	}
	
	public final static String DEFAULT_CONFIG_FILE = "babel-conf.txt";
	
	public static void main(String[] args) {
		
		try {
			Babel babel = Babel.getInstance();
			Properties props = Babel.loadConfig(args, DEFAULT_CONFIG_FILE);

			AutomatedDisseminationApp application = new AutomatedDisseminationApp();
			SecureCrashFaultReliableBroadcastProtocol bcast = new SecureCrashFaultReliableBroadcastProtocol();
			SecureStaticMembershipProtocol membership = new SecureStaticMembershipProtocol();
			
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
