package nl.opengeogroep.safetymaps.utils;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

import org.apache.commons.dbutils.handlers.MapListHandler;
import org.apache.commons.dbutils.handlers.ScalarHandler;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONArray;
import org.json.JSONObject;
import org.quartz.CronExpression;
import org.quartz.CronScheduleBuilder;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.quartz.impl.StdSchedulerFactory;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DeliverCallback;

import nl.opengeogroep.safetymaps.server.cache.CACHE;
import nl.opengeogroep.safetymaps.server.cache.CacheCleanJob;
import nl.opengeogroep.safetymaps.server.cache.CacheSaveJob;
import nl.opengeogroep.safetymaps.server.cache.IncidentCacheItem;
import nl.opengeogroep.safetymaps.server.cache.UnitCacheItem;
import nl.opengeogroep.safetymaps.server.db.Cfg;
import nl.opengeogroep.safetymaps.server.db.DB;

public class SafetyConnectMessageReceiver implements ServletContextListener {
  private static final Log LOG = LogFactory.getLog(SafetyConnectMessageReceiver.class);

  private static ServletContext CONTEXT;
  private static Scheduler SCHEDULER = null;
  private static String RQ_SENDERS;
  private static String RQ_TENANTS;
  private static String RQ_REGIONS;
  private static String RQ_HOST;
  private static String RQ_VHOSTS;
  private static String RQ_USER;
  private static String RQ_PASS;
  private static String RQ_PARAMS;
  private static String RQ_OPTIONAL_ONLY_UNITS;
  private static String RQ_OPTIONAL_NAME_PREFIX;
  
  private static HashMap<String, Connection> RQ_CONNECTIONS = new HashMap<String, Connection>();
  private static HashMap<String, Channel> RQ_CHANNELS = new HashMap<String, Channel>();

  private static final String RQ_MB_INCIDENT_CHANGED = "SafetyConnect.Messages.IncidentChanged:IIncidentChangedEvent";
  private static final String RQ_MB_UNIT_CHANGED = "SafetyConnect.Messages.EenheidChanged:IEenheidChangedEvent";
  private static final String RQ_MB_UNIT_MOVED = "SafetyConnect.Messages.PositionReceived:IPositionReceivedEvent";

