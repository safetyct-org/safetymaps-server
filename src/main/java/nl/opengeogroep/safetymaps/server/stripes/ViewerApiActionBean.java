package nl.opengeogroep.safetymaps.server.stripes;

import java.io.IOException;
import java.io.OutputStream;
import java.io.StringReader;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;
import net.sourceforge.stripes.action.*;
import net.sourceforge.stripes.mock.MockHttpServletRequest;
import net.sourceforge.stripes.validation.Validate;
import nl.opengeogroep.safetymaps.server.db.Cfg;
import static nl.opengeogroep.safetymaps.server.db.JsonExceptionUtils.*;
import nl.opengeogroep.safetymaps.server.db.DB;
import static nl.opengeogroep.safetymaps.server.db.DB.ROLE_ADMIN;
import static nl.opengeogroep.safetymaps.server.db.DB.ROLE_DRAWING_EDITOR;
import static nl.opengeogroep.safetymaps.server.db.DB.ROLE_EIGEN_VOERTUIGNUMMER;
import static nl.opengeogroep.safetymaps.server.db.DB.ROLE_INCIDENTMONITOR;
import static nl.opengeogroep.safetymaps.server.db.DB.ROLE_INCIDENTMONITOR_KLADBLOK;
import static nl.opengeogroep.safetymaps.server.db.DB.ROLE_TABLE;
import static nl.opengeogroep.safetymaps.server.db.DB.USER_ROLE_TABLE;
import static nl.opengeogroep.safetymaps.server.db.DB.ROLE_INCIDENT_GOOGLEMAPSNAVIGATION;
import static nl.opengeogroep.safetymaps.server.db.DB.ROLE_INCIDENTMONITOR_MANUALLYCREATED;
import static nl.opengeogroep.safetymaps.server.db.DB.ROLE_KLADBLOKCHAT_EDITOR;
import static nl.opengeogroep.safetymaps.server.db.DB.ROLE_KLADBLOKCHAT_EDITOR_GMS;
import static nl.opengeogroep.safetymaps.server.db.DB.ROLE_KLADBLOKCHAT_VIEWER;
import static nl.opengeogroep.safetymaps.server.db.DB.ROLE_INCIDENTMONITOR_PRIO_4_5;
import static nl.opengeogroep.safetymaps.server.db.DB.ROLE_INCIDENTMONITOR_WITHOUT_UNITS;
import static nl.opengeogroep.safetymaps.server.db.DB.getUserDetails;
import nl.opengeogroep.safetymaps.viewer.ViewerDataExporter;
import org.apache.commons.dbutils.QueryRunner;
import org.apache.commons.dbutils.handlers.ColumnListHandler;
import org.apache.commons.dbutils.handlers.MapListHandler;
import org.apache.commons.dbutils.handlers.ScalarHandler;
import org.apache.commons.io.IOUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * API for online safetymaps-viewer sites.
 *
 * /viewer/api/organisation.json: general settings
 * /viewer/api/features.json: objects for creator objects, with ETag caching
 * /viewer/api/object/n.json: creator object details
 * /viewer/api/library.json: creator library
 * /viewer/api/autocomplete/search: see NLExtractBagAddressSearchActionBean
 *
 * @author Matthijs Laan
 */
@StrictBinding
@UrlBinding("/viewer/api/{path}")
public class ViewerApiActionBean implements ActionBean {
    private static final Log log = LogFactory.getLog(ViewerApiActionBean.class);

    private static final String FEATURES = "features.json";
    private static final String ORGANISATION = "organisation.json";
    private static final String ORGANISATION_SMVNG = "organisation-smvng.json";
    private static final String STYLES = "styles.json";
    private static final String LIBRARY = "library.json";
    private static final String OBJECT = "object/";

    private ActionBeanContext context;

    @Validate
    private String path;

    @Validate
    private int version = 1;

    @Validate
    private int indent = 0;

    @Validate
    private int srid = 28992;

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

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public int getIndent() {
        return indent;
    }

    public void setIndent(int indent) {
        this.indent = indent;
    }

    public int getSrid() {
        return srid;
    }

    public void setSrid(int srid) {
        this.srid = srid;
    }

