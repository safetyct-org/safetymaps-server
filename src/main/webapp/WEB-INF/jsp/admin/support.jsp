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

<stripes:layout-render name="/WEB-INF/jsp/templates/admin.jsp" pageTitle="Berichten tickets" menuitem="support">
  <stripes:layout-component name="content">

    <h1>Support tickets</h1>
    <table class="table table-bordered table-striped table-fixed-header table-condensed table-hover" id="support-table">
      <thead>
        <tr>
          <th>Onderwerp</th>
          <th>Datum</th>
          <th>Melder</th>
          <th class="table-actions">&nbsp;</th>
        </tr>
      </thead>
      <tbody>
        <c:forEach var="ticket" items="${actionBean.tickets}">
          <c:if test="${ticket.handled == 0}">
            <stripes:url var="editLink" beanclass="nl.opengeogroep.safetymaps.server.admin.stripes.SupportActionBean" event="edit">
              <stripes:param name="id" value="${ticket.id}"/>
            </stripes:url>
            <tr style="cursor: pointer" class="${actionBean.id == ticket.id ? 'info' : ''} ${ticket.handled == 1 ? 'disabled' : ''}" onclick="${'window.location.href=\''.concat(editLink).concat('\'')}">
              <td><c:out value="${ticket.subject}"/></td>
              <td><c:out value="${ticket.dtgmelding}"/></td>
              <td><c:out value="${ticket.name}"/> (<c:out value="${ticket.username}"/>)</td>
              <td class="table-actions">
                <stripes:link beanclass="nl.opengeogroep.safetymaps.server.admin.stripes.SupportActionBean" event="edit" title="Bewerken">
                  <stripes:param name="id" value="${ticket.id}"/>
                  <span class="glyphicon glyphicon-pencil"></span>
                </stripes:link>
                <stripes:link class="handle-item" beanclass="nl.opengeogroep.safetymaps.server.admin.stripes.SupportActionBean" event="handle" title="Afhandelen">
                  <stripes:param name="id" value="${ticket.id}"/>
                  <span class="glyphicon glyphicon-check"></span>
                </stripes:link>
              </td>
            </tr>
          </c:if>
        </c:forEach>
      </tbody>
    </table>

    <stripes:form beanclass="nl.opengeogroep.safetymaps.server.admin.stripes.SupportActionBean" class="form-horizontal">
      <c:set var="event" value="${actionBean.context.eventName}"/>
      <c:if test="${(event == 'edit' || event == 'save')  && actionBean.handled == 0}">
        <stripes:submit name="save" class="btn btn-primary">Opslaan</stripes:submit>
        <c:if test="${!empty actionBean.id && actionBean.handled == 0}">
          <stripes:submit name="handle" class="btn btn-success handle-item">Afhandelen</stripes:submit>
        </c:if>
        <c:if test="${!empty actionBean.id}">
          <stripes:submit name="delete" class="btn btn-danger remove-item">Verwijderen</stripes:submit>
        </c:if>
        <stripes:submit name="cancel" class="btn btn-default">Annuleren</stripes:submit>
        <c:if test="${!empty actionBean.id}">
          <stripes:hidden name="id" value="${actionBean.id}"/>
          <br /><br/>
        </c:if>

        <div class="form-group">
          <label class="col-sm-2 control-label">Onderwerp:</label>
          <div class="col-sm-10">
            <c:out value="${actionBean.subject}"/>
          </div>
        </div>
        <div class="form-group">
          <label class="col-sm-2 control-label">Datum melding:</label>
          <div class="col-sm-10">
            <c:out value="${actionBean.dtgmelding}"/>
          </div>
        </div>
        <div class="form-group">
          <label class="col-sm-2 control-label">Gemeld door:</label>
          <div class="col-sm-10">
            <c:out value="${actionBean.name}"/> (<c:out value="${actionBean.username}"/>) <c:out value="${actionBean.phone}"/> <c:out value="${actionBean.email}"/>
          </div>
        </div>
        <div class="form-group">
          <label class="col-sm-2 control-label">Link:</label>
          <div class="col-sm-10">
            <stripes:link href="${actionBean.permalink}" target="_blank"><c:out value="${actionBean.permalink}"/></stripes:link>            
          </div>
        </div>
        <div class="form-group">
          <label class="col-sm-2 control-label">Melding:</label>
          <div class="col-sm-10">
            <c:out value="${actionBean.description}"/>
          </div>
        </div>
        <div class="form-group">
          <label class="col-sm-2 control-label">Afhandeling:</label>
          <div class="col-sm-10">
            <stripes:text class="form-control" name="solution" />
          </div>
        </div>
      </c:if>
    </stripes:form>

  </stripes:layout-component>
</stripes:layout-render>