  @Override
  public void contextInitialized(ServletContextEvent sce) {
    CONTEXT = sce.getServletContext();
    
    if (isEnabled() == false) { 
      return; 
    }

    try {
      getConfigFromDb();
    } catch (Exception e) {
      LOG.error("Exception while exec 'getConfigFromDb()': ", e);
      return;
    }

    try {
      CACHE.InitializeIncidentCache();
      CACHE.InitializeUnitCache();
      
      LOG.info("Incident- and UnitCache initialized. IncidentCount: " + CACHE.GetAllIncidents().size() + ", UnitCount: " + CACHE.GetAllUnits().size());
    } catch (Exception e) {
      LOG.error("Exception while initializing Incident- and UnitCache: ", e);
    }

    try {
      SCHEDULER = getSchedulerInstance();

      JobDetail cacheCleanJob = JobBuilder.newJob(CacheCleanJob.class)
        .withIdentity("CacheClean job")
        .withDescription("Clean incidents from cache each day at 6")
        .build();
      JobDetail cacheSaveJob = JobBuilder.newJob(CacheSaveJob.class)
        .withIdentity("CacheSave job")
        .withDescription("Save incidents and units from cache into db each 5 minutes")
        .build(); 

      CronExpression ceCacheClean = new CronExpression("0 0 6 * * ?");
      CronExpression ceCacheSave = new CronExpression("0 */5 * * * ?");

      CronScheduleBuilder csCacheClean = CronScheduleBuilder.cronSchedule(ceCacheClean);
      CronScheduleBuilder csCacheSave = CronScheduleBuilder.cronSchedule(ceCacheSave);

      Trigger cacheCleanTrigger = TriggerBuilder.newTrigger()
        .withIdentity("CacheClean trigger")
        .startNow()
        .withSchedule(csCacheClean)
        .build();
      Trigger cacheSaveTrigger = TriggerBuilder.newTrigger()
        .withIdentity("CacheSave trigger")
        .startNow()
        .withSchedule(csCacheSave)
        .build();

        SCHEDULER.scheduleJob(cacheCleanJob, cacheCleanTrigger);
        SCHEDULER.scheduleJob(cacheSaveJob, cacheSaveTrigger);

        LOG.info("Incident- and UnitCache schedule configred.");
    } catch (Exception e) {
      LOG.error("Exception while configuring schedules for Incident- and UnitCache: ", e);
    }

    List<String> vhosts = Arrays.asList(RQ_VHOSTS.split(","));
    List<String> hosts = Arrays.asList(RQ_HOST.split(","));
    List<String> users = Arrays.asList(RQ_USER.split(","));
    List<String> passes = Arrays.asList(RQ_PASS.split(","));

    Boolean onlyUnitSubscription = "true".equals(RQ_OPTIONAL_ONLY_UNITS);

    vhosts.forEach((vhost) -> {
      String matchVhost = "[" + vhost + "]:";
      Optional<String> host = hosts.stream().filter(h -> h.startsWith(matchVhost)).findFirst();
      Optional<String> user = users.stream().filter(u -> u.startsWith(matchVhost)).findFirst();
      Optional<String> pass = passes.stream().filter(p -> p.startsWith(matchVhost)).findFirst();

      try {
        if (host.isPresent() && user.isPresent() && pass.isPresent()) {
          initiateRabbitMqConnection(vhost, host.get().replace(matchVhost, ""), user.get().replace(matchVhost, ""), pass.get().replace(matchVhost, ""));
        } else {
          LOG.error("Missing host/user/pass combination for 'initiateRabbitMqConnection(" + vhost + ")': ");
        }
        LOG.info("SafetyConnectMessageReceiver RabbitMqConnection initialized.");
      } catch (Exception e) {
        LOG.error("Exception while exec 'initiateRabbitMqConnection(" + vhost + ")': ", e);
      }

      if (onlyUnitSubscription == false) {
        try {
          initRabbitMqChannel(vhost, host.get().replace(matchVhost, ""), RQ_MB_INCIDENT_CHANGED, "incident_changed");
          LOG.info("SafetyConnectMessageReceiver RabbitMqChannel('" + vhost + "', '" + RQ_MB_INCIDENT_CHANGED + "') initialized.");
        } catch (Exception e) {
          LOG.error("Exception while exec 'initRabbitMqChannel(" + vhost + ", " + RQ_MB_INCIDENT_CHANGED + ")'", e);
        }
      }

      try {
        initRabbitMqChannel(vhost, host.get().replace(matchVhost, ""), RQ_MB_UNIT_CHANGED, "unit_changed");
        LOG.info("SafetyConnectMessageReceiver RabbitMqChannel('" + vhost + "', '" + RQ_MB_UNIT_CHANGED + "') initialized.");
      } catch (Exception e) {
        LOG.error("Exception while exec 'initRabbitMqChannel(" + vhost + ", " + RQ_MB_UNIT_CHANGED + ")'", e);
      }

      try {
        initRabbitMqChannel(vhost, host.get().replace(matchVhost, ""), RQ_MB_UNIT_MOVED, "unit_moved");
        LOG.info("SafetyConnectMessageReceiver RabbitMqChannel('" + vhost + "', '" + RQ_MB_UNIT_MOVED + "') initialized.");
      } catch (Exception e) {
        LOG.error("Exception while exec 'initRabbitMqChannel(" + vhost + ", " + RQ_MB_UNIT_MOVED + ")'", e);
      }
    });

    LOG.info("SafetyConnectMessageReceiver initialized for all vhosts.");
  }

  @Override
  public void contextDestroyed(ServletContextEvent sce) {
    RQ_CHANNELS.forEach((key, value) -> {
      try { 
        RQ_CHANNELS.get(key).close();
      } catch (Exception e) {
        LOG.error("Exception on 'RQ_CHANNELS.get(" + key + ").close()'", e);
      }
    });

    RQ_CONNECTIONS.forEach((key, value) -> {
      try { 
        RQ_CONNECTIONS.get(key).close();
      } catch (Exception e) {
        LOG.error("Exception on 'RQ_CONNECTIONS.get(" + key + ").close()'", e);
      }
    });

    try {
      DB.qr().update("DELETE FROM safetymaps.rq");
    } catch (Exception e) {
      LOG.error("Exception while cleaning up after contextDestroyed: ", e);
    }
  }

