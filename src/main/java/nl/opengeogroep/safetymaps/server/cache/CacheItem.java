package nl.opengeogroep.safetymaps.server.cache;

import java.util.Date;

public class CacheItem {
  private Date updated;
  private Boolean dirty;

  protected String source;
  protected String sourceEnv;
  protected String sourceId;
  protected String sourceEnvId;

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
    Date outDated = new Date(now.getTime() - (minutes * 1000 * 60));
    return this.updated.before(outDated);
  }

  public String GetSourceEnvId() { return this.sourceEnvId; }
  public String GetSourceEnv() { return this.sourceEnv; }
}
