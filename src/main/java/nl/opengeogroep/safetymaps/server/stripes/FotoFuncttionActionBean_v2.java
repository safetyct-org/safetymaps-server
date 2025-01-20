package nl.opengeogroep.safetymaps.server.stripes;

import static nl.opengeogroep.safetymaps.server.db.JSONUtils.rowToJson;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.StringReader;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

import javax.servlet.ServletException;
import javax.servlet.annotation.MultipartConfig;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.dbutils.QueryRunner;
import org.apache.commons.dbutils.handlers.MapListHandler;
import org.apache.commons.dbutils.handlers.ScalarHandler;
import org.apache.commons.io.IOUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONArray;
import org.json.JSONObject;

import net.sourceforge.stripes.action.ActionBean;
import net.sourceforge.stripes.action.ActionBeanContext;
import net.sourceforge.stripes.action.DefaultHandler;
import net.sourceforge.stripes.action.FileBean;
import net.sourceforge.stripes.action.Resolution;
import net.sourceforge.stripes.action.StrictBinding;
import net.sourceforge.stripes.action.UrlBinding;
import net.sourceforge.stripes.validation.Validate;
import nl.b3p.web.stripes.ErrorMessageResolution;
import nl.opengeogroep.safetymaps.server.db.Cfg;
import nl.opengeogroep.safetymaps.server.db.DB;

@StrictBinding
@MultipartConfig
@UrlBinding("/viewer/api/foto")
public class FotoFuncttionActionBean_v2 implements ActionBean {
  
  private static final Log LOG = LogFactory.getLog(FotoFunctionActionBean.class);
  private static final String TABLE = "\"FotoFunctie\"";
  
  private ActionBeanContext context;

  // #region PROPERTIES 

  @Validate
  private FileBean picture;

  @Validate
  private String fileName;

  @Validate
  private String extraInfo;

  @Validate
  private String location;

  @Validate
  private String voertuigNummer;

  @Validate
  private String incidentNummer;

  @Validate
  private String type;

  public String getVoertuigNummer() {
      return voertuigNummer;
  }

  public void setVoertuigNummer(String voertuigNummer) {
      this.voertuigNummer = voertuigNummer;
  }

  public String getIncidentNummer() {
      return incidentNummer;
  }

  public void setIncidentNummer(String incidentNummer) {
      this.incidentNummer = incidentNummer;
  }

  public String getType() {
      return type;
  }

  public void setType(String type) {
      this.type = type;
  }

  public String getFileName() {
      return fileName;
  }

  public void setFileName(String fileName) {
      this.fileName = fileName;
  }

  public String getLocation() {
      return location;
  }

  public void setLocation(String location) {
      this.location = location;
  }

  public String getExtraInfo() {
      return extraInfo;
  }

  public void setExtraInfo(String extraInfo) {
      this.extraInfo = extraInfo;
  }

  public FileBean getPicture() {
    return picture;
  }

  public void setPicture(FileBean picture) {
      this.picture = picture;
  }

  // #endregion

  // #region OVERRIDES 

  @Override
    public void setContext(ActionBeanContext context) {
        this.context = context;
    }

    @Override
    public ActionBeanContext getContext() {
        return context;
    }

  // #endregion

  // #region RESOLUTIONS 

  @DefaultHandler
  public Resolution foto() throws Exception {
    JSONObject result = new JSONObject();

    return ZippedJSONResponse(result.toString());
  }

  public Resolution fotoForIncident() {
    try {
      JSONArray result = new JSONArray();

      List<Map<String, Object>> rows = getFromDb();
      for (Map<String, Object> row : rows) {
        result.put(rowToJson(row, false, false));
      }

      return ZippedJSONResponse(result.toString());
    } catch (Exception e) {
      return new ErrorMessageResolution(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Onverwachte fout opgetreden in fotoForIncident().");
    }
  }

  public Resolution download()  {
    try {
      boolean exists = DB.qr().query("select 1 from wfs." + TABLE + " where filename = ?", new ScalarHandler<>(), fileName) != null;

      if(!exists || fileName.contains("..")) {
        return new ErrorMessageResolution(HttpServletResponse.SC_NOT_FOUND, "Foto '" + fileName + "' niet gevonden in de database!");
      }

      String path = Cfg.getSetting("fotofunctie");
      File pathDir = new File(path);
      File file = new File(path + File.separator + fileName);

      if(!file.getParentFile().equals(pathDir)) {
        return new ErrorMessageResolution(HttpServletResponse.SC_BAD_REQUEST, "Bestandsnaam bevat een /: " + fileName);
      }

      if(!file.exists() || !file.canRead()) {
        return new ErrorMessageResolution(HttpServletResponse.SC_NOT_FOUND, "Foto '" + fileName + "' niet gevonden of niet toegankelijk!");
      }

      return ZippedFileResponse(file);
    } catch (Exception e) {
      return new ErrorMessageResolution(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Onverwachte fout opgetreden in download().");
    }
  }

  // #endregion

  // #region PRIVATES

  private Resolution ZippedJSONResponse(String result) {
    return new Resolution() {
      @Override
      public void execute(HttpServletRequest request, HttpServletResponse response) throws Exception {
        OutputStream out;
        String encoding = "UTF-8";
        String acceptEncoding = request.getHeader("Accept-Encoding");
        response.setCharacterEncoding(encoding);
        response.setContentType("application/json");
        if(acceptEncoding != null && acceptEncoding.contains("gzip")) {
            response.setHeader("Content-Encoding", "gzip");
            out = new GZIPOutputStream(response.getOutputStream(), true);
        } else {
            out = response.getOutputStream();
        }
        IOUtils.copy(new StringReader(result), out, encoding);
        out.flush();
        out.close();
      }
    };
  }

  private Resolution ZippedFileResponse(File file) {
    return new Resolution() {
      @Override
      public void execute(HttpServletRequest request, HttpServletResponse response) throws Exception {
        OutputStream out;
        String encoding = "UTF-8";
        String acceptEncoding = request.getHeader("Accept-Encoding");
        String contentType = Files.probeContentType(file.toPath());
        response.setCharacterEncoding(encoding);
        response.setContentType(contentType);
        if(acceptEncoding != null && acceptEncoding.contains("gzip")) {
            response.setHeader("Content-Encoding", "gzip");
            out = new GZIPOutputStream(response.getOutputStream(), true);
        } else {
            out = response.getOutputStream();
        }
        FileInputStream fis = new FileInputStream(file);
        byte[] buffer = new byte[1024];
        int len;
        while ((len = fis.read(buffer)) > 0) {
            out.write(buffer, 0, len);
        }
        out.flush();
        out.close();
      }
    };
  }

  private List<Map<String, Object>> getFromDb() throws Exception {
    QueryRunner qr = DB.qr();

    List<Map<String, Object>> rows = qr.query("SELECT \"filename\", \"omschrijving\", \"location\" from wfs."+TABLE+" where incident_nummer =?", new MapListHandler(),incidentNummer);

    return rows;
  }

  // #endregion
}