  private static void getConfigFromDb() throws Exception {
    RQ_HOST = Cfg.getSetting("safetyconnect_rq_host");
    RQ_VHOSTS = Cfg.getSetting("safetyconnect_rq_vhost");
    RQ_USER = Cfg.getSetting("safetyconnect_rq_user");
    RQ_PASS = Cfg.getSetting("safetyconnect_rq_pass");
    RQ_SENDERS = Cfg.getSetting("safetyconnect_rq_senders", "");
    RQ_TENANTS = Cfg.getSetting("safetyconnect_rq_tenants", "");
    RQ_PARAMS = Cfg.getSetting("safetyconnect_rq_params", "");
    RQ_REGIONS = Cfg.getSetting("safetyconnect_rq_regios", "");
    RQ_OPTIONAL_ONLY_UNITS = Cfg.getSetting("safetyconnect_rq_optional_only_units", "false");
    RQ_OPTIONAL_NAME_PREFIX = Cfg.getSetting("safetyconnect_rq_optional_name_prefix", "");

    if (RQ_HOST == null || RQ_USER == null || RQ_PASS == null) {
      throw new Exception("One or more required 'safetyconnect_rq' settings are empty.");
    }

    if (RQ_VHOSTS == null) { 
      RQ_VHOSTS = "*"; 
    }
  }

  private static void initiateRabbitMqConnection(String vhost, String host, String user, String pass) throws Exception {
    ConnectionFactory rqConFac;

    rqConFac = new ConnectionFactory();
    if (vhost != "*") {
      rqConFac.setVirtualHost(vhost);
    }
    rqConFac.setHost(host);
    rqConFac.setUsername(user);
    rqConFac.setPassword(pass);

    RQ_CONNECTIONS.put(vhost, rqConFac.newConnection());
  }

  private static void initRabbitMqChannel(String vhost, String host, String rqMb, String internalEvent) throws Exception {    
    Channel channel = RQ_CONNECTIONS.get(vhost).createChannel();
    channel.basicQos(1);
    String queueName = nameQueue(channel, rqMb, internalEvent, vhost, host);
    String channelName = nameChannel(vhost, rqMb);
    DeliverCallback messageHandler = getMessageHandler(vhost, rqMb, channelName);

    channel.basicConsume(queueName, false, messageHandler, consumerTag -> { });

    RQ_CHANNELS.put(channelName, channel);
  }

  private static DeliverCallback getMessageHandler(String vhost, String rqMb, String channelName) {
    return (consumerTag, delivery) -> {
      String msgBody = new String(delivery.getBody(), "UTF-8");

      try {
        switch (rqMb) {
          case RQ_MB_INCIDENT_CHANGED:
            handleIncidentChangedMessage(vhost, msgBody);
            break;
          case RQ_MB_UNIT_CHANGED:
            handleUnitChangedMessage(vhost, msgBody);
            break;
          case RQ_MB_UNIT_MOVED:
            handleUnitMovedMessage(vhost, msgBody);
            break;
          default:
            break;
        }
      } catch(Exception e) {
        LOG.error(e.getMessage());
      } finally {
        RQ_CHANNELS.get(channelName).basicAck(delivery.getEnvelope().getDeliveryTag(), false);
      }
    }; 
  }

