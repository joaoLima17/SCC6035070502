package tukano.api;

import java.net.URI;
//import java.net.URI;
import java.util.UUID;

import cache.RedisCache;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response.Status;
import redis.clients.jedis.Jedis;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.CookieParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Cookie;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.NewCookie;
import tukano.api.auth.RequestCookies;
import jakarta.ws.rs.core.Response;
import java.util.logging.Logger;

@Path(Authentication.PATH)
public class Authentication {
	static final String PATH = "login";
	static final String USER = "userId";
	static final String PWD = "pwd";
	static final String COOKIE_KEY = "scc:session";
	static final String LOGIN_PAGE = "login.html";
	private static final int MAX_COOKIE_AGE = 3600;
	public static final String REDIRECT_TO_AFTER_LOGIN = "/ctrl/version";
	private static Logger Log = Logger.getLogger(Authentication.class.getName());

	
	@POST
	public static Response login( @PathParam(USER) String user, @QueryParam(PWD) String password ) {
		System.out.println("user: " + user + " pwd:" + password );
		boolean pwdOk = true; 
		if (pwdOk) {
			String uid = UUID.randomUUID().toString();
			var cookie = new NewCookie.Builder(COOKIE_KEY)
					.value(uid).path("/")
					.comment("sessionid")
					.maxAge(MAX_COOKIE_AGE)
					.secure(false) 
					.httpOnly(true)
					.build();
			
			RedisCache.putSession(uid, new Session( uid, user));	
			
            return Response.status(200)
                   .cookie(cookie) 
                   .build();
		} else
			throw new NotAuthorizedException("Incorrect login");
	}
	
	
	@GET
	@Produces(MediaType.TEXT_HTML)
	public String login() {
		try {
			var in = getClass().getClassLoader().getResourceAsStream(LOGIN_PAGE);
			return new String( in.readAllBytes() );			
		} catch( Exception x ) {
			throw new WebApplicationException( Status.INTERNAL_SERVER_ERROR );
		}
	}
	
	static public Session validateSession(String userId) throws NotAuthorizedException {
		if (userId=="admin")
			return new Session(userId, "admin");
		Log.info("BOLACHINHAS:   " );
		return validateSession( RequestCookies.get().values().stream().findFirst().get(), userId );

	}
	
	static public Session validateSession(@CookieParam(COOKIE_KEY) Cookie cookie, String userId) throws NotAuthorizedException {

		if (cookie == null ) {
			Log.info("NAO TENHO COOKIE CRIADA");
			throw new NotAuthorizedException("No session initialized");
		}
		Log.info("VOU BUSCAR A SESSAO COM BASE NA COOKIE");
		var session = RedisCache.getSession( cookie.getValue());
		if( session == null ) {
			Log.info("NAO ENCONTREI A SESSAO");
			throw new NotAuthorizedException("No valid session initialized");
		}
		Log.info("ENCONTREI SESSAO");
		if (session.user() == null || session.user().length() == 0) 
			throw new NotAuthorizedException("No valid session initialized");
		
		if (!session.user().equals(userId))
			throw new NotAuthorizedException("Invalid user : " + session.user());
		
		return session;
	}

	
}
