package nl.opengeogroep.safetymaps.server.cache;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

public class CacheSaveJob implements Job {
  private static final Log LOG = LogFactory.getLog(CacheSaveJob.class);
  
  @Override
  public void execute(JobExecutionContext jec) throws JobExecutionException {
    try {
      CACHE.SaveRoadAttentions();
      CACHE.SaveIncidents();
      CACHE.SaveUnits();
    } catch (Exception e) {
      LOG.error("Error while executing CacheSaveJob: ", e);
    }
  }
}
