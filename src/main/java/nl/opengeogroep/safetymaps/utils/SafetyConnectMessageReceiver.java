package nl.opengeogroep.safetymaps.utils;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

import org.apache.commons.dbutils.handlers.ColumnListHandler;
import org.apache.commons.dbutils.handlers.MapHandler;
import org.apache.commons.dbutils.handlers.MapListHandler;
import org.apache.commons.dbutils.handlers.ScalarHandler;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONArray;
import org.json.JSONObject;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DeliverCallback;

import nl.opengeogroep.safetymaps.server.db.Cfg;
import nl.opengeogroep.safetymaps.server.db.DB;

public class SafetyConnectMessageReceiver implements ServletContextListener {
  private static final Log log = LogFactory.getLog(SafetyConnectMessageReceiver.class);

  private ServletContext CONTEXT;
  private String RQ_SENDERS;
  private String RQ_HOST;
  private String RQ_VHOSTS;
  private String RQ_USER;
  private String RQ_PASS;
  
  private HashMap<String, Connection> RQ_CONNECTIONS = new HashMap<String, Connection>();
  private HashMap<String, Channel> RQ_CHANNELS = new HashMap<String, Channel>();

  private static final String RQ_MB_INCIDENT_CHANGED = "SafetyConnect.Messages.IncidentChanged:IIncidentChangedEvent";
  private static final String RQ_MB_UNIT_CHANGED = "SafetyConnect.Messages.EenheidChanged:IEenheidChangedEvent";
  private static final String RQ_MB_UNIT_MOVED = "SafetyConnect.Messages.EenheidMoved:IPositionReceivedEvent";

  @Override
  public void contextInitialized(ServletContextEvent sce) {
    CONTEXT = sce.getServletContext();
    
    if (isEnabled() == false) { 
      return; 
    }

    try {
      getConfigFromDb();
    } catch (Exception e) {
      log.error("Exception while exec 'getConfigFromDb()': ", e);
      return;
    }

    List<String> vhosts = Arrays.asList(RQ_VHOSTS.split(","));

    vhosts.forEach((vhost) -> {
      try {
        initiateRabbitMqConnection(vhost);
        log.info("SafetyConnectMessageReceiver RabbitMqConnection initialized.");
      } catch (Exception e) {
        log.error("Exception while exec 'initiateRabbitMqConnection(" + vhost + ")': ", e);
      }

      try {
        initRabbitMqChannel(vhost, RQ_MB_INCIDENT_CHANGED, "incident_changed");
        log.info("SafetyConnectMessageReceiver RabbitMqChannel('" + vhost + "', '" + RQ_MB_INCIDENT_CHANGED + "') initialized.");
      } catch (Exception e) {
        log.error("Exception while exec 'initRabbitMqChannel(" + vhost + ", " + RQ_MB_INCIDENT_CHANGED + ")'", e);
      }

      try {
        initRabbitMqChannel(vhost, RQ_MB_UNIT_CHANGED, "unit_changed");
        log.info("SafetyConnectMessageReceiver RabbitMqChannel('" + vhost + "', '" + RQ_MB_UNIT_CHANGED + "') initialized.");
      } catch (Exception e) {
        log.error("Exception while exec 'initRabbitMqChannel(" + vhost + ", " + RQ_MB_UNIT_CHANGED + ")'", e);
      }

      try {
        initRabbitMqChannel(vhost, RQ_MB_UNIT_MOVED, "incident_moved");
        log.info("SafetyConnectMessageReceiver RabbitMqChannel('" + vhost + "', '" + RQ_MB_UNIT_MOVED + "') initialized.");
      } catch (Exception e) {
        log.error("Exception while exec 'initRabbitMqChannel(" + vhost + ", " + RQ_MB_UNIT_MOVED + ")'", e);
      }
    });

    log.info("SafetyConnectMessageReceiver initialized for all vhosts.");
  }

  @Override
  public void contextDestroyed(ServletContextEvent sce) {
    RQ_CHANNELS.forEach((key, value) -> {
      try { 
        RQ_CHANNELS.get(key).close();
      } catch (Exception e) {
        log.error("Exception on 'RQ_CHANNELS.get(" + key + ").close()'", e);
      }
    });

    RQ_CONNECTIONS.forEach((key, value) -> {
      try { 
        RQ_CONNECTIONS.get(key).close();
      } catch (Exception e) {
        log.error("Exception on 'RQ_CONNECTIONS.get(" + key + ").close()'", e);
      }
    });

    try {
      DB.qr().update("DELETE FROM safetymaps.rq");
    } catch (Exception e) {
      log.error("Exception while cleaning up after contextDestroyed: ", e);
    }
  }

