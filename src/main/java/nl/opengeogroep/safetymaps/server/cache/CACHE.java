package nl.opengeogroep.safetymaps.server.cache;

import static nl.opengeogroep.safetymaps.server.db.DB.getUserDetails;
import static nl.opengeogroep.safetymaps.server.db.JSONUtils.rowToJson;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

import javax.naming.NamingException;
import javax.servlet.http.HttpServletRequest;

import org.apache.commons.dbutils.handlers.MapListHandler;
import org.json.JSONArray;
import org.json.JSONObject;

import nl.opengeogroep.safetymaps.server.db.Cfg;
import nl.opengeogroep.safetymaps.server.db.DB;

public class CACHE {

  private static Date incidentCacheInitialized = null;
  private static Date unitCacheInitialized = null;
  private static Date roadAttentionCacheInitialized = null;
  private static Date authCacheInitialized = null;

  /*private static final List<RoadAttentionCacheItem> roadAttentions = new CopyOnWriteArrayList<RoadAttentionCacheItem>();
  private static final List<UnitCacheItem> units = new CopyOnWriteArrayList<UnitCacheItem>();
  private static final List<IncidentCacheItem> incidents = new CopyOnWriteArrayList<IncidentCacheItem>();*/

  private static final Map<Integer, String> unitStatusList = new ConcurrentHashMap<Integer, String>();
  private static final Map<String, RoadAttentionCacheItem> roadAttentions = new ConcurrentHashMap<String, RoadAttentionCacheItem>();
  private static final Map<String, UnitCacheItem> units = new ConcurrentHashMap<String, UnitCacheItem>();
  private static final Map<String, IncidentCacheItem> incidents = new ConcurrentHashMap<String, IncidentCacheItem>();
  private static final Map<String, List<String>> userVehicles = new ConcurrentHashMap<String, List<String>>();
  private static final List<AuthCacheItem> auths = new CopyOnWriteArrayList<AuthCacheItem>();
  private static final List<AuthIncLocCacheItem> authIncLocs = new CopyOnWriteArrayList<AuthIncLocCacheItem>();

  public static List<Map<String,Object>> bag = new CopyOnWriteArrayList<Map<String,Object>>();
  public static JSONArray dbkwithaddress = new JSONArray();

  //#region  DBK
  public static final void AddDbk(JSONObject dbk) {
    synchronized(CACHE.dbkwithaddress) {
      CACHE.dbkwithaddress.put(dbk);
    }
  }

  public static final void ClearDbks() {
    synchronized(CACHE.dbkwithaddress) {
      CACHE.dbkwithaddress = new JSONArray();
    }
  }

  private static final Integer getOIVVersion() throws Exception {
    return Integer.parseInt(Cfg.getSetting("oivVersion", "30611"));
  }

  public static final void ReInitDbks() throws SQLException, NamingException, Exception {    
    String prefix = CACHE.getOIVVersion() > 30611 ? "concat(ot.symbol_name, '_', ot.symbol_type) as symbol_name" : "symbol_name";
    String suffix = CACHE.getOIVVersion() > 30611 ? " ot.symbol_type,": "";

    List<Map<String,Object>> dbks = DB.oivQr().query(
      "select vo.typeobject, " + prefix + ", vo.id, vo.formelenaam, st_astext(vo.geom) geom, basisreg_identifier as bid, vo.bron, bron_tabel, hoogste_bouwlaag, laagste_bouwlaag, st_astext(ST_Union(ST_SnapToGrid(t.geom, 0.0001))) as terrein_geom " +
      "from objecten.mview_objectgegevens vo " + 
      "inner join objecten.object_type ot on ot.naam = vo.typeobject " +
      "left join (select distinct object_id, pand_id, hoogste_bouwlaag, laagste_bouwlaag from objecten.mview_bouwlagen) vb on vb.object_id = vo.id and vb.pand_id = basisreg_identifier " + 
      "left join objecten.mview_terrein t on vo.id = t.object_id " +
      " group by vo.typeobject, ot.symbol_name,"+ suffix +" vo.id, vo.formelenaam, vo.geom, basisreg_identifier, vo.bron, bron_tabel, hoogste_bouwlaag, laagste_bouwlaag"
    , new MapListHandler());
    
    ClearDbks();

    for(Map<String, Object> dbk: dbks) {
        String source = (String)dbk.get("bron");
        String bid = (String)dbk.get("bid");
        JSONObject result = rowToJson(dbk, false, false);

        if ("BAG".equals(source)) {
          List<Map<String,Object>> dbkAdresses = DB.bagQr().query(
              "select huisnummer, huisletter, huisnummertoevoeging, postcode, woonplaatsnaam, openbareruimtenaam as straatnaam, pandid " +
              "from bag_actueel.adres_full " +
              "where pandid = ?"
            , new MapListHandler(), bid);
          
          JSONArray addresses = new JSONArray();
          for(Map<String, Object> da: dbkAdresses) {
            CACHE.bag.add(da);
            addresses.put(rowToJson(da, true, false));
          }
          result.put("adressen", addresses);
        } else {
          result.put("adressen", new JSONArray());
        }

        synchronized(CACHE.dbkwithaddress) {
          CACHE.dbkwithaddress.put(result);
        }
    }
  }
  //#endregion

