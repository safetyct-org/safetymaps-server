package nl.opengeogroep.safetymaps.utils;

import java.util.Map;

import org.json.JSONArray;
import org.json.JSONObject;

public class SafetyConnectMessageUtil {
  public static JSONObject MapIncidentDbRowAllColumnsAsJSONObject(Map<String, Object> incidentDbRow) {
    JSONObject incident = new JSONObject();
    JSONArray notes = (JSONArray)incidentDbRow.get("notes") != null ? (JSONArray)incidentDbRow.get("notes") : new JSONArray();
    JSONArray units = (JSONArray)incidentDbRow.get("units") != null ? (JSONArray)incidentDbRow.get("units") : new JSONArray();
    JSONArray characts = (JSONArray)incidentDbRow.get("characts") != null ? (JSONArray)incidentDbRow.get("characts") : new JSONArray();

    incident.put("incidentNummer", (Integer)incidentDbRow.get("number"));
    incident.put("incidentId", (String)incidentDbRow.get("sourceid"));
    incident.put("status", (String)incidentDbRow.get("status"));
    incident.put("kladblokregels", notes);
    incident.put("betrokkenEenheden", units);
    incident.put("karakteristieken", characts);
    incident.put("incidentLocatie", (JSONObject)incidentDbRow.get("location"));
    incident.put("brwDisciplineGegevens", (JSONObject)incidentDbRow.get("discipline"));
    return incident;
  }
}
