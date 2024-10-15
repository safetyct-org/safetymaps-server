package nl.opengeogroep.safetymaps.server.admin.stripes;

import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import javax.naming.NamingException;
import net.sourceforge.stripes.action.ActionBean;
import net.sourceforge.stripes.action.ActionBeanContext;
import net.sourceforge.stripes.action.Before;
import net.sourceforge.stripes.action.DefaultHandler;
import net.sourceforge.stripes.action.DontValidate;
import net.sourceforge.stripes.action.ForwardResolution;
import net.sourceforge.stripes.action.RedirectResolution;
import net.sourceforge.stripes.action.Resolution;
import net.sourceforge.stripes.action.StrictBinding;
import net.sourceforge.stripes.action.UrlBinding;
import net.sourceforge.stripes.validation.Validate;
import net.sourceforge.stripes.validation.ValidationErrorHandler;
import net.sourceforge.stripes.validation.ValidationErrors;
import nl.opengeogroep.safetymaps.server.db.DB;

import org.apache.commons.dbutils.handlers.MapHandler;
import org.apache.commons.dbutils.handlers.MapListHandler;

/**
 *
 * @author Safety C&T
 */
@StrictBinding
@UrlBinding("/admin/action/databank")
public class DatabankActionBean implements ActionBean, ValidationErrorHandler {
  private ActionBeanContext context;
  private static final String JSP = "/WEB-INF/jsp/admin/databank.jsp";

  @Override
  public ActionBeanContext getContext() {
      return context;
  }

  @Override
  public void setContext(ActionBeanContext context) {
      this.context = context;
  }

  private List<Map<String,Object>> words = new ArrayList();
  public List<Map<String, Object>> getWords() {
      return words;
  }
  public void setWords(List<Map<String, Object>> words) {
      this.words = words;
  }

  /*
   * Attributes
   */

   @Validate
   private int id;
 
   public int getId() {
     return id;
   }
   public void setId(int id) {
     this.id = id;
   }

   @Validate
   private String word;
 
   public String getWord() {
     return word;
   }
   public void setWord(String word) {
     this.word = word;
   }

   @Validate
   private String become;
 
   public String getBecome() {
     return become;
   }
   public void setBecome(String become) {
     this.become = become;
   }

  /**
   * Load list information handler
   * 
   * @throws NamingException
   * @throws SQLException
   */
  @Before
  private void loadInfo() throws NamingException, SQLException {
    words = DB.qr().query("SELECT * FROM safetymaps.databank ORDER BY word ASC, become ASC", new MapListHandler());
  }

  /**
   * Edit handler
   * 
   * @return
   * @throws NamingException
   * @throws SQLException
   */
  public Resolution edit() throws NamingException, SQLException { 
    if (id > 0) {
      Map<String,Object> data = DB.qr().query("SELECT id, word, become FROM safetymaps.databank WHERE id=?", new MapHandler(), id);

      if(data.get("id") != null) {
        word = data.get("word").toString();
        become = data.get("become").toString();
      }
    }

    return list();
  }
  
  /**
   * Save new or edited handler
   * 
   * @return
   * @throws Exception
   */
  public Resolution save() throws Exception {
    if (id > 0) {
      DB.qr().update("UPDATE safetymaps.databank SET word=?, become=? WHERE id=?", word, become, id);
    } else {
      DB.qr().update("INSERT INTO safetymaps.databank(word, become) VALUES(?, ?)", word, become);
    }

    return cancel();
  }

  public Resolution delete() throws Exception {
    DB.qr().update("DELETE FROM safetymaps.databank WHERE id=?", id);

    return cancel();
  }


  /**
   * Default handlers
   */

   @DefaultHandler
   public Resolution list() throws NamingException, SQLException {
     return new ForwardResolution(JSP);
   }
 
   @DontValidate
   public Resolution cancel() throws Exception {
     loadInfo();
     return new RedirectResolution(this.getClass()).flash(this);
   }
 
   @Override
   public Resolution handleValidationErrors(ValidationErrors errors) throws Exception {
     loadInfo();
     return new ForwardResolution(JSP);
   }
}