  //#region  USERVEHICLE
  public static final List<String> GetUserVehicles(HttpServletRequest request) {
    String username = request.getRemoteUser();
    Optional<List<String>> vehicleList = Optional.ofNullable(userVehicles.get(username));

    if (!vehicleList.isPresent()) {
      try(Connection c = DB.getConnection()) {
        JSONObject details = getUserDetails(request, c);   
        vehicleList = Optional.of(Arrays.asList(details.optString("voertuignummer", "-").replaceAll("\\s", ",").replaceAll("-", "").split(",")));

        synchronized(CACHE.userVehicles) {
          CACHE.userVehicles.put(username, vehicleList.get());
        }
      } catch(Exception e) {
        return null;
      }
    }

    return vehicleList.get();
  }
  
  public static final void ClearUserVehicles(String username) {
    synchronized(CACHE.userVehicles) {
      userVehicles.remove(username);
    }
  }
  //#endregion 

  //#region INITIALIZATION
  public static final Boolean IsRoadAttentionCacheInitialized() { return roadAttentionCacheInitialized != null; }
  public static final void InitializeRoadAttentionCache() throws SQLException, NamingException {
    List<Map<String, Object>> dbList = DB.qr().query("select * from safetymaps.roadattentions", new MapListHandler());
    for (Map<String, Object> dbItem : dbList) {
      RoadAttentionCacheItem ci = new RoadAttentionCacheItem(
        (String)dbItem.get("source"), 
        (String)dbItem.get("sourceEnv"), 
        (String)dbItem.get("sourceId"),
        (String)dbItem.get("sourceEnvId"),
        (String)dbItem.get("tenantId"),
        (String)dbItem.get("kindOfAttention"),
        "", 
        (String)dbItem.get("attention"),
        (Date)dbItem.get("beginDate"),
        (Date)dbItem.get("endDate"),
        (String)dbItem.get("geoLocation")
      );

      ci.Save();
      CACHE.AddRoadAttention(ci);
    }
    
    CACHE.authCacheInitialized = new Date();
  }

  public static final Boolean IsAuthCacheInitialized() { return authCacheInitialized != null; }
  public static final void InitializeAuthCache() throws SQLException, NamingException {
    List<Map<String, Object>> dbList = DB.qr().query("select id, loc from safetymaps.incidentlocations", new MapListHandler());
    for (Map<String, Object> dbItem : dbList) {
      AuthIncLocCacheItem ci = new AuthIncLocCacheItem(
        (Integer)dbItem.get("id"),
        (String)dbItem.get("loc")
      );

      CACHE.authIncLocs.add(ci);
    }
    
    dbList = DB.qr().query("select * from safetymaps.incidentauthorization", new MapListHandler());
    for (Map<String, Object> dbItem : dbList) {
      AuthCacheItem ci = new AuthCacheItem(
        (String)dbItem.get("role"), 
        (String)dbItem.get("mcs"), 
        (String)dbItem.get("funcs"), 
        (String)dbItem.get("chars"), 
        (String)dbItem.get("kvts"), 
        (String)dbItem.get("locs"),
        authIncLocs
      );

      ci.Save();
      CACHE.AddAuth(ci);
    }
    
    CACHE.authCacheInitialized = new Date();
  }

