package nl.opengeogroep.safetymaps.utils;

import java.util.Map;

import org.json.JSONArray;
import org.json.JSONObject;

public class SafetyConnectMessageUtil {
  public static JSONObject MapIncidentDbRowAllColumnsAsJSONObject(Map<String, Object> incidentDbRow) {
    JSONObject incident = new JSONObject();
    String notesString = (String)incidentDbRow.get("notes");
    String unitsString = (String)incidentDbRow.get("units");
    String charactsString = (String)incidentDbRow.get("characts");
    String locationString = (String)incidentDbRow.get("location");
    String discString = (String)incidentDbRow.get("discipline");
    JSONArray notes = incidentDbRow.get("notes") != null ? new JSONArray(notesString) : new JSONArray();
    JSONArray units = incidentDbRow.get("units") != null ? new JSONArray(unitsString) : new JSONArray();
    JSONArray characts = incidentDbRow.get("characts") != null ? new JSONArray(charactsString): new JSONArray();

    incident.put("incidentNummer", (Integer)incidentDbRow.get("number"));
    incident.put("incidentId", (String)incidentDbRow.get("sourceid"));
    incident.put("status", (String)incidentDbRow.get("status"));
    incident.put("kladblokregels", notes);
    incident.put("betrokkenEenheden", units);
    incident.put("karakteristieken", characts);
    incident.put("incidentLocatie", new JSONObject(locationString));
    incident.put("brwDisciplineGegevens", new JSONObject(discString));
    return incident;
  }
}
