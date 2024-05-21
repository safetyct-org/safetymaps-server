package nl.opengeogroep.safetymaps.server.cache;

import java.sql.SQLException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import javax.naming.NamingException;

import org.json.JSONObject;

import nl.opengeogroep.safetymaps.server.db.DB;

public class RoadAttentionCacheItem extends CacheItem {
  private String tenantId;
  private String kindOfAttention;
  private String attention;
  private Date beginDate;
  private Date endDate;
  private String geoLocation;

  public RoadAttentionCacheItem(String source,
    String sourceEnv, 
    String sourceId, 
    String sourceEnvId,
    String tenantId,
    String kindOfAttention,
    String attention,
    Date beginDate,
    Date endDate,
    String geoLocation
  ) {
    this.source = source;
    this.sourceEnv = sourceEnv;
    this.sourceId = sourceId;
    this.sourceEnvId = sourceEnvId;
    this.tenantId = tenantId;
    this.kindOfAttention = kindOfAttention;
    this.attention = attention;
    this.beginDate = beginDate;
    this.endDate = endDate;
    this.geoLocation = geoLocation;

    this.Renew();
  }

  public void Update(String kindOfAttention,
    String attention,
    Date beginDate,
    Date endDate,
    String geoLocation
  ) {
    this.attention = attention;
    this.beginDate = beginDate;
    this.endDate = endDate;
    this.geoLocation = geoLocation;

    this.Renew();
  }

  public Map<String, Object> ConvertToMap() {
    Map<String, Object> cacheObject = new HashMap<String, Object>();

    cacheObject.put("source", this.source);
    cacheObject.put("sourceenv", this.sourceEnv);
    cacheObject.put("sourceid", this.sourceId);
    cacheObject.put("sourceenvid", this.sourceEnvId);
    cacheObject.put("tenantId", this.tenantId);
    cacheObject.put("kindOfAttention", this.kindOfAttention);
    cacheObject.put("attention", this.attention);
    cacheObject.put("beginDate", this.beginDate);
    cacheObject.put("endDate", this.endDate);
    cacheObject.put("geoLocation", this.geoLocation);

    return cacheObject;
  }

  public void SaveToDb() throws SQLException, NamingException {
    JSONObject geom = new JSONObject(this.geoLocation);
    Double lon = geom.has("lon") && geom.get("lon").toString() != "null" ? geom.getDouble("lon") : 0;
    Double lat = geom.has("lat") && geom.get("lat").toString() != "null" ? geom.getDouble("lat") : 0;

    DB.qr().update("INSERT INTO safetymaps.roadattentions " +
      "(source, sourceEnv, sourceId, sourceEnvId, tenantId, kindOfAttention, attention, beginDate, endDate, geom) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ST_SetSRID(ST_MakePoint(?, ?), 4326)) " +
      "ON CONFLICT (sourceEnvId) DO UPDATE SET attention = ?, beginDate = ?, endDate = ?, geom = ST_SetSRID(ST_MakePoint(?, ?), 4326))"
    , source, sourceEnv, sourceId, sourceEnvId, tenantId, kindOfAttention, attention, beginDate, endDate, lon, lat, attention, beginDate, endDate, lon, lat);
  }

  public void RemoveFromDb() throws SQLException, NamingException {
    DB.qr().update("DELETE FROM safetymaps.roadattentions WHERE source = ? and sourceEnvId = ?", source, sourceEnvId);
  }
}
