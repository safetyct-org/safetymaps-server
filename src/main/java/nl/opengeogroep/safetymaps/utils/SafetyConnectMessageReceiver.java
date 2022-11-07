package nl.opengeogroep.safetymaps.utils;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
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
  private String RQ_HOST;
  private String RQ_VHOSTS;
  private String RQ_USER;
  private String RQ_PASS;
  
  private HashMap<String, Connection> RQ_CONNECTIONS = new HashMap<String, Connection>();
  private HashMap<String, Channel> RQ_CHANNELS = new HashMap<String, Channel>();

  //private static final String RQ_MB_INCIDENTS_SYNCED = "SafetyConnect.Messages.IncidentenSynchronized";
  private static final String RQ_MB_INCIDENT_CHANGED = "SafetyConnect.Messages.IncidentChanged:IIncidentChangedEvent";
  //private static final String RQ_MB_UNITS_SYNCED = "SafetyConnect.Messages.EenhedenSynchronized";
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
        initRabbitMqChannel(vhost, RQ_MB_INCIDENT_CHANGED);
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

  private void initRabbitMqChannel(String vhost, String rqMb) throws Exception {    
    DeliverCallback dc = (consumerTag, delivery) -> {
      String msgBody = new String(delivery.getBody(), "UTF-8");

      if (rqMb == RQ_MB_INCIDENT_CHANGED) {
        handleIncidentChangedMessage(vhost, msgBody);
      }
    }; 

    Channel channel = RQ_CONNECTIONS.get(vhost).createChannel();
    String queueName = channel.queueDeclare().getQueue();
    String channelName = nameChannel(vhost, rqMb);

    channel.queueBind(queueName, rqMb, "");
    channel.basicConsume(queueName, true, dc, consumerTag -> { });

    RQ_CHANNELS.put(channelName, channel);
  }

  private String nameChannel(String vhost, String rqMb) {
    return vhost + "-" + rqMb;
  }

  private void handleIncidentChangedMessage(String vhost, String msgBody) {
    JSONObject msg = new JSONObject(msgBody);
    JSONObject incident = msg.getJSONObject("message");
    String kic = incident.getString("kindOfChange"); // updated, synchronisatie, created
    String incidentId = incident.getString("incidentId");
    String envId = vhost + '-' + incidentId;
    
    try {
      if (kic == "updated") {

      } else {
        DB.qr().update("INSERT INTO safetymaps.incidents (source, sourceEnv, sourceId, sourceEnvId, details) values ('sc', ?, ?, ?, ?) on conflict (sourceEnvId) do update set details = ?", vhost, incidentId, envId, incident.toString(), incident.toString());
      }
    } catch (Exception e) {
      log.error("Exception while upserting incident(" + envId + ") in database: ", e);
    }
  }

  private boolean isEnabled() {
    try {
      String useRabbitMq = Cfg.getSetting("safetyconnect_rq", "false");

      return "true".equals(useRabbitMq);
    } catch (Exception e) {
      log.error("Exception while while checking isEnabled(): ", e);
    }

    return false;
  }
}
