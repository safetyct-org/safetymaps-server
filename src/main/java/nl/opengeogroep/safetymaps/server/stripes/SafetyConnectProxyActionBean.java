package nl.opengeogroep.safetymaps.server.stripes;

import net.sourceforge.stripes.action.ActionBean;
import net.sourceforge.stripes.action.ActionBeanContext;
import net.sourceforge.stripes.action.Resolution;
import net.sourceforge.stripes.action.UrlBinding;
import nl.b3p.web.stripes.ErrorMessageResolution;
import nl.opengeogroep.safetymaps.server.db.Cfg;
import nl.opengeogroep.safetymaps.server.db.DB;
import nl.opengeogroep.safetymaps.utils.SafetyConnectMessageUtil;

import org.apache.commons.dbutils.handlers.ColumnListHandler;
import org.apache.commons.dbutils.handlers.MapListHandler;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.mutable.MutableObject;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.HttpResponse;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStream;
import java.io.StringReader;
import java.sql.Connection;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

import org.json.JSONArray;
import org.json.JSONObject;

import static nl.opengeogroep.safetymaps.server.db.DB.ROLE_ADMIN;
import static nl.opengeogroep.safetymaps.server.db.DB.getUserDetails;

/**
 *
 * @author matthijsln
 */
@UrlBinding("/viewer/api/safetyconnect/{path}")
public class SafetyConnectProxyActionBean implements ActionBean {
    private static final Log log = LogFactory.getLog(SafetyConnectProxyActionBean.class);

    private ActionBeanContext context;

    static final String ROLE = "smvng_incident_safetyconnect_webservice";
    static final String ROLE_PROD = "smvng_incident_safetyconnect_webservice__prod";
    static final String ROLE_OPL = "smvng_incident_safetyconnect_webservice__opl";
    static final String ROLE_TEST = "smvng_incident_safetyconnect_webservice__test";

    static final String INCIDENT_REQUEST = "incident";
    static final String EENHEIDLOCATIE_REQUEST = "eenheidlocatie";
    static final String KLADBLOKREGEL_REQUEST = "kladblokregel";
    static final String EENHEID_REQUEST = "eenheid";
    static final String EENHEIDSTATUS_REQUEST = "eenheidstatus";
    static final String[] UNMODIFIED_REQUESTS = { EENHEID_REQUEST, EENHEIDSTATUS_REQUEST };

    private String path;

    @Override
    public ActionBeanContext getContext() {
        return context;
    }

