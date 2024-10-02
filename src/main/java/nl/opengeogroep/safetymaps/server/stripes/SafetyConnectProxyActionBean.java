package nl.opengeogroep.safetymaps.server.stripes;

import net.sourceforge.stripes.action.ActionBean;
import net.sourceforge.stripes.action.ActionBeanContext;
import net.sourceforge.stripes.action.Resolution;
import net.sourceforge.stripes.action.StreamingResolution;
import net.sourceforge.stripes.action.UrlBinding;
import nl.b3p.web.stripes.ErrorMessageResolution;
import nl.opengeogroep.safetymaps.server.cache.CACHE;
import nl.opengeogroep.safetymaps.server.db.Cfg;
import nl.opengeogroep.safetymaps.server.db.DB;
import nl.opengeogroep.safetymaps.utils.SafetyctMessageUtil;

import org.apache.commons.dbutils.handlers.ColumnListHandler;
import org.apache.commons.dbutils.handlers.MapHandler;
import org.apache.commons.dbutils.handlers.MapListHandler;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.mutable.MutableObject;
import org.apache.commons.lang3.time.DateUtils;
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
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.sql.Connection;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.zip.GZIPOutputStream;
/*import java.awt.Polygon;
import java.awt.Point;*/
import org.geotools.geometry.jts.JTS;
import org.geotools.geometry.jts.JTSFactoryFinder;

import org.json.JSONArray;
import org.json.JSONObject;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.io.WKTReader;

