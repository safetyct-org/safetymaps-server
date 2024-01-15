package nl.opengeogroep.safetymaps.server.stripes;

import static nl.opengeogroep.safetymaps.server.db.DB.bagQr;
import static nl.opengeogroep.safetymaps.server.db.JSONUtils.rowToJson;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import javax.naming.NamingException;

import org.apache.commons.dbutils.QueryRunner;
import org.apache.commons.dbutils.handlers.MapListHandler;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.json.JSONArray;
import org.json.JSONObject;

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

@UrlBinding("/viewer/api/oiv/{path}")
public class OIVActionBean implements ActionBean {
  private static final Log log = LogFactory.getLog(OIVActionBean.class);
  
  private ActionBeanContext context;

  private static final String OBJECTS = "objects";
  private static final String OBJECT = "object/";
  private static final String STYLES = "styles";

  @Validate
  private String path;

  @Override
  public ActionBeanContext getContext() {
    return context;
  }

  @Override
  public void setContext(ActionBeanContext context) {
    this.context = context;
  }

  public String getPath() {
    return path;
  }

  public void setPath(String path) {
    this.path = path;
  }

  @DefaultHandler
  public Resolution oiv() {
    try {
      if (OBJECTS.equals(path)) {
        return objects();
      } else if (OBJECT.equals(path)) {
        return object();
      } else if (STYLES.equals(path)) {
        return styles();
      } else {
        return new ErrorResolution(404, "Not found: /oiv/" + path);
      }
    } catch(Exception e) {
      return new ErrorMessageResolution(500, "Error: " + e.getClass() + ": " + e.getMessage());
    }
  }

  private Resolution objects() throws SQLException, NamingException {
    List<Map<String,Object>> dbks = DB.oivQr().query(
        "select typeobject, ot.symbol_name, concat('data:image/png;base64,', encode(s.symbol, 'base64')) as symbol, vo.id, formelenaam, geom, basisreg_identifier as bid, bron, bron_tabel, max_bouwlaag, min_bouwlaag " +
        "from objecten.view_objectgegevens vo " +
        "inner join objecten.object_type ot on ot.naam = vo.typeobject " +
        "inner join algemeen.\"symbols\" s on s.symbol_name = ot.symbol_name"
      , new MapListHandler());
    JSONArray results = new JSONArray();

    for(Map<String, Object> dbk: dbks) {
      try {
        String source = (String)dbk.get("bron");
        String bid = (String)dbk.get("bid");
        JSONObject result = rowToJson(dbk, false, false);

        if ("BAG".equals(source)) {
          List<Map<String,Object>> dbkAdresses = DB.bagQr().query(
              "select huisnummer, huisletter, huisnummertoevoeging, postcode, woonplaatsnaam" +
              "from bagactueel.adres_full " +
              "where pandid = ?"
            , new MapListHandler(), bid);
          
          result.put("adressen", new JSONArray(dbkAdresses.toString()));
        }

        results.put(result);

      } catch(Exception e) {
        log.error("Error while handling object in OVIActionBean.objects", e);
      }
    }

    return new StreamingResolution("application/json", results.toString());
  }

  private Resolution object() {
    return new ErrorResolution(400, "Not implemented yet!");
  }

  private Resolution styles() {
    return new ErrorResolution(400, "Not implemented yet!");
  }
}
