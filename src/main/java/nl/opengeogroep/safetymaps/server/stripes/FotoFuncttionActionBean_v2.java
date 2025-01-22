package nl.opengeogroep.safetymaps.server.stripes;

import static nl.opengeogroep.safetymaps.server.db.JSONUtils.rowToJson;

import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.zip.GZIPOutputStream;

import javax.activation.DataHandler;
import javax.activation.FileDataSource;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMessage.RecipientType;
import javax.naming.Context;
import javax.naming.InitialContext;
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

import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.model.ZipParameters;
import net.lingala.zip4j.model.enums.AesKeyStrength;
import net.lingala.zip4j.model.enums.CompressionLevel;
import net.lingala.zip4j.model.enums.CompressionMethod;
import net.lingala.zip4j.model.enums.EncryptionMethod;
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
import nl.opengeogroep.safetymaps.utils.SafetyctResponseUtil;

@StrictBinding
@MultipartConfig
@UrlBinding("/viewer/api/foto")
public class FotoFuncttionActionBean_v2 implements ActionBean {
  
  private final ExecutorService EXEC = Executors.newCachedThreadPool();
  private static final Log LOG = LogFactory.getLog(FotoFunctionActionBean.class);
  private static final Map<String,CachedResponseString> LOADCACHE = new HashMap<>();
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
  public Resolution foto() {
    JSONObject result = new JSONObject();
    result.put("result", false);

    try {
      String path = Cfg.getSetting("fotofunctie");
      if (path == null) {
        return new ErrorMessageResolution(HttpServletResponse.SC_BAD_REQUEST, "Serverpad voor het opslaan van fotos is niet geconfigureerd");
      }

      if (extraInfo == null) extraInfo = "";
      if (incidentNummer == null) incidentNummer = "N.V.T.";

      fileName = fileName.replace('/','_');
      
      insertIntoDb();

      CachedResponseString cache = LOADCACHE.get(this.incidentNummer);
      UpdateCache(cache);

      result.put("result", true);

      EXEC.submit(() -> {
        try {
          File savedFile = SaveFileToDisk(path);
          ZipFileAndEmail(savedFile);
        } catch (Exception e) {
          LOG.error("Unexpected error occurred while handling photo upload in seprate thread: ", e);
        }
      });

      return SafetyctResponseUtil.ZippedJSONResponse(result.toString());
    } catch (Exception e) {
      return new ErrorMessageResolution(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Onverwachte fout opgetreden in @DefaultHandler.");
    }
  }

  public Resolution fotoForIncident() {
    synchronized(LOADCACHE) {
      CleanupCacheLoad();

      try {
        CachedResponseString cache = LOADCACHE.get(this.incidentNummer);

        if (!LOADCACHE.containsKey(this.incidentNummer) || cache == null || cache.isOutDated()) {
          cache = UpdateCache(cache);
        }
  
        return SafetyctResponseUtil.ZippedJSONResponse(cache.response);
      } catch (Exception e) {
        return new ErrorMessageResolution(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Onverwachte fout opgetreden in fotoForIncident().");
      }
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

      if (path == null) {
        return new ErrorMessageResolution(HttpServletResponse.SC_BAD_REQUEST, "Serverpad voor het opslaan van fotos is niet geconfigureerd");
      }

      if(!file.getParentFile().equals(pathDir)) {
        return new ErrorMessageResolution(HttpServletResponse.SC_BAD_REQUEST, "Bestandsnaam bevat een /: " + fileName);
      }

      if(!file.exists() || !file.canRead()) {
        return new ErrorMessageResolution(HttpServletResponse.SC_NOT_FOUND, "Foto '" + fileName + "' niet gevonden of niet toegankelijk!");
      }

      return SafetyctResponseUtil.ZippedFileResponse(file);
    } catch (Exception e) {
      return new ErrorMessageResolution(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Onverwachte fout opgetreden in download().");
    }
  }

  // #endregion

  // #region PRIVATES

  private CachedResponseString UpdateCache(CachedResponseString cache) throws Exception {
    JSONArray result = new JSONArray();
    
    List<Map<String, Object>> rows = getFromDb();
    for (Map<String, Object> row : rows) {
      result.put(rowToJson(row, false, false));
    }

    cache = new CachedResponseString(result.toString());
    LOADCACHE.put(this.incidentNummer, cache);

    return cache;
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

  private void CleanupCacheLoad() {
    LOADCACHE.values().removeIf(value -> value.isReadyToCleanup());
  }

  private List<Map<String, Object>> getFromDb() throws Exception {
    QueryRunner qr = DB.qr();

    List<Map<String, Object>> rows = qr.query("SELECT \"filename\", \"omschrijving\", \"location\" from wfs."+TABLE+" where incident_nummer =?", new MapListHandler(),incidentNummer);

    return rows;
  }

  private void insertIntoDb() throws Exception {
      Calendar calendar = Calendar.getInstance();
      java.sql.Date date = new java.sql.Date(calendar.getTime().getTime());
      Object[] qparams = new Object[] {
          fileName,
          type,
          voertuigNummer,
          incidentNummer,
          date,
          extraInfo,
          location
      };
      QueryRunner qr = DB.qr();
      qr.insert("insert into wfs." + TABLE + " (filename, datatype, voertuig_nummer, incident_nummer, date, omschrijving, location) values(?,?,?,?,?,?,?)", new MapListHandler(), qparams);
  }

  private File SaveFileToDisk(String path) throws Exception {
    String filePath = path + File.separator + fileName;
    final File file = new File(filePath);
    picture.save(file);

    return file;
  }

  private void ZipFileAndEmail(File savedFile) throws Exception {
    String zipPhoto = Cfg.getSetting("fotofunctie_zip");
    String zipPass = Cfg.getSetting("fotofunctie_zipPass");
    String zipPath = savedFile.getPath() + ".zip";
    Boolean zipSuccess = false;
    
    if (zipPhoto != null && "true".equals(zipPhoto) && zipPass != null) {
      try {
        ZipParameters zipParameters = new ZipParameters();
        zipParameters.setCompressionMethod(CompressionMethod.DEFLATE);
        zipParameters.setCompressionLevel(CompressionLevel.NORMAL);
        zipParameters.setEncryptFiles(true);
        zipParameters.setEncryptionMethod(EncryptionMethod.AES);
        zipParameters.setAesKeyStrength(AesKeyStrength.KEY_STRENGTH_256);

        ZipFile zip = new ZipFile(zipPath, zipPass.toCharArray());
        zip.addFile(savedFile, zipParameters);
        zip.close();

        zipSuccess = true;
      } catch(Exception e) { }
    }

    if (zipSuccess) {
      Path target = Paths.get(zipPath);
      Session session = null;
      String to = null;
      String from = null;

      Context ctx = new InitialContext();
      session = (Session)ctx.lookup("java:comp/env/mail/session");
      to = Cfg.getSetting("fotofunctie_mail_to");
      from = Cfg.getSetting("fotofunctie_mail_from");

      if(to != null || from != null) {
        String subject = "Foto/screenshot voor incident " + incidentNummer + " toegevoegd.";
        String mail = subject + " Bestandsnaam: " + fileName;

        javax.mail.Message msg = new MimeMessage(session);
        msg.setFrom(new InternetAddress(from));
        msg.addRecipient(RecipientType.TO, new InternetAddress(to));
        String sender = context.getRequest().getParameter("email");
        if(sender != null) {
            msg.addRecipient(RecipientType.CC, new InternetAddress(sender));
        }
        msg.setSubject(subject);
        msg.setSentDate(new Date());
        msg.setContent(mail, "text/plain");
        msg.setDataHandler(new DataHandler(new FileDataSource(target.toFile())));
        msg.setFileName(fileName + ".zip");

        Transport.send(msg);

        File zipFile = new File(zipPath);
        zipFile.delete();
      }
    }
  }
  
  // #endregion
}
