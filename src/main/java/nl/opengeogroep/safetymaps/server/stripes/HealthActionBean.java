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

@UrlBinding("/health")
public class HealthActionBean implements ActionBean {
  
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

  /**
   * Default handler
   * 
   * @return
   * @throws Exception
   */  
  @DefaultHandler
  public Resolution def() {
    try {
      List<Map<String,Object>> results = DB.qr().query("SELECT * FROM organisation.organisation_smvng_json(4326)", new MapListHandler());

      return new ErrorMessageResolution(HttpServletResponse.SC_OK, "Healty");
    } catch(Exception e) {
      return new ErrorMessageResolution(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Unhealty");
    } 
  }
  
}
