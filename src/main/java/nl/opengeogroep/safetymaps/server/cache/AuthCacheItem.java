package nl.opengeogroep.safetymaps.server.cache;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.naming.NamingException;

import org.json.JSONArray;
import org.json.JSONObject;

import nl.opengeogroep.safetymaps.server.db.DB;

public class AuthCacheItem extends CacheItem {
  private String roles;
  private String mcs;
  private String locs;
  private List<AuthIncLocCacheItem> incLocs;

  public AuthCacheItem(String roles, String mcs, String locs, ArrayList<AuthIncLocCacheItem> allIncLocs) {
    this.roles = roles;
    this.mcs = mcs.toLowerCase();
    this.locs = locs;

    this.incLocs = allIncLocs.stream().filter(il -> Arrays.stream(locs.split(",")).filter(l -> l.equals(il.GetIdString())).count() > 0).collect(Collectors.toList());

    this.Renew();
  }

  public String GetRoles() {
    return this.roles;
  }

  public Boolean HasLocs() { return this.locs != null && this.locs.length() > 0; }
  public List<AuthIncLocCacheItem> GetIncLocs() {
    return this.incLocs;
  }

  public Map<String, Object> ConvertToMap() {
    Map<String, Object> cacheObject = new HashMap<String, Object>();

    cacheObject.put("role", this.roles);
    cacheObject.put("mcs", this.mcs);
    cacheObject.put("locs", this.locs);

    return cacheObject;
  }

  public Boolean HasMcs() { return this.mcs != null && this.mcs.length() > 0; }
  public Boolean ContainsMc(String mc) {
    return this.mcs.contains(mc.toLowerCase());
  }

  public String[] GetRolesArray() {
    if (this.roles != null && this.roles.length() > 0) {
      return this.roles.split(",");
    } else {
      return "".split(",");
    }
  }

  public void UpdateAuth(String mcs, String locs) {
    this.mcs = mcs;
    this.locs = locs;
  }

  public void SaveToDb() throws SQLException, NamingException {
    DB.qr().update("INSERT INTO safetymaps.incidentauthorization(role, mcs, locs) VALUES(?, ?, ?) ON CONFLICT (role) SET mcs = ?, locs = ?", this.roles, this.mcs, this.locs, this.mcs, this.locs);
  }
}