  private static void handleUnitMovedMessage(String vhost, String msgBody) {
    JSONObject move = extractObjectFromMessage(msgBody);
    
    String moveId = move.getString("unit");
    String envId = vhost + '-' + moveId;

    try {
      // Is message for me
      if (unitIsForMyRegion(move, Arrays.asList(RQ_REGIONS.split(",")))) {
        Double lon = (Double)move.getDouble("lon");
        Double lat = (Double)move.getDouble("lat");
        Integer speed = move.has("speed") && move.get("speed").toString() != "null" ? move.getInt("speed") : 0;
        Integer heading = move.has("heading") && move.get("heading").toString() != "null" ? move.getInt("heading") : 0;
        Integer eta = move.has("eta") && move.get("eta").toString() != "null" ? move.getInt("eta") : 0;

        /*DB.qr().update(
          "INSERT INTO safetymaps.units (source, sourceEnv, sourceId, sourceEnvId, lon, lat, speed, heading, eta, geom) VALUES ('sc', ?, ?, ?, ?, ?, ?, ?, ?, ST_SetSRID(ST_MakePoint(?, ?), 4326)) ON CONFLICT (sourceEnvId) DO UPDATE SET lon = ?, lat = ?, speed = ?, heading = ?, eta = ?, geom = ST_SetSRID(ST_MakePoint(?, ?), 4326)",
          vhost, moveId, envId, lon, lat, speed, heading, eta, lon, lat, lon, lat, speed, heading, eta, lon, lat
        );*/

        Optional<UnitCacheItem> oci = CACHE.FindUnit(envId);
        if (oci.isPresent()) {
          UnitCacheItem ci = oci.get();
          ci.UpdateLocation(lon, lat, speed, heading, eta);
          CACHE.UpdateUnit(envId, ci);
        }
      }
    } catch (Exception e) {
      LOG.error("Exception while updating unit-positions(" + envId + ") in database: ", e);
      throw new RuntimeException(e);
    }
  }

  private static void handleUnitChangedMessage(String vhost, String msgBody) {
    JSONObject unit = extractObjectFromMessage(msgBody);

    String unitId = unit.getString("roepnaam");
    String envId = vhost + '-' + unitId;

    try {
      // Is message for me
      if (
        unitIsForMe(unit, "afzender", Arrays.asList(RQ_SENDERS.split(",")), true) == true ||
        unitIsForMe(unit, "meldkamerStatusAbonnementen", Arrays.asList(RQ_SENDERS.split(",")), false) == true
      ) {
        Integer gmsStatusCode = unit.getInt("gmsStatusCode");
        String sender = unit.getString("afzender");
        String post = unit.has("standplaatsKazerne") ? unit.getString("standplaatsKazerne") : "";
        String primairevoertuigsoort = unit.has("primaireVoertuigSoort") ? unit.getString("primaireVoertuigSoort") : "";
        JSONArray abbs = unit.has("meldkamerStatusAbonnementen") ? unit.getJSONArray("meldkamerStatusAbonnementen") : new JSONArray();
    
        addOrUpdateDbUnit(vhost, unitId, envId, gmsStatusCode, sender, primairevoertuigsoort, abbs.toString(), post);
      }
    } catch(Exception e) {
      LOG.error("Exception while updating unit(" + envId + ") in database: ", e);
      throw new RuntimeException(e);
    }
  }

  private static void addOrUpdateDbUnit(String vhost, String unitId, String envId, Integer gmsStatusCode, String sender, String primairevoertuigsoort, String abbs, String post) {
    try {
      Optional<UnitCacheItem> oci = CACHE.FindUnit(envId);
      if (oci.isPresent()) {
        UnitCacheItem ci = oci.get();
        ci.UpdateUnit(gmsStatusCode, primairevoertuigsoort, sender, post, abbs);
        CACHE.UpdateUnit(envId, ci);
      } else {
        UnitCacheItem ci = new UnitCacheItem("sc", vhost, unitId, envId, gmsStatusCode, sender, primairevoertuigsoort, abbs, post);
        CACHE.AddUnit(ci);
      }

      /*if (post.equals("")) {
        DB.qr().update("INSERT INTO safetymaps.units " +
          "(source, sourceEnv, sourceId, sourceEnvId, gmsstatuscode, sender, primairevoertuigsoort, abbs) VALUES('sc', ?, ?, ?, ?, ?, ?, ?) " +
          " ON CONFLICT (sourceEnvId) DO UPDATE SET gmsstatuscode = ?, primairevoertuigsoort = ?, abbs = ?",
          vhost, unitId, envId, gmsStatusCode, sender, primairevoertuigsoort, abbs, gmsStatusCode, primairevoertuigsoort, abbs);
      } else {
        DB.qr().update("INSERT INTO safetymaps.units " +
          "(source, sourceEnv, sourceId, sourceEnvId, gmsstatuscode, sender, primairevoertuigsoort, abbs, post) VALUES('sc', ?, ?, ?, ?, ?, ?, ?, ?) " +
          " ON CONFLICT (sourceEnvId) DO UPDATE SET gmsstatuscode = ?, primairevoertuigsoort = ?, abbs = ?, post = ?",
          vhost, unitId, envId, gmsStatusCode, sender, primairevoertuigsoort, abbs, post, gmsStatusCode, primairevoertuigsoort, abbs, post);
      }*/
    } catch (Exception e) {
      LOG.error("Exception while upserting unit(" + envId + ") in database: ", e);
    }
  }

