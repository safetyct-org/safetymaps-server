<%--
Copyright (C) 2021 Safety C&T
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

<stripes:layout-render name="/WEB-INF/jsp/templates/admin.jsp" pageTitle="Livestream beheer" menuitem="livestreams">
  <stripes:layout-component name="content">

    <div style="width: 49%; float: left; margin-right: 1%;">
      <h1>Voertuigen met livestream</h1>

      <table class="table table-bordered table-striped table-fixed-header table-condensed table-hover" id="vehiclestream-table">
        <thead>
            <tr>
                <th>Vehicle</th>
                <th>URL</th>
                <th class="table-actions">&nbsp;</th>
            </tr>
        </thead>
        <tbody>
          <c:forEach var="u" items="${actionBean.vehicleStreams}">
            <stripes:url var="editLink_vs" beanclass="nl.opengeogroep.safetymaps.server.admin.stripes.LivestreamsActionBean" event="edit_vs">
              <stripes:param name="vehicleStreamId" value="${u.row_id}"/>
            </stripes:url>
            <tr style="cursor: pointer" class="${actionBean.vehicleStreamId == u.row_id ? 'info' : ''}" onclick="${ 'window.location.href=\''.concat(editLink_vs).concat('\'') }">
              <td><c:out value="${u.vehicle}"/></td>
              <td><c:out value="${u.url}"/></td>
              <td class="table-actions">
                <stripes:link beanclass="nl.opengeogroep.safetymaps.server.admin.stripes.LivestreamsActionBean" event="edit_vs" title="Bewerken">
                    <stripes:param name="vehicleStreamId" value="${u.row_id}"/>
                    <span class="glyphicon glyphicon-pencil"></span>
                </stripes:link>
                <stripes:link class="remove-item" beanclass="nl.opengeogroep.safetymaps.server.admin.stripes.LivestreamsActionBean" event="delete_vs" title="Verwijderen">
                    <stripes:param name="vehicleStreamId" value="${u.row_id}"/>
                    <span class="glyphicon glyphicon-remove"></span>
                </stripes:link>
              </td>
            </tr>
          </c:forEach>
        </tbody>
      </table>

      <stripes:form beanclass="nl.opengeogroep.safetymaps.server.admin.stripes.LivestreamsActionBean" class="form-horizontal">
        <c:set var="event" value="${actionBean.context.eventName}"/>
        <c:if test="${event == 'list'}">
            <stripes:submit name="edit_vs" class="btn btn-primary">Nieuw voertuig</stripes:submit>
        </c:if>
        <c:if test="${event == 'edit_vs' || event == 'save_vs'}">
          <stripes:submit name="save_vs" class="btn btn-primary">Opslaan</stripes:submit>
          <c:if test="${!empty actionBean.vehicleStreamId}">
            <stripes:submit name="delete_vs" class="btn btn-danger remove-item">Verwijderen</stripes:submit>
          </c:if>
          <stripes:submit name="cancel" class="btn btn-default">Annuleren</stripes:submit>

          <c:if test="${!empty actionBean.vehicleStreamId}">
              <stripes:hidden name="vehicleStreamId" value="${actionBean.vehicleStreamId}"/>
              <br /><br/>
          </c:if>
          <div class="form-group">
            <label class="col-sm-2 control-label">Voertuig:</label>
            <div class="col-sm-10">
              <stripes:text class="form-control" name="vehicle" />
            </div>
          </div>
          <div class="form-group">
            <label class="col-sm-2 control-label">URL:</label>
            <div class="col-sm-10">
              <stripes:text class="form-control" name="urlvs" />
            </div>
          </div>
          <div class="form-group">
            <label class="col-sm-2 control-label">Gebruiker:</label>
            <div class="col-sm-10">
              <stripes:text class="form-control" name="username" />
            </div>
          </div>
          <div class="form-group">
            <label class="col-sm-2 control-label">Wachtwoord:</label>
            <div class="col-sm-10">
              <stripes:password class="form-control" name="password" />
            </div>
          </div>
        </c:if>
      </stripes:form>
    </div>

    <div style="width: 49%; float: left; margin-left: 1%;">
      <h1>Active livestreams</h1>

      <table class="table table-bordered table-striped table-fixed-header table-condensed table-hover" id="livestream-table">
        <thead>
            <tr>
                <th>Incident</th>
                <th>Name</th>
                <th class="table-actions">&nbsp;</th>
            </tr>
        </thead>
        <tbody>
          <c:forEach var="u" items="${actionBean.incidentStreams}">
            <stripes:url var="editLink_is" beanclass="nl.opengeogroep.safetymaps.server.admin.stripes.LivestreamsActionBean" event="edit_is">
              <stripes:param name="incidentStreamId" value="${u.row_id}"/>
            </stripes:url>
            <tr style="cursor: pointer" class="${actionBean.incidentStreamId == u.row_id ? 'info' : ''}" onclick="${ 'window.location.href=\''.concat(editLink_is).concat('\'') }">
              <td><c:out value="${u.incident}"/></td>
              <td><c:out value="${u.name}"/></td>
              <td class="table-actions">
                <stripes:link beanclass="nl.opengeogroep.safetymaps.server.admin.stripes.LivestreamsActionBean" event="edit_is" title="Bewerken">
                    <stripes:param name="incidentStreamId" value="${u.row_id}"/>
                    <span class="glyphicon glyphicon-pencil"></span>
                </stripes:link>
                <stripes:link class="remove-item" beanclass="nl.opengeogroep.safetymaps.server.admin.stripes.LivestreamsActionBean" event="delete_is" title="Verwijderen">
                    <stripes:param name="incidentStreamId" value="${u.row_id}"/>
                    <span class="glyphicon glyphicon-remove"></span>
                </stripes:link>
              </td>
            </tr>
          </c:forEach>
        </tbody>
      </table>

      <stripes:form beanclass="nl.opengeogroep.safetymaps.server.admin.stripes.LivestreamsActionBean" class="form-horizontal">
        <c:set var="event" value="${actionBean.context.eventName}"/>
        <c:if test="${event == 'list'}">
            <stripes:submit name="edit_is" class="btn btn-primary">Nieuwe livestream</stripes:submit>
        </c:if>
        <c:if test="${event == 'edit_is' || event == 'save_is'}">
          <stripes:submit name="save_is" class="btn btn-primary">Opslaan</stripes:submit>
          <c:if test="${!empty actionBean.incidentStreamId}">
            <stripes:submit name="delete_is" class="btn btn-danger remove-item">Verwijderen</stripes:submit>
          </c:if>
          <stripes:submit name="cancel" class="btn btn-default">Annuleren</stripes:submit>

          <c:if test="${!empty actionBean.incidentStreamId}">
              <stripes:hidden name="incidentStreamId" value="${actionBean.incidentStreamId}"/>
              <br /><br/>
          </c:if>
          <div class="form-group">
            <label class="col-sm-2 control-label">Incident:</label>
            <div class="col-sm-10">
              <stripes:text class="form-control" name="incident" />
            </div>
          </div>
          <div class="form-group">
            <label class="col-sm-2 control-label">Name:</label>
            <div class="col-sm-10">
              <stripes:text class="form-control" name="name" />
            </div>
          </div>
          <div class="form-group">
            <label class="col-sm-2 control-label">URL:</label>
            <div class="col-sm-10">
              <stripes:text class="form-control" name="urlis" />
            </div>
          </div>
        </c:if>
      </stripes:form>
    </div>

  </stripes:layout-component>
</stripes:layout-render>