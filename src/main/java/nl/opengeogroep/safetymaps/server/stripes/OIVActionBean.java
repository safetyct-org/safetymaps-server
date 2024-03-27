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

  private Resolution styles() throws Exception {
    List<Map<String,Object>> symbols = DB.oivQr().query(
      "select symbol_name, concat('data:image/png;base64,', encode(symbol, 'base64')) as symbol from algemeen.symbols"
    , new MapListHandler());

    JSONObject result = new JSONObject();
    JSONObject result_symbols = new JSONObject();

    for(Map<String, Object> symbol: symbols) {
      JSONObject result_symbol = rowToJson(symbol, false, false);
      result_symbols.put((String)symbol.get("symbol_name"), result_symbol);
    }

    result.put("symbols", result_symbols);
    
    return new StreamingResolution("application/json", result.toString());
  }

  private Resolution objects() throws SQLException, NamingException {
    JSONArray results = new JSONArray();

    try {
      results = dbkWithAddresList(0);
    } catch(Exception e) {
      log.error("Error while handling object in OVIActionBean.objects", e);
    }

    return new StreamingResolution("application/json", results.toString());
  }

  private Resolution object() throws JSONException, Exception {
    Pattern p = Pattern.compile("object\\/([0-9]+)\\/(-?[0-9]+)\\/([0-9]+)");
    Matcher m = p.matcher(path);

    if(!m.find()) {
      return new ErrorResolution(404, "No object id found: /api/" + path);
    }

    int id = Integer.parseInt(m.group(1));
    int layer = Integer.parseInt(m.group(2));
    String bagid = m.group(3);

    JSONArray dbks = new JSONArray();
    dbks = dbkWithAddresList(id);

    Map<String,Object> dbk = DB.oivQr().query(
      "select ot.formelenaam, bl.min_bouwlaag, bl.max_bouwlaag, vo.typeobject " +
      "from objecten.object_terrein ot " +
      "inner join objecten.mview_objectgegevens vo on vo.id = ot.object_id " + 
      "left join ( " +
      "  select min(bouwlaag) min_bouwlaag, max(bouwlaag) max_bouwlaag, object_id " +
      "  from objecten.mview_bouwlagen vb  " +
      "  group by object_id " +
      ") bl on bl.object_id = ot.object_id " +
      "where ot.object_id = ?"
    , new MapHandler(), id);

    List<Map<String,Object>> gs = DB.oivQr().query(
        "select vn_nr, gevi_nr, eric_kaart, hoeveelheid, eenheid, toestand, omschrijving, st_astext(geom) geom, coalesce(rotatie, 0) rotatie, size, " +
        "vgb.symbol_name, concat('data:image/png;base64,', encode(s.symbol, 'base64')) as symbol " +
        "from objecten.mview_gevaarlijkestof_bouwlaag vgb " +
        "inner join algemeen.symbols s on s.symbol_name = vgb.symbol_name " +
        "where object_id = ? and bouwlaag = ? " +
        "union select vn_nr, gevi_nr, eric_kaart, hoeveelheid, eenheid, toestand, omschrijving, st_astext(geom) geom, coalesce(rotatie, 0) rotatie, size, " +
        "vgr.symbol_name, concat('data:image/png;base64,', encode(s.symbol, 'base64')) as symbol " +
        "from objecten.mview_gevaarlijkestof_ruimtelijk vgr " +
        "inner join algemeen.symbols s on s.symbol_name = vgr.symbol_name " +
        "where object_id = ?"
      , new MapListHandler(), id, layer, id);

    List<Map<String,Object>> ber = DB.oivQr().query(
      "select * from (select label, st_astext(b.geom) geom, lijndikte, lijnkleur, vulkleur, vulstijl, verbindingsstijl, eindstijl, soortnaam, lijnstijl " +
      "from ( " +
        "select *, cast(unnest(string_to_array(coalesce(style_ids, '0'), ',')) as integer) styleid " +
        "from objecten.mview_bereikbaarheid vb " +
        "where vb.object_id = ? " +
      ") b " +
      "left join algemeen.vw_styles s on s.id = b.styleid " +
      "union select hoogte::varchar(255) as label, st_astext(b.geom) geom, lijndikte, lijnkleur, vulkleur, vulstijl, verbindingsstijl, eindstijl, soortnaam, lijnstijl " +
      "from ( " +
        "select *, cast(unnest(string_to_array(coalesce(style_ids, '0'), ',')) as integer) styleid " +
        "from ( " +
          "SELECT b.id, b.geom, b.datum_aangemaakt, b.datum_gewijzigd, b.hoogte, b.omschrijving, b.object_id, o.formelenaam, st.style_ids " +
          "FROM objecten.object o " +
          "JOIN objecten.isolijnen b ON o.id = b.object_id " +
          "LEFT JOIN objecten.isolijnen_type st ON b.hoogte::text = st.naam::text " +
          "JOIN ( SELECT DISTINCT historie.object_id, " +
          "    max(historie.datum_aangemaakt) AS maxdatetime " +
          "  FROM objecten.historie " +
          "  WHERE historie.status::text = 'in gebruik'::text AND historie.parent_deleted = 'infinity'::timestamp with time zone AND historie.self_deleted = 'infinity'::timestamp with time zone " +
          "  GROUP BY historie.object_id) part ON o.id = part.object_id " +
          "WHERE (o.datum_geldig_vanaf <= now() OR o.datum_geldig_vanaf IS NULL) AND (o.datum_geldig_tot > now() OR o.datum_geldig_tot IS NULL) AND o.self_deleted = 'infinity'::timestamp with time zone AND b.parent_deleted = 'infinity'::timestamp with time zone AND b.self_deleted = 'infinity'::timestamp with time zone " +         
        ") vb " +
        "where vb.object_id = ? " +
      ") b " +
      "left join algemeen.vw_styles s on s.id = b.styleid) sel order by sel.soortnaam asc "
    , new MapListHandler(), id, id);

    List<Map<String,Object>> ruimten = DB.oivQr().query(
      "select * from (select '' as soortnaam, '' as label, st_astext(b.geom) geom, lijndikte, lijnkleur, vulkleur, vulstijl, verbindingsstijl, eindstijl, lijnstijl, 1 as order " +
      "from ( " +
        "select *, cast(unnest(string_to_array(coalesce(style_ids, '0'), ',')) as integer) styleid " +
        "from objecten.mview_bouwlagen bl " +
        "where bl.object_id = ? " +
        " and bl.bouwlaag = ?" + 
      ") b " +
      "left join algemeen.vw_styles s on s.id = b.styleid " + 
      "union select soortnaam, '' as label, st_astext(b.geom) geom, lijndikte, lijnkleur, vulkleur, vulstijl, verbindingsstijl, eindstijl, lijnstijl, 2 as order " +
      "from ( " +
        "select *, cast(unnest(string_to_array(coalesce(style_ids, '0'), ',')) as integer) styleid " +
        "from objecten.mview_ruimten vr " +
        "where vr.object_id = ? " +
        " and vr.bouwlaag = ? " + 
      ") b " +
      "left join algemeen.vw_styles s on s.id = b.styleid " + 
      "union select soortnaam, label, st_astext(b.geom) geom, lijndikte, lijnkleur, vulkleur, vulstijl, verbindingsstijl, eindstijl, lijnstijl, 3 as order " +
      "from ( " +
        "select *, cast(unnest(string_to_array(coalesce(style_ids, '0'), ',')) as integer) styleid " +
        "from objecten.mview_sectoren vs " +
        "where vs.object_id = ? " +
      ") b " +
      "left join algemeen.vw_styles s on s.id = b.styleid " +
      "union select soortnaam, label, st_astext(b.geom) geom, lijndikte, lijnkleur, vulkleur, vulstijl, verbindingsstijl, eindstijl, lijnstijl, 4 as order " +
      "from ( " +
        "select *, cast(unnest(string_to_array(coalesce(style_ids, '0'), ',')) as integer) styleid " +
        "from objecten.mview_gebiedsgerichte_aanpak vs " +
        "where vs.object_id = ? " +
      ") b " +
      "left join algemeen.vw_styles s on s.id = b.styleid " +
      "union select soortnaam, '' as label, st_astext(b.geom) geom, lijndikte, lijnkleur, vulkleur, vulstijl, verbindingsstijl, eindstijl, lijnstijl, 5 as order " +
      "from ( " +
        "select *, cast(unnest(string_to_array(coalesce(style_ids, '0'), ',')) as integer) styleid " +
        "from objecten.view_schade_cirkel_bouwlaag vs " +
        "where vs.object_id = ? " +
        " and vs.bouwlaag = ? " +
      ") b " +
      "left join algemeen.vw_styles s on s.id = b.styleid " + 
      "union select soortnaam, '' as label, st_astext(b.geom) geom, lijndikte, lijnkleur, vulkleur, vulstijl, verbindingsstijl, eindstijl, lijnstijl, 6 as order " +
      "from ( " +
        "select *, cast(unnest(string_to_array(coalesce(style_ids, '0'), ',')) as integer) styleid " +
        "from objecten.mview_schade_cirkel_ruimtelijk vs " +
        "where vs.object_id = ? " +
      ") b " +
      "left join algemeen.vw_styles s on s.id = b.styleid) sel order by sel.order asc, sel.soortnaam asc "
    , new MapListHandler(), id, layer, id, layer, id, id, id, layer, id);

    List<Map<String,Object>> veilighbouwk = DB.oivQr().query(
      "select * from (select st_astext(b.geom) geom, lijndikte, lijnkleur, vulkleur, vulstijl, verbindingsstijl, eindstijl, soortnaam, lijnstijl " +
      "from ( " +
      "  select *, cast(unnest(string_to_array(coalesce(style_ids, '0'), ',')) as integer) styleid " +
      "  from objecten.mview_veiligh_bouwk vvb " +
      "  where vvb.object_id = ? " +
      "    and vvb.bouwlaag = ? " +
      ") b " +
      "left join algemeen.vw_styles s on s.id = b.styleid) sel order by sel.soortnaam asc "
    , new MapListHandler(), id, layer);

    List<Map<String,Object>> cont = DB.oivQr().query(
      "select telefoonnummer, soort, 'onbekend' as naam " +
      "from objecten.view_contactpersoon vc " +
      "where vc.object_id = ? "
    , new MapListHandler(), id);

    List<Map<String,Object>> symbols = DB.oivQr().query(
      "select rotatie, label, size, st_astext(geom) geom, soort, vab.symbol_name " +
      "from objecten.mview_afw_binnendekking vab " +
      "inner join algemeen.symbols s on s.symbol_name = vab.symbol_name " +
      "where object_id = ? " +
      "union select rotatie, label, size, st_astext(geom) geom, soort, vo.symbol_name " +
      "from objecten.mview_opstelplaats vo " +
      "inner join algemeen.symbols s on s.symbol_name = vo.symbol_name " +
      "where vo.object_id = ? " +
      "union select rotatie, label, size, st_astext(geom) geom, soort, vib.symbol_name " +
      "from objecten.mview_ingang_bouwlaag vib " +
      "inner join algemeen.symbols s on s.symbol_name = vib.symbol_name " +
      "where object_id = ? " +
      "  and bouwlaag = ? " +
      "union select rotatie, label, size, st_astext(geom) geom, soort, vir.symbol_name " +
      "from objecten.mview_ingang_ruimtelijk vir " +
      "inner join algemeen.symbols s on s.symbol_name = vir.symbol_name " +
      "where object_id = ? " +
      "union select rotatie, label, size, st_astext(geom) geom, soort, vdb.symbol_name " +
      "from objecten.mview_dreiging_bouwlaag vdb " +
      "inner join algemeen.symbols s on s.symbol_name = vdb.symbol_name " +
      "where object_id = ? " +
      "  and bouwlaag = ? " +
      "union select rotatie, label, size, st_astext(geom) geom, soort, vdr.symbol_name " +
      "from objecten.mview_dreiging_ruimtelijk vdr " +
      "inner join algemeen.symbols s on s.symbol_name = vdr.symbol_name " +
      "where object_id = ? " +
      "union select rotatie, label, size, st_astext(geom) geom, soort, vpoi.symbol_name " +
      "from objecten.mview_points_of_interest vpoi " +
      "inner join algemeen.symbols s on s.symbol_name = vpoi.symbol_name " +
      "where vpoi.object_id = ? " +
      "union select rotatie, label, size, st_astext(geom) geom, soort, vsb.symbol_name " +
      "from objecten.mview_sleutelkluis_bouwlaag vsb " +
      "inner join algemeen.symbols s on s.symbol_name = vsb.symbol_name " +
      "where object_id = ? " +
      "  and bouwlaag = ? " +
      "union select rotatie, label, size, st_astext(geom) geom, soort, vsr.symbol_name " +
      "from objecten.mview_sleutelkluis_ruimtelijk vsr " +
      "inner join algemeen.symbols s on s.symbol_name = vsr.symbol_name " +
      "where object_id = ? " +
      "union select rotatie, label, size, st_astext(geom) geom, soort, vvi.symbol_name " +
      "from objecten.mview_veiligh_install vvi " +
      "inner join algemeen.symbols s on s.symbol_name = vvi.symbol_name " +
      "where object_id = ? " +
      "  and bouwlaag = ? " +
      "union select rotatie, label, size, st_astext(geom) geom, soort, vvr.symbol_name " +
      "from objecten.mview_veiligh_ruimtelijk vvr " +
      "inner join algemeen.symbols s on s.symbol_name = vvr.symbol_name " +
      "where object_id = ?"
    , new MapListHandler(), id, id, id, layer, id, id, layer, id, id, id, layer, id, id, layer, id);  

    List<Map<String,Object>> labels = DB.oivQr().query(
      "select * from (select omschrijving, rotatie, size, st_astext(geom) geom, lijndikte, lijnkleur, vulkleur, vulstijl, verbindingsstijl, eindstijl, lijnstijl " +
      "from ( " +
      "  select *, cast(unnest(string_to_array(coalesce(style_ids, '0'), ',')) as integer) styleid " +
      "  from objecten.mview_label_bouwlaag vlb " +
      "  where vlb.object_id = ? " +
      "    and vlb.bouwlaag = ? " +
      ") b " +
      "left join algemeen.vw_styles s on s.id = b.styleid " + 
      "union select omschrijving, rotatie, size, st_astext(geom) geom, lijndikte, lijnkleur, vulkleur, vulstijl, verbindingsstijl, eindstijl, lijnstijl " +
      "from ( " +
      "  select *, cast(unnest(string_to_array(coalesce(style_ids, '0'), ',')) as integer) styleid " +
      "  from objecten.mview_label_ruimtelijk vlr " +
      "  where vlr.object_id = ? " +
      ") b " +
      "left join algemeen.vw_styles s on s.id = b.styleid) sel "
    , new MapListHandler(), id, layer, id);

    JSONObject dbkJSON = rowToJson(dbk, false, false);

    /*if (!"0".equals(bagid)) {
      Map<String,Object> dbkAdres = DB.bagQr().query(
        "select huisnummer, huisletter, huisnummertoevoeging, postcode, woonplaatsnaam, openbareruimtenaam as straatnaam " +
        "from bagactueel.adres_full " +
        "where pandid = ?"
      , new MapHandler(), bagid);

      if (dbkAdres != null) {
        dbkJSON.put("adres", rowToJson(dbkAdres, false, false));
      }
    }*/

    if (bagid.equals("0405100000551806")) {
      JSONArray media = new JSONArray();
      JSONObject mediaItem = new JSONObject();
      mediaItem.put("filename", "Fictief_RBP_OIV.pdf");
      mediaItem.put("type", "document");
      media.put(mediaItem);
      dbkJSON.put("media", media);
    }

    dbkJSON.put("id", id);
    dbkJSON.put("bid", bagid);
    dbkJSON.put("bouwlaag", layer);
    dbkJSON.put("panden", dbks);
    dbkJSON.put("contactpersonen", rowsToJson(cont, false, false));
    dbkJSON.put("bereikbaarheid", rowsToJson(ber, false, false));
    dbkJSON.put("ruimten", rowsToJson(ruimten, false, false));
    dbkJSON.put("veilighbouwkaders", rowsToJson(veilighbouwk, false, false));
    dbkJSON.put("gevaarlijkestoffen", rowsToJson(gs, false, false));
    dbkJSON.put("symbolen", rowsToJson(symbols, false, false));
    dbkJSON.put("labels", rowsToJson(labels, false, false));

    return new StreamingResolution("application/json", dbkJSON.toString());
  }

  private JSONArray dbkWithAddresList(Integer id) throws Exception {
    String where = id > 0 ? "where vo.id = ?" : "where vo.id <> ?";
    List<Map<String,Object>> dbks = DB.oivQr().query(
        "select typeobject, ot.symbol_name, vo.id, formelenaam, st_astext(coalesce(st_centroid(be.geovlak), vo.geom)) geom, coalesce(vb.pand_id, basisreg_identifier) as bid, vo.bron, bron_tabel, hoogste_bouwlaag, laagste_bouwlaag, st_astext(t.geom) as terrein_geom " +
        "from objecten.mview_objectgegevens vo " +
        "inner join objecten.object_type ot on ot.naam = vo.typeobject " + 
        "left join (select distinct object_id, pand_id, hoogste_bouwlaag, laagste_bouwlaag from objecten.mview_bouwlagen) vb on vb.object_id = vo.id " +
        "left join algemeen.bag_extent be on vb.pand_id = be.identificatie " +
        "left join objecten.terrein t on vo.id = t.object_id " + where
      , new MapListHandler(), id);
    JSONArray results = new JSONArray();

    for(Map<String, Object> dbk: dbks) {
        String source = (String)dbk.get("bron");
        String bid = (String)dbk.get("bid");
        JSONObject result = rowToJson(dbk, false, false);

        if ("BAG".equals(source)) {
          List<Map<String,Object>> dbkAdresses = DB.bagQr().query(
              "select huisnummer, huisletter, huisnummertoevoeging, postcode, woonplaatsnaam, openbareruimtenaam as straatnaam " +
              "from bag_actueel.adres_full " +
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
    }

    return results;
  }
}
