package nl.opengeogroep.safetymaps.server.admin.stripes;

import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
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
import nl.opengeogroep.safetymaps.server.cache.CACHE;
import nl.opengeogroep.safetymaps.server.db.DB;

import org.apache.commons.dbutils.handlers.MapHandler;
import org.apache.commons.dbutils.handlers.MapListHandler;
import org.apache.commons.lang3.StringUtils;

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
  public List<Map<String, Object>> getGroups() {
      return groups;
  }
  public void setGroups(List<Map<String, Object>> groups) {
      this.groups = groups;
  }

  private List<Map<String,Object>> allLocs = new ArrayList();
  public List<Map<String, Object>> getAllLocs() {
      return allLocs;
  }
  public void setAllLocs(List<Map<String, Object>> allLocs) {
      this.allLocs = allLocs;
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
   private String irstring;
 
   public String getIrstring() {
     return irstring;
   }
   public void setIrstring(String irstring) {
     this.irstring = irstring;
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
   private String funcs;
 
   public String getFuncs() {
     return funcs;
   }
   public void setFuncs(String funcs) {
     this.funcs = funcs;
   }

  @Validate
   private String kvts;
 
   public String getKvts() {
     return kvts;
   }
   public void setKvts(String kvts) {
     this.kvts = kvts;
   }

  @Validate
   private String chars;
 
   public String getChars() {
     return chars;
   }
   public void setChars(String chars) {
     this.chars = chars;
   }

   @Validate
   private String mcs;
 
   public String getMcs() {
     return mcs;
   }
   public void setMcs(String mcs) {
     this.mcs = mcs;
   }

  @Validate
  private List<String> restrictions = new ArrayList<>();
 
  public List<String> getRestrictions() {
    return restrictions;
  }
  public void setRestrictions(List<String> restrictions) {
    this.restrictions = restrictions;
  }

  @Validate
  private List<String> locs = new ArrayList<>();

  public List<String> getLocs() {
    return locs;
  }

  public void setLocs(List<String> locs) {
      this.locs = locs;
  }

  private List<String> incidentroles = new ArrayList<>();

  public List<String> getIncidentroles() {
    return incidentroles;
  }

  public void setIncidentroles(List<String> irs) {
      this.incidentroles = irs;
  }

   /**
   * Load list information handler
   * 
   * @throws NamingException
   * @throws SQLException
   */
  @Before
  private void loadInfo() throws NamingException, SQLException {
    groups = DB.qr().query("select r.role, concat(count(ird.role), ' groep authorisatie(s) voor incident') description, string_agg(ird.description, ';' order by ir.role) incident_roles " +
            "from safetymaps.role r " + 
            "inner join (select role, trim(regexp_split_to_table(roles, ',')) incident_role from safetymaps.role) ir " +
            "  on ir.role = r.role " +
            "  and ir.incident_role like 'smvng_incident_%' " +
            "inner join safetymaps.role ird " +
            "  on ird.role = ir.incident_role " +
            "where r.protected = false " +
            "group by r.role", new MapListHandler());
    allLocs = DB.qr().query("select id, loc, description from safetymaps.incidentlocations", new MapListHandler());
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
      incidentroles =  irstring != null ? Arrays.asList(irstring.split(";")) : null;

      Map<String,Object> data = DB.qr().query("SELECT id, role, mcs, locs, funcs, kvts, chars FROM safetymaps.incidentauthorization WHERE role=?", new MapHandler(), group);

      if(data != null && data.get("id") != null) {
        id = Integer.parseInt(data.get("id").toString());
        mcs = data.get("mcs") != null ? data.get("mcs").toString() : null;
        funcs = data.get("funcs") != null ? data.get("funcs").toString() : null;
        kvts = data.get("kvts") != null ? data.get("kvts").toString() : null;
        chars = data.get("chars") != null ? data.get("chars").toString() : null;
        locs = data.get("locs") != null ? Arrays.asList(data.get("locs").toString().split(",")) : null;

        String myrestrictions = mcs != null && mcs.length() > 0 ? "mcs"
          : funcs != null && funcs.length() > 0 ? "funcs"
          : kvts != null && kvts.length() > 0 ? "kvts"
          : chars != null && chars.length() > 0 ? "chars"
          : "";

        restrictions = Arrays.asList(myrestrictions.split(", "));
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
      if (mcs == null) mcs = "";
      if (funcs == null) funcs = "";
      if (kvts == null) kvts = "";
      if (chars == null) chars = "";
      if (locs == null) locs = new ArrayList<>();
      String locString = StringUtils.join(locs, ",");
      DB.qr().update("DELETE FROM safetymaps.incidentauthorization WHERE id=?", id);
      if (mcs.length() > 0 || funcs.length() > 0 || kvts.length() > 0 || chars.length() > 0 || locString.length() > 0) {
        //DB.qr().update("UPDATE safetymaps.incidentauthorization SET mcs=?, locs=? WHERE id=?", mcs, locString, id);
        DB.qr().update("INSERT INTO safetymaps.incidentauthorization(role, mcs, locs, funcs, kvts, chars) VALUES(?, ?, ?, ?, ?, ?)", group, mcs.toLowerCase(), locString, funcs.toLowerCase(), kvts.toLowerCase(), chars.toLowerCase());
      }
    } else {
      if (mcs == null) mcs = "";
      if (funcs == null) funcs = "";
      if (kvts == null) kvts = "";
      if (chars == null) chars = "";
      if (locs == null) locs = new ArrayList<>();
      String locString = StringUtils.join(locs, ",");
      DB.qr().update("INSERT INTO safetymaps.incidentauthorization(role, mcs, locs, funcs, kvts, chars) VALUES(?, ?, ?, ?, ?, ?)", group, mcs.toLowerCase(), locString, funcs.toLowerCase(), kvts.toLowerCase(), chars.toLowerCase());
    }

    CACHE.ReInitializeAuthCache();

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