    public Resolution api() {

        try(Connection c = DB.getConnection()) {
            if(path != null) {
                if(FEATURES.equals(path)) {
                    return features(c);
                }
                if(ORGANISATION.equals(path)) {
                    return organisation(c, false);
                }
                if(ORGANISATION_SMVNG.equals(path)) {
                    return organisation(c, true);
                }
                if(STYLES.equals(path)) {
                    return styles(c);
                }
                if(LIBRARY.equals(path)) {
                    return library(c);
                }
                if(path.indexOf(OBJECT) == 0) {
                    return object(c);
                }
            }

            return new ErrorResolution(HttpServletResponse.SC_NOT_FOUND, "Not found: /api/" + path);
        } catch (IOException e) {
          String exceptionSimpleName = e.getCause().getClass().getSimpleName();

          if ("ClientAbortException".equals(exceptionSimpleName)) {
            return null;
          } else {
            return new StreamingResolution("application/json", logExceptionAndReturnJSONObject(log, "Error on /api/" + path, e).toString(indent));
          }
        } catch(Exception e) {
            return new StreamingResolution("application/json", logExceptionAndReturnJSONObject(log, "Error on /api/" + path, e).toString(indent));
        }
    }

    private Resolution features(Connection c) {

        ViewerDataExporter vde = new ViewerDataExporter(c);
        try {
            final String etag = '"' + vde.getObjectsETag() + '"';

            String ifNoneMatch = getContext().getRequest().getHeader("If-None-Match");
            if(ifNoneMatch != null && ifNoneMatch.contains(etag)) {
                return new ErrorResolution(HttpServletResponse.SC_NOT_MODIFIED);
            }

            final JSONObject o = version < 3 ? getFeaturesLegacy(c) : getFeaturesJson(vde);

            return new Resolution() {
                @Override
                public void execute(HttpServletRequest request, HttpServletResponse response) throws Exception {
                    try {
                      String encoding = "UTF-8";
                      response.setCharacterEncoding(encoding);
                      response.setContentType("application/json");
                      response.addHeader("ETag", etag);

                      OutputStream out;
                      String acceptEncoding = request.getHeader("Accept-Encoding");
                      if(acceptEncoding != null && acceptEncoding.contains("gzip")) {
                          response.setHeader("Content-Encoding", "gzip");
                          out = new GZIPOutputStream(response.getOutputStream(), true);
                      } else {
                          out = response.getOutputStream();
                      }
                      IOUtils.copy(new StringReader(o.toString(indent)), out, encoding);
                      out.flush();
                      out.close();
                    } catch (IOException e) {
                      // Do nothing
                    }
                }
            };        
        } catch(Exception e) {
            return new StreamingResolution("application/json", logExceptionAndReturnJSONObject(log, "Error getting viewer objects", e).toString(indent));
        }
    }

    private JSONObject getFeaturesLegacy(Connection c) throws Exception {
        JSONObject o = new JSONObject();
        o.put("type", "FeatureCollection");
        JSONArray ja = new JSONArray();
        o.put("features", ja);
        boolean version2 = version == 2;
        String from = version2 ? "dbk2.dbkfeatures_json" : " dbk.dbkfeatures_adres_json";
        List rows = (List)new QueryRunner().query(c, "select \"feature\" from " + from + "(" + srid + ")", new ColumnListHandler());
        for (Object row: rows) {
            JSONObject d = new JSONObject(row.toString());
            JSONObject j = new JSONObject();
            ja.put(j);
            j.put("type", "Feature");
            j.put("id", "DBKFeature.gid--" + d.get("gid"));
            j.put("geometry", d.get("geometry"));
            JSONObject properties = new JSONObject();
            j.put("properties", properties);
            for(Object key: d.keySet()) {
                if(!"geometry".equals(key)) {
                    properties.put((String)key, d.get((String)key));
                }
            }
        }

        return o;
    }

    private JSONObject getFeaturesJson(ViewerDataExporter vde) throws Exception {
        JSONObject o = new JSONObject("{success:true}");
        o.put("results", vde.getViewerObjectMapOverview());
        return o;
    }

    public static JSONObject getOrganisation(Connection c, int srid) throws Exception {
        Object org = new QueryRunner().query(c, "select \"organisation\" from organisation.organisation_nieuw_json(" + srid + ")", new ScalarHandler<>());
        JSONObject j = new JSONObject();
        j.put("organisation", new JSONObject(org.toString()));
        return j;
    }

