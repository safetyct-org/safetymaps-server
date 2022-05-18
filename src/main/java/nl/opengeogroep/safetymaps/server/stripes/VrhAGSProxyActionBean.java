package nl.opengeogroep.safetymaps.server.stripes;

import java.io.IOException;
import java.io.OutputStream;
import java.io.StringReader;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import net.sourceforge.stripes.action.ActionBean;
import net.sourceforge.stripes.action.ActionBeanContext;
import net.sourceforge.stripes.action.Resolution;
import net.sourceforge.stripes.action.StreamingResolution;
import net.sourceforge.stripes.action.UrlBinding;
import nl.b3p.web.stripes.ErrorMessageResolution;
import nl.opengeogroep.safetymaps.server.db.Cfg;
import static nl.opengeogroep.safetymaps.server.db.DB.ROLE_ADMIN;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.mutable.MutableObject;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.conn.ssl.SSLContextBuilder;
import org.apache.http.conn.ssl.TrustStrategy;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;

/**
 *
 * @author matthijsln
 */
@UrlBinding("/viewer/api/vrhAGS{path}")
public class VrhAGSProxyActionBean implements ActionBean {
    private static final Log log = LogFactory.getLog(VrhAGSProxyActionBean.class);

    private ActionBeanContext context;

    static final String ROLE = "smvng_incident_vrh_ags_replica";
    static final String ROLE_PROD = "smvng_incident_vrh_ags_replica__prod";
    static final String ROLE_TEST = "smvng_incident_vrh_ags_replica__test";
    static final String ROLE_ALLNOTEPAD = "smvng_incident_vrh_ags_replica__fullnotepad";

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
        if(!context.getRequest().isUserInRole(ROLE) && !context.getRequest().isUserInRole(ROLE_ADMIN)) {
            return new ErrorMessageResolution(HttpServletResponse.SC_FORBIDDEN, "Gebruiker heeft geen toegang tot VRH AGS!");
        }

        RequestBuilder builder;

        String authorization = Cfg.getSetting("vrh_ags_token_authorization");
        String tokenurl = Cfg.getSetting("vrh_ags_token_url");
        String uniturl = Cfg.getSetting("vrh_ags_eenheden_url");
        String defaultApi = Cfg.getSetting("vrh_ags_incidents_default"); // new

        Boolean useAdmin = context.getRequest().isUserInRole(ROLE_ADMIN);
        Boolean useTestUrl = context.getRequest().isUserInRole(ROLE_TEST);
        Boolean useProdUrl = context.getRequest().isUserInRole(ROLE_PROD);
        Boolean getAllNotepad = context.getRequest().isUserInRole(ROLE_ALLNOTEPAD);

        String testincidentsurl = Cfg.getSetting("vrh_ags_incidents_url_test"); // new
        String prodincidentsurl = Cfg.getSetting("vrh_ags_incidents_url_prod"); // new
        String defaultUrl = "prod".equals(defaultApi) ? prodincidentsurl : "test".equals(defaultApi) ? testincidentsurl : null;

        String incidentsurl = useAdmin ? defaultUrl : useProdUrl ? prodincidentsurl : useTestUrl ? testincidentsurl : defaultUrl;

