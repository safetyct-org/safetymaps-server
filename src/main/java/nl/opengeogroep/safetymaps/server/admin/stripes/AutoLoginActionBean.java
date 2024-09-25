package nl.opengeogroep.safetymaps.server.admin.stripes;

import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;

import javax.servlet.http.HttpServletResponse;

import net.sourceforge.stripes.action.ActionBean;
import net.sourceforge.stripes.action.ActionBeanContext;
import net.sourceforge.stripes.action.DefaultHandler;
import net.sourceforge.stripes.action.ErrorResolution;
import net.sourceforge.stripes.action.Resolution;
import net.sourceforge.stripes.action.StreamingResolution;
import net.sourceforge.stripes.action.UrlBinding;
import nl.b3p.web.stripes.ErrorMessageResolution;

@UrlBinding("/autologin")
public class AutoLoginActionBean implements ActionBean  {
  private ActionBeanContext context;

    @Override
    public ActionBeanContext getContext() {
        return context;
    }

    @Override
    public void setContext(ActionBeanContext context) {
        this.context = context;
    }

    @DefaultHandler
    public Resolution defaultHandler() throws Exception {
      String forUserWithGuid = context.getRequest().getParameter("as");

      if (isValidURL(context.getRequest().getRequestURI()) && forUserWithGuid != null) {
        return new ErrorResolution(HttpServletResponse.SC_OK);
      } else {
        return new ErrorResolution(HttpServletResponse.SC_FORBIDDEN); 
      } 
    }

    boolean isValidURL(String url) throws MalformedURLException, URISyntaxException {
      try {
          new URL(url).toURI();
          return true;
      } catch (MalformedURLException e) {
          return false;
      } catch (URISyntaxException e) {
          return false;
      }
  }
}
