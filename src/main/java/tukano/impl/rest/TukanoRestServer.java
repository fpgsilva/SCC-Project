package tukano.impl.rest;

import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;

import jakarta.ws.rs.core.Application;

import utils.Args;

public class TukanoRestServer extends Application {
	final private static Logger Log = Logger.getLogger(TukanoRestServer.class.getName());

	static final String INETADDR_ANY = "0.0.0.0";
	public static String serverURI = "";

	static {
		System.setProperty("java.util.logging.SimpleFormatter.format", "%4$s: %5$s");
	}

	private Set<Object> singletons = new HashSet<>();
	private Set<Class<?>> resources = new HashSet<>();

	public TukanoRestServer() {
		singletons.add(RestBlobsResource.class);
		singletons.add(RestUsersResource.class);
		singletons.add(RestShortsResource.class);
	}

	@Override
	public Set<Class<?>> getClasses() {
		return resources;
	}

	public static void main(String[] args) throws Exception {
		Args.use(args);
		new TukanoRestServer();
	}
}