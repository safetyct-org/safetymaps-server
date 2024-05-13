package nl.opengeogroep.safetymaps.server.cache;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

import javax.naming.NamingException;

import org.json.JSONArray;
import org.json.JSONObject;

import nl.opengeogroep.safetymaps.server.db.DB;

public class IncidentCacheItem extends CacheItem {
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

  public boolean IsFromGMS() {
    return !sourceId.startsWith("FLK") && !sourceId.startsWith("DCU");
  }

  public boolean IsForUnit(String unitSourceId) {
    JSONArray units = this.units != null ? new JSONArray(this.units) : new JSONArray();
    boolean isForUnit = false;
    for(int i=0; i<units.length(); i++) {
      JSONObject unit = (JSONObject)units.get(i);
      if (unit.has("roepnaam") && unitSourceId.equals(unit.getString("roepnaam")) && !unit.has("eindeActieDtg")) {
        isForUnit = true;
      }
    }
    return isForUnit;
  }

  public void RemoveUnit(String unitSourceId) {
    JSONArray units = this.units != null ? new JSONArray(this.units) : new JSONArray();
    JSONArray updatedUnits = new JSONArray();

    for(int i=0; i<units.length(); i++) {
      JSONObject unit = (JSONObject)units.get(i);
      if (unit.has("roepnaam") && unitSourceId.equals(unit.getString("roepnaam")) && !unit.has("eindeActieDtg")) {
        // Do nothing
      } else {
        updatedUnits.put(unit);
      }
    }

    this.units = updatedUnits.toString();
    
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

  public Boolean IsActive() { return this.status.equals("operationeel"); }

  public Boolean IsReadyForCleanup() {
    return !IsDirty() && IsExpiredAfter(1 * 60 * 24 * 5) && !status.equals("operationeel");
  }
}
