package nl.opengeogroep.safetymaps.server.admin.stripes;

import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

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

/**
 *
 * @author Safety C&T
 */
@StrictBinding
@UrlBinding("/admin/action/support")
public class SupportActionBean implements ActionBean, ValidationErrorHandler {
  private ActionBeanContext context;
  private static final String JSP = "/WEB-INF/jsp/admin/support.jsp";

  @Override
  public ActionBeanContext getContext() {
      return context;
  }

  @Override
  public void setContext(ActionBeanContext context) {
      this.context = context;
  }

  private List<Map<String,Object>> tickets = new ArrayList();
  public List<Map<String, Object>> getTickets() {
      return tickets;
  }
  public void setTickets(List<Map<String, Object>> tickets) {
      this.tickets = tickets;
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
   private String dtgmelding;
 
   public String getDtgmelding() {
     return dtgmelding;
   }
   public void setDtgmelding(String dtgmelding) {
     this.dtgmelding = dtgmelding;
   }

   @Validate
   private String description;
 
   public String getDescription() {
     return description;
   }
   public void setDescription(String description) {
     this.description = description;
   }

   @Validate
   private String email;
 
   public String getEmail() {
     return email;
   }
   public void setEmail(String email) {
     this.email = email;
   }
   
   @Validate
   private String name;
 
   public String getName() {
     return name;
   }
   public void setName(String name) {
     this.name = name;
   }

   @Validate
   private String phone;
 
   public String getPhone() {
     return phone;
   }
   public void setPhone(String phone) {
     this.phone = phone;
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
   private String username;
 
   public String getUsername() {
     return username;
   }
   public void setUsername(String username) {
     this.username = username;
   }

   @Validate
   private String solution;
 
   public String getSolution() {
     return solution;
   }
   public void setSolution(String solution) {
     this.solution = solution;
   }

   @Validate
   private String permalink;
 
   public String getPermalink() {
     return solution;
   }
   public void setPermalink(String solution) {
     this.solution = solution;
   }

   @Validate
   private int handled;
 
   public int getHandled() {
     return handled;
   }
   public void setHandled(int handled) {
     this.handled = handled;
   }


   /**
   * Load list information handler
   * 
   * @throws NamingException
   * @throws SQLException
   */
  @Before
  private void loadInfo() throws NamingException, SQLException {
    tickets = DB.qr().query("SELECT * FROM safetymaps.support ORDER BY dtgmelding DESC", new MapListHandler());
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
      Map<String,Object> data = DB.qr().query("SELECT id, permalink, solution, subject, description, username, name, email, phone, handled, to_char(dtgmelding, 'YYYY-MM-DD HH24:MI') as dtgmelding FROM safetymaps.support WHERE id=?", new MapHandler(), id);

      if(data.get("id") != null) {
        dtgmelding = data.get("dtgmelding").toString();
        subject = data.get("subject").toString();
        description = data.get("description").toString();
        email = data.get("email").toString();
        name = data.get("name").toString();
        phone = data.get("phone").toString();
        username = data.get("username").toString();
        solution = data.get("solution").toString();
        permalink = data.get("permalink").toString();
        handled = Integer.parseInt(data.get("handled").toString());
      }
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
    if (id > 0) {
      DB.qr().update("UPDATE safetymaps.support SET solution=? WHERE id=?", solution, id);
    }

    return cancel();
  }

  public Resolution handle() throws Exception {
    save();

    DB.qr().update("UPDATE safetymaps.support SET handled=1 WHERE id=?", id);

    return cancel();
  }

  public Resolution delete() throws Exception {
    DB.qr().update("DELETE FROM safetymaps.support WHERE id=?", id);

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