  private void getConfigFromDb() throws Exception {
    RQ_HOST = Cfg.getSetting("safetyconnect_rq_host");
    RQ_VHOSTS = Cfg.getSetting("safetyconnect_rq_vhost");
    RQ_USER = Cfg.getSetting("safetyconnect_rq_user");
    RQ_PASS = Cfg.getSetting("safetyconnect_rq_pass");
    RQ_SENDERS = Cfg.getSetting("safetyconnect_rq_senders", "");

    if (RQ_HOST == null || RQ_USER == null || RQ_PASS == null) {
      throw new Exception("One or more required 'safetyconnect_rq' settings are empty.");
    }

    if (RQ_VHOSTS == null) { 
      RQ_VHOSTS = "*"; 
    }
  }

  private void initiateRabbitMqConnection(String vhost) throws Exception {
    ConnectionFactory rqConFac;

    rqConFac = new ConnectionFactory();
    if (vhost != "*") {
      rqConFac.setVirtualHost(vhost);
    }
    rqConFac.setHost(RQ_HOST);
    rqConFac.setUsername(RQ_USER);
    rqConFac.setPassword(RQ_PASS);

    RQ_CONNECTIONS.put(vhost, rqConFac.newConnection());
  }

  private void initRabbitMqChannel(String vhost, String rqMb, String internalEvent) throws Exception {    
    Channel channel = RQ_CONNECTIONS.get(vhost).createChannel();
    String queueName = nameQueue(channel, rqMb, internalEvent);
    String channelName = nameChannel(vhost, rqMb);
    DeliverCallback messageHandler = getMessageHandler(vhost, rqMb);

    channel.basicConsume(queueName, true, messageHandler, consumerTag -> { });

    RQ_CHANNELS.put(channelName, channel);
  }

  private DeliverCallback getMessageHandler(String vhost, String rqMb) {
    return (consumerTag, delivery) -> {
      String msgBody = new String(delivery.getBody(), "UTF-8");

      switch (rqMb) {
        case RQ_MB_INCIDENT_CHANGED:
          handleIncidentChangedMessage(vhost, msgBody);
          break;
        case RQ_MB_UNIT_CHANGED:
          handleUnitChangedMessage(vhost, msgBody);
        case RQ_MB_UNIT_MOVED:
          handleUnitMovedMessage(vhost, msgBody);
        default:
          break;
      }
    }; 
  }

  private void handleUnitMovedMessage(String vhost, String msgBody) {
    JSONObject move = extractObjectFromMessage(msgBody);

    if (messageIsForMe(move, "unit", getUnits()) == false) {
      return;
    }

    String moveId = move.getString("unit");
    String envId = vhost + '-' + moveId;

    try {
      List<Map<String, Object>> dbUnits = DB.qr().query("SELECT * FROM safetymaps.units WHERE source = 'sc' AND sourceenvid = ?", new MapListHandler(), envId);
      JSONObject dbUnit = dbUnits.size() > 0 ? SafetyConnectMessageUtil.MapUnitDbRowAllColumnsAsJSONObject(dbUnits.get(0)) : new JSONObject();

      if (dbUnits.size() > 0) {
        JSONObject position = dbUnit.has("positie") 
          ? dbUnit.getJSONObject("positie") 
          : new JSONObject();

        position.put("lon", move.getDouble("lon"));
        position.put("lat", move.getDouble("lat"));
        if (move.has("speed")) { position.put("speed", move.getInt("speed")); }
        if (move.has("heading")) { position.put("heading", move.getInt("heading")); }
        if (move.has("eta")) { position.put("eta", move.getInt("eta")); }

        DB.qr().update("UPDATE safetymaps.units SET position = ? WHERE sourceEnvId = ?", position.toString(), envId);
      }
    } catch (Exception e) {
      log.error("Exception while updating unit-positions(" + envId + ") in database: ", e);
    }
  }

