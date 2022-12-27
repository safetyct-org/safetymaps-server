package nl.opengeogroep.safetymaps.server.stripes;

import java.util.Date;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.dbutils.handlers.MapListHandler;
import org.json.JSONArray;

import net.sourceforge.stripes.action.ActionBean;
import net.sourceforge.stripes.action.ActionBeanContext;
import net.sourceforge.stripes.action.DefaultHandler;
import net.sourceforge.stripes.action.Resolution;
import net.sourceforge.stripes.action.StreamingResolution;
import net.sourceforge.stripes.action.UrlBinding;
import net.sourceforge.stripes.validation.Validate;
import nl.b3p.web.stripes.ErrorMessageResolution;
import nl.opengeogroep.safetymaps.server.db.DB;
import static nl.opengeogroep.safetymaps.server.db.JSONUtils.rowToJson;

@UrlBinding("/viewer/api/messages/{path}")
public class MessageActionBean implements ActionBean {
  
  /*
   * Default attributes
   */
  
  private ActionBeanContext context;

  @Override
  public ActionBeanContext getContext() {
    return context;
  }

  @Override
  public void setContext(ActionBeanContext context) {
    this.context = context;
  }

  /*
   * Attributes
   */

  @Validate()
  private String path;

  public String getPath() {
    return path;
  }

  public void setPath(String path) {
      this.path = path;
  }

  /**
   * Default handler
   * 
   * @return
   * @throws Exception
   */  
  @DefaultHandler
  public Resolution def() {
    JSONArray response = new JSONArray();
    Date now = new Date();
    
    try {
      if("all".equals(path)) {
        List<Map<String,Object>> results = DB.qr().query("SELECT * FROM safetymaps.messages", new MapListHandler());
        for (Map<String, Object> resultRow : results) {
          response.put(rowToJson(resultRow, false, false));
        }
      }

      if("active".equals(path)) {
        List<Map<String,Object>> results = DB.qr().query("SELECT * FROM safetymaps.messages WHERE dtgstart<=? AND dtgend >?", new MapListHandler(), new java.sql.Timestamp(now.getTime()), new java.sql.Timestamp(now.getTime()));
        for (Map<String, Object> resultRow : results) {
          response.put(rowToJson(resultRow, false, false));
        }
      }

      return new StreamingResolution("application/json", response.toString());
    } catch(Exception e) {
      return new ErrorMessageResolution(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Error: " + e.getClass() + ": " + e.getMessage());
    } 
  }
  
}
