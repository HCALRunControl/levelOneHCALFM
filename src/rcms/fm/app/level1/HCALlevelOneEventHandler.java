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
import java.io.IOException;

import net.hep.cms.xdaqctl.XDAQException;
import net.hep.cms.xdaqctl.XDAQTimeoutException;
import net.hep.cms.xdaqctl.XDAQMessageException;
import rcms.resourceservice.db.Group;
import rcms.fm.resource.qualifiedresource.XdaqExecutive;
import rcms.fm.resource.qualifiedresource.XdaqExecutiveConfiguration;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.soap.SOAPException;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

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
import rcms.fm.resource.qualifiedresource.XdaqApplication;
import rcms.fm.resource.qualifiedresource.XdaqApplicationContainer;
import rcms.fm.resource.qualifiedresource.XdaqExecutive;
import rcms.resourceservice.db.resource.fm.FunctionManagerResource;
import rcms.stateFormat.StateNotification;
import rcms.util.logger.RCMSLogger;
import rcms.util.logsession.LogSessionException;
import rcms.xdaqctl.XDAQParameter;
import rcms.xdaqctl.XDAQMessage;
import rcms.utilities.runinfo.RunNumberData;
import rcms.utilities.runinfo.RunInfoException;
import rcms.statemachine.definition.Input;
import rcms.fm.resource.CommandException;
import rcms.fm.resource.qualifiedresource.FunctionManager;
import rcms.fm.fw.service.parameter.ParameterServiceException;

/**
 * Event Handler class for HCAL Function Managers
 *
 * @author Arno Heister
 *
 */

public class HCALlevelOneEventHandler extends HCALEventHandler {

  static RCMSLogger logger = new RCMSLogger(HCALlevelOneEventHandler.class);

  public HCALlevelOneEventHandler() throws rcms.fm.fw.EventHandlerException {}

  public void init() throws rcms.fm.fw.EventHandlerException {

    functionManager = (HCALFunctionManager) getUserFunctionManager();
    qualifiedGroup  = functionManager.getQualifiedGroup();

    super.init();  // this method calls the base class init and has to be called _after_ the getting of the functionManager

    logger.debug("[HCAL LVL1] init() called: functionManager = " + functionManager );
  }

