package nl.opengeogroep.safetymaps.server.admin.stripes;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.naming.NamingException;

import org.apache.commons.dbutils.handlers.MapHandler;
import org.apache.commons.dbutils.handlers.MapListHandler;

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

/**
 *
 * @author Safety C&T
 */
@StrictBinding
@UrlBinding("/admin/action/krodata")
public class KroDataActionBean implements ActionBean, ValidationErrorHandler {
  private ActionBeanContext context;
  private static final String JSP = "/WEB-INF/jsp/admin/krodata.jsp";

  @Override
  public ActionBeanContext getContext() {
      return context;
  }

  @Override
  public void setContext(ActionBeanContext context) {
      this.context = context;
  }

  private List<Map<String,Object>> krodata = new ArrayList();
  public List<Map<String, Object>> getKrodata() {
      return krodata;
  }
  public void setKrodata(List<Map<String, Object>> krodata) {
      this.krodata = krodata;
  }

   /**
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
  private String bagpandid;

  public String getBagpandid() {
    return bagpandid;
  }
  public void setBagpandid(String bagpandid) {
    this.bagpandid = bagpandid;
  }

  @Validate
  private String titel;

  public String getTitel() {
    return titel;
  }
  public void setTitel(String titel) {
    this.titel = titel;
  }

  @Validate
  private String inhoud;

  public String getInhoud() {
    return inhoud;
  }
  public void setInhoud(String inhoud) {
    this.inhoud = inhoud;
  }

  @Validate
  private Boolean alertering;

  public Boolean getAlertering() {
    return alertering;
  }
  public void setAlertering(Boolean alertering) {
    this.alertering = alertering;
  }  

  /**
   * Load list information handler
   * 
   * @throws NamingException
   * @throws SQLException
   */
  @Before
  private void loadInfo() throws NamingException, SQLException {
    krodata = DB.qr().query("SELECT * FROM safetymaps.krodata ORDER BY bagpandid, titel ASC", new MapListHandler());
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
      Map<String,Object> data = DB.qr().query("SELECT * FROM safetymaps.krodata WHERE id=?", new MapHandler(), id);

      if(data.get("id") != null) {
        bagpandid = data.get("bagpandid").toString();
        titel = data.get("titel").toString();
        inhoud = data.get("inhoud").toString();
        alertering = Boolean.parseBoolean(data.get("alertering").toString());
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
      DB.qr().update("UPDATE safetymaps.krodata SET titel=?, inhoud=?, alertering=? WHERE id=?", titel, inhoud, alertering, id);
    }

    return cancel();
  }

  public Resolution delete() throws Exception {
    DB.qr().update("DELETE FROM safetymaps.krodata WHERE id=?", id);

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
