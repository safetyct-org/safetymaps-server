package nl.opengeogroep.safetymaps.server.cache;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.naming.NamingException;

import org.apache.commons.dbutils.handlers.MapListHandler;

import nl.opengeogroep.safetymaps.server.db.DB;

public class CACHE {

  private static Date incidentCacheInitialized = null;
  private static Date unitCacheInitialized = null;
  private static Date roadAttentionCacheInitialized = null;
  private static Date authCacheInitialized = null;

  private static final ArrayList<RoadAttentionCacheItem> roadAttentions = new ArrayList<RoadAttentionCacheItem>();
  private static final ArrayList<UnitCacheItem> units = new ArrayList<UnitCacheItem>();
  private static final ArrayList<IncidentCacheItem> incidents = new ArrayList<IncidentCacheItem>();
  private static final ArrayList<AuthCacheItem> auths = new ArrayList<AuthCacheItem>();
  private static final ArrayList<AuthIncLocCacheItem> authIncLocs = new ArrayList<AuthIncLocCacheItem>();

  private static final Map<Integer, String> unitStatusList = new HashMap<Integer, String>();
  public static List<Map<String,Object>> bag = new ArrayList<Map<String,Object>>();

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
        (String)dbItem.get("locs"),
        authIncLocs
      );

      ci.Save();
      CACHE.AddAuth(ci);
    }
    
    CACHE.authCacheInitialized = new Date();
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
        (Integer)dbItem.get("number")
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
        (String)dbItem.get("post")
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
    return CACHE.roadAttentions.stream().filter(ra -> ra.GetSourceEnvId().equals(sourceEnvId)).findFirst();
  }

  public static final void AddRoadAttention(RoadAttentionCacheItem raci) {
    CACHE.roadAttentions.add(raci);
  }

  public static final void UpdateRoadAttention(String sourceEnvId, RoadAttentionCacheItem raci) {
    Optional<RoadAttentionCacheItem> oldRaci = CACHE.FindRoadAttention(sourceEnvId);

    if (oldRaci.isPresent()) {
      Integer index = CACHE.roadAttentions.indexOf(oldRaci.get());
      CACHE.roadAttentions.set(index, raci);
    }
  }

  public static final List<RoadAttentionCacheItem> GetReadyToCleanupRoadAttentions() {
    return CACHE.roadAttentions.stream().filter(ci -> ci.IsReadyForCleanup()).collect(Collectors.toList());
  }

  public static final List<RoadAttentionCacheItem> GetDirtyRoadAttentions() {
    return CACHE.roadAttentions.stream().filter(ci -> ci.IsDirty()).collect(Collectors.toList());
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
      CACHE.roadAttentions.remove(ci);
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
      CACHE.auths.set(index, aci);
    }
  }

  public static final void AddAuth(AuthCacheItem aci) {
    CACHE.auths.add(aci);
  }

  public static final ArrayList<AuthCacheItem> GetAllAuths() {
    return CACHE.auths;
  }
  //#endregion

  //#region UNITS 
  public static final Map<Integer, String> GetUnitStatusList() throws SQLException, NamingException { 
    if (CACHE.unitStatusList.size() == 0) {
      List<Map<String, Object>> dbList = DB.qr().query("select * from safetymaps.mdstatusses", new MapListHandler());
      for (Map<String, Object> dbItem : dbList) {
        CACHE.unitStatusList.put((Integer)dbItem.get("gmsstatuscode"), (String)dbItem.get("gmsstatustext"));
      }
    }

    return CACHE.unitStatusList; 
  }

  public static final ArrayList<UnitCacheItem> GetAllUnits() { return CACHE.units; }
  public static final void AddUnit(UnitCacheItem ci) {
    CACHE.units.add(ci);
  }

  public static final Optional<UnitCacheItem> FindUnit(String sourceEnvId) { 
    return CACHE.units.stream().filter(u -> u.GetSourceEnvId().equals(sourceEnvId)).findFirst();
  }

  public static final List<Map<String, Object>> GetUnits(String sourceEnv) {
    return CACHE.units.stream().filter(u -> u.GetSourceEnv().equals(sourceEnv)).map(u -> u.ConvertToMap()).collect(Collectors.toList());
  }

  public static final List<UnitCacheItem> GetDirtyUnits() {
    return CACHE.units.stream().filter(u -> u.IsDirty()).collect(Collectors.toList());
  }

  public static final void UpdateUnit(String sourceEnvId, UnitCacheItem ci) {
    Optional<UnitCacheItem> oldCi = CACHE.FindUnit(sourceEnvId);

    if (oldCi.isPresent()) {
      Integer index = CACHE.units.indexOf(oldCi.get());
      CACHE.units.set(index, ci);
    }
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
  public static final ArrayList<IncidentCacheItem> GetAllIncidents() { return CACHE.incidents; }
  public static final void AddIncident(IncidentCacheItem ci) {
    CACHE.incidents.add(ci);
  }

  public static final Optional<IncidentCacheItem> FindIncident(String sourceEnvId) { 
    return CACHE.incidents.stream().filter(i -> i.GetSourceEnvId().equals(sourceEnvId)).findFirst();
  }

  public static final Optional<IncidentCacheItem> FindActiveNonGMSIncident(String sourceEnvId, String env, String unitSourceId) {
    return CACHE.incidents.stream().filter(i -> i.GetSourceEnvId().equals(sourceEnvId) == false && i.IsActive() && i.GetSourceEnv().equals(env) && !i.IsFromGMS() && i.IsForUnit(unitSourceId)).findFirst();
  }

  public static final Optional<IncidentCacheItem> FindActiveGMSIncident(String sourceEnvId, String env, String unitSourceId) {
    return CACHE.incidents.stream().filter(i -> i.GetSourceEnvId().equals(sourceEnvId) == false && i.IsActive() && i.GetSourceEnv().equals(env) && i.IsFromGMS() && i.IsForUnit(unitSourceId)).findFirst();
  }

  public static final Optional<IncidentCacheItem> FindActiveIncident(String sourceEnv, String unitSourceId) {
    return CACHE.incidents.stream().filter(i -> i.GetSourceEnv().equals(sourceEnv) && i.IsActive() && i.IsForUnit(unitSourceId)).findFirst();
  }

  public static final List<Map<String, Object>> GetIncidents(String sourceEnv) {
    return CACHE.incidents.stream().filter(i -> i.GetSourceEnv().equals(sourceEnv)).map(i -> i.ConvertToMap()).collect(Collectors.toList());
  }

  public static final List<IncidentCacheItem> GetDirtyIncidents() {
    return CACHE.incidents.stream().filter(u -> u.IsDirty()).collect(Collectors.toList());
  }

  public static final List<IncidentCacheItem> GetReadyToCleanupIncidents() {
    return CACHE.incidents.stream().filter(u -> u.IsReadyForCleanup()).collect(Collectors.toList());
  }

  public static final void UpdateIncident(String sourceEnvId, IncidentCacheItem ci) {
    Optional<IncidentCacheItem> oldCi = CACHE.FindIncident(sourceEnvId);

    if (oldCi.isPresent()) {
      Integer index = CACHE.incidents.indexOf(oldCi.get());
      CACHE.incidents.set(index, ci);
    }
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
      CACHE.incidents.remove(ci);
    }
  }
  //#endregion

}
