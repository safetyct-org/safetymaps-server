package nl.opengeogroep.safetymaps.server.cache;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

import javax.naming.NamingException;

import nl.opengeogroep.safetymaps.server.db.DB;

public class UnitCacheItem extends CacheItem {
  private String source;
  private String sourceEnv;
  private String sourceId;
  private String sourceEnvId;
  private Integer gmsstatuscode;
  private String sender;
  private String primairevoertuigsoort;
  private Double lon;
  private Double lat;
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

  public void UpdateLocation(Double lon,
    Double lat,
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
      "(source, sourceEnv, sourceId, sourceEnvId, gmsstatuscode, sender, primairevoertuigsoort, abbs, post, lon, lat, speed, heading, eta, geom) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ST_SetSRID(ST_MakePoint(?, ?), 4326)) " +
      " ON CONFLICT (sourceEnvId) DO UPDATE SET gmsstatuscode = ?, primairevoertuigsoort = ?, abbs = ?, post = ?, lon = ?, lat = ?, speed = ?, heading = ?, eta = ?, geom = ST_SetSRID(ST_MakePoint(?, ?), 4326)",
    source, sourceEnv, sourceId, sourceEnvId, gmsstatuscode, sender, primairevoertuigsoort, abbs, post, lon, lat, speed, heading, eta, lon, lat, gmsstatuscode, primairevoertuigsoort, abbs, post, lon, lat, speed, heading, eta, lon, lat);
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