    public static JSONObject getOrganisationWithUserAuthorization(final String username, Connection c, int srid) throws Exception {
        final Set<String> roles = new HashSet<String>(new QueryRunner().query(c, "select role from " + USER_ROLE_TABLE + " where username = ?", new ColumnListHandler<String>(), username));
        return getOrganisationWithAuthorizedModulesAndLayers(new HttpServletRequestWrapper(new MockHttpServletRequest(null, null)) {
            @Override
            public String getRemoteUser() {
                return username;
            }

            @Override
            public boolean isUserInRole(String role) {
                return roles.contains(role);
            }
        }, c, srid, false);
    }

    public static JSONObject getOrganisationWithAuthorizedModulesAndLayers(HttpServletRequest request, Connection c, int srid, boolean isSmvng) throws Exception {
        String functionString = !isSmvng ? "organisation_nieuw_json" : "organisation_smvng_json";
        Object org = new QueryRunner().query(c, "select \"organisation\" from organisation." + functionString + "(" + srid + ")", new ScalarHandler<>());
        JSONObject organisation = new JSONObject(org.toString());
        organisation.put("integrated", true);
        organisation.put("username", request.getRemoteUser());
        organisation.put("helpUrl", Cfg.getSetting("help_url"));

        List<Map<String,Object>> roles = new QueryRunner().query(c, "select role, modules, coalesce(wms, '') as wms, coalesce(defaultwms, '') as defaultwms from " + ROLE_TABLE + " where modules is not null", new MapListHandler());
        Set<String> authorizedModules = new HashSet();
        Set<String> authorizedLayers = new HashSet();
        Set<String> defaultLayers = new HashSet();
        for(Map<String,Object> role: roles) {
            if(request.isUserInRole(role.get("role").toString()) || request.isUserInRole(ROLE_ADMIN)) {
                String modules = (String)role.get("modules");
                authorizedModules.addAll(Arrays.asList(modules.split(", ")));
                String layers = (String)role.get("wms");
                authorizedLayers.addAll(Arrays.asList(layers.toLowerCase().split(", ")));
                String dlayers = (String)role.get("defaultwms");
                defaultLayers.addAll(Arrays.asList(dlayers.toLowerCase().split(", ")));
            }
        }
        JSONArray modules = organisation.getJSONArray("modules");
        JSONArray jaAuthorizedModules = new JSONArray();
        for(int i = 0; i < modules.length(); i++) {
            JSONObject module = modules.getJSONObject(i);
            if(authorizedModules.contains(module.getString("name"))) {
                checkModuleAuthorizations(request, c, module, isSmvng);
                jaAuthorizedModules.put(module);
            }
        }
        JSONArray layers = organisation.getJSONArray("layers");
        JSONArray jaAuthorizedLayers = new JSONArray();
        for(int i = 0; i < layers.length(); i++) {
            JSONObject layer = layers.getJSONObject(i);
            if(authorizedLayers.contains(layer.getString("uid"))) {
                if(!layer.getBoolean("defaultEnabled")) {
                    layer.put("defaultEnabled", defaultLayers.contains((layer.getString("uid"))));
                }
                jaAuthorizedLayers.put(layer);
            }
        }

        organisation.put("layers", jaAuthorizedLayers);

        if(!request.isUserInRole(ROLE_ADMIN)) {
            organisation.put("modules", jaAuthorizedModules);
        } else {
            JSONArray adminModules = organisation.getJSONArray("modules");
            for(int i = 0; i < adminModules.length(); i++) {
                // Add settings to options for admin
                checkModuleAuthorizations(request, c, adminModules.getJSONObject(i), isSmvng);
            }
        }
        
        if (!isSmvng) {
            JSONObject j = new JSONObject();
            j.put("organisation", organisation);
            return j;
        } else {
            return organisation;
        }
    }

