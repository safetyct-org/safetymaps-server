package nl.opengeogroep.safetymaps.server.stripes;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;
import javax.mail.Message.RecipientType;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import net.sourceforge.stripes.action.*;
import nl.opengeogroep.safetymaps.server.db.Cfg;
import nl.opengeogroep.safetymaps.server.db.DB;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.text.StrLookup;
import org.apache.commons.lang3.text.StrSubstitutor;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONObject;

/**
 *
 * @author Matthijs Laan
 */
@StrictBinding
@UrlBinding("/viewer/api/annotation")
public class MailActionBean implements ActionBean {
    private ActionBeanContext context;

    private static final Log log = LogFactory.getLog("cfg");

    @Override
    public ActionBeanContext getContext() {
        return context;
    }

    @Override
    public void setContext(ActionBeanContext context) {
        this.context = context;
    }

    public Resolution mail() throws IOException {
      JSONObject response = new JSONObject();

      try {
        String useSupportTickets = Cfg.getSetting("useSupportTickets");

        if ("true".equals(useSupportTickets)) {
          Date message = new Date();
          String username = context.getRequest().getRemoteUser();
          String name = context.getRequest().getParameter("name");
          String email = context.getRequest().getParameter("email");
          String subject = context.getRequest().getParameter("subject");
          String description = context.getRequest().getParameter("remarks");
          String phone = context.getRequest().getParameter("phone");
          String permalink = context.getRequest().getParameter("permalink");

          DB.qr().update("INSERT INTO safetymaps.support(dtgmelding, username, name, email, subject, description, phone, permalink, handled) VALUES(?, ?, ?, ?, ?, ?, ?, 0)", 
            new java.sql.Timestamp(message.getTime()), username, name, email, subject, description, permalink, phone);

          response.put("result", true);
        } else {
          response = sendMail();
        }
      } catch (Exception e) {
        response.put("error", e.getMessage());
        response.put("result", false);
      }
      
      return new StreamingResolution("application/json", response.toString());
    }

    private JSONObject sendMail() {
      Session session;
        JSONObject response = new JSONObject();
        response.put("result", false);
        try {
            Context ctx = new InitialContext();
            session = (Session)ctx.lookup("java:comp/env/mail/session");
        } catch(Exception e) {
            log.error("Mail session not configured correctly, exception looking up JNDI resource", e);
            response.put("error", "Server not configured correctly to send mail");
            return response;
        }

        String mail, to, from, subject;
        try {
            String template = Cfg.getSetting("support_mail_template");
            if(template == null) {
                template = FileUtils.readFileToString(new File(context.getServletContext().getRealPath("/WEB-INF/mail.txt")));
            }

            final Map<String,String[]> parameters = new HashMap(context.getRequest().getParameterMap());

            String replace = Cfg.getSetting("support_mail_replace_search");
            String replacement = Cfg.getSetting("support_mail_replacement");
            if(replace != null && replacement != null && parameters.containsKey("permalink")) {
                String permalink = parameters.get("permalink")[0];
                permalink = Pattern.compile(replace).matcher(permalink).replaceAll(replacement);
                parameters.put("permalink", new String[] { permalink });
            }

            StrSubstitutor request = new StrSubstitutor(new StrLookup<String>() {
                @Override
                public String lookup(String key) {
                    String[] value = parameters.get(key);
                    if(value == null || value.length == 0) {
                        return "";
                    } else {
                        return value[0];
                    }
                }
            });
            mail = request.replace(template);

            to = Cfg.getSetting("support_mail_to");
            from = Cfg.getSetting("support_mail_from");
            if(from == null) {
                from = context.getRequest().getParameter("name") + "<" + context.getRequest().getParameter("email") + ">";
            }
            subject = Cfg.getSetting("support_mail_subject");

            if(to == null || from == null || subject == null) {
                log.error("Missing safetymaps.settings keys for either support_mail_to, support_mail_from or support_mail_subject");
                response.put("error", "Server configuration error formatting mail");
                return response;
            }

            subject = request.replace(subject);

            log.debug("Sending formatted mail to: " + to + ", subject: " + subject + ", body: " + mail);
            log.info("Sending mail to " + to + ", received request from " + context.getRequest().getRemoteAddr());

        } catch(Exception e) {
            log.error("Error formatting mail", e);
            response.put("error", "Server error formatting mail");
            return response;
        }

        try {
            javax.mail.Message msg = new MimeMessage(session);
            msg.setFrom(new InternetAddress(from));
            msg.addRecipient(RecipientType.TO, new InternetAddress(to));
            String sender = context.getRequest().getParameter("email");
            if(sender != null) {
                msg.addRecipient(RecipientType.CC, new InternetAddress(sender));
            }
            msg.setSubject(subject);
            msg.setSentDate(new Date());
            msg.setContent(mail, "text/plain");

            Transport.send(msg);
        } catch(Exception e) {
            log.error("Error formatting mail", e);
            response.put("error", "Server error formatting mail");
            return response;
        }
             
        response.put("result", true);
        return response;
    }
}