  public static final void ReInitializeAuthCache() throws SQLException, NamingException {
    CACHE.authIncLocs.clear();
    CACHE.auths.clear();
    CACHE.InitializeAuthCache();
  }

  public static final Boolean IsIncidentCacheInitialized() { return incidentCacheInitialized != null; }
  public static final void InitializeIncidentCache() throws SQLException, NamingException {
    List<Map<String, Object>> dbList = DB.qr().query("select * from safetymaps.incidents", new MapListHandler());
    for (Map<String, Object> dbItem : dbList) {
      IncidentCacheItem ci = new IncidentCacheItem(
        (String)dbItem.get("source"), 
        (String)dbItem.get("sourceenv"), 
        (String)dbItem.get("sourceid"), 
        (String)dbItem.get("sourceenvid"), 
        (String)dbItem.get("notes"), 
        (String)dbItem.get("units"), 
        (String)dbItem.get("location"), 
        (String)dbItem.get("discipline"), 
        (String)dbItem.get("status"), 
        (String)dbItem.get("sender"), 
        (String)dbItem.get("characts"), 
        (String)dbItem.get("tenantid"), 
        (String)dbItem.get("talkinggroups"), 
        (Integer)dbItem.get("number"),
        (String)dbItem.get("funcs")
      );

      ci.Save();
      CACHE.AddIncident(ci);
    }
    
    CACHE.incidentCacheInitialized = new Date();
  }

  public static final Boolean IsUnitCacheInitialized() { return unitCacheInitialized != null; }
  public static final void InitializeUnitCache() throws SQLException, NamingException {
    List<Map<String, Object>> dbList = DB.qr().query("select * from safetymaps.units", new MapListHandler());
    for (Map<String, Object> dbItem : dbList) {
      UnitCacheItem ci = new UnitCacheItem(
        (String)dbItem.get("source"), 
        (String)dbItem.get("sourceenv"), 
        (String)dbItem.get("sourceid"), 
        (String)dbItem.get("sourceenvid"), 
        (Integer)dbItem.get("gmsstatuscode"), 
        (String)dbItem.get("sender"), 
        (String)dbItem.get("primairevoertuigsoort"),  
        (String)dbItem.get("abbs"), 
        (String)dbItem.get("post"),
        (String)dbItem.get("rol"),
        (String)dbItem.get("incident")
      );

      if (dbItem.get("lon") != null) {
        ci.UpdateLocation(
          ((BigDecimal)dbItem.get("lon")).doubleValue(),
          ((BigDecimal)dbItem.get("lat")).doubleValue(),
          (Integer)dbItem.get("speed"),
          (Integer)dbItem.get("heading"),
          (Integer)dbItem.get("eta")
        );
      }

      ci.Save();
      CACHE.AddUnit(ci);
    }
    
    CACHE.unitCacheInitialized = new Date();
  }
  //#endregion

  //#region ROADATTENTIONS 
  public static final Optional<RoadAttentionCacheItem> FindRoadAttention(String sourceEnvId) { 
    return Optional.ofNullable(CACHE.roadAttentions.get(sourceEnvId));
    //return CACHE.roadAttentions.values().stream().filter(ra -> ra.GetSourceEnvId().equals(sourceEnvId)).findFirst();
  }

  public static final void AddRoadAttention(RoadAttentionCacheItem raci) {
    CACHE.roadAttentions.put(raci.GetSourceEnvId(), raci);
    //CACHE.roadAttentions.add(raci);
  }

  public static final void UpdateRoadAttention(String sourceEnvId, RoadAttentionCacheItem raci) {
    CACHE.roadAttentions.put(sourceEnvId, raci);
    /*Optional<RoadAttentionCacheItem> oldRaci = CACHE.FindRoadAttention(sourceEnvId);

    if (oldRaci.isPresent()) {
      Integer index = CACHE.roadAttentions.indexOf(oldRaci.get());
      CACHE.roadAttentions.set(index, raci);
    }*/
  }

  public static final List<RoadAttentionCacheItem> GetReadyToCleanupRoadAttentions() {
    return CACHE.roadAttentions.values().stream().filter(ci -> ci.IsReadyForCleanup()).collect(Collectors.toList());
  }