    @Override
    public void setContext(ActionBeanContext context) {
        this.context = context;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public Resolution proxy() throws Exception {
        if(requestIs(INCIDENT_REQUEST) && !context.getRequest().isUserInRole(ROLE) && !context.getRequest().isUserInRole(ROLE_ADMIN)) {
            return unAuthorizedResolution();
        }

        if (requestIs(KLADBLOKREGEL_REQUEST) && !context.getRequest().isUserInRole("smvng_incident_logtogms_notepadchat") && !context.getRequest().isUserInRole(ROLE_ADMIN)) {
            return unAuthorizedResolution();
        }

        String regioCode = Cfg.getSetting("safetyconnect_regio_code");
        String defaultApi = Cfg.getSetting("safetyconnect_webservice_default"); // new

        String authorizationProd = Cfg.getSetting("safetyconnect_webservice_authorization_prod"); // new
        String authorizationOpl = Cfg.getSetting("safetyconnect_webservice_authorization_opl"); // new
        String authorizationTest = Cfg.getSetting("safetyconnect_webservice_authorization_test"); // new
        String urlProd = Cfg.getSetting("safetyconnect_webservice_url_prod"); // new
        String urlOpl = Cfg.getSetting("safetyconnect_webservice_url_opl"); // new
        String urlTest = Cfg.getSetting("safetyconnect_webservice_url_test"); // new

        Boolean useAdmin = context.getRequest().isUserInRole(ROLE_ADMIN);
        Boolean useProd = context.getRequest().isUserInRole(ROLE_PROD);
        Boolean useOpl = context.getRequest().isUserInRole(ROLE_OPL);
        Boolean useTest = context.getRequest().isUserInRole(ROLE_TEST);

        String useRabbitMq = "false"; //Cfg.getSetting("safetyconnect_rq", "false");
        String rabbitMqSourceDefault = "prod".equals(defaultApi) ? "production" : "opl".equals(defaultApi) ? "opleiding" : "test".equals(defaultApi) ? "test" : null;
        String rabbitMqSource = useAdmin ? rabbitMqSourceDefault : useProd ?  "production" : useOpl ? "opleiding" : useTest ? "test" : rabbitMqSourceDefault;

        String defaultAuth = "prod".equals(defaultApi) ? authorizationProd : "opl".equals(defaultApi) ? authorizationOpl : "test".equals(defaultApi) ? authorizationTest : null;
        String defaultUrl = "prod".equals(defaultApi) ? urlProd : "opl".equals(defaultApi) ? urlOpl : "test".equals(defaultApi) ? urlTest : null;

        String authorization = useAdmin ? defaultAuth : useProd ? authorizationProd : useOpl ? authorizationOpl : useTest ? authorizationTest : defaultAuth;
        String url = useAdmin ? defaultUrl : useProd ? urlProd : useOpl ? urlOpl : useTest ? urlTest : defaultUrl;

        if("false".equals(useRabbitMq) && (authorization == null || url == null)) {
            return new ErrorMessageResolution(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Geen toegangsgegevens voor webservice geconfigureerd door beheerder");
        }

        if ("true".equals(useRabbitMq) && requestIs(INCIDENT_REQUEST)) {
            return new Resolution() {
                @Override
                public void execute(HttpServletRequest request, HttpServletResponse response) throws Exception {
                    response.setCharacterEncoding("UTF-8");
                    response.setContentType("application/json");

                    OutputStream out;
                    String acceptEncoding = request.getHeader("Accept-Encoding");
                    if(acceptEncoding != null && acceptEncoding.contains("gzip")) {
                        response.setHeader("Content-Encoding", "gzip");
                        out = new GZIPOutputStream(response.getOutputStream(), true);
                    } else {
                        out = response.getOutputStream();
                    }

                    JSONArray incidents = new JSONArray();
                    List<Map<String, Object>> results = DB.qr().query("select * from safetymaps.incidents where source='sc' and sourceenv=?", new MapListHandler(), rabbitMqSource);

                    for (Map<String, Object> res : results) {
                      incidents.put(SafetyConnectMessageUtil.MapIncidentDbRowAllColumnsAsJSONObject(res));
                    }

                    IOUtils.copy(new StringReader(incidents.toString()), out, "UTF-8");

                    out.flush();
                    out.close();
                }
            };
        }

        if ("true".equals(useRabbitMq) && requestIs(EENHEIDSTATUS_REQUEST)) {
          String unitId = context.getRequest().getQueryString().replaceAll("id=", "");
          
          return new Resolution() {
              @Override
              public void execute(HttpServletRequest request, HttpServletResponse response) throws Exception {
                  response.setCharacterEncoding("UTF-8");
                  response.setContentType("application/json");

                  OutputStream out;
                  String acceptEncoding = request.getHeader("Accept-Encoding");
                  if(acceptEncoding != null && acceptEncoding.contains("gzip")) {
                      response.setHeader("Content-Encoding", "gzip");
                      out = new GZIPOutputStream(response.getOutputStream(), true);
                  } else {
                      out = response.getOutputStream();
                  }

                  JSONArray units = new JSONArray();
                  List<Map<String, Object>> results = DB.qr().query("select * from safetymaps.units where source='sc' and sourceenv=?", new MapListHandler(), rabbitMqSource);
                  
                  for (Map<String, Object> res : results) {
                    JSONObject unit = SafetyConnectMessageUtil.MapUnitDbRowAllColumnsAsJSONObject(res);
                    if (unitId.equals(unit.getString("roepnaam"))) { units.put(unit); }
                  }
                  
                  IOUtils.copy(new StringReader(units.toString()), out, "UTF-8");

                  out.flush();
                  out.close();
              }
          };
       }      

        String qs = context.getRequest().getQueryString();
        String uri = url + "/" + path + (regioCode == null ? (qs == null ? "" : "?") : "?regioCode=" + regioCode + (qs == null ? "" : "&")) + qs;
        final HttpUriRequest req;
        
        if (requestIs(KLADBLOKREGEL_REQUEST)) {
            req = RequestBuilder.post()
                .setUri(uri)
                .addHeader("Authorization", authorization)
                .addHeader("Content-Type", "none")
                .setEntity(new StringEntity(""))
                .build();
        } else {
            req = RequestBuilder.get()
                .setUri(uri)
                .addHeader("Authorization", authorization)
                .build();
        }

        try(CloseableHttpClient client = HttpClients.createDefault()) {
            final MutableObject<String> contentType = new MutableObject<>("text/plain");
            final String responseContent = client.execute(req, new ResponseHandler<String>() {
                @Override
                public String handleResponse(HttpResponse hr) {
                    log.debug("proxy for user " + context.getRequest().getRemoteUser() + " URL " + req.getURI() + ", response: " + hr.getStatusLine().getStatusCode() + " " + hr.getStatusLine().getReasonPhrase());
                    
                    if (hr.getEntity() != null && hr.getEntity().getContentType() != null) {
                        contentType.setValue(hr.getEntity().getContentType().getValue());
                    }
                    try {
                        return IOUtils.toString(hr.getEntity().getContent(), "UTF-8");
                    } catch(IOException e) {
                        log.error("Exception reading HTTP content", e);
                        return "Exception " + e.getClass() + ": " + e.getMessage();
                    }
                }
            });
            
            final String content;
            // Filter response from the webservice to remove any data that the user is not authorized for
            if (requestIs(INCIDENT_REQUEST)) {
                content = applyAuthorizationToIncidentContent(responseContent);
            } else if (requestIs(EENHEIDLOCATIE_REQUEST)) {
                content = applyFilterToEenheidLocatieContent(responseContent);
            } else if (requestIs(KLADBLOKREGEL_REQUEST)) {
                content = responseContent;
            } else if (keepRequestUnmodified()) {
                content = responseContent;
            } else {
                return unAuthorizedResolution();
            }

            return new Resolution() {
                @Override
                public void execute(HttpServletRequest request, HttpServletResponse response) throws Exception {
                    String encoding = "UTF-8";

                    response.setCharacterEncoding(encoding);
                    response.setContentType(contentType.getValue());

                    OutputStream out;
                    String acceptEncoding = request.getHeader("Accept-Encoding");
                    if(acceptEncoding != null && acceptEncoding.contains("gzip")) {
                        response.setHeader("Content-Encoding", "gzip");
                        out = new GZIPOutputStream(response.getOutputStream(), true);
                    } else {
                        out = response.getOutputStream();
                    }
                    IOUtils.copy(new StringReader(content), out, encoding);
                    out.flush();
                    out.close();
                }
            };
        } catch(IOException e) {
            log.error("Failed to write output:", e);
            return null;
        }
    }

    private Resolution unAuthorizedResolution() {
        return new ErrorMessageResolution(HttpServletResponse.SC_FORBIDDEN, "Gebruiker heeft geen toegang tot webservice");
    }

    private String defaultError(Exception e) {
        return "Error on " + path;
    }

    private boolean keepRequestUnmodified() {
        return Arrays.stream(UNMODIFIED_REQUESTS).anyMatch((path.toLowerCase())::startsWith);
    }

    private boolean requestIs(String pathPart) {
        return path.toLowerCase().startsWith(pathPart);
    }

    private String applyAuthorizationToIncidentContent(String contentFromResponse) throws Exception {
        //return contentFromResponse;

        HttpServletRequest request = context.getRequest();
        JSONArray content = new JSONArray(contentFromResponse);

        String verbergKladblokTerm = Cfg.getSetting("kladblok_hidden_on_term", "#&*^@&^#&*&HGDGJFGS8F778ASDxcvsdfdfsdfsd");
        boolean kladblokAlwaysAuthorized = "true".equals(Cfg.getSetting("kladblok_always_authorized", "false"));
        boolean incidentMonitorKladblokAuthorized = kladblokAlwaysAuthorized || request.isUserInRole(ROLE_ADMIN) || true;
        boolean eigenVoertuignummerAuthorized = request.isUserInRole(ROLE_ADMIN) || request.isUserInRole("smvng_incident_ownvehiclenumber");
        boolean zonderEenhedenAuthorized = request.isUserInRole(ROLE_ADMIN) || request.isUserInRole("smvng_incident_incidentwithoutunit");
        boolean incidentMonitorAuthorized = request.isUserInRole(ROLE_ADMIN) || request.isUserInRole("IncidentMonitor");

        /*if (incidentMonitorAuthorized && incidentMonitorKladblokAuthorized) {
            return content.toString();
        }*/

        //try(Connection c = DB.getConnection()) {        
            Connection c = DB.getConnection();    
            JSONArray authorizedContent = new JSONArray();
            JSONObject details = getUserDetails(request, c);
            List<String> userVehicleList = Arrays.asList(details.optString("voertuignummer", "-").replaceAll("\\s", ",").split(","));

            for(int i=0; i<content.length(); i++) {
                JSONObject incident = (JSONObject)content.get(i);
                
                // Incident voor eigen voertuig?
                JSONArray attachedVehicles;
                if (incident.has("BetrokkenEenheden") && incident.get("BetrokkenEenheden") != null) {
                    attachedVehicles = (JSONArray)incident.get("BetrokkenEenheden");
                } else {
                    attachedVehicles = new JSONArray();
                }

                boolean incidentForUserVehicle = false;
                for(int v=0; v<attachedVehicles.length(); v++) {
                    JSONObject vehicle = (JSONObject)attachedVehicles.get(v);
                    if (userVehicleList.contains(vehicle.get("Roepnaam"))) {
                        incidentForUserVehicle = true;
                    }
                }

                // Kladblok met verbergregel erin?
                JSONArray notepad;
                if (incident.has("Kladblokregels") && incident.get("Kladblokregels") != null) {
                  notepad = (JSONArray)incident.get("Kladblokregels");
                } else {
                  notepad = new JSONArray();
                }

                boolean hideNotepad = false;
                for(int v=0; v<notepad.length(); v++) {
                    JSONObject notepadrule = (JSONObject)notepad.get(v);
                    String inhoud = notepadrule.getString("Inhoud");
                    if (inhoud.toLowerCase().startsWith(verbergKladblokTerm.toLowerCase())) {
                      hideNotepad = true;
                    }
                }

                if(hideNotepad || (!incidentForUserVehicle && !eigenVoertuignummerAuthorized && !incidentMonitorKladblokAuthorized)) {
                    incident.put("Kladblokregels", new JSONArray());
                }

                if((incidentForUserVehicle || eigenVoertuignummerAuthorized || incidentMonitorAuthorized) && ((zonderEenhedenAuthorized && attachedVehicles.length() == 0) || attachedVehicles.length() > 0)) {
                    authorizedContent.put(incident);
                }
            }

            return authorizedContent.toString();
        //} catch(Exception e) {
            //return new JSONObject().toString();
            //return defaultError(e);
       // }
    }

    // Applies filter to /eenheidLocatie to filter out locations for vehicles not attached to an incident
    private String applyFilterToEenheidLocatieContent(String contentFromResponse) throws Exception {
        JSONObject content = new JSONObject(contentFromResponse);
        JSONArray features = (JSONArray)content.get("features");
        JSONArray authorizedFeatures = new JSONArray();

        for(int i=0; i<features.length(); i++) {
            JSONObject feature = (JSONObject)features.get(i);
            JSONObject props = (JSONObject)feature.get("properties");
            Integer incidentnr = (Integer)props.get("incidentNummer");

            boolean authorizedForEenheidLocatie = true;

            if (incidentnr == null || incidentnr == 0) {
                authorizedForEenheidLocatie = false;
            }

            if(authorizedForEenheidLocatie) {
                authorizedFeatures.put(feature);
            }
        }

        content.put("features", authorizedFeatures);

        return content.toString();
    }
}
