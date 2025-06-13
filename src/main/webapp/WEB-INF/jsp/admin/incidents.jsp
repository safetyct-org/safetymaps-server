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

    <h1>Incident authorisatie</h1>
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
            <label class="control-label">Groep authorisatie(s) voor incident</label>
            <c:forEach var="ir" items="${actionBean.incidentroles}">
              <p class="help-block text-warning">- <c:out value="${ir}"/></p>
            </c:forEach>
          </div>
          <div class="col-sm-4">
            <div class="form-group">              
              <label class="control-label">Alleen voor locaties:</label>
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
              <label class="control-label">Alleen met functionaris(sen):</label>
              <p class="help-block text-warning">Gebruik een komma voor meerdere mogelijkheden en laat leeg voor alle. Gebruik alleen kleine letters!</p>
              <stripes:text class="form-control" name="funcs" />
            </div>
            <div class="form-group">
              <label class="control-label">Alleen gekoppeld aan KVT-code(s):</label>
              <p class="help-block text-warning">Gebruik een komma voor meerdere mogelijkheden en laat leeg voor alle. Gebruik alleen kleine letters!</p>
              <stripes:text class="form-control" name="kvts" />
            </div>
            <div class="form-group">
              <label class="control-label">Alleen met meldingsclassificatie(s):</label>
              <p class="help-block text-warning">Gebruik een komma voor meerdere mogelijkheden en laat leeg voor alle. Gebruik alleen kleine letters!</p>
              <stripes:text class="form-control" name="mcs" />
            </div>
            <div class="form-group">
              <label class="control-label">Alleen met karakteristiek(en):</label>
              <p class="help-block text-warning">Gebruik een komma voor meerdere mogelijkheden en laat leeg voor alle. Gebruik alleen kleine letters!</p>
              <stripes:text class="form-control" name="chars" />
            </div>
          </div>
        </div>
      </c:if>
    </stripes:form>

  </stripes:layout-component>
</stripes:layout-render>