import static nl.opengeogroep.safetymaps.server.db.DB.ROLE_ADMIN;
import static nl.opengeogroep.safetymaps.server.db.DB.getUserDetails;
import nl.opengeogroep.safetymaps.server.cache.IncidentCacheItem;

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

    private class CachedResponseString {
      Date created;
      String response; 

      public CachedResponseString(String response) {
        this.created = new Date();
        this.response = response;
      }

      public boolean isOutDated() {
        int outdatedAfterSecondes = 10;
        Date now = new Date();
        Date outDated = new Date(now.getTime() - outdatedAfterSecondes * 1000);
        return this.created.before(outDated);
      }

      public boolean isReadyToCleanup() {
        int outdatedAfterHours = 1;
        Date now = new Date();
        Date outDated = new Date(now.getTime() - outdatedAfterHours * 60 * 60 * 1000);
        return this.created.before(outDated);
      }
    }

    private void CleanupCacheProxy() {
      cache_proxy.values().removeIf(value -> value.isReadyToCleanup());
    }

    private static final Map<String,CachedResponseString> cache_proxy = new HashMap<>();

    private List<String> JSONArryToStringList(JSONArray arr) {
      //JSONArray arr = new JSONArray(ja);
      List<String> list = new ArrayList<String>();
      for(int i = 0; i < arr.length(); i++){
          list.add(arr.get(i).toString());
      }

      return list;
    }

    public Resolution proxy() throws Exception {
          if(requestIs(INCIDENT_REQUEST) && !context.getRequest().isUserInRole(ROLE) && !context.getRequest().isUserInRole(ROLE_ADMIN)) {
              return unAuthorizedResolution();
          }

          if (requestIs(KLADBLOKREGEL_REQUEST) && !context.getRequest().isUserInRole("smvng_incident_logtogms_notepadchat") && !context.getRequest().isUserInRole(ROLE_ADMIN)) {
              return unAuthorizedResolution();
          }

          GeometryFactory geometryFactory = new GeometryFactory();

          String qs = context.getRequest().getQueryString();
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

          String useRabbitMq = Cfg.getSetting("safetyconnect_rq", "false");
          String useRabbitMqProxy = Cfg.getSetting("safetyconnect_rq_proxy", useRabbitMq);
          String rabbitMqSourceDefault = "prod".equals(defaultApi) ? "productie" : "opl".equals(defaultApi) ? "opleiding" : "test".equals(defaultApi) ? "test" : null;
          String rabbitMqSource = useAdmin ? rabbitMqSourceDefault : useProd ?  "productie" : useOpl ? "opleiding" : useTest ? "test" : rabbitMqSourceDefault;

          String defaultAuth = "prod".equals(defaultApi) ? authorizationProd : "opl".equals(defaultApi) ? authorizationOpl : "test".equals(defaultApi) ? authorizationTest : null;
          String defaultUrl = "prod".equals(defaultApi) ? urlProd : "opl".equals(defaultApi) ? urlOpl : "test".equals(defaultApi) ? urlTest : null;

          String authorization = useAdmin ? defaultAuth : useProd ? authorizationProd : useOpl ? authorizationOpl : useTest ? authorizationTest : defaultAuth;
          String url = useAdmin ? defaultUrl : useProd ? urlProd : useOpl ? urlOpl : useTest ? urlTest : defaultUrl;

          if("false".equals(useRabbitMqProxy) && (authorization == null || url == null)) {
              return new ErrorMessageResolution(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Geen toegangsgegevens voor webservice geconfigureerd door beheerder");
          }

          if ("true".equals(useRabbitMqProxy) && requestIs(INCIDENT_REQUEST)) {
            HttpServletRequest request = context.getRequest();
            String numString = path.substring(path.lastIndexOf('/') + 1);
            Integer number = 0;
            String daysInPast = getQueryStringMap(qs).get("daysInPast") != null ? getQueryStringMap(qs).get("daysInPast") : "5";

            if (numString.equals("incident") == false) {
              number = Integer.parseInt(numString);
            }

            final Integer incidentNummer = number;

            JSONArray incidents = new JSONArray();
            //List<Map<String, Object>> results = DB.qr().query("select * from safetymaps.incidents where source='sc' and sourceenv=?", new MapListHandler(), rabbitMqSource);

            List<Map<String, Object>> results = CACHE.GetIncidents(rabbitMqSource);
            
            String tenant = Cfg.getSetting("safetyconnect_rq_tenants", "").split(",")[0];

            for (Map<String, Object> res : results) {              
              boolean handle = false;

              boolean isauthfor_interregio = request.isUserInRole(ROLE_ADMIN) || request.isUserInRole("smvng_incident_interregio");
              if (isauthfor_interregio || res.get("tenantid").equals(tenant)) {
                handle = true;
              } else {
                handle = SafetyctMessageUtil.IncidentDbRowHasUnitForRegion(res, Cfg.getSetting("safetyconnect_rq_regios", ""));
              }

              if (handle) {
                JSONObject incident = SafetyctMessageUtil.MapIncidentDbRowAllColumnsAsJSONObject(res);

                /**
                * smvng_incident_hidenotepad	Kladblok verbergen
                * smvng_incident_ownvehiclenumber	Gebruiker mag eigen voertuignummer wijzigen.
                * smvng_incident_prio45	Toon incidenten met prio 4 of 5 en koppel voertuigen aan deze incidenten.
                * smvng_incident_samengevoegd Toon samengevoegde incidenten en koppel voertuigen aan deze incidenten.
                * smvng_incident_trainingincident	Toon training incidenten en koppel voertuigen aan deze incidenten.
                */
                String hideNotepadOnTerm = Cfg.getSetting("kladblok_hidden_on_term", "#&*^@&^#&*&HGDGJFGS8F778ASDxcvsdfdfsdfsd");
  
                boolean isauthfor_hidenotepad = request.isUserInRole("smvng_incident_hidenotepad");
                boolean isauthfor_alldiscnotepad = request.isUserInRole(ROLE_ADMIN) || request.isUserInRole("smvng_incident_alldiscnotepad");
                boolean isauthfor_alldiscunits = request.isUserInRole(ROLE_ADMIN) || request.isUserInRole("smvng_incident_alldiscunits");
                boolean isauthfor_ownvehiclenumber = request.isUserInRole(ROLE_ADMIN) || request.isUserInRole("smvng_incident_ownvehiclenumber");
                boolean isauthfor_prio45 = request.isUserInRole(ROLE_ADMIN) || request.isUserInRole("smvng_incident_prio45");
                boolean isauthfor_concatted = request.isUserInRole(ROLE_ADMIN) || request.isUserInRole("smvng_incident_samengevoegd");
                boolean isauthfor_trainingincident = request.isUserInRole(ROLE_ADMIN) || request.isUserInRole("smvng_incident_trainingincident");              
                boolean isauthfor_im = request.isUserInRole(ROLE_ADMIN) || request.isUserInRole("IncidentMonitor");
                boolean isauthfor_withoutunits = request.isUserInRole(ROLE_ADMIN) || request.isUserInRole("smvng_incident_incidentwithoutunit");
                boolean isauthfor_incident = incidentIsForUserVehicle(incident) != "" || (isauthfor_im && (isauthfor_alldiscunits || incidentHasBrwUnit(incident) || isauthfor_withoutunits)) || isauthfor_ownvehiclenumber;
  
                if (incidentNummer == 0 || incidentNummer == incident.getInt("incidentNummer")) { 
                  JSONObject discipline = incident.has("brwDisciplineGegevens") ? (JSONObject)incident.get("brwDisciplineGegevens") : null;
                  JSONObject location = incident.has("incidentLocatie") ? (JSONObject)incident.get("incidentLocatie") : null;
                  JSONObject locationCoords = location.has("incidentCoordinaten") ? (JSONObject)location.get("incidentCoordinaten") : null;
                  SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                  Date checkDate = DateUtils.addDays(new Date(), (-1 * Integer.parseInt(daysInPast)));
                  Date startDtg = discipline.has("startDtg") ? sdf.parse(discipline.getString("startDtg").replaceAll("T", " ")) : checkDate;
                  String mc1 = discipline.has("meldingsclassificatie1") ? discipline.getString("meldingsclassificatie1") : "";
                  Coordinate incCoordinate = locationCoords != null ? new Coordinate(locationCoords.getDouble("lon"), locationCoords.getDouble("lat")) : null;
                  Point incLoc = geometryFactory.createPoint(incCoordinate);
                  String closureCode = discipline.has("afsluitCode") ? discipline.getString("afsluitCode") : "";
                  String concattedClosureCode = "Samengevoegd incident";
                  boolean incIsNotConcattedOrIsUserisAuthForConcatted = closureCode != concattedClosureCode || (closureCode == concattedClosureCode && isauthfor_concatted);
  
                  JSONArray notepad;
                  JSONArray discnotepad = new JSONArray();
                  if (incident.has("kladblokregels") && !JSONObject.NULL.equals(incident.get("kladblokregels"))) {
                    notepad = (JSONArray)incident.get("kladblokregels");
                  } else {
                    notepad = new JSONArray();
                  }
                  boolean hideNotepad = false;
                  for(int v=0; v<notepad.length(); v++) {
                      JSONObject notepadrule = (JSONObject)notepad.get(v);
                      String inhoud = notepadrule.has("inhoud") ? notepadrule.getString("inhoud") : notepadrule.has("Inhoud") ? notepadrule.getString("Inhoud") : "";
                      List<String> discs = notepadrule.has("disciplines") ? JSONArryToStringList((JSONArray)notepadrule.get("disciplines")) : null;
                      
                      if (isauthfor_alldiscnotepad || (discs != null && discs.contains("B"))) {
                        discnotepad.put(notepadrule);
                      }
                      if (inhoud.toLowerCase().startsWith(hideNotepadOnTerm.toLowerCase())) {
                        hideNotepad = true;
                      }
                  }
  
                  if (isauthfor_hidenotepad || hideNotepad || incidentNummer == 0) { 
                    incident.put("kladblokregels", new JSONArray()); 
                  } else {
                    incident.put("kladblokregels", discnotepad);
                  }
  
                  if (checkDate.before(startDtg) || incident.getString("status").equals("operationeel"))
                  {
                    Boolean isPutWithDefaultAuth = false;
                    if (isauthfor_incident && incIsNotConcattedOrIsUserisAuthForConcatted && isauthfor_trainingincident && incident.getString("incidentId").startsWith(("FLK")) && isauthfor_prio45 && discipline != null && discipline.has("prioriteit") && (Integer)discipline.get("prioriteit") > 3) {
                      //incidents.put(incident);
                      isPutWithDefaultAuth = true; 
                    } else if (isauthfor_incident && incIsNotConcattedOrIsUserisAuthForConcatted && isauthfor_trainingincident && incident.getString("incidentId").startsWith(("FLK")) && discipline != null && discipline.has("prioriteit") && (Integer)discipline.get("prioriteit") <= 3) {
                      //incidents.put(incident); 
                      isPutWithDefaultAuth = true;
                    } else if (isauthfor_incident && incIsNotConcattedOrIsUserisAuthForConcatted && incident.getString("incidentId").startsWith(("FLK")) == false && isauthfor_prio45 && discipline != null && discipline.has("prioriteit") && (Integer)discipline.get("prioriteit") > 3) {
                      //incidents.put(incident); 
                      isPutWithDefaultAuth = true;
                    } else if (isauthfor_incident && incIsNotConcattedOrIsUserisAuthForConcatted && incident.getString("incidentId").startsWith(("FLK")) == false && discipline != null && discipline.has("prioriteit") && (Integer)discipline.get("prioriteit") <= 3) {
                      //incidents.put(incident); 
                      isPutWithDefaultAuth = true;
                    }

                    if (isPutWithDefaultAuth) {
                      Boolean userIsAuth = null;
                      List<Map<String, Object>> auths = DB.qr().query("SELECT role, LOWER(mcs) mcs, locs FROM safetymaps.incidentauthorization WHERE mcs IS NOT NULL || locs IS NOT NULL", new MapListHandler());
                      for(Map<String,Object> auth: auths) {
                        String[] restrictedGroups = auth.get("role").toString().split(",");
                        for(int i1 = 0; i1< restrictedGroups.length; i1++) {
                          if (request.isUserInRole(restrictedGroups[i1])) {
                            
                            if (auth.get("mcs") != null && auth.get("mcs").toString().length() > 0) {
                              userIsAuth = auth.get("mcs").toString().contains(mc1.toLowerCase());
                            }

                            if ((userIsAuth == null || userIsAuth) && auth.get("locs") != null && auth.get("locs").toString().length() > 0) {
                              String[] locs = auth.get("locs").toString().split(",");
                              for(int i2 = 0; i2< locs.length; i2++) {
                                Map<String, Object> loc = DB.qr().query("SELECT loc FROM safetymaps.incidentlocations WHERE id=?", new MapHandler(), Integer.parseInt(locs[i2]));
                                WKTReader reader = new WKTReader(geometryFactory);
                                Polygon locPol = (Polygon) reader.read(loc.get("loc").toString());
                                
                                userIsAuth = locPol.contains(incLoc);
                              }
                            }
                          }
                        }
                      }
                      if (userIsAuth == null || userIsAuth) {
                        incidents.put(incident); 
                      }
                    }
                  }
                }
              }
            }

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
                  
                    try {
                      IOUtils.copy(new StringReader(incidents.toString()), out, "UTF-8");

                      out.flush();
                      out.close();
                    } catch (IOException e) {
                      // Do nothing
                    }
                }
              };
          }

          if ("true".equals(useRabbitMqProxy) && requestIs(EENHEIDSTATUS_REQUEST)) {
            HttpServletRequest request = context.getRequest();
            String unitId = context.getRequest().getQueryString().replaceAll("id=", "");
            JSONArray units = new JSONArray();
            //List<Map<String, Object>> results = DB.qr().query("select u.*, mds.gmsstatustext from safetymaps.units u left join safetymaps.mdstatusses mds on mds.gmsstatuscode = u.gmsstatuscode where u.source='sc' and u.sourceenv=?", new MapListHandler(), rabbitMqSource);
            List<Map<String, Object>> results = CACHE.GetUnits(rabbitMqSource);
            Map<Integer, String> unitStatusList = CACHE.GetUnitStatusList();

            for (Map<String, Object> res : results) {
              Integer gmsStatusCode = (Integer)res.get("gmsstatuscode");
              String gmsStatusText = unitStatusList.get(gmsStatusCode);
              res.put("gmsstatustext", gmsStatusText);
              JSONObject unit = SafetyctMessageUtil.MapUnitDbRowAllColumnsAsJSONObject(res);

              /*
              * smvng_incident_ownvehiclenumber	Gebruiker mag eigen voertuignummer wijzigen.
              */
              boolean isauthfor_ownvehiclenumber = request.isUserInRole(ROLE_ADMIN) || request.isUserInRole("smvng_incident_ownvehiclenumber");

              /*if (isauthfor_ownvehiclenumber && unitId.equals(unit.getString("roepnaam"))) { units.put(unit); }
              else if (getUserVehicleList().contains(unitId)) { units.put(unit); }*/
              if (unitId.equals(unit.getString("roepnaam"))) { units.put(unit); }
            }
            
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
                    
                    try {
                      IOUtils.copy(new StringReader(units.toString()), out, "UTF-8");

                      out.flush();
                      out.close();
                    } catch (IOException e) {
                      // Do nothing
                    }
                }
            };
        }
        
        if ("true".equals(useRabbitMqProxy) && requestIs(EENHEIDLOCATIE_REQUEST)) {
          HttpServletRequest request = context.getRequest();
          JSONArray units = new JSONArray();
          //List<Map<String, Object>> dbUnits = DB.qr().query("select * from safetymaps.units where source='sc' and sourceenv=?", new MapListHandler(), rabbitMqSource);
          //List<Map<String, Object>> dbIncidents = DB.qr().query("select * from safetymaps.incidents where source='sc' and sourceenv=? and status='operationeel'", new MapListHandler(), rabbitMqSource);
          
          List<Map<String, Object>> dbUnits = CACHE.GetUnits(rabbitMqSource);

          for (Map<String, Object> dbUnit : dbUnits) {
            JSONObject unit = SafetyctMessageUtil.MapUnitDbRowAllColumnsAsJSONObject(dbUnit);

            Boolean unitHasActiveIncident = false;
            Optional<IncidentCacheItem> oici = CACHE.FindActiveIncident(rabbitMqSource, unit.getString("roepnaam"));
            if (oici.isPresent()) {
              unitHasActiveIncident = true;
              unit.put("incidentId", oici.get().GetId());
              unit.put("incidentRol",oici.get().GetUnitRol(unit.getString("roepnaam")));
            }
           
            /**
             * smvng_vehicleinfo_unasigned	Toon locaties van alle voertuigen die niet aan een incident gekoppeld zijn.
             * smvng_vehicleinfo_maplocations	Toon locaties van alle voertuigen die aan een incident gekoppeld zijn.
             * smvng_vehicleinfo_incidentlocations	Toon locaties van betrokken voertuigen wanner het incident is geopend.
             * smvng_incident_ownvehiclenumber   Mag eigen voertuignummer wijzigen
             */
            boolean isauthfor_unasigned = request.isUserInRole(ROLE_ADMIN) || request.isUserInRole("smvng_vehicleinfo_unasigned");
            boolean isauthfor_maplocations = request.isUserInRole(ROLE_ADMIN) || request.isUserInRole("smvng_vehicleinfo_maplocations");
            boolean isauthfor_incidentlocations = request.isUserInRole(ROLE_ADMIN) || request.isUserInRole("smvng_vehicleinfo_incidentlocations");
            boolean isauthfor_ownvehiclenumber = request.isUserInRole(ROLE_ADMIN) || request.isUserInRole("smvng_incident_ownvehiclenumber");

            if (isauthfor_unasigned && !unitHasActiveIncident) {
              units.put(unit);
            } else if (isauthfor_maplocations && unitHasActiveIncident) {
              units.put(unit);
            } else if (isauthfor_ownvehiclenumber && unitHasActiveIncident) {
              units.put(unit);
            } else if (isauthfor_incidentlocations && unitHasActiveIncident) {
              units.put(unit);
            }
          }

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

                try {
                  IOUtils.copy(new StringReader(units.toString()), out, "UTF-8");

                  out.flush();
                  out.close();
                } catch (IOException e) {
                  // Do nothing
                }
            }
          };
        }

        String uri = url + "/" + path + (regioCode == null ? (qs == null ? "" : "?") : "?regioCode=" + regioCode + (qs == null ? "" : "&")) + qs;
        final HttpUriRequest req;
        final String content;
        String responseContent = "";

        synchronized(cache_proxy) {
          CleanupCacheProxy();
          CachedResponseString cache = cache_proxy.get(uri);

          final MutableObject<String> contentType = new MutableObject<>("text/plain");

          if (!cache_proxy.containsKey(uri) || cache == null || cache.isOutDated()) {
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
                responseContent = client.execute(req, new ResponseHandler<String>() {
                    @Override
                    public String handleResponse(HttpResponse hr) {
                        log.debug("proxy for user " + context.getRequest().getRemoteUser() + " URL " + req.getURI() + ", response: " + hr.getStatusLine().getStatusCode() + " " + hr.getStatusLine().getReasonPhrase());
                        
                        if (hr.getEntity() != null && hr.getEntity().getContentType() != null) {
                            contentType.setValue(hr.getEntity().getContentType().getValue());
                        }
                        try {
                            return IOUtils.toString(hr.getEntity().getContent(), "UTF-8");
                        } catch(IOException e) {
                            //log.error("Exception reading HTTP content", e);
                            return "Exception " + e.getClass() + ": " + e.getMessage();
                        }
                    }
                });
              } catch(IOException e) {
                log.error("Failed to request SafetyConnect:", e);
                return null;
              }

              cache = new CachedResponseString(responseContent);
              cache_proxy.put(uri, cache);
            }
                
            String cachedResponse = cache.response;
            // Filter response from the webservice to remove any data that the user is not authorized for
            if (requestIs(INCIDENT_REQUEST)) {
                content = applyAuthorizationToIncidentContent(cachedResponse);
            } else if (requestIs(EENHEIDLOCATIE_REQUEST)) {
                content = applyFilterToEenheidLocatieContent(cachedResponse);
            } else if (requestIs(KLADBLOKREGEL_REQUEST)) {
                content = cachedResponse;
            } else if (keepRequestUnmodified()) {
                content = cachedResponse;
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
                try {
                  IOUtils.copy(new StringReader(content), out, encoding);
                  out.flush();
                  out.close();
                } catch (IOException e) {
                  // Do nothing
                }
              }
            };
        }      
    }

    private Map<String, String> getQueryStringMap(String query) {  
      String[] params = query.split("&");  
      Map<String, String> map = new HashMap<String, String>();
  
      for (String param : params) {  
          String name = param.split("=")[0];  
          String value = param.split("=").length == 1 ? null : param.split("=")[1];  
          map.put(name, value);  
      }  
      return map;  
    }

    private Boolean incidentHasBrwUnit(JSONObject incident) {
      JSONArray units;
      if (incident.has("betrokkenEenheden") && !JSONObject.NULL.equals(incident.get("betrokkenEenheden"))) {
          units = (JSONArray)incident.get("betrokkenEenheden");
      } else {
          units = new JSONArray();
      }

      Boolean containsBrwUnit = false;
      for(int v=0; v<units.length(); v++) {
        JSONObject vehicle = (JSONObject)units.get(v);
          String disc = incident.has("discipline") ? (String)incident.get("discipline") : "B";
          if (disc.equals("B")) {
            containsBrwUnit = true;
          }
      }

      return containsBrwUnit;
    }

    private String incidentIsForUserVehicle(JSONObject incident) {
      // Incident voor eigen voertuig?
      JSONArray units;
      if (incident.has("betrokkenEenheden") && !JSONObject.NULL.equals(incident.get("betrokkenEenheden"))) {
          units = (JSONArray)incident.get("betrokkenEenheden");
      } else {
          units = new JSONArray();
      }

      String incidentForUserVehicle = "";
      for(int v=0; v<units.length(); v++) {
          JSONObject vehicle = (JSONObject)units.get(v);
          if (incidentForUserVehicle == "" && getUserVehicleList().contains(vehicle.get("roepnaam"))) {
              incidentForUserVehicle = (String)incident.get("incidentId");
          }
      }

      return incidentForUserVehicle;
    }

    private List<String> getUserVehicleList() {
      HttpServletRequest request = context.getRequest();

      try(Connection c = DB.getConnection()) {
        JSONObject details = getUserDetails(request, c);   
        return Arrays.asList(details.optString("voertuignummer", "-").replaceAll("\\s", ",").replaceAll("-", "").split(","));
      } catch(Exception e) {
        return null;
      }
    }

    private Resolution unAuthorizedResolution() {
        return new ErrorMessageResolution(HttpServletResponse.SC_FORBIDDEN, "Gebruiker heeft geen toegang tot webservice");
    }

    private String defaultError(Exception e) {
      return e.getMessage();
      //return "Error on " + path;
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
        boolean isauthfor_alldiscnotepad = request.isUserInRole(ROLE_ADMIN) || request.isUserInRole("smvng_incident_alldiscnotepad");

        /*if (incidentMonitorAuthorized && incidentMonitorKladblokAuthorized) {
            return content.toString();
        }*/

        try(Connection c = DB.getConnection()) {        
            //Connection c = DB.getConnection();    
            JSONArray authorizedContent = new JSONArray();
            JSONObject details = getUserDetails(request, c);
            List<String> userVehicleList = Arrays.asList(details.optString("voertuignummer", "-").replaceAll("\\s", ",").split(","));

            for(int i=0; i<content.length(); i++) {
                JSONObject incident = (JSONObject)content.get(i);
                
                // Incident voor eigen voertuig?
                JSONArray attachedVehicles;
                if (incident.has("BetrokkenEenheden") && !JSONObject.NULL.equals(incident.get("BetrokkenEenheden"))) {
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
                JSONArray filteredNotepad = new JSONArray();
                if (incident.has("Kladblokregels") && !JSONObject.NULL.equals(incident.get("Kladblokregels"))) {
                  notepad = (JSONArray)incident.get("Kladblokregels");
                } else {
                  notepad = new JSONArray();
                }

                boolean hideNotepad = false;
                for(int v=0; v<notepad.length(); v++) {
                    JSONObject notepadrule = (JSONObject)notepad.get(v);
                    // Check discipline
                    String disc = notepadrule.getString("Discipline");
                    if (isauthfor_alldiscnotepad || disc.contains("B")) {
                      filteredNotepad.put(notepadrule);
                    }
                    // Check inhoud
                    String inhoud = notepadrule.getString("Inhoud");
                    if (inhoud.toLowerCase().startsWith(verbergKladblokTerm.toLowerCase())) {
                      hideNotepad = true;
                    }
                }

                incident.put("Kladblokregels", filteredNotepad);

                if(hideNotepad || (!incidentForUserVehicle && !eigenVoertuignummerAuthorized && !incidentMonitorKladblokAuthorized)) {
                    incident.put("Kladblokregels", new JSONArray());
                }

                if((incidentForUserVehicle || eigenVoertuignummerAuthorized || incidentMonitorAuthorized) && ((zonderEenhedenAuthorized && attachedVehicles.length() == 0) || attachedVehicles.length() > 0)) {
                    authorizedContent.put(incident);
                }
            }

            return authorizedContent.toString();
        } catch(Exception e) {
            //return new JSONObject().toString();
            return defaultError(e);
        }
    }

    // Applies filter to /eenheidLocatie to filter out locations for vehicles not attached to an incident
    private String applyFilterToEenheidLocatieContent(String contentFromResponse) throws Exception {
        try {
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
        } catch (Exception e) {
          // Do nothing
          return "";
        }
    }
}
