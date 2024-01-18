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

  public static class CacheItem {
    private Date updated;
    private Boolean dirty;

    public void Renew() {
      this.updated = new Date();
      this.dirty = true;
    }

    public Boolean IsDirty() { return this.dirty; }
    public void Save() {
      this.dirty = false;
    }

    public Boolean IsExpiredAfter(Integer minutes) {
      Date now = new Date();
      Date outDated = new Date(now.getTime() - minutes * 1000 * 60);
      return this.updated.before(outDated);
    }
  }

  public static class IncidentCacheItem extends CacheItem {
    private String source;
    private String sourceEnv;
    private String sourceId;
    private String sourceEnvId;
    private String notes;
    private String units;
    private String location;
    private String discipline;
    private String status;
    private String sender;
    private String characts;
    private String tenantid;
    private String talkinggroups;
    private Integer number;

    public IncidentCacheItem(String source,
      String sourceEnv,
      String sourceId,
      String sourceEnvId,
      String notes,
      String units,
      String location,
      String discipline,
      String status,
      String sender,
      String characts,
      String tenantid,
      String talkinggroups,
      Integer number
    ) {
      this.source = source;
      this.sourceEnv = sourceEnv;
      this.sourceId = sourceId;
      this.sourceEnvId = sourceEnvId;
      this.notes = notes;
      this.units = units;
      this.location = location;
      this.discipline = discipline;
      this.status = status;
      this.sender = sender;
      this.characts = characts;
      this.tenantid = tenantid;
      this.talkinggroups = talkinggroups;
      this.number = number;

      this.Renew();
    }

    public void UpdateIncident(String notes,
      String units,
      String location,
      String discipline,
      String status,
      String characts,
      String talkinggroups
    ) {
      if (notes != null) this.notes = notes;
      if (units != null) this.units = units;
      if (location != null) this.location = location;
      if (discipline != null) this.discipline = discipline;
      if (status != null) this.status = status;
      if (characts != null) this.characts = characts;
      if (talkinggroups != null) this.talkinggroups = talkinggroups;

      this.Renew();
    }

    public Map<String, Object> ConvertToMap() {
      Map<String, Object> cacheObject = new HashMap<String, Object>();

      cacheObject.put("source", this.source);
      cacheObject.put("sourceenv", this.sourceEnv);
      cacheObject.put("sourceid", this.sourceId);
      cacheObject.put("sourceenvid", this.sourceEnvId);
      cacheObject.put("notes", this.notes);
      cacheObject.put("units", this.units);
      cacheObject.put("location", this.location);
      cacheObject.put("discipline", this.discipline);
      cacheObject.put("status", this.status);
      cacheObject.put("sender", this.sender);
      cacheObject.put("characts", this.characts);
      cacheObject.put("tenantid", this.tenantid);
      cacheObject.put("talkinggroups", this.talkinggroups);
      cacheObject.put("number", this.number);

      return cacheObject;
    }

    public void SaveToDb() throws SQLException, NamingException {
      DB.qr().update("INSERT INTO safetymaps.incidents " +
        "(source, sourceEnv, sourceId, sourceEnvId, status, sender, number, notes, units, characts, location, discipline, tenantid, talkinggroups) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
        " ON CONFLICT (sourceEnvId) DO UPDATE SET status = ?, notes = ?, units = ?, characts = ?, location = ?, discipline = ?, tenantId = ?, talkinggroups = ?", 
      source, sourceEnv, sourceId, sourceEnvId, status, sender, number, notes, units, characts, location, discipline, tenantid, talkinggroups, status, notes, units, characts, location, discipline, tenantid, talkinggroups);
    }

    public void RemoveFromDb() throws SQLException, NamingException {
      DB.qr().update("DELETE FROM safetymaps.incidents WHERE source = ? and sourceEnvId = ?", source, sourceEnvId);
    }

    public String GetSourceEnvId() { return this.sourceEnvId; }
    public String GetSourceEnv() { return this.sourceEnv; }

    public Boolean IsReadyForCleanup() {
      return !IsDirty() && IsExpiredAfter(1 * 60 * 24 * 5);
    }
  }

  public static class UnitCacheItem extends CacheItem {
    private String source;
    private String sourceEnv;
    private String sourceId;
    private String sourceEnvId;
    private Integer gmsstatuscode;
    private String sender;
    private String primairevoertuigsoort;
    private BigDecimal lon;
    private BigDecimal lat;
    private Integer speed;
    private Integer heading;
    private Integer eta;
    private String abbs;
    private String post;

    public UnitCacheItem(String source, 
      String sourceEnv, 
      String sourceId, 
      String sourceEnvId,
      Integer gmsstatuscode,
      String sender,
      String primairevoertuigsoort,
      String abbs,
      String post
    ) {
      this.source = source;
      this.sourceEnv = sourceEnv;
      this.sourceId = sourceId;
      this.sourceEnvId = sourceEnvId;
      this.gmsstatuscode = gmsstatuscode;
      this.sender = sender;
      this.primairevoertuigsoort = primairevoertuigsoort;
      this.abbs = abbs;
      this.post = post;

      this.Renew();
    }

    public void UpdateUnit(Integer gmsstatuscode, String primairevoertuigsoort, String sender, String post, String abbs) {
      this.gmsstatuscode = gmsstatuscode;
      this.sender = sender;
      if (primairevoertuigsoort != null) this.primairevoertuigsoort = primairevoertuigsoort;
      if (post != null) this.post = post;
      if (abbs != null) this.abbs = abbs;

      this.Renew();
    }

    public void UpdateLocation(BigDecimal lon,
      BigDecimal lat,
      Integer speed,
      Integer heading,
      Integer eta
    ) {
      this.lon = lon;
      this.lat = lat;
      this.speed = speed;
      this.heading = heading;
      this.eta = eta;

      this.Renew();
    }

    public Map<String, Object> ConvertToMap() {
      Map<String, Object> cacheObject = new HashMap<String, Object>();

      cacheObject.put("source", this.source);
      cacheObject.put("sourceenv", this.sourceEnv);
      cacheObject.put("sourceid", this.sourceId);
      cacheObject.put("sourceenvid", this.sourceEnvId);
      cacheObject.put("gmsstatuscode", this.gmsstatuscode);
      cacheObject.put("sender", this.sender);
      cacheObject.put("primairevoertuigsoort", this.primairevoertuigsoort);
      cacheObject.put("lon", this.lon);
      cacheObject.put("lat", this.lat);
      cacheObject.put("speed", this.speed);
      cacheObject.put("heading", this.heading);
      cacheObject.put("eta", this.eta);
      cacheObject.put("abbs", this.abbs);
      cacheObject.put("post", this.post);

      return cacheObject;
    }

    public void SaveToDb() throws SQLException, NamingException {
      DB.qr().update("INSERT INTO safetymaps.units " +
        "(source, sourceEnv, sourceId, sourceEnvId, gmsstatuscode, sender, primairevoertuigsoort, abbs, post, lon, lat, speed, heading, eta) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
        " ON CONFLICT (sourceEnvId) DO UPDATE SET gmsstatuscode = ?, primairevoertuigsoort = ?, abbs = ?, post = ?, lon = ?, lat = ?, speed = ?, heading = ?, eta = ?",
      source, sourceEnv, sourceId, sourceEnvId, gmsstatuscode, sender, primairevoertuigsoort, abbs, post, lon, lat, speed, heading, eta, gmsstatuscode, primairevoertuigsoort, abbs, post, lon, lat, speed, heading, eta);
    }

    public void RemoveFromDb() throws SQLException, NamingException {
      DB.qr().update("DELETE FROM safetymaps.units WHERE source = ? and sourceEnvId = ?", source, sourceEnvId);
    }

    public Boolean IsReadyForCleanup() {
      return false;
    }

    public String GetSourceEnvId() { return this.sourceEnvId; }
    public String GetSourceEnv() { return this.sourceEnv; }
  }

  private static Date incidentCacheInitialized = null;
  private static Date unitCacheInitialized = null;
  private static final ArrayList<UnitCacheItem> units = new ArrayList<UnitCacheItem>();
  private static final ArrayList<IncidentCacheItem> incidents = new ArrayList<IncidentCacheItem>();
  private static final Map<Integer, String> unitStatusList = new HashMap<Integer, String>();

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
        (String)dbItem.get("abbssender"), 
        (String)dbItem.get("post")
      );
      ci.UpdateLocation(
        (BigDecimal)dbItem.get("lon"),
        (BigDecimal)dbItem.get("lat"),
        (Integer)dbItem.get("speed"),
        (Integer)dbItem.get("heading"),
        (Integer)dbItem.get("eta")
      );

      CACHE.AddUnit(ci);
    }

    CACHE.unitCacheInitialized = new Date();
  }

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

  public static final ArrayList<IncidentCacheItem> GetAllIncidents() { return CACHE.incidents; }
  public static final void AddIncident(IncidentCacheItem ci) {
    CACHE.incidents.add(ci);
  }

  public static final Optional<IncidentCacheItem> FindIncident(String sourceEnvId) { 
    return CACHE.incidents.stream().filter(i -> i.GetSourceEnvId().equals(sourceEnvId)).findFirst();
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

  public static final void SaveUnits() throws SQLException, NamingException {
    for (UnitCacheItem ci : CACHE.GetDirtyUnits()) {
      ci.SaveToDb();
      ci.Save();
      CACHE.UpdateUnit(ci.GetSourceEnvId(), ci);
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
}
