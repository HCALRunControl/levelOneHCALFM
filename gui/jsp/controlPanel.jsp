
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
<div id="wrapper" width="100%" border="0"  style="position:absolute; top:0; background-color: #3a5165;">

  <div id="header"> 
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

    <!-- Central title begin -->
    <div id="hcaltitle" width="50%" height="49" align="left" valign="central" bordercolor="#CC6600" class="header">
      HCAL Run Control
    </div>
    <!-- Central title end -->
    <div width="50%" align="right" class="header">
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
    </div>
  </div>

  <div id="middle"> 
    <div id="sidebar"  height="21" class="header">&nbsp;</td>
    <!-- Menu begin -->
      <rcms.menu:menuRenderer indentation="3"/>
    </div>
    <!-- Menu end -->
    <!-- Custom dynamic content begin -->
    <div id="center" height="259" valign="top" colspan="2" style="background-color:#fdfdfd">
      <!-- Form begin -->
      <form name="FMPilotForm" id="FMPilotForm" method="POST" action="FMPilotServlet">
        <rcms.control:actionHiddenInputRenderer indentation="4"/>
        <rcms.control:commandHiddenInputRenderer indentation="4"/>
        <rcms.notification:hiddenInputRenderer indentation="4"/>
        <rcms.control:configurationKeyRenderer titleClass="control_label1" label="Configuration Keys:&nbsp;" contentClass="control_label2" indentation="10"/>
        <input type="hidden" id="globalParameterName1"  name="globalParameterName1" value="" />
        <input type="hidden" id="globalParameterValue1" name="globalParameterValue1" value="" />
        <input type="hidden" id="globalParameterType1"  name="globalParameterType1" value="" />
        <input type="hidden" id="NO_RESPONSE" name="NO_RESPONSE" value="" />
        <!-- Working area begin -->
        <%@ include file="./mainArea.jspf"%>
        <!-- Working area end -->
      </form>
    </div>
    <!-- Custom dynamic content end -->
  </div>
  <div id="bottom">
    <%@ include file="./footer.jspf"%>
  </div>
</div>

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
