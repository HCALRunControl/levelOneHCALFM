package rcms.fm.app.level1;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.Date;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Hashtable;
import java.lang.Integer;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.math.BigInteger;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Random;
import java.util.ListIterator;
import java.net.URL;
import java.net.MalformedURLException;
import java.lang.Math;


import java.io.StringWriter;
import java.io.PrintWriter;

import net.hep.cms.xdaqctl.XDAQException;
import net.hep.cms.xdaqctl.XDAQTimeoutException;
import net.hep.cms.xdaqctl.XDAQMessageException;

import javax.xml.parsers.DocumentBuilder;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.Text;

import rcms.fm.fw.StateEnteredEvent;
import rcms.fm.fw.parameter.Parameter;
import rcms.fm.fw.parameter.FunctionManagerParameter;
import rcms.fm.fw.parameter.ParameterSet;
import rcms.fm.fw.service.parameter.ParameterServiceException;
import rcms.fm.fw.parameter.type.IntegerT;
import rcms.fm.fw.parameter.type.StringT;
import rcms.fm.fw.parameter.type.DoubleT;
import rcms.fm.fw.parameter.type.VectorT;
import rcms.fm.fw.parameter.type.BooleanT;
import rcms.fm.fw.user.UserActionException;
import rcms.fm.fw.user.UserEventHandler;
import rcms.fm.resource.QualifiedGroup;
import rcms.fm.resource.CommandException;
import rcms.fm.resource.QualifiedResource;
import rcms.fm.resource.QualifiedResourceContainer;
import rcms.fm.resource.QualifiedResourceContainerException;
import rcms.fm.resource.qualifiedresource.XdaqApplication;
import rcms.fm.resource.qualifiedresource.XdaqServiceApplication;
import rcms.fm.resource.qualifiedresource.XdaqApplicationContainer;
import rcms.fm.resource.qualifiedresource.XdaqExecutive;
import rcms.fm.resource.qualifiedresource.JobControl;
import rcms.fm.resource.qualifiedresource.FunctionManager;
import rcms.resourceservice.db.Group;
import rcms.resourceservice.db.resource.Resource;
import rcms.resourceservice.db.resource.ResourceException;
import rcms.resourceservice.db.resource.config.ConfigProperty;
import rcms.resourceservice.db.resource.fm.FunctionManagerResource;
import rcms.resourceservice.db.resource.xdaq.XdaqApplicationResource;
import rcms.resourceservice.db.resource.xdaq.XdaqExecutiveResource;
import rcms.stateFormat.StateNotification;
import rcms.util.logger.RCMSLogger;
import rcms.xdaqctl.XDAQParameter;
import rcms.xdaqctl.XDAQMessage;
import rcms.utilities.runinfo.RunInfo;
import rcms.utilities.runinfo.RunInfoConnectorIF;
import rcms.utilities.runinfo.RunInfoException;
import rcms.utilities.runinfo.RunNumberData;
import rcms.utilities.runinfo.RunSequenceNumber;
import rcms.util.logsession.LogSessionConnector;
import rcms.statemachine.definition.Input;

/**
 * Event Handler base class for HCAL Function Managers
 * @maintainer John Hakala
 */

public class HCALEventHandler extends UserEventHandler {

  // Helper classes
  protected HCALFunctionManager functionManager = null;
  static RCMSLogger logger = new RCMSLogger(HCALEventHandler.class);
  public HCALxmlHandler xmlHandler = null;
  public LogSessionConnector logSessionConnector;  // Connector for logsession DB

  // Essential xdaq stuff
  public static final String XDAQ_NS = "urn:xdaq-soap:3.0";



  String ConfigDoc     = "";
  String FullCfgScript = "not set";

  public boolean UsePrimaryTCDS                =  true;   // Switch to use primary/secondary TCDS system (TODO: check implementation)
  public boolean OfficialRunNumbers            =  false;  // Query the database for a run number corresponding to the SID 
  public boolean RunInfoPublish                =  false;  // Switch to publish RunInfo or not
  public boolean RunInfoPublishfromXDAQ        =  false;  // Switch to publish additional runinfo from the hcalRunInfoServer or not
  public boolean stopMonitorThread             =  false;  // For turning off the level2 watch thread
  public boolean stopHCALSupervisorWatchThread =  false;  // For turning off the supervisor watch thread
  public boolean stopTriggerAdapterWatchThread =  false;  // For turning off the TA thread
  public boolean stopAlarmerWatchThread        =  false;  // For turning off the alarmer thread
  public boolean stopTTCciWatchThread          =  false;  // For turning off the TTCci thread
  public boolean delayAlarmerWatchThread       =  false;  // For delaying the alarmer thread at the start
  public boolean NotifiedControlledFMs         =  false;  // For notifications to level2s
  public boolean ClockChanged                  =  false;  // Flag for whether the clock source has changed
  public boolean UseResetForRecover            =  true;   // Switch to disable the "Recover" behavior and instead replace it with doing a "Reset" behavior
  public Integer Sid              =  0;           // Session ID for database connections
  public Integer TriggersToTake   =  0;           // Requested number of events to be taken
  public Integer RunSeqNumber     =  0;           // Run sequence number
  public Integer eventstaken      =  -1;          //Events taken for local runs
  public Integer localeventstaken =  -1;          // TODO: what does this do?
  public String  GlobalConfKey    =  "";          // global configuration key
  public String  RunType          =  "";          // local or global
  public String  GlobalRunkey           =  "";          // Current global run key
  public String  CachedGlobalRunkey     =  "";          // Previous global run key
  public String  TpgKey           =  "";          // Current trigger key
  public String  CachedTpgKey     =  "";          // Previous trigger key
  public String  FedEnableMask    =  "";          // FED enable mask received from level0 on configure in global
  public String RunSequenceName   =  "HCAL test"; // Run sequence name, for attaining a run sequence number
  public String SupervisorError   =  "";          // String which stores an error retrieved from the hcalSupervisor.
  public String CfgCVSBasePath    =  "";          // Where to look for snippets
  public String TestMode          =  "off";       // Skeletor comment: "Switch to be able to ignore any errors which would cause the FM state machine to end in an error state"
  public Double completion      = -1.0; // Completion status, incorporating info from child FMs
  public Double localcompletion = -1.0;
  public Date StartTime = null; // Broken
  public Date StopTime  = null; // Broken
  public DocumentBuilder docBuilder;

  protected boolean SpecialFMsAreControlled    =  false;  // Switch for saying whether "special" FMs are controlled. TODO: is this needed any more?

  protected String WSE_FILTER                        =  "empty";                         // for XMAS -- TODO: is this needed?

  private List<Thread> TriggerAdapterWatchThreadList =  new ArrayList<Thread>();  // For querying the TA periodically
  private List<Thread> MonitorThreadList             =  new ArrayList<Thread>();  // For watching level2s
  private List<Thread> HCALSupervisorWatchThreadList =  new ArrayList<Thread>();  // For querying the hcalSupervisor periodically
  private List<Thread> AlarmerWatchThreadList        =  new ArrayList<Thread>();  // For querying alarmer periodically
  public String maskedAppsForRunInfo = "";
  public String emptyFMsForRunInfo   = "";


  public HCALEventHandler() throws rcms.fm.fw.EventHandlerException {

    // Let's register the StateEnteredEvent triggered when the FSM enters in a new state.
    subscribeForEvents(StateEnteredEvent.class);

    addAction(HCALStates.INITIALIZING,            "initAction");
    addAction(HCALStates.CONFIGURING,             "configureAction");
    addAction(HCALStates.HALTING,                 "haltAction");
    addAction(HCALStates.EXITING,                 "exitAction");
    addAction(HCALStates.STOPPING,                "stoppingAction");
    addAction(HCALStates.PREPARING_TTSTEST_MODE,  "preparingTTSTestModeAction");
    addAction(HCALStates.TESTING_TTS,             "testingTTSAction");
    addAction(HCALStates.PAUSING,                 "pauseAction");
    addAction(HCALStates.RECOVERING,              "recoverAction");
    addAction(HCALStates.RESETTING,               "resetAction");
    addAction(HCALStates.RESUMING,                "resumeAction");
    addAction(HCALStates.STARTING,                "startAction");
    addAction(HCALStates.RUNNING,                 "runningAction");
    addAction(HCALStates.COLDRESETTING,           "coldResetAction");
  }

  public void init() throws rcms.fm.fw.EventHandlerException {
    logger.info("[HCAL " + functionManager.FMname + "]:  Executing HCALEventHandler::init()");
    xmlHandler = new HCALxmlHandler(this.functionManager);
    // Evaluating some basic configurations from the userXML
    // Switch for each level1 and level2 to enable TriggerAdapter handling. Note that only one level2 should handle the TriggerAdapter
    {
      logger.info("[HCAL " + functionManager.FMname + "]: This FM has userXML that says: " + ((FunctionManagerResource)functionManager.getQualifiedGroup().getGroup().getThisResource()).getUserXml() );
      //Boolean doHandleTriggerAdapter = ((FunctionManagerResource)functionManager.getQualifiedGroup().getGroup().getThisResource()).getUserXml().contains("<HandleTriggerAdapter>true</HandleTriggerAdapter>");
      if (((FunctionManagerResource)functionManager.getQualifiedGroup().getGroup().getThisResource()).getRole().equals("EvmTrig")) {
        logger.info("[HCAL " + functionManager.FMname + "]: The function manager with name " + functionManager.FMname + " was assigned role EvmTrig and thus will handle the trigger adapter.");
      }
    }

    VectorT<StringT> availableResources = new VectorT<StringT>();

    QualifiedGroup qg = functionManager.getQualifiedGroup();
    List<QualifiedResource> qrList = qg.seekQualifiedResourcesOfType(new FunctionManager());
    for (QualifiedResource qr : qrList) {
      availableResources.add(new StringT(qr.getName()));
    }

    qrList = qg.seekQualifiedResourcesOfType(new XdaqExecutive());
    for (QualifiedResource qr : qrList) {
      availableResources.add(new StringT(qr.getName()));
    }

    qrList = qg.seekQualifiedResourcesOfType(new JobControl());
    for (QualifiedResource qr : qrList) {
      availableResources.add(new StringT(qr.getName()));
    }

    qrList = qg.seekQualifiedResourcesOfType(new XdaqApplication());
    for (QualifiedResource qr : qrList) {
      availableResources.add(new StringT(qr.getName()));
    }

    functionManager.getHCALparameterSet().put(new FunctionManagerParameter<VectorT<StringT>>("AVAILABLE_RESOURCES",availableResources));
  }

  public void destroy() {
    // Stop all threads
    functionManager.parameterSender.shutdown();
    stopMonitorThread = true;
    stopHCALSupervisorWatchThread = true;
    stopTriggerAdapterWatchThread = true;
    stopAlarmerWatchThread = true;

    // Destroy the FM
    super.destroy();
  }

  // Function to "send" the USE_PRIMARY_TCDS aprameter to the HCAL supervisor application. It gets the info from the userXML.
  //protected void getUsePrimaryTCDS(){
  //  boolean UsePrimaryTCDS = Boolean.parseBoolean(GetUserXMLElement("UsePrimaryTCDS"));
  //  if (GetUserXMLElement("UsePrimaryTCDS").equals("")){
  //    logger.info("[HCAL " + functionManager.FMname + "] UsePrimaryTCDS in userXML found.\nHere is it:\n" + GetUserXMLElement("UsePrimaryTCDS"));
  //  }
  //  else {
  //    logger.info("[HCAL "+ functionManager.FMname + "] No UsePrimaryTCDS found in userXML.\n");
  //  }
  //  functionManager.getHCALparameterSet().put(new FunctionManagerParameter<BooleanT>(HCALParameters.USE_PRIMARY_TCDS,new BooleanT(UsePrimaryTCDS)));
  //  // more logging stuff here...?
  //}

