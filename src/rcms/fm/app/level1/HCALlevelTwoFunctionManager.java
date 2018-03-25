package rcms.fm.app.level1;

import rcms.fm.fw.parameter.FunctionManagerParameter;
import rcms.fm.fw.parameter.type.StringT;
import rcms.fm.fw.parameter.type.VectorT;
import rcms.fm.fw.parameter.type.MapT;
import rcms.fm.resource.QualifiedResource;
import rcms.fm.resource.qualifiedresource.XdaqApplication;
import rcms.statemachine.definition.StateMachineDefinitionException;
import rcms.util.logger.RCMSLogger;

import rcms.xdaqctl.XDAQParameter;
import net.hep.cms.xdaqctl.XDAQTimeoutException;
import net.hep.cms.xdaqctl.XDAQException;

/**
	* Function Machine to control a Level 2 HCAL Function Manager
	* 
	* @author Arno Heister
	*
	*/

public class HCALlevelTwoFunctionManager extends HCALFunctionManager {

	static RCMSLogger logger = new RCMSLogger(HCALlevelTwoFunctionManager.class);

	public HCALlevelTwoFunctionManager() {}

	public void init() throws StateMachineDefinitionException,rcms.fm.fw.EventHandlerException {

		super.init();

		// add event handler
		theEventHandler = new HCALlevelTwoEventHandler();
		addEventHandler(theEventHandler);
	}

	/**----------------------------------------------------------------------
	 * get supervisor error messages
	 */
  public String getSupervisorErrorMessage() {
    XDAQParameter pam = null;
		String supervisorError = "";
    String[] errAppNameString ;
    String[] errAppMsgString  ;
    String partition="";
		for (QualifiedResource qr : containerhcalSupervisor.getApplications() ){
			try {
				pam =((XdaqApplication)qr).getXDAQParameter();
				pam.select(new String[] {"Partition", "overallErrorMessage","StateTransitionMessage","ProblemApplicationNameInstanceVector","ProblemApplicationMessageVector"});
				pam.get();
				supervisorError = "(" + pam.getValue("Partition") + ") " + pam.getValue("overallErrorMessage");
				//supervisorError = "(" + pam.getValue("Partition") + ") " ;
        partition        = pam.getValue("Partition");
        errAppNameString = pam.getVector("ProblemApplicationNameInstanceVector");
        errAppMsgString  = pam.getVector("ProblemApplicationMessageVector");
        VectorT<StringT> errAppNameVector = new VectorT<StringT>();
        VectorT<StringT> errAppMsgVector  = new VectorT<StringT>();
        VectorT<MapT<StringT>> xDAQ_err_msg  = new VectorT<MapT<StringT>>();
        for (String s : errAppNameString){          errAppNameVector.add(new StringT(s));        }
        for (String s : errAppMsgString ){          errAppMsgVector.add(new StringT(s));        }
        logger.error("errAppNameString = " + errAppNameVector.toString());
        logger.error("errAppMsgString = " + errAppMsgVector.toString());

        if(errAppNameVector.size()==(errAppMsgVector.size())){
          for (StringT errAppName : errAppNameVector){
            // none is a place holder on xDAQ side for RCMS to safely look at the infospace at anytime.
            if(!errAppName.equals("none")){
              MapT<StringT> errMap = new MapT<StringT>();
              // Associate name of app to err_message of app by vector position
              //Name_err_map.put( "("+partition+") "+ errAppName, errAppMsgVector.get(  errAppNameVector.indexOf( errAppName ))) ;
              errMap.put( "timestamp", new StringT(getTimestampString()));
              errMap.put( "app", new StringT("("+partition+") "+ errAppName));
              errMap.put("message", errAppMsgVector.get(  errAppNameVector.indexOf( errAppName ))) ;
              
              xDAQ_err_msg.add(errMap);
            }
          }
        }
        else{
          logger.error("[" +FMname+"] Got different size of errApp and errMsg from supervisor. errApp = "+errAppNameVector.toString()+" errMsg = "+ errAppMsgVector.toString()); 
        }

        if(!pam.getValue("StateTransitionMessage").equalsIgnoreCase("ok")){
          supervisorError+= "; transitionMessage=" + pam.getValue("StateTransitionMessage");
        }
				getHCALparameterSet().put(new FunctionManagerParameter<StringT>("SUPERVISOR_ERROR", new StringT(supervisorError)));
				getHCALparameterSet().put(new FunctionManagerParameter<VectorT<MapT<StringT>>>("XDAQ_ERR_MSG", xDAQ_err_msg));
			}
			catch (XDAQTimeoutException e) {
				String errMessage = "[HCAL " + FMname + "] Error! XDAQTimeoutException: getSupervisorErrorMessage(): couldn't get xdaq parameters";
				goToError(errMessage,e);
			}
			catch (XDAQException e) {
				String errMessage = "[HCAL " + FMname + "] Error! XDAQException: getSupervisorErrorMessage(): couldn't get xdaq parameters";
				goToError(errMessage,e);
			}
		}
    return supervisorError;
	}

}
