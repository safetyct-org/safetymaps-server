/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package nl.opengeogroep.safetymaps.server.stripes;

import java.io.File;
import javax.servlet.annotation.MultipartConfig;
import javax.servlet.http.HttpServletResponse;

import net.sourceforge.stripes.action.ActionBean;
import net.sourceforge.stripes.action.ActionBeanContext;
import net.sourceforge.stripes.action.DefaultHandler;
import net.sourceforge.stripes.action.Resolution;
import net.sourceforge.stripes.action.StrictBinding;
import net.sourceforge.stripes.action.UrlBinding;
import net.sourceforge.stripes.validation.Validate;
import nl.b3p.web.stripes.ErrorMessageResolution;
import nl.opengeogroep.safetymaps.server.db.Cfg;
import nl.opengeogroep.safetymaps.utils.SafetyctResponseUtil;

/**
 *
 * @author martijn
 */
@StrictBinding
@MultipartConfig
@UrlBinding("/viewer/api/media/{filename}")
public class MediaActionBean implements ActionBean {

    private ActionBeanContext context;

    @Validate
    private String filename;

    @Override
    public void setContext(ActionBeanContext context) {
        this.context = context;
    }

    @Override
    public ActionBeanContext getContext() {
        return context;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    @DefaultHandler
    public Resolution download()  {
      try {
        if(filename.contains("..")) {
          return new ErrorMessageResolution(HttpServletResponse.SC_NOT_FOUND, "Media '" + filename + "' niet gevonden in de database!");
        }

        String path = Cfg.getSetting("media");
        File pathDir = new File(path);
        File file = new File(path + File.separator + filename);

        if (path == null) {
          return new ErrorMessageResolution(HttpServletResponse.SC_BAD_REQUEST, "Serverpad voor het ophalen van media is niet geconfigureerd");
        }

        if(!file.getParentFile().equals(pathDir)) {
          return new ErrorMessageResolution(HttpServletResponse.SC_BAD_REQUEST, "Bestandsnaam bevat een /: " + filename);
        }

        if(!file.exists() || !file.canRead()) {
          return new ErrorMessageResolution(HttpServletResponse.SC_NOT_FOUND, "Media '" + filename + "' niet gevonden of niet toegankelijk!");
        }

        return SafetyctResponseUtil.ZippedFileResponse(file);
      } catch (Exception e) {
        return new ErrorMessageResolution(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Onverwachte fout opgetreden in download().");
      }
    }

}
