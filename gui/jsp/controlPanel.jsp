<%@ page language="java" contentType="text/html"%>
<%@ page import="java.util.*" %>
<%@ page import="rcms.gui.servlet.pilot.FMPilotConstants" %>
<%@ page import="rcms.gui.common.FMPilotState" %>
<%@ page import="rcms.gui.servlet.pilot.FMPilotBean" %>

<%@ page isELIgnored="false"%>

<%@ taglib prefix="rcms.menu"            uri="rcms.menu"            %>
<%@ taglib prefix="rcms.control"         uri="rcms.control"         %>
<%@ taglib prefix="rcms.globalParameter" uri="rcms.globalParameter" %>
<%@ taglib prefix="rcms.notification"    uri="rcms.notification"    %>

<!-- Optional Section to set the visibility of available commands at a given state begin -->
<%
  /*
  List visibleCommandList = new ArrayList();
  visibleCommandList.add("TurnOn");
  visibleCommandList.add("TurnOff");
  pageContext.setAttribute(FMPilotConstants.VISIBLE_COMMANDS, visibleCommandList);
  */
%>

<%
FMPilotBean myFMPilotBean = (FMPilotBean)(pageContext.getRequest().getAttribute(FMPilotConstants.FM_PILOT_BEAN));
%>

<!-- Cache fragment -->
<jsp:include page="./cache.jsp"/>

<rcms.control:menuCreator/>

<html>
<rcms.globalParameter:getParameterMap fmParameterContainer="pars" />


<head>
  <meta Http-Equiv="Cache-Control" Content="no-cache">
  <meta Http-Equiv="Pragma" Content="no-cache">
  <meta Http-Equiv="Expires" Content="0">

  <title>Run Control and Monitoring System</title>
  <link rel="StyleSheet" href="../css/common.css" type="text/css"/>
  <link rel="StyleSheet" href="../css/control.css" type="text/css"/>
  <rcms.control:customResourceRenderer indentation="1" type="css" path="/css/hcalcontrol.css" />
  <rcms.control:customResourceRenderer indentation="1" type="js" path="/js/jquery.min.js" />
  <rcms.control:customResourceRenderer indentation="1" type="js" path="/js/hcalui.js" />
  <script type="text/javascript" src="../js/stateNotification.js"></script>
  <script type="text/javascript" src="../js/common.js"></script>
  <script type="text/javascript" src="../js/control.js"></script>
  <script type="text/javascript" src="../js/globalParameters.js"></script>

  <!-- Custom javascript section begin -->
  <script>
    <rcms.control:onLoadJSRenderer reloadOnStateChange="false" commandButtonCssClass="button1" commandParameterCheckBoxTitle="&nbsp;Show Command Parameter Section" commandParameterCssClass="label_left_black" indentation="2"/>
    <rcms.control:buttonsJSRenderer indentation="2"/>
    <rcms.notification:jSRenderer indentation="2"/>
    <rcms.globalParameter:jSRenderer indentation="2"/>

    function drawMyCommandButtons(currentState) {
        // do nothing
        // placeholder for custom function
    }

    function myUpdateParameters(message) {
        // do nothing
        // placeholder for custom function
    }


  </script>

  <script>
    function getfullpath() {
      var fullpath = document.getElementsByClassName("control_label2")[0];
      var eloginfo = document.getElementById("elogInfo");
      eloginfo.innerHTML =  "Run # " + ${pars.RUN_NUMBER}  + " -  " + fullpath.innerHTML + " -  Local run key:  ${pars.CFGSNIPPET_KEY_SELECTED}  - " + ${pars.NUMBER_OF_EVENTS} + " events ";
    }
  </script>

  <script>
    $(document).ready(function() {
       setProgress(Math.round(${pars.HCAL_EVENTSTAKEN}/${pars.NUMBER_OF_EVENTS} * 1000)/10);
    });
  </script>

  <script>
    function activate_relevant_table(tbid) {
      if (<%= myFMPilotBean.getSessionState().isInputAllowed(FMPilotState.REFRESH) %>) {turn_on_visibility(tbid);}
      else {turn_off_visibility(tbid);}
    }
  </script>
  <!-- Custom javascript section end -->

</head>

<body onLoad='hcalOnLoad(); makedropdown("${pars.AVAILABLE_RUN_CONFIGS}"); makecheckboxes("${pars.AVAILABLE_RESOURCES}");'>


