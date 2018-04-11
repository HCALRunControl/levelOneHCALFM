package rcms.fm.app.level1;

import java.util.Iterator;

import rcms.fm.fw.user.UserEventHandler;
import rcms.fm.fw.user.UserActionException;
import rcms.util.logger.RCMSLogger;
import rcms.fm.fw.EventHandlerException;
import rcms.fm.fw.parameter.ParameterSet;
import rcms.fm.resource.qualifiedresource.FunctionManager;
import rcms.resourceservice.db.resource.fm.FunctionManagerResource;

/**
	* Set Parameter Handler class for HCAL Function Managers
	* 
	* @author Arno Heister
	*
	*/

public class HCALSetParameterHandler extends UserEventHandler {

	static RCMSLogger logger = new RCMSLogger(HCALSetParameterHandler.class);

	protected HCALFunctionManager functionManager = null;

	// switch to be able to ignore any errors which would cause the FM state machine to end in an error state
	public String TestMode = "off";

	public HCALSetParameterHandler() throws EventHandlerException {

		subscribeForEvents(ParameterSet.class);

		// Adds callbacks action associated to a specific FM state
		addAnyStateAction("onParameterSet");
	}

	public void init() throws rcms.fm.fw.EventHandlerException {
		functionManager = (HCALFunctionManager) getUserFunctionManager();
		logger.debug("[HCALSetParameterHandler] init() called: functionManager = " + functionManager );

		// check for the HCAL FM test mode
		{
			String useTestMode = GetUserXMLElement("TestMode");
			if (!useTestMode.equals("")) {
				TestMode = useTestMode;
				logger.warn("[HCAL base] TestMode: " + TestMode + " enabled - ignoring anything which would set the state machine to an error state!");					
			}
		}

	}

	/**
	 * call back executed when a parameterSet event is processed.
	 */
        public void onParameterSet(ParameterSet parameters) throws UserActionException {
		logger.info("[HCALSetParameterHandler] onParameterSet called: functionManager = " + functionManager );


	}
	
	/**
	  * @return Returns the embeded String of the User XML field.
	  * If not found an empty string is returned.
	  */	
	protected String GetUserXMLElement(String elementName) {

		// get the FM's resource configuration
		String myConfig = ((FunctionManagerResource)functionManager.getQualifiedGroup().getGroup().getThisResource()).getUserXml();

		logger.debug("[HCAL base] GetUserXMLElement: looking for element " + elementName + " in : " + myConfig );

		// get element value		
		String elementValue = getXmlRscConf(myConfig, elementName);

		return elementValue;
	}
	
	/**
	  * @return Returns the xml string of element "ElementName".
	  * If not found an empty string is returned.
	  */
	static private String getXmlRscConf(String xmlRscConf, String elementName) {

		// response string
		String response = "";

		// check the _xmlRscConf and _documentConf are filled
		if (xmlRscConf == null || xmlRscConf.equals("") ) return response;

		// check for a valid argument
		if (elementName == null || elementName.equals("") ) return response;

		int beginIndex = xmlRscConf.indexOf("<"+elementName+">") + elementName.length() + 2;
		int endIndex   = xmlRscConf.indexOf("</"+elementName+">");

		// check if element is available in UserXML and if so get the info
		if (beginIndex >= (elementName.length() + 2)) response = xmlRscConf.substring(beginIndex, endIndex);
		return response;
	}
	
	/**
	  * find out the state of a class of FMs with a given role and return true if this state is reached for all of them
	  */
	protected Boolean waitforFMswithRole(String Role, String toState) {

		Boolean OkToProceed = true;

		Iterator it = functionManager.containerFMChildren.getQualifiedResourceList().iterator();
		FunctionManager fmChild = null; 
		while (it.hasNext()) {
			fmChild = (FunctionManager) it.next();

			// check if FMs with the role given have the desired state
			if (fmChild.getRole().toString().equals(Role)) {
				if (!fmChild.refreshState().getStateString().equals(toState)) { 
					OkToProceed = false; 
				}
			}
		}
		return OkToProceed;
	}

}
