package tukano.impl.rest;

import java.net.URI;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;

import org.glassfish.jersey.jdkhttp.JdkHttpServerFactory;
import org.glassfish.jersey.server.ResourceConfig;

import jakarta.ws.rs.core.Application;
import tukano.api.Authentication;
import tukano.api.auth.RequestCookiesCleanupFilter;
import tukano.api.auth.RequestCookiesFilter;
import tukano.impl.Token;
import utils.Args;
import utils.IP;
import utils.Props;


public class TukanoRestServer extends Application{
	final private static Logger Log = Logger.getLogger(TukanoRestServer.class.getName());

	static final String INETADDR_ANY = "0.0.0.0";
	static String SERVER_BASE_URI = "http://%s:%s/tukano/rest";

	public static final int PORT = 8080;

	private Set<Object> singletons = new HashSet<>();
	private Set<Class<?>> resources = new HashSet<>();

	public static String serverURI;
			
	static {
		System.setProperty("java.util.logging.SimpleFormatter.format", "%4$s: %5$s");
	}
	
	public TukanoRestServer() {
		Token.setSecret( Args.valueOf("-secret", "Secret")); //Token.setSecret("string");
		serverURI = String.format(SERVER_BASE_URI, IP.hostname(), PORT);
		resources.add(RestBlobsResource.class);
		resources.add(RestShortsResource.class);
		resources.add(RestUsersResource.class);
		resources.add(RequestCookiesFilter.class);
     	resources.add(RequestCookiesCleanupFilter.class);
        resources.add(Authentication.class);
	}


	public void start() throws Exception {
	
		ResourceConfig config = new ResourceConfig();
		
		config.register(RestBlobsResource.class);
		config.register(RestUsersResource.class); 
		config.register(RestShortsResource.class);
		
		JdkHttpServerFactory.createHttpServer( URI.create(serverURI.replace(IP.hostname(), INETADDR_ANY)), config);
		
		Log.info(String.format("Tukano Server ready @ %s\n",  serverURI));
	}
	
	@Override
	public Set<Class<?>> getClasses() {
		return resources;
	}

	@Override
	public Set<Object> getSingletons() {
		return singletons;
	}

	public static void main(String[] args) throws Exception {
		//Args.use(args);
		Token.setSecret( Args.valueOf("-secret", "Secret"));
		
		Props.load("azurekeys-region.props");
		
		new TukanoRestServer().start();
		return;
	}
}