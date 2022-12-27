package nl.opengeogroep.safetymaps.server.admin.stripes;

import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import javax.ejb.Local;
import javax.naming.NamingException;
import net.sourceforge.stripes.action.ActionBean;
import net.sourceforge.stripes.action.ActionBeanContext;
import net.sourceforge.stripes.action.Before;
import net.sourceforge.stripes.action.DefaultHandler;
import net.sourceforge.stripes.action.DontValidate;
import net.sourceforge.stripes.action.ForwardResolution;
import net.sourceforge.stripes.action.RedirectResolution;
import net.sourceforge.stripes.action.Resolution;
import net.sourceforge.stripes.action.StrictBinding;
import net.sourceforge.stripes.action.UrlBinding;
import net.sourceforge.stripes.validation.Validate;
import net.sourceforge.stripes.validation.ValidationErrorHandler;
import net.sourceforge.stripes.validation.ValidationErrors;
import nl.opengeogroep.safetymaps.server.db.DB;

import org.apache.commons.dbutils.handlers.MapHandler;
import org.apache.commons.dbutils.handlers.MapListHandler;
import org.apache.commons.dbutils.handlers.ScalarHandler;

/**
 *
 * @author Safety C&T
 */
@StrictBinding
@UrlBinding("/admin/action/messages")
public class MessagesActionBean implements ActionBean, ValidationErrorHandler {
  private ActionBeanContext context;
  private static final String JSP = "/WEB-INF/jsp/admin/messages.jsp";

  /*
   * Default attributes
   */

  @Override
  public ActionBeanContext getContext() {
      return context;
  }

  @Override
  public void setContext(ActionBeanContext context) {
      this.context = context;
  }

  private List<Map<String,Object>> messages = new ArrayList();
  public List<Map<String, Object>> getMessages() {
      return messages;
  }
  public void setMessages(List<Map<String, Object>> messages) {
      this.messages = messages;
  }

  /*
   * Attributes
   */

  @Validate
  private int id;

  public int getId() {
    return id;
  }
  public void setId(int id) {
    this.id = id;
  }

  @Validate
  private String dtgstart;

  public String getDtgstart() {
    return dtgstart;
  }
  public void setDtgstart(String dtgstart) {
    this.dtgstart = dtgstart;
  }

  @Validate
  private String dtgend;

  public String getDtgend() {
    return dtgend;
  }
  public void setDtgend(String dtgend) {
    this.dtgend = dtgend;
  }

  @Validate
  private String subject;

  public String getSubject() {
    return subject;
  }
  public void setSubject(String subject) {
    this.subject = subject;
  }

  @Validate
  private String description;

  public String getDescription() {
    return description;
  }
  public void setDescription(String description) {
    this.description = description;
  }

  /**
   * Load list information handler
   * 
   * @throws NamingException
   * @throws SQLException
   */
  @Before
  private void loadInfo() throws NamingException, SQLException {
    messages = DB.qr().query("SELECT * FROM safetymaps.messages ORDER BY dtgstart DESC", new MapListHandler());
  }

  /**
   * Edit handler
   * 
   * @return
   * @throws NamingException
   * @throws SQLException
   */
  public Resolution edit() throws NamingException, SQLException {
    if (id > 0) {
      Map<String,Object> data = DB.qr().query("SELECT id, subject, description, username, to_char(dtgstart, 'YYYY-MM-DD HH24:MI') as dtgstart, to_char(dtgend, 'YYYY-MM-DD HH24:MI') as dtgend,  FROM safetymaps.messages WHERE id=?", new MapHandler(), id);

      if(data.get("id") != null) {
        dtgstart = data.get("dtgstart").toString();
        dtgend = data.get("dtgend").toString();
        subject = data.get("subject").toString();
        description = data.get("description").toString();
      }
    } else {
      DateTimeFormatter myFormatObj = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
      LocalDateTime myDateObj = LocalDateTime.now();
      String formattedNow = myFormatObj.format(myDateObj);

      dtgstart = formattedNow;
      dtgend = formattedNow;
    }

    return list();
  }

  /**
   * Save new or edited handler
   * 
   * @return
   * @throws Exception
   */
  public Resolution save() throws Exception {
    String username = context.getRequest().getRemoteUser();
    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");
    Date start = sdf.parse(dtgstart);
    Date end = sdf.parse(dtgend);

    if (id > 0) {
      DB.qr().update("UPDATE safetymaps.messages SET dtgstart=?, dtgend=?, subject=?, description=?, username=? WHERE id=?", 
        new java.sql.Timestamp(start.getTime()), new java.sql.Timestamp(end.getTime()), subject, description, username, id);
    } else {
      DB.qr().update("INSERT INTO safetymaps.messages(dtgstart, dtgend, subject, description, username) VALUES(?, ?, ?, ?, ?)",
        new java.sql.Timestamp(start.getTime()), new java.sql.Timestamp(end.getTime()), subject, description, username);
    }

    return cancel();
  }

  public Resolution delete() throws Exception {
    DB.qr().update("DELETE FROM safetymaps.messages WHERE id=?", id);

    return cancel();
  }

  /**
   * Default handlers
   */

  @DefaultHandler
  public Resolution list() throws NamingException, SQLException {
    return new ForwardResolution(JSP);
  }

  @DontValidate
  public Resolution cancel() throws Exception {
    loadInfo();
    return new RedirectResolution(this.getClass()).flash(this);
  }

  @Override
  public Resolution handleValidationErrors(ValidationErrors errors) throws Exception {
    loadInfo();
    return new ForwardResolution(JSP);
  }
}