  private void handleUnitChangedMessage(String vhost, String msgBody) {
    JSONObject unit = extractObjectFromMessage(msgBody);

    if (messageIsForMe(unit, "afzender", Arrays.asList(RQ_SENDERS.split(","))) == false) {
      return;
    }

    //String kic = unit.getString("kindOfChange"); // updated, synchronisatie, created, deleted
    String unitId = unit.getString("roepnaam");
    String envId = vhost + '-' + unitId;

    try {
      List<Map<String, Object>> dbUnits = DB.qr().query("SELECT * FROM safetymaps.units WHERE source = 'sc' AND sourceenvid = ?", new MapListHandler(), envId);
      JSONObject dbUnit = dbUnits.size() > 0 ? SafetyConnectMessageUtil.MapUnitDbRowAllColumnsAsJSONObject(dbUnits.get(0)) : new JSONObject();

      Integer gmsStatusCode = unit.getInt("gmsStatusCode");
      String sender = unit.getString("afzender");
      String primairevoertuigsoort = unit.getString("primaireVoertuigSoort");
      JSONObject position = dbUnit.has("positie") 
          ? dbUnit.getJSONObject("positie") 
          : new JSONObject();

      DB.qr().update("INSERT INTO safetymaps.units " +
        "(source, sourceEnv, sourceId, sourceEnvId, position, gmsstatuscode, sender, primairevoertuigsoort) VALUES('sc', ?, ?, ?, ?, ?, ?, ?) " +
        " ON CONFLICT (sourceEnvId) DO UPDATE SET gmsstatuscode = ?, primairevoertuigsoort = ?",
        vhost, unitId, envId, position.toString(), gmsStatusCode, sender, primairevoertuigsoort, gmsStatusCode, primairevoertuigsoort);
    } catch (Exception e) {
      log.error("Exception while upserting unit(" + envId + ") in database: ", e);
    }
  }

  private void handleIncidentChangedMessage(String vhost, String msgBody) {
    JSONObject incident = extractObjectFromMessage(msgBody);
    
    if (messageIsForMe(incident, "afzender", Arrays.asList(RQ_SENDERS.split(","))) == false) {
      return;
    }

    //String kic = incident.getString("kindOfChange"); // updated, synchronisatie, created
    String incidentId = incident.getString("incidentId");
    String envId = vhost + '-' + incidentId;
    
    try {
      List<Map<String, Object>> dbIncidents = DB.qr().query("SELECT * FROM safetymaps.incidents WHERE source = 'sc' AND sourceenvid = ?", new MapListHandler(), envId);
      JSONObject dbIncident = dbIncidents.size() > 0 ? SafetyConnectMessageUtil.MapIncidentDbRowAllColumnsAsJSONObject(dbIncidents.get(0)) : new JSONObject();
      
      Integer number = incident.getInt("incidentNummer");
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

      DB.qr().update("INSERT INTO safetymaps.incidents " + 
        "(source, sourceEnv, sourceId, sourceEnvId, status, sender, number, notes, units, characts, location, discipline) VALUES ('sc', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
        " ON CONFLICT (sourceEnvId) DO UPDATE SET status = ?, notes = ?, units = ?, characts = ?, location = ?, discipline = ?", 
        vhost, incidentId, envId, status, sender, number, notes.toString(), units.toString(), characts.toString(), location.toString(), discipline.toString(), status, notes.toString(), units.toString(), characts.toString(), location.toString(), discipline.toString());
    } catch (Exception e) {
      log.error("Exception while upserting incident(" + envId + ") in database: ", e);
    }
  }

  private List<String> getUnits() {
    try {
      return DB.qr().query("SELECT sourceid FROM safetymaps.units WHERE source = 'sc'", new ColumnListHandler<String>());
    } catch (Exception e) {
      return Arrays.asList("".split(","));
    }
  }

  private JSONObject extractObjectFromMessage(String msgBody) {
    JSONObject msg = new JSONObject(msgBody);
    JSONObject object = msg.getJSONObject("message");

    return object;
  }

  private String nameQueue(Channel channel, String rqMb, String event) {
    String name = null;
    try {
      name = DB.qr().query("select queuenname from safetymaps.rq where messagebus = ?", new ScalarHandler<String>(), rqMb);
      if (name != null) {
        channel.queueBind(name, rqMb, "");
        return name;
      } else {
        name = channel.queueDeclare("Safetymaps-Server_" + StringUtils.join(RQ_SENDERS, "_") + "_" + event, true, false, false, null).getQueue();
        channel.queueBind(name, rqMb, "");
        DB.qr().update("INSERT INTO safetymaps.rq (queuenname, messagebus) VALUES (?, ?)", name, rqMb);
        return name;
      }
    } catch (Exception e) {
      log.error("Exception while executing nameQueue('" + rqMb + "', '" + event + "'): ", e);
      return null;
    }
  }

  private String nameChannel(String vhost, String rqMb) {
    return vhost + "-" + rqMb;
  }

  private boolean messageIsForMe(JSONObject object, String key, List<String> valuesToCheck) {
    boolean matched = false;
    String keyValue = object.getString(key);

    matched = valuesToCheck.contains(keyValue);
    
    return matched;
  }

  private boolean isEnabled() {
    try {
      String useRabbitMq = Cfg.getSetting("safetyconnect_rq", "false");

      return "true".equals(useRabbitMq);
    } catch (Exception e) {
      log.error("Exception while checking isEnabled(): ", e);
    }

    return false;
  }
}
