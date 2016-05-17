	<!-- T1_Row1 begin -->
	<tr> 
		<td height="97" align="center" bgcolor="#EEEEEE">
			<!-- Logo begin-->
			<a href="http://cmsdoc.cern.ch/TriDAS/RCMS/" target="_blank">
				<img src="../icons/rcms2logosm.gif" alt="RCMS2" width="89" height="80" border="0">
			</a>
			<!-- Logo end -->
			<br>
			<!-- Host : Port begin -->
			<font color="Black" size="1" face="Arial, Helvetica, sans-serif">
				<%=request.getLocalName()%>:<%=request.getLocalPort()%>
			</font>
			<!-- Host : Port end -->
		</td>

		<!-- Central title begin -->
		<th width="50%" height="49" align="left" valign="central" bordercolor="#CC6600" bgcolor="#EEEEEE">
				<font color="#000099" size="4" face="Arial, Helvetica, sans-serif">
					HCAL Run Control
				</font>
		</th>
		<!-- Central title end -->
		
		<td width="50%" align="right" bgcolor="#EEEEEE">
			<p id="versionSpot"></p>
			<p>
			<!-- Version begin -->
			<font color="#000099" size="1" face="Arial, Helvetica, sans-serif">
				Tag : <b><%=getServletContext().getAttribute("version")%></b>
			</font>
			<!-- Version end -->
		  </p>
			<!-- Tag begin -->
			<%if (request.getRemoteUser() != null) {%>
			<font size="1" color="#000099" face="Arial, Helvetica, sans-serif">
				User : &nbsp;
				<b><%= request.getRemoteUser()%></b>
			</font>
			<%}%>
			<!-- Tag end -->
      <p>
			<!-- Bug-report link begin -->
			<font color="#000099" size="1" face="Arial, Helvetica, sans-serif">
				<a href="https://github.com/HCALRunControl/levelOneHCALFM/issues"> File a bug report </a>
			</font>
			<!-- Bug-report link  end -->
		  </p>
		</td>
	</tr>
	<!-- T1_Row1 end -->
	
	<!-- T1_Row2 begin -->
	<tr> 
		<td height="21" bgcolor="#EEEEEE">&nbsp;</td>
		<td height="21" colspan="2" bgcolor="#FFFFFF">&nbsp;</td>
	</tr>
	<!-- T1_Row2 end -->
