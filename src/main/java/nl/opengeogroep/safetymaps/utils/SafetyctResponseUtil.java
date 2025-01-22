package nl.opengeogroep.safetymaps.utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.io.StringReader;
import java.nio.file.Files;
import java.util.zip.GZIPOutputStream;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.io.IOUtils;

import net.sourceforge.stripes.action.Resolution;

public class SafetyctResponseUtil {
  
  public static Resolution ZippedJSONResponse(String result) {
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

  public static Resolution ZippedFileResponse(File file) {
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

}
