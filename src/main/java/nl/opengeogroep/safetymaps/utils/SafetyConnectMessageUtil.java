package nl.opengeogroep.safetymaps.utils;

import java.util.List;
import java.util.Map;
import java.math.BigDecimal;

import org.json.JSONArray;
import org.json.JSONObject;

public class SafetyConnectMessageUtil {
  public static JSONObject IncidentDbRowHasActiveUnit(List<Map<String, Object>> dbIncidents, String unitSourceId) {
    JSONObject activeUnitFound = null;

    for (Map<String, Object> incidentDbRow : dbIncidents) {
      String incidentId = (String)incidentDbRow.get("sourceid");
      String unitsString = (String)incidentDbRow.get("units");
      JSONArray units = incidentDbRow.get("units") != null ? new JSONArray(unitsString) : new JSONArray();

      for(int i=0; i<units.length(); i++) {
        JSONObject unit = (JSONObject)units.get(i);

        if (unit.has("roepnaam") && unit.get("roepnaam") == unitSourceId && !unit.has("eindeActieDtg")) {
          activeUnitFound = unit;
          activeUnitFound.put("incidentId", incidentId);
          activeUnitFound.put("incidentRol", unit.has("voertuigSoort") ? unit.get("voertuigSoort") : "");
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

  public static JSONObject MapUnitDbRowAllColumnsAsJSONObject(Map<String, Object> unitDbRow) {
    JSONObject unit = new JSONObject();

    unit.put("roepnaam", (String)unitDbRow.get("sourceid"));
    unit.put("gmsStatusCode", (Integer)unitDbRow.get("gmsstatuscode"));
    unit.put("primaireVoertuigSoort", (String)unitDbRow.get("primairevoertuigsoort"));
    unit.put("lon", (BigDecimal)unitDbRow.get("lon"));
    unit.put("lat", (BigDecimal)unitDbRow.get("lat"));
    unit.put("speed", (Integer)unitDbRow.get("speed"));
    unit.put("heading", (Integer)unitDbRow.get("heading"));
    unit.put("eta", (Integer)unitDbRow.get("eta"));

    return unit;
  }
}
