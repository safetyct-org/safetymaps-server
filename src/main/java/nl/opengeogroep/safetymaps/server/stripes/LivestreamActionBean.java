package nl.opengeogroep.safetymaps.server.stripes;

import net.sourceforge.stripes.action.ActionBean;
import net.sourceforge.stripes.action.ActionBeanContext;
import net.sourceforge.stripes.action.DefaultHandler;
import net.sourceforge.stripes.action.ErrorResolution;
import net.sourceforge.stripes.action.Resolution;
import net.sourceforge.stripes.action.StreamingResolution;
import net.sourceforge.stripes.action.StrictBinding;
import net.sourceforge.stripes.action.UrlBinding;
import net.sourceforge.stripes.validation.Validate;
import nl.b3p.web.stripes.ErrorMessageResolution;
import nl.opengeogroep.safetymaps.server.db.DB;
import static nl.opengeogroep.safetymaps.server.db.JSONUtils.rowToJson;

import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.dbutils.handlers.MapListHandler;
import org.json.JSONArray;

import static nl.opengeogroep.safetymaps.server.db.DB.ROLE_ADMIN;

/**
 *
 * @author Bart Verhaar
 */
@UrlBinding("/viewer/api/livestream/{path}")
public class LivestreamActionBean implements ActionBean {
  private ActionBeanContext context;

    @Validate
  private String incident;
  private String path;
  private String vehicles;

  @Override
    public ActionBeanContext getContext() {
        return context;
    }

    @Override
    public void setContext(ActionBeanContext context) {
        this.context = context;
    }

    public String getIncident() {
        return incident;
    }

    public void setIncident(String incident) {
        this.incident = incident;
    }

    public String getPath() {
      return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getVehicles() {
      return vehicles;
    }

    public void setVehicles(String vehicles) {
        this.vehicles = vehicles;
    }

    public Resolution proxy() throws Exception {
      /*if(!context.getRequest().isUserInRole(ROLE) && !context.getRequest().isUserInRole(ROLE_ADMIN)) {
        return new ErrorMessageResolution(HttpServletResponse.SC_FORBIDDEN, "Gebruiker heeft geen toegang tot Livestreams");
      }*/

      if ("get".equals(path)) {
        return load();
      }
      
      if ("set".equals(path)) {
        for(String vehicle: vehicles.split(",")) { 
          DB.qr().update("insert into safetymaps.live(incident, name, url, vehicle) select ?, ?, case when (username = '') is not false then url else replace(url, 'rtsp://', concat('rtsp://', username, ':', pass, '@')) end, vehicle from safetymaps.live_vehicles where vehicle = ? on conflict do nothing", incident, vehicle, vehicle);
        }

        return new StreamingResolution("application/json", "");
      }

      if ("del".equals(path)) {
        try {
          DB.qr().update("delete from safetymaps.live where incident = ? and vehicle not in ?", incident, (String[])vehicles.split(","));
        } catch(Exception e) {
          return new ErrorMessageResolution(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Error: " + e.getClass() + ": " + e.getMessage());
        }

        return new StreamingResolution("application/json", "");
      }

      return null;
    }

    public Resolution load() throws Exception {
      HttpServletRequest request = getContext().getRequest();
      JSONArray response = new JSONArray();

      try {
        List<Map<String,Object>> results = DB.qr().query("SELECT CONCAT(incident, '-', name) as name from safetymaps.live where incident = ?", new MapListHandler(), incident);

        for (Map<String, Object> resultRow : results) {
            response.put(rowToJson(resultRow, false, false));
        }

        return new StreamingResolution("application/json", response.toString());
      } catch(Exception e) {
          return new ErrorMessageResolution(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Error: " + e.getClass() + ": " + e.getMessage());
      }  
    }
}