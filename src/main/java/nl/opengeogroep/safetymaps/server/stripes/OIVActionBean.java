package nl.opengeogroep.safetymaps.server.stripes;

import static nl.opengeogroep.safetymaps.server.db.DB.bagQr;
import static nl.opengeogroep.safetymaps.server.db.JSONUtils.rowToJson;
import static nl.opengeogroep.safetymaps.server.db.JSONUtils.rowsToJson;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.naming.NamingException;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.dbutils.QueryRunner;
import org.apache.commons.dbutils.handlers.MapHandler;
import org.apache.commons.dbutils.handlers.MapListHandler;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.json.JSONArray;
import org.json.JSONException;
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
      } else if (path.indexOf(OBJECT) == 0) {
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
        "select typeobject, ot.symbol_name, concat('data:image/png;base64,', encode(s.symbol, 'base64')) as symbol, vo.id, formelenaam, st_astext(coalesce(st_centroid(be.geovlak), vo.geom)) geom, coalesce(vb.pand_id, basisreg_identifier) as bid, vo.bron, bron_tabel, hoogste_bouwlaag, laagste_bouwlaag, st_astext(t.geom) as terrein_geom " +
        "from objecten.view_objectgegevens vo " +
        "inner join objecten.object_type ot on ot.naam = vo.typeobject " +
        "inner join algemeen.symbols s on s.symbol_name = ot.symbol_name " + 
        "left join (select distinct object_id, pand_id, hoogste_bouwlaag, laagste_bouwlaag from objecten.view_bouwlagen) vb on vb.object_id = vo.id " +
        "left join algemeen.bag_extent be on vb.pand_id = be.identificatie " +
        "left join objecten.terrein t on vo.id = t.object_id "
      , new MapListHandler());
    JSONArray results = new JSONArray();

    for(Map<String, Object> dbk: dbks) {
      try {
        String source = (String)dbk.get("bron");
        String bid = (String)dbk.get("bid");
        JSONObject result = rowToJson(dbk, false, false);

        if ("BAG".equals(source)) {
          List<Map<String,Object>> dbkAdresses = DB.bagQr().query(
              "select huisnummer, huisletter, huisnummertoevoeging, postcode, woonplaatsnaam " +
              "from bagactueel.adres_full " +
              "where pandid = ?"
            , new MapListHandler(), bid);
          
          JSONArray addresses = new JSONArray();
          for(Map<String, Object> da: dbkAdresses) {
            addresses.put(rowToJson(da, true, false));
          }
          result.put("adressen", addresses);
        } else {
          result.put("adressen", new JSONArray());
        }

        results.put(result);

      } catch(Exception e) {
        log.error("Error while handling object in OVIActionBean.objects", e);
      }
    }

    return new StreamingResolution("application/json", results.toString());
  }

  private Resolution object() throws JSONException, Exception {
    Pattern p = Pattern.compile("object\\/([0-9]+)\\/([0-9]+)");
    Matcher m = p.matcher(path);

    if(!m.find()) {
      return new ErrorResolution(404, "No object id found: /api/" + path);
    }

    int id = Integer.parseInt(m.group(1));
    int layer = Integer.parseInt(m.group(2));

    Map<String,Object> dbk = DB.oivQr().query(
        "select typeobject, ot.symbol_name, concat('data:image/png;base64,', encode(s.symbol, 'base64')) as symbol, vo.id, formelenaam, st_astext(coalesce(st_centroid(be.geovlak), vo.geom)) geom, coalesce(vb.pand_id, basisreg_identifier) as bid, vo.bron, bron_tabel, hoogste_bouwlaag, laagste_bouwlaag, st_astext(t.geom) as terrein_geom " +
        "from objecten.view_objectgegevens vo " +
        "inner join objecten.object_type ot on ot.naam = vo.typeobject " +
        "inner join algemeen.symbols s on s.symbol_name = ot.symbol_name " + 
        "left join (select distinct object_id, pand_id, hoogste_bouwlaag, laagste_bouwlaag from objecten.view_bouwlagen) vb on vb.object_id = vo.id " +
        "left join algemeen.bag_extent be on vb.pand_id = be.identificatie " +
        "left join objecten.terrein t on vo.id = t.object_id "
      , new MapHandler());

    List<Map<String,Object>> gs = DB.oivQr().query(
        "select vn_nr, gevi_nr, eric_kaart, hoeveelheid, eenheid, toestand, omschrijving, st_astext(geom) geom, coalesce(rotatie, 0) rotatie, size, " +
        "vgb.symbol_name, concat('data:image/png;base64,', encode(s.symbol, 'base64')) as symbol " +
        "from objecten.view_gevaarlijkestof_bouwlaag vgb " +
        "inner join algemeen.symbols s on s.symbol_name = vgb.symbol_name " +
        "where object_id = ? and bouwlaag = ? " +
        "union select vn_nr, gevi_nr, eric_kaart, hoeveelheid, eenheid, toestand, omschrijving, st_astext(geom) geom, coalesce(rotatie, 0) rotatie, size, " +
        "vgr.symbol_name, concat('data:image/png;base64,', encode(s.symbol, 'base64')) as symbol " +
        "from objecten.view_gevaarlijkestof_ruimtelijk vgr " +
        "inner join algemeen.symbols s on s.symbol_name = vgr.symbol_name " +
        "where object_id = ?"
      , new MapListHandler(), id, layer, id);
    JSONObject dbkJSON = rowToJson(dbk, false, false);


    dbkJSON.put("gevaarlijkestoffen", rowsToJson(gs, false, false));

    return new StreamingResolution("application/json", dbkJSON.toString());
  }

  private Resolution styles() {
    return new ErrorResolution(400, "Not implemented yet!");
  }
}
