package nl.opengeogroep.safetymaps.utils;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class CacheUtil {
  private static Map<String, Object> cache = new ConcurrentHashMap<>();

  public static final String INCIDENT_CACHE_KEY = "incident";
  
  public static final void AddOrReplace(String key, Object value) {
    Object cachedItem = cache.get(key);
    if (cachedItem != null) {
      cache.replace(key, value);
    } else {
      cache.put(key, value);
    }
  }

  public static final Object Get(String key) {
    return cache.get(key);
  }
}