  private static void handleIncidentChangedMessage(String vhost, String msgBody) {
    JSONObject incident = extractObjectFromMessage(msgBody);
    List<String> regions = Arrays.asList(RQ_REGIONS.split(","))
      .stream()
      .filter(reg -> !reg.startsWith("(EM)"))
      .collect(Collectors.toList());
    
    if (
      // SMVNG-711 : only filter on tenant // incidentIsForMe(incident, "afzender", Arrays.asList(RQ_SENDERS.split(","))) == true ||
      incidentIsForMe(incident, "tenantIndentifier", Arrays.asList(RQ_TENANTS.split(","))) == true ||
      (
        incidentIsForMe(incident, "afzender", Arrays.asList(RQ_SENDERS.split(","))) == true &&
        incidentHasUnitForMe(incident, regions) == true
      )
    ) {

      String incidentId = incident.getString("incidentId");
      String envId = vhost + '-' + incidentId;
      
      try {
        /*List<Map<String, Object>> dbIncidents = DB.qr().query("SELECT * FROM safetymaps.incidents WHERE source = 'sc' AND sourceenvid = ?", new MapListHandler(), envId);
        JSONObject dbIncident = dbIncidents.size() > 0 ? SafetyConnectMessageUtil.MapIncidentDbRowAllColumnsAsJSONObject(dbIncidents.get(0)) : new JSONObject();*/
        Optional<IncidentCacheItem> oci = CACHE.FindIncident(envId);
        JSONObject dbIncident = oci.isPresent() ? SafetyConnectMessageUtil.MapIncidentDbRowAllColumnsAsJSONObject(oci.get().ConvertToMap()) : new JSONObject();

        // TODO : SMVNG-734 - Verder afmaken zodra Rinke ook wat heeft gedaan
        JSONObject brwDisc = incident.has("brwDisciplineGegevens") ? incident.getJSONObject("brwDisciplineGegevens") : null;
        JSONObject ambuDisc = incident.has("brwDisciplineGegevens") ? incident.getJSONObject("cpaDisciplineGegevens") : null;
        JSONObject polDisc = incident.has("brwDisciplineGegevens") ? incident.getJSONObject("polDisciplineGegevens") : null;
        
        Integer number = incident.getInt("incidentNummer");
        String tenantId = incident.getString("tenantIndentifier");
        String status = incident.getString("status");
        String sender = incident.getString("afzender");
        JSONArray notes = incident.has("kladblokregels") 
          ? incident.getJSONArray("kladblokregels") 
          : dbIncident.has("notes") 
            ? dbIncident.getJSONArray("notes") 
            : new JSONArray();
        JSONArray units = incident.has("betrokkenEenheden") 
          ? incident.getJSONArray("betrokkenEenheden") 
          : dbIncident.has("units") 
            ? dbIncident.getJSONArray("units") 
            : new JSONArray();
        JSONArray characts = incident.has("karakteristieken") 
          ? incident.getJSONArray("karakteristieken") 
          : dbIncident.has("characts") 
            ? dbIncident.getJSONArray("characts") 
            : new JSONArray();
        JSONObject location = incident.has("incidentLocatie") 
          ? incident.getJSONObject("incidentLocatie") 
          : dbIncident.has("location") 
            ? dbIncident.getJSONObject("location") 
            : new JSONObject();
        JSONObject discipline = incident.has("brwDisciplineGegevens") 
          ? incident.getJSONObject("brwDisciplineGegevens") 
          : dbIncident.has("discipline") 
            ? dbIncident.getJSONObject("discipline") 
            : new JSONObject();
        JSONArray talkinggroups = incident.has("gespreksgroepen") 
          ? incident.getJSONArray("gespreksgroepen") 
          : dbIncident.has("talkinggroups") 
            ? dbIncident.getJSONArray("talkinggroups") 
            : new JSONArray();

        JSONArray modifiedUnits = new JSONArray();
        for(int i=0; i<units.length(); i++) {
          JSONObject unit = (JSONObject)units.get(i);

          String unitId = unit.getString("roepnaam");
          String unitEnvId = vhost + '-' + unitId;
          Integer gmsStatusCode = unit.getInt("statusCode");
          String primairevoertuigsoort = unit.has("primaireVoertuigSoort") ? unit.getString("primaireVoertuigSoort") : null;
          JSONArray abbs = unit.has("meldkamerStatusAbonnementen") ? unit.getJSONArray("meldkamerStatusAbonnementen") : new JSONArray();

          addOrUpdateDbUnit(vhost, unitId, unitEnvId, gmsStatusCode, sender, primairevoertuigsoort, abbs.toString(), null);

          List<Map<String, Object>> dbUnits = DB.qr().query("SELECT * FROM safetymaps.units WHERE source = 'sc' AND sourceenvid = ?", new MapListHandler(), unitEnvId);
          JSONObject dbUnit = dbUnits.size() > 0 ? SafetyConnectMessageUtil.MapUnitDbRowAllColumnsAsJSONObject(dbUnits.get(0)) : new JSONObject();

          unit.put("standPlaatsKazerneCode", dbUnit.has("post") ? dbUnit.getString("post") : "");
          modifiedUnits.put(unit);
        }

        /*
        DB.qr().update("INSERT INTO safetymaps.incidents " + 
          "(source, sourceEnv, sourceId, sourceEnvId, status, sender, number, notes, units, characts, location, discipline, tenantid, talkinggroups) VALUES ('sc', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
          " ON CONFLICT (sourceEnvId) DO UPDATE SET status = ?, notes = ?, units = ?, characts = ?, location = ?, discipline = ?, tenantId = ?, talkinggroups = ?", 
          vhost, incidentId, envId, status, sender, number, notes.toString(), modifiedUnits.toString(), characts.toString(), location.toString(), discipline.toString(), tenantId, talkinggroups.toString(), status, notes.toString(), modifiedUnits.toString(), characts.toString(), location.toString(), discipline.toString(), tenantId, talkinggroups.toString());
        */

        if (oci.isPresent()) {
          IncidentCacheItem ci = oci.get();
          ci.UpdateIncident(notes.toString(), modifiedUnits.toString(), location.toString(), discipline.toString(), status, characts.toString(), talkinggroups.toString());
          CACHE.UpdateIncident(envId, ci);
        } else {
          IncidentCacheItem ci = new IncidentCacheItem("sc", vhost, incidentId, envId, notes.toString(), modifiedUnits.toString(), location.toString(), discipline.toString(), status, sender, characts.toString(), tenantId, talkinggroups.toString(), number);
          CACHE.AddIncident(ci);
        }
      } catch (Exception e) {
        LOG.error("Exception while upserting incident(" + envId + ") in database: ", e);
        throw new RuntimeException(e);
      }
    }
  }

