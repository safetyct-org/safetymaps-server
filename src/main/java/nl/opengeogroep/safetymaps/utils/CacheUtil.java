package nl.opengeogroep.safetymaps.utils;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class CacheUtil {
  private static Map<String, Object> cache = new ConcurrentHashMap<>();

  public static final String INCIDENT_PROD_CACHE_KEY = "incident-prod";
  public static final String INCIDENT_OPL_CACHE_KEY = "incident-opl";
  public static final String INCIDENT_TEST_CACHE_KEY = "incident-test";
  
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

  public static final void Remove(String key) {
    Object cachedItem = cache.get(key);
    if (cachedItem != null) {
      cache.remove(key);
    }
  }
}