  public static final List<RoadAttentionCacheItem> GetDirtyRoadAttentions() {
    return CACHE.roadAttentions.values().stream().filter(ci -> ci.IsDirty()).collect(Collectors.toList());
  }

  public static final void SaveRoadAttentions() throws SQLException, NamingException {
    for (RoadAttentionCacheItem ci : CACHE.GetDirtyRoadAttentions()) {
      ci.SaveToDb();
      ci.Save();
      CACHE.UpdateRoadAttention(ci.GetSourceEnvId(), ci);
    }
  }

  public static final void CleanupRoadAttentions() throws SQLException, NamingException {
    for (RoadAttentionCacheItem ci : CACHE.GetReadyToCleanupRoadAttentions()) {
      ci.RemoveFromDb();
      CACHE.roadAttentions.remove(ci.GetSourceEnvId());
    }
  }
  //#endregion

  //#region AUTH 
  public static final List<AuthCacheItem> GetDirtyAuths() {
    return CACHE.auths.stream().filter(ci -> ci.IsDirty()).collect(Collectors.toList());
  }

  public static final void SaveAuths() throws SQLException, NamingException {
    for (AuthCacheItem ci : CACHE.GetDirtyAuths()) {
      ci.SaveToDb();
      ci.Save();
      CACHE.UpdateAuth(ci.GetRoles(), ci);
    }
  }

  public static final Optional<AuthCacheItem> FindAuth(String roles) { 
    return CACHE.auths.stream().filter(a -> a.GetRoles().equals(roles)).findFirst();
  }

  public static final void UpdateAuth(String roles, AuthCacheItem aci) {
    Optional<AuthCacheItem> oldAci = CACHE.FindAuth(roles);

    if (oldAci.isPresent()) {
      Integer index = CACHE.auths.indexOf(oldAci.get());
      synchronized(CACHE.auths) {
        CACHE.auths.set(index, aci);
      }
    }
  }

  public static final void AddAuth(AuthCacheItem aci) {
    synchronized(CACHE.auths) {
      CACHE.auths.add(aci);
    }
  }

  public static final List<AuthCacheItem> GetAllAuths() {
    return CACHE.auths;
  }
  //#endregion

  //#region UNITS 
  public static final Map<Integer, String> GetUnitStatusList() throws SQLException, NamingException { 
    if (CACHE.unitStatusList.size() == 0) {
      List<Map<String, Object>> dbList = DB.qr().query("select * from safetymaps.mdstatusses", new MapListHandler());
      for (Map<String, Object> dbItem : dbList) {
        synchronized(CACHE.unitStatusList) {
          CACHE.unitStatusList.put((Integer)dbItem.get("gmsstatuscode"), (String)dbItem.get("gmsstatustext"));
        }
      }
    }

    return CACHE.unitStatusList; 
  }

  public static final List<UnitCacheItem> GetAllUnits() { return CACHE.units.values().stream().collect(Collectors.toList()); }
  public static final void AddUnit(UnitCacheItem ci) {
    synchronized(CACHE.units) {
      CACHE.units.put(ci.GetSourceEnvId(), ci);
    }
    //CACHE.units.add(ci);
  }

  public static final Optional<UnitCacheItem> FindUnit(String sourceEnvId) { 
    return Optional.ofNullable(CACHE.units.get(sourceEnvId));
    //return CACHE.units.stream().filter(u -> u.GetSourceEnvId().equals(sourceEnvId)).findFirst();
  }

  public static final List<UnitCacheItem> FindUnitsWithId(String unitId) {
    return CACHE.units.values().stream().filter(u -> u.sourceId.equals(unitId)).collect(Collectors.toList());
  }

  public static final List<Map<String, Object>> GetUnits(String sourceEnv) {
    return CACHE.units.values().stream().filter(u -> u.GetSourceEnv().equals(sourceEnv)).map(u -> u.ConvertToMap()).collect(Collectors.toList());
  }

  public static final List<UnitCacheItem> GetDirtyUnits() {
    return CACHE.units.values().stream().filter(u -> u.IsDirty()).collect(Collectors.toList());
  }

