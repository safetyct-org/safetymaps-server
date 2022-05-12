<%--
Copyright (C) 2019 B3Partners B.V.

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

<stripes:layout-render name="/WEB-INF/jsp/templates/admin.jsp" pageTitle="Groepenbeheer" menuitem="groups">
    <stripes:layout-component name="content">

        <h1>Groepenbeheer</h1>

        <p>Let op! Voor een gebruiker zijn alleen de modules beschikbaar die bij groepen waarvan hij lid
            van is geautoriseerd zijn (cumulatief).</p>

        <p>Voor speciale groepen kunnen geen modules ingesteld worden.</p>

        <table class="table table-bordered table-striped table-fixed-header table-condensed table-hover" id="layers-table">
            <thead>
                <tr>
                    <th>Naam</th>
                    <th>Omschrijving</th>
                    <th>Geautoriseerde modules</th>
                    <th>Geautoriseerde lagen</th>
                    <th class="table-actions">&nbsp;</th>
                </tr>
            </thead>
            <tbody>
            <c:forEach var="r" items="${actionBean.allRoles}">
                <stripes:url var="editLink" beanclass="nl.opengeogroep.safetymaps.server.admin.stripes.EditGroupsActionBean" event="edit">
                    <stripes:param name="role" value="${r.role}"/>
                </stripes:url>
                <%--c:if test="${r['protected']}">
                    <c:set var="editLink" value=""/>
                </c:if--%>
                <tr style="${editLink == '' ? '' : 'cursor: pointer'}" class="${actionBean.role == r.role ? 'info' : ''}" onclick="${editLink != '' ? 'window.location.href=\''.concat(editLink).concat('\'') : ''}">
                    <td><c:out value="${r.role}"/></td>
                    <td><c:out value="${r.description}"/></td>
                    <td><c:out value="${r.modules}"/></td>
                    <td><c:out value="${r.wms}"/></td>
                    <td class="table-actions">
                        <c:if test="${!empty editLink}">
                            <stripes:link beanclass="nl.opengeogroep.safetymaps.server.admin.stripes.EditGroupsActionBean" event="edit" title="Bewerken">
                                <stripes:param name="role" value="${r.role}"/>
                                <span class="glyphicon glyphicon-pencil"></span>
                            </stripes:link>
                            <stripes:link class="remove-item" beanclass="nl.opengeogroep.safetymaps.server.admin.stripes.EditGroupsActionBean" event="delete" title="Verwijderen">
                                <stripes:param name="role" value="${r.role}"/>
                                <span class="glyphicon glyphicon-remove"></span>
                            </stripes:link>
                        </c:if>
                    </td>
                </tr>
            </c:forEach>
            </tbody>
        </table>
        <jsp:include page="/WEB-INF/jsp/common/messages.jsp"/>

        <stripes:form beanclass="nl.opengeogroep.safetymaps.server.admin.stripes.EditGroupsActionBean" class="form-horizontal">
            <c:set var="event" value="${actionBean.context.eventName}"/>

            <br>
            <c:if test="${event == 'list'}">
                <stripes:submit name="edit" class="btn btn-primary">Nieuwe groep</stripes:submit>
            </c:if>
            <c:if test="${event == 'edit' || event == 'save'}">

                <stripes:submit name="save" class="btn btn-primary">Opslaan</stripes:submit>
                <stripes:submit name="delete" class="btn btn-danger remove-item">Verwijderen</stripes:submit>
                <stripes:submit name="cancel" class="btn btn-default">Annuleren</stripes:submit>

                <c:choose>
                    <c:when test="${empty actionBean.role}">
                        <h2>Bewerken van nieuwe groep</h2>
                    </c:when>
                    <c:otherwise>
                        <h2>Bewerken van groep &quot;<c:out value="${actionBean.role}"/>&quot;</h2>
                    </c:otherwise>
                </c:choose>

                <div class="form-group">
                    <label class="col-sm-2 control-label">Naam:</label>
                    <div class="col-sm-10">
                        <stripes:text class="form-control" name="role" disabled="${!empty actionBean.role}"/>
                        <c:if test="${!empty actionBean.role}">
                            <stripes:hidden name="role" value="${actionBean.role}"/>
                        </c:if>
                    </div>
                </div>
                <c:if test="${!actionBean.protectedGroup}">
                    <div class="form-group">
                        <label class="col-sm-2 control-label">Geautoriseerde modules:</label>
                        <div class="col-sm-10">
                            Let op! Indien de module voor alle gebruikers is uitgeschakeld is deze doorgestreept.
                            <p>
                            <c:forEach var="module" items="${actionBean.allModules}" varStatus="status">
                                <div class="custom-control custom-checkbox">
                                    <stripes:checkbox name="modules" class="custom-control-input" value="${module.name}" id="role${status.index}" onclick="javascript:handleChildren(this, '${module.name}');"/>
                                    <label class="custom-control-label" for="role${status.index}" style="${module.enabled ? '' : 'text-decoration: line-through;'}"><c:out value="${module.description} (${module.name})"/></label>
                                    
                                    <c:forEach var="extraRole" items="${actionBean.allExtraRoles}" varStatus="innerstatus">
                                        <c:if test="${fn:containsIgnoreCase(extraRole.role, '_'.concat(module.name).concat('_')) && not fn:contains(extraRole.role, extraRole.role.concat('__'))}">
                                            <div class="custom-control custom-checkbox" style="margin-left: 32px !important;">
                                                <c:if test="${not fn:contains(actionBean.modules, module.name)}">
                                                    <stripes:checkbox name="extraRoles" class="custom-control-input ${module.name}_child" value="${extraRole.role}" id="extraRole${module.name}${status.index}" disabled="true"/>
                                                </c:if>
                                                <c:if test="${fn:contains(actionBean.modules, module.name)}">
                                                    <stripes:checkbox name="extraRoles" class="custom-control-input ${module.name}_child" value="${extraRole.role}" id="extraRole${module.name}${status.index}" disabled="false"/>

                                                    <c:forEach var="innerExtraRole" items="${actionBean.allExtraRoles}" varStatus="deepinnerstatus">
                                                        <c:if test="${fn:containsIgnoreCase(innerExtraRole.role, concat(extraRole.role).concat('__'))}">
                                                            <div class="custom-control custom-checkbox" style="margin-left: 64px !important;">
                                                                <c:if test="${not fn:contains(actionBean.extraRoles, extraRole.role)}">
                                                                    <stripes:checkbox name="extraRoles" lass="custom-control-input ${extraRole.role}_child" value="${innerExtraRole.role}" id="innerExtraRole${extraRole.role}${status.index}" disabled="true"/>
                                                                </c:if>
                                                                <c:if test="${fn:contains(actionBean.extraRoles, extraRole.role)}">
                                                                    <stripes:checkbox name="extraRoles" lass="custom-control-input ${extraRole.role}_child" value="${innerExtraRole.role}" id="innerExtraRole${extraRole.role}${status.index}" disabled="false"/>
                                                                </c:if>
                                                                <label class="custom-control-label" style="font-weight: 300 !important;" for="innerExtraRole${extraRole.role}${deepinnerstatus.index}"><c:out value="${innerExtraRole.description}"/></label>
                                                            </div>
                                                        </c:if>
                                                    </c:forEach>

                                                </c:if>
                                                <label class="custom-control-label" style="font-weight: 300 !important;" for="extraRole${module.name}${innerstatus.index}"><c:out value="${extraRole.description}"/></label>
                                            </div>
                                        </c:if>
                                    </c:forEach>
                                    
                                </div>
                            </c:forEach>
                        </div>
                    </div>
                    <div class="form-group">
                        <label class="col-sm-2 control-label">Geautoriseerde lagen:</label>
                        <div class="col-sm-10">
                            <p>
                            <c:forEach var="layer" items="${actionBean.allLayers}" varStatus="status">
                                <div class="custom-control custom-checkbox">
                                    <stripes:checkbox name="layers" class="custom-control-input" value="${layer.uid}" id="authLayer${status.index}"/>
                                    <label class="custom-control-label" for="authLayer${status.index}" style="${layer.enabled ? '' : 'text-decoration: line-through;'}"><c:out value="${layer.name} (${layer.uid})"/></label>
                                </div>
                            </c:forEach>
                        </div>
                    </div>
                    <div class="form-group">
                        <label class="col-sm-2 control-label">Standaard lagen:</label>
                        <div class="col-sm-10">
                            <p>
                            <c:forEach var="layer" items="${actionBean.layers}" varStatus="status">
                                <div class="custom-control custom-checkbox">
                                    <stripes:checkbox name="defaultlayers" class="custom-control-input" value="${layer}" id="layer${status.index}"/>
                                    <label class="custom-control-label" for="layer${status.index}"><c:out value="${layer}"/></label>
                                </div>
                            </c:forEach>
                        </div>
                    </div>
                </c:if>
                <div class="form-group">
                    <label class="col-sm-2 control-label">Gebruikers lid van deze groep:</label>
                    <div class="col-sm-10">
                        <p class="help-block text-warning">Let op: wijzigingen in groeplidmaatschap worden pas toegepast na uit- en inloggen. Als de uitlogknop is
                            verborgen kan alleen worden uitgelogd door de hele browsercache te legen of door een beheerder met de "Overal uitloggen" knop op de gebruikerspagina.</p>
                        <c:forEach var="user" items="${actionBean.allUsers}" varStatus="status">
                            <div class="custom-control custom-checkbox">
                                <stripes:checkbox name="users" class="custom-control-input" value="${user}" id="role${status.index}"/>
                                <label class="custom-control-label" for="role${status.index}"><c:out value="${user}"/></label>
                            </div>
                        </c:forEach>
                    </div>
                </div>
            </c:if>
        </stripes:form>

        <script language="javascript" type="text/javascript">
            function handleChildren(obj, module) {
                Array.from(document.getElementsByClassName(module + "_child")).forEach(
                    function (element, index, array) {
                        element.checked = false;
                        element.disabled = (obj.checked == false);
                    }
                );
            }
        </script>

    </stripes:layout-component>
</stripes:layout-render>