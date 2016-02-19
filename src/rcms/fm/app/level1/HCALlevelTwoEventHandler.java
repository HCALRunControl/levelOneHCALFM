package rcms.fm.app.level1;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Vector;
import java.lang.Double;

import java.io.StringReader;
import java.io.StringWriter;
import java.io.IOException;

import net.hep.cms.xdaqctl.XDAQException;
import net.hep.cms.xdaqctl.XDAQTimeoutException;
import net.hep.cms.xdaqctl.XDAQMessageException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.w3c.dom.DOMException;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import rcms.errorFormat.CMS.CMSError;
import rcms.fm.fw.StateEnteredEvent;
import rcms.fm.fw.parameter.Parameter;
import rcms.fm.fw.parameter.CommandParameter;
import rcms.fm.fw.parameter.FunctionManagerParameter;
import rcms.fm.fw.parameter.ParameterSet;
import rcms.fm.fw.parameter.type.ParameterType;
import rcms.fm.fw.parameter.type.IntegerT;
import rcms.fm.fw.parameter.type.StringT;
import rcms.fm.fw.parameter.type.DoubleT;
import rcms.fm.fw.parameter.type.BooleanT;
import rcms.fm.fw.parameter.type.DateT;
import rcms.fm.fw.user.UserActionException;
import rcms.fm.fw.user.UserStateNotificationHandler;
import rcms.fm.resource.QualifiedGroup;
import rcms.fm.resource.QualifiedResource;
import rcms.fm.resource.QualifiedResourceContainerException;
import rcms.fm.resource.qualifiedresource.FunctionManager;
import rcms.fm.fw.user.UserFunctionManager;
import rcms.fm.resource.qualifiedresource.XdaqApplication;
import rcms.fm.resource.qualifiedresource.XdaqApplicationContainer;
import rcms.fm.resource.qualifiedresource.XdaqExecutive;
import rcms.fm.resource.qualifiedresource.XdaqExecutiveConfiguration;
import rcms.resourceservice.db.resource.fm.FunctionManagerResource;
import rcms.stateFormat.StateNotification;
import rcms.util.logger.RCMSLogger;
import rcms.util.logsession.LogSessionException;
import rcms.xdaqctl.XDAQParameter;
import rcms.xdaqctl.XDAQMessage;
import rcms.utilities.runinfo.RunNumberData;
import rcms.statemachine.definition.Input;

import rcms.utilities.fm.task.TaskSequence;
import rcms.utilities.fm.task.SimpleTask;
import rcms.utilities.fm.task.Task;

/**
 * Event Handler class for Level 2 HCAL Function Manager
 *
 * @maintainer John Hakala
 *
 */

public class HCALlevelTwoEventHandler extends HCALEventHandler {

  static RCMSLogger logger = new RCMSLogger(HCALlevelTwoEventHandler.class);
  public HCALxmlHandler xmlHandler = null;

  public HCALlevelTwoEventHandler() throws rcms.fm.fw.EventHandlerException {}

  public void init() throws rcms.fm.fw.EventHandlerException {
    functionManager = (HCALFunctionManager) getUserFunctionManager();
    qualifiedGroup  = functionManager.getQualifiedGroup();
    xmlHandler = new HCALxmlHandler(this.functionManager);
    super.init();
      

    logger.debug("[HCAL LVL2] init() called: functionManager = " + functionManager );
  }