        if("Token".equals(path)) {
            if(authorization == null || tokenurl == null) {
                return new ErrorMessageResolution(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Geen toegangsgegevens voor VRH AGS geconfigureerd door beheerder");
            }

            builder = RequestBuilder.post()
                    .setUri(tokenurl)
                    .addParameter("f", context.getRequest().getParameter("f"))
                    .addParameter("username", authorization.split(":")[0])
                    .addParameter("password", authorization.split(":")[1]);
        } else if(path != null && path.startsWith("Eenheden")) {
            path = path.substring("Eenheden".length());
            builder = buildProxyRequestBuilder(uniturl);
        } else {
            builder = buildProxyRequestBuilder(incidentsurl, true, getAllNotepad);
        }

        final HttpUriRequest req = builder.build();

        try(CloseableHttpClient client = createHttpClient()) {
            final MutableObject<String> contentType = new MutableObject<>("text/plain");
            final String content = client.execute(req, new ResponseHandler<String>() {
                @Override
                public String handleResponse(HttpResponse hr) {
                    log.debug("proxy for user " + context.getRequest().getRemoteUser() + " URL " + req.getURI() + ", response: " + hr.getStatusLine().getStatusCode() + " " + hr.getStatusLine().getReasonPhrase());
                    if(log.isTraceEnabled()) {
                        log.trace("response headers: " + Arrays.asList(hr.getAllHeaders()));
                    }
                    if(hr.getEntity() != null && hr.getEntity().getContentType() != null) {
                        contentType.setValue(hr.getEntity().getContentType().getValue());
                    }
                    try {
                        // XXX streaming werkt niet?
                        return IOUtils.toString(hr.getEntity().getContent(), "UTF-8");
                    } catch(IOException e) {
                        log.error("Exception reading HTTP content", e);
                        return "Exception " + e.getClass() + ": " + e.getMessage();
                    }
                }
            });

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

            //return new StreamingResolution(contentType.getValue(), new StringReader(content));
        } catch(IOException e) {
            log.error("Failed to write output:", e);
            return null;
        }
    }

    private RequestBuilder buildProxyRequestBuilder(String url) throws Exception {
        return buildProxyRequestBuilder(url, false, false);
    }

    private RequestBuilder buildProxyRequestBuilder(String url, Boolean checkDisc, Boolean allDisc) throws Exception {
        String qs = context.getRequest().getQueryString();
        RequestBuilder builder = RequestBuilder.create(context.getRequest().getMethod())
                .setUri(url + (path == null ? "" : path) + (qs == null ? "" : "?" + qs));

        if("POST".equals(getContext().getRequest().getMethod())) {
            String contentType = getContext().getRequest().getContentType();
            if(contentType != null && contentType.contains("application/x-www-form-urlencoded")) {

                List <NameValuePair> nvps = new ArrayList<>();
                for(Map.Entry<String,String[]> param: context.getRequest().getParameterMap().entrySet()) {
                    String value = context.getRequest().getParameter(param.getKey());

                    if (Boolean.TRUE.equals(checkDisc)) {
                        if (Boolean.TRUE.equals(allDisc) && "where".equals(param.getKey())) {
                            String newValue = value.replace("AND T_IND_DISC_KLADBLOK_REGEL LIKE '_B_'", "");
                            nvps.add(new BasicNameValuePair(param.getKey(), newValue));
                        } else {
                            nvps.add(new BasicNameValuePair(param.getKey(), value));
                        }                     
                    } else {
                        nvps.add(new BasicNameValuePair(param.getKey(), value));
                    }
                }

                builder.setEntity(new UrlEncodedFormEntity(nvps, "UTF-8"));
            } else {
                /*
                if(contentType.contains(";")) {
                    contentType = contentType.split(";")[0];
                }
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                IOUtils.copy(context.getRequest().getInputStream(), bos);
                byte[] body = bos.toByteArray();
                log.debug("Setting body content type " + contentType + " to: " + new String(body));
                ByteArrayInputStream bis = new ByteArrayInputStream(body);
                builder.setEntity(new InputStreamEntity(bis, body.length, ContentType.create(contentType)));
                //builder.setEntity(new InputStreamEntity(getContext().getRequest().getInputStream(), ContentType.create(contentType)));
                */

                throw new Exception("Post alleen x-www-form-urlencoded naar proxy!");
            }
        }
        return builder;
    }

    private CloseableHttpClient createHttpClient() throws Exception {
        return HttpClients.custom()
                .setSslcontext(new SSLContextBuilder().loadTrustMaterial(null, new TrustStrategy() {
                        @Override
                        public boolean isTrusted(X509Certificate[] xcs, String string) throws CertificateException {
                            return true;
                        }
                }).build())
                .build();
    }
}
