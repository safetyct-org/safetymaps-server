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

<stripes:layout-render name="/WEB-INF/jsp/templates/admin.jsp" pageTitle="Berichten beheer" menuitem="messages">
  <stripes:layout-component name="content">

    <h1>Berichten</h1>
    <table class="table table-bordered table-striped table-fixed-header table-condensed table-hover" id="messages-table">
      <thead>
        <tr>
          <th>Onderwerp</th>
          <th>Van</th>
          <th>Tot</th>
          <th class="table-actions">&nbsp;</th>
        </tr>
      </thead>
      <tbody>
        <c:forEach var="message" items="${actionBean.messages}">
          <stripes:url var="editLink" beanclass="nl.opengeogroep.safetymaps.server.admin.stripes.MessagesActionBean" event="edit">
            <stripes:param name="id" value="${message.id}"/>
          </stripes:url>
          <tr style="cursor: pointer" class="${actionBean.id == message.id ? 'info' : ''}" onclick="${'window.location.href=\''.concat(editLink).concat('\'')}">
            <td><c:out value="${message.subject}"/></td>
            <td><c:out value="${message.dtgstart}"/></td>
            <td><c:out value="${message.dtgend}"/></td>
            <td class="table-actions">
              <stripes:link beanclass="nl.opengeogroep.safetymaps.server.admin.stripes.MessagesActionBean" event="edit" title="Bewerken">
                <stripes:param name="id" value="${message.id}"/>
                <span class="glyphicon glyphicon-pencil"></span>
              </stripes:link>
              <stripes:link class="remove-item" beanclass="nl.opengeogroep.safetymaps.server.admin.stripes.MessagesActionBean" event="delete" title="Verwijderen">
                <stripes:param name="id" value="${message.id}"/>
                <span class="glyphicon glyphicon-remove"></span>
              </stripes:link>
            </td>
          </tr>
        </c:forEach>
      </tbody>
    </table>

    <stripes:form beanclass="nl.opengeogroep.safetymaps.server.admin.stripes.MessagesActionBean" class="form-horizontal">
      <c:set var="event" value="${actionBean.context.eventName}"/>
      <c:if test="${event == 'list'}">
        <stripes:submit name="edit" class="btn btn-primary">Nieuw bericht</stripes:submit>
      </c:if>
      <c:if test="${event == 'edit' || event == 'save'}">
        <stripes:submit name="save" class="btn btn-primary">Opslaan</stripes:submit>
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
            <stripes:text class="form-control" name="subject" />
          </div>
        </div>
        <div class="form-group">
          <label class="col-sm-2 control-label">Van:</label>
          <div class="col-sm-10">
            <stripes:text class="form-control" name="dtgstart" />
          </div>
        </div>
        <div class="form-group">
          <label class="col-sm-2 control-label">Tot:</label>
          <div class="col-sm-10">
            <stripes:text class="form-control" name="dtgend" />
          </div>
        </div>
        <div class="form-group">
          <label class="col-sm-2 control-label">Bericht:</label>
          <div class="col-sm-10">
            <stripes:text class="form-control" name="description" />
          </div>
        </div>
      </c:if>
    </stripes:form>

  </stripes:layout-component>
</stripes:layout-render>
