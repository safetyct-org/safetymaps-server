package nl.opengeogroep.safetymaps.server.admin.stripes;

import java.sql.SQLException;
import java.util.ArrayList;
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
@UrlBinding("/admin/action/livestreams")
public class LivestreamsActionBean implements ActionBean, ValidationErrorHandler {
  private ActionBeanContext context;

  private static final String JSP = "/WEB-INF/jsp/admin/livestreams.jsp";

  @Override
  public ActionBeanContext getContext() {
      return context;
  }
  @Override
  public void setContext(ActionBeanContext context) {
      this.context = context;
  }

  private List<Map<String,Object>> streams = new ArrayList();
  public List<Map<String, Object>> getStreams() {
      return streams;
  }
  public void setStreams(List<Map<String, Object>> streams) {
      this.streams = streams;
  }

  @Validate
  private String rowid;

  public String getRowid() {
    return rowid;
  }
  public void setRowid(String rowid) {
    this.rowid = rowid;
  }

  @Validate
  private String incident;

  public String getIncident() {
    return incident;
  }
  public void setIncident(String incident) {
    this.incident = incident;
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
  private String url;

  public String getUrl() {
    return url;
  }
  public void setUrl(String url) {
    this.url = url;
  }

  @Before
  private void loadInfo() throws NamingException, SQLException {
    streams = DB.qr().query("select CONCAT(incident, '-', name) as row_id, * from safetymaps.live", new MapListHandler());
  }

  @DefaultHandler
  public Resolution list() throws NamingException, SQLException {
    return new ForwardResolution(JSP);
  }

  public Resolution edit() throws SQLException, NamingException {
    if (rowid != null) {
      Map<String,Object> data = DB.qr().query("select CONCAT(incident, '-', name) as row_id, * from safetymaps.live where CONCAT(incident, '-', name) = ?", new MapHandler(), rowid);

      if(data.get("row_id") != null) {
        incident = data.get("incident").toString();
        name = data.get("name").toString();
        url = data.get("url").toString();
      }
    }
    return new ForwardResolution(JSP);
  }

  public Resolution save() throws Exception {
    if (rowid == null) {
      DB.qr().update("insert into safetymaps.live(incident, name, url) values(?, ?, ?)", incident, name, url);
    } else {
      DB.qr().update("update safetymaps.live set incident = ?, name = ?, url = ? where row_id=?", incident, name, url, rowid);
    }
    return cancel();
  }

  public Resolution delete() throws Exception {
    DB.qr().update("delete from safetymaps.live where CONCAT(incident, '-', name) = ?", rowid);
    return cancel();
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
