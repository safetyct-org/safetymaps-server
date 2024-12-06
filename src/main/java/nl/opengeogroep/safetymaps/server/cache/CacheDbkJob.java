package nl.opengeogroep.safetymaps.server.cache;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

public class CacheDbkJob implements Job {
    private static final Log LOG = LogFactory.getLog(CacheDbkJob.class);
  
  @Override
  public void execute(JobExecutionContext jec) throws JobExecutionException {
    try {
      CACHE.ReInitDbks();
    } catch (Exception e) {
      LOG.error("Error while executing CacheDbkJob: ", e);
    }
  }
}