  public void initAction(Object obj) throws UserActionException {

    if (obj instanceof StateNotification) {

      // triggered by State Notification from child resource
      computeNewState((StateNotification) obj);
      return;

    }
    else if (obj instanceof StateEnteredEvent) {
      setMaskedFMs();
      QualifiedGroup qg = functionManager.getQualifiedGroup();
      List<QualifiedResource> xdaqExecList = qg.seekQualifiedResourcesOfType(new XdaqExecutive());
      // loop over the ecalsup executives to strip the connections
     
      String MaskedResources =  ((StringT)functionManager.getParameterSet().get(HCALParameters.MASKED_RESOURCES).getValue()).getString();
      logger.info("[JohnLog2] " + functionManager.FMname + ": MaskedResources has length " + MaskedResources.length());
      if (MaskedResources.length() > 0) {
        logger.info("[JohnLog2] " + functionManager.FMname + ": about to set the xml for the xdaq executives.");
        for( QualifiedResource qr : xdaqExecList) {
          XdaqExecutive exec = (XdaqExecutive)qr;
          XdaqExecutiveConfiguration config =  exec.getXdaqExecutiveConfiguration();
          String oldExecXML = config.getXml();
          try {
            String newExecXML = stripExecXML(oldExecXML);
            config.setXml(newExecXML);
            logger.info("[JohnLog2] " + functionManager.FMname + ": Just set the xml for executive " + qr.getName());
          }
          catch (UserActionException e) {
            String errMessage = e.getMessage();
            logger.info(errMessage);
            functionManager.sendCMSError(errMessage);
            functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT("Error")));
            functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT(errMessage)));
            if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; return;}
          }
          XdaqExecutiveConfiguration configRetrieved =  exec.getXdaqExecutiveConfiguration();
          System.out.println(qr.getName() + " has edited executive xml: " +  configRetrieved.getXml());
        }
      }
      System.out.println("[HCAL LVL1 " + functionManager.FMname + "] Executing initAction");
      logger.debug("[HCAL LVL1 " + functionManager.FMname + "] Executing initAction");

      // reset the non-async error state handling
      functionManager.ErrorState = false;

      // set actions
      functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT("calculating state")));
      functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("Initializing")));

      // initialize all XDAQ executives
      initXDAQ();

      // start the monitor thread
      System.out.println("[HCAL LVL1 " + functionManager.FMname + "] Starting Monitor thread ...");
      logger.debug("[HCAL LVL1 " + functionManager.FMname + "] Starting Monitor thread ...");
      LevelOneMonitorThread thread1 = new LevelOneMonitorThread();
      thread1.start();

      // start the TriggerAdapter watchdog thread
      System.out.println("[HCAL LVL1 " + functionManager.FMname + "] Starting TriggerAdapter watchdog thread ...");
      logger.debug("[HCAL LVL1 " + functionManager.FMname + "] StartingTriggerAdapter watchdog thread ...");
      TriggerAdapterWatchThread thread3 = new TriggerAdapterWatchThread();
      thread3.start();

      // don't do this weird stuff for the HCAL supervisor - which is not taking async SOAP - for the level 1 FM
      HCALSuperVisorIsOK = true;

      // get the parameters of the command
      ParameterSet<CommandParameter> parameterSet = getUserFunctionManager().getLastInput().getParameterSet();

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

        // set the run type in the function manager parameters
        functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.HCAL_RUN_TYPE,new StringT(RunType)));

        // get the Sid from the init command
        if (parameterSet.get(HCALParameters.SID) != null) {
          Sid = ((IntegerT)parameterSet.get(HCALParameters.SID).getValue()).getInteger();
          functionManager.getParameterSet().put(new FunctionManagerParameter<IntegerT>(HCALParameters.SID,new IntegerT(Sid)));
          functionManager.getParameterSet().put(new FunctionManagerParameter<IntegerT>(HCALParameters.INITIALIZED_WITH_SID,new IntegerT(Sid)));
        }
        else {
          String warnMessage = "[HCAL LVL1 " + functionManager.FMname + "] Did not receive a SID ...";
          logger.warn(warnMessage);
        }

        // get the GlobalConfKey from the init command
        if (parameterSet.get(HCALParameters.GLOBAL_CONF_KEY) != null) {
          GlobalConfKey = ((StringT)parameterSet.get(HCALParameters.GLOBAL_CONF_KEY).getValue()).getString();
          functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.GLOBAL_CONF_KEY,new StringT(GlobalConfKey)));
          functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.INITIALIZED_WITH_GLOBAL_CONF_KEY,new StringT(GlobalConfKey)));
        }
        else {
          String warnMessage = "[HCAL LVL1 " + functionManager.FMname + "] Did not receive a GlobalConfKey ...";
          logger.warn(warnMessage);
        }
      }

      // give the RunType to the controlling FM
      functionManager.RunType = RunType;
      logger.info("[HCAL LVL1 " + functionManager.FMname + "] initAction: We are in " + RunType + " mode ...");

      // prepare run number to be passed to level 2
      ParameterSet<CommandParameter> pSet = new ParameterSet<CommandParameter>();
      pSet.put(new CommandParameter<IntegerT>(HCALParameters.SID, new IntegerT(Sid)));
      pSet.put(new CommandParameter<StringT>(HCALParameters.GLOBAL_CONF_KEY, new StringT(GlobalConfKey)));

      String RunConfigSelected = ((StringT)functionManager.getParameterSet().get(HCALParameters.RUN_CONFIG_SELECTED).getValue()).getString();
      pSet.put(new CommandParameter<StringT>(HCALParameters.RUN_CONFIG_SELECTED, new StringT(RunConfigSelected)));
      String CfgSnippetKeySelected = ((StringT)functionManager.getParameterSet().get(HCALParameters.CFGSNIPPET_KEY_SELECTED).getValue()).getString();
      pSet.put(new CommandParameter<StringT>(HCALParameters.CFGSNIPPET_KEY_SELECTED, new StringT(RunConfigSelected)));
      String xmlString = "<userXML>" + ((FunctionManagerResource)functionManager.getQualifiedGroup().getGroup().getThisResource()).getUserXml() + "</userXML>";
      logger.info("[JohnLog2] " + functionManager.FMname + ": Started out with masked resources: " + MaskedResources);
      try {
        DocumentBuilder docBuilder;
        logger.info("[JohnLog2] " + functionManager.FMname + ": The xmlString was: " + xmlString );

        docBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        InputSource inputSource = new InputSource();
        inputSource.setCharacterStream(new StringReader(xmlString));
        Document userXML = docBuilder.parse(inputSource);
        userXML.getDocumentElement().normalize();

        NodeList nodes = null;
        nodes = userXML.getDocumentElement().getElementsByTagName("RunConfig");
        logger.info("[JohnLog2] " + functionManager.FMname + "RunConfigSelected was " + RunConfigSelected);
        for (int i=0; i < nodes.getLength(); i++) {
					logger.info("[JohnLog2] " + functionManager.FMname + ": In RunConfig element " + Integer.toString(i) + " with name " + nodes.item(i).getAttributes().getNamedItem("name").getNodeValue() + " found maskedapp nodevalue " + nodes.item(i).getAttributes().getNamedItem("maskedapps").getNodeValue());
          if (nodes.item(i).getAttributes().getNamedItem("name").getNodeValue().equals(CfgSnippetKeySelected)) {
            String maskedAppsNodeContent =  nodes.item(i).getAttributes().getNamedItem("maskedapps").getNodeValue();
            if (maskedAppsNodeContent != null && !maskedAppsNodeContent.isEmpty()) {
              String MaskedResourcesFromXML= maskedAppsNodeContent.replace("|",";");
              MaskedResources += MaskedResourcesFromXML;
              logger.info("[JohnLog2] " + functionManager.FMname + ": From selecting the RunConfig " + RunConfigSelected + ", got additional masked applications " + nodes.item(i).getAttributes().getNamedItem("maskedapps").getNodeValue());
            }
          }
        } 
        logger.info("[JohnLog2] " + functionManager.FMname + ": Ended up with the list of masked resources: " + MaskedResources);
      }
      catch (ParserConfigurationException | SAXException | IOException e) {
        logger.error("[JohnLog2] " + functionManager.FMname + ": Got an error when trying to manipulate the userXML: " + e.getMessage());
      }
      logger.info("[JohnLog2] " + functionManager.FMname + ": About to set the initial list of masked resources: " + MaskedResources );
      functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.MASKED_RESOURCES, new StringT(MaskedResources)));
      pSet.put(new CommandParameter<StringT>(HCALParameters.MASKED_RESOURCES, new StringT(MaskedResources)));

      // prepare command plus the parameters to send
      Input initInput = new Input(HCALInputs.INITIALIZE.toString());
      initInput.setParameters( pSet );

      if (!functionManager.containerFMChildren.isEmpty()) {

        Iterator it = functionManager.containerFMChildren.getQualifiedResourceList().iterator();
        FunctionManager fmChild = null;
        while (it.hasNext()) {
          fmChild = (FunctionManager) it.next();
          if (fmChild.isActive()) {
            try {
              logger.info("[HCAL LVL1 " + functionManager.FMname + "] Will send " + initInput + " to FM named: " + fmChild.getResource().getName().toString() + "\nThe role is: " + fmChild.getResource().getRole().toString() + "\nAnd the URI is: " + fmChild.getResource().getURI().toString());
              fmChild.execute(initInput);
            }
            catch (CommandException e) {
              String errMessage = "[HCAL LVL1 " + functionManager.FMname + "] Error! for FM with role: " + fmChild.getRole().toString() + ", CommandException: sending: " + initInput + " failed ...";
              logger.error(errMessage,e);
              functionManager.sendCMSError(errMessage);
              functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT("Error")));
              functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("oops - problems ...")));
              if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; return;}
            }
          }
        }

      }
      else {
        if (!functionManager.ErrorState) {
          logger.debug("[HCAL LVL1 " + functionManager.FMname + "] fireEvent: " + HCALInputs.SETHALT);
          functionManager.fireEvent(HCALInputs.SETHALT);
        }
      }

      // set actions
      functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT(functionManager.getState().getStateString())));
      functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("initAction executed ...")));

      // publish the initialization time for this FM to the paramterSet
      functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.HCAL_TIME_OF_FM_START, new StringT(functionManager.utcFMtimeofstart)));

      logger.info("[HCAL LVL1 " + functionManager.FMname + "] initAction executed ...");
    }
  }

  public void resetAction(Object obj) throws UserActionException {

    if (obj instanceof StateNotification) {

      // triggered by State Notification from child resource
      computeNewState((StateNotification) obj);
      return;

    }
    else if (obj instanceof StateEnteredEvent) {
      System.out.println("[HCAL LVL1 " + functionManager.FMname + "] Executing resetAction");
      logger.debug("[HCAL LVL1 " + functionManager.FMname + "] Executing resetAction");

      publishRunInfoSummary();
      publishRunInfoSummaryfromXDAQ(); 
      functionManager.HCALRunInfo = null; // make RunInfo ready for the next round of run info to store

      // reset the non-async error state handling
      functionManager.ErrorState = false;

      // set actions
      functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT("calculating state")));
      functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("Resetting")));
      functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.SUPERVISOR_ERROR,new StringT("")));

      // kill all XDAQ executives
      //destroyXDAQ();

      // init all XDAQ executives
      //initXDAQ();

      if (!functionManager.containerFMChildren.isEmpty()) {

        // reset all FMs
        try {
          logger.debug("[HCAL LVL1 " + functionManager.FMname + "] Found FM childs - good! fireEvent: " + HCALInputs.RESET);
          functionManager.containerFMChildren.execute(HCALInputs.RESET);
        }
        catch (QualifiedResourceContainerException e) {
          String errMessage = "[HCAL LVL1 " + functionManager.FMname + "] Error! QualifiedResourceContainerException: sending: " + HCALInputs.RESET + " failed ...";
          logger.error(errMessage,e);
          functionManager.sendCMSError(errMessage);
          functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT("Error")));
          functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("oops - problems ...")));
          if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; return;}
        }
      }
      else {
        if (!functionManager.ErrorState) {
          logger.debug("[HCAL LVL1 " + functionManager.FMname + "] fireEvent: " + HCALInputs.SETHALT);
          functionManager.fireEvent(HCALInputs.SETHALT);
        }
      }

      // set actions
      functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT(functionManager.getState().getStateString())));
      functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("resetAction executed ...")));

      logger.info("[HCAL LVL1 " + functionManager.FMname + "] resetAction executed ...");
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
      System.out.println("[HCAL LVL1 " + functionManager.FMname + "] Executing recoverAction");
      logger.info("[HCAL LVL1 " + functionManager.FMname + "] Executing recoverAction");

      // reset the non-async error state handling
      functionManager.ErrorState = false;

      // set actions
      functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT("calculating state")));
      functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("recovering")));

      if (!functionManager.containerFMChildren.isEmpty()) {
        // recover all FMs
        try {
          logger.debug("[HCAL LVL1 " + functionManager.FMname + "] Found FM childs - good! fireEvent: " + HCALInputs.RECOVER);
          functionManager.containerFMChildren.execute(HCALInputs.RECOVER);
        }
        catch (QualifiedResourceContainerException e) {
          String errMessage = "[HCAL LVL1 " + functionManager.FMname + "] Error! QualifiedResourceContainerException: sending: " + HCALInputs.RECOVER + " failed ...";
          logger.error(errMessage,e);
          functionManager.sendCMSError(errMessage);
          functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT("Error")));
          functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("oops - problems ...")));
          if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; return;}
        }
      }
      else {
        if (!functionManager.ErrorState) {
          logger.debug("[HCAL LVL1 " + functionManager.FMname + "] fireEvent: " + HCALInputs.SETHALT);
          functionManager.fireEvent(HCALInputs.SETHALT);
        }
      }

      // set actions
      functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT(functionManager.getState().getStateString())));
      functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("recoverAction executed ...")));

      logger.info("[HCAL LVL1 " + functionManager.FMname + "] recoverAction executed ...");
    }
  }

  public void configureAction(Object obj) throws UserActionException {

    if (obj instanceof StateNotification) {

      // triggered by State Notification from child resource
      computeNewState((StateNotification) obj);
      return;

    }
    else if (obj instanceof StateEnteredEvent) {
      System.out.println("[HCAL LVL1 " + functionManager.FMname + "] Executing configureAction");
      logger.info("[HCAL LVL1 " + functionManager.FMname + "] Executing configureAction");

      // reset the non-async error state handling
      functionManager.ErrorState = false;

      // set actions
      functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT("calculating state")));
      functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("configuring")));

      // get the parameters of the command
      ParameterSet<CommandParameter> parameterSet = getUserFunctionManager().getLastInput().getParameterSet();

      // check parameter set, if it is not set see if we are in local mode
      if (parameterSet.size()==0)  {
        RunType = "local";
        functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.HCAL_RUN_TYPE,new StringT(RunType)));
        getFedEnableMask();
        FedEnableMask = ((StringT)functionManager.getParameterSet().get(HCALParameters.FED_ENABLE_MASK).getValue()).getString();
        logger.info("[HCAL LVL1 " + functionManager.FMname + "] The FED_ENABLE_MASK retrieved by the level-1 is: " + FedEnableMask);
      }
      else {
        RunType = "global";

        // set the run type in the function manager parameters
        functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.HCAL_RUN_TYPE,new StringT(RunType)));

        // get the run key from the configure command
        if (parameterSet.get(HCALParameters.RUN_KEY) != null) {
          RunKey = ((StringT)parameterSet.get(HCALParameters.RUN_KEY).getValue()).getString();
          // set the run key in the function manager parameters
          functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.RUN_KEY,new StringT(RunKey)));
          functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.CONFIGURED_WITH_RUN_KEY,new StringT(RunKey)));

        }
        else {
          String warnMessage = "[HCAL LVL1 " + functionManager.FMname + "] Did not receive a run key.\nThis is probably OK for normal HCAL LVL1 operations ...";
          logger.warn(warnMessage);
        }

        // get the tpg key from the configure command
        if (parameterSet.get(HCALParameters.TPG_KEY) != null) {
          TpgKey = ((StringT)parameterSet.get(HCALParameters.TPG_KEY).getValue()).getString();
          functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.CONFIGURED_WITH_TPG_KEY,new StringT(TpgKey)));
          String warnMessage = "[HCAL LVL1 " + functionManager.FMname + "] Received a L1 TPG key: " + TpgKey;
          logger.warn(warnMessage);
        }
        else {
          String warnMessage = "[HCAL LVL1 " + functionManager.FMname + "] Did not receive a L1 TPG key.\nThis is only OK for HCAL local run operations or if HCAL is out of the trigger for global runs ...";
          logger.warn(warnMessage);
        }

        // get the run number from the configure command and cache this one
        if (parameterSet.get(HCALParameters.RUN_NUMBER) != null) {
          functionManager.CachedRunNumber = ((IntegerT)parameterSet.get(HCALParameters.RUN_NUMBER).getValue()).getInteger();
          functionManager.getParameterSet().put(new FunctionManagerParameter<IntegerT>(HCALParameters.CONFIGURED_WITH_RUN_NUMBER,new IntegerT(functionManager.CachedRunNumber)));
          logger.info("[HCAL LVL1 " + functionManager.FMname + "] Did receive a run number during the configureAction().\nThe run number received was: " + functionManager.CachedRunNumber);
        }
        else {
          logger.info("[HCAL LVL1 " + functionManager.FMname + "] Did not receive a run number during the configureAction().\nThis is probably OK for normal HCAL LVL1 operations ...");
        }

        // get the info from the LVL1 if special actions due to a central CMS clock source change are indicated
        ClockChanged = false;
        if (parameterSet.get(HCALParameters.CLOCK_CHANGED) != null) {
          ClockChanged = ((BooleanT)parameterSet.get(HCALParameters.CLOCK_CHANGED).getValue()).getBoolean();
          functionManager.getParameterSet().put(new FunctionManagerParameter<BooleanT>(HCALParameters.CLOCK_CHANGED,new BooleanT(ClockChanged)));
          if (ClockChanged) {
            logger.warn("[HCAL LVL1 " + functionManager.FMname + "] Did receive a request to perform special actions due to central CMS clock source change during the configureAction().\nThe ClockChange is: " + ClockChanged);
          }
          else {
            logger.debug("[HCAL LVL1 " + functionManager.FMname + "] Did not receive a request to perform special actions due to central CMS clock source change during the configureAction().\nThe ClockChange is: " + ClockChanged);
          }

        }
        else {
          logger.info("[HCAL LVL1 " + functionManager.FMname + "] Did not receive any request to perform special actions due to a central CMS clock source change during the configureAction().\nThis is (probably) OK for HCAL local runs ...");
        }

        UseResetForRecover = true;
        if (parameterSet.get(HCALParameters.USE_RESET_FOR_RECOVER) != null) {
          UseResetForRecover = ((BooleanT)parameterSet.get(HCALParameters.USE_RESET_FOR_RECOVER).getValue()).getBoolean();
          functionManager.getParameterSet().put(new FunctionManagerParameter<BooleanT>(HCALParameters.USE_RESET_FOR_RECOVER, new BooleanT(UseResetForRecover)));
        }

        UsePrimaryTCDS = true;
        if (parameterSet.get(HCALParameters.USE_PRIMARY_TCDS) != null) {
          UsePrimaryTCDS = ((BooleanT)parameterSet.get(HCALParameters.USE_PRIMARY_TCDS).getValue()).getBoolean();
          functionManager.getParameterSet().put(new FunctionManagerParameter<BooleanT>(HCALParameters.USE_PRIMARY_TCDS, new BooleanT(UsePrimaryTCDS)));
        }

        // Give the supervisor error to the level1FM
        SupervisorError = "";
        if (parameterSet.get(HCALParameters.SUPERVISOR_ERROR) != null) {
          SupervisorError = ((StringT)parameterSet.get(HCALParameters.SUPERVISOR_ERROR).getValue()).getString();
          functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.SUPERVISOR_ERROR, new StringT(SupervisorError)));
        }

        // get the FED list from the configure command
        if (parameterSet.get(HCALParameters.FED_ENABLE_MASK) != null) {
          FedEnableMask = ((StringT)parameterSet.get(HCALParameters.FED_ENABLE_MASK).getValue()).getString();
          functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.FED_ENABLE_MASK,new StringT(FedEnableMask)));
          functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.CONFIGURED_WITH_FED_ENABLE_MASK,new StringT(TpgKey)));

          functionManager.HCALFedList = getEnabledHCALFeds(FedEnableMask);

          logger.info("[HCAL LVL1 " + functionManager.FMname + "] ... did receive a FED list during the configureAction().");
        }
        else {
          logger.warn("[HCAL LVL1 " + functionManager.FMname + "] Did not receive a FED list during the configureAction() - this is bad!");
        }
      }

      // give the RunType to the controlling FM
      functionManager.RunType = RunType;
      logger.info("[HCAL LVL1 " + functionManager.FMname + "] configureAction: We are in " + RunType + " mode ...");

      // switch parsing, etc. of the zero supression HCAL CFG snippet on or off, special zero suppression handling ...
      if (RunKey.equals("noZS") || RunKey.equals("VdM-noZS")) {
        logger.warn("[HCAL LVL1 " + functionManager.FMname + "] The zero supression is switched off ...");
        functionManager.useZS        = false;
        functionManager.useSpecialZS = false;
      }
      else if (RunKey.equals("test-ZS") || RunKey.equals("VdM-test-ZS")) {
        logger.warn("[HCAL LVL1 " + functionManager.FMname + "] The special zero suppression is switched on i.e. not blocked by this FM ...");
        functionManager.useZS        = false;
        functionManager.useSpecialZS = true;
      }
      else if (RunKey.equals("ZS") || RunKey.equals("VdM-ZS")) {
        logger.warn("[HCAL LVL1 " + functionManager.FMname + "] The zero suppression is switched on i.e. not blocked by this FM ...");
        functionManager.useZS        = true;
        functionManager.useSpecialZS = false;
      }
      else {
        if (!RunKey.equals("")) {
          String errMessage = "[HCAL LVL1 " + functionManager.FMname + "] Do not understand how to handle this RUN_KEY: " + RunKey + " - please check the RS3 config in use!\nPerhaps the wrong key was given by the CDAQ shifter!?";
          logger.error(errMessage);
          functionManager.sendCMSError(errMessage);
          functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT("Error")));
          functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("oops - problems ...")));
          if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; return; }
        }
      }

      // check the RUN_KEY for VdM snippets, etc. request
      if (RunKey.equals("VdM-noZS") || RunKey.equals("VdM-test-ZS") || RunKey.equals("VdM-ZS")) {
        logger.warn("[HCAL LVL1 " + functionManager.FMname + "] Special VdM scan snippets, etc. were enabled by the RUN_KEY for this FM.\nThe RUN_KEY given is: " + RunKey);
        functionManager.useVdMSnippet = true;
      }
      else {
        logger.debug("[HCAL LVL1 " + functionManager.FMname + "] No special VdM scan snippets, etc. enabled for this FM.\nThe RUN_KEY given is: " + RunKey);
      }

      // check if the RUN_KEY has changed

      Boolean ChangedKeysDetected = false;

      if (functionManager.VeryFirstConfigure  && !functionManager.containerFMChildren.isEmpty()) {

        logger.warn("[HCAL LVL1 " + functionManager.FMname + "] Found attached FM childs will try to check their RUN_KEY ...");

        Iterator it = functionManager.containerFMChildren.getQualifiedResourceList().iterator();
        FunctionManager fmChild = null;
        functionManager.VeryFirstConfigure = false;
      }

      if (RunKey.equals(CachedRunKey)) {
        logger.debug("[HCAL LVL1 " + functionManager.FMname + "] The RUN_KEY did not change for this run ...");
      }
      else {
        ChangedKeysDetected = true;
        logger.warn("[HCAL LVL1 " + functionManager.FMname + "] The RUN_KEY has changed for this run.");
      }

      if (TpgKey.equals(CachedTpgKey)) {
        logger.debug("[HCAL LVL1 " + functionManager.FMname + "] The TPG_KEY did not change for this run ...");
      }
      else {
        ChangedKeysDetected = true;
        logger.warn("[HCAL LVL1 " + functionManager.FMname + "] The TPG_KEY has changed for this run.");
      }

      CachedRunKey = RunKey;
      CachedTpgKey = TpgKey;

      // reset previously computed config scripts to force a re-compute for each new "Configuring" transition
      FullCfgScript            = "not set";
      FullTTCciControlSequence = "not set";
      FullLTCControlSequence   = "not set";
      FullTCDSControlSequence   = "not set";
      FullLPMControlSequence   = "not set";
      FullPIControlSequence = "not set";

      // compile CfgScript from UserXML to be sent to controlled LVL2 FMs
      getCfgScript();

      if (TpgKey!=null && TpgKey!="NULL") {

        FullCfgScript += "\n### BEGIN TPG key add from HCAL FM named: " + functionManager.FMname + "\n";
        FullCfgScript += "# A HcalTriggerKey was retrieved by this FM and will be added by the LVL2 FMs.";
        FullCfgScript += "\n### END TPG key add from HCAL FM named: " + functionManager.FMname + "\n";

        logger.warn("[HCAL LVL1 " + functionManager.FMname + "] added the received TPG_KEY: " + TpgKey + " as HTR snippet to the full CfgScript ...");
        logger.debug("[HCAL LVL1 " + functionManager.FMname + "] FullCfgScript with added received TPG_KEY: " + TpgKey + " as HTR snippet.\nHere it is:\n" + FullCfgScript);

      }
      else {
        logger.warn("[HCAL LVL1 " + functionManager.FMname + "] Warning! Did not receive any TPG_KEY.\nPerhaps this is OK for local runs ... ");

        if (!RunType.equals("local")) {
          logger.error("[HCAL LVL1 " + functionManager.FMname + "] Error! For global runs we should have received a TPG_KEY.\nPlease check if HCAL is in the trigger.\n If HCAL is in the trigger and you see this message please call an expert - this is bad!!");
        }
      }

      // get TTCci control sequence to be sent to controlled LVL2 FMs
      getTTCciControl();

      // get LTC control sequence to be sent to controlled LVL2 FMs
      getLTCControl();

      // get TCDS control sequence to be sent to controlled LVL2 FMs
      getTCDSControl();

      // get LPM control sequence to be sent to controlled LVL2 FMs
      getLPMControl();

      // get PI control sequence to be sent to controlled LVL2 FMs
      getPIControl();

      // prepare run mode to be passed to level 2
      ParameterSet<CommandParameter> pSet = new ParameterSet<CommandParameter>();
      pSet.put(new CommandParameter<IntegerT>(HCALParameters.RUN_NUMBER, new IntegerT(functionManager.RunNumber)));
      pSet.put(new CommandParameter<StringT>(HCALParameters.HCAL_RUN_TYPE, new StringT(RunType)));
      pSet.put(new CommandParameter<StringT>(HCALParameters.RUN_KEY, new StringT(RunKey)));
      pSet.put(new CommandParameter<StringT>(HCALParameters.TPG_KEY, new StringT(TpgKey)));
      pSet.put(new CommandParameter<StringT>(HCALParameters.FED_ENABLE_MASK, new StringT(FedEnableMask)));
      pSet.put(new CommandParameter<StringT>(HCALParameters.HCAL_CFGSCRIPT, new StringT(FullCfgScript)));
      pSet.put(new CommandParameter<StringT>(HCALParameters.HCAL_TTCCICONTROL, new StringT(FullTTCciControlSequence)));
      pSet.put(new CommandParameter<StringT>(HCALParameters.HCAL_LTCCONTROL, new StringT(FullLTCControlSequence)));
      pSet.put(new CommandParameter<StringT>(HCALParameters.HCAL_TCDSCONTROL, new StringT(FullTCDSControlSequence)));
      pSet.put(new CommandParameter<StringT>(HCALParameters.HCAL_LPMCONTROL, new StringT(FullLPMControlSequence)));
      pSet.put(new CommandParameter<BooleanT>(HCALParameters.CLOCK_CHANGED, new BooleanT(ClockChanged)));
      pSet.put(new CommandParameter<BooleanT>(HCALParameters.USE_RESET_FOR_RECOVER, new BooleanT(UseResetForRecover)));
      pSet.put(new CommandParameter<StringT>(HCALParameters.HCAL_PICONTROL, new StringT(FullPIControlSequence)));
      pSet.put(new CommandParameter<BooleanT>(HCALParameters.USE_PRIMARY_TCDS, new BooleanT(UsePrimaryTCDS)));
      pSet.put(new CommandParameter<StringT>(HCALParameters.SUPERVISOR_ERROR, new StringT(SupervisorError)));

      // prepare command plus the parameters to send
      Input configureInput= new Input(HCALInputs.CONFIGURE.toString());
      configureInput.setParameters( pSet );

      if (!functionManager.containerFMChildren.isEmpty()) {
        logger.debug("[HCAL LVL1 " + functionManager.FMname + "] Found FM childs - good! fireEvent: " + configureInput);

        // include scheduling ToDo

        // check if some partitions have to be configured first
        if (SpecialFMsAreControlled) {
          {
            Iterator it = functionManager.containerFMChildren.getQualifiedResourceList().iterator();
            FunctionManager fmChild = null;
            while (it.hasNext()) {
              if (fmChild.isActive()) {
                fmChild = (FunctionManager) it.next();

                if (fmChild.getRole().toString().equals("Level2_Priority_1") ) {
                  try {
                    logger.debug("[HCAL LVL1 " + functionManager.FMname + "] Found priority FM childs - good! fireEvent: " + configureInput);
                    fmChild.execute(configureInput);
                  }
                  catch (CommandException e) {
                    String errMessage = "[HCAL LVL1 " + functionManager.FMname + "] Error! CommandException: sending: " + configureInput + " failed ...";
                    logger.error(errMessage,e);
                    functionManager.sendCMSError(errMessage);
                    functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT("Error")));
                    functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("oops - problems ...")));
                    if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; return;}
                  }
                }
              }
            }
          }

          while (!waitforFMswithRole("Level2_Priority_1",HCALStates.CONFIGURED.toString())) {
            try { Thread.sleep(1000); }
            catch (Exception ignored) {}
            logger.debug("[HCAL LVL1 " + functionManager.FMname + "] ... waiting for FMs to be in the state "+ HCALStates.CONFIGURED.toString() + "\nAll FMs which have the role: Level2_Priority_1.");
          }

          {
            Iterator it = functionManager.containerFMChildren.getQualifiedResourceList().iterator();
            FunctionManager fmChild = null;
            while (it.hasNext()) {
              fmChild = (FunctionManager) it.next();
              if (fmChild.isActive()) {
                if ( fmChild.getRole().toString().equals("Level2_Priority_2") ) {
                  try {
                    logger.debug("[HCAL LVL1 " + functionManager.FMname + "] Found priority FM childs - good! fireEvent: " + configureInput);
                    fmChild.execute(configureInput);
                  }
                  catch (CommandException e) {
                    String errMessage = "[HCAL LVL1 " + functionManager.FMname + "] Error! CommandException: sending: " + configureInput + " failed ...";
                    logger.error(errMessage,e);
                    functionManager.sendCMSError(errMessage);
                    functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT("Error")));
                    functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("oops - problems ...")));
                    if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; return;}
                  }
                }
              }
            }
          }

          while (!waitforFMswithRole("Level2_Priority_2",HCALStates.CONFIGURED.toString())) {
            try { Thread.sleep(1000); }
            catch (Exception ignored) {}
            logger.debug("[HCAL LVL1 " + functionManager.FMname + "] ... waiting for FMs to be in the state "+ HCALStates.CONFIGURED.toString() + "\nAll FMs which have the role: Level2_Priority_2.");
          }


          Boolean needtowait = false;

          // now configure the rest of the HCAL FMs in parallel
          {
            Iterator it = functionManager.containerFMChildren.getQualifiedResourceList().iterator();
            FunctionManager fmChild = null;
            while (it.hasNext()) {
              fmChild = (FunctionManager) it.next();
              if (fmChild.isActive()) {
                if ( !(fmChild.getRole().toString().equals("Level2_Priority_1") || fmChild.getRole().toString().equals("Level2_Priority_2"))) {
                  try {
                    logger.debug("[HCAL LVL1 " + functionManager.FMname + "] Found non priority FM childs - good! fireEvent: " + configureInput);
                    fmChild.execute(configureInput);
                  }
                  catch (CommandException e) {
                    String errMessage = "[HCAL LVL1 " + functionManager.FMname + "] Error! CommandException: sending: " + configureInput + " failed ...";
                    logger.error(errMessage,e);
                    functionManager.sendCMSError(errMessage);
                    functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT("Error")));
                    functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("oops - problems ...")));
                    if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; return;}
                  }
                }
              }
            }
          }

          while (!waitforFMswithNotTheRole("Level2_Priority_1","Level2_Priority_2","dummy","dummy","dummy",HCALStates.CONFIGURED.toString())) {
            try { Thread.sleep(1000); }
            catch (Exception ignored) {}
            logger.debug("[HCAL LVL1 " + functionManager.FMname + "] ... waiting for all FMs to be in the state "+ HCALStates.CONFIGURED.toString() + "\n All FMs which do not have the role: Level2_Priority_1, Level2_Priority_2, or Level2_Laser");
          }

          if (functionManager.FMsWereConfiguredOnce) {
            if (!functionManager.ErrorState) {
              logger.debug("[HCAL LVL1 " + functionManager.FMname + "] fireEvent: " + HCALInputs.SETCONFIGURE);
              if (!functionManager.getState().getStateString().equals(HCALStates.CONFIGURED.toString())) {
                functionManager.fireEvent(HCALInputs.SETCONFIGURE);
              }
            }
          }

        }
        else {

          functionManager.FMsWereConfiguredOnce = true;

          Boolean needtowait = false;


          {
            Iterator it = functionManager.containerFMChildren.getQualifiedResourceList().iterator();
            FunctionManager fmChild = null;
            while (it.hasNext()) {
              fmChild = (FunctionManager) it.next();
              if (fmChild.isActive()) {
                try {
                  logger.debug("[HCAL LVL1 " + functionManager.FMname + "] Found FM childs - good! fireEvent: " + configureInput);
                  fmChild.execute(configureInput);
                }
                catch (CommandException e) {
                  String errMessage = "[HCAL LVL1 " + functionManager.FMname + "] Error! CommandException: sending: " + configureInput + " failed ...";
                  logger.error(errMessage,e);
                  functionManager.sendCMSError(errMessage);
                  functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT("Error")));
                  functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("oops - problems ...")));
                  if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; return;}
                }
              }
            }
          }

          while (!waitforFMswithNotTheRole("dummy","dummy","dummy","dummy","dummy",HCALStates.CONFIGURED.toString())) {
            try { Thread.sleep(1000); }
            catch (Exception ignored) {}
            logger.debug("[HCAL LVL1 " + functionManager.FMname + "] ... waiting for all FMs to be in the state "+ HCALStates.CONFIGURED.toString());
          }
        }

      }
      else {
        if (!functionManager.ErrorState) {
          logger.debug("[HCAL LVL1 " + functionManager.FMname + "] fireEvent: " + HCALInputs.SETCONFIGURE);
          if (!functionManager.getState().getStateString().equals(HCALStates.CONFIGURED.toString())) {
            functionManager.fireEvent(HCALInputs.SETCONFIGURE);
          }
        }
      }

      // set actions
      functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT(functionManager.getState().getStateString())));
      functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("configureAction executed ... - we're close ...")));

      logger.info("[HCAL LVL1 " + functionManager.FMname + "] configureAction executed ... - were are close ...");
    }
  }

  public void startAction(Object obj) throws UserActionException {

    if (obj instanceof StateNotification) {

      // triggered by State Notification from child resource
      computeNewState((StateNotification) obj);
      return;

    }
    else if (obj instanceof StateEnteredEvent) {
      System.out.println("[HCAL LVL1 " + functionManager.FMname + "] Executing startAction");
      logger.info("[HCAL LVL1 " + functionManager.FMname + "] Executing startAction");

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
          String errMessage = "[HCAL LVL1 " + functionManager.FMname + "] Error! command parameter problem for the startAction ...";
          logger.error(errMessage);
          functionManager.sendCMSError(errMessage);
          functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT("Error")));
          functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("oops - problems ...")));
          if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; return;}
        }
        else {
          logger.info("[HCAL LVL1 " + functionManager.FMname + "] startAction: We are in local mode ...");

          // determine run number and run sequence number and overwrite what was set before
          if (OfficialRunNumbers) {

            RunNumberData rnd = getOfficialRunNumber();

            functionManager.RunNumber    = rnd.getRunNumber();
            RunSeqNumber = rnd.getSequenceNumber();

            functionManager.getParameterSet().put(new FunctionManagerParameter<IntegerT>(HCALParameters.RUN_NUMBER, new IntegerT(functionManager.RunNumber)));
            functionManager.getParameterSet().put(new FunctionManagerParameter<IntegerT>(HCALParameters.RUN_SEQ_NUMBER, new IntegerT(RunSeqNumber)));

            logger.info("[HCAL LVL1 " + functionManager.FMname + "] ... run number: " + functionManager.RunNumber + ", SequenceNumber: " + RunSeqNumber);

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
          String errMessage = "[HCAL LVL1 " + functionManager.FMname + "] Error! Did not receive a run number ...";
          logger.error(errMessage);
          functionManager.sendCMSError(errMessage);
          functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT("Error")));
          functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("oops - problems ...")));
          if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; return;}
        }

        // get the run sequence number from the start command
        if (parameterSet.get(HCALParameters.RUN_SEQ_NUMBER) != null) {
          RunSeqNumber = ((IntegerT)parameterSet.get(HCALParameters.RUN_SEQ_NUMBER).getValue()).getInteger();
          functionManager.getParameterSet().put(new FunctionManagerParameter<IntegerT>(HCALParameters.RUN_SEQ_NUMBER, new IntegerT(RunSeqNumber)));
        }
        else {
          if (RunType.equals("local")) { logger.warn("[HCAL LVL1 " + functionManager.FMname + "] Warning! Did not receive a run sequence number.\nThis is OK for global runs."); }
        }

        // get the number of requested events
        if (parameterSet.get(HCALParameters.NUMBER_OF_EVENTS) != null) {
          TriggersToTake = ((IntegerT)parameterSet.get(HCALParameters.NUMBER_OF_EVENTS).getValue()).getInteger();
          functionManager.getParameterSet().put(new FunctionManagerParameter<IntegerT>(HCALParameters.NUMBER_OF_EVENTS,new IntegerT(TriggersToTake)));
        }
        else {
          if (RunType.equals("local")) { logger.warn("[HCAL LVL1 " + functionManager.FMname + "] Warning! Did not receive the number of events to take.\nThis is OK for global runs."); }

          // fix for global run configs running a local HCAL DAQ partition like the uTCA one
          TriggersToTake = ((IntegerT)functionManager.getParameterSet().get(HCALParameters.NUMBER_OF_EVENTS).getValue()).getInteger();
        }

      }

      // prepare run number,etc. to be passed to level 2
      ParameterSet<CommandParameter> pSet = new ParameterSet<CommandParameter>();
      pSet.put(new CommandParameter<IntegerT>(HCALParameters.RUN_NUMBER, new IntegerT(functionManager.RunNumber)));
      pSet.put(new CommandParameter<IntegerT>(HCALParameters.RUN_SEQ_NUMBER, new IntegerT(RunSeqNumber)));
      pSet.put(new CommandParameter<IntegerT>(HCALParameters.NUMBER_OF_EVENTS, new IntegerT(TriggersToTake)));

      // prepare command plus the parameters to send
      Input startInput= new Input(HCALInputs.START.toString());
      startInput.setParameters( pSet );

      if (!functionManager.containerFMChildren.isEmpty()) {
        logger.debug("[HCAL LVL1 " + functionManager.FMname + "] Found FM childs - good! fireEvent: " + startInput);

        if (SpecialFMsAreControlled) {

          // reset the notification thread
          NotifiedControlledFMs = false;

          // start FMs e.g. from ECAL or FMs with Priority (HCAL_RCTMaster, HCAL_HCALMaster) which have to be running when the triggers are enabled
          // start Level2_Priority_1
          {
            Iterator it = functionManager.containerFMChildren.getQualifiedResourceList().iterator();
            FunctionManager fmChild = null;
            while (it.hasNext()) {
              fmChild = (FunctionManager) it.next();
              if (fmChild.isActive()) {
                if (fmChild.getRole().toString().equals("Level2_Priority_1")) {
                  try {
                    logger.debug("[HCAL LVL1 " + functionManager.FMname + "] Found FM with role: " + fmChild.getRole().toString() + " and role: " + fmChild.getRole().toString() + " which has to be started early, executing: " + startInput);
                    fmChild.execute(startInput);
                  }
                  catch (CommandException e) {
                    String errMessage = "[HCAL LVL1 " + functionManager.FMname + "] Error! for FM with role: " + fmChild.getRole().toString() + ", CommandException: sending: " + startInput + " failed ...";
                    logger.error(errMessage,e);
                    functionManager.sendCMSError(errMessage);
                    functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT("Error")));
                    functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("oops - problems ...")));
                    if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; return;}
                  }
                }
              }
            }
          }

          while (!waitforFMswithRole("Level2_Priority_1",HCALStates.RUNNING.toString())) {
            try { Thread.sleep(1000); }
            catch (Exception ignored) {}
            logger.debug("[HCAL LVL1 " + functionManager.FMname + "] ... waiting for FMs to be in the state "+ HCALStates.RUNNING.toString() + "\nAll FMs which have the role: Level2_Priority_1.");
          }

          // start Level2_Priority_2
          {
            Iterator it = functionManager.containerFMChildren.getQualifiedResourceList().iterator();
            FunctionManager fmChild = null;
            while (it.hasNext()) {
              fmChild = (FunctionManager) it.next();
              if (fmChild.isActive()) {
                if (fmChild.getRole().toString().equals("Level2_Priority_2")) {
                  try {
                    logger.debug("[HCAL LVL1 " + functionManager.FMname + "] Found FM with role: " + fmChild.getRole().toString() + " which has to be started early, executing: " + startInput);
                    fmChild.execute(startInput);
                  }
                  catch (CommandException e) {
                    String errMessage = "[HCAL LVL1 " + functionManager.FMname + "] Error! for FM with role: " + fmChild.getRole().toString() + ", CommandException: sending: " + startInput + " failed ...";
                    logger.error(errMessage,e);
                    functionManager.sendCMSError(errMessage);
                    functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT("Error")));
                    functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("oops - problems ...")));
                    if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; return;}
                  }
                }
              }
            }
          }

          while (!waitforFMswithRole("Level2_Priority_2",HCALStates.RUNNING.toString())) {
            try { Thread.sleep(1000); }
            catch (Exception ignored) {}
            logger.debug("[HCAL LVL1 " + functionManager.FMname + "] ... waiting for FMs to be in the state "+ HCALStates.RUNNING.toString() + "\nAll FMs which have the role: Level2_Priority_2.");
          }

          Boolean needtowait = false;

          // afterwards start now the FMs which can be configured with no special order or priority
          {
            Iterator it = functionManager.containerFMChildren.getQualifiedResourceList().iterator();
            FunctionManager fmChild = null;
            while (it.hasNext()) {
              fmChild = (FunctionManager) it.next();
              if (fmChild.isActive()) {
                if ( !(fmChild.getRole().toString().equals("Level2_FilterFarm") || fmChild.getRole().toString().equals("Level2_Priority_1") || fmChild.getRole().toString().equals("Level2_Priority_2") || fmChild.getRole().toString().equals("Level2_Laser")) ) {
                  if (!(fmChild.getName().toString().equals("HCAL_HBHEa") && fmChild.getRole().toString().equals("Level2_FilterFarm"))) {
                    try {
                      logger.debug("[HCAL LVL1 " + functionManager.FMname + "] Found FM with role: " + fmChild.getRole().toString() + " which needs no specific order when starting, executing: " + startInput);
                      fmChild.execute(startInput);
                    }
                    catch (CommandException e) {
                      String errMessage = "[HCAL LVL1 " + functionManager.FMname + "] Error! for FM with role: " + fmChild.getRole().toString() + ", CommandException: sending: " + startInput + " failed ...";
                      logger.error(errMessage,e);
                      functionManager.sendCMSError(errMessage);
                      functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT("Error")));
                      functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("oops - problems ...")));
                      if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; return;}
                    }
                  }
                }
              }
            }
          }

          while (!waitforFMswithNotTheRole("Level2_Priority_1","Level2_Priority_2","Level2_FilterFarm","dummy","Level2_Laser",HCALStates.RUNNING.toString())) {
            try { Thread.sleep(1000); }
            catch (Exception ignored) {}
            logger.debug("[HCAL LVL1 " + functionManager.FMname + "] ... waiting for all FMs to be in the state "+ HCALStates.RUNNING.toString() + "\n All FMs which do not have the role: Level2_Priority_1, Level2_Priority_2, Level2_FilterFarm, or Level2_Laser");
          }

          // start Level2_FilterFarm
          {
            Iterator it = functionManager.containerFMChildren.getQualifiedResourceList().iterator();
            FunctionManager fmChild = null;
            while (it.hasNext()) {
              fmChild = (FunctionManager) it.next();
              if (fmChild.isActive()) {
                if (fmChild.getRole().toString().equals("Level2_FilterFarm") || (fmChild.getName().toString().equals("HCAL_HBHEa") && fmChild.getRole().toString().equals("Level2_FilterFarm"))) {
                  try {
                    logger.debug("[HCAL LVL1 " + functionManager.FMname + "] Found FM with role: " + fmChild.getRole().toString() + " which does e.g. event building, executing: " + startInput);
                    fmChild.execute(startInput);
                  }
                  catch (CommandException e) {
                    String errMessage = "[HCAL LVL1 " + functionManager.FMname + "] Error! for FM with role: " + fmChild.getRole().toString() + ", CommandException: sending: " + startInput + " failed ...";
                    logger.error(errMessage,e);
                    functionManager.sendCMSError(errMessage);
                    functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT("Error")));
                    functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("oops - problems ...")));
                    if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; return;}
                  }
                }
              }
            }
          }

          while (!waitforFMswithRole("Level2_FilterFarm",HCALStates.RUNNING.toString())) {
            try { Thread.sleep(1000); }
            catch (Exception ignored) {}
            logger.debug("[HCAL LVL1 " + functionManager.FMname + "] ... waiting for FMs to be in the state "+ HCALStates.RUNNING.toString() + "\nAll FMs which have the role: Level2_FilterFarm.");
          }

          // start Level2_Laser
          {
            Iterator it = functionManager.containerFMChildren.getQualifiedResourceList().iterator();
            FunctionManager fmChild = null;
            while (it.hasNext()) {
              fmChild = (FunctionManager) it.next();
              if (fmChild.isActive()) {
                if (fmChild.getRole().toString().equals("Level2_Laser")) {
                  try {
                    logger.debug("[HCAL LVL1 " + functionManager.FMname + "] Found FM with role: " + fmChild.getRole().toString() + " which does e.g. event building, executing: " + startInput);
                    fmChild.execute(startInput);
                  }
                  catch (CommandException e) {
                    String errMessage = "[HCAL LVL1 " + functionManager.FMname + "] Error! for FM with role: " + fmChild.getRole().toString() + ", CommandException: sending: " + startInput + " failed ...";
                    logger.error(errMessage,e);
                    functionManager.sendCMSError(errMessage);
                    functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT("Error")));
                    functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("oops - problems ...")));
                    if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; return;}
                  }
                }
              }
            }
          }

          while (!waitforFMswithRole("Level2_Laser",HCALStates.RUNNING.toString())) {
            try { Thread.sleep(1000); }
            catch (Exception ignored) {}
            logger.debug("[HCAL LVL1 " + functionManager.FMname + "] ... waiting for FMs to be in the state "+ HCALStates.RUNNING.toString() + "\nAll FMs which have the role: Level2_Laser.");
          }

        }
        else {

          // start all controlled FMs, the order is not important
          try {
            logger.debug("[HCAL LVL1 " + functionManager.FMname + "] Found FM childs - good! fireEvent: " + startInput);
            functionManager.containerFMChildren.execute(startInput);
          }
          catch (QualifiedResourceContainerException e) {
            String errMessage = "[HCAL LVL1 " + functionManager.FMname + "] Error! QualifiedResourceContainerException: sending: " + startInput + " failed ...";
            logger.error(errMessage,e);
            functionManager.sendCMSError(errMessage);
            functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT("Error")));
            functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("oops - problems ...")));
            if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; return;}
          }
        }

      }
      else {
        if (!functionManager.ErrorState) {
          logger.debug("[HCAL LVL1 " + functionManager.FMname + "] fireEvent: " + HCALInputs.SETSTART);
          functionManager.fireEvent(HCALInputs.SETSTART);
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
      logger.info("[JohnLog2] " + functionManager.FMname + " state notification while in the RUNNING state. ");
      computeNewState((StateNotification) obj);
      return;

    }
    else if (obj instanceof StateEnteredEvent) {
      System.out.println("[HCAL LVL1 " + functionManager.FMname + "] Executing runningAction");
      logger.info("[HCAL LVL1 " + functionManager.FMname + "] Executing runningAction");

      // reset the non-async error state handling
      functionManager.ErrorState = false;

      // remember that this FM was in the running State
      functionManager.FMWasInRunningStateOnce = true;

      functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT(functionManager.getState().getStateString())));
      functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("running like hell ...")));

      logger.info("[HCAL LVL1 " + functionManager.FMname + "] runningAction executed ...");

    }
  }

  public void pauseAction(Object obj) throws UserActionException {

    if (obj instanceof StateNotification) {

      // triggered by State Notification from child resource
      computeNewState((StateNotification) obj);
      return;

    }
    else if (obj instanceof StateEnteredEvent) {
      System.out.println("[HCAL LVL1 " + functionManager.FMname + "] Executing pauseAction");
      logger.info("[HCAL LVL1 " + functionManager.FMname + "] Executing pauseAction");

      // reset the non-async error state handling
      functionManager.ErrorState = false;

      // set action
      functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT("calculating state")));
      functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("pausing")));

      if (!functionManager.containerFMChildren.isEmpty()) {
        try {
          logger.debug("[HCAL LVL1 " + functionManager.FMname + "] Found FM childs - good! fireEvent: " + HCALInputs.PAUSE);
          functionManager.containerFMChildren.execute(HCALInputs.PAUSE);
        }
        catch (QualifiedResourceContainerException e) {
          String errMessage = "[HCAL LVL1 " + functionManager.FMname + "] Error! QualifiedResourceContainerException: sending: " + HCALInputs.PAUSE + " failed ...";
          logger.error(errMessage,e);
          functionManager.sendCMSError(errMessage);
          functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT("Error")));
          functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("oops - problems ...")));
          if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; return;}
        }
      }
      else {
        if (!functionManager.ErrorState) {
          logger.debug("[HCAL LVL1 " + functionManager.FMname + "] fireEvent: " + HCALInputs.SETPAUSE);
          functionManager.fireEvent(HCALInputs.SETPAUSE);
        }
      }

      // set action
      functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT(functionManager.getState().getStateString())));
      functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("pausingAction executed ...")));

      logger.debug("[HCAL LVL1 " + functionManager.FMname + "] pausingAction executed ...");

    }
  }

  public void resumeAction(Object obj) throws UserActionException {

    if (obj instanceof StateNotification) {

      // triggered by State Notification from child resource
      computeNewState((StateNotification) obj);
      return;

    }
    else if (obj instanceof StateEnteredEvent) {
      System.out.println("[HCAL LVL1 " + functionManager.FMname + "] Executing resumeAction");
      logger.info("[HCAL LVL1 " + functionManager.FMname + "] Executing resumeAction");

      // reset the non-async error state handling
      functionManager.ErrorState = false;

      // set action
      functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT("calculating state")));
      functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("resuming")));

      if (!functionManager.containerFMChildren.isEmpty()) {
        try {
          logger.debug("[HCAL LVL1 " + functionManager.FMname + "] Found FM childs - good! fireEvent: " + HCALInputs.RESUME);
          functionManager.containerFMChildren.execute(HCALInputs.RESUME);
        }
        catch (QualifiedResourceContainerException e) {
          String errMessage = "[HCAL LVL1 " + functionManager.FMname + "] Error! QualifiedResourceContainerException: sending: " + HCALInputs.RESUME + " failed ...";
          logger.error(errMessage,e);
          functionManager.sendCMSError(errMessage);
          functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT("Error")));
          functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("oops - problems ...")));
          if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; return;}
        }
      }
      else {
        if (!functionManager.ErrorState) {
          logger.debug("[HCAL LVL1 " + functionManager.FMname + "] fireEvent: " + HCALInputs.SETRESUME);
          functionManager.fireEvent(HCALInputs.SETRESUME);
        }
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
      System.out.println("[HCAL LVL1 " + functionManager.FMname + "] Executing haltAction");
      logger.info("[HCAL LVL1 " + functionManager.FMname + "] Executing haltAction");

      // reset the non-async error state handling
      functionManager.ErrorState = false;

      // set action
      functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT("calculating state")));
      functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("haaaalting ...")));

      publishRunInfoSummary();
      publishRunInfoSummaryfromXDAQ(); 
      functionManager.HCALRunInfo = null; // make RunInfo ready for the next round of run info to store

      if (!functionManager.containerFMChildren.isEmpty()) {

        // define stop time
        StopTime = new Date();

        // Remember if FM was in running state once
        functionManager.FMWasInRunningStateOnce = false;


        // halt all FMs
        Iterator it = functionManager.containerFMChildren.getQualifiedResourceList().iterator();
        FunctionManager fmChild = null;
        while (it.hasNext()) {
          fmChild = (FunctionManager) it.next();
          if (fmChild.isActive()) {
            if (! (fmChild.refreshState().toString().equals(HCALStates.HALTING.toString()) || fmChild.refreshState().toString().equals(HCALStates.HALTED.toString()))) {
              try {
                logger.debug("[HCAL LVL1 " + functionManager.FMname + "] Will sent " + HCALInputs.HALT + " to FM named: " + fmChild.getResource().getName().toString() + "\nThe role is: " + fmChild.getResource().getRole().toString() + "\nAnd the URI is: " + fmChild.getResource().getURI().toString());
                fmChild.execute(HCALInputs.HALT);
              }
              catch (CommandException e) {
                String errMessage = "[HCAL LVL1 " + functionManager.FMname + "] Error! for FM with role: " + fmChild.getRole().toString() + ", CommandException: sending: " + HCALInputs.HALT + " failed ...";
                logger.error(errMessage,e);
                functionManager.sendCMSError(errMessage);
                functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT("Error")));
                functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("oops - problems ...")));
                if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; return;}
              }
            }
            else {
              logger.debug("[HCAL LVL1 " + functionManager.FMname + "] This FM is already \"Halted\".\nWill sent not send" + HCALInputs.HALT + " to FM named: " + fmChild.getResource().getName().toString() + "\nThe role is: " + fmChild.getResource().getRole().toString() + "\nAnd the URI is: " + fmChild.getResource().getURI().toString());
            }
          }
        }
      }
      else {
        if (!functionManager.ErrorState) {
          logger.debug("[HCAL LVL1 " + functionManager.FMname + "] fireEvent: " + HCALInputs.SETHALT);
          functionManager.fireEvent(HCALInputs.SETHALT);
        }
      }


      // set action
      functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT(functionManager.getState().getStateString())));
      functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("haltAction executed ...")));

      logger.debug("[HCAL LVL1 " + functionManager.FMname + "] haltAction executed ...");
    }
  }

  public void coldResetAction(Object obj) throws UserActionException {
    if (obj instanceof StateNotification) {

      // triggered by State Notification from child resource
      computeNewState((StateNotification) obj);
      return;

    }
    else if (obj instanceof StateEnteredEvent) {
      System.out.println("[HCAL LVL1 " + functionManager.FMname + "] Executing coldResetAction");
      logger.info("[HCAL LVL1 " + functionManager.FMname + "] Executing coldResetAction");

      // reset the non-async error state handling
      functionManager.ErrorState = false;

      // set action
      functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT("calculating state")));
      functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("brrr - cold resetting ...")));

      publishRunInfoSummary();
      publishRunInfoSummaryfromXDAQ(); 
      functionManager.HCALRunInfo = null; // make RunInfo ready for the next round of run info to store

      if (!functionManager.containerFMChildren.isEmpty()) {

        // define stop time
        StopTime = new Date();

        functionManager.FMWasInRunningStateOnce = false;



        // reset all FMs 
        Iterator it = functionManager.containerFMChildren.getQualifiedResourceList().iterator();
        FunctionManager fmChild = null;
        while (it.hasNext()) {
          fmChild = (FunctionManager) it.next();
          if (fmChild.isActive()) {
            if (! (fmChild.refreshState().toString().equals("ColdResetting")) ) {
              try {
                logger.debug("[HCAL LVL1 " + functionManager.FMname + "] Will sent " + HCALInputs.COLDRESET + " to FM named: " + fmChild.getResource().getName().toString() + "\nThe role is: " + fmChild.getResource().getRole().toString() + "\nAnd the URI is: " + fmChild.getResource().getURI().toString());
                fmChild.execute(HCALInputs.COLDRESET);
              }
              catch (CommandException e) {
                String errMessage = "[HCAL LVL1 " + functionManager.FMname + "] Error! for FM with role: " + fmChild.getRole().toString() + ", CommandException: sending: " + HCALInputs.COLDRESET + " failed ...";
                logger.error(errMessage,e);
                functionManager.sendCMSError(errMessage);
                functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT("Error")));
                functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("oops - problems ...")));
                if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; return;}
              }
            }
            else {
              logger.debug("[HCAL LVL1 " + functionManager.FMname + "] This FM is already \"ColdResetting\".\nWill sent not send" + HCALInputs.COLDRESET + " to FM named: " + fmChild.getResource().getName().toString() + "\nThe role is: " + fmChild.getResource().getRole().toString() + "\nAnd the URI is: " + fmChild.getResource().getURI().toString());
            }
          }
        }
      }
      else {
        if (!functionManager.ErrorState) {
          logger.debug("[HCAL LVL1 " + functionManager.FMname + "] fireEvent: " + HCALInputs.SETCOLDRESET);
          functionManager.fireEvent(HCALInputs.SETCOLDRESET);
        }
      }


      // set action
      functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT(functionManager.getState().getStateString())));
      functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("coldResetAction executed ...")));

      logger.debug("[HCAL LVL1 " + functionManager.FMname + "] coldResetAction executed ...");
    }
  }

  public void stoppingAction(Object obj) throws UserActionException {

    if (obj instanceof StateNotification) {

      // triggered by State Notification from child resource

      computeNewState((StateNotification) obj);
      return;

    }
    else if (obj instanceof StateEnteredEvent) {
      System.out.println("[HCAL LVL1 " + functionManager.FMname + "] Executing stoppingAction");
      logger.info("[HCAL LVL1 " + functionManager.FMname + "] Executing stoppingAction");

      // reset the non-async error state handling
      functionManager.ErrorState = false;

      // set action
      functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT("calculating state")));
      functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("stopping")));
      logger.info("[JohnLog] LVL1 about to call publishRunInfoSummary");
      publishRunInfoSummary();
      publishRunInfoSummaryfromXDAQ(); 
      functionManager.HCALRunInfo = null; // make RunInfo ready for the next round of run info to store

      if (!functionManager.containerFMChildren.isEmpty()) {

        // define stop time
        StopTime = new Date();

        // Ancient history: "old" behavior where the LUMI FMs were stopped always no matter what state of the deflector shield ...
        /*
           {
           Iterator it = functionManager.containerFMChildren.getQualifiedResourceList().iterator();
           FunctionManager fmChild = null;
           while (it.hasNext()) {
           fmChild = (FunctionManager) it.next();

           if (! (fmChild.refreshState().toString().equals(HCALStates.STOPPING.toString()) || fmChild.refreshState().toString().equals(HCALStates.CONFIGURED.toString())) ) {
           try {
           logger.debug("[HCAL LVL1 " + functionManager.FMname + "] Will send " + HCALInputs.STOP + " to the FM named: " + fmChild.getResource().getName().toString() + "\nThe role is: " + fmChild.getResource().getRole().toString() + "\nAnd the URI is: " + fmChild.getResource().getURI().toString());
           fmChild.execute(HCALInputs.STOP);
           }
           catch (CommandException e) {
           String errMessage = "[HCAL LVL1 " + functionManager.FMname + "] Error! CommandException: sending: " + HCALInputs.STOP + " during stoppingAction() failed ...";
           logger.error(errMessage,e);
           functionManager.sendCMSError(errMessage);
           functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT("Error")));
           functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("oops - problems ...")));
           if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; return;}
           }
           }   else {
           logger.debug("[HCAL LVL1 " + functionManager.FMname + "] This FM is already \"Configured\".\nWill sent not send" + HCALInputs.STOP + " to FM named: " + fmChild.getResource().getName().toString() + "\nThe role is: " + fmChild.getResource().getRole().toString() + "\nAnd the URI is: " + fmChild.getResource().getURI().toString());
           }
           }
           }
           */

        // stop all FMs 

        Iterator it = functionManager.containerFMChildren.getQualifiedResourceList().iterator();
        FunctionManager fmChild = null;
        while (it.hasNext()) {
          fmChild = (FunctionManager) it.next();
          if (fmChild.isActive()) {
            if (! (fmChild.refreshState().toString().equals(HCALStates.STOPPING.toString()) || fmChild.refreshState().toString().equals(HCALStates.CONFIGURED.toString())) ) {
              try {
                logger.info("[JohnLog2] [HCAL LVL1 " + functionManager.FMname + "] Will send " + HCALInputs.STOP + " to the FM named: " + fmChild.getResource().getName().toString() + "\nThe role is: " + fmChild.getResource().getRole().toString() + "\nAnd the URI is: " + fmChild.getResource().getURI().toString());
                fmChild.execute(HCALInputs.STOP);
              }
              catch (CommandException e) {
                String errMessage = "[HCAL LVL1 " + functionManager.FMname + "] Error! CommandException: sending: " + HCALInputs.STOP + " during stoppingAction() failed ...";
                logger.error(errMessage,e);
                functionManager.sendCMSError(errMessage);
                functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT("Error")));
                functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("oops - problems ...")));
                if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; return;}
              }
            }
            else {
              logger.debug("[HCAL LVL1 " + functionManager.FMname + "] This FM is already \"Configured\".\nWill sent not send" + HCALInputs.STOP + " to FM named: " + fmChild.getResource().getName().toString() + "\nThe role is: " + fmChild.getResource().getRole().toString() + "\nAnd the URI is: " + fmChild.getResource().getURI().toString());
            }
          }
        }


      }
      else {
        if (!functionManager.ErrorState) {
          logger.debug("[HCAL LVL1 " + functionManager.FMname + "] fireEvent: " + HCALInputs.SETCONFIGURE);
          if (!functionManager.getState().getStateString().equals(HCALStates.CONFIGURED.toString())) {
            functionManager.fireEvent(HCALInputs.SETCONFIGURE);
          }
        }
      }


      // set actions
      functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT(functionManager.getState().getStateString())));
      functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("stoppingAction executed ...")));

      logger.debug("[HCAL LVL1 " + functionManager.FMname + "] stoppingAction executed ...");

    }
  }

  public void preparingTTSTestModeAction(Object obj) throws UserActionException {

    if (obj instanceof StateNotification) {

      // triggered by State Notification from child resource
      computeNewState((StateNotification) obj);
      return;

    }
    else if (obj instanceof StateEnteredEvent) {
      System.out.println("[HCAL LVL1 " + functionManager.FMname + "] Executing preparingTestModeAction");
      logger.info("[HCAL LVL1 " + functionManager.FMname + "] Executing preparingTestModeAction");

      // reset the non-async error state handling
      functionManager.ErrorState = false;

      // set actions
      functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT("calculating state")));
      functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("preparingTestMode")));

      if (!functionManager.containerFMChildren.isEmpty()) {
        try {
          logger.debug("[HCAL LVL1 " + functionManager.FMname + "] Found FM childs - good! fireEvent: " + HCALInputs.TTSTEST_MODE);
          functionManager.containerFMChildren.execute(HCALInputs.TTSTEST_MODE);
        }
        catch (QualifiedResourceContainerException e) {
          String errMessage = "[HCAL LVL1 " + functionManager.FMname + "] Error! QualifiedResourceContainerException: sending: " + HCALInputs.TTSTEST_MODE + " failed ...";
          logger.error(errMessage,e);
          functionManager.sendCMSError(errMessage);
          functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT("Error")));
          functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("oops - problems ...")));
          if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; return;}
        }
      }
      else {
        if (!functionManager.ErrorState) {
          logger.debug("[HCAL LVL1 " + functionManager.FMname + "] fireEvent: " + HCALInputs.SETTTSTEST_MODE);
          functionManager.fireEvent(HCALInputs.SETTTSTEST_MODE);
        }
      }

      // set actions
      functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT(functionManager.getState().getStateString())));
      functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("preparingTestModeAction executed ...")));

      logger.debug("[HCAL LVL1 " + functionManager.FMname + "] preparingTestModeAction executed ...");
    }
  }

  public void testingTTSAction(Object obj) throws UserActionException {

    if (obj instanceof StateNotification) {

      // triggered by State Notification from child resource
      computeNewState((StateNotification) obj);
      return;

    }
    else if (obj instanceof StateEnteredEvent) {
      System.out.println("[HCAL LVL1 " + functionManager.FMname + "] Executing testingTTSAction");
      logger.info("[HCAL LVL1 " + functionManager.FMname + "] Executing testingTTSAction");

      // reset the non-async error state handling
      functionManager.ErrorState = false;

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
        String errMsg = "[HCAL LVL1 " + functionManager.FMname + "] Error! No parameters given with TestTTS command: testingTTSAction";
        logger.error(errMsg);
        functionManager.sendCMSError(errMsg);
        if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; return;}

      }
      else {

        logger.debug("[HCAL LVL1 " + functionManager.FMname + "] Getting parameters for sTTS test now ...");

        // get the paramters from the command
        FedId = ((IntegerT)parameterSet.get(HCALParameters.TTS_TEST_FED_ID).getValue()).getInteger();
        mode = ((StringT)parameterSet.get(HCALParameters.TTS_TEST_MODE).getValue()).getString();
        pattern = ((StringT)parameterSet.get(HCALParameters.TTS_TEST_PATTERN).getValue()).getString();
        cycles = ((IntegerT)parameterSet.get(HCALParameters.TTS_TEST_SEQUENCE_REPEAT).getValue()).getInteger();
      }

      // prepare parameters to be passed to level 2
      ParameterSet<CommandParameter> pSet = new ParameterSet<CommandParameter>();
      pSet.put(new CommandParameter<IntegerT>(HCALParameters.TTS_TEST_FED_ID, new IntegerT(FedId)));
      pSet.put(new CommandParameter<StringT>(HCALParameters.TTS_TEST_MODE, new StringT(mode)));
      pSet.put(new CommandParameter<StringT>(HCALParameters.TTS_TEST_PATTERN, new StringT(pattern)));
      pSet.put(new CommandParameter<IntegerT>(HCALParameters.TTS_TEST_SEQUENCE_REPEAT, new IntegerT(cycles)));

      // prepare command plus the parameters to send
      Input sTTSInput= new Input(HCALInputs.TEST_TTS.toString());
      sTTSInput.setParameters( pSet );

      if (!functionManager.containerFMChildren.isEmpty()) {
        try {
          logger.debug("[HCAL LVL1 " + functionManager.FMname + "] Found FM childs - good! fireEvent: " + sTTSInput);
          functionManager.containerFMChildren.execute(sTTSInput);
        }
        catch (QualifiedResourceContainerException e) {
          String errMessage = "[HCAL LVL1 " + functionManager.FMname + "] Error! QualifiedResourceContainerException: sending: " + sTTSInput + " failed ...";
          logger.error(errMessage,e);
          functionManager.sendCMSError(errMessage);
          functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT("Error")));
          functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("oops - problems ...")));
          if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; return;}
        }
      }
      else {
        if (!functionManager.ErrorState) {
          logger.debug("[HCAL LVL1 " + functionManager.FMname + "] fireEvent: " + HCALInputs.SETTTSTEST_MODE);
          functionManager.fireEvent(HCALInputs.SETTTSTEST_MODE);
        }
      }

      // set actions
      functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT(functionManager.getState().getStateString())));
      functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("testingTTSAction executed ...")));

      logger.debug("[HCAL LVL1 " + functionManager.FMname + "] testingTTSAction executed ...");
    }
  }
}