  public static final void UpdateUnit(String sourceEnvId, UnitCacheItem ci) {
    synchronized(CACHE.units) {
      CACHE.units.put(sourceEnvId, ci);
    }
    /*Optional<UnitCacheItem> oldCi = CACHE.FindUnit(sourceEnvId);

    if (oldCi.isPresent()) {
      Integer index = CACHE.units.indexOf(oldCi.get());
      CACHE.units.set(index, ci);
    }*/
  }

  public static final void SaveUnits() throws SQLException, NamingException {
    for (UnitCacheItem ci : CACHE.GetDirtyUnits()) {
      ci.SaveToDb();
      ci.Save();
      CACHE.UpdateUnit(ci.GetSourceEnvId(), ci);
    }
  }
  //#endregion

  //#region INCIDENTS 
  public static final List<IncidentCacheItem> GetAllIncidents() { return CACHE.incidents.values().stream().collect(Collectors.toList()); }
  public static final void AddIncident(IncidentCacheItem ci) {
    synchronized(CACHE.incidents) {
      CACHE.incidents.put(ci.GetSourceEnvId(), ci);
    }
    //CACHE.incidents.add(ci);
  }

  public static final Optional<IncidentCacheItem> FindIncident(String sourceEnvId) { 
    return Optional.ofNullable(CACHE.incidents.get(sourceEnvId));
    //return CACHE.incidents.stream().filter(i -> i.GetSourceEnvId().equals(sourceEnvId)).findFirst();
  }

  public static final Optional<IncidentCacheItem> FindActiveNonGMSIncident(String sourceEnvId, String env, String unitSourceId) {
    return CACHE.incidents.values().stream().filter(i -> i.GetSourceEnvId().equals(sourceEnvId) == false && i.IsActive() && i.GetSourceEnv().equals(env) && !i.IsFromGMS() && i.IsForUnit(unitSourceId)).findFirst();
  }

  public static final Optional<IncidentCacheItem> FindActiveGMSIncident(String sourceEnvId, String env, String unitSourceId) {
    return CACHE.incidents.values().stream().filter(i -> i.GetSourceEnvId().equals(sourceEnvId) == false && i.IsActive() && i.GetSourceEnv().equals(env) && i.IsFromGMS() && i.IsForUnit(unitSourceId)).findFirst();
  }

  public static final Optional<IncidentCacheItem> FindActiveIncident(String sourceEnv, String unitSourceId) {
    return CACHE.incidents.values().stream().filter(i -> i.GetSourceEnv().equals(sourceEnv) && i.IsActive() && i.IsForUnit(unitSourceId)).findFirst();
  }

  public static final List<Map<String, Object>> GetIncidents(String sourceEnv) {
    return CACHE.incidents.values().stream().filter(i -> i.GetSourceEnv().equals(sourceEnv)).map(i -> i.ConvertToMap()).collect(Collectors.toList());
  }

  public static final List<IncidentCacheItem> GetDirtyIncidents() {
    return CACHE.incidents.values().stream().filter(u -> u.IsDirty()).collect(Collectors.toList());
  }

  public static final List<IncidentCacheItem> GetReadyToCleanupIncidents() {
    return CACHE.incidents.values().stream().filter(u -> u.IsReadyForCleanup()).collect(Collectors.toList());
  }

  public static final void UpdateIncident(String sourceEnvId, IncidentCacheItem ci) {
    synchronized(CACHE.incidents) {
      CACHE.incidents.put(sourceEnvId, ci);
    }
    /*Optional<IncidentCacheItem> oldCi = CACHE.FindIncident(sourceEnvId);

    if (oldCi.isPresent()) {
      Integer index = CACHE.incidents.indexOf(oldCi.get());
      CACHE.incidents.set(index, ci);
    }*/
  }

  public static final void SaveIncidents() throws SQLException, NamingException {
    for (IncidentCacheItem ci : CACHE.GetDirtyIncidents()) {
      ci.SaveToDb();
      ci.Save();
      CACHE.UpdateIncident(ci.GetSourceEnvId(), ci);
    }
  }

  public static final void CleanupIncidents() throws SQLException, NamingException {
    for (IncidentCacheItem ci : CACHE.GetReadyToCleanupIncidents()) {
      ci.RemoveFromDb();
      synchronized(CACHE.incidents) {
        CACHE.incidents.remove(ci.GetSourceEnvId());
      }
    }
  }
  //#endregion

}
