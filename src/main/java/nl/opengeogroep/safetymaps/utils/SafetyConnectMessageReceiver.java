package nl.opengeogroep.safetymaps.utils;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

import org.apache.commons.dbutils.handlers.ColumnListHandler;
import org.apache.commons.dbutils.handlers.MapListHandler;
import org.apache.commons.dbutils.handlers.ScalarHandler;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
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
  private static final String RQ_MB_UNIT_MOVED = "SafetyConnect.Messages.EenheidMoved:IEenheidMovedEvent";
  private static final String RQ_MB_ETA_RECEIVED = "SafetyConnect.Messages.EtaReceived:IEtaReceivedEvent";

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
    JSONObject moveToSave = move;

    if (messageIsForMe(move, "unit", getUnits()) == false) {
      return;
    }

    String moveId = move.getString("unit");
    String envId = vhost + '-' + moveId;

    try {
      DB.qr().update("INSERT INTO safetymaps.unitlocations (source, sourceEnv, sourceId, sourceEnvId, details) VALUES ('sc', ?, ?, ?, ?) ON CONFLICT (sourceEnvId) DO UPDATE SET details = ?", vhost, moveId, envId, moveToSave.toString(), moveToSave.toString());
    } catch (Exception e) {
      log.error("Exception while upserting unitlocations(" + envId + ") in database: ", e);
    }
  }

  private void handleUnitChangedMessage(String vhost, String msgBody) {
    JSONObject unit = extractObjectFromMessage(msgBody);
    JSONObject unitToSave = unit;

    if (messageIsForMe(unit, "afzender", Arrays.asList(RQ_SENDERS.split(","))) == false) {
      return;
    }

    String kic = unit.getString("kindOfChange"); // updated, synchronisatie, created, deleted
    String unitId = unit.getString("roepnaam");
    String envId = vhost + '-' + unitId;

    try {
      String dbUnitString = DB.qr().query("SELECT details FROM safetymaps.units WHERE source = 'sc' AND sourceenvid = ?", new ScalarHandler<String>(), envId);
      JSONObject dbUnit = new JSONObject(dbUnitString);

      if (dbUnit != null && (kic == "updated" || kic == "synchronisatie")) {
        if (unit.has("discipline") && !unit.getString("discipline").equals(dbUnit.getString("discipline"))) { 
          dbUnit.put("discipline", unit.getString("discipline")); 
        }

        if (unit.has("gmsStatusCode") && !(unit.getInt("gmsStatusCode") == dbUnit.getInt("gmsStatusCode"))) { 
          dbUnit.put("gmsStatusCode", unit.getInt("gmsStatusCode")); 
        }

        if (unit.has("standplaatsKazerne") && !unit.getString("standplaatsKazerne").equals(dbUnit.getString("standplaatsKazerne"))) { 
          dbUnit.put("standplaatsKazerne", unit.getString("standplaatsKazerne")); 
        }

        if (unit.has("standplaatsKazerneCode") && !unit.getString("standplaatsKazerneCode").equals(dbUnit.getString("standplaatsKazerneCode"))) { 
          dbUnit.put("standplaatsKazerneCode", unit.getString("standplaatsKazerneCode")); 
        }

        if (unit.has("primaireVoertuigSoort") && !unit.getString("primaireVoertuigSoort").equals(dbUnit.getString("primaireVoertuigSoort"))) { 
          dbUnit.put("primaireVoertuigSoort", unit.getString("primaireVoertuigSoort")); 
        }

        unitToSave = dbUnit;
      }

      DB.qr().update("INSERT INTO safetymaps.units (source, sourceEnv, sourceId, sourceEnvId, details) VALUES ('sc', ?, ?, ?, ?) ON CONFLICT (sourceEnvId) DO UPDATE SET details = ?", vhost, unitId, envId, unitToSave.toString(), unitToSave.toString());
    } catch (Exception e) {
      log.error("Exception while upserting unit(" + envId + ") in database: ", e);
    }
  }

  private void handleIncidentChangedMessage(String vhost, String msgBody) {
    JSONObject incident = extractObjectFromMessage(msgBody);
    JSONObject incidentToSave = incident;
    
    if (messageIsForMe(incident, "afzender", Arrays.asList(RQ_SENDERS.split(","))) == false) {
      return;
    }

    String kic = incident.getString("kindOfChange"); // updated, synchronisatie, created
    String incidentId = incident.getString("incidentId");
    String envId = vhost + '-' + incidentId;
    
    try {
      String dbIncidentString = DB.qr().query("SELECT details FROM safetymaps.incidents WHERE source = 'sc' AND sourceenvid = ?", new ScalarHandler<String>(), envId);
      JSONObject dbIncident = new JSONObject(dbIncidentString);

      if (dbIncident != null && (kic == "updated" || kic == "synchronisatie")) {
        if (incident.has("status") && !incident.getString("status").equals(dbIncident.getString("status"))) { 
          dbIncident.put("status", incident.getString("status")); 
        }

        if (incident.has("brwDisciplineGegevens") && !incident.getJSONObject("brwDisciplineGegevens").toString().equals(dbIncident.getJSONObject("brwDisciplineGegevens").toString())) { 
          dbIncident.put("brwDisciplineGegevens", incident.getJSONObject("brwDisciplineGegevens")); 
        }

        if (incident.has("incidentLocatie") && !incident.getJSONObject("incidentLocatie").toString().equals(dbIncident.getJSONObject("incidentLocatie").toString())) { 
          dbIncident.put("incidentLocatie", incident.getJSONObject("incidentLocatie")); 
        }

        if (incident.has("kladblokregels") && !incident.getJSONArray("kladblokregels").toString().equals(dbIncident.getJSONArray("kladblokregels").toString())) { 
          dbIncident.put("kladblokregels", incident.getJSONArray("kladblokregels")); 
        }

        if (incident.has("betrokkenEenheden") && !incident.getJSONArray("betrokkenEenheden").toString().equals(dbIncident.getJSONArray("betrokkenEenheden").toString())) { 
          dbIncident.put("betrokkenEenheden", incident.getJSONArray("betrokkenEenheden")); 
        }

        if (incident.has("karakteristieken") && !incident.getJSONArray("karakteristieken").toString().equals(dbIncident.getJSONArray("karakteristieken").toString())) { 
          dbIncident.put("karakteristieken", incident.getJSONArray("karakteristieken")); 
        }

        incidentToSave = dbIncident;
      }

      DB.qr().update("INSERT INTO safetymaps.incidents (source, sourceEnv, sourceId, sourceEnvId, details) VALUES ('sc', ?, ?, ?, ?) ON CONFLICT (sourceEnvId) DO UPDATE SET details = ?", vhost, incidentId, envId, incidentToSave.toString(), incidentToSave.toString());
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
    //List<String> senders = Arrays.asList(valuesToCheck.split(","));

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
