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
  <link href="https://fonts.googleapis.com/css?family=Open+Sans:400italic,600italic,700italic,400,600,700" rel="stylesheet" type="text/css">

  <title>Run Control and Monitoring System</title>
  <rcms.control:customResourceRenderer indentation="1" type="css" path="/css/common.css" />
  <rcms.control:customResourceRenderer indentation="1" type="css" path="/css/hcalcontrol.css" />
  <rcms.control:customResourceRenderer indentation="1" type="js" path="/js/jquery.min.js" />
  <rcms.control:customResourceRenderer indentation="1" type="js" path="/js/md5.js" />
  <rcms.control:customResourceRenderer indentation="1" type="js" path="/js/jquery.browser-fingerprint-1.1.js" />
  <rcms.control:customResourceRenderer indentation="1" type="js" path="/js/hcalui.js" />
  <rcms.control:customResourceRenderer indentation="1" type="js" path="/js/GUI.js" />
  <rcms.control:customResourceRenderer indentation="1" type="js" path="/js/stateNotification.js" />
  <rcms.control:customResourceRenderer indentation="1" type="customPath" path="/icons/redX.png" htmlId="redX"/>
  <rcms.control:customResourceRenderer indentation="1" type="customPath" path="/icons/greenCheck.png" htmlId="greenCheck"/>
  <script type="text/javascript">
    var guiInst = new GUI();
  </script>
  <rcms.control:customResourceRenderer indentation="1" type="js" path="/js/ajaxRequest.js" />
  <%--<rcms.control:customResourceRenderer indentation="1" type="js" path="/js/ajaxRequestFunctions.js" />--%>
  <%--<rcms.control:customResourceRenderer indentation="1" type="js" path="/js/notifications.js" />--%>
  <%--<script type="text/javascript" src="../js/stateNotification.js"></script>--%>
  <script type="text/javascript" src="../js/common.js"></script>
  <script type="text/javascript" src="../js/control.js"></script>
  <script type="text/javascript" src="../js/globalParameters.js"></script>

  <!-- Custom javascript section begin -->
  <script>
    <rcms.control:onLoadJSRenderer reloadOnStateChange="false" commandButtonCssClass="button1" commandParameterCheckBoxTitle="&nbsp;Show Command Parameter Section" commandParameterCssClass="label_left_black" indentation="2"/>
    <rcms.control:buttonsJSRenderer indentation="2"/>
    <rcms.notification:jSRenderer indentation="2"/>
    <rcms.globalParameter:jSRenderer indentation="2"/>

  </script>

  <script>
    function activate_relevant_table(tbid) {
      if (<%= myFMPilotBean.getSessionState().isInputAllowed(FMPilotState.REFRESH) %>) {turn_on_visibility(tbid);}
      else {turn_off_visibility(tbid);}
    }
  </script>
  <rcms.control:customResourceRenderer indentation="1" type="js" path="/js/notifications.js" />
  <!-- Custom javascript section end -->

</head>