  private static JSONObject extractObjectFromMessage(String msgBody) {
    JSONObject msg = new JSONObject(msgBody);
    JSONObject object = msg.getJSONObject("message");

    return object;
  }

  private static String nameQueue(Channel channel, String rqMb, String event, String vhost, String host) {
    String name = null;
    String checkByName = RQ_OPTIONAL_NAME_PREFIX + "_" + RQ_VHOSTS.substring(0, 1) + "_" + vhost + "_SMVNG_" + StringUtils.join(RQ_SENDERS, "_") + "_" + event;
    
    try {
      name = DB.qr().query("select queuenname from safetymaps.rq where queuenname = ?", new ScalarHandler<String>(), checkByName);
      if (name != null) {
        channel.queueBind(name, rqMb, "");
        return name;
      } else {
        Map<String, Object> args = new HashMap<>();

        // Backwards compatible, disallow args on old server
        if (host.equals("10.233.184.139") == false) {
          List<String> params = Arrays.asList(RQ_PARAMS.split(","));
          params.forEach((param) -> {
            String[] paramArr = param.split(":");
            if (paramArr.length == 2) {
              args.put(paramArr[0], paramArr[1]);
            }
          });
        }

        name = channel.queueDeclare(checkByName, true, false, false, args).getQueue();
        channel.queueBind(name, rqMb, "");

        DB.qr().update("INSERT INTO safetymaps.rq (queuenname, messagebus) VALUES (?, ?)", name, rqMb);

        return name;
      }
    } catch (Exception e) {
      LOG.error("Exception while executing nameQueue('" + rqMb + "', '" + event + "'): ", e);
      return null;
    }
  }

