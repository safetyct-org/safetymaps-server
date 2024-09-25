package nl.opengeogroep.safetymaps.server.admin.stripes;

import static nl.opengeogroep.safetymaps.server.db.DB.USER_TABLE;
import static nl.opengeogroep.safetymaps.server.db.DB.qr;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.catalina.realm.SecretKeyCredentialHandler;
import org.apache.commons.dbutils.handlers.ScalarHandler;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import net.sourceforge.stripes.action.ActionBean;
import net.sourceforge.stripes.action.ActionBeanContext;
import net.sourceforge.stripes.action.DefaultHandler;
import net.sourceforge.stripes.action.ErrorResolution;
import net.sourceforge.stripes.action.RedirectResolution;
import net.sourceforge.stripes.action.Resolution;
import net.sourceforge.stripes.action.UrlBinding;
import nl.opengeogroep.safetymaps.server.db.Cfg;
import nl.opengeogroep.safetymaps.server.security.PersistentSessionManager;

@UrlBinding("/autologin")
public class AutoLoginActionBean implements ActionBean  {
  private ActionBeanContext context;
  private static final Log log = LogFactory.getLog("autologin");

    @Override
    public ActionBeanContext getContext() {
        return context;
    }

    @Override
    public void setContext(ActionBeanContext context) {
        this.context = context;
    }

    @DefaultHandler
    public Resolution defaultHandler() throws Exception {
      String forUserWithGuid = "";

      try {
        HttpServletRequest request = context.getRequest();
        HttpServletResponse response = context.getResponse();
        forUserWithGuid = request.getParameter("as").replaceAll("[^a-zA-Z0-9-]","");

        if (forUserWithGuid == null) { throw new Exception(); }

        String userHashedPassword = qr().query("select password from " + USER_TABLE + " where guid = ?", new ScalarHandler<String>(), forUserWithGuid);

        if (userHashedPassword == null) { throw new Exception(); }

        String username = qr().query("select username from " + USER_TABLE + " where guid = ?", new ScalarHandler<String>(), forUserWithGuid);

        SecretKeyCredentialHandler credentialHandler = new SecretKeyCredentialHandler();
        credentialHandler.setAlgorithm("PBKDF2WithHmacSHA512");
        credentialHandler.setIterations(100000);
        credentialHandler.setKeyLength(256);
        credentialHandler.setSaltLength(16);
        String tempPassword = RandomStringUtils.random(12, true, true);
        String tempHashedPassword = credentialHandler.mutate(tempPassword);

        Cookie authCookie = null;
        if(request.getCookies() != null) {
            for(Cookie cookie: request.getCookies()) {
                if("sm-plogin".equals(cookie.getName())) {
                    authCookie = cookie;
                }
            }
        }

        if(authCookie != null) {
            String sessionId = authCookie.getValue();
            Cookie cookie = new Cookie("sm-plogin", sessionId);
            cookie.setMaxAge(0);
            response.addCookie(cookie);
            PersistentSessionManager.deleteSession(sessionId);
        }
        
        qr().update("update " + USER_TABLE + " set password = ? where username = ?", tempHashedPassword, username);

        request.logout();
        request.getSession().invalidate();
        request.getSession();
        request.login(username, tempPassword);

        qr().update("update " + USER_TABLE + " set password = ? where username = ?", userHashedPassword, username);

        return new RedirectResolution("/admin"); 
      } catch (Exception ex) {
        log.info("Exception occurred while auto login guid " + forUserWithGuid + ". Details: " + ex.getMessage());
        return new ErrorResolution(HttpServletResponse.SC_FORBIDDEN); 
      }
    }
}
