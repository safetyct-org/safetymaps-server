package nl.opengeogroep.safetymaps.utils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONObject;

public class SafetyConnectMessageUtil {
  public static JSONObject IncidentDbRowHasActiveUnit(List<Map<String, Object>> dbIncidents, String unitSourceId) {
    JSONObject activeUnitFound = null;

    for (Map<String, Object> incidentDbRow : dbIncidents) {
      Integer incidentId = (Integer)incidentDbRow.get("number");
      String unitsString = (String)incidentDbRow.get("units");
      JSONArray units = incidentDbRow.get("units") != null ? new JSONArray(unitsString) : new JSONArray();

      for(int i=0; i<units.length(); i++) {
        JSONObject unit = (JSONObject)units.get(i);

        if (unit.has("roepnaam") && unitSourceId.equals(unit.getString("roepnaam"))) {
          activeUnitFound = unit;
          activeUnitFound.put("incidentId", incidentId);
          activeUnitFound.put("incidentRol", unit.has("inzetrol") ? unit.get("inzetrol") : "");
        }
      }
    }

    return activeUnitFound;
  }

  public static JSONObject MapIncidentDbRowAllColumnsAsJSONObject(Map<String, Object> incidentDbRow) {
    JSONObject incident = new JSONObject();

    String notesString = (String)incidentDbRow.get("notes");
    String unitsString = (String)incidentDbRow.get("units");
    String charactsString = (String)incidentDbRow.get("characts");
    String locationString = (String)incidentDbRow.get("location");
    String discString = (String)incidentDbRow.get("discipline");
    String talkingString = (String)incidentDbRow.get("talkinggroups");
    JSONArray notes = incidentDbRow.get("notes") != null ? new JSONArray(notesString) : new JSONArray();
    JSONArray units = incidentDbRow.get("units") != null ? new JSONArray(unitsString) : new JSONArray();
    JSONArray characts = incidentDbRow.get("characts") != null ? new JSONArray(charactsString): new JSONArray();
    JSONArray talkinggroups = incidentDbRow.get("talkinggroups") != null ? new JSONArray(talkingString): new JSONArray();

    incident.put("incidentNummer", (Integer)incidentDbRow.get("number"));
    incident.put("incidentId", (String)incidentDbRow.get("sourceid"));
    incident.put("status", (String)incidentDbRow.get("status"));
    incident.put("tenantId", (String)incidentDbRow.get("tenantid"));
    incident.put("kladblokregels", notes);
    incident.put("betrokkenEenheden", units);
    incident.put("karakteristieken", characts);
    incident.put("incidentLocatie", new JSONObject(locationString));
    incident.put("brwDisciplineGegevens", new JSONObject(discString));
    incident.put("gespreksGroepen", talkinggroups);

    return incident;
  }

  public static JSONObject MapUnitDbRowAllColumnsAsJSONObject(Map<String, Object> unitDbRow) {
    JSONObject unit = new JSONObject();

    String abbsString = (String)unitDbRow.get("abbs");
    JSONArray abbs = unitDbRow.get("abbs") != null ? new JSONArray(abbsString) : new JSONArray();

    Object lon = unitDbRow.get("lon");
    Object lat = unitDbRow.get("lat");

    unit.put("roepnaam", (String)unitDbRow.get("sourceid"));
    unit.put("gmsStatusCode", (Integer)unitDbRow.get("gmsstatuscode"));
    unit.put("gmsStatusText", (String)unitDbRow.get("gmsstatustext"));
    unit.put("primaireVoertuigSoort", (String)unitDbRow.get("primairevoertuigsoort"));
    unit.put("post", (String)unitDbRow.get("post"));

    if (lon instanceof BigDecimal) {
      unit.put("lon", ((BigDecimal)lon).doubleValue());
    } else {
      unit.put("lon", (Double)lon);
    }

    if (lat instanceof BigDecimal) {
      unit.put("lat", ((BigDecimal)lat).doubleValue());
    } else {
      unit.put("lat", (Double)lat);
    }

    unit.put("speed", (Integer)unitDbRow.get("speed"));
    unit.put("heading", (Integer)unitDbRow.get("heading"));
    unit.put("eta", (Integer)unitDbRow.get("eta"));
    unit.put("abbs", abbs);

    return unit;
  }
}
