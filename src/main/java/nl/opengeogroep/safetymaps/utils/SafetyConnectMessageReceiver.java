package nl.opengeogroep.safetymaps.utils;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

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
  private String[] RQ_SENDERS;
  private String RQ_HOST;
  private String RQ_VHOSTS;
  private String RQ_USER;
  private String RQ_PASS;
  
  private HashMap<String, Connection> RQ_CONNECTIONS = new HashMap<String, Connection>();
  private HashMap<String, Channel> RQ_CHANNELS = new HashMap<String, Channel>();

  private static final String RQ_MB_INCIDENT_CHANGED = "SafetyConnect.Messages.IncidentChanged:IIncidentChangedEvent";
  private static final String RQ_MB_UNIT_CHANGED = "SafetyConnect.Messages.EenheidChanged";
  private static final String RQ_MB_UNIT_MOVED = "SafetyConnect.Messages.EenheidMoved";

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
    });

    log.info("SafetyConnectMessageReceiver initialized.");
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
    RQ_SENDERS = Cfg.getSetting("safetyconnect_rq_senders", "").split(",");

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
    DeliverCallback dc = getDc(vhost, rqMb);

    channel.basicConsume(queueName, true, dc, consumerTag -> { });

    RQ_CHANNELS.put(channelName, channel);
  }

  private void handleIncidentChangedMessage(String vhost, String msgBody) {
    JSONObject msg = new JSONObject(msgBody);
    JSONObject incident = msg.getJSONObject("message");
    JSONObject incidentToSave = incident;
    String sender = incident.getString("afzender");
    String kic = incident.getString("kindOfChange"); // updated, synchronisatie, created
    String incidentId = incident.getString("incidentId");
    String envId = vhost + '-' + incidentId;

    if (messageIsForMe(sender) == false) {
      return;
    }
    
    try {
      if (kic == "updated" || kic == "synchronisatie") {
        String dbIncidentString = DB.qr().query("SELECT details FROM safetymaps.incidents WHERE source = 'sc' AND sourceenvid = ?", new ScalarHandler<String>(), envId);
        JSONObject dbIncident = new JSONObject(dbIncidentString);

        if (dbIncident != null && !incident.getString("status").equals(dbIncident.getString("status"))) { 
          dbIncident.put("status", incident.getString("status")); 
        }

        if (dbIncident != null && !incident.getJSONObject("brwDisciplineGegevens").toString().equals(dbIncident.getJSONObject("brwDisciplineGegevens").toString())) { 
          dbIncident.put("brwDisciplineGegevens", incident.getJSONObject("brwDisciplineGegevens")); 
        }

        if (dbIncident != null && !incident.getJSONObject("incidentLocatie").toString().equals(dbIncident.getJSONObject("incidentLocatie").toString())) { 
          dbIncident.put("incidentLocatie", incident.getJSONObject("incidentLocatie")); 
        }

        if (dbIncident != null && !incident.getJSONArray("kladblokregels").toString().equals(dbIncident.getJSONArray("kladblokregels").toString())) { 
          dbIncident.put("kladblokregels", incident.getJSONArray("kladblokregels")); 
        }

        if (dbIncident != null && !incident.getJSONArray("betrokkenEenheden").toString().equals(dbIncident.getJSONArray("betrokkenEenheden").toString())) { 
          dbIncident.put("betrokkenEenheden", incident.getJSONArray("betrokkenEenheden")); 
        }

        if (dbIncident != null && !incident.getJSONArray("karakteristieken").toString().equals(dbIncident.getJSONArray("karakteristieken").toString())) { 
          dbIncident.put("karakteristieken", incident.getJSONArray("karakteristieken")); 
        }

        if (dbIncident != null) {
          incidentToSave = dbIncident;
        }
      }

      DB.qr().update("INSERT INTO safetymaps.incidents (source, sourceEnv, sourceId, sourceEnvId, details) VALUES ('sc', ?, ?, ?, ?) ON CONFLICT (sourceEnvId) DO UPDATE SET details = ?", vhost, incidentId, envId, incidentToSave.toString(), incidentToSave.toString());
    } catch (Exception e) {
      log.error("Exception while upserting incident(" + envId + ") in database: ", e);
    }
  }

  private DeliverCallback getDc(String vhost, String rqMb) {
    return (consumerTag, delivery) -> {
      String msgBody = new String(delivery.getBody(), "UTF-8");

      if (rqMb == RQ_MB_INCIDENT_CHANGED) {
        handleIncidentChangedMessage(vhost, msgBody);
      }
    }; 
  }

  private String nameQueue(Channel channel, String rqMb, String event) { //"_incident-changed"
    String name = null;
    try {
      name = DB.qr().query("select queuenname from safetymaps.rq where messagebus = ?", new ScalarHandler<String>(), rqMb);
      if (name != null) {
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

  private boolean messageIsForMe(String sender) {
    boolean matched = false;

    for(String rqSender: RQ_SENDERS) {
      if (rqSender.toLowerCase() == sender.toLowerCase()) {
        matched = true;
      }
    }

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
