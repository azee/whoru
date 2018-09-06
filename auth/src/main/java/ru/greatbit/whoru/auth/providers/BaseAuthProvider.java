package ru.greatbit.whoru.auth.providers;

import ru.greatbit.whoru.auth.AuthProvider;
import ru.greatbit.whoru.auth.RedirectResponse;
import ru.greatbit.whoru.auth.Session;
import ru.greatbit.whoru.auth.SessionProvider;
import ru.greatbit.whoru.auth.error.UnauthorizedException;
import ru.greatbit.whoru.auth.utils.HttpUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;

import static ru.greatbit.whoru.auth.utils.HttpUtils.getTokenValueFromHeaders;
import static ru.greatbit.whoru.auth.utils.HttpUtils.isTokenAccessRequest;
import static org.springframework.util.StringUtils.isEmpty;


public abstract class BaseAuthProvider implements AuthProvider {

    public static final String IP_HEADER = "X-Real-IP";
    public static final String PARAM_LOGIN = "login";
    public static final String PARAM_PASSWORD = "password";

    final Logger logger = LoggerFactory.getLogger(getClass());

    @Autowired
    SessionProvider sessionProvider;

    @Value("${auth.domain}")
    protected String authDomain;

    @Value("${auth.session.ttl}")
    protected int sessionTtl;

    @Value("${auth.admin.login}")
    protected String adminLogin;

    @Value("${auth.admin.password}")
    protected String adminPassword;

    @Value("${auth.admin.token}")
    protected String adminToken;

    @Override
    public abstract void doAuth(HttpServletRequest request, HttpServletResponse response);


    @Override
    public void doLogout(HttpServletRequest request, HttpServletResponse response) {
        try {
            if (isTokenAccessRequest(request)) {
                String id = getTokenValueFromHeaders(request);
                sessionProvider.removeSession(id);
                return;
            }

            Cookie sid = HttpUtils.findCookie(request, HttpUtils.SESSION_ID);
            final Cookie cookie = new Cookie(HttpUtils.SESSION_ID, null);
            cookie.setPath("/");
            cookie.setMaxAge(0);
            cookie.setDomain(authDomain);
            cookie.setHttpOnly(false);
            response.addCookie(cookie);
            sessionProvider.removeSession(sid.getValue());
        } catch (Exception e) {
            logger.error("Failed to logout", e);
        }
    }

    @Override
    public boolean isAuthenticated(HttpServletRequest request) throws UnauthorizedException {
        if (isTokenAccessRequest(request)) {
            String id = getTokenValueFromHeaders(request);
            return sessionProvider.sessionExists(id);
        }
        Cookie sid = HttpUtils.findCookie(request, HttpUtils.SESSION_ID);
        return sid != null && sessionProvider.sessionExists(sid.getValue());
    }


    @Override
    public Session getSession(HttpServletRequest request) throws UnauthorizedException {
        if (isTokenAccessRequest(request)) {
            String id = getTokenValueFromHeaders(request);
            if (sessionProvider.sessionExists(id)) {
                return sessionProvider.getSessionById(id);
            } else {
                throw new UnauthorizedException("Can't get session");
            }
        }

        Cookie sid = HttpUtils.findCookie(request, HttpUtils.SESSION_ID);
        if (sid != null && sessionProvider.sessionExists(sid.getValue())){
            return sessionProvider.getSessionById(sid.getValue());
        }
        throw new UnauthorizedException("Can't get session");
    }

    @Override
    public boolean verifyLogin(HttpServletRequest request, String login) {
        return !isEmpty(login) && login.equals(getSession(request).getLogin());
    }

    protected void sendRedirect(HttpServletRequest request, HttpServletResponse response) throws IOException {
        final String retPath = request.getParameterMap().containsKey("retpath") ? request.getParameter("retpath") : "";
        if (!isEmpty(retPath)) {
            response.sendRedirect(retPath);
        }
    }

    @Override
    public RedirectResponse redirectNotAuthTo(HttpServletRequest request) {
        return new RedirectResponse("/login", "retpath");
    }
}
