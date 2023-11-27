package nl.opengeogroep.safetymaps.server.stripes;

import java.util.Date;
import java.util.HashMap;
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

  private class CachedResponseString {
    Date created;
    String response; 

    public CachedResponseString(String response) {
      this.created = new Date();
      this.response = response;
    }

    public boolean isOutDated() {
      int outdatedAfterSecondes = 10;
      Date now = new Date();
      Date outDated = new Date(now.getTime() - outdatedAfterSecondes * 1000);
      return this.created.before(outDated);
    }

    public boolean isReadyToCleanup() {
      int outdatedAfterHours = 1;
      Date now = new Date();
      Date outDated = new Date(now.getTime() - outdatedAfterHours * 60 * 60 * 1000);
      return this.created.before(outDated);
    }
  }

  private void CleanupCacheDef() {
    cache_def.values().removeIf(value -> value.isReadyToCleanup());
  }

  private static final Map<String,CachedResponseString> cache_def = new HashMap<>();

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
    CachedResponseString cache = new CachedResponseString("");

    synchronized(cache_def) {
      CleanupCacheDef();
      try {
        if("all".equals(path)) {
          cache = cache_def.get("all");

          if (!cache_def.containsKey("all") || cache == null || cache.isOutDated()) {
            List<Map<String,Object>> results = DB.qr().query("SELECT * FROM safetymaps.messages", new MapListHandler());
            for (Map<String, Object> resultRow : results) {
              response.put(rowToJson(resultRow, false, false));
            }

            cache = new CachedResponseString(response.toString());
            cache_def.put("all", cache);
          }
        }

        if("active".equals(path)) {
          cache = cache_def.get("active");

          if (!cache_def.containsKey("active") || cache == null || cache.isOutDated()) {
            List<Map<String,Object>> results = DB.qr().query("SELECT * FROM safetymaps.messages WHERE dtgstart<=? AND dtgend >?", new MapListHandler(), new java.sql.Timestamp(now.getTime()), new java.sql.Timestamp(now.getTime()));
            for (Map<String, Object> resultRow : results) {
              response.put(rowToJson(resultRow, false, false));
            }

            cache = new CachedResponseString(response.toString());
            cache_def.put("active", cache);          
          }
        }

        return new StreamingResolution("application/json", cache.response);
      } catch(Exception e) {
        return new ErrorMessageResolution(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Error: " + e.getClass() + ": " + e.getMessage());
      } 
    }
  }
  
}
