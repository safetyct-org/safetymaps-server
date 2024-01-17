package nl.opengeogroep.safetymaps.server.cache;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

public class CacheCleanJob implements Job {
  private static final Log LOG = LogFactory.getLog(CacheCleanJob.class);
  
  @Override
  public void execute(JobExecutionContext jec) throws JobExecutionException {
    try {
      CACHE.CleanupIncidents();
    } catch (Exception e) {
      LOG.error("Error while executing CacheCleanJob: ", e);
    }
  }
}
