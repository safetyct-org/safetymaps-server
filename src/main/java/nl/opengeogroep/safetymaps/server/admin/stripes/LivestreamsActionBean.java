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

  private List<Map<String,Object>> incidentStreams = new ArrayList();
  public List<Map<String, Object>> getIncidentStreams() {
      return incidentStreams;
  }
  public void setIncidentStreams(List<Map<String, Object>> incidentStreams) {
      this.incidentStreams = incidentStreams;
  }

  private List<Map<String,Object>> vehicleStreams = new ArrayList();
  public List<Map<String, Object>> getVehicleStreams() {
      return vehicleStreams;
  }
  public void setVehicleStreams(List<Map<String, Object>> vehicleStreams) {
      this.vehicleStreams = vehicleStreams;
  }  

  @Validate
  private String incidentStreamId;

  public String getIncidentStreamId() {
    return incidentStreamId;
  }
  public void setIncidentStreamId(String incidentStreamId) {
    this.incidentStreamId = incidentStreamId;
  }

  @Validate
  private String vehicleStreamId;

  public String getVehicleStreamId() {
    return vehicleStreamId;
  }
  public void setVehicleStreamId(String vehicleStreamId) {
    this.vehicleStreamId = vehicleStreamId;
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
  private String urlis;

  public String getUrlIs() {
    return urlis;
  }
  public void setUrlIs(String urlis) {
    this.urlis = urlis;
  }

  @Validate
  private String vehicle;

  public String getVehicle() {
    return vehicle;
  }
  public void setVehicle(String vehicle) {
    this.vehicle = vehicle;
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
  private String password;

  public String getPassword() {
    return password;
  }
  public void setPassword(String password) {
    this.password = password;
  }

  @Validate
  private String urlvs;

  public String getUrlVs() {
    return urlvs;
  }
  public void setUrlVs(String urlvs) {
    this.urlvs = urlvs;
  }

  @Before
  private void loadInfo() throws NamingException, SQLException {
    incidentStreams = DB.qr().query("select CONCAT(incident, '-', name) as row_id, * from safetymaps.live", new MapListHandler());
    vehicleStreams = DB.qr().query("select CONCAT(vehicle, '-', url) as row_id, * from safetymaps.live_vehicles", new MapListHandler());
  }

  @DefaultHandler
  public Resolution list() throws NamingException, SQLException {
    return new ForwardResolution(JSP);
  }

  public Resolution edit_is() throws SQLException, NamingException {
    if (incidentStreamId != null) {
      Map<String,Object> data = DB.qr().query("select CONCAT(incident, '-', name) as row_id, * from safetymaps.live where CONCAT(incident, '-', name) = ?", new MapHandler(), incidentStreamId);

      if(data.get("row_id") != null) {
        incident = data.get("incident").toString();
        name = data.get("name").toString();
        urlis = data.get("url").toString();
      }
    }
    return new ForwardResolution(JSP);
  }

  public Resolution edit_vs() throws Exception {
    if (vehicleStreamId != null) {
      Map<String,Object> data = DB.qr().query("select CONCAT(vehicle, '-', url) as row_id, * from safetymaps.live_vehicles where CONCAT(vehicle, '-', url) = ?", new MapHandler(), vehicleStreamId);

      if(data.get("row_id") != null) {
        vehicle = data.get("incident").toString();
        username = data.get("username").toString();
        password = data.get("pass").toString();
        urlvs = data.get("url").toString();
      }
    }
    return new ForwardResolution(JSP);
  }

  public Resolution save_vs() throws Exception {
    if (vehicleStreamId == null) {
      DB.qr().update("insert into safetymaps.live_vehicles(vehicle, url, username, pass) values(?, ?, ?)", vehicle, urlvs, username, password);
    } else {
      DB.qr().update("update safetymaps.live_vehicles set vehicle = ?, url = ?, username = ?, pass = ? where CONCAT(vehicle, '-', url)=?", vehicle, urlvs, username, password, incidentStreamId);
    }
    return cancel();
  }

  public Resolution delete_vs() throws Exception {
    DB.qr().update("delete from safetymaps.live_vehicles where CONCAT(vehicle, '-', url) = ?", vehicleStreamId);
    return cancel();
  }

  public Resolution save_is() throws Exception {
    if (incidentStreamId == null) {
      DB.qr().update("insert into safetymaps.live(incident, name, url) values(?, ?, ?)", incident, name, urlis);
    } else {
      DB.qr().update("update safetymaps.live set incident = ?, name = ?, url = ? where CONCAT(incident, '-', name)=?", incident, name, urlis, incidentStreamId);
    }
    return cancel();
  }

  public Resolution delete_is() throws Exception {
    DB.qr().update("delete from safetymaps.live where CONCAT(incident, '-', name) = ?", incidentStreamId);
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