    /**
     * Modify module options to apply authorizations.
     */
    private static JSONObject checkModuleAuthorizations(HttpServletRequest request, Connection c, JSONObject module, boolean isSmvng) throws Exception {
        String prefixSmvng = "isAuthorizedFor_";
        String name = module.getString("name");
        JSONObject options = module.isNull("options") ? new JSONObject(): module.getJSONObject("options");

        if(!isSmvng && "incidents".equals(name)) {
            options.put("sourceVrhAGSAuthorized", request.isUserInRole(ROLE_ADMIN) || request.isUserInRole(VrhAGSProxyActionBean.ROLE));
            options.put("sourceSafetyConnectAuthorized", request.isUserInRole(ROLE_ADMIN) || request.isUserInRole(SafetyConnectProxyActionBean.ROLE));
            options.put("incidentMonitorAuthorized", request.isUserInRole(ROLE_ADMIN) || request.isUserInRole(ROLE_INCIDENTMONITOR));
            boolean kladbklokAlwaysAuthorized = "true".equals(Cfg.getSetting("kladblok_always_authorized", "false"));
            options.put("incidentMonitorKladblokAuthorized", kladbklokAlwaysAuthorized || request.isUserInRole(ROLE_ADMIN) || request.isUserInRole(ROLE_INCIDENTMONITOR_KLADBLOK));
            options.put("eigenVoertuignummerAuthorized", request.isUserInRole(ROLE_ADMIN) || request.isUserInRole(ROLE_EIGEN_VOERTUIGNUMMER));
            options.put("googleMapsNavigationAuthorized", request.isUserInRole(ROLE_ADMIN) || request.isUserInRole(ROLE_INCIDENT_GOOGLEMAPSNAVIGATION));
            options.put("excludeManuallyCreatedIncidents", !request.isUserInRole(ROLE_ADMIN) && !request.isUserInRole(ROLE_INCIDENTMONITOR_MANUALLYCREATED));
            options.put("prio4and5Authorized", request.isUserInRole(ROLE_ADMIN) || request.isUserInRole(ROLE_INCIDENTMONITOR_PRIO_4_5));
            options.put("withoutUnitsAuthorized", request.isUserInRole(ROLE_ADMIN) || request.isUserInRole(ROLE_INCIDENTMONITOR_WITHOUT_UNITS));
            options.put("logKladblokToGmsAuthorized", request.isUserInRole(ROLE_ADMIN) || request.isUserInRole(ROLE_KLADBLOKCHAT_EDITOR_GMS));
            options.put("editKladblokChatAuthorized", request.isUserInRole(ROLE_ADMIN) || request.isUserInRole(ROLE_KLADBLOKCHAT_EDITOR) || request.isUserInRole(ROLE_KLADBLOKCHAT_EDITOR_GMS));
            options.put("showKladblokChatAuthorized", request.isUserInRole(ROLE_ADMIN) || request.isUserInRole(ROLE_KLADBLOKCHAT_VIEWER) || request.isUserInRole(ROLE_KLADBLOKCHAT_EDITOR) || request.isUserInRole(ROLE_KLADBLOKCHAT_EDITOR_GMS));

            JSONObject details = getUserDetails(request, c);
            options.put("userVoertuignummer", details.optString("voertuignummer", null));
        } else if(!isSmvng && "drawing".equals(name)) {
            options.put("editAuthorized", request.isUserInRole(ROLE_ADMIN) || request.isUserInRole(ROLE_DRAWING_EDITOR));
        } else if (isSmvng && "Incident".equals(name)) {
            //JSONObject details = getUserDetails(request, c);
            JSONObject details = getLocalUserObject(request, c);
            options.put(prefixSmvng + "HideNotepad", request.isUserInRole("smvng_incident_hidenotepad")); 
            options.put(prefixSmvng + "Show_Notepadchat", request.isUserInRole(ROLE_ADMIN) || request.isUserInRole("smvng_incident_showchat")); 
            options.put(prefixSmvng + "Add_Notepadchat", request.isUserInRole(ROLE_ADMIN) || request.isUserInRole("smvng_incident_addchat")); 
            options.put(prefixSmvng + "GoogleNav", request.isUserInRole(ROLE_ADMIN) || request.isUserInRole("smvng_incident_googlenav")); 
            options.put(prefixSmvng + "LogToGms_Notepadchat", request.isUserInRole(ROLE_ADMIN) || request.isUserInRole("smvng_incident_logtogms_notepadchat")); 
            options.put(prefixSmvng + "Prio_45", request.isUserInRole(ROLE_ADMIN) || request.isUserInRole("smvng_incident_prio45")); 
            options.put(prefixSmvng + "Trainings_Incidents", request.isUserInRole(ROLE_ADMIN) || request.isUserInRole("smvng_incident_trainingincident")); 
            options.put(prefixSmvng + "Own_Vehiclenumber", request.isUserInRole(ROLE_ADMIN) || request.isUserInRole("smvng_incident_ownvehiclenumber")); 
            options.put(prefixSmvng + "Incidents_Without_Unit", request.isUserInRole(ROLE_ADMIN) || request.isUserInRole("smvng_incident_incidentwithoutunit")); 
            options.put(prefixSmvng + "Notepad_From_All_Discs", request.isUserInRole(ROLE_ADMIN) || (request.isUserInRole("smvng_incident_vrh_ags_replica") && request.isUserInRole("smvng_incident_vrh_ags_replica__fullnotepad")) || (!request.isUserInRole("smvng_incident_vrh_ags_replica") && request.isUserInRole("smvng_incident_safetyconnect_webservice")));
            
            if (!request.isUserInRole(ROLE_ADMIN) && !request.isUserInRole("smvng_incident_safetyconnect_webservice") && request.isUserInRole("smvng_incident_vrh_ags_replica")) {
              options.put("api", options.get("_api"));
              options.put("apiUrlIncident", options.get("_apiUrlIncident"));
              options.put("apiUrlIncidentList", options.get("_apiUrlIncidentList"));
            }
            
            options.put("userVoertuignummer", details.optString("voertuignummer", null));
        } else if (isSmvng && "IncidentMonitor".equals(name)) {
            options.put(prefixSmvng + "LeavingIncidentForLocalVehcile", request.isUserInRole(ROLE_ADMIN) || request.isUserInRole("smvng_incidentmonitor_leaveincident")); 
        } else if (isSmvng && "VehicleInfo".equals(name)) {
            options.put(prefixSmvng + "showVehicleLocationsOnIncident", request.isUserInRole(ROLE_ADMIN) || request.isUserInRole("smvng_vehicleinfo_incidentlocations")); 
            options.put(prefixSmvng + "showVehicleLocationsOnMap", request.isUserInRole(ROLE_ADMIN) || request.isUserInRole("smvng_vehicleinfo_maplocations")); 
            options.put(prefixSmvng + "showLocationsFromUnasignedVehiclesOnMap", request.isUserInRole(ROLE_ADMIN) || request.isUserInRole("smvng_vehicleinfo_unasigned")); 
        } else if (isSmvng && "Drawing".equals(name)) {
            options.put(prefixSmvng + "crud", request.isUserInRole(ROLE_ADMIN) || request.isUserInRole("smvng_drawing_crud")); 
            options.put(prefixSmvng + "whitepaper", request.isUserInRole(ROLE_ADMIN) || request.isUserInRole("smvng_drawing_whitepaper")); // SMVNG-236
        } else if (isSmvng && "Streetview".equals(name)) {
            options.put(prefixSmvng + "earth", request.isUserInRole(ROLE_ADMIN) || request.isUserInRole("smvng_streetview_earth")); 
        } else if (isSmvng && "Cyclomedia".equals(name)) {
            options.put(prefixSmvng + "earth", request.isUserInRole(ROLE_ADMIN) || request.isUserInRole("smvng_cyclomedia_earth"));  // SMVNG-306
            options.put(prefixSmvng + "birdview", request.isUserInRole(ROLE_ADMIN) || request.isUserInRole("smvng_cyclomedia_birdview")); // SMVNG-306
        } else if (isSmvng && "Dbk".equals(name)) {
            options.put(prefixSmvng + "DefaultHide", request.isUserInRole("smvng_dbk_defaulthide")); // SMVNG-303
        } else if (isSmvng && "Photo".equals(name)) {
            options.put(prefixSmvng + "Printscreen", request.isUserInRole(ROLE_ADMIN) || request.isUserInRole("smvng_photo_printscreen")); // SMVNG-236
            options.put(prefixSmvng + "Drawing", request.isUserInRole(ROLE_ADMIN) || request.isUserInRole("smvng_photo_drawing")); // SMVNG-236
            options.put(prefixSmvng + "Whitepaper", request.isUserInRole(ROLE_ADMIN) || request.isUserInRole("smvng_photo_whitepaper")); // SMVNG-236
            options.put(prefixSmvng + "Takephoto", request.isUserInRole(ROLE_ADMIN) || request.isUserInRole("smvng_photo_takephoto")); // SMVNG-236
        }

        return module;
    }

