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
@UrlBinding("/admin/action/incidents")
public class IncidentsActionBean implements ActionBean, ValidationErrorHandler {
  private ActionBeanContext context;
  private static final String JSP = "/WEB-INF/jsp/admin/incidents.jsp";
  
  @Override
  public ActionBeanContext getContext() {
      return context;
  }

  @Override
  public void setContext(ActionBeanContext context) {
      this.context = context;
  }

  private List<Map<String,Object>> groups = new ArrayList();
  public List<Map<String, Object>> geGroups() {
      return groups;
  }
  public void setGroups(List<Map<String, Object>> groups) {
      this.groups = groups;
  }

  /*
   * Attributes
   */

   @Validate
   private String group;
 
   public String getGroup() {
     return group;
   }
   public void setGroup(String group) {
     this.group = group;
   }

   @Validate
   private int id;
 
   public int getId() {
     return id;
   }
   public void setId(int id) {
     this.id = id;
   }

   @Validate
   private String mcs;
 
   public String getMcs() {
     return mcs;
   }
   public void setMcs(String mcs) {
     this.mcs = mcs;
   }

   /**
   * Load list information handler
   * 
   * @throws NamingException
   * @throws SQLException
   */
  @Before
  private void loadInfo() throws NamingException, SQLException {
    groups = DB.qr().query("select role, description from safetymaps.role where protected = false or role = 'admin' order by protected desc, role", new MapListHandler());
  }

    /**
   * Edit handler
   * 
   * @return
   * @throws NamingException
   * @throws SQLException
   */
  public Resolution edit() throws NamingException, SQLException { 
    if (group != null && group.length() > 0) {
      Map<String,Object> data = DB.qr().query("SELECT id, role, mcs FROM safetymaps.incidentauthorization WHERE role=?", new MapHandler(), group);

      if(data.get("id") != null) {
        id = (int)data.get("id");
        mcs = data.get("mcs").toString();
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
    if (id > 0 && mcs != null && mcs.length() > 0) {
      DB.qr().update("UPDATE safetymaps.incidentauthorization SET mcs=? WHERE id=?", mcs, id);
    } else if (id > 0 && (mcs == null || mcs.length() == 0)) {
      DB.qr().update("DELETE FROM safetymaps.incidentauthorization WHERE id=?", id);
    } else {
      DB.qr().update("INSERT INTO safetymaps.incidentauthorization(role, mcs)", group, mcs);
    }

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