  // configuring all created HCAL applications by means of sending the HCAL CfgScript to the HCAL supervisor
  protected void sendRunTypeConfiguration( String CfgScript, String TTCciControlSequence, String LTCControlSequence,
                                           String ICIControlSequence, String LPMControlSequence, String PIControlSequence, 
                                           String FedEnableMask, boolean UsePrimaryTCDS
                                         ) {
    if (!functionManager.containerTTCciControl.isEmpty()) {

      {
        String debugMessage = "[HCAL " + functionManager.FMname + "] TTCciControl found - good!";
        System.out.println(debugMessage);
        logger.debug(debugMessage);
      }

      {
        XDAQParameter pam = null;

        // prepare and set for all TTCciControl applications the RunType
        for (QualifiedResource qr : functionManager.containerTTCciControl.getApplications() ){
          try {
            pam =((XdaqApplication)qr).getXDAQParameter();

            pam.select("Configuration");
            pam.setValue("Configuration",TTCciControlSequence);
            logger.debug("[HCAL " + functionManager.FMname + "] sending TTCciControlSequence ...");

            pam.send();
          }
          catch (XDAQTimeoutException e) {
            String errMessage = "[HCAL " + functionManager.FMname + "] Error! XDAQTimeoutException: sendRunTypeConfiguration()\n Perhaps the TTCciControl application is dead!?";
            logger.error(errMessage,e);
            functionManager.sendCMSError(errMessage);
            functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("STATE",new StringT("Error")));
            functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("ACTION_MSG",new StringT(errMessage)));
            if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; return;}
          }
          catch (XDAQException e) {
            String errMessage = "[HCAL " + functionManager.FMname + "] Error! XDAQException: sendRunTypeConfiguration()" + e.getMessage();
            logger.error(errMessage,e);
            functionManager.sendCMSError(errMessage);
            functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("STATE",new StringT("Error")));
            functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("ACTION_MSG",new StringT(errMessage)));
            if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; return;}
          }
        }
      }
    }

    if (!functionManager.containerLTCControl.isEmpty()) {

      {
        String debugMessage = "[HCAL " + functionManager.FMname + "] LTCControl found - good!";
        System.out.println(debugMessage);
        logger.debug(debugMessage);
      }

      {
        XDAQParameter pam = null;

        // prepare and set for all HCAL supervisors the RunType
        for (QualifiedResource qr : functionManager.containerLTCControl.getApplications() ){
          try {
            pam =((XdaqApplication)qr).getXDAQParameter();

            pam.select("Configuration");
            pam.setValue("Configuration",LTCControlSequence);
            logger.debug("[HCAL " + functionManager.FMname + "] sending LTCControlSequence ...");

            pam.send();
          }
          catch (XDAQTimeoutException e) {
            String errMessage = "[HCAL " + functionManager.FMname + "] Error! XDAQTimeoutException: sendRunTypeConfiguration()\n Perhaps the LTCControl application is dead!?";
            logger.error(errMessage,e);
            functionManager.sendCMSError(errMessage);
            functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("STATE",new StringT("Error")));
            functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("ACTION_MSG",new StringT(errMessage)));
            if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; return;}
          }
          catch (XDAQException e) {
            String errMessage = "[HCAL " + functionManager.FMname + "] Error! XDAQException: sendRunTypeConfiguration()" + e.getMessage();
            logger.error(errMessage,e);
            functionManager.sendCMSError(errMessage);
            functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("STATE",new StringT("Error")));
            functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("ACTION_MSG",new StringT(errMessage)));
            if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; return;}
          }
        }
      }
    }

    if (!functionManager.containerhcalSupervisor.isEmpty()) {

      {
        String debugMessage = "[HCAL " + functionManager.FMname + "] HCAL supervisor found - good!";
        System.out.println(debugMessage);
        logger.debug(debugMessage);
      }

      {
        XDAQParameter pam = null;

        // prepare and set for all HCAL supervisors the RunType
        for (QualifiedResource qr : functionManager.containerhcalSupervisor.getApplications() ){
          try {
            pam =((XdaqApplication)qr).getXDAQParameter();

            if (CfgScript.equals(""))
            {
              pam.select("RunType");
              pam.setValue("RunType",functionManager.FMfullpath);
              logger.debug("[HCAL " + functionManager.FMname + "] sending RunType: " + functionManager.FMfullpath);
            }
            else {
              pam.select(new String[] {"RunType", "ConfigurationDoc", "Partition", "RunSessionNumber", "hardwareConfigurationStringTCDS", "hardwareConfigurationStringLPM", "hardwareConfigurationStringPI", "fedEnableMask", "usePrimaryTCDS"});
              pam.setValue("RunType",functionManager.FMfullpath);
              logger.debug("[HCAL " + functionManager.FMname + "]: the ConfigurationDoc to be sent to the supervisor is: " + CfgScript);
              pam.setValue("ConfigurationDoc",CfgScript);
              pam.setValue("Partition",functionManager.FMpartition);
              pam.setValue("RunSessionNumber",Sid.toString());
              pam.setValue("hardwareConfigurationStringTCDS", ICIControlSequence);
              pam.setValue("hardwareConfigurationStringLPM", LPMControlSequence);
              pam.setValue("hardwareConfigurationStringPI", PIControlSequence);
              pam.setValue("fedEnableMask", FedEnableMask);
              pam.setValue("usePrimaryTCDS", new Boolean(UsePrimaryTCDS).toString());
              logger.debug("[HCAL " + functionManager.FMname + "] sending ICIControl sequence:\n" + ICIControlSequence);
              logger.debug("[HCAL " + functionManager.FMname + "] sending LPMControl sequence:\n" + LPMControlSequence);
              logger.debug("[HCAL " + functionManager.FMname + "] sending PIControl sequence:\n" + PIControlSequence);
              logger.debug("[HCAL " + functionManager.FMname + "] sending FedEnableMask sequence:\n" + FedEnableMask);
              logger.debug("[HCAL " + functionManager.FMname + "] sending UsePrimaryTCDS value:\n" + UsePrimaryTCDS);
              if (RunType.equals("undefined"))
              {
                logger.debug("[HCAL " + functionManager.FMname + "] sending CfgScript found in userXML - good!");
              }
              else {
                logger.debug("[HCAL " + functionManager.FMname + "] sending RunType: " + functionManager.FMfullpath + " together with CfgScript found in userXML - good!");
              }
            }
            pam.send();
          }
          catch (XDAQTimeoutException e) {
            String errMessage = "[HCAL " + functionManager.FMname + "] Error! XDAQTimeoutException: sendRunTypeConfiguration()\n Perhaps the HCAL supervisor application is dead!?";
            logger.error(errMessage,e);
            functionManager.sendCMSError(errMessage);
            functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("STATE",new StringT("Error")));
            functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("ACTION_MSG",new StringT(errMessage)));
            if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; return;}
          }
          catch (XDAQException e) {
            String errMessage = "[HCAL " + functionManager.FMname + "] Error! XDAQException: sendRunTypeConfiguration()";
            logger.error(errMessage,e);
            functionManager.sendCMSError(errMessage);
            functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("STATE",new StringT("Error")));
            functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("ACTION_MSG",new StringT(errMessage)));
            if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; return;}
          }
        }
      }

      // send SOAP configure to the HCAL supervisor
      try {
        functionManager.containerhcalSupervisor.execute(HCALInputs.CONFIGURE);
      }
      catch (QualifiedResourceContainerException e) {
        String errMessage = "[HCAL " + functionManager.FMname + "] Error! QualifiedResourceContainerException: sendRunTypeConfiguration()";
        logger.error(errMessage,e);
        functionManager.sendCMSError(errMessage);
        functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("STATE",new StringT("Error")));
        functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("ACTION_MSG",new StringT(errMessage)));
        if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; return;}
      }
    }
    else if (!functionManager.FMrole.equals("Level2_TCDSLPM")) {
      String errMessage = "[HCAL " + functionManager.FMname + "] Error! No HCAL supervisor found: sendRunTypeConfiguration()";
      logger.error(errMessage);
      functionManager.sendCMSError(errMessage);
      functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("STATE",new StringT("Error")));
      functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("ACTION_MSG",new StringT(errMessage)));
      if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; return;}

    }
  }

  // get the TriggerAdapter name from the HCAL supervisor only if no trigger adapter was already set
  protected void getTriggerAdapter() {
    if (functionManager.containerTriggerAdapter.isEmpty()) {
      if (!functionManager.containerhcalSupervisor.isEmpty()) {

        XDAQParameter pam = null;
        String TriggerAdapter = "undefined";

        // ask for the status of the HCAL supervisor and wait until it is Ready or Failed
        for (QualifiedResource qr : functionManager.containerhcalSupervisor.getApplications() ){
          try {
            pam =((XdaqApplication)qr).getXDAQParameter();
            pam.select("TriggerAdapterName");
            pam.get();

            TriggerAdapter = pam.getValue("TriggerAdapterName");
            if (!TriggerAdapter.equals("")) {
              logger.info("[HCAL " + functionManager.FMname + "] TriggerAdapter named: " + TriggerAdapter + " found.");
            }
            else {
              logger.warn("[HCAL " + functionManager.FMname + "] no TriggerAdapter found.\nProbably this is OK if we are in LocalMultiPartitionReadOut.");
            }

          }
          catch (XDAQTimeoutException e) {
            String errMessage = "[HCAL " + functionManager.FMname + "] Error! XDAQTimeoutException: getTriggerAdapter()\n Perhaps the trigger adapter application is dead!?";
            functionManager.goToError(errMessage,e);
          }
          catch (XDAQException e) {
            String errMessage = "[HCAL " + functionManager.FMname + "] Error! XDAQException: getTriggerAdapter()";
            functionManager.goToError(errMessage,e);
          }
        }

        functionManager.containerTriggerAdapter = new XdaqApplicationContainer(functionManager.containerXdaqApplication.getApplicationsOfClass(TriggerAdapter));

        if (functionManager.containerTriggerAdapter.isEmpty()) {
          String errMessage = "[HCAL " + functionManager.FMname + "] Error! Not at least one TriggerAdapter with name " +  TriggerAdapter + " found. This is not good ...";
          functionManager.goToError(errMessage);
        }

      }
      else if (!functionManager.FMrole.equals("Level2_TCDSLPM")){
        String errMessage = "[HCAL " + functionManager.FMname + "] Error! No HCAL supervisor found: getTriggerAdapter()";
        functionManager.goToError(errMessage);
      }
    }
  }

  // check the status of the TriggerAdapter and wait until it is in the "Ready", "Failed" state or it takes longer than TimeOut [sec]
  protected void waitforTriggerAdapter(int TimeOut) {
    if (functionManager.containerTriggerAdapter!=null) {
      if (!functionManager.containerTriggerAdapter.isEmpty()) {

        {
          String debugMessage = "[HCAL " + functionManager.FMname + "] TriggerAdapter found for asking its state - good!";
          logger.debug(debugMessage);
        }

        XDAQParameter pam = null;
        String status = "undefined";
        int elapsedseconds = 0;
        int counter = 0;

        // ask for the status of the TriggerAdapter and wait until it is Ready, Failed or it takes longer than 60s
        for (QualifiedResource qr : functionManager.containerTriggerAdapter.getApplications() ){
          if (TimeOut!=0) {
            while ((!status.equals("Ready")) && (!status.equals("Failed")) && (elapsedseconds<=TimeOut)) {
              try {
                if (elapsedseconds%10==0) {
                  logger.debug("[HCAL " + functionManager.FMname + "] asking for the TriggerAdapter stateName after requesting: " + TriggersToTake + " events (with " + TimeOut + "sec time out enabled) ...");
                }

                elapsedseconds +=5;
                try { Thread.sleep(1000); }
                catch (Exception ignored) {}

                pam =((XdaqApplication)qr).getXDAQParameter();

                pam.select(new String[] {"stateName", "NextEventNumber"});
                pam.get();
                status = pam.getValue("stateName");

                String NextEventNumberString = pam.getValue("NextEventNumber");
                Double NextEventNumber = Double.parseDouble(NextEventNumberString);

                if (TriggersToTake.doubleValue()!=0) {
                  localcompletion = NextEventNumber/TriggersToTake.doubleValue();
                }

                if (elapsedseconds%15==0) {
                  logger.debug("[HCAL " + functionManager.FMname + "] state of the TriggerAdapter stateName is: " + status + ".\nThe NextEventNumberString is: " + NextEventNumberString + ". \nThe local completion is: " + localcompletion + " (" + NextEventNumber + "/" + TriggersToTake.doubleValue() + ")");
                }

              }
              catch (XDAQTimeoutException e) {
                String errMessage = "[HCAL " + functionManager.FMname + "] Error! XDAQTimeoutException: waitforTriggerAdapter()\n Perhaps the trigger adapter application is dead!?";
                logger.error(errMessage,e);
                functionManager.sendCMSError(errMessage);
                functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("STATE",new StringT("Error")));
                functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("ACTION_MSG",new StringT("oops - technical difficulties ...")));
                if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; return;}
              }
              catch (XDAQException e) {
                String errMessage = "[HCAL " + functionManager.FMname + "] Error! XDAQException: waitforTriggerAdapter()";
                logger.error(errMessage,e);
                functionManager.sendCMSError(errMessage);
                functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("STATE",new StringT("Error")));
                functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("ACTION_MSG",new StringT("oops - technical difficulties ...")));
                if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; return;}
              }
            }

            logger.debug("[HCAL " + functionManager.FMname + "] The data was taken in about: " + elapsedseconds + " sec (+ " + TimeOut + " sec timeout)");
            functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("ACTION_MSG",new StringT("needed " + elapsedseconds + " sec (+60 sec)")));
          }
          else {
            while ((!status.equals("Ready")) && (!status.equals("Failed"))) {
              try {
                if (counter%10==0) {
                  logger.debug("[HCAL " + functionManager.FMname + "] asking for the TriggerAdapter stateName after requesting: " + TriggersToTake + " events ...");
                }

                counter +=5;
                try { Thread.sleep(1000); }
                catch (Exception ignored) {}

                pam =((XdaqApplication)qr).getXDAQParameter();

                pam.select(new String[] {"stateName", "NextEventNumber"});
                pam.get();
                status = pam.getValue("stateName");

                String NextEventNumberString = pam.getValue("NextEventNumber");
                Double NextEventNumber = Double.parseDouble(NextEventNumberString);

                if (TriggersToTake.doubleValue()!=0) {
                  localcompletion = NextEventNumber/TriggersToTake.doubleValue();
                }

                if (elapsedseconds%15==0) {
                  logger.debug("[HCAL " + functionManager.FMname + "] state of the TriggerAdapter stateName is: " + status + ".\nThe NextEventNumberString is: " + NextEventNumberString + ". \nThe local completion is: " + localcompletion + " (" + NextEventNumber + "/" + TriggersToTake.doubleValue() + ")");
                }
              }
              catch (XDAQTimeoutException e) {
                String errMessage = "[HCAL " + functionManager.FMname + "] Error! XDAQTimeoutException: waitforTriggerAdapter()\n Perhaps the trigger adapter application is dead!?";
                logger.error(errMessage,e);
                functionManager.sendCMSError(errMessage);
                functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("STATE",new StringT("Error")));
                functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("ACTION_MSG",new StringT("oops - technical difficulties ...")));
                if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; return;}
              }
              catch (XDAQException e) {
                String errMessage = "[HCAL " + functionManager.FMname + "] Error! XDAQException: waitforTriggerAdapter()";
                logger.error(errMessage,e);
                functionManager.sendCMSError(errMessage);
                functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("STATE",new StringT("Error")));
                functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("ACTION_MSG",new StringT("oops - technical difficulties ...")));
                if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; return;}
              }
            }
          }
        }

        if (status.equals("Failed")) {
          String errMessage = "[HCAL " + functionManager.FMname + "] Error! TriggerAdapter reports error state: " + status + ". Please check log messages which were sent earlier than this one for more details ... (E1)";
          logger.error(errMessage);
          functionManager.sendCMSError(errMessage);
          functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("STATE",new StringT("Error")));
          functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("ACTION_MSG",new StringT("oops - technical difficulties ...")));
          if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; return;}
        }
        if (elapsedseconds>TimeOut) {
          String errMessage = "[HCAL " + functionManager.FMname + "] Error! TriggerAdapter timed out (> " + TimeOut + "sec). Please check log messages which were sent earlier than this one for more details ... (E2)";
          logger.error(errMessage);
          functionManager.sendCMSError(errMessage);
          functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("STATE",new StringT("Error")));
          functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("ACTION_MSG",new StringT("oops - technical difficulties ...")));
          if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; return;}
        }
      }
      else {
        String errMessage = "[HCAL " + functionManager.FMname + "] Error! No TriggerAdapter found: waitforTriggerAdapter()";
        logger.error(errMessage);
        functionManager.sendCMSError(errMessage);
        functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("STATE",new StringT("Error")));
        functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("ACTION_MSG",new StringT(errMessage)));
        if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; return;}
      }
    }
  }

  // initialize qualified group, i.e. all XDAQ executives
  protected void initXDAQ() throws UserActionException{

    //Get the updated FM qg
    QualifiedGroup qg = functionManager.getQualifiedGroup();

    //Always set TCDS executive and xdaq apps to initialized and the Job control to Active false
    maskTCDSExecAndJC(qg);

    // Fill containers of TCDS apps
    //functionManager.containerXdaqServiceApplication = new XdaqApplicationContainer(qg.seekQualifiedResourcesOfType(new XdaqServiceApplication()));
    //XdaqApplicationContainer XdaqServiceAppContainer    = functionManager.containerXdaqServiceApplication ;
    functionManager.containerXdaqApplication = new XdaqApplicationContainer(qg.seekQualifiedResourcesOfType(new XdaqApplication()));
    XdaqApplicationContainer XdaqAppContainer    = functionManager.containerXdaqApplication ;
    
    List<XdaqApplication> tcdsList = new ArrayList<XdaqApplication>();
    tcdsList.addAll(XdaqAppContainer.getApplicationsOfClass("tcds::lpm::LPMController"));
    tcdsList.addAll(XdaqAppContainer.getApplicationsOfClass("tcds::ici::ICIController"));
    tcdsList.addAll(XdaqAppContainer.getApplicationsOfClass("tcds::pi::PIController"));
    functionManager.containerTCDSControllers = new XdaqApplicationContainer(tcdsList);
    functionManager.containerlpmController   = new XdaqApplicationContainer(XdaqAppContainer.getApplicationsOfClass("tcds::lpm::LPMController"));
    functionManager.containerICIController   = new XdaqApplicationContainer(XdaqAppContainer.getApplicationsOfClass("tcds::ici::ICIController"));
    functionManager.containerPIController    = new XdaqApplicationContainer(XdaqAppContainer.getApplicationsOfClass("tcds::pi::PIController"));

    try {
      qg.init();
    }
    catch (Exception e) {
      // failed to init
      StringWriter sw = new StringWriter();
      e.printStackTrace( new PrintWriter(sw) );
      System.out.println(sw.toString());
      //String errMessage = "[HCAL " + functionManager.FMname + "] " + this.getClass().toString() + " failed to initialize resources. Printing stacktrace: "+ sw.toString();
      throw new UserActionException(e.getMessage());
    }

    ////////////////////////////////////////
    // Fill containers for levelOne FM
    ////////////////////////////////////////
    // Get list of childFMs from QG
    List<QualifiedResource> childFMs = qg.seekQualifiedResourcesOfType(new FunctionManager());
    functionManager.containerFMChildren = new QualifiedResourceContainer(childFMs);
    functionManager.containerAllFMChildren = new QualifiedResourceContainer(childFMs);
    // Fill containerFMchildren with Active FMs only
    List<QualifiedResource> ActiveChildFMs = functionManager.containerFMChildren.getActiveQRList();
    functionManager.containerFMChildren   = new QualifiedResourceContainer(ActiveChildFMs);

    functionManager.containerFMEvmTrig = new QualifiedResourceContainer(qg.seekQualifiedResourcesOfRole("EvmTrig"));
    functionManager.containerFMTCDSLPM = new QualifiedResourceContainer(qg.seekQualifiedResourcesOfRole("Level2_TCDSLPM"));
    //Empty the container if LPM FM is masked
    functionManager.containerFMTCDSLPM = new QualifiedResourceContainer(functionManager.containerFMTCDSLPM.getActiveQRList());
    ActiveChildFMs.removeAll(qg.seekQualifiedResourcesOfRole("EvmTrig"));
    ActiveChildFMs.removeAll(qg.seekQualifiedResourcesOfRole("Level2_TCDSLPM"));

    functionManager.containerFMChildrenNoEvmTrigNoTCDSLPM = new QualifiedResourceContainer(ActiveChildFMs);

    // see if we have any "special" FMs
    List<FunctionManager> evmTrigList = new ArrayList<FunctionManager>();
    List<FunctionManager> normalList = new ArrayList<FunctionManager>();
    // see if we have any "special" FMs; store them in containers
    for(QualifiedResource qr : functionManager.containerFMChildren.getActiveQRList()){
      FunctionManager fmChild = (FunctionManager)qr;
      if (fmChild.getRole().toString().equals("EvmTrig")){
          evmTrigList.add(fmChild);
      }
      else{
          normalList.add(fmChild);
      }
    }

    functionManager.containerFMChildrenEvmTrig = new QualifiedResourceContainer(evmTrigList);
    functionManager.containerFMChildrenNormal = new QualifiedResourceContainer(normalList);

    ////////////////////////////////////////
    // Fill applications for level two FMs
    //////////////////////////////////////////    
    functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("ACTION_MSG",new StringT("Retrieving HCAL XDAQ applications ...")));
    // find xdaq applications
    List<QualifiedResource> xdaqList = qg.seekQualifiedResourcesOfType(new XdaqApplication());
    functionManager.containerXdaqApplication = new XdaqApplicationContainer(xdaqList);
    logger.debug("[HCAL " + functionManager.FMname + "] Number of XDAQ applications controlled: " + xdaqList.size() );

    functionManager.containerhcalSupervisor = new XdaqApplicationContainer(functionManager.containerXdaqApplication.getApplicationsOfClass("hcalSupervisor"));

    // TA container to be filled by getTriggerAdapter
    functionManager.containerTriggerAdapter = new XdaqApplicationContainer(new ArrayList<XdaqApplication>());

    
    functionManager.containerhcalDCCManager = new XdaqApplicationContainer(functionManager.containerXdaqApplication.getApplicationsOfClass("hcalDCCManager"));
    functionManager.containerTTCciControl   = new XdaqApplicationContainer(functionManager.containerXdaqApplication.getApplicationsOfClass("ttc::TTCciControl"));

    // workaround for old HCAL teststands
    if (functionManager.containerTTCciControl.isEmpty()) {
      functionManager.containerTTCciControl   = new XdaqApplicationContainer(functionManager.containerXdaqApplication.getApplicationsOfClass("TTCciControl"));
    }

    functionManager.containerLTCControl     = new XdaqApplicationContainer(functionManager.containerXdaqApplication.getApplicationsOfClass("ttc::LTCControl"));

    // workaround for old HCAL teststands
    if (functionManager.containerLTCControl.isEmpty()) {
      functionManager.containerLTCControl     = new XdaqApplicationContainer(functionManager.containerXdaqApplication.getApplicationsOfClass("LTCControl"));
    }

    functionManager.containerEVM               = new XdaqApplicationContainer(functionManager.containerXdaqApplication.getApplicationsOfClass("EVM"));
    functionManager.containerBU                = new XdaqApplicationContainer(functionManager.containerXdaqApplication.getApplicationsOfClass("BU"));
    functionManager.containerRU                = new XdaqApplicationContainer(functionManager.containerXdaqApplication.getApplicationsOfClass("RU"));
    functionManager.containerFUResourceBroker  = new XdaqApplicationContainer(functionManager.containerXdaqApplication.getApplicationsOfClass("evf::FUResourceBroker"));
    functionManager.containerFUEventProcessor  = new XdaqApplicationContainer(functionManager.containerXdaqApplication.getApplicationsOfClass("evf::FUEventProcessor"));
    functionManager.containerStorageManager    = new XdaqApplicationContainer(functionManager.containerXdaqApplication.getApplicationsOfClass("StorageManager"));
    functionManager.containerPeerTransportATCP = new XdaqApplicationContainer(functionManager.containerXdaqApplication.getApplicationsOfClass("pt::atcp::PeerTransportATCP"));
    functionManager.containerhcalRunInfoServer = new XdaqApplicationContainer(functionManager.containerXdaqApplication.getApplicationsOfClass("hcalRunInfoServer"));


    if (!functionManager.containerPeerTransportATCP.isEmpty()) {
      logger.debug("[HCAL " + functionManager.FMname + "] Found PeerTransportATCP applications - will handle them ...");
    }

    

    // find out if HCAL supervisor is ready for async SOAP communication
    if (!functionManager.containerhcalSupervisor.isEmpty()) {
      // Set FM_PARTITION and put that into a parameter
      functionManager.FMpartition = functionManager.FMname.substring(5);  // FMname = HCAL_X;
      functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("FM_PARTITION",new StringT(functionManager.FMpartition)));

      FillTCDScontainersWithURI();

      XDAQParameter pam = null;

      String dowehaveanasynchcalSupervisor="undefined";

      // ask for the status of the HCAL supervisor and wait until it is Ready or Failed
      for (QualifiedResource qr : functionManager.containerhcalSupervisor.getApplications() ){

        try {
          pam =((XdaqApplication)qr).getXDAQParameter();
          pam.select(new String[] {"TriggerAdapterName", "PartitionState", "InitializationProgress","ReportStateToRCMS"});
          pam.get();

          dowehaveanasynchcalSupervisor = pam.getValue("ReportStateToRCMS");

          logger.info("[HCAL " + functionManager.FMname + "] initXDAQ(): asking for the HCAL supervisor ReportStateToRCMS results is: " + dowehaveanasynchcalSupervisor);

        }
        catch (XDAQTimeoutException e) {
          String errMessage = "[HCAL " + functionManager.FMname + "] Error! XDAQTimeoutException in initXDAQ() when checking the async SOAP capabilities ...\n Perhaps the HCAL supervisor application is dead!?";
          functionManager.goToError(errMessage,e);
        }
        catch (XDAQException e) {
          String errMessage = "[HCAL " + functionManager.FMname + "] Error! XDAQException in initXDAQ() when checking the async SOAP capabilities ...";
          functionManager.goToError(errMessage,e);
        }

        logger.info("[HCAL " + functionManager.FMname + "] using async SOAP communication with HCAL supervisor ...");
      }
    }
    else {
      logger.info("[HCAL " + functionManager.FMname + "] Warning! No HCAL supervisor found in initXDAQ().\nThis happened when checking the async SOAP capabilities.\nThis is OK for a level1 FM.");
    }


    // define the condition state vectors only here since the group must have been qualified before and all containers are filled
    functionManager.defineConditionState();
    functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("ACTION_MSG",new StringT("")));
  }

  //Set instance numbers and HandleLPM after initXDAQ()
  protected void initXDAQinfospace() {
      Integer sid = ((IntegerT)functionManager.getHCALparameterSet().get("SID").getValue()).getInteger();
      String ruInstance = ((StringT)functionManager.getHCALparameterSet().get("RU_INSTANCE").getValue()).getString();
      List<QualifiedResource> xdaqApplicationList = functionManager.getQualifiedGroup().seekQualifiedResourcesOfType(new XdaqApplication());
      QualifiedResourceContainer qrc = new QualifiedResourceContainer(xdaqApplicationList);
      for (QualifiedResource qr : qrc.getActiveQRList()) {
          try {
            XDAQParameter pam = null;
            pam = ((XdaqApplication)qr).getXDAQParameter();

            List<String> pamNames      = pam.getNames();
            List<String> pamNamesToSet = new ArrayList<String>();
            if (pamNames.contains("RUinstance")) {              pamNamesToSet.add("RUinstance")              ;}
            if (pamNames.contains("BUInstance")) {              pamNamesToSet.add("BUInstance")              ;}
            if (pamNames.contains("EVMinstance")){              pamNamesToSet.add("EVMinstance")             ;}
            //KKH: These are for local DAQ
            //if (pamNames.contains("EnableDisableTTCOrTCDS")) {  pamNamesToSet.add("EnableDisableTTCOrTCDS")  ;}
            
            // WARNING: Cannot reuse pam for the same Xdaq, select the paramters needed in array and then send.
            if(pamNamesToSet.size()!=0){        
              String[] pamNamesToSet_str = new String[pamNamesToSet.size()];
              pamNamesToSet_str = pamNamesToSet.toArray(pamNamesToSet_str);

              pam.select(pamNamesToSet_str);
              if (pamNamesToSet.contains("RUinstance")) {              pam.setValue("RUinstance", ruInstance.split("_")[1])              ;}
              if (pamNamesToSet.contains("BUInstance")) {              pam.setValue("BUInstance", ruInstance.split("_")[1])              ;}
              if (pamNamesToSet.contains("EVMinstance")){              pam.setValue("EVMinstance", ruInstance.split("_")[1])             ;}
              // KKH: these are for local DAQ
              //if (pamNamesToSet.contains("EnableDisableTTCOrTCDS")) {  pam.setValue("EnableDisableTTCOrTCDS","false")                    ;}
 
              logger.info("[HCAL LVL2 " + functionManager.FMname + "]: initXDAQinfospace(): Setting parameters:"+pamNamesToSet.toString()+" in infospace of "+qr.getName() );

              pam.send();
            }
          }
          catch (ArrayIndexOutOfBoundsException e){
            String errMessage = "[HCAL " + functionManager.FMname + "] initXDAQinfospace(): Cannot find RUinstance(e.g.hcalEventBuilder) for " + qr.getName()+" . FM parameter RU_INSTANCE is "+ruInstance;
            functionManager.goToError(errMessage);
          }
          catch (XDAQTimeoutException e) {
            String errMessage = "[HCAL " + functionManager.FMname + "] Error! XDAQTimeoutException while querying the XDAQParameter names for " + qr.getName() + ". Message: " + e.getMessage();
            functionManager.goToError(errMessage);
          }
          catch (XDAQException e) {
            String errMessage = "[HCAL " + functionManager.FMname + "] Error! XDAQException while querying the XDAQParameter names for " + qr.getName() + ". Message: " + e.getMessage();
            functionManager.goToError(errMessage);
          }
        }
        for(XdaqApplication xdaqApp : functionManager.containerhcalSupervisor.getApplications()){
          try{
            if (functionManager.FMrole.equals("EvmTrig")){
              XDAQParameter pam = xdaqApp.getXDAQParameter();
              pam.select(new String[] {"SessionID"});
              pam.setValue("SessionID"  ,sid.toString());
              logger.info("[HCAL " + functionManager.FMname + "] Sent SID to supervisor: " + Sid);

              pam.send();
            }
            // ignoreEnable LPM for non-EvmTrig supervisors (LPM should be enabled by TA in EvmTrig FM)
            else{
              XDAQParameter pam = xdaqApp.getXDAQParameter();
              pam.select(new String[] {"SessionID","IgnoreForEnableVector"});
              pam.setValue("SessionID"  ,sid.toString());
              String [] appsToIgnoreAtEnable = {"tcds::lpm::LPMController"};
              pam.setVector("IgnoreForEnableVector"  ,appsToIgnoreAtEnable);
              logger.info("[HCAL " + functionManager.FMname + "] Sent SID to supervisor: " + Sid);
              logger.info("[HCAL " + functionManager.FMname + "] Sent IgnoreForEnableVector to supervisor: tcds::lpm::LPMController");

              pam.send();
            }
          }
          catch (XDAQTimeoutException e) {
            String errMessage = "[HCAL " + functionManager.FMname + "] Error! initXDAQinfospace(): XDAQTimeoutException: when trying init HCAL supervisor infospace";
            functionManager.goToError(errMessage,e);
          }
          catch (XDAQException e) {
            String errMessage = "[HCAL " + functionManager.FMname + "] Error! initXDAQinfospace(): XDAQException: when trying init HCAL supervisor infospace";
            functionManager.goToError(errMessage,e);
          }
        }
  }


  // prepare SOAP bag for sTTS test
  protected XDAQMessage getTTSBag(String TTSMessage, int sourceid, int cycle, int value) throws XDAQMessageException {
    Map<String, String> v=new HashMap<String, String>();
    Map<String, String> t=new HashMap<String, String>();

    v.put("sourceId",Integer.toString(sourceid));
    t.put("sourceId","xsd:integer");
    v.put("cycle",Integer.toString(cycle));
    t.put("cycle","xsd:integer");
    v.put("value",Integer.toString(value));
    t.put("value","xsd:integer");

    return xdaqMsgWithParameters(TTSMessage,v,t);
  }

  // helper function for getTTSBag(..)
  private XDAQMessage xdaqMsgWithParameters(String command, Map valuesMap, Map typesMap) throws XDAQMessageException {

    XDAQMessage xdaqMsg = new XDAQMessage( command );

    Document document = (Document)xdaqMsg.getDOM();

    Element cmd = (Element)document.getElementsByTagNameNS(XDAQ_NS, command ).item(0);

    Iterator it = valuesMap.keySet().iterator();

    while (it.hasNext()) {
      String key = (String)it.next();
      String value = (String)valuesMap.get(key);
      String typestr = (String) typesMap.get(key);

      Element item=document.createElementNS(XDAQ_NS, key);
      item.setAttributeNS("http://www.w3.org/2001/XMLSchema-instance","xsi:type",typestr);
      item.appendChild(document.createTextNode(value));
      cmd.appendChild(item);
    }

    xdaqMsg.setDOM(document);

    return xdaqMsg;
  }

  // get official CMS run and sequence number
  protected RunNumberData getOfficialRunNumber() {

    // check availability of runInfo DB
    RunInfoConnectorIF ric = functionManager.getRunInfoConnector();
    // Get SID from parameter
    Sid = ((IntegerT)functionManager.getParameterSet().get("SID").getValue()).getInteger();
    if ( ric == null ) {
      logger.error("[HCAL " + functionManager.FMname + "] RunInfoConnector is empty i.e. Is there a RunInfo DB? Or is RunInfo DB down?");

      // by default give run number 0
      return new RunNumberData(new Integer(Sid),new Integer(0),functionManager.getOwner(),Calendar.getInstance().getTime());
    }
    else {
      RunSequenceNumber rsn = new RunSequenceNumber(ric,functionManager.getOwner(),RunSequenceName);
      RunNumberData rnd = rsn.createRunSequenceNumber(Sid);

      logger.info("[HCAL " + functionManager.FMname + "] received run number: " + rnd.getRunNumber() + " and sequence number: " + rnd.getSequenceNumber());

      functionManager.HCALRunInfo = null; // make RunInfo ready for the next round of run info to store

      return rnd;
    }
  }

  // establish connection to RunInfoDB - if needed
  protected void checkRunInfoDBConnection() {
    if (functionManager.HCALRunInfo == null) {
      logger.info("[HCAL " + functionManager.FMname + "] creating new RunInfo accessor with namespace: " + functionManager.HCAL_NS + " now ...");

      //Get SID from parameter
      Sid = ((IntegerT)functionManager.getParameterSet().get("SID").getValue()).getInteger();

      RunInfoConnectorIF ric = functionManager.getRunInfoConnector();
      functionManager.HCALRunInfo =  new RunInfo(ric,Sid,Integer.valueOf(functionManager.RunNumber));

      functionManager.HCALRunInfo.setNameSpace(functionManager.HCAL_NS);

      logger.info("[HCAL " + functionManager.FMname + "] ... RunInfo accessor available.");
    }
  }

  // method to call for publishing runinfo
  protected void publishLocalParameter (String nameForDB, String parameterString) {
    Parameter<StringT> parameter;
    if (!parameterString.equals("")) {
      parameter = new Parameter<StringT>(nameForDB, new StringT(parameterString));
    }
    else {
      parameter = new Parameter<StringT>(nameForDB, new StringT("empty string"));
    }
    try {
      logger.debug("[HCAL " + functionManager.FMname + "] Publishing local parameter  '" + nameForDB + "' to the RunInfo DB; value = " + parameter.getValue().toString());
      if (functionManager.HCALRunInfo!=null) { functionManager.HCALRunInfo.publish(parameter); }
    }
    catch (RunInfoException e) {
      String errMessage = "[HCAL " + functionManager.FMname + "] Error! RunInfoException caught when publishing the Runinfo parameter '" + nameForDB +".";
      logger.error(errMessage,e);
    }
  }

  protected void publishGlobalParameter (String nameForDB, String parameterName){
    String globalParameterString = ((StringT)functionManager.getHCALparameterSet().get(parameterName).getValue()).getString();
    Parameter<StringT> parameter;
    if (!globalParameterString.equals("")) {
      parameter = new Parameter<StringT>(nameForDB,new StringT(globalParameterString));
    }
    else {
      parameter = new Parameter<StringT>(nameForDB,new StringT("empty string"));
    }
    try {
      logger.debug("[HCAL " + functionManager.FMname + "] Publishing global parameter  '" + nameForDB + "' to the RunInfo DB; value = " + parameter.getValue().toString());
      if (functionManager.HCALRunInfo!=null) { functionManager.HCALRunInfo.publish(parameter); }
    }
    catch (RunInfoException e) {
      String errMessage = "[HCAL " + functionManager.FMname + "] Error! RunInfoException caught when publishing the Runinfo parameter '" + nameForDB +".";
      logger.error(errMessage,e);
    }
  }

  protected void publishGlobalParameter (String parameterName) {
    publishGlobalParameter(parameterName, parameterName);
  }

  // make entry into the CMS run info database
  protected void publishRunInfoSummary() {
    functionManager = this.functionManager;
    String globalParams[] = new String[] {"HCAL_LPMCONTROL", "HCAL_ICICONTROL_SINGLE","HCAL_ICICONTROL_MULTI",
                                          "HCAL_PICONTROL_SINGLE","HCAL_PICONTROL_MULTI", "HCAL_TTCCICONTROL",
                                          "SUPERVISOR_ERROR", "HCAL_COMMENT", "HCAL_CFGSCRIPT", "RUN_KEY",  
                                          "HCAL_TIME_OF_FM_START", "DQM_TASK", 
                                          "LOCAL_RUNKEY_SELECTED", "MASTERSNIPPET_SELECTED"};
    Hashtable<String, String> localParams = new Hashtable<String, String>();

    maskedAppsForRunInfo = ((VectorT<StringT>)functionManager.getParameterSet().get("MASKED_RESOURCES").getValue()).toString();
    emptyFMsForRunInfo   = ((VectorT<StringT>)functionManager.getParameterSet().get("EMPTY_FMS").getValue()).toString();

    localParams.put(   "FM_FULLPATH"           ,  functionManager.FMfullpath                  );
    localParams.put(   "FM_NAME"               ,  functionManager.FMname                      );
    localParams.put(   "FM_URL"                ,  functionManager.FMurl                       );
    localParams.put(   "FM_URI"                ,  functionManager.FMuri                       );
    localParams.put(   "FM_ROLE"               ,  functionManager.FMrole                      );
    localParams.put(   "STATE_ON_EXIT"         ,  functionManager.getState().getStateString() );
    localParams.put(   "TRIGGERS"              ,  String.valueOf(TriggersToTake)              );
    localParams.put(   "MASKED_RESOURCES"      ,  maskedAppsForRunInfo                        );
    localParams.put(   "EMPTY_FMS"             ,  emptyFMsForRunInfo                          );
    localParams.put(   "TRIGGERS"              ,  String.valueOf(TriggersToTake)              );

    // TODO JHak put in run start time and stop times. This was always broken.


    RunInfoPublish = ((BooleanT)functionManager.getHCALparameterSet().get("HCAL_RUNINFOPUBLISH").getValue()).getBoolean();

    if ( RunInfoPublish ) {
      logger.info("[HCAL " + functionManager.FMname + "]: publishingRunInfoSummary");
      // check availability of RunInfo DB
      
      checkRunInfoDBConnection();

      if ( functionManager.HCALRunInfo == null) {
        logger.warn("[HCAL " + functionManager.FMname + "] Cannot publish run info summary!");
      }
      else {
        logger.debug("[HCAL " + functionManager.FMname + "] Start of publishing to the RunInfo DB ...");
        // Publish the local parameters
        Set<String> localParamKeys = localParams.keySet();
        String lpKey;
        Iterator<String> lpi = localParamKeys.iterator();
        while (lpi.hasNext()) {
          lpKey = lpi.next();
          publishLocalParameter( lpKey,localParams.get(lpKey));
        }
        
        // Publish the global parameters
        for (String paramName : globalParams) {
          publishGlobalParameter(paramName);
        }
      }
      logger.info("[HCAL " + functionManager.FMname + "] finished publishing to the RunInfo DB.");
    }else{
      logger.info("[HCAL " + functionManager.FMname + "]: HCAL_RUNINFOPUBLISH is set to false. Not publishing RunInfo");
    }
  }

  // make entry into the CMS run info database with info from hcalRunInfoServer
  protected void publishRunInfoSummaryfromXDAQ() {
    RunInfoPublish = ((BooleanT)functionManager.getHCALparameterSet().get("HCAL_RUNINFOPUBLISH").getValue()).getBoolean();
    if (RunInfoPublish){
      logger.info("[HCAL " + functionManager.FMname + "]:  Going to publishRunInfoSummaryfromXDAQ");
      if ( functionManager.HCALRunInfo == null) {
        logger.warn("[HCAL " + functionManager.FMname + "]: [HCAL " + functionManager.FMname + "] Cannot publish run info summary!");
        logger.info("[HCAL " + functionManager.FMname + "]: [HCAL " + functionManager.FMname + "] RunInfoConnector is empty i.e.is RunInfo DB down? Please check the logs ...");
        // Make new connection if we want to publish but do not have RunInfo
        checkRunInfoDBConnection();
      }

      if (functionManager.HCALRunInfo!=null) {
        logger.debug("[HCAL " + functionManager.FMname + "]: publishRunInfoSummaryfromXDAQ: attempting to publish runinfo from xdaq after checking userXML...");

        // prepare and set for all HCAL supervisors the RunType
        if (functionManager.containerhcalRunInfoServer!=null && !functionManager.containerhcalRunInfoServer.isEmpty()) {
          logger.debug("[HCAL " + functionManager.FMname + "]: [HCAL " + functionManager.FMname + "] Start of publishing to the RunInfo DB the info from the hcalRunInfoServer ...");

          RunInfoServerReader RISR = new RunInfoServerReader();

          // find all RunInfoServers controlled by this FM and acquire the information
          for (QualifiedResource qr : functionManager.containerhcalRunInfoServer.getApplications() ) {
            RISR.acquire((XdaqApplication)qr);
          }

          // convert the acquired HashMap into the RunInfo structure
          HashMap<String,String> theInfo = RISR.getInfo();
          Iterator theInfoIterator = theInfo.keySet().iterator();

          while(theInfoIterator.hasNext()) {
            // get the next row from the HashMap
            String key = (String) theInfoIterator.next();
            String value = theInfo.get(key);
            String setValue = "not set";
            if (!value.equals("") && value != null) { setValue = value; }
            logger.debug("[HCAL " + functionManager.FMname + "] The next parameter from RunInfoFromXDAQ is: " + key + ", and it has value: " + value);

            // fill HashMap Strings into the RunInfo compliant data type
            if (!key.equals("") && key!=null) {
              try {
                logger.debug("[HCAL " + functionManager.FMname + "] Publishing the XDAQ RunInfo parameter with key name: " + key + " to the RunInfo database.");
                functionManager.HCALRunInfo.publishWithHistory(new Parameter<StringT>(key, new StringT(setValue)));
              }
              catch (RunInfoException e) {
                String errMessage = "[HCAL " + functionManager.FMname + "] Error: caught a RunInfoException whemn attempting to publish XDAQ RunInfo parameter with key name: " + key;
                logger.error(errMessage,e);
              }
            }
          }
          logger.info("[HCAL " + functionManager.FMname + "] publishRunInfoSummaryfromXDAQ done");
        }
        else if (!(functionManager.FMrole.equals("Level2_TCDSLPM") || functionManager.FMrole.contains("TTCci"))) {
          String errMessage = "[HCAL " + functionManager.FMname + "] Error! publishRunInfoSummaryfromXDAQ() requested but no hcalRunInfoServer application found - please check!";
          logger.error(errMessage);
        }
      }
      else {
        logger.info("[HCAL " + functionManager.FMname + "] publishRunInfofromXDAQ(): Tried to publish but HCALRunInfo was null.... bad.");
      }
    }
    else{
      logger.info("[HCAL " + functionManager.FMname + "]: HCAL_RUNINFOPUBLISH is set to false. Not publishing RunInfo");
    }
  }

  // Computes new FSM State based on all child FMs
  // newState: state notification from a Resource
  // toState: target state
  public void computeNewState(StateNotification newState) {

    //logger.warn("[SethLog HCAL " + functionManager.FMname + "] 1 BEGIN computeNewState(): calculating new state for FM\n@ URI: "+ functionManager.getURI());

    if (newState.getToState() == null) {
      logger.debug("[HCAL " + functionManager.FMname + "] computeNewState() is receiving a state with empty ToState\nfor FM @ URI: "+ functionManager.getURI());
      return;
    }
    else {
      logger.info("[SethLog HCAL " + functionManager.FMname + "] 2 received id: " + newState.getIdentifier() + ", ToState: " + newState.getToState());
    }

    // search the resource which sent the notification
    QualifiedResource resource = null;
    try {
      resource = functionManager.getQualifiedGroup().seekQualifiedResourceOfURI(new URI(newState.getIdentifier()));
    }
    catch (URISyntaxException e) {
      String errMessage = "[HCAL " + functionManager.FMname + "] Error! computeNewState() for FM\n@ URI: " + functionManager.getURI() + "\nthe Resource: " + newState.getIdentifier() + " reports an URI exception!";
      functionManager.goToError(errMessage,e);
    }

    // check if the resource was a FM or xdaq app
    if (checkIfControlledResource(resource)) {
      // check if it's a tcds app
      for (QualifiedResource app : functionManager.containerTCDSControllers.getQualifiedResourceList()) {
        if(app.getURL().equals(resource.getURL())) {
          if(!functionManager.containerhcalSupervisor.isEmpty()){ // we have a supervisor to listen to; ignore all TCDS notifications
            return;
          }
          if(!functionManager.FMrole.equals("Level2_TCDSLPM")) { // no supervisor, but this is not a TCDS LPM FM: we are not expecting this to happen
            logger.warn("[HCAL " + functionManager.FMname + "] Warning: Ignoring TCDS state notification, but this FM is not a TCDSLPM FM and does not have a supervisor either! This is unexpected.");
            return; 
          }
        }
      }
      if (newState.getToState().equals(HCALStates.ERROR.getStateString()) || newState.getToState().equals(HCALStates.FAILED.getStateString())) {
        String errMessage = "[HCAL " + functionManager.FMname + "] Error! computeNewState() for FM\n@ URI: " + functionManager.getURI() + "\nthe Resource: " + newState.getIdentifier() + " reports an error state!";
        functionManager.goToError(errMessage);
      }
      else {
        functionManager.calcState = functionManager.getUpdatedState();

        logger.info("[SethLog HCAL " + functionManager.FMname + "] 3 calcState = " + functionManager.calcState.getStateString() + ", from state (actualState): " + functionManager.getState().getStateString() + "\nfor FM: " + functionManager.getURI());

        if (!functionManager.calcState.getStateString().equals("Undefined") && !functionManager.calcState.getStateString().equals(functionManager.getState().getStateString())) {
          logger.debug("[HCAL " + functionManager.FMname + "] new state = " + functionManager.calcState.getStateString() + " for FM: " + functionManager.getURI());

          {
            String actualState = functionManager.getState().getStateString();
            String toState = functionManager.calcState.getStateString();

            String errMessage = "[HCAL " + functionManager.FMname + "] Error! static state to go not found in computeNewState() for FM\n@ URI: " + functionManager.getURI() + "\nthe Resource: " + newState.getIdentifier() + " reports an error state! From state: " + actualState + " to state: " + toState;

            if (toState.equals(HCALStates.TTSTEST_MODE.getStateString())) {
              if (actualState.equals(HCALStates.PREPARING_TTSTEST_MODE.getStateString())) { functionManager.fireEvent(HCALInputs.SETTTSTEST_MODE); }
              else if (actualState.equals(HCALStates.TESTING_TTS.getStateString()))       { functionManager.fireEvent(HCALInputs.SETTTSTEST_MODE); }
              else {
                functionManager.goToError(errMessage);
              }
            }
            else if (toState.equals(HCALStates.INITIAL.getStateString())) {
              if (actualState.equals(HCALStates.RECOVERING.getStateString())) { functionManager.fireEvent(HCALInputs.SETINITIAL); }
              else {
                functionManager.goToError(errMessage);
              }
            }
            else if (toState.equals(HCALStates.HALTED.getStateString())) {
              if (actualState.equals(HCALStates.INITIALIZING.getStateString()))    {
                functionManager.theStateNotificationHandler.setTimeoutThread(false); // have to unset timeout thread here
                if (!functionManager.containerFMChildren.isEmpty()) {
                  //logger.warn("[SethLog HCAL " + functionManager.FMname + "] computeNewState() we are in initializing and have no FM children so functionManager.fireEvent(HCALInputs.SETHALT)");
                  functionManager.fireEvent(HCALInputs.SETHALT);
                }
                functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("ACTION_MSG",new StringT("... task done.")));
              }
              else if (actualState.equals(HCALStates.HALTING.getStateString()))       {
                //logger.warn("[SethLog HCAL " + functionManager.FMname + "] computeNewState() we are in halting so functionManager.fireEvent(HCALInputs.SETHALT)");
                functionManager.fireEvent(HCALInputs.SETHALT); }
              else if (actualState.equals(HCALStates.RECOVERING.getStateString()))    {
                //logger.warn("[SethLog HCAL " + functionManager.FMname + "] computeNewState() we are in recovering so functionManager.fireEvent(HCALInputs.SETHALT)");
                functionManager.fireEvent(HCALInputs.SETHALT); }
              else if (actualState.equals(HCALStates.RESETTING.getStateString()))     {
                //logger.warn("[SethLog HCAL " + functionManager.FMname + "] computeNewState() we are in resetting so functionManager.fireEvent(HCALInputs.SETHALT)");
                functionManager.fireEvent(HCALInputs.SETHALT); }
              else if (actualState.equals(HCALStates.CONFIGURING.getStateString()))   { /* do nothing */ }
              else if (actualState.equals(HCALStates.COLDRESETTING.getStateString())) { functionManager.fireEvent(HCALInputs.SETCOLDRESET); }
              else {
                functionManager.goToError(errMessage);
              }
            }
            else if (toState.equals(HCALStates.CONFIGURED.getStateString())) {
              if (actualState.equals(HCALStates.INITIALIZING.getStateString()) || actualState.equals(HCALStates.RECOVERING.getStateString()) ||
                  actualState.equals(HCALStates.RUNNING.getStateString()) || actualState.equals(HCALStates.RUNNINGDEGRADED.getStateString()) ||
                  actualState.equals(HCALStates.CONFIGURING.getStateString()) || actualState.equals(HCALStates.STOPPING.getStateString())) {
                //logger.warn("[HCAL " + functionManager.FMname + "] HCALEventHandler actualState is "+ actualState+", but SETCONFIGURE ...");
                functionManager.fireEvent(HCALInputs.SETCONFIGURE);
              }
              else if (actualState.equals(HCALStates.STARTING.getStateString())) { /* do nothing */ }
              else {
                functionManager.goToError(errMessage);
              }
            }
            else if (toState.equals(HCALStates.RUNNING.getStateString())) {
              if (actualState.equals(HCALStates.INITIALIZING.getStateString()))     {
                //logger.warn("[HCAL " + functionManager.FMname + "] HCALEventHandler actualState is INITIALIZING, but SETSTART ...");
                functionManager.fireEvent(HCALInputs.SETSTART);
              }
              else if (actualState.equals(HCALStates.RECOVERING.getStateString()))  {
                //logger.warn("[HCAL " + functionManager.FMname + "] HCALEventHandler actualState is RECOVERING, but SETSTART ...");
                functionManager.fireEvent(HCALInputs.SETSTART);
              }
              else if (actualState.equals(HCALStates.CONFIGURING.getStateString())) { /* do nothing */ }
              else if (actualState.equals(HCALStates.STARTING.getStateString()))    {
                //logger.warn("[HCAL " + functionManager.FMname + "] HCALEventHandler actualState is "+actualState+", but SETSTART ...");
                functionManager.fireEvent(HCALInputs.SETSTART);
              }
              else if (actualState.equals(HCALStates.RESUMING.getStateString()))   { functionManager.fireEvent(HCALInputs.SETRESUME); }
              else if (actualState.equals(HCALStates.HALTING.getStateString()))    { /* do nothing */ }
              else {
                functionManager.goToError(errMessage);
              }
            }
            else if (toState.equals(HCALStates.PAUSED.getStateString())) {
              if (actualState.equals(HCALStates.PAUSING.getStateString()))         { functionManager.fireEvent(HCALInputs.SETPAUSE); }
              else if (actualState.equals(HCALStates.RECOVERING.getStateString())) { functionManager.fireEvent(HCALInputs.SETPAUSE); }
              else {
                functionManager.goToError(errMessage);
              }
            }
            else if (toState.equals(HCALStates.STOPPING.getStateString())) {
              if (actualState.equals(HCALStates.RUNNING.getStateString()) || actualState.equals(HCALStates.RUNNINGDEGRADED.getStateString()))       { functionManager.fireEvent(HCALInputs.STOP); }
              else if (actualState.equals(HCALStates.STARTING.getStateString())) { /* do nothing */ }
              else {
                functionManager.goToError(errMessage);
              }
            }
            else {
              String errMessage2 = "[HCAL " + functionManager.FMname + "] Error! transitional state not found in computeNewState() for FM\n@ URI: " + functionManager.getURI() + "\nthe Resource: " + newState.getIdentifier() + " reports an error state!\nFrom state: " + functionManager.getState().getStateString() + " \nstate: " + functionManager.calcState.getStateString();
              functionManager.goToError(errMessage2);
            }
          }
        }
      }
    }
  }

  // Checks if the FM resource is inside the StateVector
  private boolean checkIfControlledResource(QualifiedResource resource) {
    boolean foundResource = false;

    if (resource.getResource().getQualifiedResourceType().equals("rcms.fm.resource.qualifiedresource.FunctionManager") || resource.getResource().getQualifiedResourceType().equals("rcms.fm.resource.qualifiedresource.XdaqApplication") || resource.getResource().getQualifiedResourceType().equals("rcms.fm.resource.qualifiedresource.XdaqServiceApplication")) {
      foundResource = true;

      logger.debug("[HCAL " + functionManager.FMname + "] ... got asynchronous StateNotification from controlled ressource");

    }
    return foundResource;
  }

  // Checks if the FM resource is in an ERROR state
  protected boolean checkIfErrorState(FunctionManager fmChild) {
    boolean answer = false;

    if ((fmChild.isInitialized()) && (fmChild.refreshState().toString().equals(HCALStates.ERROR.toString()))) {
      answer = true;

      String errMessage = "[HCAL LVL1 " + functionManager.FMname + "] Error! state of the LVL2 FM with role: " + fmChild.getRole().toString() + "\nPlease check the chainsaw logs, jobcontrol, etc. The name of this FM is: " + fmChild.getName().toString() +"\nThe URI is: " + fmChild.getURI().toString();
      logger.error(errMessage);
      functionManager.sendCMSError(errMessage);
      functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("STATE",new StringT("Error")));
      functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("ACTION_MSG",new StringT("oops - technical difficulties ...")));
      if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; }
    }
    return answer;
  }

  // calculates the completion status and incorporates the status of possible child FMs
  protected void pollCompletion() {

    if (functionManager.containerFMChildren==null) {
      completion = localcompletion;
      eventstaken = localeventstaken;

    }
    else {
      if (functionManager.containerFMChildren.isEmpty()) {
        completion        = localcompletion;
        eventstaken       = localeventstaken;
      }
      else {
        completion = 0.0;
        eventstaken = -1;

        Iterator it = functionManager.containerFMChildren.getQualifiedResourceList().iterator();
        while (it.hasNext()) {
          FunctionManager aFMChild = (FunctionManager) it.next();

          if (aFMChild.isInitialized()) {
            ParameterSet<FunctionManagerParameter> paraSet;
            try {
              paraSet = aFMChild.getParameter(functionManager.getHCALparameterSet());
            }
            catch (ParameterServiceException e) {
              logger.warn("[HCAL " + functionManager.FMname + "] Could not update parameters for FM client: " + aFMChild.getResource().getName() + " The exception is:", e);
              return;
            }

            Double lvl2completion = ((DoubleT)paraSet.get("COMPLETION").getValue()).getDouble();
            completion += lvl2completion;

            localeventstaken = ((IntegerT)paraSet.get("HCAL_EVENTSTAKEN").getValue()).getInteger();
            if (localeventstaken!=-1) { eventstaken = localeventstaken; }
          }
        }

        if (localcompletion!=-1.0) {
          completion += localcompletion;
          if ((functionManager.containerFMChildren.getQualifiedResourceList().size()+1)!=0) {
            completion = completion / (functionManager.containerFMChildren.getQualifiedResourceList().size()+1);
          }
        }
        else {
          if ((functionManager.containerFMChildren.getQualifiedResourceList().size())!=0) {
            completion = completion / (functionManager.containerFMChildren.getQualifiedResourceList().size());
          }
        }
      }
    }

    functionManager.getHCALparameterSet().put(new FunctionManagerParameter<DoubleT>("COMPLETION",new DoubleT(completion)));
    functionManager.getHCALparameterSet().put(new FunctionManagerParameter<IntegerT>("HCAL_EVENTSTAKEN",new IntegerT(eventstaken)));
  }

  // check that the controlled LVL2 FMs are not in an error state
  protected void pollLVL2FMhealth() {

    if ((functionManager != null) && (functionManager.isDestroyed() == false)) {
      if (functionManager.containerFMChildren!=null) {
        if (!functionManager.containerFMChildren.isEmpty()) {
          Iterator it = functionManager.containerFMChildren.getQualifiedResourceList().iterator();
          while (it.hasNext()) {
            FunctionManager fmChild = (FunctionManager) it.next();
            logger.debug("[HCAL LVL1 " + functionManager.FMname + "] current fmChild is: " + fmChild.getName().toString());
            if ( fmChild.isInitialized() && fmChild.refreshState().toString().equals(HCALStates.ERROR.toString())) {
              String errMessage = "[HCAL LVL1 " + functionManager.FMname + "] Error! state of the LVL2 FM with role: " + fmChild.getRole().toString() + "\nPlease check the chainsaw logs, jobcontrol, etc. The name of this FM is: " + fmChild.getName().toString() +"\nThe URI is: " + fmChild.getURI().toString();
              try {
                errMessage = "[HCAL LVL1 " + functionManager.FMname + "] Level 2 FM with name " + fmChild.getName().toString() + " has received an xdaq error from the hcalSupervisor: " + ((StringT)fmChild.getParameter().get("FED_ENABLE_MASK").getValue()).getString();
                logger.error(errMessage);
                if (!((StringT)functionManager.getHCALparameterSet().get("FED_ENABLE_MASK").getValue()).getString().contains(((StringT)fmChild.getParameter().get("FED_ENABLE_MASK").getValue()).getString())){
                  String totalSupervisorError = ((StringT)functionManager.getHCALparameterSet().get("FED_ENABLE_MASK").getValue()).getString() + ((StringT)fmChild.getParameter().get("FED_ENABLE_MASK").getValue()).getString() +  System.getProperty("line.separator") ;
                  functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("FED_ENABLE_MASK", new StringT(totalSupervisorError)));
                  functionManager.sendCMSError(totalSupervisorError);
                }
              }
              catch (ParameterServiceException e) {
                errMessage = "[HCAL LVL1 " + functionManager.FMname + "] Level 2 FM with name " + fmChild.getName().toString() + " is in error, but the hcalSupervisor was unable to report an error message from xdaq.";
                logger.error(errMessage);
                functionManager.sendCMSError(errMessage);
              }
              //functionManager.sendCMSError(errMessage);
              functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("STATE",new StringT("Error")));
              if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); }
            }
          }
        }
      }
    }
  }

  // checks if the TriggerAdapter is stopped
  protected Boolean isTriggerAdapterStopped() {
    Boolean TAisstopped = false;

    if (functionManager.containerTriggerAdapter!=null) {
      if (!functionManager.containerTriggerAdapter.isEmpty()) {
        XDAQParameter pam = null;
        String status = "undefined";

        // ask for the status of the TriggerAdapter and wait until it is Ready, Failed
        for (QualifiedResource qr : functionManager.containerTriggerAdapter.getApplications() ){
          try {
            pam =((XdaqApplication)qr).getXDAQParameter();

            pam.select(new String[] {"stateName", "NextEventNumber"});
            pam.get();
            status = pam.getValue("stateName");

            String NextEventNumberString = pam.getValue("NextEventNumber");
            Double NextEventNumber = Double.parseDouble(NextEventNumberString);

            if (TriggersToTake.doubleValue()!=0) {
              localcompletion = NextEventNumber/TriggersToTake.doubleValue();
            }

            logger.debug("[HCAL " + functionManager.FMname + "] state of the TriggerAdapter stateName is: " + status + ".\nThe NextEventNumberString is: " + NextEventNumberString + ". \nThe local completion is: " + localcompletion + " (" + NextEventNumber + "/" + TriggersToTake.doubleValue() + ")");
          }
          catch (XDAQTimeoutException e) {
            String errMessage = "[HCAL " + functionManager.FMname + "] Error! XDAQTimeoutException: TriggerAdapterWatchThread()\n Perhaps this application is dead!?";
            logger.error(errMessage,e);
            functionManager.sendCMSError(errMessage);
            functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("STATE",new StringT("Error")));
            functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("ACTION_MSG",new StringT("oops - technical difficulties ...")));
            if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; }
          }
          catch (XDAQException e) {
            String errMessage = "[HCAL " + functionManager.FMname + "] Error! XDAQException: TriggerAdapterWatchThread()";
            logger.error(errMessage,e);
            functionManager.sendCMSError(errMessage);
            functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("STATE",new StringT("Error")));
            functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("ACTION_MSG",new StringT("oops - technical difficulties ...")));
            if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; }
          }
        }

        if (status.equals("Failed")) {
          String errMessage = "[HCAL " + functionManager.FMname + "] Error! TriggerAdapter reports error state: " + status + ". Please check log messages which were sent earlier than this one for more details ...(E4)";
          logger.error(errMessage);
          functionManager.sendCMSError(errMessage);
          functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("STATE",new StringT("Error")));
          functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("ACTION_MSG",new StringT("oops - technical difficulties ...")));
          if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; }
        }

        if (status.equals("Ready")) {
          logger.info("[HCAL " + functionManager.FMname + "] The Trigger adapter reports: " + status + " , which means that all Triggers were sent ...");
          functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("STATE",new StringT("")));
          functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("ACTION_MSG",new StringT("The TA is stopped ...")));
          TAisstopped = true;
        }
      }
      else {
        String errMessage = "[HCAL " + functionManager.FMname + "] Error! No TriggerAdapter found: TriggerAdapterWatchThread()";
        logger.error(errMessage);
        functionManager.sendCMSError(errMessage);
        functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("STATE",new StringT("Error")));
        functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("ACTION_MSG",new StringT(errMessage)));
        if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; }
      }
    }

    return TAisstopped;
  }

  List<String> getMaskedChildFMsFromFedMask(String thisFedEnableMask, HashMap<String, List<Integer> > childFMFedMap) {
    // Make a list of FEDs to test mask/no mask
    List<Integer> fedList = new ArrayList<Integer>();
    for (Map.Entry<String, List<Integer> > entry : childFMFedMap.entrySet()) {
      fedList.addAll(entry.getValue());
    }

    // Get mask/no mask for each FED
    HashMap<Integer, BigInteger> parsedFedEnableMask = parseFedEnableMask(thisFedEnableMask);
    HashMap<Integer, Boolean> fedStatusMap = new HashMap<Integer, Boolean>();
    for (Integer fedId : fedList) {
      BigInteger fedMaskWord = parsedFedEnableMask.get(fedId);
      if (fedMaskWord == null) {
        logger.warn("[HCAL " + functionManager.FMname + "] Warning! FED " + fedId + " was not found in the FED_ENABLE_MASK. I will consider it enabled, but you might want to investigate.");
        fedStatusMap.put(fedId, true);
      } else {
        boolean isMaskWord3  = (fedMaskWord.testBit(0) && fedMaskWord.testBit(1) && !fedMaskWord.testBit(2) && !fedMaskWord.testBit(3));
        boolean isMaskWord11 = (fedMaskWord.testBit(0) && fedMaskWord.testBit(1) && !fedMaskWord.testBit(2) && fedMaskWord.testBit(3));
        // See comment for function parseFedEnableMask for information about the fedMaskWord. In short, fedMaskWord==11 means enabled for a FED with SLINK but no TTS output
        fedStatusMap.put(fedId, isMaskWord3||isMaskWord11);
      }
    }

    // Loop over partitions, and determine if they have any enabled FEDs
    HashMap<String, Boolean> childFMStatusMap = new HashMap<String, Boolean>();
    for (Map.Entry<String, List<Integer> > entry : childFMFedMap.entrySet()) {
      String childFMName = entry.getKey();
      List<Integer> childFMFedList = entry.getValue();
      Boolean fmStatus = false;
      for (Integer fedId : childFMFedList) {
        fmStatus = (fmStatus || fedStatusMap.get(fedId));
      }
      childFMStatusMap.put(childFMName, fmStatus);
    }
    // Convert to List<String> of masked partitions and return
    List<String> maskedChildFMs = new ArrayList<String>();
    for (Map.Entry<String, Boolean> entry : childFMStatusMap.entrySet()) {
      if (!entry.getValue()) {
        maskedChildFMs.add(entry.getKey());
      }
    }
    return maskedChildFMs;
  }

  // Parse the FED enable mask string into a map <FedId:FedMaskWord>
  // FED_ENABLE_MASK is formatted as FEDID_1&FEDMASKWORD_1%FEDID_2&FEDMASKWORD2%...
  // The mask word is 3 for enabled FEDs:
  // bit  0 : SLINK ON / OFF
  //      1 : ENABLED/DISABLED
  //  2 & 0 : SLINK NA / BROKEN
  //      4 : NO CONTROL
  protected HashMap<Integer, BigInteger> parseFedEnableMask(String thisFedEnableMask) {
    String[] fedIdValueArray = thisFedEnableMask.split("%");
    HashMap<Integer, BigInteger> parsedFedEnableMask = new HashMap<Integer, BigInteger>();
    for (String fedIdValueString : fedIdValueArray) {
      String[] fedIdValue = fedIdValueString.split("&");

      // Require 2 strings, the FED ID and the mask
      if (fedIdValue.length!=2){
        logger.warn("[HCAL " + functionManager.FMname + "] parseFedEnableMask: inconsistent fedIdValueString found (should be of format fedId&mask).\n The length is: " + fedIdValue.length + "\nString: " + fedIdValueString);
        break;
      }

      // Get the FED ID
      Integer fedId = null;
      try {
        fedId = new Integer(fedIdValue[0]);
      } catch (NumberFormatException nfe) {
        if (!RunType.equals("local")) {
          logger.error("[HCAL " + functionManager.FMname + "] parseFedEnableMask: FedId format error: " + nfe.getMessage());
        } else {
          logger.debug("[HCAL " + functionManager.FMname + "] parseFedEnableMask: FedId format error: " + nfe.getMessage());
        }
        continue;
      }

      BigInteger fedMaskWord = null;
      try {
        fedMaskWord = new BigInteger( fedIdValue[1] );
      } catch (NumberFormatException nfe) {
        if (!RunType.equals("local")) {
          logger.error("parseFedEnableMask: fedMaskWord format error: " + nfe.getMessage());
        } else {
          logger.debug("parseFedEnableMask: fedMaskWord format error: " + nfe.getMessage());
        }
        continue;
      }
      logger.debug("parseFedEnableMask: parsing result ...\n(FedId/Status) = (" + fedIdValue[0] + "/"+ fedIdValue[1] + ")");

      parsedFedEnableMask.put(fedId, fedMaskWord);

    } // End loop over fedId:fedMaskWord
    return parsedFedEnableMask;
  }

  // DEPRECATED
  // determine the active HCAL FEDs from the ENABLE_FED_MASK string received in the configureAction()
  protected List<String> getEnabledHCALFeds(String FedEnableMask) {
    List<String> fedVector = new ArrayList<String>();

    // parse FED mask
    String[] FedValueArray = FedEnableMask.split("%");

    for ( int j=0 ; j<FedValueArray.length ; j++) {
      logger.debug("[HCAL " + functionManager.FMname + "] FED_ENABLE_MASK parsing: testing " + FedValueArray[j]);

      // make the name value pair
      String[] NameValue = FedValueArray[j].split("&");

      Integer FedId = null;
      try {
        FedId = new Integer(NameValue[0]);
      }
      catch ( NumberFormatException nfe ) {
        if (!RunType.equals("local")) {
          logger.error("[HCAL " + functionManager.FMname + "] FED_ENABLE_MASK parsing: FedId format error: " + nfe.getMessage());
        }
        else {
          logger.debug("[HCAL " + functionManager.FMname + "] FED_ENABLE_MASK parsing: FedId format error: " + nfe.getMessage());
        }
        continue;
      }

      if ( FedId < functionManager.firstHCALFedId || FedId > functionManager.lastHCALFedId ) {
        logger.debug("[HCAL " + functionManager.FMname + "] FED_ENABLE_MASK parsing: FedId = " + FedId + " is not in the HCAL FED range.");
        continue;
      }

      // check NameValue consistency
      if (NameValue.length!=2){
        logger.warn("[HCAL " + functionManager.FMname + "] FED_ENABLE_MASK parsing: inconsistent NameValue found.\n The length is: " + NameValue.length + "\nString: " + FedValueArray[j]);
        break;
      }

      // get fed mask value (NameValue[0] is fed id)
      BigInteger FedValue = null;
      if (NameValue[1] != null && NameValue[1].length()>0 ) {
        FedValue = new BigInteger( NameValue[1] );
      }

      // bit  0 : SLINK ON / OFF
      //      1 : ENABLED/DISABLED
      //  2 & 0 : SLINK NA / BROKEN
      //      4 : NO CONTROL

      logger.debug("[HCAL " + functionManager.FMname + "] FED_ENABLE_MASK parsing: parsing result ...\n(FedId/Status) = (" + NameValue[0] + "/"+ NameValue[1] + ")");

      if (NameValue[0]!=null && NameValue[0].length()>0 && FedValue!=null) {
        //check bits 2 & 4 too ?
        logger.debug("[HCAL " + functionManager.FMname + "] FED_ENABLE_MASK parsing: bitmap result ...\ntestbit(0) "+ FedValue.testBit(0) + "\ntestbit(2) " +FedValue.testBit(2) + "\ntestbit(0) & !testbit(2): " + (!FedValue.testBit(2) && FedValue.testBit(0)));

        // collect the found and enabled HCAL FEDs
        if ( !FedValue.testBit(2) && FedValue.testBit(1) && FedValue.testBit(0) ) {
          logger.info("[HCAL " + functionManager.FMname + "] Found and adding new HCAL FED with FedId: " + NameValue[0] + " to the list of active HCAL FEDs.");
          fedVector.add(new String(NameValue[0]));

          // check if HCAL FEDs are enabled for this run
          if ( FedId >= functionManager.firstHCALFedId && FedId <= functionManager.lastHCALFedId ) {
            logger.info("[HCAL " + functionManager.FMname + "] FedId = " + FedId + " is in the HCAL FED range.");
            functionManager.HCALin = true;
          }

          // check if FEDs from a specific HCAL partition are enabled
          if ( FedId >= functionManager.firstHBHEaFedId && FedId <= functionManager.lastHBHEaFedId ) {
            if(!functionManager.HBHEain) {
              if (functionManager.FMrole.equals("HCAL")) {
                logger.info("[HCAL " + functionManager.FMname + "] FedId = " + FedId + " is in the HCAL HBHEa FED range.\nEnabling the HBHEa partition.");
              }
              functionManager.HBHEain = true;
            }
          }
          else if ( FedId >= functionManager.firstHBHEbFedId && FedId <= functionManager.lastHBHEbFedId ) {
            if(!functionManager.HBHEbin) {
              if (functionManager.FMrole.equals("HCAL")) {
                logger.info("[HCAL " + functionManager.FMname + "] FedId = " + FedId + " is in the HCAL HBHEb FED range.\nEnabling the HBHEb partition.");
              }
              functionManager.HBHEbin = true;
            }
          }
          else if ( FedId >= functionManager.firstHBHEcFedId && FedId <= functionManager.lastHBHEcFedId ) {
            if(!functionManager.HBHEcin) {
              if (functionManager.FMrole.equals("HCAL")) {
                logger.info("[HCAL " + functionManager.FMname + "] FedId = " + FedId + " is in the HCAL HBHEc FED range.\nEnabling the HBHEc partition.");
              }
              functionManager.HBHEcin = true;
            }
          }
          else if ( FedId >= functionManager.firstHFFedId && FedId <= functionManager.lastHFFedId ) {
            if(!functionManager.HFin) {
              if (functionManager.FMrole.equals("HCAL")) {
                logger.info("[HCAL " + functionManager.FMname + "] FedId = " + FedId + " is in the HCAL HF FED range.\nEnabling the HF partition.");
              }
              functionManager.HFin = true;
            }
          }
          else if ( FedId >= functionManager.firstHOFedId && FedId <= functionManager.lastHOFedId ) {
            if(!functionManager.HOin) {
              if (functionManager.FMrole.equals("HCAL")) {
                logger.info("[HCAL " + functionManager.FMname + "] FedId = " + FedId + " is in the HCAL HF FED range.\nEnabling the HO partition.");
              }
              functionManager.HOin = true;
            }
          }
          else {
            if (functionManager.FMrole.equals("HCAL")) {
              logger.error("[HCAL " + functionManager.FMname + "] FED_ENABLE_MASK parsing: FedId = " + FedId + " is in not the HCAL FED range.\nThis should never happen at this stage!!");
            }
          }
        }
      }
    }

    functionManager.checkHCALPartitionFEDListConsistency();

    return fedVector;
  }

  //get table from hcalRunInfo in Jeremy's way
  protected class RunInfoServerReader {
    private HashMap<String,String> m_items;

    public RunInfoServerReader() {
      m_items=new HashMap<String,String>();
      logger.debug("[HCAL " + functionManager.FMname + "] ... new RunInfoServerReader constructed.");
    }

    public void acquire(XdaqApplication app) {
      try {

        logger.debug("[HCAL " + functionManager.FMname + "] RunInfoServerReader is acquiring information now ...");

        org.w3c.dom.Document d=app.command(new XDAQMessage("GetHcalRunInfo"));

        HashMap<String,String> hm=new HashMap<String,String>();
        extract(d.getDocumentElement(),hm);
        m_items.putAll(hm);

      }
      catch (XDAQException e) {
        String errMessage = "[HCAL " + functionManager.FMname + "] Error! XDAQException: RunInfoServerReader::acquire(..) when trying to retrieve info from a hcalRunInfoServer XDAQ application";
        logger.error(errMessage,e);
        functionManager.sendCMSError(errMessage);
        functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("STATE",new StringT("Error")));
        functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("ACTION_MSG",new StringT(errMessage)));
        if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; return;}
      }

      logger.debug("[HCAL " + functionManager.FMname + "] ... RunInfoServerReader acquiring information done.");
    }

    public HashMap<String,String> getInfo() { return m_items; }

    private void extract(Element e, HashMap<String,String> m) {
      int n_elem=0;
      StringBuffer sb=new StringBuffer();
      for (Node n=e.getFirstChild(); n!=null; n=n.getNextSibling()) {
        if (n instanceof Text) {
          sb.append(n.getNodeValue());
        }
        else if (n instanceof Element) {
          n_elem++;
          Element ec=(Element)n;
          extract(ec,m);
        }
      }
      if (n_elem==0) {
        String name=e.getNodeName();
        if (name.indexOf(':')!=-1) {
          name=name.substring(name.indexOf(':')+1);
        }
        m.put(name,sb.toString());
      }
    }
  }

  // class which makes the HCAL fishy
  protected class MoveTheLittleFishy {

    private Boolean movehimtotheright = true;
    private Integer moves = 0;
    private Integer offset = 0;
    private Integer maxmoves = 30;
    private String TheLittleFishySwimsToTheRight ="><)))\'>";
    private String TheLittleFishySwimsToTheLeft  ="<\')))><";
    private String TheLine = "";
    private Random theDice;

    public MoveTheLittleFishy(Integer themaxmoves) {
      movehimtotheright = true;
      moves = 0;
      offset = 0;
      maxmoves = themaxmoves;
      if (maxmoves < 30) { maxmoves = 30; }
      TheLine = "";
      theDice = new Random();
      logger.debug("[HCAL " + functionManager.FMname + "] The little fishy should show up - catch him!!!");
    }

    public void movehim() {
      TheLine = "";
      if (movehimtotheright) {
        moves++;
        TheLine +="_";
        for (int count=1; count < moves; count++) { TheLine +="_"; }
        TheLine += TheLittleFishySwimsToTheRight;

        if ((maxmoves-moves) > 6) {
          Integer sayit = theDice.nextInt(10);
          if (sayit == 9) {
            Integer saywhat = theDice.nextInt(10);
            if (saywhat >= 0 && saywhat <= 4) {
              TheLine += " BLUBB";
              offset = 6;
            }
            else if (saywhat == 5 && (maxmoves-moves) > 22) {
              TheLine += " What am I doing here?";
              offset = 22;
            }
            else if (saywhat == 6 && (maxmoves-moves) > 23) {
              TheLine += " hicks - I meant a Higgs!";
              offset = 23;
            }
            else if (saywhat == 7 && (maxmoves-moves) > 16) {
              TheLine += " Howdy stranger!";
              offset = 16;
            }
            else if (saywhat == 8 && (maxmoves-moves) > 20) {
              TheLine += " No, I'm not stinky!";
              offset = 20;
            }
            else {
              TheLine += " hello";
              offset = 6;
            }
          }
        }

        for (int count=moves+offset; count < maxmoves; count++) { TheLine +="_"; }
        offset = 0;
        TheLine +="_";
        if (moves==maxmoves) {
          movehimtotheright = false;
        }
        else {
          Integer wheretogo = theDice.nextInt(10);
          if (wheretogo >= 7) {
            movehimtotheright = false;
          }
        }
      }
      else {
        TheLine +="_";
        for (int count=moves; count > 1; count--) { TheLine +="_"; }
        TheLine += TheLittleFishySwimsToTheLeft;
        for (int count=maxmoves; count > moves; count--) { TheLine +="_"; }
        TheLine +="_";
        moves--;
        if (moves<1) {
          movehimtotheright = true;
          moves = 0;
        }
        else {
          Integer wheretogo = theDice.nextInt(10);
          if (wheretogo >= 7) {
            movehimtotheright = true;
          }
        }
      }
      functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("ACTION_MSG",new StringT(TheLine)));
    }
  }

  // thread which sets FM parameters, updates the runInfo, etc.
  protected class LevelOneMonitorThread extends Thread {

    MoveTheLittleFishy LittleA;
    MoveTheLittleFishy LittleB;

    private Random theDice;
    private Boolean OkToShow = false;

    private int elapsedseconds;

    public LevelOneMonitorThread() {
      MonitorThreadList.add(this);
      LittleA = new MoveTheLittleFishy(70);
      LittleB = new MoveTheLittleFishy(70);
      theDice = new Random();
      OkToShow = false;
      elapsedseconds = 0;
    }

    public void run() {
      stopMonitorThread = false;

      int icount = 0;
      while ( stopMonitorThread == false && functionManager.isDestroyed() == false ) {
        icount++;
        Date now = Calendar.getInstance().getTime();

        // always update the completion status by looping over FM's and Subsystems and update the paramter set
        try {
          pollCompletion();
        }
        catch (Exception ignore) { return; }

        // initialize the configuration timer
        if ((functionManager != null) && (functionManager.isDestroyed() == false) && (functionManager.getState().getStateString().equals(HCALStates.HALTED.toString()) || functionManager.getState().getStateString().equals(HCALStates.RUNNING.toString()))) {
          elapsedseconds = 0;
        }

        // count the seconds in the configuring state
        if ((functionManager != null) && (functionManager.isDestroyed() == false) && (functionManager.getState().getStateString().equals(HCALStates.CONFIGURING.toString()))) {
          if (icount%1==0) {
            elapsedseconds++;
          }
        }

        // update FMs action and state parameters for steady states reached
        if (icount%1==0) {
          if ((functionManager != null) && (functionManager.isDestroyed() == false) && (functionManager.getState().getStateString().equals(HCALStates.UNINITIALIZED.toString()))) {
            functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("STATE",new StringT(HCALStates.UNINITIALIZED.toString())));
            functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("ACTION_MSG",new StringT("... reached the \"" + HCALStates.UNINITIALIZED.toString() + "\" state.")));
            functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("ERROR_MSG",new StringT("")));
          }
          if ((functionManager != null) && (functionManager.isDestroyed() == false) && (functionManager.getState().getStateString().equals(HCALStates.INITIAL.toString()))) {
            functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("STATE",new StringT(HCALStates.INITIAL.toString())));
            functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("ACTION_MSG",new StringT("... reached the \"" + HCALStates.INITIAL.toString() + "\" state.")));
            functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("ERROR_MSG",new StringT("")));
          }
          if ((functionManager != null) && (functionManager.isDestroyed() == false) && (functionManager.getState().getStateString().equals(HCALStates.HALTED.toString()))) {
            functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("STATE",new StringT(HCALStates.HALTED.toString())));
            functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("ACTION_MSG",new StringT("... reached the \"" + HCALStates.HALTED.toString() + "\" state.")));
            functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("ERROR_MSG",new StringT("")));
          }
          if ((functionManager != null) && (functionManager.isDestroyed() == false) && (functionManager.getState().getStateString().equals(HCALStates.CONFIGURED.toString()))) {
            pollCompletion(); // get the latest update of the LVL2 config times
            functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("STATE",new StringT(HCALStates.CONFIGURED.toString())));
            functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("ACTION_MSG",new StringT("... reached the \"" + HCALStates.CONFIGURED.toString() + "\" state in about " + elapsedseconds + " sec.")));
            functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("ERROR_MSG",new StringT("")));
          }
          if ((functionManager != null) && (functionManager.isDestroyed() == false) && (functionManager.getState().getStateString().equals(HCALStates.PAUSED.toString()))) {
            functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("STATE",new StringT(HCALStates.PAUSED.toString())));
            functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("ACTION_MSG",new StringT("... reached the \"" + HCALStates.PAUSED.toString() + "\" state.")));
            functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("ERROR_MSG",new StringT("")));
          }
          if ((functionManager != null) && (functionManager.isDestroyed() == false) && (functionManager.getState().getStateString().equals(HCALStates.RUNNING.toString()))) {
            functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("STATE",new StringT(HCALStates.RUNNING.toString())));
            functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("ERROR_MSG",new StringT("")));
          }
          if ((functionManager != null) && (functionManager.isDestroyed() == false) && (functionManager.getState().getStateString().equals(HCALStates.RUNNINGDEGRADED.toString()))) {
            functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("STATE",new StringT(HCALStates.RUNNINGDEGRADED.toString())));
            functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("ERROR_MSG",new StringT("")));
          }
        }

        // move the little fishys every 2s
        if ( icount%2==0) {
          // tell little fishy in HCAL and HF to say configuration progress when configuring
          if ((functionManager != null) && (functionManager.isDestroyed() == false) && (functionManager.getState().getStateString().equals(HCALStates.CONFIGURING.toString()))) {
            Double progress = ((DoubleT)functionManager.getHCALparameterSet().get("PROGRESS").getValue()).getDouble()*100;
            Double RoundedProgress = Math.round(progress * 100.0) / 100.0 ;
            if( RoundedProgress > 0.0){
              String msg = "___><)))\'>: Configuration progress = "+ RoundedProgress +" %_________________________________________";
              functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("ACTION_MSG",new StringT(msg)));
            }
          }
          // move the little fishy in HCAL when running
          if(functionManager.FMrole.equals("HCAL")){
            if ((functionManager != null) && (functionManager.isDestroyed() == false) && functionManager.getState().getStateString().equals(HCALStates.RUNNING.toString()) ) {
              LittleB.movehim();
            }
          }
        }

        // Set the action message if we are in RunningDegraded
        if(icount%15==0){
          if ((functionManager != null) && (functionManager.isDestroyed() == false) && functionManager.getState().getStateString().equals(HCALStates.RUNNINGDEGRADED.toString()) ) {
            functionManager.setAction("><))),> : DAQ shifter, please contact HCAL DOC now!");
          }
        }

        // no fishys for the LVL2s give static message to the LVL2 action box
        Boolean noticedonce = false;
        if ((functionManager != null) && (functionManager.isDestroyed() == false) && (!functionManager.FMrole.equals("HCAL")) && (!noticedonce) && (functionManager.getState().getStateString().equals(HCALStates.RUNNING.toString()))) {
          noticedonce = true;
          functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("ACTION_MSG",new StringT("running like hell ...")));
        }

        // from time to time report the progress in some transitional states
        if (icount%120==0) {
          if ((functionManager != null) && (functionManager.isDestroyed() == false) && (functionManager.getState().getStateString().equals(HCALStates.CONFIGURING.toString()))) {
            functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("ACTION_MSG",new StringT("still executing configureAction ... - so we should be closer now...")));
          }
          if ((functionManager != null) && (functionManager.isDestroyed() == false) && (functionManager.getState().getStateString().equals(HCALStates.RUNNING.toString()))) {
            functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("ACTION_MSG",new StringT("still running like hell ...")));
          }
        }

        // from time to time say something really meaningful
        if (icount%40==0) {
          Integer showthis = theDice.nextInt(30);
          if (showthis == 30) {
            OkToShow = true;
          }
          if (OkToShow) {
            if ((functionManager != null) && (functionManager.isDestroyed() == false) && (functionManager.getState().getStateString().equals(HCALStates.CONFIGURING.toString()))) {
              functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("ACTION_MSG",new StringT("still executing configureAction ... - we should be better done soon!!")));
            }
            if ((functionManager != null) && (functionManager.isDestroyed() == false) && (functionManager.getState().getStateString().equals(HCALStates.RUNNING.toString()))) {
              functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("ACTION_MSG",new StringT("catch the little fishy ;-)")));
            }
            OkToShow =false;
          }
        }

        // update run info every 3min
        if (icount%180==0) {
          // action only when in the "Running" state
          if ((functionManager != null) && (functionManager.isDestroyed() == false) && (functionManager.getState().getStateString().equals(HCALStates.RUNNING.toString()))) {

            // define kind of start time
            if (StartTime==null)
            {
              StartTime = new Date();
            }

            // define kind of stop time
            if (StopTime==null)
            {
              StopTime = new Date();
            }


            publishRunInfoSummary();

            String Message = "[HCAL " + functionManager.FMname + "] ... (possibly) updated run info at: " + now.toString();
            logger.info(Message);
            System.out.println(Message);
          }
        }
        // delay between polls
        try { Thread.sleep(1000); }
        catch (Exception ignored) { return; }
      }

      // stop the Monitor watchdog thread
      System.out.println("[HCAL " + functionManager.FMname + "] ... stopping Monitor watchdog thread done.");
      logger.debug("[HCAL " + functionManager.FMname + "] ... stopping Monitor watchdog thread done.");

      MonitorThreadList.remove(this);
    }
  }

  // thread which checks the HCAL supervisor state
  protected class HCALSupervisorWatchThread extends Thread {

    public HCALSupervisorWatchThread() {
      HCALSupervisorWatchThreadList.add(this);
    }

    public void run() {
      stopHCALSupervisorWatchThread = false;

      int icount = 0;
      while ((stopHCALSupervisorWatchThread == false) && (functionManager != null) && (functionManager.isDestroyed() == false)) {
        icount++;
        // poll HCAL supervisor status in the Configuring/Configured/Running/RunningDegraded states every 5 sec to see if it is still alive  (dangerous because ERROR state is reported wrongly quite frequently)
        if (icount%5==0) {
          if ((functionManager.getState().getStateString().equals(HCALStates.CONFIGURING.toString()) ||
                functionManager.getState().getStateString().equals(HCALStates.CONFIGURED.toString()) ||
                functionManager.getState().getStateString().equals(HCALStates.RUNNING.toString()) ||
                functionManager.getState().getStateString().equals(HCALStates.RUNNINGDEGRADED.toString()))) {
            if (!functionManager.containerhcalSupervisor.isEmpty()) {

              {
                String debugMessage = "[HCAL " + functionManager.FMname + "] HCAL supervisor found for checking its state i.e. health - good!";
                logger.debug(debugMessage);
              }

              XDAQParameter pam = null;
              String status   = "undefined";
              String stateName   = "undefined";
              String progressFromSupervisor = "undefined";
              // ask for the status of the HCAL supervisor
              for (QualifiedResource qr : functionManager.containerhcalSupervisor.getApplications() ){
                try {
                  pam =((XdaqApplication)qr).getXDAQParameter();
                  pam.select(new String[] {"TriggerAdapterName", "PartitionState", "InitializationProgress", "overallProgress", "stateName"});
                  pam.get();

                  status = pam.getValue("PartitionState");
                  stateName = pam.getValue("stateName");
                  progressFromSupervisor = pam.getValue("overallProgress");

                  if (status==null || stateName==null) {
                    String errMessage = "[HCAL " + functionManager.FMname + "] Error! Asking the hcalSupervisor for the PartitionState and stateName to see if it is alive or not resulted in a NULL pointer - this is bad!";
                    functionManager.goToError(errMessage);
                  }
                  if (progressFromSupervisor == null) {
                    // TODO: do something more drastic in this case?
                    logger.error("[JohnLogProgress] " + functionManager.FMname + " Something went wrong when asking the hcalSupervisor for her overallProgress...");
                  }
                  else {
		    logger.debug("JohnDebug: progressFromSupervisor " + qr.getName() + "in " + functionManager.FMname + " was " + progressFromSupervisor);
                    try {
		      if (!Double.isNaN(Double.parseDouble(progressFromSupervisor))) {
                        functionManager.getHCALparameterSet().put(new FunctionManagerParameter<DoubleT>("PROGRESS", new DoubleT(Double.parseDouble(progressFromSupervisor))));
                      }
		      else {
		        logger.debug("JohnDebug: progressFromSupervisor was NaN. Not setting progress to NaN.");
	              }
                    }
                    catch(NumberFormatException e) {
		      logger.debug("JohnDebug: progressFromSupervisor was NOT parseable to a double. Will not set progress");
                    }
                  }

                  logger.debug("[HCAL " + functionManager.FMname + "] asking for the HCAL supervisor PartitionState to see if it is still alive.\n The PartitionState is: " + status);
                }
                catch (XDAQTimeoutException e) {
                  String errMessage = "[HCAL " + functionManager.FMname + "] Timed out when querying supervisor's status. The hcalSupervisor may have crashed, please look at the RCMS logs/JobControl logs for more information"; 
                  functionManager.goToError(errMessage);
                }
                catch (XDAQException e) {
                  String errMessage = "[HCAL " + functionManager.FMname + "] Cannot query supervisor's status. The hcalSupervisor may have crashed, please look at the RCMS logs/JobControl logs for more information";
                  functionManager.goToError(errMessage);
                }

                if (status.equals("Failed") || status.equals("Faulty") || status.equals("Error") || stateName.equalsIgnoreCase("failed")) {
                  String errMessage = "[HCAL " + functionManager.FMname + "] Error! HCALSupervisorWatchThread(): supervisor reports partitionState: " + status + " and stateName: " + stateName +"; ";
                  String supervisorError = ((HCALlevelTwoFunctionManager)functionManager).getSupervisorErrorMessage();
                  errMessage+=supervisorError;
                  functionManager.goToError(errMessage);
                }
              }
            }
            else {
              String errMessage = "[HCAL " + functionManager.FMname + "] Error! No HCAL supervisor found: HCALSupervisorWatchThread()";
              functionManager.goToError(errMessage);
            }
          }
        }
        // delay between polls
        try { Thread.sleep(1000); }
        catch (Exception ignored) { return; }
      }

      // stop the HCAL supervisor watchdog thread
      System.out.println("[HCAL " + functionManager.FMname + "] ... stopping HCAL supervisor watchdog thread done.");
      logger.debug("[HCAL " + functionManager.FMname + "] ... stopping HCAL supervisor watchdog thread done.");

      HCALSupervisorWatchThreadList.remove(this);
    }
  }

  // thread which checks the TriggerAdapter state
  protected class TriggerAdapterWatchThread extends Thread {

    public TriggerAdapterWatchThread() {
      TriggerAdapterWatchThreadList.add(this);
    }

    public void run() {
      stopTriggerAdapterWatchThread = false;

      int icount = 0;
      while ((stopTriggerAdapterWatchThread == false) && (functionManager != null) && (functionManager.isDestroyed() == false)) {
        icount++;
        // poll TriggerAdapter status every 1 sec
        if (icount%1==0) {
          if ((functionManager != null) && (functionManager.isDestroyed() == false) && ((functionManager.getState().getStateString().equals(HCALStates.RUNNING.toString())) ||
                (functionManager.getState().getStateString().equals(HCALStates.RUNNINGDEGRADED.toString()))) ) {
            // check the state of the TriggerAdapter
            if (functionManager.containerTriggerAdapter!=null) {
              if (!functionManager.containerTriggerAdapter.isEmpty()) {
                {
                  String debugMessage = "[HCAL " + functionManager.FMname + "] TriggerAdapter found for asking its state - good!";
                  logger.debug(debugMessage);
                }
                XDAQParameter pam = null;
                String status = "undefined";
                Double NextEventNumber = -1.0;

                // ask for the status of the TriggerAdapter and wait until it is Ready, Failed
                for (QualifiedResource qr : functionManager.containerTriggerAdapter.getApplications() ){
                  try {
                    pam =((XdaqApplication)qr).getXDAQParameter();

                    pam.select(new String[] {"stateName", "NextEventNumber"});
                    pam.get();
                    status = pam.getValue("stateName");
                    if (status==null) {
                      String errMessage = "[HCAL " + functionManager.FMname + "] Error! Asking the TA for the stateName when Running resulted in a NULL pointer - this is bad!";
                      functionManager.goToError(errMessage);
                    }

                    String NextEventNumberString = pam.getValue("NextEventNumber");
                    if (NextEventNumberString!=null) {
                      NextEventNumber = Double.parseDouble(NextEventNumberString);
                      if (TriggersToTake.doubleValue()!=0) {
                        localcompletion = NextEventNumber/TriggersToTake.doubleValue();
                      }
                      else {
                        localcompletion = -1.0;
                      }
                      localeventstaken = Integer.parseInt(NextEventNumberString);
                    }
                    else {
                      String errMessage = "[HCAL " + functionManager.FMname + "] Error! Asking the TA for the NextEventNumber when Running resulted in a NULL pointer - this is bad!";
                      functionManager.goToError(errMessage);
                    }
                    //5s per log message in normal run. 
                    if(icount %5==0){
                      logger.info("[HCAL " + functionManager.FMname + "] state of the TriggerAdapter stateName is: " + status + ".\nThe NextEventNumberString is: " + NextEventNumberString + ". \nThe local completion is: " + localcompletion + " (" + NextEventNumber + "/" + TriggersToTake.doubleValue() + ")");
                    }
                    functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("ACTION_MSG",new StringT("The state of the TriggerAdapter is: " + status + ".\nThe NextEventNumberString is: " + NextEventNumberString + ". \nThe local completion is: " + localcompletion + " (" + NextEventNumber + "/" + TriggersToTake.doubleValue() + ")")));
                  }
                  catch (XDAQTimeoutException e) {
                    String errMessage = "[HCAL " + functionManager.FMname + "] Timed out when querying TriggerAdapter's status. The TriggerAdapter application may have crashed, please look at the RCMS logs/JobControl logs for more information"; 
                    functionManager.goToError(errMessage);
                  }
                  catch (XDAQException e) {
                    String errMessage = "[HCAL " + functionManager.FMname + "] Cannot query TriggerAdapter's status. The TriggerAdapter  may have crashed, please look at the RCMS logs/JobControl logs for more information";
                    functionManager.goToError(errMessage);
                  }
                }

                if (status.equalsIgnoreCase("Failed")) {
                  String errMessage = "[HCAL " + functionManager.FMname + "] Error! TriggerAdapter reports error state: " + status + ". Please check log messages which were sent earlier than this one for more details ... (E9)";
                  functionManager.goToError(errMessage);
                }

                if (status.equals("Ready")) {
                  logger.info("[HCAL " + functionManager.FMname + "] The Trigger adapter reports: " + status + " , which means that all Triggers were sent ...");
                  functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("STATE",new StringT("")));
                  functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("ACTION_MSG",new StringT("Stopping the TA ...")));

                  if (!SpecialFMsAreControlled) {
                    logger.info("[HCAL " + functionManager.FMname + "] Do functionManager.fireEvent(HCALInputs.STOP)");
                    functionManager.fireEvent(HCALInputs.STOP);
                  }

                  logger.debug("[HCAL " + functionManager.FMname + "] TriggerAdapter should have reported to be in the Ready state, which means the events are taken ...");
                  logger.info("[HCAL " + functionManager.FMname + "] All L1As were sent, i.e. Trigger adapter is in the Ready state, changing back to Configured state ...");
                }
              }
              else {
                String errMessage = "[HCAL " + functionManager.FMname + "] Error! No TriggerAdapter found: TriggerAdapterWatchThread()";
                functionManager.goToError(errMessage);
              }
            }
          }
        }
        // delay between polls
        try { Thread.sleep(1000); }
        catch (Exception ignored) { return; }
      }

      // stop the TriggerAdapter watchdog thread
      System.out.println("[HCAL " + functionManager.FMname + "] ... stopping TriggerAdapter watchdog thread done.");
      logger.info("[HCAL " + functionManager.FMname + "] ... stopping TriggerAdapter watchdog thread done.");
      TriggerAdapterWatchThreadList.remove(this);
    }
  }

  // thread which checks the alarmer state
  protected class AlarmerWatchThread extends Thread {
    public AlarmerWatchThread() {
      AlarmerWatchThreadList.add(this);
    }

    public void run() {
      // Build the list of partitions to watch from the LV2 FM names
      // TODO: Watch "Dev" partition base on a FM parameter
      
      List<QualifiedResource> fmChildrenList    = functionManager.containerFMChildren.getActiveQRList();
      List<String>  watchedAlarms     = new ArrayList<String>();  //All parameters to query
      List<String>  watchedPartitions = new ArrayList<String>();  //All watchedPartitions (LV2 names)
      List<String>  AlarmerPamNames   = new ArrayList<String>();  //All alarmer pam Names
      String        FMstate           =  functionManager.getState().getStateString();
      XDAQParameter NameQuery         = new XDAQParameter(functionManager.alarmerURL,"hcalAlarmer",0);
      HashMap<String,String> partitionStatusMap  = new HashMap<String,String>(); // e.g. <HO,HO_Status>,<Laser,LASER_Status>
      HashMap<String,String> partitionMessageMap = new HashMap<String,String>(); // e.g. <HO,HO_Message>,<Laser,LASER_Message>

      // TODO: Get this map from FM property OR snippet 
      // If FM name is found in this map, all partitions will be watched/ignored together.
      // If not the partition matched to the FM name substring will be watched.
      HashMap<String,List<String>> FMnameToPartitionMap = new HashMap<String,List<String>>(); // e.g. <HBHE,HBHEa>, <HBHE,HBHEb> ...
      List<String>  HBHEpartitions   = new ArrayList<String>();  // HBHEa,HBHEb,HBHEc
      HBHEpartitions.add("HBHEa");
      HBHEpartitions.add("HBHEb");
      HBHEpartitions.add("HBHEc");
      FMnameToPartitionMap.put("HCAL_HBHE",HBHEpartitions);

      try{
        AlarmerPamNames = NameQuery.getNames();
      }
      catch(XDAQException e){
        logger.error("[HCAL "+functionManager.FMname+"] AlarmerWatchThread: Cannot get alarmer infospace parameter names. Exception: "+e.getMessage());
      }
      for(QualifiedResource qr : fmChildrenList){
        String LV2FMname             = qr.getName(); //e.g. HCAL_HO
        try{
          if(FMnameToPartitionMap.get(LV2FMname)!=null){
            List<String> parititonsOfThisFM = FMnameToPartitionMap.get(LV2FMname);
            for (String partition : parititonsOfThisFM){
              for(String pamName : AlarmerPamNames){
                //Match partition names with AlarmerInfospace parameters ignore case
                if(pamName.toLowerCase().contains(partition.toLowerCase()) && pamName.contains("_Status")  ){
                  //Use Infospace partition name for query
                  watchedAlarms.add(pamName);                                    //e.g. HO_Status
                  //Use FMname partition name for ignoring Empty/Masked FMs
                  watchedPartitions.add(partition);                              //e.g. HO
                  partitionStatusMap.put(partition,pamName);
                }
                if(pamName.toLowerCase().contains(partition.toLowerCase()) && pamName.contains("_Message")  ){
                  watchedAlarms.add(pamName);                                   //e.g. HO_Message
                  partitionMessageMap.put(partition,pamName);
                }
              }
            }
          }
          else{
            // Get FM_PARTITION from the LV2 parameterSet 
            String partition = ((StringT)(((FunctionManager)qr).getParameter().get("FM_PARTITION").getValue())).getString();
            if (!partition.equals("not set")){
              for(String pamName : AlarmerPamNames){
                //Match partition names with AlarmerInfospace parameters ignore case
                if(pamName.toLowerCase().contains(partition.toLowerCase()) && pamName.contains("_Status")  ){
                  //Use Infospace partition name for query
                  watchedAlarms.add(pamName);                                    //e.g. HO_Status
                  //Use FMname partition name for ignoring Empty/Masked FMs
                  watchedPartitions.add(partition);                              //e.g. HO
                  partitionStatusMap.put(partition,pamName);
                }
                if(pamName.toLowerCase().contains(partition.toLowerCase()) && pamName.contains("_Message")  ){
                  watchedAlarms.add(pamName);                                   //e.g. HO_Message
                  partitionMessageMap.put(partition,pamName);
                }
              }
            }
            else{
              logger.warn("[HCAL " + functionManager.FMname+"] AlarmerWatchThread: not watching this partition: "+partition+" because LV2:"+LV2FMname+" has no supervisor");
            }
          }
        }
        catch (ParameterServiceException e){
          logger.error("[HCAL " + functionManager.FMname+"] AlarmerWatchThread: fail to get FM_PARTITION from this LV2:"+LV2FMname);
        }
      }
      // Add Unknown status
      //watchedAlarms.add("Unknown_Status");
      //watchedPartitions.add("Unknown");
      String[]  watchedAlarms_Str     = new String[watchedAlarms.size()];
      watchedAlarms_Str = watchedAlarms.toArray(watchedAlarms_Str);

      for (String alarm : watchedAlarms){
        logger.info("[HCAL " + functionManager.FMname+"] AlarmerWatchThread: watchedAlarms built from LV2 names:"+alarm);
      }

      stopAlarmerWatchThread = false;
      try {
        @SuppressWarnings("unused")
        URL alarmerURL = new URL(functionManager.alarmerURL);
      } catch (MalformedURLException e) {
        // in case the URL is bogus, just don't run the thread
        stopAlarmerWatchThread = true;
        logger.warn("[HCAL " + functionManager.FMname + "] HCALEventHandler: alarmerWatchThread: value of alarmerURL is not valid: " + functionManager.alarmerURL + "; not checking alarmer status");
      }

      // poll alarmer status in the Running/RunningDegraded states every 30 sec to see if it is still OK/alive
      while ((stopAlarmerWatchThread == false) && (functionManager != null) && (functionManager.isDestroyed() == false)) {
        FMstate = functionManager.getState().getStateString();
        if (FMstate.equals(HCALStates.RUNNING.toString()) || FMstate.equals(HCALStates.RUNNINGDEGRADED.toString()) ) {
          try {
            if (delayAlarmerWatchThread){
              try { Thread.sleep(60000); }   // delay the first poll by 60s when we enter Running state
              catch (Exception ignored) { return; }
              delayAlarmerWatchThread=false;
            }
            // Empty or masked partitions. Alarms will be ignored for these partitions.
            VectorT<StringT> EmptyOrMaskedFMs       = (VectorT<StringT>)functionManager.getParameterSet().get("EMPTY_FMS").getValue();
            VectorT<StringT> maskedFMs              = (VectorT<StringT>)functionManager.getParameterSet().get("MASK_SUMMARY").getValue();
            EmptyOrMaskedFMs.getVector().addAll(maskedFMs.getVector()); 
            LinkedHashSet<String> ignoredPartitions = new LinkedHashSet<String>();

            for(StringT FMname : EmptyOrMaskedFMs){
              if(FMnameToPartitionMap.get(FMname)!=null){
                // Should be 1) a valid partition and 2)not already ignored
                List<String> parititonsOfThisFM = FMnameToPartitionMap.get(FMname);
                for (String partition : parititonsOfThisFM){
                  if(watchedPartitions.contains(partition) && !ignoredPartitions.contains(partition)){
                    ignoredPartitions.add(partition);
                    logger.debug("[HCAL " + functionManager.FMname+"] AlarmerWatchThread: Going to ignore this parition:"+partition+" because FM is empty/masked: "+FMname.getString());
                  }
                }
              }
              else{
                String partitionOfemptyFM = FMname.getString().substring(5);
                // Should be 1) a valid partition and 2)not already ignored
                if(watchedPartitions.contains(partitionOfemptyFM) && !ignoredPartitions.contains(partitionOfemptyFM)){
                  ignoredPartitions.add(partitionOfemptyFM);
                  logger.debug("[HCAL " + functionManager.FMname+"] AlarmerWatchThread: Going to ignore this parition:"+partitionOfemptyFM+" because FM is empty/masked: "+FMname.getString());
                }
              }
            }
            for (String ignoredPartition : ignoredPartitions) {
              logger.debug("[HCAL " + functionManager.FMname+"] AlarmerWatchThread: Alarms from this masked or empty partition will be ignored: "+ignoredPartition);
            }

            // ask for the status of the HCAL alarmer
            // ("http://hcalmon.cms:9945","hcalAlarmer",0);
            XDAQParameter pam = new XDAQParameter(functionManager.alarmerURL,"hcalAlarmer",0);
            // this does a lazy get. do we need to force the update before getting it?

            // Get the status for each watched alarm
            HashMap<String, Boolean> partitionStatuses     = new HashMap<String, Boolean>();
            HashMap<String, String> partitionStatusStrings = new HashMap<String, String>();
            pam.select(watchedAlarms_Str);
            pam.get();
            for (String thisPartition : watchedPartitions) {
              String thisAlarm           = partitionStatusMap.get(thisPartition);
              if (pam.getValue(thisAlarm)!=null){
                String alarmerStatusString = pam.getValue(thisAlarm);
                partitionStatusStrings.put(thisPartition, alarmerStatusString);
                if (alarmerStatusString.equals("OK")) {
                  partitionStatuses.put(thisPartition, true);
                } else if (alarmerStatusString.equals("")) {
                  String errMessage = "[David Log " + functionManager.FMname + "] Cannot get alarmerStatusValue with alarmer name " + thisAlarm + ", partition name " + thisPartition + ".";
                  logger.warn(errMessage); 
                } else {
                  partitionStatuses.put(thisPartition, false);
                }
              }
              else{
                String errMessage="HCAL "+functionManager.FMname+"] AlarmerWatchThread: No parameter named: "+thisAlarm+" in alarmer's infospace!";
                logger.error(errMessage);
                throw new Exception(errMessage);
              }
            }

            // Calculate total alarmer status (= AND of all partition statuses, excluding empty and masked ones) 
            Boolean totalStatus = true;
            ArrayList<String> badAlarmerPartitions = new ArrayList<String>();
            for (String partitionName : watchedPartitions) {
              if (ignoredPartitions.contains(partitionName) ) {
                continue;
              }
              totalStatus = (totalStatus && partitionStatuses.get(partitionName));
              if (!partitionStatuses.get(partitionName)) {
                badAlarmerPartitions.add(partitionName);
              }
            }
            logger.debug("[HCAL " + functionManager.FMname+"] AlarmerWatchThread: Finished vetoing statuses and adding bad partitions. TotalStatus is "+totalStatus);

            // Actions taken based on alarmer results
            if (!totalStatus) {
              // Print debug partition results
              logger.debug("[HCAL " + functionManager.FMname + "] HCALEventHandler: alarmerWatchThread : Printing partition statuses:");
              for (String partitionName : watchedPartitions) {
                String thisPartitionAlarmerResults = "[HCAL " + functionManager.FMname + "] David log : Partition " + partitionName + " / alarm " + partitionStatusMap.get(partitionName) + " => ";
                if (partitionStatuses.get(partitionName)) {
                  thisPartitionAlarmerResults = thisPartitionAlarmerResults + " OK";
                } else {
                  thisPartitionAlarmerResults = thisPartitionAlarmerResults + " NOT OK";
                }
                if (ignoredPartitions.contains(partitionName)) {
                  thisPartitionAlarmerResults = thisPartitionAlarmerResults + " (but FM is EMPTY/MASKED, so ignoring)";
                }
                logger.debug(thisPartitionAlarmerResults);
              }
              // Print simple result
              String PartitionAlarmerResult = "[HCAL " + functionManager.FMname + "] AlarmerWatchThread : Following partition status is not OK: ";
              for (String partitionName : watchedPartitions){
                if (!partitionStatuses.get(partitionName) && !ignoredPartitions.contains(partitionName)){
                  PartitionAlarmerResult += partitionName + " " ;
                }
              }
              logger.warn(PartitionAlarmerResult);

              // Put a message in the fishy box
              String badAlarmerMessage = "><))),> : RunningDegraded state due to:";
              for (String partitionName : badAlarmerPartitions) {
                badAlarmerMessage = badAlarmerMessage + " " + partitionName;
              }
              badAlarmerMessage += ". Please contact HCAL DOC!";

              // get fresh FM state after first delay poll 
              FMstate = functionManager.getState().getStateString();
              if (FMstate.equals(HCALStates.RUNNING.toString()) || FMstate.equals(HCALStates.RUNNINGDEGRADED.toString()) ) {
                // total status not OK and FMstate != RunningDegraded => go to degraded state 
                if(!FMstate.equals(HCALStates.RUNNINGDEGRADED.toString())) {
                  logger.warn("[HCAL " + functionManager.FMname + "] AlarmerWatchThread: due to bad alarmer status (see previous messages), going to RUNNINGDEGRADED state");
                  String runningDegradedReason = "Running degraded due to";
                  for (String partitionName : badAlarmerPartitions){
                    if (ignoredPartitions.contains(partitionName)){
                      continue;
                    }
                    String alarmDetails  = pam.getValue(partitionMessageMap.get(partitionName));
                    if (alarmDetails!=null){
                      runningDegradedReason += " " + partitionName + ":" + alarmDetails + " |";
                    }
                  }
                  logger.warn("[HCAL HCAL_LEVEL_1] AlarmerWatchThread(): Setting running degraded reason to" + runningDegradedReason);
                  Input degradeInput = new Input(HCALInputs.SETRUNNINGDEGRADED);
                  degradeInput.setReason(runningDegradedReason);
                  functionManager.fireEvent(degradeInput);
                  functionManager.setAction(badAlarmerMessage);
                }
                else {
                  logger.debug("[HCAL " + functionManager.FMname + "] AlarmerWatchThread: due to bad alarmer status (see previous messages), going to stay in RUNNINGDEGRADED state");
                  functionManager.setAction(badAlarmerMessage);
                }
              }
            } else {
              FMstate = functionManager.getState().getStateString();
              // Alarmer status is OK. If RUNNINGDEGRADED, unset.
              if(FMstate.equals(HCALStates.RUNNINGDEGRADED.toString())) {
                // if we got back to OK, go back to RUNNING
                logger.warn("[HCAL " + functionManager.FMname + "] AlarmerWatchThread: Alarmer status is OK. Going to get out of RUNNINGDEGRADED state now");
                functionManager.fireEvent(HCALInputs.UNSETRUNNINGDEGRADED);
              }
            }
          }
          catch (Exception e) {
            // on exceptions, we go to degraded, or stay there
            if(!functionManager.getState().getStateString().equals(HCALStates.RUNNINGDEGRADED.toString())) {
              String errMessage = "[HCAL " + functionManager.FMname + "] Error! Got an exception: AlarmerWatchThread()\n...\nHere is the exception: " +e.getMessage()+"\n...going to change to RUNNINGDEGRADED state";
              logger.error(errMessage);
              Input degradeInput = new Input(HCALInputs.SETRUNNINGDEGRADED);
              degradeInput.setReason("Cannot get monitoring data from alarmer due to Exception: " + e.getMessage());
              functionManager.fireEvent(degradeInput);
            }
            else {
              String errMessage = "[HCAL " + functionManager.FMname + "] Error! Got an exception: AlarmerWatchThread()\n...\nHere is the exception: " +e+"\n...going to stay in RUNNINGDEGRADED state";
              logger.warn(errMessage);
            }
          }
        }
        // delay between polls
        try { Thread.sleep(30000); } // check every 30 seconds
        catch (Exception ignored) { return; }
      }

      // stop the HCAL supervisor watchdog thread
      //System.out.println("[HCAL " + functionManager.FMname + "] ... stopping HCAL supervisor watchdog thread done.");
      logger.debug("[HCAL " + functionManager.FMname + "] ... stopping HCAL supervisor watchdog thread done.");
      AlarmerWatchThreadList.remove(this);
    }
  }
  
  // Function to receive parameter and set to same parameter
  void CheckAndSetParameter(ParameterSet pSet , String PamName, boolean printResult) throws UserActionException{
    CheckAndSetTargetParameter(pSet,PamName, PamName,printResult);
  }
  void CheckAndSetParameter(ParameterSet pSet , String PamName) throws UserActionException{
    CheckAndSetTargetParameter(pSet,PamName, PamName,true);
  }


  // Function to receive parameter and set to other parameter
  void CheckAndSetTargetParameter(ParameterSet pSet , String InputPamName, String TargetPamName, boolean printResult) throws UserActionException{
    String inputString = getUserFunctionManager().getLastInput().getInputString();

    if( pSet.get(InputPamName) != null){
      if (pSet.get(InputPamName).getType().equals(StringT.class)){
        String PamValue = ((StringT)pSet.get(InputPamName).getValue()).getString();
        if (functionManager.getParameterSet().get(TargetPamName) != null){
        functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(TargetPamName, new StringT(PamValue)));
          if(printResult){
            logger.info("[HCAL "+ functionManager.FMname +" ] Received pam:"+InputPamName+ " and set pam:"+ TargetPamName +" from last input= "+inputString+". Here is the set value: \n"+ PamValue);
          }
          else{
            logger.info("[HCAL "+ functionManager.FMname +" ] Received pam:"+InputPamName+ " and set pam:"+ TargetPamName +" from last input= "+inputString+"." );
          }
        }else{
          String errMessage = "Trying to set pam="+TargetPamName+" from input "+inputString+" but cannot find target parameter "+TargetPamName;
          logger.error(errMessage);
          throw new UserActionException(errMessage);
        }
      }
      if (pSet.get(InputPamName).getType().equals(IntegerT.class)){
        Integer PamValue = ((IntegerT)pSet.get(InputPamName).getValue()).getInteger();
        if (functionManager.getParameterSet().get(TargetPamName) != null){
          functionManager.getParameterSet().put(new FunctionManagerParameter<IntegerT>(TargetPamName, new IntegerT(PamValue)));
          if(printResult){
            logger.info("[HCAL "+ functionManager.FMname +" ] Received pam:"+InputPamName+ " and set pam:"+ TargetPamName +" from last input= "+inputString+". Here is the set value: \n"+ PamValue);
          }
          else{
            logger.info("[HCAL "+ functionManager.FMname +" ] Received pam:"+InputPamName+ " and set pam:"+ TargetPamName +" from last input= "+inputString+"." );
          }
        }
        else{
          String errMessage = "Trying to set pam="+TargetPamName+" from input "+inputString+" but cannot find target parameter "+TargetPamName;
          logger.error(errMessage);
          throw new UserActionException(errMessage);
        }
      }
      if (pSet.get(InputPamName).getType().equals(BooleanT.class)){
        Boolean PamValue = ((BooleanT)pSet.get(InputPamName).getValue()).getBoolean();
        if (functionManager.getParameterSet().get(TargetPamName) != null){
          functionManager.getParameterSet().put(new FunctionManagerParameter<BooleanT>(TargetPamName, new BooleanT(PamValue)));
          if(printResult){
            logger.info("[HCAL "+ functionManager.FMname +" ] Received pam:"+InputPamName+ " and set pam:"+ TargetPamName +" from last input= "+inputString+". Here is the set value: \n"+ PamValue);
          }
          else{
            logger.info("[HCAL "+ functionManager.FMname +" ] Received pam:"+InputPamName+ " and set pam:"+ TargetPamName +" from last input= "+inputString+"." );
          }
        }
        else{
           String errMessage = "Trying to set pam="+TargetPamName+" from input "+inputString+" but cannot find target parameter "+TargetPamName;
          logger.error(errMessage);
          throw new UserActionException(errMessage);
        }
      }
    }
    else{
      String errMessage =" Did not receive "+ InputPamName +" from last input= "+inputString+" ! Please check if "+ InputPamName+ " was filled";
      logger.warn(errMessage);
      throw new UserActionException(errMessage);
    }
  }

  // Print of the names of the QR in an arrayList
  void PrintQRnames(List<QualifiedResource> qrlist){
    QualifiedResourceContainer qrc = new QualifiedResourceContainer(qrlist);
    PrintQRnames(qrc);
  }

  // Print of the names of the QR in a QRContainer 
  void PrintQRnames(QualifiedResourceContainer qrc){
    String Names = "";
    if (!qrc.isEmpty()){
      List<QualifiedResource> qrlist = qrc.getQualifiedResourceList();
      for(QualifiedResource qr : qrlist){
        Names += qr.getName() + ";";
      }
    }
    logger.info(Names);
  }

  // turn all TCDS apps into XdaqServiceApplication 
  QualifiedGroup ConvertTCDSAppsToServiceApps(QualifiedGroup qg) {
     boolean groupWasChanged = false;
     Group origGroup = qg.getGroup();
     List<Resource> childrenRscList = origGroup.getChildrenResources();
     List<QualifiedResource> xdaqQRExecutiveList = qg.seekQualifiedResourcesOfType(new XdaqExecutive());

     for (QualifiedResource qr : xdaqQRExecutiveList) {
       boolean isTCDSExec = false;
       XdaqExecutiveResource exec = (XdaqExecutiveResource) qr.getResource();
       List<XdaqApplicationResource> appList = exec.getApplications();
       for (ListIterator<XdaqApplicationResource> iterator = appList.listIterator(); iterator.hasNext();) {
         XdaqApplicationResource xar = iterator.next();
         if (xar.getName().contains("tcds") || xar.getName().contains("TCDS")) {
           isTCDSExec = true;
           childrenRscList.remove(xar); // remove the app!
           iterator.remove();
           // try to make new xdaqserviceapp
           String qrTypeOfServiceApp = "rcms.fm.resource.qualifiedresource.XdaqServiceApplication";
           try {
             XdaqApplicationResource tcdsAppRsc = new XdaqApplicationResource(xar.getDirectory(),
                 xar.getName(),xar.getURI(),qrTypeOfServiceApp,xar.getConfigFile(),xar.getRole());
             iterator.add(tcdsAppRsc);
             childrenRscList.add(tcdsAppRsc);
           } catch (Exception e) {
             String errMessage = "[HCAL " + functionManager.FMname + "] got an Exception while attempting to modify TCDS applications";
             functionManager.goToError(errMessage,e);
           }
         }
       } // end loop over apps
       if(isTCDSExec) {
         groupWasChanged = true;
       }
     } // end of loop over execs

     if(groupWasChanged) {
       // reset children resources
       try {
         origGroup.setChildrenResources(childrenRscList);
       } catch (Exception e) {
         String errMessage = "[HCAL " + functionManager.FMname + "] got an Exception while attempting to modify QG application resources in ConvertTCDSAppsToServiceApps()" ;
         functionManager.goToError(errMessage,e);
       }

       // make new QG
       QualifiedGroup newQG = new QualifiedGroup(origGroup);

       return newQG;
     }

     return qg;
  }

  void maskTCDSExecAndJC(QualifiedGroup qg){
     // mark TCDS execs as initialized and mask their JobControl
    List<QualifiedResource> xdaqExecutiveList   = qg.seekQualifiedResourcesOfType(new XdaqExecutive());
    List<QualifiedResource> xdaqServiceAppsList = qg.seekQualifiedResourcesOfType(new XdaqServiceApplication());
    List<QualifiedResource> xdaqAppsList = qg.seekQualifiedResourcesOfType(new XdaqApplication());

    //In case we turn TCDS to service app on-the-fly in the future...
    xdaqAppsList.addAll(xdaqServiceAppsList);

    for (QualifiedResource qr : xdaqExecutiveList) {
      if (qr.getResource().getHostName().contains("tcds") ) {
        qr.setInitialized(true);
        qg.seekQualifiedResourceOnPC(qr, new JobControl()).setActive(false);
      }
    }   
    // mark TCDS apps as initialized 
    for (QualifiedResource qr : xdaqAppsList) {
      if (qr.getResource().getHostName().contains("tcds") ) {
        qr.setInitialized(true);
      }
    }
  }

  // Get property from a QR
  public String getProperty(QualifiedResource QR,  String name ) throws Exception {

    List<ConfigProperty> propertiesList = QR.getResource().getProperties();

    if(propertiesList.isEmpty()) {
      throw new Exception("Property list is empty");
    }
    ConfigProperty property = null;
    Iterator<ConfigProperty> iter = propertiesList.iterator();
    while(iter.hasNext()) {
      property = iter.next();
      if(property.getName().equals(name)) {
        return property.getValue();
      }
    }
    throw new Exception("Property "+name+" not found");
  }

  // Fill TCDS containers with URI 
  public void FillTCDScontainersWithURI() {
		for (QualifiedResource qr : functionManager.containerhcalSupervisor.getApplications() ){
      try {
          String[] AppNameString ;
          String[] AppURIString  ;

		  		XDAQParameter pam =((XdaqApplication)qr).getXDAQParameter();
		  		pam.select(new String[] {"HandledApplicationNameInstanceVector","HandledApplicationURIVector"});
		  		pam.get();
          AppNameString = pam.getVector("HandledApplicationNameInstanceVector");
          AppURIString  = pam.getVector("HandledApplicationURIVector");
          VectorT<StringT> AppNameVector = new VectorT<StringT>();
          VectorT<StringT> AppURIVector  = new VectorT<StringT>();
          for (String s : AppNameString){          AppNameVector.add(new StringT(s));        }
          for (String s : AppURIString){           AppURIVector.add(new StringT(s));        }

          List<XdaqApplication> tcdsList = new ArrayList<XdaqApplication>();
          for (StringT appName : AppNameVector){
            StringT appURI = AppURIVector.get( AppNameVector.indexOf(appName));
            // Check URI to see if this is a TCDS app
            if (appURI.contains("tcds-control")){
              String QRtype = "rcms.fm.resource.qualifiedresource.XdaqApplication";
              String tcdsname = appName.getString();
              String tcdsURI  = appURI.getString();
              int sessionId = ((IntegerT)functionManager.getParameterSet().get("SID").getValue()).getInteger();
              XdaqApplicationResource tcdsAppRsc = new XdaqApplicationResource(functionManager.getGroup().getDirectory(), tcdsname, tcdsURI , QRtype, null, null);
              XdaqApplication  tcdsApp = new XdaqApplication(tcdsAppRsc);
              tcdsList.add(tcdsApp);
            }
          }
          functionManager.containerTCDSControllers = new XdaqApplicationContainer(tcdsList);
          logger.info("FillTCDScontainersWithURI(): found following TCDS apps");
          PrintQRnames(functionManager.containerTCDSControllers);
      }
      catch (XDAQTimeoutException e) {
				String errMessage = "[HCAL " + functionManager.FMname + "] Error! XDAQTimeoutException: FillTCDScontainersWIthURI(): couldn't get xdaq parameters";
				functionManager.goToError(errMessage,e);
			}
      catch (XDAQException e) {
				String errMessage = "[HCAL " + functionManager.FMname + "] Error! XDAQTimeoutException: FillTCDScontainersWIthURI(): couldn't get xdaq parameters";
				functionManager.goToError(errMessage,e);
			}
      catch (ResourceException e){
        String errMessage = "[HCAL " + functionManager.FMname + "] failed HALT of TCDS applications with reason: "+ e.getMessage();
        logger.warn(errMessage);
      }
    }
  }
}