<!-- Table T1 begin -->
<table width="100%" border="0" cellpadding="0" cellspacing="0">

  <!-- Header fragment -->
  <jsp:include page="./header.jsp"/>

  <!-- T1_Row3 begin -->
  <tr>
    <!-- Menu begin -->
    <td width="17%" valign="top" bgcolor="#EEEEEE">
      <br>
      <rcms.menu:menuRenderer indentation="3"/>
    </td>
    <!-- Menu end -->
    <!-- Custom dynamic content begin -->
    <td height="259" valign="top" colspan="2">
      <!-- Form begin -->
      <form id="FMPilotForm" method="POST" action="FMPilotServlet">
        <rcms.control:actionHiddenInputRenderer indentation="4"/>
        <rcms.control:commandHiddenInputRenderer indentation="4"/>
        <rcms.notification:hiddenInputRenderer indentation="4"/>
        <rcms.control:configurationKeyRenderer titleClass="control_label1" label="Configuration Keys:&nbsp;" contentClass="control_label2" indentation="10"/>
        <!-- Working area begin -->
        <table border="0" cellpadding="0" cellspacing="10" align="left">
          <!-- First section begin -->
          <tr>
            <td align="center" valign="top">
              <!-- Control Console begin -->
              <table border="1" cellpadding="10" cellspacing="1" align="left" width="280">
                <tr>
                  <td>
                    <rcms.control:stateRenderer titleClass="hcal_control_state" label="State:&nbsp;" indentation="10"/>
                    <br><br>
                    <div id="fullPath">
                      <rcms.control:configurationPathRenderer titleClass="control_label1" label="Full Path:&nbsp;" contentClass="control_label2" indentation="10"/>
                    </div>
                     <br><br>
                    <rcms.control:configurationNameRenderer titleClass="control_label1" label="Group Name:&nbsp;" contentClass="control_label2" indentation="10"/>
                  </td>
                </tr>
                <tr>
                  <td align="center">
                    <rcms.control:createButtonRenderer cssClass="button1" onClickFunction="onCreateButton()" name="Create" indentation="10"/>
                    <rcms.control:attachButtonRenderer cssClass="button1" onClickFunction="onAttachButton()" name="Attach" indentation="10"/>
                    <rcms.control:detachButtonRenderer cssClass="button1" onClickFunction="onDetachButton()" name="Detach" indentation="10"/>
                    <rcms.control:destroyButtonRenderer cssClass="button1" onClickFunction="onDestroyButton()" name="Destroy" indentation="10"/>
                    <br><br>
                    <rcms.control:refreshButtonRenderer cssClass="button1" onClickFunction="onRefreshButton()" name="Refresh" indentation="10"/>
                    <rcms.control:showTreeButtonRenderer cssClass="button1" onClickFunction="onShowTreeButton()" name="Status Display" indentation="10"/>
                    <rcms.control:showStatusTableButtonRenderer cssClass="button1" onClickFunction="onShowStatusTableButton()" name="Status Table" indentation="10"/>
                  </td>
                </tr>
                <tr>
                  <td align="center" bgcolor="#cccccc">
                    <div id="commandSection">
                      <rcms.control:commandButtonsRenderer cssClass="button1" indentation="11"/>
                    </div>
                    <br>
                    <div id="commandParameterCheckBoxSection" class="control_label1">
                      <input id="commandParameterCheckBox" type="checkbox" onclick="onClickCommandParameterCheckBox(); toggle_visibility('Blork');" value="" name="commandParameterCheckBox ">  &nbsp; Show Command Parameter Section
                    </div>
                  </td>
                </tr>
              </table>
              <!-- Control Console end -->
            </td>
            <td>
              <table id="AllParamTables" class="tbl">
                <tr>
                  <td align="center" valign="top">
                    <table>
                      <tr>
                        <td align="center" valign="top">
                          <!--New main table-->
                            <table width="720" border="1" cellpadding="10" cellspacing="0">
                              <tr>
                                <td class="title_center_black_yellow_bg" width="155">
                                  Enable user control
                                </td>
                                <td class="title_center_black_yellow_bg">
                                  Parameter
                                </td>
                                <td class="title_center_black_yellow_bg" width="155">
                                  Value
                                </td>
                              </tr>
                              <tr>
                                <td id="newCFGSNIPPET_KEY_SELECTEDcheckbox" class="label_center_black" width="155">
                                </td>
                                <td class="label_left_black">
                                  <strong>Local Run Key</strong><div id="dropdowndiv"></div>
                                </td>
                                <td id ="newCFGSNIPPET_KEY_SELECTED" class="label_center_black" width="155">
                                </td>
                              </tr>
                              <tr>
                                <td id="newRUN_CONFIG_SELECTEDcheckbox" class="label_center_black" width="155">
                                </td>
                                <td class="label_left_black">
                                  <strong>Mastersnippet</strong>
                                </td>
                                <td id ="newRUN_CONFIG_SELECTED" class="label_center_black" width="155">
                                </td>
                              </tr>
                              <tr>
                                <td id="newMASKED_RESOURCEScheckbox" class="label_center_black" width="155">
                                </td>
                                <td class="label_left_black">
                                  <strong>Masked Resources</strong>
                                </td>
                                <td id ="newMASKED_RESOURCES" class="label_center_black" width="155">
                                </td>
                              </tr>
                              <tr>
                                <td id="newNUMBER_OF_EVENTScheckbox" class="label_center_black" width="155">
                                </td>
                                <td class="label_left_black">
                                  <strong>Number of Events</strong>
                                </td>
                                <td id ="newNUMBER_OF_EVENTS" class="label_center_black" width="155">
                                </td>
                              </tr>
                              <tr>
                                <td class="label_center_black" width="155">
                                  &nbsp;
                                </td>
                                <td class="label_left_black">
                                  <strong>Events Taken</strong>
                                </td>
                                <td id ="newHCAL_EVENTSTAKEN" class="label_center_black" width="155">
                                </td>
                              </tr>
                              <tr>
                                <td id="newRUN_NUMBERcheckbox" class="label_center_black" width="155">
                                </td>
                                <td class="label_left_black">
                                  <strong>Run Number</strong>
                                </td>
                                <td id ="newRUN_NUMBER" class="label_center_black" width="155">
                                </td>
                              </tr>
                              <tr>
                                <td class="label_center_black" width="155">
                                  &nbsp;
                                </td>
                                <td class="label_left_black">
                                  <strong>FM Start Time</strong>
                                </td>
                                <td id ="newHCAL_TIME_OF_FM_START" class="label_center_black" width="155">
                                </td>
                              </tr>
                              <tr>
                                <!--Progress bar-->
                                <td class="label_center_black" colspan="3">
                                  <div class="container"><div class="progressbar"></div></div>
                                </td>
                              </tr>
                              <tr id="supervisorRow" style="display:none">
                                <!--Supervisor error box-->
                                <td id="supervisorCell" class="label_center_black" colspan="3">
                                  <div id="supervisorError" class="hcal_control_Supervisor"></div>
                                </td>
                              </tr>
                              <tr>
                                <!--Area for masking checkboxes-->
                                <td class="label_center_black" colspan="3">
                                  <div>
                                    <input type="checkbox" onclick="toggle_visibility('masks');"><strong>Mask FMs</strong>
                                    <br>
                                      Masked resources: <span id="maskTest"></span>
                                    <div id="masks" class="tbl"></div>
                                  </div>
                                </td>
                              </tr>
                            </table>
                          <br>
                          <br>
                          <!--Buttons for main table -->
                          <center>
                            <input id="setGlobalParametersButton" class="button1" type="button" onclick="onClickSetGlobalParameters()" value="Set Enabled Parameters" name="setGlobalParametersButton">
                            <input id="refreshGlobalParametersButton" class="button1" type="button" onclick="onClickRefreshGlobalParameter()" value="Refresh Parameters" name="refreshGlobalParametersButton">
                          </center>
                        </td>
                      </tr>

                      <tr><td><br><br></td></tr>

                      <tr>
                        <td align="center" class="label_center_black">
                          <input type="checkbox" onclick="toggle_visibility('GlobalParamsTable');"> <strong>&nbsp; View Global Parameters</strong>
                          <br><br>
                          <table id="GlobalParamsTable" class="tbl">
                            <tr>
                              <td align="center">
                                <rcms.globalParameter:tableRenderer indentation="7" title="Global Parameters" titleClass="control_label4" headerClass="title_center_black_yellow_bg" nameClass="label_left_black" valueMaxLenght="100" valueSize="20" buttonClass="button1" setButtonLabel="Set Global Parameters" refreshButtonLabel="Refresh Global Parameters"/>
                              </td>
                            </tr>
                          </table>
                        </td>
                      </tr>
                    </table>
                    <table>
                      <tbody id="parameterTableSection">
                        <tr>
                          <td colspan="2">
                            <table id="Blork" class="tbl">
                              <tr>
                                <td>
                                  <br>
                                  <table width="100%" border="1" cellpadding="10" cellspacing="0">
                                    <tr>
                                      <td class="title_center_black_yellow_bg">
                                      Command
                                      </td>
                                      <td class="title_center_black_yellow_bg">
                                        Enabled
                                      </td>
                                      <td class="title_center_black_yellow_bg">
                                        Name
                                      </td>
                                      <td class="title_center_black_yellow_bg">
                                        Value
                                      </td>
                                      <td class="title_center_black_yellow_bg">
                                        Type
                                      </td>
                                    </tr>
                                    <tbody id="commandParameterSection">
                                      <rcms.control:commandParametersRenderer tdClass="label_left_black" indentation="8"/>
                                    </tbody>
                                  </table>
                                </td>
                              </tr>
                            </table>
                          </td>
                        </tr>
                      </tbody>
                    </table>
                  </td>
                </tr>
              </table>
            </td>
          </tr>
          <!-- First section end -->
        </table>
          <%@ include file="./footer.jspf"%>
        <!-- Working area end -->
      </form>
    </td>
    <!-- Custom dynamic content end -->
  </tr>
  <!-- T1_Row3 end -->

  <!-- Footer fragment -->
          <!-- Second section: Command Parameters begin -->
          <!-- Second section: Command Parameters end -->
</table>

<!-- Table T1 end -->
</body>
</html>