  public void initAction(Object obj) throws UserActionException {
    if (obj instanceof StateNotification) {

      // triggered by State Notification from child resource
      computeNewState((StateNotification) obj);
      return;

    }
    else if (obj instanceof StateEnteredEvent) {
      System.out.println("[HCAL LVL2 " + functionManager.FMname + "] Executing initAction");
      logger.debug("[HCAL LVL2 " + functionManager.FMname + "] Executing initAction");

      // reset the non-async error state handling
      functionManager.ErrorState = false;

      // set actions
      functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT("calculating state")));
      functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("Initializing ...")));

      // get the parameters of the command
      ParameterSet<CommandParameter> parameterSet = getUserFunctionManager().getLastInput().getParameterSet();
      
      if (parameterSet.get(HCALParameters.EVM_TRIG_FM) != null) {
        String evmTrigFM = ((StringT)parameterSet.get(HCALParameters.EVM_TRIG_FM).getValue()).getString();
        functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.EVM_TRIG_FM,new StringT(evmTrigFM)));
      }
      if ( ((StringT)parameterSet.get(HCALParameters.EVM_TRIG_FM).getValue()).getString().equals(functionManager.FMname) ) {
        functionManager.FMrole="EvmTrig";
      }
      List<QualifiedResource> xdaqApplicationList = qualifiedGroup.seekQualifiedResourcesOfType(new XdaqApplication());
      if (parameterSet.get(HCALParameters.MASKED_RESOURCES) != null && !((StringT)parameterSet.get(HCALParameters.MASKED_RESOURCES).getValue()).getString().isEmpty()) {
        String MaskedResources = ((StringT)parameterSet.get(HCALParameters.MASKED_RESOURCES).getValue()).getString();
        functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.MASKED_RESOURCES,new StringT(MaskedResources)));
        String[] MaskedResourceArray = MaskedResources.split(";");
        List<QualifiedResource> level2list = qualifiedGroup.seekQualifiedResourcesOfType(new FunctionManager());
        for (String MaskedApplication: MaskedResourceArray) {
          String MaskedAppWcolonsNoCommas = MaskedApplication.replace("," , ":");
          //logger.info("[JohnLog2] " + functionManager.FMname + ": " + functionManager.FMname + ": Starting to mask application " + MaskedApplication);
          logger.info("[HCAL LVL2 " + functionManager.FMname + "]: " + functionManager.FMname + ": Starting to mask application " + MaskedApplication);
          for (QualifiedResource qr : xdaqApplicationList) {
            if (qr.getName().equals(MaskedApplication) || qr.getName().equals(MaskedAppWcolonsNoCommas)) {
              //logger.info("[JohnLog] " + functionManager.FMname + ": found the matching application in the qr list: " + qr.getName());
              logger.info("[HCAL LVL2 " + functionManager.FMname + "]: found the matching application in the qr list: " + qr.getName());
              //logger.info("[JohnLog] " + functionManager.FMname + ": Going to call setActive(false) on "+qr.getName());
              logger.info("[HCAL LVL2 " + functionManager.FMname + "]: Going to call setActive(false) on "+qr.getName());
              qr.setActive(false);
            }
          }
        }
        //logger.info("[JohnLog] " + functionManager.FMname + ": This FM has role: " + functionManager.FMrole);
        logger.info("[HCAL LVL2 " + functionManager.FMname + "]: This FM has role: " + functionManager.FMrole);
        List<QualifiedResource> xdaqExecList = qualifiedGroup.seekQualifiedResourcesOfType(new XdaqExecutive());
        // loop over the executives and strip the connections
     
        //logger.info("[JohnLog3] " + functionManager.FMname + ": about to set the xml for the xdaq executives.");
        logger.info("[HCAL LVL2 " + functionManager.FMname + "]: about to set the xml for the xdaq executives.");
        for( QualifiedResource qr : xdaqExecList) {
          XdaqExecutive exec = (XdaqExecutive)qr;
          //logger.info("[JohnLog3] " + functionManager.FMname + " Found qualified resource: " + qr.getName());
          logger.info("[HCAL LVL2 " + functionManager.FMname + "]: Found qualified resource: " + qr.getName());
          XdaqExecutiveConfiguration config =  exec.getXdaqExecutiveConfiguration();
          String oldExecXML = config.getXml();
          try {
            String newExecXML = xmlHandler.stripExecXML(oldExecXML, getUserFunctionManager().getParameterSet());
            config.setXml(newExecXML);
            //logger.info("[JohnLog3] " + functionManager.FMname + ": Just set the xml for executive " + qr.getName());
            logger.info("[HCAL LVL2 " + functionManager.FMname + "]: Just set the xml for executive " + qr.getName());
          }
          catch (UserActionException e) {
            String errMessage = e.getMessage();
            //logger.info("[JohnLog2] " + functionManager.FMname + " got an error while trying to strip the ExecXML: " + errMessage);
            logger.info("[HCAL LVL2 " + functionManager.FMname + "]: got an error while trying to strip the ExecXML: " + errMessage);
            functionManager.sendCMSError(errMessage);
            functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT("Error")));
            functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT(errMessage)));
            if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; return;}
          }
          XdaqExecutiveConfiguration configRetrieved =  exec.getXdaqExecutiveConfiguration();
          System.out.println("[HCAL LVL2 System] " +qr.getName() + " has executive xml: " +  configRetrieved.getXml());
        }
      }
      else {
        String warnMessage = "[HCAL LVL2 " + functionManager.FMname + "] Did not receive any applications requested to be masekd.";
        logger.warn(warnMessage);
      }

      // initialize all XDAQ executives
      initXDAQ();
      parameterSet = getUserFunctionManager().getLastInput().getParameterSet();
      //for (QualifiedResource qr : xdaqApplicationList) { 
      //  if (qr.getName().contains("TriggerAdapter") || qr.getName().contains("FanoutTTCciTA")) {
      //    if (qr.isActive())functionManager.FMrole="EvmTrig";
      //  }
      //}
      String ruInstance = "";
      if (parameterSet.get(HCALParameters.RU_INSTANCE) != null) {
        ruInstance = ((StringT)parameterSet.get(HCALParameters.RU_INSTANCE).getValue()).getString();
        functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.RU_INSTANCE,new StringT(ruInstance)));
      }
      String lpmSupervisor = "";
      if (parameterSet.get(HCALParameters.LPM_SUPERVISOR) != null) {
        lpmSupervisor = ((StringT)parameterSet.get(HCALParameters.LPM_SUPERVISOR).getValue()).getString();
        functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.LPM_SUPERVISOR,new StringT(lpmSupervisor)));
      }
      for (QualifiedResource qr : xdaqApplicationList) {
        if (qr.isActive()) {
          try {
            XDAQParameter pam = null;
            pam = ((XdaqApplication)qr).getXDAQParameter();
            for (String pamName : pam.getNames()){
              if (pamName.equals("RUinstance")) {
                pam.select(new String[] {"RUinstance"});
                pam.setValue("RUinstance", ruInstance.split("_")[1]);
                pam.send();
                //logger.info("[JohnLog4] " + functionManager.FMname + ": Just set the RUinstance for " + qr.getName() + " to " +  ruInstance.split("_")[1]);
                logger.info("[HCAL LVL2 " + functionManager.FMname + "]: Just set the RUinstance for " + qr.getName() + " to " +  ruInstance.split("_")[1]);
              }
              if (pamName.equals("BUInstance")) {
                pam.select(new String[] {"BUInstance"});
                pam.setValue("BUInstance", ruInstance.split("_")[1]);
                pam.send();
                //logger.info("[JohnLog4] " + functionManager.FMname + ": Just set the BUInstance for " + qr.getName() + " to " +  ruInstance.split("_")[1]);
                logger.info("[HCAL LVL2 " + functionManager.FMname + "]: Just set the BUInstance for " + qr.getName() + " to " +  ruInstance.split("_")[1]);
              }
              if (pamName.equals("EVMinstance")) {
                pam.select(new String[] {"EVMinstance"});
                pam.setValue("EVMinstance", ruInstance.split("_")[1]);
                pam.send();
                //logger.info("[JohnLog4] " + functionManager.FMname + ": Just set the EVMinstance for " + qr.getName() + " to " +  ruInstance.split("_")[1]);
                logger.info("[HCAL LVL2 " + functionManager.FMname + "]: Just set the EVMinstance for " + qr.getName() + " to " +  ruInstance.split("_")[1]);
              }
              if (pamName.equals("HandleLPM")) {
                pam.select(new String[] {"HandleLPM"});
                pam.setValue("HandleLPM", "true");
                pam.send();
                //logger.info("[JohnLog4] " + functionManager.FMname + ": Just set the EVMinstance for " + qr.getName() + " to " +  ruInstance.split("_")[1]);
                logger.info("[HCAL LVL2 " + functionManager.FMname + "]: Just set the EVMinstance for " + qr.getName() + " to " +  ruInstance.split("_")[1]);
              }
            }
          }
          catch (XDAQTimeoutException e) {
            String errMessage = "[HCAL " + functionManager.FMname + "] Error! XDAQTimeoutException while querying the XDAQParameter names for " + qr.getName() + ". Message: " + e.getMessage();
            logger.error(errMessage,e);
            functionManager.sendCMSError(errMessage);
            functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT("Error")));
            functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("oops - technical difficulties ...")));
            if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; return;}
          }
          catch (XDAQException e) {
            String errMessage = "[HCAL " + functionManager.FMname + "] Error! XDAQException while querying the XDAQParameter names for " + qr.getName() + ". Message: " + e.getMessage();
            logger.error(errMessage,e);
            functionManager.sendCMSError(errMessage);
            functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT("Error")));
            functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("oops - technical difficulties ...")));
            if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; return;}
          }
        }
      }
      // start the monitor thread
      System.out.println("[HCAL LVL2 " + functionManager.FMname + "] Starting Monitor thread ...");
      logger.debug("[HCAL LVL2 " + functionManager.FMname + "] Starting Monitor thread ...");
      LevelOneMonitorThread thread1 = new LevelOneMonitorThread();
      thread1.start();

      // start the HCALSupervisor watchdog thread
      System.out.println("[HCAL LVL2 " + functionManager.FMname + "] Starting HCAL supervisor watchdog thread ...");
      logger.debug("[HCAL LVL2 " + functionManager.FMname + "] Starting HCAL supervisor watchdog thread ...");
      if (!functionManager.FMrole.equals("Level2_TCDSLPM")) {
        HCALSupervisorWatchThread thread2 = new HCALSupervisorWatchThread();
        thread2.start();
      }

      // start the TriggerAdapter watchdog thread
      System.out.println("[HCAL LVL2 " + functionManager.FMname + "] Starting TriggerAdapter watchdog thread ...");
      logger.debug("[HCAL LVL2 " + functionManager.FMname + "] StartingTriggerAdapter watchdog thread ...");
      TriggerAdapterWatchThread thread3 = new TriggerAdapterWatchThread();
      thread3.start();


      // check parameter set
      if (parameterSet.size()==0 || parameterSet.get(HCALParameters.SID) == null )  {

        RunType = "local";

        // request a session ID
        getSessionId();

        GlobalConfKey = "not used";

        // set the run type in the function manager parameters
        functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.HCAL_RUN_TYPE,new StringT(RunType)));
        functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.GLOBAL_CONF_KEY,new StringT(GlobalConfKey)));
      }
      else {

        RunType = "global";

        // get the Sid from the init command
        if (parameterSet.get(HCALParameters.SID) != null) {
          Sid = ((IntegerT)parameterSet.get(HCALParameters.SID).getValue()).getInteger();
          functionManager.getParameterSet().put(new FunctionManagerParameter<IntegerT>(HCALParameters.SID,new IntegerT(Sid)));
          functionManager.getParameterSet().put(new FunctionManagerParameter<IntegerT>(HCALParameters.INITIALIZED_WITH_SID,new IntegerT(Sid)));
        }
        else {
          String warnMessage = "[HCAL LVL2 " + functionManager.FMname + "] Did not receive a SID ...";
          logger.warn(warnMessage);
        }

        // get the GlobalConfKey from the init command
        if (parameterSet.get(HCALParameters.GLOBAL_CONF_KEY) != null) {
          GlobalConfKey = ((StringT)parameterSet.get(HCALParameters.GLOBAL_CONF_KEY).getValue()).getString();
          functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.GLOBAL_CONF_KEY,new StringT(GlobalConfKey)));
          functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.INITIALIZED_WITH_GLOBAL_CONF_KEY,new StringT(GlobalConfKey)));
        }
        else {
          String warnMessage = "[HCAL LVL2 " + functionManager.FMname + "] Did not receive a GlobalConfKey ...";
          logger.warn(warnMessage);
        }
      }

      if (parameterSet.get(HCALParameters.RUN_CONFIG_SELECTED) != null) {
        String RunConfigSelected = ((StringT)parameterSet.get(HCALParameters.RUN_CONFIG_SELECTED).getValue()).getString();
        functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.RUN_CONFIG_SELECTED,new StringT(RunConfigSelected)));
      }
      else {
        String warnMessage = "[HCAL LVL2 " + functionManager.FMname + "] Did not receive the user-selected CfgSnippet key.";
        logger.warn(warnMessage);
      }
      // give the RunType to the controlling FM
      functionManager.RunType = RunType;
      logger.info("[HCAL LVL2 " + functionManager.FMname + "] initAction: We are in " + RunType + " mode ...");

      
      //sendMaskedApplications();
      // ask the HCAL supervisor for the TriggerAdapter name
      //
      
      if (functionManager.FMrole.equals("EvmTrig")) {
        //logger.info("JohnLog3] [HCAL LVL2 " + functionManager.FMname + "] Going to ask the HCAL supervisor fo the TriggerAdapter name, now...");
        logger.info("[HCAL LVL2 " + functionManager.FMname + "] Going to ask the HCAL supervisor fo the TriggerAdapter name, now...");
        getTriggerAdapter();
        logger.debug("[HCAL LVL2 " + functionManager.FMname + "] OK, now I should have at least one TriggerAdapter to talk to ...");
      }

      // go to HALT
      if (!functionManager.ErrorState) {
        functionManager.fireEvent( HCALInputs.SETHALT );
      }
      // set actions
      functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT(functionManager.getState().getStateString())));
      functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("initAction executed ...")));

      // publish the initialization time for this FM to the paramterSet
      functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.HCAL_TIME_OF_FM_START, new StringT(functionManager.utcFMtimeofstart)));

      logger.info("[HCAL LVL2 " + functionManager.FMname + "] initAction executed ...");
    }
  }

  public void resetAction(Object obj) throws UserActionException {
    if (obj instanceof StateNotification) {

      // triggered by State Notification from child resource
      computeNewState((StateNotification) obj);
      return;

    }
    else if (obj instanceof StateEnteredEvent) {
      System.out.println("[HCAL LVL2 " + functionManager.FMname + "] Executing resetAction");
      logger.debug("[HCAL LVL2 " + functionManager.FMname + "] Executing resetAction");

      publishRunInfoSummary();
      publishRunInfoSummaryfromXDAQ(); 
      functionManager.HCALRunInfo = null; // make RunInfo ready for the next round of run info to store


      // reset the non-async error state handling
      functionManager.ErrorState = false;
      functionManager.FMWasInPausedState = false;
      // set actions
      functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT("calculating state")));
      functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("Resetting")));
      functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.SUPERVISOR_ERROR,new StringT("")));

      // kill all XDAQ executives
      destroyXDAQ();

      // init all XDAQ executives
      initXDAQ();

      // go to Halted
      if (!functionManager.ErrorState) {
        functionManager.fireEvent( HCALInputs.SETHALT );
      }

      // set actions
      functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT(functionManager.getState().getStateString())));
      functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("resetAction executed ...")));

      logger.info("[HCAL LVL2 " + functionManager.FMname + "] resetAction executed ...");
    }
  }

  public void recoverAction(Object obj) throws UserActionException {
    Boolean UseResetForRecover = ((BooleanT)functionManager.getParameterSet().get(HCALParameters.USE_RESET_FOR_RECOVER).getValue()).getBoolean();
    if (UseResetForRecover) {
      resetAction(obj); return;
    }
    if (obj instanceof StateNotification) {

      // triggered by State Notification from child resource
      computeNewState((StateNotification) obj);
      return;
    }
    else if (obj instanceof StateEnteredEvent) {
      System.out.println("[HCAL LVL2 " + functionManager.FMname + "] Executing recoverAction");
      logger.info("[HCAL LVL2 " + functionManager.FMname + "] Executing recoverAction");

      // reset the non-async error state handling
      functionManager.ErrorState = false;

      // set actions
      functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT("calculating state")));
      functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("recovering")));

      if (!functionManager.containerhcalSupervisor.isEmpty()) {
        {
          String debugMessage = "[HCAL LVL2 " + functionManager.FMname + "] HCAL supervisor for recovering found- good!";
          logger.debug(debugMessage);
        }

        try {
          functionManager.containerhcalSupervisor.execute(HCALInputs.RESET);
        }
        catch (QualifiedResourceContainerException e) {
          String errMessage = "[HCAL LVL2 " + functionManager.FMname + "] Error! QualifiedResourceContainerException: recovering failed ...";
          logger.error(errMessage,e);
          functionManager.sendCMSError(errMessage);
          functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT("Error")));
          functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("oops - technical difficulties ...")));
          if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; return;}
        }

      }
      else if (!functionManager.FMrole.equals("Level2_TCDSLPM")) {
        String errMessage = "[HCAL LVL2 " + functionManager.FMname + "] Error! No HCAL supervisor found: recoverAction()";
        logger.error(errMessage);
        functionManager.sendCMSError(errMessage);
        functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT("Error")));
        functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT(errMessage)));
        if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; return;}
      }

      // leave intermediate state directly only when not talking to asynchronous applications
      if ( (!functionManager.asyncSOAP) && (!functionManager.ErrorState) ) {

        functionManager.fireEvent( HCALInputs.SETHALT );
      }

      // set actions
      functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT(functionManager.getState().getStateString())));
      functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("recoverAction executed ...")));

      logger.info("[HCAL LVL2 " + functionManager.FMname + "] recoverAction executed ...");
    }
  }

  public void configureAction(Object obj) throws UserActionException {
    if (obj instanceof StateNotification) {

      // triggered by State Notification from child resource
      computeNewState((StateNotification) obj);
      return;

    }
    else if (obj instanceof StateEnteredEvent) {
      System.out.println("[HCAL LVL2 " + functionManager.FMname + "] Executing configureAction");
      logger.info("[HCAL LVL2 " + functionManager.FMname + "] Executing configureAction");

      // reset the non-async error state handling
      functionManager.ErrorState = false;

      // set actions
      functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT("calculating state")));
      functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("configuring")));

      String LVL1CfgScript            = "not set";
      String LVL1TTCciControlSequence = "not set";
      String LVL1LTCControlSequence   = "not set";
      String LVL1TCDSControlSequence   = "not set";
      String LVL1LPMControlSequence   = "not set";
      String LVL1PIControlSequence = "not set";

      // get the parameters of the command
      ParameterSet<CommandParameter> parameterSet = getUserFunctionManager().getLastInput().getParameterSet();

      // check parameter set, if it is not set see if we are in local mode
      if (parameterSet.size()==0)  {
        RunType = "local";
        functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.HCAL_RUN_TYPE,new StringT(RunType)));
      }
      else {
        // get the run type from the configure command
        if (parameterSet.get(HCALParameters.HCAL_RUN_TYPE) != null) {
          RunType = ((StringT)parameterSet.get(HCALParameters.HCAL_RUN_TYPE).getValue()).getString();
          functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.HCAL_RUN_TYPE,new StringT(RunType)));
        }
        else {
          String warnMessage = "[HCAL LVL2 " + functionManager.FMname + "] Warning! Did not receive a run type ...\nThis is OK for e.g. CASTOR LVL2 FMs directly connected to the CDAQ LVL0 FM";
          logger.warn(warnMessage);
        }

        // get the run key from the configure command
        if (parameterSet.get(HCALParameters.RUN_KEY) != null) {
          RunKey = ((StringT)parameterSet.get(HCALParameters.RUN_KEY).getValue()).getString();
          functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.RUN_KEY,new StringT(RunKey)));
          functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.CONFIGURED_WITH_RUN_KEY,new StringT(RunKey)));
        }
        else {
          String warnMessage = "[HCAL LVL2 " + functionManager.FMname + "] Did not receive a run key.\nThis is probably OK for normal HCAL LVL2 operations ...";
          logger.warn(warnMessage);
        }

        // get the tpg key from the configure command
        if (parameterSet.get(HCALParameters.TPG_KEY) != null) {
          TpgKey = ((StringT)parameterSet.get(HCALParameters.TPG_KEY).getValue()).getString();
          functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.CONFIGURED_WITH_TPG_KEY,new StringT(TpgKey)));
          String warnMessage = "[HCAL LVL2 " + functionManager.FMname + "] Received a L1 TPG key: " + TpgKey;
          logger.warn(warnMessage);
        }
        else {
          String warnMessage = "[HCAL LVL2 " + functionManager.FMname + "] Did not receive a L1 TPG key.\nThis is only OK for HCAL local run operations ...";
          logger.warn(warnMessage);
        }

        // get the info from the LVL1 if special actions due to a central CMS clock source change are indicated
        ClockChanged = false;
        if (parameterSet.get(HCALParameters.CLOCK_CHANGED) != null) {
          ClockChanged = ((BooleanT)parameterSet.get(HCALParameters.CLOCK_CHANGED).getValue()).getBoolean();
          functionManager.getParameterSet().put(new FunctionManagerParameter<BooleanT>(HCALParameters.CLOCK_CHANGED,new BooleanT(ClockChanged)));
          if (ClockChanged) {
            logger.warn("[HCAL LVL2 " + functionManager.FMname + "] Did receive a request to perform special actions due to central CMS clock source change during the configureAction().\nThe ClockChange is: " + ClockChanged);
          }
          else {
            logger.debug("[HCAL LVL2 " + functionManager.FMname + "] Did not receive a request to perform special actions due to central CMS clock source change during the configureAction().\nThe ClockChange is: " + ClockChanged);
          }

        }
        else {
          logger.info("[HCAL LVL2 " + functionManager.FMname + "] Did not receive any request to perform special actions due to a central CMS clock source change during the configureAction().\nThis is (probably) OK for HCAL local runs.\nFor CASTOR in global runs this might be a problem ...");
        }

        UseResetForRecover = true;
        if (parameterSet.get(HCALParameters.USE_RESET_FOR_RECOVER) != null) {
          UseResetForRecover = ((BooleanT)parameterSet.get(HCALParameters.USE_RESET_FOR_RECOVER).getValue()).getBoolean();
          functionManager.getParameterSet().put(new FunctionManagerParameter<BooleanT>(HCALParameters.USE_RESET_FOR_RECOVER, new BooleanT(UseResetForRecover)));
        }

        UsePrimaryTCDS = true;
        if (parameterSet.get(HCALParameters.USE_PRIMARY_TCDS) != null) {
          UsePrimaryTCDS=((BooleanT)parameterSet.get(HCALParameters.USE_PRIMARY_TCDS).getValue()).getBoolean();
          functionManager.getParameterSet().put(new FunctionManagerParameter<BooleanT>(HCALParameters.USE_PRIMARY_TCDS, new BooleanT(UsePrimaryTCDS)));
        }

        // get the supervisor error from the lvl1 
        SupervisorError = "";
        if (parameterSet.get(HCALParameters.SUPERVISOR_ERROR) != null) {
          SupervisorError = ((StringT)parameterSet.get(HCALParameters.SUPERVISOR_ERROR).getValue()).getString();
          functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.SUPERVISOR_ERROR, new StringT(SupervisorError)));
        }

        // get the FED list from the configure command
        if (parameterSet.get(HCALParameters.FED_ENABLE_MASK) != null) {
          FedEnableMask = ((StringT)parameterSet.get(HCALParameters.FED_ENABLE_MASK).getValue()).getString();
          functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.FED_ENABLE_MASK,new StringT(FedEnableMask)));
          functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.CONFIGURED_WITH_FED_ENABLE_MASK,new StringT(FedEnableMask)));
          functionManager.HCALFedList = getEnabledHCALFeds(FedEnableMask);

          logger.info("[HCAL LVL2 " + functionManager.FMname + "] ... did receive a FED list during the configureAction().");
        }
        else {
          logger.warn("[HCAL LVL2 " + functionManager.FMname + "] Did not receive a FED list during the configureAction() - this is bad!");
        }

        // get the HCAL CfgScript from LVL1 if the LVL1 has sent something
        if (parameterSet.get(HCALParameters.HCAL_CFGSCRIPT) != null) {
          LVL1CfgScript = ((StringT)parameterSet.get(HCALParameters.HCAL_CFGSCRIPT).getValue()).getString();
        }
        else {
          logger.warn("[HCAL LVL2 " + functionManager.FMname + "] Warning! Did not receive a LVL1 CfgScript.\nThis is OK if each LVL2 (i.e.also this one) has such a CfgScript defined itself.");
        }

        // get the HCAL TTCciControl from LVL1 if the LVL1 has sent something
        if (parameterSet.get(HCALParameters.HCAL_TTCCICONTROL) != null) {
          LVL1TTCciControlSequence = ((StringT)parameterSet.get(HCALParameters.HCAL_TTCCICONTROL).getValue()).getString();
        }
        else {
          logger.warn("[HCAL LVL2 " + functionManager.FMname + "] Warning! Did not receive a LVL1 TTCci control sequence.\nThis is OK if either each LVL2 (i.e.also this one) has such a sequence defined itself or a TTCci is not used in this config.");
        }

        // get the HCAL LTCControl from LVL1 if the LVL1 has sent something
        if (parameterSet.get(HCALParameters.HCAL_LTCCONTROL) != null) {
          LVL1LTCControlSequence = ((StringT)parameterSet.get(HCALParameters.HCAL_LTCCONTROL).getValue()).getString();
        }
        else {
          logger.warn("[HCAL LVL2 " + functionManager.FMname + "] Warning! Did not receive a LVL1 LTC control sequence.\nThis is OK if either each LVL2 (i.e.also this one) has such a sequence defined itself or a LTC is not used in this config.");
        }
        // get the HCAL TCDSControl from LVL1 if the LVL1 has sent something
        if (parameterSet.get(HCALParameters.HCAL_TCDSCONTROL) != null) {
          LVL1TCDSControlSequence = ((StringT)parameterSet.get(HCALParameters.HCAL_TCDSCONTROL).getValue()).getString();
        }
        else {
          logger.warn("[HCAL LVL2 " + functionManager.FMname + "] Warning! Did not receive a LVL1 TCDS control sequence.\nThis is OK if either each LVL2 (i.e.also this one) has such a sequence defined itself or a TCDS is not used in this config.");
        }
        // get the HCAL LPMControl from LVL1 if the LVL1 has sent something
        if (parameterSet.get(HCALParameters.HCAL_LPMCONTROL) != null) {
          LVL1LPMControlSequence = ((StringT)parameterSet.get(HCALParameters.HCAL_LPMCONTROL).getValue()).getString();
        }
        else {
          logger.warn("[HCAL LVL2 " + functionManager.FMname + "] Warning! Did not receive a LVL1 LPM control sequence.\nThis is OK if either each LVL2 (i.e.also this one) has such a sequence defined itself or a LPM is not used in this config.");
        }
        // get the HCAL PIControl from LVL1 if the LVL1 has sent something
        if (parameterSet.get(HCALParameters.HCAL_PICONTROL) != null) {
          LVL1PIControlSequence = ((StringT)parameterSet.get(HCALParameters.HCAL_PICONTROL).getValue()).getString();
        }
        else {
          logger.warn("[HCAL LVL2 " + functionManager.FMname + "] Warning! Did not receive a LVL1 PI control sequence.\nThis is OK if either each LVL2 (i.e.also this one) has such a sequence defined itself or a PI is not used in this config.");
        }
      }

      if (LVL1CfgScript.equals("not set")) {
        logger.info("[HCAL LVL2 " + functionManager.FMname + "] The LVL1CfgScript is not set.\nThis is OK if this LVL2 has such a CfgScript defined itself.");
      }
      else {
        FullCfgScript = LVL1CfgScript;
        logger.info("[HCAL LVL2 " + functionManager.FMname + "] LVL1CfgScript was received.\nHere it is:\n" + FullCfgScript);
      }

      if (LVL1TTCciControlSequence.equals("not set")) {
        logger.info("[HCAL LVL2 " + functionManager.FMname + "] The LVL1 TTCci control sequence is not set.\nThis is OK if either each LVL2 (i.e.also this one) has such a sequence defined itself or a TTCci is not used in this config.");
      }
      else {
        FullTTCciControlSequence = LVL1TTCciControlSequence;
        logger.info("[HCAL LVL2 " + functionManager.FMname + "] LVL1 TTCci control sequence was received.\nHere it is:\n" + FullTTCciControlSequence);
      }

      if (LVL1LTCControlSequence.equals("not set")) {
        logger.info("[HCAL LVL2 " + functionManager.FMname + "] The LVL1 LTC control sequence is not set.\nThis is OK if either each LVL2 (i.e.also this one) has such a sequence defined itself or a LTC is not used in this config.");
      }
      else {
        FullLTCControlSequence = LVL1LTCControlSequence;
        logger.info("[HCAL LVL2 " + functionManager.FMname + "] LVL1 LTC control sequence was received.\nHere it is:\n" + FullLTCControlSequence);
      }

      if (LVL1TCDSControlSequence.equals("not set")) {
        logger.info("[HCAL LVL2 " + functionManager.FMname + "] The LVL1 TCDS control sequence is not set.\nThis is OK if either each LVL2 (i.e.also this one) has such a sequence defined itself or a TCDS is not used in this config.");
      }
      else {
        FullTCDSControlSequence = LVL1TCDSControlSequence;
        logger.info("[HCAL LVL2 " + functionManager.FMname + "] LVL1 TCDS control sequence was received.\nHere it is:\n" + FullTCDSControlSequence);
      }

      if (LVL1LPMControlSequence.equals("not set")) {
        logger.info("[HCAL LVL2 " + functionManager.FMname + "] The LVL1 LPM control sequence is not set.\nThis is OK if either each LVL2 (i.e.also this one) has such a sequence defined itself or a LPM is not used in this config.");
      }
      else {
        FullLPMControlSequence = LVL1LPMControlSequence;
        logger.info("[HCAL LVL2 " + functionManager.FMname + "] LVL1 LPM control sequence was received.\nHere it is:\n" + FullLPMControlSequence);
      }

      if (LVL1PIControlSequence.equals("not set")) {
        logger.info("[HCAL LVL2 " + functionManager.FMname + "] The LVL1 PI control sequence is not set.\nThis is OK if either each LVL2 (i.e.also this one) has such a sequence defined itself or a PI is not used in this config.");
      }
      else {
        FullPIControlSequence = LVL1PIControlSequence;
        logger.info("[HCAL LVL2 " + functionManager.FMname + "] LVL1 PI control sequence was received.\nHere it is:\n" + FullPIControlSequence);
      }

      // give the RunType to the controlling FM
      functionManager.RunType = RunType;
      logger.info("[HCAL LVL2 " + functionManager.FMname + "] configureAction: We are in " + RunType + " mode ...");

      // switch parsing, etc. of the zero supression HCAL CFG snippet on or off, special zero suppression handling ...
      if (RunKey.equals("noZS") || RunKey.equals("VdM-noZS")) {
        logger.warn("[HCAL LVL2 " + functionManager.FMname + "] The zero supression is switched off ...");
        functionManager.useZS        = false;
        functionManager.useSpecialZS = false;
      }
      else if (RunKey.equals("test-ZS") || RunKey.equals("VdM-test-ZS")) {
        logger.warn("[HCAL LVL2 " + functionManager.FMname + "] The special zero suppression is switched on i.e. not blocked by this FM ...");
        functionManager.useZS        = false;
        functionManager.useSpecialZS = true;
      }
      else if (RunKey.equals("ZS") || RunKey.equals("VdM-ZS")) {
        logger.warn("[HCAL LVL2 " + functionManager.FMname + "] The zero suppression is switched on i.e. not blocked by this FM ...");
        functionManager.useZS        = true;
        functionManager.useSpecialZS = false;

      }
      else {
        if (!RunKey.equals("")) {
          String errMessage = "[HCAL LVL2 " + functionManager.FMname + "] Do not understand how to handle this RUN_KEY: " + RunKey + " - please check the RS3 config in use!\nPerhaps the wrong key was given by the CDAQ shifter!?";
          logger.error(errMessage);
          functionManager.sendCMSError(errMessage);
          functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT("Error")));
          functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("oops - technical difficulties ...")));
          if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; return; }
        }
      }

      // check the RUN_KEY for VdM snippets, etc. request
      if (RunKey.equals("VdM-noZS") || RunKey.equals("VdM-test-ZS") || RunKey.equals("VdM-ZS")) {
        logger.warn("[HCAL LVL2 " + functionManager.FMname + "] Special VdM scan snippets, etc. were enabled by the RUN_KEY for this FM.\nThe RUN_KEY given is: " + RunKey);
        functionManager.useVdMSnippet = true;
      }
      else {
        logger.debug("[HCAL LVL2 " + functionManager.FMname + "] No special VdM scan snippets, etc. enabled for this FM.\nThe RUN_KEY given is: " + RunKey);
      }

      // compile CfgScript incorporating the local definitions found in the UserXML
      getCfgScript();

      if (TpgKey!=null && TpgKey!="NULL") {

        FullCfgScript += "\n### BEGIN TPG key add from HCAL FM named: " + functionManager.FMname + "\n";
        FullCfgScript += "HTR { ";
        FullCfgScript += "  HcalTriggerKey = \"" + TpgKey + "\" ";
        FullCfgScript += " } ";
        FullCfgScript += "uHTR { ";
        FullCfgScript += "  HcalTriggerKey = \"" + TpgKey + "\" ";
        FullCfgScript += " } ";
        FullCfgScript += "\n### END TPG key add from HCAL FM named: " + functionManager.FMname + "\n";

        logger.warn("[HCAL LVL2 " + functionManager.FMname + "] added the received TPG_KEY: " + TpgKey + " as HTR snippet to the full CfgScript ...");
        logger.debug("[HCAL LVL2 " + functionManager.FMname + "] FullCfgScript with added received TPG_KEY: " + TpgKey + " as HTR snippet.\nHere it is:\n" + FullCfgScript);

      }
      else {
        logger.warn("[HCAL LVL2 " + functionManager.FMname + "] Warning! Did not receive any TPG_KEY.\nPerhaps this is OK for local runs ... ");

        if (!RunType.equals("local")) {
          logger.error("[HCAL LVL2 " + functionManager.FMname + "] Error! For global runs we should have received a TPG_KEY.\nPlease check if HCAL is in the trigger.\n If HCAL is in the trigger and you see this message please call an expert - this is bad!!");
        }
      }

      // compile TTCci control sequence incorporating the local definitions found in the UserXML
      getTTCciControl();

      // compile LTC control sequence incorporating the local definitions found in the UserXML
      getLTCControl();

      // compile TCDS control sequence incorporating the local definitions found in the UserXML
      getTCDSControl();

      // compile LPM control sequence incorporating the local definitions found in the UserXML
      getLPMControl();

      // compile LPM control sequence incorporating the local definitions found in the UserXML
      getPIControl();


      // get the FedEnableMask found in the UserXML
      if (functionManager.getParameterSet().get(HCALParameters.FED_ENABLE_MASK) != null && ((StringT)functionManager.getParameterSet().get(HCALParameters.FED_ENABLE_MASK).getValue()).getString() == "") {
        getFedEnableMask();
        FedEnableMask = ((StringT)functionManager.getParameterSet().get(HCALParameters.FED_ENABLE_MASK).getValue()).getString();
        logger.info("[HCAL LVL2 " + functionManager.FMname + "] The FED_ENABLE_MASK to be sent to the hcalSupervisor is: " + FedEnableMask);
      }
      else {
        logger.info("[HCAL LVL2 " + functionManager.FMname + "] The FED_ENABLE_MASK was not retrieved from the userXML. This is OK if the level0 supplies the FedEnableMask.");
      }

      // configure PeerTransportATCPs
      if (!functionManager.containerPeerTransportATCP.isEmpty()) {
        String peerTransportATCPstateName = "";
        for (QualifiedResource qr : functionManager.containerPeerTransportATCP.getApplications() ) {
          try {
            XDAQParameter pam = null;
            pam = ((XdaqApplication)qr).getXDAQParameter();
            pam.select(new String[] {"stateName"});
            pam.get();
            peerTransportATCPstateName =  pam.getValue("stateName");
            logger.info("[HCAL " + functionManager.FMname + "] Got the PeerTransportATCP's stateName--it is: " + peerTransportATCPstateName);
          }
          catch (XDAQTimeoutException e) {
            String errMessage = "[HCAL " + functionManager.FMname + "] Error! XDAQTimeoutException: while getting the PeerTransportATCP stateName...";
            logger.error(errMessage);
            functionManager.sendCMSError(errMessage);
          }
          catch (XDAQException e) {
            String errMessage = "[HCAL " + functionManager.FMname + "] Error! XDAQException: while getting the PeerTransportATCP stateName...";
            logger.error(errMessage);
            functionManager.sendCMSError(errMessage);
          }
        }
        try {
          if (peerTransportATCPstateName.equals("Halted")) {
            logger.debug("[HCAL LVL2 " + functionManager.FMname + "] configuring PeerTransportATCPs ...");
            functionManager.containerPeerTransportATCP.execute(HCALInputs.CONFIGURE);
          }
        }
        catch (QualifiedResourceContainerException e) {
          String errMessage = "[HCAL LVL2 " + functionManager.FMname + "] Error! QualifiedResourceContainerException: configuring PeerTransportATCPs failed ...";
          logger.error(errMessage,e);
          functionManager.sendCMSError(errMessage);
          functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT("Error")));
          functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("oops - technical difficulties ...")));
          if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; return;}
        }
      }

      for (QualifiedResource qr : functionManager.containerhcalSupervisor.getApplications() ){
        try {
          XDAQParameter pam = null;
          pam =((XdaqApplication)qr).getXDAQParameter();

          pam.select(new String[] {"IsLocalRun", "TriggerKey"});
          pam.setValue("IsLocalRun", String.valueOf(RunType.equals("local")));
          logger.info("[HCAL " + functionManager.FMname + "] Set IsLocalRun to: " + String.valueOf(RunType.equals("local")));
          pam.setValue("TriggerKey", TpgKey);

          pam.send();
        }
        catch (XDAQTimeoutException e) {
          String errMessage = "[HCAL " + functionManager.FMname + "] Error! XDAQTimeoutException: configAction() when trying to send IsLocalRun and TriggerKey to the HCAL supervisor\n Perhaps this application is dead!?";
          logger.error(errMessage,e);
          functionManager.sendCMSError(errMessage);
          functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT("Error")));
          functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("oops - technical difficulties ...")));
          if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; return;}

        }
        catch (XDAQException e) {
          String errMessage = "[HCAL " + functionManager.FMname + "] Error! XDAQException: onfigAction() when trying to send IsLocalRun and TriggerKey to the HCAL supervisor";
          logger.error(errMessage,e);
          functionManager.sendCMSError(errMessage);
          functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT("Error")));
          functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("oops - technical difficulties ...")));
          if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; return;}

        }
      }
      // configuring all created HCAL applications by means of sending the RunType to the HCAL supervisor
      if (!functionManager.ErrorState) {
        sendRunTypeConfiguration(FullCfgScript,FullTTCciControlSequence,FullLTCControlSequence,FullTCDSControlSequence,FullLPMControlSequence,FullPIControlSequence, FedEnableMask, UsePrimaryTCDS);
      }

      // if not talking to applications which talk asynchronous SOAP wait only for the HCAL supervisor
      if ( (!functionManager.asyncSOAP) && (!functionManager.ErrorState) ) {
        waitforHCALsupervisor();
      }

      if (functionManager.FMrole.equals("EvmTrig")) {

        // configure FEDStreamers ... nothing to do here

        // configure EVMs
        if (!functionManager.containerEVM.isEmpty()) {
          try {
            logger.debug("[HCAL LVL2 " + functionManager.FMname + "] configuring EVM ...");
            functionManager.containerEVM.execute(HCALInputs.CONFIGURE);
          }
          catch (QualifiedResourceContainerException e) {
            String errMessage = "[HCAL LVL2 " + functionManager.FMname + "] Error! QualifiedResourceContainerException: configuring EVM failed ...";
            logger.error(errMessage,e);
            functionManager.sendCMSError(errMessage);
            functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT("Error")));
            functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("oops - technical difficulties ...")));
            if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; return;}
          }
        }

        // configure RUs
        if (!functionManager.containerRU.isEmpty()) {
          try {
            logger.debug("[HCAL LVL2 " + functionManager.FMname + "] configuring RU ...");
            functionManager.containerRU.execute(HCALInputs.CONFIGURE);
          }
          catch (QualifiedResourceContainerException e) {
            String errMessage = "[HCAL LVL2 " + functionManager.FMname + "] Error! QualifiedResourceContainerException: configuring RU failed ...";
            logger.error(errMessage,e);
            functionManager.sendCMSError(errMessage);
            functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT("Error")));
            functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("oops - technical difficulties ...")));
            if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; return;}
          }
        }

        // configure BUs
        if (!functionManager.containerBU.isEmpty()) {
          try {
            logger.debug("[HCAL LVL2 " + functionManager.FMname + "] configuring BU ...");
            functionManager.containerBU.execute(HCALInputs.CONFIGURE);
          }
          catch (QualifiedResourceContainerException e) {
            String errMessage = "[HCAL LVL2 " + functionManager.FMname + "] Error! QualifiedResourceContainerException: configuring BU failed ...";
            logger.error(errMessage,e);
            functionManager.sendCMSError(errMessage);
            functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT("Error")));
            functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("oops - technical difficulties ...")));
            if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; return;}
          }
        }

        // configure FUEventProcessors
        if (!functionManager.containerFUEventProcessor.isEmpty()) {
          try {
            logger.debug("[HCAL LVL2 " + functionManager.FMname + "] configuring FUEventProcessor ...");
            functionManager.containerFUEventProcessor.execute(HCALInputs.CONFIGURE);
          }
          catch (QualifiedResourceContainerException e) {
            String errMessage = "[HCAL LVL2 " + functionManager.FMname + "] Error! QualifiedResourceContainerException: configuring FUEventProcessor failed ...";
            logger.error(errMessage,e);
            functionManager.sendCMSError(errMessage);
            functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT("Error")));
            functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("oops - technical difficulties ...")));
            if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; return;}
          }
        }

        // configure FUResourceBrokers
        if (!functionManager.containerFUResourceBroker.isEmpty()) {
          try {
            logger.debug("[HCAL LVL2 " + functionManager.FMname + "] configuring FUResourceBrokers ...");
            functionManager.containerFUResourceBroker.execute(HCALInputs.CONFIGURE);
          }
          catch (QualifiedResourceContainerException e) {
            String errMessage = "[HCAL LVL2 " + functionManager.FMname + "] Error! QualifiedResourceContainerException: configuring FUResourceBrokers failed ...";
            logger.error(errMessage,e);
            functionManager.sendCMSError(errMessage);
            functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT("Error")));
            functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("oops - technical difficulties ...")));
            if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; return;}
          }
        }

        // configure StorageManagers
        if (!functionManager.containerStorageManager.isEmpty()) {
          try {
            logger.debug("[HCAL LVL2 " + functionManager.FMname + "] configuring StorageManager ...");
            functionManager.containerStorageManager.execute(HCALInputs.CONFIGURE);
          }
          catch (QualifiedResourceContainerException e) {
            String errMessage = "[HCAL LVL2 " + functionManager.FMname + "] Error! QualifiedResourceContainerException: configuring StorageManager failed ...";
            logger.error(errMessage,e);
            functionManager.sendCMSError(errMessage);
            functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT("Error")));
            functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("oops - technical difficulties ...")));
            if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; return;}
          }
        }

      }

      // configuring the MonLogger application
      if (HandleMonLoggers) {
        try {
          logger.debug("[HCAL LVL2 " + functionManager.FMname + "] Configuring the MonLogger applications ...");

          functionManager.containerMonLogger.execute(HCALInputs.CONFIGURE);
        }
        catch (QualifiedResourceContainerException e) {
          String errMessage = "[HCAL LVL2 " + functionManager.FMname + "] Error! QualifiedResourceContainerException: configuring the MonLogger failed ...";
          logger.error(errMessage,e);
          functionManager.sendCMSError(errMessage);
          functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT("Error")));
          functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("oops - technical difficulties ...")));
          if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; return;}
        }
      }

      // leave intermediate state directly only when not talking to asynchronous applications
      if ( (!functionManager.asyncSOAP) && (!functionManager.ErrorState) ) {
        if (!functionManager.getState().getStateString().equals(HCALStates.CONFIGURED.toString())) {
          functionManager.fireEvent(HCALInputs.SETCONFIGURE);
        }
      }

      // set actions
      functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT(functionManager.getState().getStateString())));
      functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("configureAction executed ... - we're close ...")));

      logger.info("[HCAL LVL2 " + functionManager.FMname + "] configureAction executed ... - were are close ...");
    }
  }

  public void startAction(Object obj) throws UserActionException {
    if (obj instanceof StateNotification) {

      // triggered by State Notification from child resource
      computeNewState((StateNotification) obj);
      return;

    }
    else if (obj instanceof StateEnteredEvent) {
      System.out.println("[HCAL LVL2 " + functionManager.FMname + "] Executing startAction");
      logger.info("[HCAL LVL2 " + functionManager.FMname + "] Executing startAction");

      // reset the non-async error state handling
      functionManager.ErrorState = false;

      // set actions
      functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT("calculating state")));
      functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("Starting ...")));

      // get the parameters of the command
      ParameterSet<CommandParameter> parameterSet = getUserFunctionManager().getLastInput().getParameterSet();

      // check parameter set
      if (parameterSet.size()==0) {

        functionManager.RunNumber = ((IntegerT)functionManager.getParameterSet().get(HCALParameters.RUN_NUMBER).getValue()).getInteger();
        RunSeqNumber = ((IntegerT)functionManager.getParameterSet().get(HCALParameters.RUN_SEQ_NUMBER).getValue()).getInteger();
        TriggersToTake = ((IntegerT)functionManager.getParameterSet().get(HCALParameters.NUMBER_OF_EVENTS).getValue()).getInteger();

        if (!RunType.equals("local")) {
          String errMessage = "[HCAL LVL2 " + functionManager.FMname + "] Error! command parameter problem for the startAction ...";
          logger.error(errMessage);
          functionManager.sendCMSError(errMessage);
          functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT("Error")));
          functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("oops - technical difficulties ...")));
          if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; return;}
        }
        else {
          logger.info("[HCAL LVL2 " + functionManager.FMname + "] startAction: We are in local mode ...");

          if (TestMode.equals("OfficialRunNumbers") || TestMode.equals("RunInfoPublish")) {

            RunNumberData rnd = getOfficialRunNumber();

            functionManager.RunNumber    = rnd.getRunNumber();
            RunSeqNumber = rnd.getSequenceNumber();

            functionManager.getParameterSet().put(new FunctionManagerParameter<IntegerT>(HCALParameters.RUN_NUMBER, new IntegerT(functionManager.RunNumber)));
            functionManager.getParameterSet().put(new FunctionManagerParameter<IntegerT>(HCALParameters.RUN_SEQ_NUMBER, new IntegerT(RunSeqNumber)));

            logger.debug("[HCAL LVL2 " + functionManager.FMname + "] TestMode! ... run number: " + functionManager.RunNumber + ", SequenceNumber: " + RunSeqNumber);
          }

          if (TestMode.equals("RunInfoPublish")) {
            logger.warn("[HCAL LVL2 A] TestMode! Publishing RunInfo summary ...");
            StartTime = new Date();
            StopTime = new Date();
            publishRunInfoSummary();
            publishRunInfoSummaryfromXDAQ();
            logger.warn("[HCAL LVL2 A] TestMode! ... RunInfo summary should be published.");
          }

        }
      }
      else {

        // get the run number from the start command
        if (parameterSet.get(HCALParameters.RUN_NUMBER) != null) {
          functionManager.RunNumber = ((IntegerT)parameterSet.get(HCALParameters.RUN_NUMBER).getValue()).getInteger();
          functionManager.getParameterSet().put(new FunctionManagerParameter<IntegerT>(HCALParameters.RUN_NUMBER,new IntegerT(functionManager.RunNumber)));
          functionManager.getParameterSet().put(new FunctionManagerParameter<IntegerT>(HCALParameters.STARTED_WITH_RUN_NUMBER,new IntegerT(functionManager.RunNumber)));
        }
        else {
          String errMessage = "[HCAL LVL2 " + functionManager.FMname + "] Error! Did not receive a run number ...";
          logger.error(errMessage);
          functionManager.sendCMSError(errMessage);
          functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT("Error")));
          functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("oops - technical difficulties ...")));
          if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; return;}
        }

        // get the run sequence number from the start command
        if (parameterSet.get(HCALParameters.RUN_SEQ_NUMBER) != null) {
          RunSeqNumber = ((IntegerT)parameterSet.get(HCALParameters.RUN_SEQ_NUMBER).getValue()).getInteger();
          functionManager.getParameterSet().put(new FunctionManagerParameter<IntegerT>(HCALParameters.RUN_SEQ_NUMBER,new IntegerT(RunSeqNumber)));
        }
        else {
          if (RunType.equals("local")) { logger.warn("[HCAL LVL2 " + functionManager.FMname + "] Warning! Did not receive a run sequence number.\nThis is OK for global runs."); }
        }

        // get the number of requested events
        if (parameterSet.get(HCALParameters.NUMBER_OF_EVENTS) != null) {
          TriggersToTake = ((IntegerT)parameterSet.get(HCALParameters.NUMBER_OF_EVENTS).getValue()).getInteger();
          functionManager.getParameterSet().put(new FunctionManagerParameter<IntegerT>(HCALParameters.NUMBER_OF_EVENTS,new IntegerT(TriggersToTake)));
        }
        else {
          if (RunType.equals("local")) { logger.warn("[HCAL LVL2 " + functionManager.FMname + "] Warning! Did not receive the number of events to take.\nThis is OK for global runs."); }
        }

        if (TestMode.equals("RunInfoPublish")) {
          logger.warn("[HCAL LVL2 B] TestMode! Publishing RunInfo summary ...");
          StartTime = new Date();
          StopTime = new Date();
          publishRunInfoSummary();
          publishRunInfoSummaryfromXDAQ();
          logger.warn("[HCAL LVL2 B] TestMode! ... RunInfo summary should be published.");
        }
      }

      // offical run number handling
      if (functionManager.containerTriggerAdapter!=null) {
        if (!functionManager.containerTriggerAdapter.isEmpty()) {

          // determine run number and run sequence number and overwrite what was set before
          if (OfficialRunNumbers) {

            RunNumberData rnd = getOfficialRunNumber();

            functionManager.RunNumber    = rnd.getRunNumber();
            RunSeqNumber = rnd.getSequenceNumber();

            functionManager.getParameterSet().put(new FunctionManagerParameter<IntegerT>(HCALParameters.RUN_NUMBER, new IntegerT(functionManager.RunNumber)));
            functionManager.getParameterSet().put(new FunctionManagerParameter<IntegerT>(HCALParameters.RUN_SEQ_NUMBER, new IntegerT(RunSeqNumber)));
          }
        }
        logger.debug("[HCAL LVL2 " + functionManager.FMname + "] Received parameters to sent to TriggerAdapter, etc.: RunType=" + RunType + ", TriggersToTake=" + TriggersToTake + ", RunNumber=" + functionManager.RunNumber + " and RunSeqNumber=" + RunSeqNumber);
      }

      logger.debug("[HCAL LVL2 " + functionManager.FMname + "] Received parameters to sent to the HCAL supervisor: RunNumber=" +functionManager.RunNumber);

      if (TestMode.equals("TriggerAdapterTest")) {
        logger.debug("[HCAL LVL2 " + functionManager.FMname + "] TriggerAdapterTest: Sending to the TriggerAdapter: RunType=" + RunType + ", TriggersToTake=" + TriggersToTake + ", RunNumber=" + functionManager.RunNumber + " and RunSeqNumber=" + RunSeqNumber);
      }

      // start i.e. enable HCAL
      if (!functionManager.containerhcalSupervisor.isEmpty()) {

        {
          String debugMessage = "[HCAL LVL2 " + functionManager.FMname + "] HCAL supervisor for starting found - good!";
          logger.debug(debugMessage);
        }

        // sending some info to the HCAL supervisor
        {
          XDAQParameter pam = null;

          // prepare and set for all HCAL supervisors the RunType
          for (QualifiedResource qr : functionManager.containerhcalSupervisor.getApplications() ){
            try {
              pam =((XdaqApplication)qr).getXDAQParameter();

              pam.select(new String[] {"RunNumber"});
              pam.setValue("RunNumber",functionManager.RunNumber.toString());

              pam.send();
            }
            catch (XDAQTimeoutException e) {
              String errMessage = "[HCAL " + functionManager.FMname + "] Error! XDAQTimeoutException: startAction() when trying to send the functionManager.RunNumber to the HCAL supervisor\n Perhaps this application is dead!?";
              logger.error(errMessage,e);
              functionManager.sendCMSError(errMessage);
              functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT("Error")));
              functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("oops - technical difficulties ...")));
              if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; return;}

            }
            catch (XDAQException e) {
              String errMessage = "[HCAL " + functionManager.FMname + "] Error! XDAQException: startAction() when trying to send the functionManager.RunNumber to the HCAL supervisor";
              logger.error(errMessage,e);
              functionManager.sendCMSError(errMessage);
              functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT("Error")));
              functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("oops - technical difficulties ...")));
              if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; return;}

            }
          }
        }

        // start the PeerTransportATCPs
        if (!functionManager.ATCPsWereStartedOnce) {

          // make sure that the ATCP transports were only started only once
          functionManager.ATCPsWereStartedOnce = true;

          if (!functionManager.containerPeerTransportATCP.isEmpty()) {
            try {
              logger.debug("[HCAL LVL2 " + functionManager.FMname + "] starting PeerTransportATCP ...");
              functionManager.containerPeerTransportATCP.execute(HCALInputs.HCALSTART);
            }
            catch (QualifiedResourceContainerException e) {
              String errMessage = "[HCAL LVL2 " + functionManager.FMname + "] Error! QualifiedResourceContainerException: starting PeerTransportATCP failed ...";
              logger.error(errMessage,e);
              functionManager.sendCMSError(errMessage);
              functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT("Error")));
              functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("oops - technical difficulties ...")));
              if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; return;}
            }
          }
        }

        // starting HCAL
        if (functionManager.asyncSOAP) { HCALSuperVisorIsOK = false; }  // handle the not async SOAP talking HCAL supervisor when there are async SOAP applications defined
        try {
          logger.debug("[HCAL LVL2 " + functionManager.FMname + "] Starting i.e. sending Enable to the HCAL supervisor ...");

          // define start time
          StartTime = new Date();

          if (HCALSupervisorAsyncEnable) {
            functionManager.containerhcalSupervisor.execute(HCALInputs.HCALASYNCSTART);
          }
          else {
            functionManager.containerhcalSupervisor.execute(HCALInputs.HCALSTART);
          }

        }
        catch (QualifiedResourceContainerException e) {
          String errMessage = "[HCAL LVL2 " + functionManager.FMname + "] Error! QualifiedResourceContainerException: starting (HCAL=Enable) failed ...";
          logger.error(errMessage,e);
          functionManager.sendCMSError(errMessage);
          functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT("Error")));
          functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("oops - technical difficulties ...")));
          if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; return;}
        }

        if (functionManager.FMrole.equals("EvmTrig")) {
          logger.debug("[HCAL LVL2 " + functionManager.FMname + "] Now I am trying to talk to a TriggerAdapter (and EVMs, BUs and RUs in case they are defined) ...");
        }

        // handle TriggerAdapters and event building ...
        if (functionManager.containerTriggerAdapter!=null) {
          if (!functionManager.containerTriggerAdapter.isEmpty()) {

            // send the run number etc. to the TriggerAdapters
            {
              XDAQParameter pam = null;
              for (QualifiedResource qr : functionManager.containerTriggerAdapter.getApplications() ) {
                try {
                  logger.debug("[HCAL LVL2 " + functionManager.FMname + "] Start of handling the TriggerAdapter ...");

                  pam =((XdaqApplication)qr).getXDAQParameter();
                  pam.select(new String[] {"runType", "TriggersToTake", "RunNumber", "RunNumberSequenceId"});

                  pam.setValue("runType",RunType);
                  pam.setValue("TriggersToTake",TriggersToTake.toString());
                  pam.setValue("RunNumber",functionManager.RunNumber.toString());
                  pam.setValue("RunNumberSequenceId",RunSeqNumber.toString());

                  logger.debug("[HCAL LVL2 " + functionManager.FMname + "] Sending to the TriggerAdapter: RunType=" + RunType + ", TriggersToTake=" + TriggersToTake + ", RunNumber=" + functionManager.RunNumber + " and RunSeqNumber=" + RunSeqNumber);

                  pam.send();
                }
                catch (XDAQTimeoutException e) {
                  String errMessage = "[HCAL LVL2 " + functionManager.FMname + "] Error! XDAQTimeoutException: startAction()\n Perhaps the trigger adapter application is dead!?";
                  logger.error(errMessage,e);
                  functionManager.sendCMSError(errMessage);
                  functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT("Error")));
                  functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("oops - technical difficulties ...")));
                  if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; return;}

                }
                catch (XDAQException e) {
                  String errMessage = "[HCAL LVL2 " + functionManager.FMname + "] Error! XDAQException: startAction()";
                  logger.error(errMessage,e);
                  functionManager.sendCMSError(errMessage);
                  functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT("Error")));
                  functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("oops - technical difficulties ...")));
                  if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; return;}

                }
              }
            }

            // send the run number to the FUResourceBrokers
            {
              XDAQParameter pam = null;
              for (QualifiedResource qr : functionManager.containerFUResourceBroker.getApplications() ) {
                try {
                  logger.debug("[HCAL LVL2 " + functionManager.FMname + "] Start of handling the FUEventProcessor applications ...");

                  pam =((XdaqApplication)qr).getXDAQParameter();
                  pam.select("RunNumber");
                  pam.setValue("RunNumber",functionManager.RunNumber.toString());

                  logger.debug("[HCAL LVL2 " + functionManager.FMname + "] Sending to the FUEventProcessor: RunNumber=" + functionManager.RunNumber);

                  pam.send();
                }
                catch (XDAQTimeoutException e) {
                  String errMessage = "[HCAL LVL2 " + functionManager.FMname + "] Error! XDAQTimeoutException: startAction()\n Perhaps the FU resource broker application is dead!?";
                  logger.error(errMessage,e);
                  functionManager.sendCMSError(errMessage);
                  functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT("Error")));
                  functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("oops - technical difficulties ...")));
                  if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; return;}

                }
                catch (XDAQException e) {
                  String errMessage = "[HCAL LVL2 " + functionManager.FMname + "] Error! XDAQException: startAction()";
                  logger.error(errMessage,e);
                  functionManager.sendCMSError(errMessage);
                  functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT("Error")));
                  functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("oops - technical difficulties ...")));
                  if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; return;}

                }
              }
            }

            // send the run number to the FUEventProcessors
            {
              XDAQParameter pam = null;
              for (QualifiedResource qr : functionManager.containerFUEventProcessor.getApplications() ) {
                try {
                  logger.debug("[HCAL LVL2 " + functionManager.FMname + "] Start of handling the FUEventProcessor applications ...");

                  pam =((XdaqApplication)qr).getXDAQParameter();
                  pam.select("RunNumber");
                  pam.setValue("RunNumber",functionManager.RunNumber.toString());

                  logger.debug("[HCAL LVL2 " + functionManager.FMname + "] Sending to the FUEventProcessor: RunNumber=" + functionManager.RunNumber);

                  pam.send();
                }
                catch (XDAQTimeoutException e) {
                  String errMessage = "[HCAL LVL2 " + functionManager.FMname + "] Error! XDAQTimeoutException: startAction()\n Perhaps the FU event processor application is dead!?";
                  logger.error(errMessage,e);
                  functionManager.sendCMSError(errMessage);
                  functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT("Error")));
                  functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("oops - technical difficulties ...")));
                  if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; return;}

                }
                catch (XDAQException e) {
                  String errMessage = "[HCAL LVL2 " + functionManager.FMname + "] Error! XDAQException: startAction()";
                  logger.error(errMessage,e);
                  functionManager.sendCMSError(errMessage);
                  functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT("Error")));
                  functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("oops - technical difficulties ...")));
                  if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; return;}

                }
              }
            }

            // send the run number to the EVM
            {
              XDAQParameter pam = null;
              for (QualifiedResource qr : functionManager.containerEVM.getApplications() ) {
                try {
                  logger.debug("[HCAL LVL2 " + functionManager.FMname + "] Start of handling the EVM applications ...");

                  pam =((XdaqApplication)qr).getXDAQParameter();
                  pam.select("RunNumber");
                  pam.setValue("RunNumber",functionManager.RunNumber.toString());

                  logger.debug("[HCAL LVL2 " + functionManager.FMname + "] Sending to the EVM: RunNumber=" + functionManager.RunNumber);

                  pam.send();
                }
                catch (XDAQTimeoutException e) {
                  String errMessage = "[HCAL LVL2 " + functionManager.FMname + "] Error! XDAQTimeoutException: startAction()\n Perhaps the EVM application is dead!?";
                  logger.error(errMessage,e);
                  functionManager.sendCMSError(errMessage);
                  functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT("Error")));
                  functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("oops - technical difficulties ...")));
                  if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; return;}

                }
                catch (XDAQException e) {
                  String errMessage = "[HCAL LVL2 " + functionManager.FMname + "] Error! XDAQException: startAction()";
                  logger.error(errMessage,e);
                  functionManager.sendCMSError(errMessage);
                  functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT("Error")));
                  functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("oops - technical difficulties ...")));
                  if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; return;}

                }
              }
            }

            // include scheduling Todo

            // start the StorageManagers early
            if (!functionManager.containerStorageManager.isEmpty()) {
              try {
                logger.debug("[HCAL LVL2 " + functionManager.FMname + "] starting StorageManager ...");
                functionManager.containerStorageManager.execute(HCALInputs.HCALSTART);
              }
              catch (QualifiedResourceContainerException e) {
                String errMessage = "[HCAL LVL2 " + functionManager.FMname + "] Error! QualifiedResourceContainerException: starting StorageManager failed ...";
                logger.error(errMessage,e);
                functionManager.sendCMSError(errMessage);
                functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT("Error")));
                functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("oops - technical difficulties ...")));
                if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; return;}
              }
            }

            // start the FEDStreamers
            if (!functionManager.containerFEDStreamer.isEmpty()) {
              try {
                logger.debug("[HCAL LVL2 " + functionManager.FMname + "] starting FEDStreamer ...");
                functionManager.containerFEDStreamer.execute(HCALInputs.FEDSTREAMERSTART);
              }
              catch (QualifiedResourceContainerException e) {
                String errMessage = "[HCAL LVL2 " + functionManager.FMname + "] Error! QualifiedResourceContainerException: starting FEDStreamer failed ...";
                logger.error(errMessage,e);
                functionManager.sendCMSError(errMessage);
                functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT("Error")));
                functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("oops - technical difficulties ...")));
                if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; return;}
              }
            }

            // start the RUs
            if (!functionManager.containerRU.isEmpty()) {
              try {
                logger.debug("[HCAL LVL2 " + functionManager.FMname + "] starting RU ...");
                functionManager.containerRU.execute(HCALInputs.HCALSTART);
              }
              catch (QualifiedResourceContainerException e) {
                String errMessage = "[HCAL LVL2 " + functionManager.FMname + "] Error! QualifiedResourceContainerException: starting RU failed ...";
                logger.error(errMessage,e);
                functionManager.sendCMSError(errMessage);
                functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT("Error")));
                functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("oops - technical difficulties ...")));
                if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; return;}
              }
            }

            // start the EVMs
            if (!functionManager.containerEVM.isEmpty()) {
              try {
                logger.debug("[HCAL LVL2 " + functionManager.FMname + "] starting EVM ...");
                functionManager.containerEVM.execute(HCALInputs.HCALSTART);
              }
              catch (QualifiedResourceContainerException e) {
                String errMessage = "[HCAL LVL2 " + functionManager.FMname + "] Error! QualifiedResourceContainerException: starting EVM failed ...";
                logger.error(errMessage,e);
                functionManager.sendCMSError(errMessage);
                functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT("Error")));
                functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("oops - technical difficulties ...")));
                if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; return;}
              }
            }

            // start the BUs
            if (!functionManager.containerBU.isEmpty()) {
              try {
                logger.debug("[HCAL LVL2 " + functionManager.FMname + "] starting BU ...");
                functionManager.containerBU.execute(HCALInputs.HCALSTART);
              }
              catch (QualifiedResourceContainerException e) {
                String errMessage = "[HCAL LVL2 " + functionManager.FMname + "] Error! QualifiedResourceContainerException: starting BU failed ...";
                logger.error(errMessage,e);
                functionManager.sendCMSError(errMessage);
                functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT("Error")));
                functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("oops - technical difficulties ...")));
                if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; return;}
              }
            }

            // start the FUResourceBrokers
            if (!functionManager.containerFUResourceBroker.isEmpty()) {
              try {
                logger.debug("[HCAL LVL2 " + functionManager.FMname + "] starting FUResourceBrokers ...");
                functionManager.containerFUResourceBroker.execute(HCALInputs.HCALSTART);
              }
              catch (QualifiedResourceContainerException e) {
                String errMessage = "[HCAL LVL2 " + functionManager.FMname + "] Error! QualifiedResourceContainerException: starting FUResourceBrokers failed ...";
                logger.error(errMessage,e);
                functionManager.sendCMSError(errMessage);
                functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT("Error")));
                functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("oops - technical difficulties ...")));
                if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; return;}
              }
            }

            // start the FUEventProcessors
            if (!functionManager.containerFUEventProcessor.isEmpty()) {
              try {
                logger.debug("[HCAL LVL2 " + functionManager.FMname + "] starting FUEventProcessor ...");
                functionManager.containerFUEventProcessor.execute(HCALInputs.HCALSTART);
              }
              catch (QualifiedResourceContainerException e) {
                String errMessage = "[HCAL LVL2 " + functionManager.FMname + "] Error! QualifiedResourceContainerException: starting FUEventProcessor failed ...";
                logger.error(errMessage,e);
                functionManager.sendCMSError(errMessage);
                functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT("Error")));
                functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("oops - technical difficulties ...")));
                if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; return;}
              }
            }
          }
          else {
            if (functionManager.FMrole.equals("EvmTrig")) {
              String errMessage = "[HCAL LVL2 " + functionManager.FMname + "] Error! No TriggerAdapter found: startAction()";
              logger.error(errMessage);
              functionManager.sendCMSError(errMessage);
              functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT("Error")));
              functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("oops - technical difficulties ...")));
              if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; return;}
            }
          }
        }
      }
      else if (!functionManager.FMrole.equals("Level2_TCDSLPM")) {
        String errMessage = "[HCAL LVL2 " + functionManager.FMname + "] Error! No HCAL supervisor found: startAction()";
        logger.error(errMessage);
        functionManager.sendCMSError(errMessage);
        functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT("Error")));
        functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT(errMessage)));
        if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; return;}
      }

      // starting the MonLogger application
      if (HandleMonLoggers) {
        try {
          logger.debug("[HCAL LVL2 " + functionManager.FMname + "] Starting i.e. sending Enable to the MonLogger applications ...");

          functionManager.containerMonLogger.execute(HCALInputs.HCALSTART);
        }
        catch (QualifiedResourceContainerException e) {
          String errMessage = "[HCAL LVL2 " + functionManager.FMname + "] Error! QualifiedResourceContainerException: starting the MonLogger failed ...";
          logger.error(errMessage,e);
          functionManager.sendCMSError(errMessage);
          functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT("Error")));
          functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("oops - technical difficulties ...")));
          if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; return;}
        }
      }

      if (!HCALSupervisorAsyncEnable) {
        // leave intermediate state only when not talking to asynchronous applications
        if ( (!functionManager.asyncSOAP) && (!functionManager.ErrorState) ) {
          functionManager.fireEvent( HCALInputs.SETSTART );
        }
      }

      // set action
      functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT(functionManager.getState().getStateString())));
      functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("startAction executed ...")));

      functionManager.RunWasStarted = true; // switch to enable writing to runInfo when run was destroyed

      logger.debug("startAction executed ...");

    }
  }

  public void runningAction(Object obj) throws UserActionException {
    if (obj instanceof StateNotification) {

      // triggered by State Notification from child resource
      computeNewState((StateNotification) obj);
      return;

    }
    else if (obj instanceof StateEnteredEvent) {
      System.out.println("[HCAL LVL2 " + functionManager.FMname + "] Executing runningAction");
      logger.info("[HCAL LVL2 " + functionManager.FMname + "] Executing runningAction");

      // reset the non-async error state handling
      functionManager.ErrorState = false;

      // only in local runs when all triggers were sent the run is stopped with 60 sec timeout
      if (functionManager.FMrole.equals("EvmTrig")) {

        // finally start the TriggerAdapters
        if (functionManager.containerTriggerAdapter!=null) {
          if (!functionManager.containerTriggerAdapter.isEmpty() && !functionManager.FMWasInPausedState) {
            try {
              //logger.info("[JohnLog4] [HCAL LVL2 " + functionManager.FMname + "] Issuing the L1As i.e. sending Enable to the TriggerAdapter ...");
              logger.info("[HCAL LVL2 " + functionManager.FMname + "] Issuing the L1As i.e. sending Enable to the TriggerAdapter ...");
              functionManager.containerTriggerAdapter.execute(HCALInputs.HCALSTART);
            }
            catch (QualifiedResourceContainerException e) {
              String errMessage = "[HCAL LVL2 " + functionManager.FMname + "] Error! QualifiedResourceContainerException: starting (TriggerAdapter=Enable) failed ...";
              logger.error(errMessage,e);
              functionManager.sendCMSError(errMessage);
              functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT("Error")));
              functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("oops - technical difficulties ...")));
              if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; return;}
            }
          }
        }

        // set actions for local runs
        functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT(functionManager.getState().getStateString())));
        functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("waiting for run to finish ...")));

        logger.debug("[HCAL LVL2 " + functionManager.FMname + "] runningAction executed ...");

      }
      else {
        // set actions for gloabl runs
        functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT(functionManager.getState().getStateString())));
        functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("running like hell ...")));
      }
      //XdaqApplicationContainer lpmContainer =  new XdaqApplicationContainer(functionManager.containerXdaqApplication.getApplicationsOfClass("tcds::lpm::LPMController"));      
      //if (!lpmContainer.isEmpty()) {
      //  logger.info("[JohnLog4] " + functionManager.FMname + ": Sending Enable to the LPMController.");
      //  try {
      //    lpmContainer.execute(HCALInputs.HCALSTART);
      //  }
      //  catch (QualifiedResourceContainerException e) {
      //    String errMessage = "[HCAL LVL2 " + functionManager.FMname + "] Error! QualifiedResourceContainerException: starting (LPMController=Enable) failed ... Message: " + e.getMessage();
      //    logger.error(errMessage,e);
      //    functionManager.sendCMSError(errMessage);
      //    functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT("Error")));
      //    functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("oops - technical difficulties ...")));
      //    if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; return;}
      //  }
      //}
      // patch for pause-resume behavior
      functionManager.FMWasInPausedState = false;
    }
  }

  public void pauseAction(Object obj) throws UserActionException {
    if (obj instanceof StateNotification) {

      // triggered by State Notification from child resource
      computeNewState((StateNotification) obj);
      return;

    }
    else if (obj instanceof StateEnteredEvent) {
      System.out.println("[HCAL LVL2 " + functionManager.FMname + "] Executing pauseAction");
      logger.info("[HCAL LVL2 " + functionManager.FMname + "] Executing pauseAction");

      // reset the non-async error state handling
      functionManager.ErrorState = false;
      functionManager.FMWasInPausedState = true;
      // set action
      functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT("calculating state")));
      functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("pausing")));

      // pause triggers
      if (functionManager.containerTriggerAdapter!=null) {
        if (!functionManager.containerTriggerAdapter.isEmpty()) {

          {
            String debugMessage = "[HCAL LVL2 " + functionManager.FMname + "] TriggerAdapter for pausing found- good!";
            logger.debug(debugMessage);
          }

          try {
            functionManager.containerTriggerAdapter.execute(HCALInputs.HCALPAUSE);
          }
          catch (QualifiedResourceContainerException e) {
            String errMessage = "[HCAL LVL2 " + functionManager.FMname + "] Error! QualifiedResourceContainerException: pausing (TriggerAdapter=Suspend) failed ...";
            logger.error(errMessage,e);
            functionManager.sendCMSError(errMessage);
            functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT("Error")));
            functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("oops - technical difficulties ...")));
            if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; return;}
          }

        }
        else {
          if (functionManager.FMrole.equals("EvmTrig")) {
            String errMessage = "[HCAL LVL2 " + functionManager.FMname + "] Error! No TriggerAdapter found: pauseAction()";
            logger.error(errMessage);
            functionManager.sendCMSError(errMessage);
            functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT("Error")));
            functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("oops - technical difficulties ...")));
            if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; return;}
          }
        }
      }

      // leave intermediate state
      if (!functionManager.ErrorState) {
        functionManager.fireEvent( HCALInputs.SETPAUSE );
      }
      // set action
      functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT(functionManager.getState().getStateString())));
      functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("pausingAction executed ...")));

      logger.debug("[HCAL LVL2 " + functionManager.FMname + "] pausingAction executed ...");

    }
  }

  public void resumeAction(Object obj) throws UserActionException {
    if (obj instanceof StateNotification) {

      // triggered by State Notification from child resource
      computeNewState((StateNotification) obj);
      return;

    }
    else if (obj instanceof StateEnteredEvent) {
      System.out.println("[HCAL LVL2 " + functionManager.FMname + "] Executing resumeAction");
      logger.info("[HCAL LVL2 " + functionManager.FMname + "] Executing resumeAction");

      // reset the non-async error state handling
      functionManager.ErrorState = false;

      // set action
      functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT("calculating state")));
      functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("resuming")));

      // resume triggers
      if (functionManager.containerTriggerAdapter!=null) {
        if (!functionManager.containerTriggerAdapter.isEmpty()) {

          {
            String debugMessage = "[HCAL LVL2 " + functionManager.FMname + "] TriggerAdapter for resuming found- good!";
            logger.debug(debugMessage);
          }

          try {
            functionManager.containerTriggerAdapter.execute(HCALInputs.RESUME);
          }
          catch (QualifiedResourceContainerException e) {
            String errMessage = "[HCAL LVL2 " + functionManager.FMname + "] Error! QualifiedResourceContainerException: resuming failed ...";
            logger.error(errMessage,e);
            functionManager.sendCMSError(errMessage);
            functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT("Error")));
            functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("oops - technical difficulties ...")));
            if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; return;}
          }

        }
        else {
          if (functionManager.FMrole.equals("EvmTrig")) {
            String errMessage = "[HCAL LVL2 " + functionManager.FMname + "] Error! No TriggerAdapter found: resumeAction()";
            logger.error(errMessage);
            functionManager.sendCMSError(errMessage);
            functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT("Error")));
            functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("oops - technical difficulties ...")));
            if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; return;}
          }
        }
      }

      // leave intermediate state
      if (!functionManager.ErrorState) {
        functionManager.fireEvent( HCALInputs.SETRESUME );
      }

      // set actions
      functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT(functionManager.getState().getStateString())));
      functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("resumeAction executed ...")));

      logger.debug("resumeAction executed ...");

    }
  }

  public void haltAction(Object obj) throws UserActionException {
    if (obj instanceof StateNotification) {

      // triggered by State Notification from child resource
      computeNewState((StateNotification) obj);
      return;

    }
    else if (obj instanceof StateEnteredEvent) {
      System.out.println("[HCAL LVL2 " + functionManager.FMname + "] Executing haltAction");
      logger.info("[HCAL LVL2 " + functionManager.FMname + "] Executing haltAction");

      // reset the non-async error state handling
      functionManager.ErrorState = false;
      functionManager.FMWasInPausedState = false;
      // set action
      functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT("calculating state")));
      functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("halting")));

      // halting the MonLogger application
      if (HandleMonLoggers) {
        try {
          logger.debug("[HCAL LVL2 " + functionManager.FMname + "] Halting the MonLogger applications ...");

          functionManager.containerMonLogger.execute(HCALInputs.HCALHALT);
        }
        catch (QualifiedResourceContainerException e) {
          String errMessage = "[HCAL LVL2 " + functionManager.FMname + "] Error! QualifiedResourceContainerException: halting the MonLogger failed ...";
          logger.error(errMessage,e);
          functionManager.sendCMSError(errMessage);
          functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT("Error")));
          functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("oops - technical difficulties ...")));
          if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; return;}
        }
      }

      // stop i.e. halt the triggering immediately and not waiting for the trigger adapter to report that it is stopped
      if (functionManager.FMrole.equals("EvmTrig")) {
        if (functionManager.containerTriggerAdapter!=null) {
          if (!functionManager.containerTriggerAdapter.isEmpty()) {

            {
              String debugMessage = "[HCAL LVL2 " + functionManager.FMname + "] TriggerAdapter for halting found- good!";
              logger.debug(debugMessage);
            }

            try {
              functionManager.containerTriggerAdapter.execute(HCALInputs.HCALHALT);
            }
            catch (QualifiedResourceContainerException e) {
              String errMessage = "[HCAL LVL2 " + functionManager.FMname + "] Error! QualifiedResourceContainerException: TriggerAdapter=Disable failed ...";
              logger.error(errMessage,e);
              functionManager.sendCMSError(errMessage);
              functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT("Error")));
              functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("oops - technical difficulties ...")));
              if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; return;}
            }

          }
          else {
            if (functionManager.FMrole.equals("EvmTrig")) {
              String errMessage = "[HCAL LVL2 " + functionManager.FMname + "] Error! No TriggerAdapter found: haltingAction()";
              logger.error(errMessage);
              functionManager.sendCMSError(errMessage);
              functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT("Error")));
              functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("oops - technical difficulties ...")));
              if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; return;}
            }
          }
        }
      }

      // halting HCAL
      if (!functionManager.containerhcalSupervisor.isEmpty()) {

        {
          String debugMessage = "[HCAL LVL2 " + functionManager.FMname + "] HCAL supervisor for haltAction() found- good!";
          logger.debug(debugMessage);
        }

        // halt
        try {

          // define stop time
          StopTime = new Date();

          functionManager.containerhcalSupervisor.execute(HCALInputs.HCALHALT);
        }
        catch (QualifiedResourceContainerException e) {
          String errMessage = "[HCAL LVL2 " + functionManager.FMname + "] Error! QualifiedResourceContainerException: halting step 1/2 (HCAL=Disable) failed";
          logger.error(errMessage,e);
          functionManager.sendCMSError(errMessage);
          functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT("Error")));
          functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("oops - technical difficulties ...")));
          if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; return;}
        }

        try {
          functionManager.containerhcalSupervisor.execute(HCALInputs.RESET);
        }
        catch (QualifiedResourceContainerException e) {
          String errMessage = "[HCAL LVL2 " + functionManager.FMname + "] Error! QualifiedResourceContainerException: halting step 2/2 (HCAL=Reset) failed ...";
          logger.error(errMessage,e);
          functionManager.sendCMSError(errMessage);
          functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT("Error")));
          functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("oops - technical difficulties ...")));
          if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; return;}
        }

      }
      else if (!functionManager.FMrole.equals("Level2_TCDSLPM")) {
        String errMessage = "[HCAL LVL2 " + functionManager.FMname + "] Error! No HCAL supervisor found: haltAction()";
        logger.error(errMessage);
        functionManager.sendCMSError(errMessage);
        functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT("Error")));
        functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT(errMessage)));
        if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; return;}
      }

      // stop the event building gracefully
      if (functionManager.FMrole.equals("EvmTrig")) {

        // check if all events were build before stopping
        if(!isRUBuildersEmpty()) {
          String errMessage = "[HCAL LVL2 " + functionManager.FMname + "] Error! Could not flush the EVMs ...";
          logger.error(errMessage);
          functionManager.sendCMSError(errMessage);
          functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT("Error")));
          functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("oops - technical difficulties ...")));
          if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; return;}
        }

        // stop the FEDStreamers
        if (!functionManager.containerFEDStreamer.isEmpty()) {
          try {
            logger.debug("[HCAL LVL2 " + functionManager.FMname + "] stopping FEDStreamers ...");
            functionManager.containerFEDStreamer.execute(HCALInputs.FEDSTREAMERSTOP);
          }
          catch (QualifiedResourceContainerException e) {
            String errMessage = "[HCAL LVL2 " + functionManager.FMname + "] Error! QualifiedResourceContainerException: stopping FEDStreamers failed ...";
            logger.error(errMessage,e);
            functionManager.sendCMSError(errMessage);
            functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT("Error")));
            functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("oops - technical difficulties ...")));
            if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; return;}
          }
        }

        // stop the FUEventProcessors
        if (!functionManager.containerFUEventProcessor.isEmpty()) {
          try {
            logger.debug("[HCAL LVL2 " + functionManager.FMname + "] stopping FUEventProcessors ...");
            functionManager.containerFUEventProcessor.execute(HCALInputs.STOP);
          }
          catch (QualifiedResourceContainerException e) {
            String errMessage = "[HCAL LVL2 " + functionManager.FMname + "] Error! QualifiedResourceContainerException: stopping FUEventProcessors failed ...";
            logger.error(errMessage,e);
            functionManager.sendCMSError(errMessage);
            functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT("Error")));
            functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("oops - technical difficulties ...")));
            if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; return;}
          }
        }

        // stop the FUResourceBrokers
        if (!functionManager.containerFUResourceBroker.isEmpty()) {
          try {
            logger.debug("[HCAL LVL2 " + functionManager.FMname + "] stopping FUEventProcessors ...");
            functionManager.containerFUResourceBroker.execute(HCALInputs.STOP);
          }
          catch (QualifiedResourceContainerException e) {
            String errMessage = "[HCAL LVL2 " + functionManager.FMname + "] Error! QualifiedResourceContainerException: stopping FUEventProcessors failed ...";
            logger.error(errMessage,e);
            functionManager.sendCMSError(errMessage);
            functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT("Error")));
            functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("oops - technical difficulties ...")));
            if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; return;}
          }
        }

        // stop the PeerTransportATCPs
        /*
           if (!functionManager.containerPeerTransportATCP.isEmpty()) {
           try {
           logger.debug("[HCAL LVL2 " + functionManager.FMname + "] stopping PeerTransportATCPs (using a HALT)...");
           functionManager.containerPeerTransportATCP.execute(HCALInputs.HALT);
           }
           catch (QualifiedResourceContainerException e) {
           String errMessage = "[HCAL LVL2 " + functionManager.FMname + "] Error! QualifiedResourceContainerException: stopping PeerTransportATCPs failed ...";
           logger.error(errMessage,e);
           functionManager.sendCMSError(errMessage);
           functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT("Error")));
           functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("oops - technical difficulties ...")));
           if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; return;}
           }
           }
           */
        // stop the StorageManagers
        if (!functionManager.containerStorageManager.isEmpty()) {
          try {
            logger.debug("[HCAL LVL2 " + functionManager.FMname + "] stopping StorageManagers ...");
            functionManager.containerStorageManager.execute(HCALInputs.STOP);
          }
          catch (QualifiedResourceContainerException e) {
            String errMessage = "[HCAL LVL2 " + functionManager.FMname + "] Error! QualifiedResourceContainerException: stopping StorageManagers failed ...";
            logger.error(errMessage,e);
            functionManager.sendCMSError(errMessage);
            functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT("Error")));
            functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("oops - technical difficulties ...")));
            if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; return;}
          }
        }
      }

      // check from which state we came, i.e. if we were in sTTS test mode disable this DCC special mode
      if (functionManager.getPreviousState().equals(HCALStates.TTSTEST_MODE)) {
        // when we came from TTSTestMode we need to give back control of sTTS to HW
        if (!functionManager.containerhcalDCCManager.isEmpty()) {

          {
            String debugMessage = "[HCAL LVL2 " + functionManager.FMname + "] at least one DCC (HCAL FED) for leaving the sTTS testing found- good!";
            logger.debug(debugMessage);
          }

          Integer DCC0sourceId=-1;
          Integer DCC1sourceId=-1;

          // get the DCC source ids
          {
            XDAQParameter pam = null;

            for (QualifiedResource qr : functionManager.containerhcalDCCManager.getApplications() ){
              try {
                logger.debug("[HCAL LVL2 " + functionManager.FMname + "] asking for the DCC source ids ...");

                pam =((XdaqApplication)qr).getXDAQParameter();
                pam.select("DCC0");
                pam.get();
                String DCC0sourceId_string = pam.getValue("sourceId");
                DCC0sourceId = new Integer(DCC0sourceId_string);

                logger.debug("[HCAL LVL2 " + functionManager.FMname + "] found DCC0 with source id: " + DCC0sourceId);

                pam =((XdaqApplication)qr).getXDAQParameter();
                pam.select("DCC1");
                pam.get();
                String DCC1sourceId_string = pam.getValue("sourceId");

                if (!DCC1sourceId_string.equals("-1")) {
                  DCC1sourceId = new Integer(DCC1sourceId_string);
                  logger.debug("[HCAL LVL2 " + functionManager.FMname + "] found DCC1 with source id: " + DCC1sourceId);
                }
                else {
                  logger.warn("[HCAL LVL2 " + functionManager.FMname + "] no DCC1 found cause source id = " + DCC1sourceId);
                }
              }
              catch (XDAQTimeoutException e) {
                String errMessage = "[HCAL LVL2 " + functionManager.FMname + "] Error! XDAQTimeoutException: haltAction()\n Perhaps the DCC manager application is dead!?";
                logger.error(errMessage,e);
                functionManager.sendCMSError(errMessage);
                functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT("Error")));
                functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("oops - technical difficulties ...")));
                if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; return;}
              }
              catch (XDAQException e) {
                String errMessage = "[HCAL LVL2 " + functionManager.FMname + "] Error! XDAQException: preparingTTSTestModeAction";
                logger.error(errMessage,e);
                functionManager.sendCMSError(errMessage);
                functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT("Error")));
                functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("oops - technical difficulties ...")));
                if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; return;}
              }
            }
          }

          // disable the sTTS test mode and reconfigure the DCCs for normal operation
          {
            Iterator ithcalDCC = functionManager.containerhcalDCCManager.getQualifiedResourceList().iterator();

            XdaqApplication hcalDCC = null;

            while (ithcalDCC.hasNext()) {

              hcalDCC = (XdaqApplication)ithcalDCC.next();

              logger.debug("[HCAL LVL2 " + functionManager.FMname + "] disabling the sTTS test now ...");

              try {
                if (DCC0sourceId!=-1) { hcalDCC.command(getTTSBag("disableTTStest",DCC0sourceId,0,0)); }
                if (DCC1sourceId!=-1) { hcalDCC.command(getTTSBag("disableTTStest",DCC1sourceId,0,0)); }
              }
              catch (XDAQMessageException e) {
                String errMessage = "[HCAL LVL2 " + functionManager.FMname + "] Error! XDAQMessageException: haltAction()";
                logger.error(errMessage,e);
                functionManager.sendCMSError(errMessage);
                functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT("Error")));
                functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("oops - technical difficulties ...")));
                if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; return;}
              }
            }
          }
        }
        else {
          String errMessage = "[HCAL LVL2 " + functionManager.FMname + "] Error! No DCC (HCAL FED) found: haltAction()";
          logger.error(errMessage);
          functionManager.sendCMSError(errMessage);
          functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT("Error")));
          functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("oops - technical difficulties ...")));
          if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; return;}
        }

      }

      // leave intermediate state only when not talking to asynchronous applications
      if ( (!functionManager.asyncSOAP) && (!functionManager.ErrorState) ) {
        functionManager.fireEvent( HCALInputs.SETHALT );
      }

      // publish info of the actual run taken
      publishRunInfoSummary();
      publishRunInfoSummaryfromXDAQ();
      functionManager.HCALRunInfo = null; // make RunInfo ready for the next round of run info to store

      // set action
      functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT(functionManager.getState().getStateString())));
      functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("haltAction executed ...")));

      logger.debug("[HCAL LVL2 " + functionManager.FMname + "] haltAction executed ...");
    }
  }

  public void coldResetAction(Object obj) throws UserActionException {
    if (obj instanceof StateNotification) {

      // triggered by State Notification from child resource
      computeNewState((StateNotification) obj);
      return;

    }
    else if (obj instanceof StateEnteredEvent) {
      System.out.println("[HCAL LVL2 " + functionManager.FMname + "] Executing coldResetAction");
      logger.info("[HCAL LVL2 " + functionManager.FMname + "] Executing coldResetAction");

      // reset the non-async error state handling
      functionManager.ErrorState = false;
      functionManager.FMWasInPausedState = false;
      // set action
      functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT("calculating state")));
      functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("brrr - cold resetting ...")));

      //
      // perhaps nothing have to be done here for HCAL !?
      //

      // leave intermediate state only when not talking to asynchronous applications
      if ( (!functionManager.asyncSOAP) && (!functionManager.ErrorState) ) {
        functionManager.fireEvent( HCALInputs.SETCOLDRESET );
      }


      publishRunInfoSummary();
      publishRunInfoSummaryfromXDAQ(); 
      functionManager.HCALRunInfo = null; // make RunInfo ready for the next round of run info to store


      // set action
      functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT(functionManager.getState().getStateString())));
      functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("coldResetAction executed ...")));

      logger.debug("[HCAL LVL2 " + functionManager.FMname + "] coldResetAction executed ...");
    }
  }

  public void stoppingAction(Object obj) throws UserActionException {
    if (obj instanceof StateNotification) {

      // triggered by State Notification from child resource
      computeNewState((StateNotification) obj);
      return;

    }
    else if (obj instanceof StateEnteredEvent) {
      System.out.println("[HCAL LVL2 " + functionManager.FMname + "] Executing stoppingAction");
      logger.info("[HCAL LVL2 " + functionManager.FMname + "] Executing stoppingAction");

      // reset the non-async error state handling
      functionManager.ErrorState = false;
      functionManager.FMWasInPausedState = false;
      // set action
      functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT("calculating state")));
      functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("stopping")));

      // stopping the MonLogger application
      if (HandleMonLoggers) {
        try {
          logger.debug("[HCAL LVL2 " + functionManager.FMname + "] Stopping the MonLogger applications ...");

          functionManager.containerMonLogger.execute(HCALInputs.HCALHALT);
        }
        catch (QualifiedResourceContainerException e) {
          String errMessage = "[HCAL LVL2 " + functionManager.FMname + "] Error! QualifiedResourceContainerException: stopping the MonLogger failed ...";
          logger.error(errMessage,e);
          functionManager.sendCMSError(errMessage);
          functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT("Error")));
          functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("oops - technical difficulties ...")));
          if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; return;}
        }
      }

      // stop the triggering
      if (functionManager.FMrole.equals("EvmTrig")) {
        if (functionManager.containerTriggerAdapter!=null) {
          if (!functionManager.containerTriggerAdapter.isEmpty()) {

            {
              String debugMessage = "[HCAL LVL2 " + functionManager.FMname + "] TriggerAdapter for stoppingAction() found- good!";
              logger.debug(debugMessage);
            }

            try {
              functionManager.containerTriggerAdapter.execute(HCALInputs.HCALHALT);
            }
            catch (QualifiedResourceContainerException e) {
              String errMessage = "[HCAL LVL2 " + functionManager.FMname + "] Error! QualifiedResourceContainerException: step 1/2 (TriggerAdapter=Disable) failed ...";
              logger.error(errMessage,e);
              functionManager.sendCMSError(errMessage);
              functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT("Error")));
              functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("oops - technical difficulties ...")));
              if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; return;}
            }

            // waits for the TriggerAdapter to be in the Ready or Failed state, the timeout is 10s
            waitforTriggerAdapter(10);

          }
          else {
            if (functionManager.FMrole.equals("EvmTrig")) {
              String errMessage = "[HCAL LVL2 " + functionManager.FMname + "] Error! No TriggerAdapter found: stoppingAction()";
              logger.error(errMessage);
              functionManager.sendCMSError(errMessage);
              functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT("Error")));
              functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("oops - technical difficulties ...")));
              if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; return;}
            }
          }
        }
      }

      // stop HCAL
      if (!functionManager.containerhcalSupervisor.isEmpty()) {

        {
          String debugMessage = "[HCAL LVL2 " + functionManager.FMname + "] HCAL supervisor for stopRunning found- good!";
          logger.debug(debugMessage);
        }

        if (functionManager.asyncSOAP) { HCALSuperVisorIsOK = false; }  // handle the not async SOAP talking HCAL supervisor when there are async SOAP applications defined
        try {

          // define stop time
          StopTime = new Date();

          functionManager.containerhcalSupervisor.execute(HCALInputs.HCALHALT);
        }
        catch (QualifiedResourceContainerException e) {
          String errMessage = "[HCAL LVL2 " + functionManager.FMname + "] Error! QualifiedResourceContainerException:  step 2/2 (HCAL=Disable) failed ...";
          logger.error(errMessage,e);
          functionManager.sendCMSError(errMessage);
          functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT("Error")));
          functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("oops - technical difficulties ...")));
          if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; return;}
        }
      }
      else if (!functionManager.FMrole.equals("Level2_TCDSLPM")) {
        String errMessage = "[HCAL LVL2 " + functionManager.FMname + "] Error! No HCAL supervisor found: stoppingAction()";
        logger.error(errMessage);
        functionManager.sendCMSError(errMessage);
        functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT("Error")));
        functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT(errMessage)));
        if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; return;}
      }

      // stop the event building gracefully
      if (functionManager.FMrole.equals("EvmTrig")) {

        // check if all events were build before stopping
        if(!isRUBuildersEmpty()) {
          String errMessage = "[HCAL LVL2 " + functionManager.FMname + "] Error! Could not flush the EVMs ...";
          logger.error(errMessage);
          functionManager.sendCMSError(errMessage);
          functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT("Error")));
          functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("oops - technical difficulties ...")));
          if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; return;}
        }

        // include scheduling ToDo

        // stop the FEDStreamers
        if (functionManager.StopFEDStreamer) {
          if (!functionManager.containerFEDStreamer.isEmpty()) {

            logger.warn("[HCAL LVL2 " + functionManager.FMname + "] Stopping FEDStreamers applications ...");

            try {
              logger.debug("[HCAL LVL2 " + functionManager.FMname + "] stopping FEDStreamers ...");
              functionManager.containerFEDStreamer.execute(HCALInputs.FEDSTREAMERSTOP);
            }
            catch (QualifiedResourceContainerException e) {
              String errMessage = "[HCAL LVL2 " + functionManager.FMname + "] Error! QualifiedResourceContainerException: stopping FEDStreamers failed ...";
              logger.error(errMessage,e);
              functionManager.sendCMSError(errMessage);
              functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT("Error")));
              functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("oops - technical difficulties ...")));
              if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; return;}
            }
          }
        }

        // stop the FUEventProcessors
        if (!functionManager.containerFUEventProcessor.isEmpty()) {

          logger.warn("[HCAL LVL2 " + functionManager.FMname + "] Stopping FUEventProcessors applications ...");

          try {
            logger.debug("[HCAL LVL2 " + functionManager.FMname + "] stopping FUEventProcessors ...");
            functionManager.containerFUEventProcessor.execute(HCALInputs.STOP);
          }
          catch (QualifiedResourceContainerException e) {
            String errMessage = "[HCAL LVL2 " + functionManager.FMname + "] Error! QualifiedResourceContainerException: stopping FUEventProcessorss failed ...";
            logger.error(errMessage,e);
            functionManager.sendCMSError(errMessage);
            functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT("Error")));
            functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("oops - technical difficulties ...")));
            if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; return;}
          }
        }

        // stop the FUResourceBrokers
        if (!functionManager.containerFUResourceBroker.isEmpty()) {

          logger.warn("[HCAL LVL2 " + functionManager.FMname + "] Stopping FUResourceBrokers applications ...");

          try {
            logger.debug("[HCAL LVL2 " + functionManager.FMname + "] stopping FUEventProcessors ...");
            functionManager.containerFUResourceBroker.execute(HCALInputs.STOP);
          }
          catch (QualifiedResourceContainerException e) {
            String errMessage = "[HCAL LVL2 " + functionManager.FMname + "] Error! QualifiedResourceContainerException: stopping FUEventProcessors failed ...";
            logger.error(errMessage,e);
            functionManager.sendCMSError(errMessage);
            functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT("Error")));
            functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("oops - technical difficulties ...")));
            if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; return;}
          }
        }

        // stop the PeerTransportATCPs
        if (functionManager.StopATCP) {
          if (!functionManager.containerPeerTransportATCP.isEmpty()) {
            try {
              logger.debug("[HCAL LVL2 " + functionManager.FMname + "] stopping PeerTransportATCPs ...");
              functionManager.containerPeerTransportATCP.execute(HCALInputs.HALT);
            }
            catch (QualifiedResourceContainerException e) {
              String errMessage = "[HCAL LVL2 " + functionManager.FMname + "] Error! QualifiedResourceContainerException: stopping PeerTransportATCPs failed ...";
              logger.error(errMessage,e);
              functionManager.sendCMSError(errMessage);
              functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT("Error")));
              functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("oops - technical difficulties ...")));
              if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; return;}
            }
          }
        }

        // stop the StorageManagers
        if (!functionManager.containerStorageManager.isEmpty()) {

          logger.warn("[HCAL LVL2 " + functionManager.FMname + "] Stopping StorageManagers applications ...");

          try {
            logger.debug("[HCAL LVL2 " + functionManager.FMname + "] stopping StorageManagers ...");
            functionManager.containerStorageManager.execute(HCALInputs.STOP);
          }
          catch (QualifiedResourceContainerException e) {
            String errMessage = "[HCAL LVL2 " + functionManager.FMname + "] Error! QualifiedResourceContainerException: stopping StorageManagers failed ...";
            logger.error(errMessage,e);
            functionManager.sendCMSError(errMessage);
            functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT("Error")));
            functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("oops - technical difficulties ...")));
            if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; return;}
          }
        }
      }

      // leave intermediate state only when not talking to asynchronous applications
      if ( (!functionManager.asyncSOAP) && (!functionManager.ErrorState) ) {
        if (!functionManager.getState().getStateString().equals(HCALStates.CONFIGURED.toString())) {
          functionManager.fireEvent(HCALInputs.SETCONFIGURE);
        }
      }

      //logger.info("[JohnLog] about to call publishRunInfoSummary");
      logger.info("[HCAL LVL2 " + functionManager.FMname +"] about to call publishRunInfoSummary");
      publishRunInfoSummary();
      publishRunInfoSummaryfromXDAQ(); 
      functionManager.HCALRunInfo = null; // make RunInfo ready for the next round of run info to store


      // set actions
      functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT(functionManager.getState().getStateString())));
      functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("stoppingAction executed ...")));

      logger.debug("[HCAL LVL2 " + functionManager.FMname + "] stoppingAction executed ...");

    }
  }

  public void preparingTTSTestModeAction(Object obj) throws UserActionException {
    if (obj instanceof StateNotification) {

      // triggered by State Notification from child resource
      computeNewState((StateNotification) obj);
      return;

    }
    else if (obj instanceof StateEnteredEvent) {
      System.out.println("[HCAL LVL2 " + functionManager.FMname + "] Executing preparingTestModeAction");
      logger.info("[HCAL LVL2 " + functionManager.FMname + "] Executing preparingTestModeAction");

      // reset the non-async error state handling
      functionManager.ErrorState = false;
      functionManager.FMWasInPausedState = false;
      // set actions
      functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT("calculating state")));
      functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("preparingTestMode")));

      String LVL1CfgScript            = "not set";
      String LVL1TTCciControlSequence = "not set";
      String LVL1LTCControlSequence   = "not set";

      // get the parameters of the command
      ParameterSet<CommandParameter> parameterSet = getUserFunctionManager().getLastInput().getParameterSet();

      // check parameter set, if it is not set see if we are in local mode
      if (parameterSet.size()!=0)  {

        // get the HCAL CfgScript from LVL1 if the LVL1 has sent something
        if (parameterSet.get(HCALParameters.HCAL_CFGSCRIPT) != null) {
          LVL1CfgScript = ((StringT)parameterSet.get(HCALParameters.HCAL_CFGSCRIPT).getValue()).getString();
        }
        else {
          logger.warn("[HCAL LVL2 " + functionManager.FMname + "] Warning! Did not receive a LVL1 CfgScript.\nThis is OK if each LVL2 (i.e.also this one) has such a CfgScript defined itself.");
        }

        // get the HCAL TTCciControl from LVL1 if the LVL1 has sent something
        if (parameterSet.get(HCALParameters.HCAL_TTCCICONTROL) != null) {
          LVL1TTCciControlSequence = ((StringT)parameterSet.get(HCALParameters.HCAL_TTCCICONTROL).getValue()).getString();
        }
        else {
          logger.warn("[HCAL LVL2 " + functionManager.FMname + "] Warning! Did not receive a LVL1 TTCci control sequence.\nThis is OK if either each LVL2 (i.e.also this one) has such a sequence defined itself or a TTCci is not used in this config.");
        }

        // get the HCAL LTCControl from LVL1 if the LVL1 has sent something
        if (parameterSet.get(HCALParameters.HCAL_LTCCONTROL) != null) {
          LVL1LTCControlSequence = ((StringT)parameterSet.get(HCALParameters.HCAL_LTCCONTROL).getValue()).getString();
        }
        else {
          logger.warn("[HCAL LVL2 " + functionManager.FMname + "] Warning! Did not receive a LVL1 LTC control sequence.\nThis is OK if either each LVL2 (i.e.also this one) has such a sequence defined itself or a LTC is not used in this config.");
        }

        // set the function manager parameters
        functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.HCAL_RUN_TYPE,new StringT(RunType)));
      }

      if (LVL1CfgScript.equals("not set")) {
        logger.warn("[HCAL LVL2 " + functionManager.FMname + "] Warning! The LVL1CfgScript is not set.\nThis is OK if this LVL2 has such a CfgScript defined itself.");
      }
      else {
        FullCfgScript = LVL1CfgScript;
        logger.info("[HCAL LVL2 " + functionManager.FMname + "] LVL1CfgScript was received.\nHere it is:\n" + FullCfgScript);
      }

      if (LVL1TTCciControlSequence.equals("not set")) {
        logger.warn("[HCAL LVL2 " + functionManager.FMname + "] Warning! The LVL1 TTCci control sequence is not set.\nThis is OK if either each LVL2 (i.e.also this one) has such a sequence defined itself or a TTCci is not used in this config.");
      }
      else {
        FullTTCciControlSequence = LVL1TTCciControlSequence;
        logger.info("[HCAL LVL2 " + functionManager.FMname + "] LVL1 TTCci control sequence was received.\nHere it is:\n" + FullTTCciControlSequence);
      }

      if (LVL1LTCControlSequence.equals("not set")) {
        logger.warn("[HCAL LVL2 " + functionManager.FMname + "] Warning! The LVL1 LTC control sequence is not set.\nThis is OK if either each LVL2 (i.e.also this one) has such a sequence defined itself or a LTC is not used in this config.");
      }
      else {
        FullLTCControlSequence = LVL1LTCControlSequence;
        logger.info("[HCAL LVL2 " + functionManager.FMname + "] LVL1 LTC control sequence was received.\nHere it is:\n" + FullLTCControlSequence);
      }

      // compile CfgScript incorporating the local definitions found in the UserXML
      getCfgScript();

      // compile TTCci control sequence incorporating the local definitions found in the UserXML
      getTTCciControl();

      // compile LTC control sequence incorporating the local definitions found in the UserXML
      getLTCControl();

      // configuring all created HCAL applications by means of sending the RunType to the HCAL supervisor
      sendRunTypeConfiguration(FullCfgScript,FullTTCciControlSequence,FullLTCControlSequence,FullTCDSControlSequence,FullLPMControlSequence,FullPIControlSequence,FedEnableMask,UsePrimaryTCDS);

      if (!functionManager.containerhcalDCCManager.isEmpty()) {

        {
          String debugMessage = "[HCAL LVL2 " + functionManager.FMname + "] at least one DCC (HCAL FED) for preparing the sTTS testing found- good!";
          logger.debug(debugMessage);
        }

        Integer DCC0sourceId=-1;
        Integer DCC1sourceId=-1;

        // get the DCC source ids
        {
          XDAQParameter pam = null;

          for (QualifiedResource qr : functionManager.containerhcalDCCManager.getApplications() ){
            try {
              logger.debug("[HCAL LVL2 " + functionManager.FMname + "] asking for the DCC source ids ...");

              pam =((XdaqApplication)qr).getXDAQParameter();
              pam.select("DCC0");
              pam.get();
              String DCC0sourceId_string = pam.getValue("sourceId");
              DCC0sourceId = new Integer(DCC0sourceId_string);

              logger.debug("[HCAL LVL2 " + functionManager.FMname + "] found DCC0 with source id: " + DCC0sourceId);

              pam =((XdaqApplication)qr).getXDAQParameter();
              pam.select("DCC1");
              pam.get();
              String DCC1sourceId_string = pam.getValue("sourceId");

              if (!DCC1sourceId_string.equals("-1")) {
                DCC1sourceId = new Integer(DCC1sourceId_string);
                logger.debug("[HCAL LVL2 " + functionManager.FMname + "] found DCC1 with source id: " + DCC1sourceId);
              }
              else {
                logger.warn("[HCAL LVL2 " + functionManager.FMname + "] no DCC1 found cause source id is: " + DCC1sourceId);
              }
            }
            catch (XDAQTimeoutException e) {
              String errMessage = "[HCAL LVL2 " + functionManager.FMname + "] Error! XDAQTimeoutException: preparingTTSTestModeAction\n Perhaps the DCC manager application is dead!?";
              logger.error(errMessage,e);
              functionManager.sendCMSError(errMessage);
              functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT("Error")));
              functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("oops - technical difficulties ...")));
              if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; return;}

            }
            catch (XDAQException e) {
              String errMessage = "[HCAL LVL2 " + functionManager.FMname + "] Error! XDAQException: preparingTTSTestModeAction";
              logger.error(errMessage,e);
              functionManager.sendCMSError(errMessage);
              functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT("Error")));
              functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("oops - technical difficulties ...")));
              if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; return;}

            }
          }
        }

        // enable the DCCs for sTTS testing
        {
          Iterator ithcalDCC = functionManager.containerhcalDCCManager.getQualifiedResourceList().iterator();

          XdaqApplication hcalDCC = null;

          while (ithcalDCC.hasNext()) {

            hcalDCC = (XdaqApplication)ithcalDCC.next();

            logger.debug("[HCAL LVL2 " + functionManager.FMname + "] enabling the sTTS test now ...");

            try {
              if (DCC0sourceId!=-1) { hcalDCC.command(getTTSBag("enableTTStest",DCC0sourceId,0,0)); }
              if (DCC1sourceId!=-1) { hcalDCC.command(getTTSBag("enableTTStest",DCC1sourceId,0,0)); }
            }
            catch (XDAQMessageException e) {
              String errMessage = "[HCAL LVL2 " + functionManager.FMname + "] Error! XDAQMessageException: preparingTTSTestModeAction()";
              logger.error(errMessage,e);
              functionManager.sendCMSError(errMessage);
              functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT("Error")));
              functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("oops - technical difficulties ...")));
              if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; return;}

            }
          }
        }

      }
      else {
        String errMessage = "[HCAL LVL2 " + functionManager.FMname + "] Error! No DCC (HCAL FED) found: preparingTTSTestModeAction()";
        logger.error(errMessage);
        functionManager.sendCMSError(errMessage);
        if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; return;}

      }

      // leave intermediate state
      if (!functionManager.ErrorState) {
        functionManager.fireEvent( HCALInputs.SETTTSTEST_MODE );
      }

      // set actions
      functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT(functionManager.getState().getStateString())));
      functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("preparingTestModeAction executed ...")));

      logger.debug("[HCAL LVL2 " + functionManager.FMname + "] preparingTestModeAction executed ...");
    }
  }

  public void testingTTSAction(Object obj) throws UserActionException {
    if (obj instanceof StateNotification) {

      // triggered by State Notification from child resource
      computeNewState((StateNotification) obj);
      return;

    }
    else if (obj instanceof StateEnteredEvent) {
      System.out.println("[HCAL LVL2 " + functionManager.FMname + "] Executing testingTTSAction");
      logger.info("[HCAL LVL2 " + functionManager.FMname + "] Executing testingTTSAction");

      // reset the non-async error state handling
      functionManager.ErrorState = false;
      functionManager.FMWasInPausedState = false;
      // set actions
      functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT("calculating state")));
      functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("testing TTS")));

      Integer  FedId = 0;
      String    mode = "not set";
      String pattern = "0";
      Integer cycles = 0;

      // get the parameters of the command
      ParameterSet<CommandParameter> parameterSet = getUserFunctionManager().getLastInput().getParameterSet();

      // check parameter set
      if (parameterSet.size()==0)  {
        String errMessage = "[HCAL LVL2 " + functionManager.FMname + "] Error! No parameters given with TestTTS command: testingTTSAction";
        logger.error(errMessage);
        functionManager.sendCMSError(errMessage);
        functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT("Error")));
        functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT(errMessage)));
        if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; return;}

      }
      else {

        logger.debug("[HCAL LVL2 " + functionManager.FMname + "] Getting parameters for sTTS test now ...");

        FedId = ((IntegerT)parameterSet.get(HCALParameters.TTS_TEST_FED_ID).getValue()).getInteger();
        mode = ((StringT)parameterSet.get(HCALParameters.TTS_TEST_MODE).getValue()).getString();
        pattern = ((StringT)parameterSet.get(HCALParameters.TTS_TEST_PATTERN).getValue()).getString();
        cycles = ((IntegerT)parameterSet.get(HCALParameters.TTS_TEST_SEQUENCE_REPEAT).getValue()).getInteger();
      }

      Integer ipattern = new Integer(pattern);

      logger.debug("[HCAL LVL2 " + functionManager.FMname + "] Using parameters: FedId=" + FedId + " mode=" + mode + " pattern=" + pattern + " cycles=" + cycles );

      // sending the sTTS test patterns to the DCCs
      if (!functionManager.containerhcalDCCManager.isEmpty()) {

        String debugMessage = "[HCAL LVL2 " + functionManager.FMname + "] at least one DCC (HCAL FED) for sending the sTTS test patterns found- good!";
        logger.debug(debugMessage);

        Iterator ithcalDCC = functionManager.containerhcalDCCManager.getQualifiedResourceList().iterator();

        XdaqApplication hcalDCC = null;

        while (ithcalDCC.hasNext()) {

          hcalDCC = (XdaqApplication)ithcalDCC.next();

          logger.debug("[HCAL LVL2 " + functionManager.FMname + "] sending the sTTS test pattern now ...");

          try {
            if (mode.equals("PATTERN"))    { hcalDCC.command(getTTSBag("sendTTStestpattern",FedId,0,ipattern)); }
            else if (mode.equals("CYCLE")) { hcalDCC.command(getTTSBag("sendTTStestpattern",FedId,cycles,0)); }
            else {
              String errMessage = "[HCAL LVL2 " + functionManager.FMname + "] Error! Invalid sTTS test mode received ...";
              logger.error(errMessage);
              functionManager.sendCMSError(errMessage);
              functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT("Error")));
              functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("oops - technical difficulties ...")));
              if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; return;}

            }
          }
          catch (XDAQMessageException e) {
            String errMessage = "[HCAL LVL2 " + functionManager.FMname + "] Error! XDAQMessageException: testingTTSAction()";
            logger.error(errMessage,e);
            functionManager.sendCMSError(errMessage);
            functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT("Error")));
            functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("oops - technical difficulties ...")));
            if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; return;}

          }
        }
      }
      else {
        String errMessage = "[HCAL LVL2 " + functionManager.FMname + "] Error! No DCC (HCAL FED) found: testingTTSAction()";
        logger.error(errMessage);
        functionManager.sendCMSError(errMessage);
        functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT("Error")));
        functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT(errMessage)));
        if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; return;}

      }

      // leave intermediate state
      if (!functionManager.ErrorState) {
        functionManager.fireEvent( HCALInputs.SETTTSTEST_MODE );
      }

      // set actions
      functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT(functionManager.getState().getStateString())));
      functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("testingTTSAction executed ...")));

      logger.debug("[HCAL LVL2 " + functionManager.FMname + "] testingTTSAction executed ...");
    }
  }

}

