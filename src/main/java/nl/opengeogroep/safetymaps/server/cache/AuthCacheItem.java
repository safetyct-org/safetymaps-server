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
  private String funcs;
  private String chars;
  private String kvts;
  private String locs;
  private List<AuthIncLocCacheItem> incLocs;

  public AuthCacheItem(String roles, String mcs, String funcs, String chars, String kvts, String locs, List<AuthIncLocCacheItem> allIncLocs) {
    this.roles = roles;
    this.mcs = mcs;
    this.funcs = funcs;
    this.chars = chars;
    this.kvts = kvts;
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
    cacheObject.put("funcs", this.funcs);
    cacheObject.put("chars", this.chars);
    cacheObject.put("kvts", this.kvts);
    cacheObject.put("locs", this.locs);

    return cacheObject;
  }

  public Boolean HasMcs() { return this.mcs != null && this.mcs.length() > 0; }
  public Boolean ContainsMc(String mc) {
    return this.mcs.contains(mc.toLowerCase());
  }
  public Boolean HasFuncs() { return this.funcs != null && this.funcs.length() > 0; }
  public Boolean ContainsFunc(String fn) {
    return this.funcs.contains(fn.toLowerCase());
  }
  public Boolean HasChars() { return this.chars != null && this.chars.length() > 0; }
    public Boolean ContainsChar(String name, String value) {
    return this.chars.contains("[" + name.toLowerCase() + ":" + value.toLowerCase() + "]") || this.chars.contains("[" + name.toLowerCase() + "]");
  }

  public Boolean HasKvts() { return this.kvts != null && this.kvts.length() > 0; }
  public Boolean ContainsKvt(String kvt) {
    return this.kvts.contains(kvt.toLowerCase());
  }

  public String[] GetRolesArray() {
    if (this.roles != null && this.roles.length() > 0) {
      return this.roles.split(",");
    } else {
      return "".split(",");
    }
  }

  public void UpdateAuth(String mcs, String funcs, String chars, String kvts, String locs) {
    this.mcs = mcs.toLowerCase();
    this.funcs = funcs.toLowerCase();
    this.chars = chars.toLowerCase();
    this.kvts = kvts.toLowerCase();
    this.locs = locs;
  }

  public void SaveToDb() throws SQLException, NamingException {
    DB.qr().update("INSERT INTO safetymaps.incidentauthorization(role, mcs, funcs, chars, kvts, locs) VALUES(?, ?, ?, ?, ?, ?) ON CONFLICT (role) SET mcs = ?, funcs = ?, chars = ?, kvts = ?, locs = ?", this.roles, this.mcs, this.funcs, this.chars, this.kvts, this.locs, this.mcs, this.funcs, this.chars, this.kvts, this.locs);
  }
}
