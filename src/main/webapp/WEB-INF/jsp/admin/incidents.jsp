<%--
Copyright (C) 2022 Safety C&T
This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.
This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.
You should have received a copy of the GNU General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.
--%>

<%@include file="/WEB-INF/jsp/taglibs.jsp"%>
<%@page contentType="text/html" pageEncoding="UTF-8"%>

<stripes:layout-render name="/WEB-INF/jsp/templates/admin.jsp" pageTitle="Incident authorisatie beheer" menuitem="incidents">
  <stripes:layout-component name="content">

    <h1>Incident filters</h1>
    <table class="table table-bordered table-striped table-fixed-header table-condensed table-hover" id="usergroups-table">
      <thead>
        <tr>
          <th>Groep</th>
          <th></th>
          <th class="table-actions">&nbsp;</th>
        </tr>
      </thead>
      <tbody>
        <c:forEach var="group" items="${actionBean.groups}">
          <stripes:url var="editLink" beanclass="nl.opengeogroep.safetymaps.server.admin.stripes.IncidentsActionBean" event="edit">
            <stripes:param name="group" value="${group.role}"/>
            <stripes:param name="irstring" value="${group.incident_roles}"/>
          </stripes:url>
          <tr style="cursor: pointer" class="${actionBean.group == group.role ? 'info' : ''}" onclick="${'window.location.href=\''.concat(editLink).concat('\'')}">
            <td><c:out value="${group.role}"/></td>
            <td><c:out value="${group.description}"/></td>
            <td class="table-actions">
              <stripes:link beanclass="nl.opengeogroep.safetymaps.server.admin.stripes.IncidentsActionBean" event="edit" title="Bewerken">
                <stripes:param name="group" value="${group.role}"/>
                <span class="glyphicon glyphicon-pencil"></span>
              </stripes:link>
            </td>
          </tr>
        </c:forEach>
      </tbody>
    </table>

    <stripes:form beanclass="nl.opengeogroep.safetymaps.server.admin.stripes.IncidentsActionBean" class="form-horizontal">
      <c:set var="event" value="${actionBean.context.eventName}"/>
      <c:if test="${event == 'edit' || event == 'save'}">
        <stripes:submit name="save" class="btn btn-primary">Opslaan</stripes:submit>
        <stripes:submit name="cancel" class="btn btn-default">Annuleren</stripes:submit>
        <c:if test="${!empty actionBean.group}">
          <stripes:hidden name="group" value="${actionBean.group}"/>
          <stripes:hidden name="id" value="${actionBean.id}"/>
          <br /><br/>
        </c:if>

        <div>
          <div class="col-sm-4">
            <label class="control-label">Authorisatie(s)</label>
            <p class="help-block text-warning">Alle geselecteerde authorisaties voor de incident-module.</p>
            <c:forEach var="ir" items="${actionBean.incidentroles}">
              <p class="help-block text-warning">- <c:out value="${ir}"/></p>
            </c:forEach>
          </div>
          <div class="col-sm-4">
            <div class="form-group">              
              <label class="control-label">Filter A (voor locatie):</label>
              <p class="help-block text-warning">Selecteer een of meerdere locaties, of selecteer niets voor alles.</p>
              <c:forEach var="loc" items="${actionBean.allLocs}" varStatus="status">
                  <div class="custom-control custom-checkbox">
                      <stripes:checkbox name="locs" class="custom-control-input" value="${loc.id}" id="authLoc${status.index}"/>
                      <label class="custom-control-label" for="authLoc${status.index}"><c:out value="${loc.description}"/></label>
                  </div>
              </c:forEach>
            </div>
          </div>
          <div class="col-sm-4">
            <div class="form-group">
              <label class="control-label">Filter B (voor meldingsclassificatie OF functionaris OF kvt-code OF karakteristiek):</label>
              <stripes:checkbox name="restrictions" class="custom-control-input restriction mcs" value="mcs" id="mcs" onclick="javascript:handleRestrictions(this.checked, 'mcs');"/>&nbsp;<label class="control-label">Met meldingsclassificatie(s):</label>
              <p class="help-block text-warning">Geef een meldingsclassificatie op en gebruik een komma voor meerdere mogelijkheden.
                <br/>- Bijvoorbeeld alle dienstverlening: Dienstverlening
                <br/>- of alle dienstverlening en brand: Dienstverlening,Brand
              </p>
              <stripes:text class="form-control" name="mcs" />
            </div>
            <div class="form-group">
              <stripes:checkbox name="restrictions" class="custom-control-input restriction funcs" value="funcs" id="funcs" onclick="javascript:handleRestrictions(this.checked, 'funcs');"/>&nbsp;<label class="control-label">Met functionaris(sen):</label>
              <p class="help-block text-warning">Geef een functionaris op en gebruik een komma voor meerdere mogelijkheden.</p>
              <stripes:text class="form-control" name="funcs" />
            </div>
            <div class="form-group">
              <stripes:checkbox name="restrictions" class="custom-control-input restriction kvts" value="kvts" id="kvts" onclick="javascript:handleRestrictions(this.checked, 'kvts');"/>&nbsp;<label class="control-label">Gekoppeld aan KVT-code(s):</label>
              <p class="help-block text-warning">Geef een kvt-code op en gebruik een komma voor meerdere mogelijkheden.</p>
              <stripes:text class="form-control" name="kvts" />
            </div>
            <div class="form-group">
              <stripes:checkbox name="restrictions" class="custom-control-input restriction chars" value="chars" id="chars" onclick="javascript:handleRestrictions(this.checked, 'chars');"/>&nbsp;<label class="control-label">Met karakteristiek(en):</label>
              <p class="help-block text-warning">Geef een karakteristiek en optioneel een waarde op. Zit dit tussen [] met een : als scheidingsteken. Gebruik een komma voor meerdere mogelijkheden en laat leeg voor alle. 
                <br/>- Bijvoorbeeld alle grip 1 incidenten: [GRIP:1]
                <br/>- of alle weer alarm incidenten: [Soort weeralarm] 
                <br/>- of beide: [GRIP:1], [Soort weeralarm]</p>
              <stripes:text class="form-control" name="chars" />
            </div>
          </div>
        </div>
      </c:if>
    </stripes:form>

    <script language="javascript" type="text/javascript">
      window.setTimeout(function () {
        Array.from(document.getElementsByClassName("restriction")).forEach(
          function (element, index, array) {
            if (element.checked) {
              handleRestrictions(true, element.value);
            }
          }
        )
      }, 300);
      

      function handleRestrictions(isChecked, restriction) {
        Array.from(document.getElementsByClassName("restriction")).forEach(
          function (element, index, array) {
            if (!element.classList.contains(restriction)) {
              element.checked = false;
              element.disabled = isChecked;
              document.getElementsByName(element.id)[0].value = '';
              document.getElementsByName(element.id)[0].disabled = isChecked;
            }
          }
        );
      }
    </script>

  </stripes:layout-component>
</stripes:layout-render>