    private static JSONObject getLocalUserObject(HttpServletRequest request, Connection c) throws Exception {
      JSONObject defaultUser = getUserDetails(request, c);

      try {
        JSONObject localUser = defaultUser;
        String[] externalRolesAsUsersForGroupMembership = Cfg.getSetting("external_roles_as_users_for_group_membership", "").split(",");

        for(int i = 0; i < externalRolesAsUsersForGroupMembership.length; i++) {
            String possibleUsername = externalRolesAsUsersForGroupMembership[i].trim();

            if (possibleUsername.startsWith("AZURE_") && request.isUserInRole(possibleUsername)) {
              localUser = getUserDetails(possibleUsername, c);
              break;
            }
        }

        return localUser;
      } catch(Exception e) {
        return defaultUser;
      }
  }

  private class CachedResponseString {
      Date created;
      String response; 

      public CachedResponseString(String response) {
        this.created = new Date();
        this.response = response;
      }

      public boolean isOutDated() {
        int outdatedAfterSecondes = 60 * 5;
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

  private void CleanupCacheOrganisation() {
    cache_organisation.values().removeIf(value -> value.isReadyToCleanup());
  }

  private static final Map<String,CachedResponseString> cache_organisation = new HashMap<>();

  private Resolution organisation(Connection c, boolean isSmvng) throws Exception {
    synchronized(cache_organisation) {
      CleanupCacheOrganisation();
      CachedResponseString cache = cache_organisation.get("organisation.json");

      if (!cache_organisation.containsKey("organisation.json") || cache == null || cache.isOutDated()) {
        JSONObject response = getOrganisationWithAuthorizedModulesAndLayers(getContext().getRequest(), c, srid, isSmvng);

        cache = new CachedResponseString(response.toString(indent));
      }

      return new StreamingResolution("application/json", cache.response);
    }
  }

    public static JSONObject getLibrary(Connection c) throws Exception {
        JSONArray a = new JSONArray();

        List<Map<String,Object>> rows = new QueryRunner().query(c, "select \"ID\",\"Omschrijving\",\"Documentnaam\" from wfs.\"Bibliotheek\" order by \"Omschrijving\"", new MapListHandler());
        for(Map<String,Object> r: rows) {
            JSONObject j = new JSONObject();
            for(Map.Entry<String,Object> entry: r.entrySet()) {
                j.put(entry.getKey(), entry.getValue());
            }
            a.put(j);
        }
        JSONObject library = new JSONObject();
        library.put("success", true);
        library.put("items", a);
        return library;
    }

    private Resolution library(Connection c) throws Exception {
        return new StreamingResolution("application/json", getLibrary(c).toString(indent));
    }

    private Resolution styles(Connection c) throws Exception {
        JSONObject o = new ViewerDataExporter(c).getStyles();
        return new StreamingResolution("application/json", o.toString(indent));
    }

    private Resolution object(Connection c) throws Exception {
        Pattern p = Pattern.compile("object\\/([0-9]+)\\.json");
        Matcher m = p.matcher(path);

        if(!m.find()) {
            return new ErrorResolution(HttpServletResponse.SC_NOT_FOUND, "No object id found: /api/" + path);
        }

        int id = Integer.parseInt(m.group(1));

        JSONObject o = null;
        if(version == 3) {
            o = new ViewerDataExporter(c).getViewerObjectDetails(id);
        } else {
            Object json = new QueryRunner().query(c, "select \"DBKObject\" from " + (version == 2 ? "dbk2" : "dbk") + ".dbkobject_json(?)", new ScalarHandler(), id);
            if(json != null) {
                o = new JSONObject();
                o.put("DBKObject", new JSONObject(json.toString()));
            }
        }

        if(o == null) {
            return new ErrorResolution(HttpServletResponse.SC_NOT_FOUND, "Object id not found: " + id);
        } else {
            return new StreamingResolution("application/json", o.toString(indent));
        }
    }
}