<body style="display: none">
<!-- Table T1 begin -->
<table width="100%" border="0" cellpadding="0" cellspacing="0" style="position:absolute; top:0; background-color: #3a5165;">

  <!-- Header fragment -->
	<!-- T1_Row1 begin -->
	<tr> 
		<td height="97" align="center" class="header">
			<!-- Logo begin-->
      <div id="logoSpot">
        <rcms.control:customResourceRenderer indentation="1" type="img" path="/icons/hcal.png" htmlId="hcalLogo" />
        <br />
			  <a href="https://twiki.cern.ch/twiki/bin/view/CMS/HCALFunctionManager" target="_blank">HCALFM documentation
			  </a>
			  <!-- Logo end -->
			  <br />
        <div id="hostport">
			  <!-- Host : Port begin -->
			  	<%=request.getLocalName()%>:<%=request.getLocalPort()%>
			  <!-- Host : Port end -->
        </div>
      </div>
		</td>

		<!-- Central title begin -->
		<th id="hcaltitle" width="50%" height="49" align="left" valign="central" bordercolor="#CC6600" class="header">
		  HCAL Run Control
		</th>
		<!-- Central title end -->
		
		<td width="50%" align="right" class="header">
			<!-- Version begin -->
			Tag : <b><%=getServletContext().getAttribute("version")%></b>
			<!-- Version end -->
		  </p>
			<!-- Tag begin -->
			<%if (request.getRemoteUser() != null) {%>
			  User : &nbsp;
			<b><%= request.getRemoteUser()%></b>
			<%}%>
			<!-- Tag end -->
			<p id="versionSpot"></p>
      <p>
			<!-- Bug-report link begin -->
			  <a href="https://github.com/HCALRunControl/levelOneHCALFM/issues"> File a bug report </a>
			<!-- Bug-report link  end -->
		  </p>
		</td>
	</tr>
	<!-- T1_Row1 end -->
	
	<!-- T1_Row2 begin -->
	<tr> 
		<td height="21" class="header">&nbsp;</td>
		<td height="21" colspan="2" style="border-radius: 10px 0px 0px 0px; background-color: fdfdfd;">&nbsp;</td>
	</tr>
	<!-- T1_Row2 end -->

  <!-- T1_Row3 begin -->
  <tr>
    <!-- Menu begin -->
    <td id="sidebar" valign="top">
      
      <br />
      <rcms.menu:menuRenderer indentation="3"/>
    </td>
    <!-- Menu end -->
    <!-- Custom dynamic content begin -->
    <td height="259" valign="top" colspan="2" style="background-color:#fdfdfd">
      <!-- Form begin -->
      <form name="FMPilotForm" id="FMPilotForm" method="POST" action="FMPilotServlet">
        <rcms.control:actionHiddenInputRenderer indentation="4"/>
        <rcms.control:commandHiddenInputRenderer indentation="4"/>
        <rcms.notification:hiddenInputRenderer indentation="4"/>
        <rcms.control:configurationKeyRenderer titleClass="control_label1" label="Configuration Keys:&nbsp;" contentClass="control_label2" indentation="10"/>
		<input type="hidden" id="globalParameterName1"
			name="globalParameterName1" value="" />
		<input type="hidden"
			id="globalParameterValue1" name="globalParameterValue1" value="" />
		<input type="hidden" id="globalParameterType1"
			name="globalParameterType1" value="" />
		<input type="hidden"
			id="NO_RESPONSE" name="NO_RESPONSE" value="" />
        <!-- Working area begin -->
        <table border="0" cellpadding="0" cellspacing="10" align="left">
          <!-- First section begin -->
          <tr>
            <td align="center" valign="top">
              <!-- Control Console begin -->
              <table border="1" cellpadding="10" cellspacing="1" align="left" width="280" style="border: 1px solid black;">
                <tr>
                  <td>
                    <rcms.control:stateRenderer titleClass="hcal_control_state" label="State:&nbsp;" indentation="10"/>
                    <br /><br />
                    <div id="fullPath">
                      <rcms.control:configurationPathRenderer titleClass="control_label1" label="Full Path:&nbsp;" contentClass="control_label2" indentation="10"/>
                    </div>
                     <br /><br />
                    <rcms.control:configurationNameRenderer titleClass="control_label1" label="Group Name:&nbsp;" contentClass="control_label2" indentation="10"/>
                  </td>
                </tr>
                <tr>
                  <td align="center">
                    <rcms.control:createButtonRenderer cssClass="button1" onClickFunction="onCreateButton()" name="Create" indentation="10"/>
                    <rcms.control:attachButtonRenderer cssClass="button1" onClickFunction="onAttachButton()" name="Attach" indentation="10"/>
                    <rcms.control:detachButtonRenderer cssClass="button1" onClickFunction="onDetachButton()" name="Detach" indentation="10"/>
                    <rcms.control:destroyButtonRenderer cssClass="button1" onClickFunction="onDestroyButton()" name="Destroy" indentation="10"/>
                    <br /><br />
                    <rcms.control:refreshButtonRenderer cssClass="button1" onClickFunction="onRefreshButton()" name="Refresh" indentation="10"/>
                    <rcms.control:showTreeButtonRenderer cssClass="button1" onClickFunction="onShowTreeButton()" name="Status Display" indentation="10"/>
                    <rcms.control:showStatusTableButtonRenderer cssClass="button1" onClickFunction="onShowStatusTableButton()" name="Status Table" indentation="10"/>
                  </td>
                </tr>
                <tr>
                  <td align="center">
                    <div id="commandSection">
                      <rcms.control:commandButtonsRenderer cssClass="button1" indentation="11"/>
                    </div>
                    <br />
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
                            <table id="hcalmaintable" width="720" border="0" cellpadding="10" cellspacing="0">
                              <tr>
                                <td class="title_center_black_yellow_bg" width="60">
                                  Modify
                                </td>
                                <td class="title_center_black_yellow_bg">
                                  Parameter
                                </td>
                                <td class="title_center_black_yellow_bg" width="450px">
                                  Value
                                </td>
                              </tr>
                              <tr>
                                <td id="newCFGSNIPPET_KEY_SELECTEDcheckbox" class="label_center_black">
                                </td>
                                <td class="label_left_black">
                                  <strong>Local Run Key</strong><div id="dropdowndiv"></div>
                                </td>
                                <td id ="newCFGSNIPPET_KEY_SELECTED" class="label_center_black">
                                </td>
                              </tr>
                              <tr>
                                <td id="newRUN_CONFIG_SELECTEDcheckbox" class="label_center_black">
                                </td>
                                <td class="label_left_black">
                                  <strong>Mastersnippet</strong>
                                </td>
                                <td id ="newRUN_CONFIG_SELECTED" class="label_center_black">
                                </td>
                              </tr>
                              <tr>
                                <td id="newMASK_SUMMARYcheckbox" class="label_center_black">
                                </td>
                                <td class="label_left_black">
                                  <strong>Mask Summary</strong><br /><input id="showFullMasks" type="checkbox" onclick="$('#maskedResourcesField').toggle(); $('#singlePartitionField').toggle();" />Show full masks
                                </td>
                                <td id ="newMASK_SUMMARY" class="label_center_black">
                                </td>
                              </tr>
                              <tr id="maskedResourcesField" style="display: none;">
                                <td id="newMASKED_RESOURCEScheckbox" class="label_center_black">
                                </td>
                                <td class="label_left_black">
                                  <strong>Masked Resources</strong>
                                </td>
                                <td id ="newMASKED_RESOURCES" class="label_center_black">
                                </td>
                              </tr>
                              <tr id="singlePartitionField" >
                                <td id="newSINGLEPARTITION_MODEcheckbox" class="label_center_black">
                                </td>
                                <td class="label_left_black">
                                  <strong>Singlepartition</strong>
                                </td>
                                <td id ="newSINGLEPARTITION_MODE" class="label_center_black">
                                </td>
                              </tr>
                              <tr id="driverIdField" style="display: none;"> 
                                <td id="newDRIVER_IDENTIFIERcheckbox" class="label_center_black">
                                </td>
                                <td class="label_left_black">
                                  <strong>Driver Identifier</strong>
                                </td>
                                <td id ="newDRIVER_IDENTIFIER" class="label_center_black">
                                </td>
                              </tr>
                              <tr>
                                <td id="newNUMBER_OF_EVENTScheckbox" class="label_center_black">
                                </td>
                                <td class="label_left_black">
                                  <strong>Number of Events</strong>
                                </td>
                                <td id ="newNUMBER_OF_EVENTS" class="label_center_black">
                                </td>
                              </tr>
                              <tr>
                                <td class="label_center_black">
                                  &nbsp;
                                </td>
                                <td class="label_left_black">
                                  <strong>Events Taken</strong>
                                </td>
                                <td id ="newHCAL_EVENTSTAKEN" class="label_center_black">
                                </td>
                              </tr>
                              <tr>
                                <td id="newRUN_NUMBERcheckbox" class="label_center_black">
                                </td>
                                <td class="label_left_black">
                                  <strong>Run Number</strong>
                                </td>
                                <td id ="newRUN_NUMBER" class="label_center_black">
                                </td>
                              </tr>
                              <tr>
                                <td class="label_center_black">
                                  &nbsp;
                                </td>
                                <td class="label_left_black">
                                  <strong>FM Start Time</strong>
                                </td>
                                <td id ="newHCAL_TIME_OF_FM_START" class="label_center_black">
                                </td>
                              </tr>
                              <tr>
                                <td id="newACTION_MSGcheckbox" class="label_center_black">
                                </td>
                                <td class="label_left_black">
                                  <strong>Action Message</strong>
                                </td>
                                <td id ="newACTION_MSG" class="label_center_black">
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
                                <td class="label_center_black" colspan="3" id="masked_resourses_td">
                                  <div>
				     <div>
                                       <div class="maskModes" id="multiPartition"><strong>Multipartition</strong></div>
                                       <div class="maskModes" id="singlePartition"><strong>Single partition</strong></div>
                                    </div>
                                    <br />
                                    <div>
                                      Masked FMs: <span id="maskTest"></span>
                                      <div>
                                        <div id="multiPartitionSelection" class="partitionSelection"></div>
                                        <div id="singlePartitionSelection" class="partitionSelection"></div>
                                     </div>
                                   </div>
                                  </div>
                                </td>
                              </tr>
                              <tr>
                                <!--eLog info for quick copy/pasting-->
                                <td class="label_center_black" colspan="3">
                                  <div>
                                    <strong>Quick Info</strong>
                                    <br />
                                    <div id="elogInfo"></div>
                                  </div>
                                </td>
                              </tr>
                            </table>
                          <br />
                          <br />
                          <!--Buttons for main table -->
                          <center>
                            <input id="spectate" class="button1" type="button" onclick="spectatorMode(true);" value="Spectate">
                            <input id="drive" class="button1" type="button" onclick="spectatorMode(false); takeOverDriving()" value="Take Control">
                          </center>
			  <br>
                          <center>
                            <input id="setGlobalParametersButton" class="button1" type="button" onclick="onClickSetGlobalParameters()" value="Set Enabled Parameters" name="setGlobalParametersButton">
                            <input id="refreshGlobalParametersButton" class="button1" type="button" onclick="onClickRefreshGlobalParameter()" value="Refresh Parameters" name="refreshGlobalParametersButton">
                          </center>
                        </td>
                      </tr>

                      <tr><td><br /><br /></td></tr>

                      <tr>
                        <td align="center" class="label_center_black">
                          <input id="globalParametersCheckbox" type="checkbox" onclick="toggle_visibility('GlobalParamsTable');"> <strong>&nbsp; View Global Parameters</strong>
                          <br /><br />
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
                                  <br />
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
  <script type="text/javascript">
    guiInst.attach(document);

    // a call to onLoad is needed since it starts the notification system
    $(document).ready(function() {
      onLoad();
      hcalOnLoad(); 
      $("body").show();
    });
  </script>
</body>
</html>
