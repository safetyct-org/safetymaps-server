package nl.opengeogroep.safetymaps.server.stripes;

import java.io.IOException;
import java.io.OutputStream;
import java.io.StringReader;
import java.net.http.HttpResponse;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.mutable.MutableObject;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.html.HtmlPage;

import net.sourceforge.stripes.action.ActionBean;
import net.sourceforge.stripes.action.ActionBeanContext;
import net.sourceforge.stripes.action.Resolution;
import net.sourceforge.stripes.action.UrlBinding;

@UrlBinding("/viewer/api/kavel10/{path}")
public class Kavel10ActionBean implements ActionBean {
    
  private ActionBeanContext context;
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

  public Resolution proxy() {
    String qs = context.getRequest().getQueryString();
    String uri = "https://ndp.vision10.nl/v/" + path + "?" + qs;
    String responseContent = "";

    final String encoding = "UTF-8";
    final MutableObject<String> contentType = new MutableObject<>("text/html");
    final String content;

    try(final WebClient client = new WebClient()) {
      client.getOptions().setCssEnabled(true);
      client.getOptions().setJavaScriptEnabled(true);

      final HtmlPage page = client.getPage(uri);
      responseContent = page.toString();
    } catch(Exception e) {
        e.printStackTrace();
    }

    content = responseContent;

    return new Resolution() {
      @Override
      public void execute(HttpServletRequest request, HttpServletResponse response) throws Exception {
        response.setCharacterEncoding(encoding);
        response.setContentType(contentType.getValue());

        OutputStream out;
        out = response.getOutputStream();

        try {
          IOUtils.copy(new StringReader(content), out, encoding);
          out.flush();
          out.close();
        } catch (IOException e) { }
      }
    };
  }
  
}
