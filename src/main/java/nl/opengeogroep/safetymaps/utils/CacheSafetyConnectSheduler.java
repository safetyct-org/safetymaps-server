package nl.opengeogroep.safetymaps.utils;

import nl.opengeogroep.safetymaps.server.db.Cfg;
import nl.opengeogroep.safetymaps.utils.CacheUtil;

import java.io.IOException;
import java.text.ParseException;
import java.util.Properties;
import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

import org.apache.commons.io.IOUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.HttpResponse;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.json.JSONArray;
import org.json.JSONException;
import org.quartz.CronExpression;
import org.quartz.CronScheduleBuilder;
import org.quartz.Job;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.quartz.impl.StdSchedulerFactory;

/**
 * Cache SafetyConnect requests for incidents and units
 * so proxying request from viewers isnt needed anymore
 * and request from viewers returns cached informaton
 * 
 * @author Bart Verhaar (Safety C&T)
 */

public class CacheSafetyConnectSheduler implements ServletContextListener {
  private static final String INCIDENT_JOB_ID = "GetIncidents";

  private static final Log log = LogFactory.getLog(CacheSafetyConnectSheduler.class);
  private ServletContext context;

  private static Scheduler incidentScheduler = null;

  public static class GetIncidentsJob implements Job {
    String authorization;
    String url;
    String regioncode;

    public GetIncidentsJob() {
      // Try get config values
      try {
        authorization = Cfg.getSetting("safetyconnect_webservice_authorization");
        url = Cfg.getSetting("safetyconnect_webservice_url");
        regioncode = Cfg.getSetting("safetyconnect_regio_code");
      } catch (Exception e) {
        log.error("Exception getting SETTINGS from DB:", e);
      }
    }

    @Override
    public void execute(JobExecutionContext jec) throws JobExecutionException {
      // Stop if no config values
      if (authorization == null || url == null) {
        return;
      }
      // Build request
      final String uri = url + "/incident?extended=true&kladblokregels=true" + (regioncode == null ? "" : "&regioCode=" + regioncode);
      final HttpUriRequest req = RequestBuilder.get()
        .setUri(uri)
        .addHeader("Authorization", authorization)
        .build();
      // Try request and cache response
      try(CloseableHttpClient client = HttpClients.createDefault()) {
        // Try request
        final String response = client.execute(req, new ResponseHandler<String>() {
          @Override
          public String handleResponse(HttpResponse hr) {
            try {
              return IOUtils.toString(hr.getEntity().getContent(), "UTF-8");
            } catch(IOException e) {
              log.error("Exception reading HTTP response:", e);
              return null;
            }
          }
        });
        // Try convert HTTP response to JSON
        JSONArray responseJSON = null;
        try {
          responseJSON = new JSONArray(response);
        } catch(JSONException e) {
          log.error("Exception reading JSON from HTTP response:", e);
        }
        // Cache me if you can
        if (responseJSON != null) {
          CacheUtil.AddOrReplace(CacheUtil.INCIDENT_CACHE_KEY, responseJSON);
        }
      } catch(Exception e) {
        log.error("Exception caching HTTP response:", e);
      }
    }

  }

  @Override
  public void contextInitialized(ServletContextEvent sce) {
    this.context = sce.getServletContext();

    try {
      Scheduler incidentScheduler = getIncidentInstance();
      JobDetail incidentJob = JobBuilder.newJob(GetIncidentsJob.class)
        .withIdentity(INCIDENT_JOB_ID)
        .withDescription("Get incidents from SafetyConnect each x seconds")
        .build();
      Trigger incidentTrigger = CreateTrigger("0/5 0 0 ? * * *");

      incidentScheduler.scheduleJob(incidentJob, incidentTrigger);

      log.info("Safetyconnect jobs created");
    } catch (Exception e) {
      log.error("Error creating Safetyconnect jobs:", e);
    }
  }

  private Trigger CreateTrigger(String cronExpression) throws ParseException {
    // Make a trigger for the job, every 5 seconds
    CronExpression c = new CronExpression(cronExpression);
    CronScheduleBuilder cronSchedule = CronScheduleBuilder.cronSchedule(c);

    Trigger trigger = TriggerBuilder.newTrigger()
      .withIdentity(INCIDENT_JOB_ID + "Trigger")
      .startNow()
      .withSchedule(cronSchedule)
      .build();

    return trigger;
  }

  @Override
  public void contextDestroyed(ServletContextEvent sce) {
    if(incidentScheduler != null) {
      try {
        incidentScheduler.shutdown(true);
        log.debug("Schedulers stopped");
      } catch (SchedulerException ex) {
        log.error("Cannot shutdown quartz schedulers", ex);
      }
    }
  }

  public static Scheduler getIncidentInstance() throws SchedulerException {
    if(incidentScheduler == null) {
      try {
        Properties props = new Properties();
        props.put("org.quartz.scheduler.instanceName", INCIDENT_JOB_ID + "Scheduler");
        props.put("org.quartz.threadPool.threadCount", "1");
        props.put("org.quartz.scheduler.interruptJobsOnShutdownWithWait", "true");
        props.put("org.quartz.jobStore.class", "org.quartz.simpl.RAMJobStore");
        incidentScheduler = new StdSchedulerFactory(props).getScheduler();
        incidentScheduler.start();
        log.info(INCIDENT_JOB_ID + "Scheduler created and started");
      } catch (SchedulerException ex) {
        log.error("Cannot create " + INCIDENT_JOB_ID + "Scheduler", ex);
      }
    }

    return incidentScheduler;
  }
}
