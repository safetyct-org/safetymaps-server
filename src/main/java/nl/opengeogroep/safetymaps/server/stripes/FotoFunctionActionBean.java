/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package nl.opengeogroep.safetymaps.server.stripes;

import net.sourceforge.stripes.action.*;
import net.sourceforge.stripes.validation.Validate;
import nl.b3p.web.stripes.ErrorMessageResolution;
import nl.opengeogroep.safetymaps.server.db.Cfg;
import nl.opengeogroep.safetymaps.server.db.DB;
import nl.opengeogroep.safetymaps.utils.ZipIOStream;
import org.apache.commons.dbutils.QueryRunner;
import org.apache.commons.dbutils.handlers.MapListHandler;
import org.apache.commons.dbutils.handlers.ScalarHandler;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.activation.DataHandler;
import javax.activation.FileDataSource;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMessage.RecipientType;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.servlet.ServletException;
import javax.servlet.annotation.MultipartConfig;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Map;

import java.util.ArrayList;
import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.exception.ZipException;
import net.lingala.zip4j.model.ZipParameters;
import net.lingala.zip4j.model.enums.*;

import static nl.opengeogroep.safetymaps.server.db.JSONUtils.rowToJson;

/**
 *
 * @author martijn
 */
@StrictBinding
@MultipartConfig
@UrlBinding("/viewer/api/foto")
public class FotoFunctionActionBean implements ActionBean {

    private static final Log log = LogFactory.getLog(FotoFunctionActionBean.class);
    private static final String TABLE = "\"FotoFunctie\"";
    private ActionBeanContext context;
    private String PATH = "";

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

    @Override
    public void setContext(ActionBeanContext context) {
        this.context = context;
    }

    @Override
    public ActionBeanContext getContext() {
        return context;
    }

    public FileBean getPicture() {
        return picture;
    }

    public void setPicture(FileBean picture) {
        this.picture = picture;
    }

    @DefaultHandler
    public Resolution foto() throws IOException, ServletException {
        JSONObject response = new JSONObject();
        try {
            PATH = Cfg.getSetting("fotofunctie");

            if(PATH == null) {
                throw new IllegalArgumentException("Fotofunctie serverpad niet geconfigureerd");
            }

            response.put("result", false);
            if (extraInfo == null) {
                extraInfo = "";
            }

            if (incidentNummer == null) {
                incidentNummer = "N.V.T.";
            }
            
            fileName = fileName.replace('/','_');
            String filePath = PATH + File.separator + fileName;
            final File file = new File(filePath);
            picture.save(file);
            insertIntoDb();

            Boolean zipErrored = false;
            String zipPhoto = Cfg.getSetting("fotofunctie_zip");
            String zipPass = Cfg.getSetting("fotofunctie_zipPass");
            if (zipPhoto != null && "true".equals(zipPhoto) && zipPass != null) {
                /*Path source = Paths.get(filePath);
                Map<Path, Throwable> report = new java.util.HashMap<>();
                if (!ZipIOStream.Zip(source, target, report)) {
                    for(Map.Entry<Path, Throwable> e : report.entrySet()) {
                        response.put("zipmessage", e.getValue().getMessage());
                    }
                    zipErrored = true;
                }*/
                try {
                    ZipParameters zipParameters = new ZipParameters();
                    zipParameters.setCompressionMethod(CompressionMethod.DEFLATE);
                    zipParameters.setCompressionLevel(CompressionLevel.NORMAL);
                    zipParameters.setEncryptFiles(true);
                    zipParameters.setEncryptionMethod(EncryptionMethod.AES);
                    zipParameters.setAesKeyStrength(AesKeyStrength.KEY_STRENGTH_256);

                    ZipFile zip = new ZipFile(filePath + ".zip", "".toCharArray());
                    zip.addFile(file, zipParameters);
                    zip.close();
                } catch (ZipException e) {
                    zipErrored = true;
                }
                Path target = Paths.get(filePath + ".zip");
                Session session = null;
                String to = null;
                String from = null;
                try {
                    Context ctx = new InitialContext();
                    session = (Session)ctx.lookup("java:comp/env/mail/session");

                    to = Cfg.getSetting("fotofunctie_mail_to");
                    from = Cfg.getSetting("fotofunctie_mail_from");

                    if(to == null || from == null) { throw new Exception(); }
                } catch(Exception e) {
                    response.put("mailmessage", "Server not configured correctly to send mail. Check context and settings.");
                }
                String subject = "Foto/screenshot voor incident " + incidentNummer + " toegevoegd.";
                String mail = subject + " Bestandsnaam: " + fileName;
                try {
                    if(!zipErrored && session != null && to != null && from != null) {
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

                        File zipFile = new File(filePath + ".zip");
                        zipFile.delete();
                    }
                } catch(Exception e) {
                    response.put("mailmessage", "Could not send mail.");
                }
            } else {
                response.put("zipmessage", "Zip niet (goed) geconfigureerd in settings tabel.");
            }
            
            response.put("message", "Foto is opgeslagen met bestandsnaam: " + fileName);
            response.put("result", true);
        } catch (Exception e) {
            response.put("message", "Error met fout " + e.getMessage());
        }
        return new StreamingResolution("application/json", response.toString());
    }

    public Resolution download() throws Exception {
        // First pathname security check: must exist in db
        boolean exists = DB.qr().query("select 1 from wfs." + TABLE + " where filename = ?", new ScalarHandler<>(), fileName) != null;

        // Second security check: No path breaker like /../ in filename
        if(!exists || fileName.contains("..")) {
            return new ErrorMessageResolution(HttpServletResponse.SC_NOT_FOUND, "Foto '" + fileName + "' niet gevonden");
        }

        String fotoPath = Cfg.getSetting("fotofunctie");
        File fotoPathDir = new File(fotoPath);
        File f = new File(fotoPath + File.separator + fileName);

        // Third security check: resulting path parent file must be the foto directory,
        // not another directory using path breakers like /../ etc.
        if(!f.getParentFile().equals(fotoPathDir)) {
            return new ErrorMessageResolution(HttpServletResponse.SC_BAD_REQUEST, "Filename contains path breaker: " + fileName);
        }

        if(!f.exists() || !f.canRead()) {
            return new ErrorMessageResolution(HttpServletResponse.SC_NOT_FOUND, "Foto '" + fileName + "' niet gevonden");
        }

        String mimeType = Files.probeContentType(f.toPath());
        return new StreamingResolution(mimeType, new FileInputStream(f));
    }

    public Resolution fotoForIncident() throws Exception {      
        JSONArray response = new JSONArray();

        List<Map<String, Object>> rows = getFromDb();

        for (Map<String, Object> row : rows) {
            response.put(rowToJson(row, false, false));
        }

        return new StreamingResolution("application/json", response.toString());
    }

    public void insertIntoDb() throws Exception {
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
        try {
            QueryRunner qr = DB.qr();
            qr.insert("insert into wfs." + TABLE + " (filename, datatype, voertuig_nummer, incident_nummer, date, omschrijving, location) values(?,?,?,?,?,?,?)", new MapListHandler(), qparams);
        } catch (Exception e) {
            throw e;
        }
    }

    public List<Map<String, Object>> getFromDb() throws Exception {
        QueryRunner qr = DB.qr();

        List<Map<String, Object>> rows = qr.query("SELECT \"filename\", \"omschrijving\", \"location\" from wfs."+TABLE+" where incident_nummer =?", new MapListHandler(),incidentNummer);

        return rows;
    }
}