  private static String nameChannel(String vhost, String rqMb) {
    return RQ_VHOSTS.substring(0, 1) + "-" + vhost + "-" + rqMb;
  }

  private static boolean incidentIsForMe(JSONObject object, String key, List<String> valuesToCheck) {
    boolean matched = false;
    String keyValue = object.getString(key);

    matched = valuesToCheck.contains(keyValue);
    
    return matched;
  }

  private static boolean incidentHasUnitForMe(JSONObject incident, List<String> regionCodes) {
    boolean matched = false;

    JSONArray units = incident.has("betrokkenEenheden") 
      ? incident.getJSONArray("betrokkenEenheden") 
      : new JSONArray();

    for(int i=0; i<units.length(); i++) {
      JSONObject unit = (JSONObject)units.get(i);
      String unitName = unit.has("roepnaam") ? unit.getString("roepnaam") : "aaaaaaaaaa";
      String unitRegion = unitName.substring(0, 2);

      boolean found = regionCodes.contains(unitRegion);
      if (found) { 
        matched = true;
      }
    }

    return matched;
  }

  private static boolean unitIsForMe(JSONObject object, String key, List<String> valuesToCheck, Boolean keyIsString) {
    boolean matched = false;

    if (object.has(key) == false) {
      return false;
    }

    if (keyIsString == false) {
      JSONArray keyValues = object.getJSONArray(key);

      for(int i=0; i<keyValues.length(); i++) {
        boolean found = valuesToCheck.contains(keyValues.get(i));
        if (found) { 
          matched = true;
        }
      }
    } else {
      String keyValueString = object.getString(key);
      matched = valuesToCheck.contains(keyValueString);
    }
    
    return matched;
  }

  private static boolean unitIsForMyRegion(JSONObject unit, List<String> regionCodes) {
    String unitName = unit.has("unit") ? unit.getString("unit") : "aaaaaaaaaa";
    String unitRegion = unitName.length() > 2 ? unitName.substring(0, 2) : "notfound";

    boolean matched = false;
    boolean found = regionCodes.contains(unitRegion) || regionCodes.contains("(EM)" + unitRegion);
    if (found) { 
      matched = true;
    }

    return matched;
  }

  private static boolean isEnabled() {
    try {
      String useRabbitMq = Cfg.getSetting("safetyconnect_rq", "false");

      return "true".equals(useRabbitMq);
    } catch (Exception e) {
      LOG.error("Exception while checking isEnabled(): ", e);
    }

    return false;
  }

  public static Scheduler getSchedulerInstance() throws SchedulerException {
      if (SCHEDULER == null) {
        Properties props = new Properties();
        props.put("org.quartz.scheduler.instanceName", "IncidentUnitCacheScheduler");
        props.put("org.quartz.threadPool.threadCount", "1");
        props.put("org.quartz.scheduler.interruptJobsOnShutdownWithWait", "true");
        props.put("org.quartz.jobStore.class", "org.quartz.simpl.RAMJobStore");
        SCHEDULER = new StdSchedulerFactory(props).getScheduler();
        SCHEDULER.start();
      }

      return SCHEDULER;
    }
}
