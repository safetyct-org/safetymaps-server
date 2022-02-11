<%--
Copyright (C) 2012-2020 B3Partners B.V.

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.
--%>

<%@page contentType="text/html" pageEncoding="UTF-8"%>
<%@include file="/WEB-INF/jsp/taglibs.jsp"%>

<!DOCTYPE html>
<html>
    <head>
        <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
        <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
        <title>Safetymaps NG</title>

        <style>
            .modal {
                margin: 0 auto;
                margin-top: 0px;
                max-width: 310px;
                margin-top: calc(50vh - 175px);
                max-height: 350px;
                min-height: 350px;
                border: 1px solid black;
                padding: 15px;
                background-color: rgb(31, 31, 31);
                border-radius: 5px;
            }

            .modal-dialog, .modal-content {
                padding: 0;
                margin: 0;
            }

            h4 {
                margin-top: 0;
                font-size: 1.13em;
                text-align: center;
            }

            .form-group {
                margin-bottom: 15px;
            }

            .form-group label {
                min-width: 300px;
                display: block;
                min-height: 25px;
                font-size: .87em;
                color: rgba(255, 255, 255, .6);
            }

            .form-group input {
                width: 300px;
                min-height: 20px;
                border: 1px solid black;
                background-color: rgba(255,255,255, .1);
                color: white;
                border-radius: 3px;
            }

            .btn-default {
                display: block;
                width: 307px;
                height: 30px;
                margin-top: 30px;
                border: 1px solid rgba(30, 185, 128, 0.87);
                border-radius: 3px;
                background-color: rgba(30, 185, 128, 0.6);
                color: white;
                font-weight: bold;
            }
        </style>
    </head>
    <body style="background-color: rgb(18, 18, 18); color: rgba(255,255,255,0.87); font-family: Roboto, 'Helvetica Neue', sans-serif;">
        <div id="loginpanel" class="modal fade">
          <div class="modal-dialog">
            <div class="modal-content">
              <div class="modal-header">
                <h4 class="modal-title" id="login_title"><span class="glyphicon glyphicon-lock"></span> SAFETYMAPS NG</h4>
              </div>
                <div id="loginpanel_b" class="modal-body">
                    <form method="post" action="j_security_check">
                        <div id="login_msg">
                            <c:catch>
                                <%
                                    String ssoManualHtml = nl.opengeogroep.safetymaps.server.db.Cfg.getSetting("sso_manual_html");
                                    if(ssoManualHtml != null) {
                                        ssoManualHtml = ssoManualHtml.replaceAll(java.util.regex.Pattern.quote("[contextPath]"), request.getContextPath());
                                        out.write(ssoManualHtml);
                                    }
                                %>
                            </c:catch>

                            <c:if test="${!empty loginFailMessage}">
                                <p style="color: #ff5548; font-size: .6em; margin-bottom: 30px;"><c:out value="${loginFailMessage}"/></p>
                            </c:if>
                        </div>
                        <div class="form-group">
                            <label for="j_username"><span class="glyphicon glyphicon-user"></span> <span id="login_username">Gebruikersnaam</span>:</label>
                            <input type="text" class="form-control" name="j_username" autocapitalize="none" autofocus="autofocus">
                        </div>
                        <div class="form-group">
                            <label for="j_password"><span class="glyphicon glyphicon-eye-open"></span> <span id="login_password">Wachtwoord</span>:</label>
                            <input type="password" class="form-control" name="j_password">
                        </div>
                        <input type="submit" id="loginsubmit2" style="display: none" onclick="$('#btn_login_submit').click(); return false;"></input>
                        <button id="btn_login_submit" type="submit" class="btn btn-default btn-success btn-block"><span class="glyphicon glyphicon-log-in"></span> <span id="login_submit">INLOGGEN</span></button>
                    </form>
                </div>
            </div>
          </div>
        </div>


        <script type="text/javascript">
$(document).ready(function() {
    init();
});

function init() {
    $("input[name='j_username']").focus();
}
        </script>
    </body>
</html>
