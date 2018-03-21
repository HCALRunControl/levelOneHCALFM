package rcms.fm.app.level1;

import java.net.URI;

import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.Arrays;

import rcms.fm.fw.user.UserActionException;
import rcms.fm.fw.user.UserFunctionManager;
import rcms.fm.resource.QualifiedGroup;
import rcms.fm.resource.QualifiedResource;
import rcms.fm.resource.QualifiedResourceContainer;
import rcms.fm.resource.QualifiedResourceContainerException;
import rcms.fm.resource.CommandException;
import rcms.fm.resource.qualifiedresource.XdaqApplicationContainer;
import rcms.fm.resource.qualifiedresource.XdaqApplication;
import rcms.fm.resource.qualifiedresource.XdaqExecutive;
import rcms.fm.resource.qualifiedresource.FunctionManager;
import rcms.resourceservice.db.resource.Resource;
import rcms.resourceservice.db.resource.ResourceException;
import rcms.resourceservice.db.resource.xdaq.XdaqApplicationResource;
import rcms.resourceservice.db.resource.xdaq.XdaqExecutiveResource;
import rcms.statemachine.definition.Input;
import rcms.statemachine.definition.State;
import rcms.statemachine.definition.StateMachineDefinitionException;
import rcms.fm.resource.StateVector;
import rcms.fm.resource.StateVectorCalculation;
import rcms.util.logger.RCMSLogger;
import rcms.util.logsession.LogSessionConnector;
import rcms.util.logsession.LogSession;
import rcms.errorFormat.CMS.CMSError;

import rcms.fm.fw.parameter.FunctionManagerParameter;
import rcms.fm.fw.parameter.ParameterSet;
import rcms.fm.fw.parameter.CommandParameter;

import rcms.resourceservice.db.resource.config.ConfigProperty;
import rcms.resourceservice.db.resource.fm.FunctionManagerResource;

import rcms.utilities.runinfo.RunInfo;

import rcms.util.logsession.LogSessionException;
import rcms.xdaqctl.XDAQParameter;

import java.text.DateFormat;
import java.util.Date;
import java.util.TimeZone;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Map;
import java.net.URL;
import java.net.MalformedURLException;

import rcms.fm.fw.parameter.type.StringT;
import rcms.fm.fw.parameter.type.IntegerT;
import rcms.fm.fw.parameter.type.VectorT;

import net.hep.cms.xdaqctl.WSESubscription;
import net.hep.cms.xdaqctl.XDAQException;

/**
 * Function Machine base class for HCAL Function Managers
 * 
 *
 *
 */

public class HCALFunctionManager extends UserFunctionManager {

  static RCMSLogger logger = new RCMSLogger(HCALFunctionManager.class);

  private HCALParameters hcalParameters;
  public HCALParameterSender parameterSender;

  // definition of some XDAQ containers
  public XdaqApplicationContainer containerXdaqApplication = null;  // this container contains _all_ XDAQ executives
  public XdaqApplicationContainer containerXdaqServiceApplication = null;  // this container contains _all_ XDAQ service applications 

  public XdaqApplicationContainer containerhcalSupervisor      = null;
  public XdaqApplicationContainer containerlpmController       = null;
  public XdaqApplicationContainer containerPIController       = null;
  public XdaqApplicationContainer containerICIController       = null;
  public XdaqApplicationContainer containerTCDSControllers     = null;
  public XdaqApplicationContainer containerhcalDCCManager      = null;
  public XdaqApplicationContainer containerTriggerAdapter      = null;
  public XdaqApplicationContainer containerTTCciControl        = null;
  public XdaqApplicationContainer containerLTCControl          = null;
  public XdaqApplicationContainer containerEVM                 = null;
  public XdaqApplicationContainer containerBU                  = null;
  public XdaqApplicationContainer containerRU                  = null;
  public XdaqApplicationContainer containerFUResourceBroker    = null;
  public XdaqApplicationContainer containerFUEventProcessor    = null;
  public XdaqApplicationContainer containerStorageManager      = null;
  public XdaqApplicationContainer containerPeerTransportATCP   = null;
  public XdaqApplicationContainer containerhcalRunInfoServer   = null;

  // string containing details on the setup from where this FM was started
  public String RunSetupDetails = "empty";
  public String FMfullpath = "empty";
  public String FMname = "empty";
  public String FMurl = "empty";
  public String FMuri = "empty";
  public String FMrole = "empty";
  public String FMpartition = "empty";
  public Date FMtimeofstart;
  public String utcFMtimeofstart = "empty";

  // set from the controlled EventHandler 
  public String  RunType = "";
  public Integer RunNumber = 0;
  public Integer CachedRunNumber = 0;

  // connector to log session db, used to create session identifiers
  public LogSessionConnector logSessionConnector;

  // connector to the RunInfo database
  public RunInfo HCALRunInfo = null;

  // container of XdaqExecutive in the running Group.
  public XdaqApplicationContainer containerXdaqExecutive = null;

  // container of all active FunctionManagers in the running Group.
  public QualifiedResourceContainer containerFMChildren = null;
  
  // container of all FunctionManagers in the running Group.
  public QualifiedResourceContainer containerAllFMChildren = null;
  
  // container with the EvmTrig FunctionManager for local runs
  public QualifiedResourceContainer containerFMEvmTrig = null;

  // container with the TCDSLPM FunctionManager for local runs
  public QualifiedResourceContainer containerFMTCDSLPM = null;

  // container with all FunctionManagers except the EvmTrig and TCDSLPM
  public QualifiedResourceContainer containerFMChildrenNoEvmTrigNoTCDSLPM = null;

  public QualifiedResourceContainer containerFMChildrenEvmTrig = null;
  public QualifiedResourceContainer containerFMChildrenNormal = null;

  // used to derive the state from the resources i.e. child FMs
  public StateVectorCalculation svCalc = null;
  private boolean CustomStatesDefined = false;

  // the actual calculated State.
  public State calcState = null;

  protected HCALEventHandler theEventHandler = null;
  // switch to find out if FM is available
  private boolean destroyed = false;

  // switch to check if the FM is already going to an error state
  public boolean ErrorState = false;

  // switch to find out if run was started so that a publishing to the runInfo DB would make sense before destroying 
  protected boolean RunWasStarted = false;

  // HCAL RunInfo namespace, the FM name will be added in the createAction() method
  public String HCAL_NS = "CMS.";

  // The HCAL FED ranges
  protected Boolean HCALin = false;
  protected final Integer firstHCALFedId = 700;
  protected final Integer lastHCALFedId = 731;

  protected Boolean HBHEain = false;
  protected final Integer firstHBHEaFedId = 700;
  protected final Integer lastHBHEaFedId = 705;

  protected Boolean HBHEbin = false;
  protected final Integer firstHBHEbFedId = 706;
  protected final Integer lastHBHEbFedId = 711;

  protected Boolean HBHEcin = false;
  protected final Integer firstHBHEcFedId = 712;
  protected final Integer lastHBHEcFedId = 717;

  protected Boolean HFin = false;
  protected final Integer firstHFFedId = 718;
  protected final Integer lastHFFedId = 723;

  protected Boolean HOin = false;
  protected final Integer firstHOFedId = 724;
  protected final Integer lastHOFedId = 731;

  protected List<String> HCALFedList = null;

  // switch which records if applications are defined which can talk asynchronous SOAP
  public boolean asyncSOAP = true;

  // flag to memorize the role of this FM
  public boolean Level2FM = false;

  // switch to enable async SOAP communication with the HCAL supervisor
  public boolean asynchcalSupervisor = true;

  // switch to remember FM history
  public boolean FMsWereConfiguredOnce = false;
  public boolean FMWasInRunningStateOnce = false;
  public boolean FMWasInPausedState = false;

  // switches to define the operation of any ATCP XDAQ application
  public boolean StopATCP = false;
  public boolean ATCPsWereStartedOnce = false;

  // list of XMAS/WSE ressouces for which flashlist, etc. can be accessed
  public List<QualifiedResource> wseList = null;  // list of XDAQ apps which are detected to sent flashlists
  public HashMap<String,WSESubscription> wseMap = null; // storage of subscribed flashlists
  public boolean XMASMonitoringEnabled = false;  // switch
  public WSESubscription wsSubscription = null;  
  public String RunInfoFlashlistName = "empty";

  // switch to use or not use zero suppression given by a HCAL CFG snippet
  public boolean useZS = true;

  // switch to use or not use the special zero suppression given by a HCAL CFG snippet (to use it also the standard useZS = true is needed so this snippet will be used too)
  public boolean useSpecialZS = true;

  // switch to enable special configuration snippets needed for the VdM scan
  public boolean useVdMSnippet = false;

  // switch to find out if this FM is configuring for the very first time
  protected Boolean VeryFirstConfigure = true;

  public HCALStateNotificationHandler theStateNotificationHandler = null;

  public String rcmsStateListenerURL = "";

  public String alarmerURL = "";

  public String alarmerPartition = "";

  public HCALFunctionManager() {
    // any State Machine Implementation must provide the framework with some information about itself.

    // make the parameters available
    this.hcalParameters = HCALParameters.getInstance();
    addParameters();
  }

  // add parameters to parameterSet. After this they are accessible.
  private void addParameters() {
    parameterSet = this.hcalParameters;
  }

  // enable/disable the FM custom GUI
  public boolean hasCustomGUI () {
    Boolean customGUI = true;
    return customGUI; 
  }

  public void init() throws StateMachineDefinitionException,rcms.fm.fw.EventHandlerException {

    // set first of all the State Machine Definition
    setStateMachineDefinition(new HCALStateMachineDefinition());

    // add error handler
    addEventHandler(new HCALErrorHandler());

    // add XMAS monitor handler
    addEventHandler(new HCALMonitorHandler());

    // add SetParameterHandler
    addEventHandler(new HCALSetParameterHandler());

    // state notification handler
    theStateNotificationHandler = new HCALStateNotificationHandler();
    addEventHandler(theStateNotificationHandler);

    // get log session connector
    logSessionConnector = getLogSessionConnector();

    // get session ID
    //getSessionId();
  }

  public void createAction(ParameterSet<CommandParameter> cps) throws UserActionException {
    // This method is called by the framework when the Function Manager is created.

    //  needed before RCMS release 3.2.0 here: super.createAction(cps); 
    
    System.out.println("[HCAL base] entering createAction ...");
    logger.debug("[HCAL base] entering createAction ...");

    // Retrieve the configuration for this Function Manager from the Group
    FunctionManagerResource fmConf = ((FunctionManagerResource) getQualifiedGroup().getGroup().getThisResource());

    FMfullpath = fmConf.getDirectory().getFullPath().toString();
    FMname = fmConf.getName();
    FMpartition =FMname.substring(5);  // FMname = HCAL_X; 
    FMurl = fmConf.getSourceURL().toString();
    FMuri = fmConf.getURI().toString();
    FMrole = fmConf.getRole();
    FMtimeofstart = new Date();
    DateFormat dateFormatter = new SimpleDateFormat("M/d/yy hh:mm:ss a z");
    dateFormatter.setTimeZone(TimeZone.getTimeZone("UTC"));;
    utcFMtimeofstart = dateFormatter.format(FMtimeofstart);

    // set statelistener URL
    try {
      URL fmURL = new URL(FMurl);
      String rcmsStateListenerHost = fmURL.getHost();
      int rcmsStateListenerPort = fmURL.getPort()+1;
      String rcmsStateListenerProtocol = fmURL.getProtocol();
      rcmsStateListenerURL = rcmsStateListenerProtocol+"://"+rcmsStateListenerHost+":"+rcmsStateListenerPort+"/rcms";
    } catch (MalformedURLException e) {
      String errMessage = "[HCAL " + FMname + "] Error! MalformedURLException in createAction" + e.getMessage();
      logger.error(errMessage,e);
      sendCMSError(errMessage);
      getParameterSet().put(new FunctionManagerParameter<StringT>("STATE",new StringT("Error")));
      getParameterSet().put(new FunctionManagerParameter<StringT>("ACTION_MSG",new StringT(errMessage)));
      if (theEventHandler.TestMode.equals("off")) { firePriorityEvent(HCALInputs.SETERROR); ErrorState = true; return;}
    }

    System.out.println("[HCAL " + FMname + "] createAction called.");
    logger.debug("[HCAL " + FMname + "] createAction called.");

    logger.info("[HCAL " + FMname + "] This is the HCALFM.jar version message.\nThis package is compiled against RCMS_4_2_2.");


    // get the User Function Manager setup details 
    RunSetupDetails = "\nThe used setup is: " + FMfullpath;
    RunSetupDetails += "\nFM named: " + FMname;
    RunSetupDetails += "\nFM URL: " + FMurl;
    RunSetupDetails += "\nFM URI: " + FMuri;
    RunSetupDetails += "\nFM role: " + FMrole;
    RunSetupDetails += "\nthis FM was started at: " + utcFMtimeofstart;

    logger.info("[HCAL " + FMname + "] Run configuration details" + RunSetupDetails);

    // set RunInfo namespace for this instance of the FM
    HCAL_NS += fmConf.getName();

    // try to guess the role of this FM
    if (FMrole.startsWith("Level2")) {
      Level2FM = true; 
      logger.debug("[HCAL " + FMname + "] This is a Level2 FM, the role is: " + FMrole);
    }
    else {
      logger.debug("[HCAL " + FMname + "] This is not a Level2 FM, the role is: " + FMrole);
    }

    destroyed = false;

    logger.warn("JohnLog: about to construct HCALParameterSender.");
    this.parameterSender = new HCALParameterSender(this);
    this.parameterSender.start();
    logger.warn("JohnLog: finished calling HCALParameterSender.start()");

    System.out.println("[HCAL " + FMname + "] createAction executed ...");
    logger.debug("[HCAL " + FMname + "] createAction executed ...");
  }

  public void destroyAction() throws UserActionException {
    // This method is called by the framework when the Function Manager is destroyed.

    System.out.println("[HCAL " + FMname + "] destroyAction called");
    logger.info("[HCAL " + FMname + "] destroyAction called");

    
    // if RunInfo database is connected try to report the destroying of this FM
    /*if ((HCALRunInfo!=null) && (RunWasStarted)) {
      {
      Date date = new Date();
      Parameter<DateT> stoptime = new Parameter<DateT>("TIME_ON_EXIT",new DateT(date));
      try {
      logger.debug("[HCAL " + FMname + "] Publishing to the RunInfo DB TIME_ONE_EXIT: " + date.toString());
      if (HCALRunInfo != null) { HCALRunInfo.publish(stoptime); }
      }
      catch (RunInfoException e) {
      String errMessage = "[HCAL " + FMname + "] Error! RunInfoException: something seriously went wrong when publishing the run time on exit ...\nProbably this is OK when the FM was destroyed.";
      logger.error(errMessage,e);
    // supressed to not worry the CDAQ shifter sendCMSError(errMessage);
    }
    }
    {
    Parameter<StringT> StateOnExit = new Parameter<StringT>("STATE_ON_EXIT",new StringT(getState().getStateString()));
    try {
    logger.debug("[HCAL " + FMname + "] Publishing to the RunInfo DB STATE_ON_EXIT: " + getState().getStateString());
    if (HCALRunInfo != null) { HCALRunInfo.publish(StateOnExit); }
    }
    catch (RunInfoException e) {
    String errMessage = "[HCAL " + FMname + "] Error! RunInfoException: something seriously went wrong when publishing the run state on exit ...\nProbably this is OK when the FM was destroyed.";
    logger.error(errMessage,e);
    // supressed to not worry the CDAQ shifter sendCMSError(errMessage);
    }
    }

    HCALRunInfo = null; // make RunInfo ready for the next round of run info to store
    }*/

    // LV1 should try to close any open session ID not requested by LV0 
    if ( !getQualifiedGroup().seekQualifiedResourcesOfType(new FunctionManager()).isEmpty()) {
      int sessionId       = ((IntegerT)getParameterSet().get("SID").getValue()).getInteger();
      Integer SIDforLV0   = ((IntegerT)getParameterSet().get("INITIALIZED_WITH_SID").getValue()).getInteger();
      if (logSessionConnector!=null && logSessionConnector.getSession(sessionId)!=null){
        LogSession currentSession = logSessionConnector.getSession(sessionId);
        logger.debug("[HCAL "+FMname+"] current log session is \n"+currentSession.toString());
        if (currentSession.isOpen()){
          if(SIDforLV0.equals(-1)){
            logger.info("[HCAL "+FMname+"] Closing current log session "+sessionId);
            closeSessionId(); 
          }
          else{
            logger.info("[HCAL "+FMname+"] Not closing current log session "+sessionId+" as it is requested by LV0 ");
          }
        }
        else{
            logger.info("[HCAL "+FMname+"] Not closing current log session "+sessionId+" as it is already closed");
        }
      }
    }

    // unsubscribe from retrieving XMAS info
    if (XMASMonitoringEnabled) { unsubscribeWSE(); }  

    // retrieve the Function Managers and kill them
    if (containerFMChildren!=null && containerFMChildren.getQualifiedResourceList()!=null) {
      Iterator it = containerFMChildren.getQualifiedResourceList().iterator();
      FunctionManager fmChild = null; 
      while (it.hasNext()) {
        fmChild = (FunctionManager) it.next();


        logger.info("[HCAL " + FMname + "] Will destroy FM named: " + fmChild.getResource().getName().toString() + "\nThe role is: " + fmChild.getResource().getRole().toString() + "\nAnd the URI is: " + fmChild.getResource().getURI().toString());

        if (fmChild!=null && fmChild.isInitialized()) {
          try {
            fmChild.destroy();
          }
          catch (Exception e) {
            String errMessage = "[HCAL " + FMname + "] Could not destroy FM client named: " + fmChild.getResource().getName().toString() +"\n The URI is: "+ fmChild.getResource().getURI().toString() + "\nThe exception is:\n" + e.toString();
            goToError(errMessage,e);
            throw (UserActionException) e;
          }
        }
      }
    }
    //Stop watchthreads before destroying
    theEventHandler.stopMonitorThread = true;
    theEventHandler.stopHCALSupervisorWatchThread = true;
    theEventHandler.stopTriggerAdapterWatchThread = true;
    theEventHandler.stopTriggerAdapterWatchThread = true;
    theEventHandler.stopAlarmerWatchThread = true; 
    theEventHandler.stopTTCciWatchThread = true; 
    theStateNotificationHandler.setTimeoutThread(false);

    // xdaq::reset all supervisors before destroy to release the TCDS leases 
    //VectorT<StringT> EmptyFMs  = (VectorT<StringT>)getParameterSet().get("EMPTY_FMS").getValue();
    //if (!containerhcalSupervisor.isEmpty() && !EmptyFMs.contains(new StringT(FMname))) {
    //  logger.info("[HCAL LVL2 " + FMname + "]DestroyAction: Sending halt to supervisor");
    //  
    //  try{
    //    containerhcalSupervisor.execute(HCALInputs.RESET);
    //    XDAQParameter pam = null;
    //    String stateName = "undefined";
    //    int iCount       = 0;
    //    try{
    //      while (!stateName.equals("Uninitialized") &&  iCount <=5){
    //        for (QualifiedResource qr : containerhcalSupervisor.getApplications() ){
    //          pam =((XdaqApplication)qr).getXDAQParameter();
    //          pam.select(new String[] {"stateName"});
    //          stateName = pam.getValue("stateName");
    //          logger.info("[HCAL LVL2 " + FMname + "]DestroyAction: Waiting for supervisor to be halted. StateName is "+stateName+" after "+iCount+" second");
    //        }
    //        try { Thread.sleep(1000); } 
    //        catch (Exception ignored) { return; }
    //        iCount++;
    //      }
    //    }catch (XDAQException e){
    //      String errMessage = "[HCAL LVL2 " + FMname + "] Error! DestroyAction: Cannot poll supervisor state, XdaqException:"+e.getMessage();
    //      logger.error(errMessage);
    //    }
    //  }catch (QualifiedResourceContainerException e) {
    //    String errMessage = "[HCAL LVL2 " + FMname + "] Error! DestroyAction: Cannot reset supervisor, exception(s): "+e.getCommandExceptionMap().toString();
    //    logger.error(errMessage);
    //  }
    //}

    try{
      destroyXDAQ();
    }
    catch (UserActionException e){
      String errMessage="[HCAL "+FMname+" ] Got an exception during destroyXDAQ():";
      goToError(errMessage,e);
      throw e;
    }
    
    //Ask LV1 to send halt to All TCDS apps:
    //TODO: LV2 should get its target of halt from supervisor respective TCDS apps
    if (containerFMChildren !=null && containerFMChildren.isEmpty()){
      haltTCDSControllersWithURLs();
    }

    destroyed = true;


    // Check if the session is closed finally
    if ( !getQualifiedGroup().seekQualifiedResourcesOfType(new FunctionManager()).isEmpty()) {
      int sessionId       = ((IntegerT)getParameterSet().get("SID").getValue()).getInteger();
      Integer SIDforLV0   = ((IntegerT)getParameterSet().get("INITIALIZED_WITH_SID").getValue()).getInteger();
      if (logSessionConnector!=null && logSessionConnector.getSession(sessionId)!=null){
        LogSession currentSession = logSessionConnector.getSession(sessionId);
        logger.debug("[HCAL "+FMname+"] current log session is \n"+currentSession.toString());
        if(SIDforLV0.equals(-1)){
          if (currentSession.isOpen()){
            logger.error("[HCAL "+FMname+"] ERROR! Current log session "+sessionId+ " is still open after trying to close it");
          }else{
            logger.info("[HCAL "+FMname+"] Successfully closed current log session "+sessionId);
          }
        }
      }
    }
    System.out.println("[HCAL " + FMname + "] destroyAction executed ...");
    logger.info("[HCAL " + FMname + "] destroyAction executed ...");
  }

  public void unsubscribeWSE() {

    String msg="[HCAL " + FMname + "] unsubscribeWSE(): ";

    for (QualifiedResource qr : wseList) {
      WSESubscription wsSubscription = wseMap.get(qr.getName());
      msg+="unsubscribing \""+qr.getName()+"\" ...\n";
      wsSubscription.stop();
    }

    if (wsSubscription!=null && !wsSubscription.isStopped()){
      msg+="now stopping ..."; 
      wsSubscription.stop();
    }

    logger.info(msg);

    if (!wsSubscription.isStopped()){
      String errMessage = "[HCAL " + FMname + "] Troubles here we come!! WS subscription is not stopped ...";
      logger.error(errMessage);
      sendCMSError(errMessage);
    }
  }

  public boolean isDestroyed() {
    return destroyed;
  }

  // will be called by the framework and explicitly by the HCAL level 1 FM method computeState()
  public State getUpdatedState() {

    logger.debug("[HCAL " + FMname + "] Getting the updated state ...");

    // without the qualified group we cannout do anything ;-)
    if (!getQualifiedGroup().isInitialized()) {
      logger.debug("[HCAL " + FMname + "] QualifiedGroup not initialized.\nThis should be OK when happens very early i.e. when initializing a run configuration." );

      return this.getState();
    }

    // catch very early state calculation problem when svCalc is not constructed
    if (svCalc == null) {
      logger.debug("[HCAL " + FMname + "] svCalc not constructed.\nThis should be OK when happens very early i.e. when initializing a run configuration." );
      return this.getState();
    }

    // if child FMs are available calculate the resulting state incorporate the state of all child FMs 
    //if (containerFMChildren.isEmpty() && containerFUResourceBroker.isEmpty() && containerFUEventProcessor.isEmpty() && containerStorageManager.isEmpty() ) {
    //  logger.debug("[HCAL " + FMname + "] No FM resources for asynchronous state transitions found.\nThis is probably OK for a level 2 HCAL FM.\nThis FM has the role: " + FMrole);
    //  return this.getState();
    //}
    //else {
      //logger.warn("[HCAL " + FMname + "] getUpdatedState(): Do stateVector calculation for FM with the role: " + FMrole);
      // SIC TEST
      //Iterator it = containerFMChildren.getQualifiedResourceList().iterator();
      //FunctionManager fmChild = null;
      //while (it.hasNext()) {
      //  fmChild = (FunctionManager) it.next();
      //  logger.warn("[HCAL " + FMname + "] in containerFMChildren: FM named: " + fmChild.getName() + " found with role name: " + fmChild.getRole() + " and state: " + fmChild.getState().getStateString());
      //}
      // SIC TEST
      calcState = svCalc.getState();
    //}

    //logger.warn("[HCAL " + FMname + "] getUpdatedState(): the new state of this FM - incorporating the states of all controlled resources - is: " + calcState.getStateString());

    logger.debug("[HCAL " + FMname + "] ... getting the updated state done.");

    return calcState;

  }

  // calculation of State derived from the State of the controlled resources i.e. the child FMs 
  public void defineConditionState() {

    if (!CustomStatesDefined) {

      logger.debug("[HCAL " + FMname + "] Defining the ConditionState ...");

      CustomStatesDefined=true;

      Set<QualifiedResource> resourceGroup = getQualifiedGroup().getQualifiedResourceGroup();
      svCalc = new StateVectorCalculation(resourceGroup);

      // all FM's are assumed to have either a supervisor or an LPMController which will give state notifications via xdaq2rc
      if (!containerFMChildren.isEmpty())
      {
        StateVector sv = new StateVector();
        sv.setResultState(HCALStates.INITIAL);
        sv.registerConditionState(containerFMChildren,HCALStates.INITIAL);
        svCalc.add(sv);
      }

      if (!containerFMChildren.isEmpty())
      {
        StateVector sv = new StateVector();
        sv.setResultState(HCALStates.INITIALIZING);
        sv.registerConditionState(containerFMChildren,HCALStates.INITIALIZING);
        // not needed as level-2's can ignore calculating INITIALIZING
        //sv.registerConditionState(containerlpmController,HCALStates.HALTING);
        svCalc.add(sv);
      }


      //   the level-2's fire SETHALT on themselves at the end of initAction without any state calculations
      //   but they will calculate this if reset/halt/recoverAction is called
      //     in that case, the state should be calculated from either the supervisor or the LPM
      {
        StateVector sv = new StateVector();
        sv.setResultState(HCALStates.HALTED);
        sv.registerConditionState(containerFMChildren,HCALStates.HALTED);
        sv.registerConditionState(containerFUResourceBroker,HCALStates.HALTED);
        sv.registerConditionState(containerFUEventProcessor,HCALStates.HALTED);
        sv.registerConditionState(containerStorageManager,HCALStates.HALTED);
        sv.registerConditionState(containerlpmController,HCALStates.HALTED);
        if (asynchcalSupervisor) {
          sv.registerConditionState(containerhcalSupervisor,HCALStates.UNINITIALIZED);
        }
        svCalc.add(sv);
      }

      {
        StateVector sv = new StateVector();
        sv.setResultState(HCALStates.CONFIGURING);
        sv.registerConditionState(containerFMChildren,HCALStates.CONFIGURING);
        sv.registerConditionState(containerlpmController,HCALStates.CONFIGURING);
        if (asynchcalSupervisor) {
          sv.registerConditionState(containerhcalSupervisor,Arrays.asList(HCALStates.PREINIT,HCALStates.COLDINIT,HCALStates.INIT));
        }
        svCalc.add(sv);
      }


      {
        StateVector sv = new StateVector();
        sv.setResultState(HCALStates.CONFIGURED);
        sv.registerConditionState(containerFMChildren,HCALStates.CONFIGURED);
        sv.registerConditionState(containerFUResourceBroker,HCALStates.READY);
        sv.registerConditionState(containerFUEventProcessor,HCALStates.READY);
        sv.registerConditionState(containerStorageManager,HCALStates.READY);
        sv.registerConditionState(containerlpmController,HCALStates.CONFIGURED);
        if (asynchcalSupervisor) {
          sv.registerConditionState(containerhcalSupervisor,HCALStates.READY);
        }
        svCalc.add(sv);
      }

      if (!containerFMChildren.isEmpty())
      {
        StateVector sv = new StateVector();
        sv.setResultState(HCALStates.RESUMING);
        sv.registerConditionState(containerFMChildren,HCALStates.RESUMING);
        svCalc.add(sv);
      }

      {
        StateVector sv = new StateVector();
        sv.setResultState(HCALStates.RUNNING);
        sv.registerConditionState(containerFMChildren,HCALStates.RUNNING);
        sv.registerConditionState(containerFUResourceBroker,HCALStates.ENABLED);
        sv.registerConditionState(containerFUEventProcessor,HCALStates.ENABLED);
        sv.registerConditionState(containerStorageManager,HCALStates.ENABLED);
        sv.registerConditionState(containerlpmController,HCALStates.ENABLED);
        if (asynchcalSupervisor) {
          sv.registerConditionState(containerhcalSupervisor,HCALStates.ACTIVE);
        }
        svCalc.add(sv);
      }

      {
        StateVector sv = new StateVector();
        sv.setResultState(HCALStates.RUNNINGDEGRADED);
        sv.registerConditionState(containerFMChildren,HCALStates.RUNNINGDEGRADED);
        sv.registerConditionState(containerFUResourceBroker,HCALStates.ENABLED);
        sv.registerConditionState(containerFUEventProcessor,HCALStates.ENABLED);
        sv.registerConditionState(containerStorageManager,HCALStates.ENABLED);
        sv.registerConditionState(containerlpmController,HCALStates.ENABLED);
        if (asynchcalSupervisor) {
          sv.registerConditionState(containerhcalSupervisor,HCALStates.ACTIVE);
        }
        svCalc.add(sv);
      }

      if (!containerFMChildren.isEmpty())
      {
        StateVector sv = new StateVector();
        sv.setResultState(HCALStates.STOPPING);
        sv.registerConditionState(containerFMChildrenNoEvmTrigNoTCDSLPM,HCALStates.RUNNING);
        // need this gone for 904 to work
        // why was it here for P5 anyway?
        //sv.registerConditionState(containerFMTCDSLPM,HCALStates.CONFIGURED);
        sv.registerConditionState(containerFMEvmTrig,HCALStates.STOPPING);
        svCalc.add(sv);
      }

//FIXME combine the two stopping SVs
      //{
      //  StateVector sv = new StateVector();
      //  sv.setResultState(HCALStates.STOPPING);
      //  sv.registerConditionState(containerFMChildrenNoEvmTrig,HCALStates.RUNNING);
      //  sv.registerConditionState(containerFMEvmTrig,HCALStates.STOPPING);
      //  svCalc.add(sv);
      //}

      if (!containerFMChildren.isEmpty())
      {
        StateVector sv = new StateVector();
        sv.setResultState(HCALStates.PAUSED);
        sv.registerConditionState(containerFMChildren,HCALStates.PAUSED);
        svCalc.add(sv);
      }

      {
        StateVector sv = new StateVector();
        sv.setResultState(HCALStates.ERROR);
        sv.registerConditionState(containerFMChildren,HCALStates.ERROR);
        sv.registerConditionState(containerFUResourceBroker,HCALStates.ERROR);
        sv.registerConditionState(containerFUEventProcessor,HCALStates.ERROR);
        sv.registerConditionState(containerStorageManager,HCALStates.ERROR);
        sv.registerConditionState(containerlpmController,HCALStates.ERROR);
        if (asynchcalSupervisor) {
          sv.registerConditionState(containerhcalSupervisor,HCALStates.FAILED);
        }
        svCalc.add(sv);
      }

      if (!containerFMChildren.isEmpty())
      {
        StateVector sv = new StateVector();
        sv.setResultState(HCALStates.PREPARING_TTSTEST_MODE);
        sv.registerConditionState(containerFMChildren,HCALStates.PREPARING_TTSTEST_MODE);
        svCalc.add(sv);
      }

      if (!containerFMChildren.isEmpty())
      {
        StateVector sv = new StateVector();
        sv.setResultState(HCALStates.TTSTEST_MODE);
        sv.registerConditionState(containerFMChildren,HCALStates.TTSTEST_MODE);
        svCalc.add(sv);
      }

      if (!containerFMChildren.isEmpty())
      {
        StateVector sv = new StateVector();
        sv.setResultState(HCALStates.TESTING_TTS);
        sv.registerConditionState(containerFMChildren,HCALStates.TESTING_TTS);
        svCalc.add(sv);
      }

      logger.debug("[HCAL " + FMname + "] ... defining the ConditionState done.");

    }
    else {
      logger.debug("[HCAL " + FMname + "] Already previously the defining the ConditionState was done. Therefore doing nothing here ...");
    }
  }

  // get a session Id
  @SuppressWarnings("unchecked")
    protected void getSessionId() {
      String user = getQualifiedGroup().getGroup().getDirectory().getUser();
      String description = getQualifiedGroup().getGroup().getDirectory().getFullPath();
      int sessionId = 0;

      logger.debug("[HCAL "+FMname+"] Log session connector: " + logSessionConnector );

      if (logSessionConnector != null) {
        try {
          sessionId = logSessionConnector.createSession( user, description );
          logger.debug("[HCAL "+FMname+"] New session Id obtained =" + sessionId );
          logger.info("[HCAL "+FMname+"] New session obtained :" + logSessionConnector.getSession(sessionId).toString() );
        }
        catch (LogSessionException e1) {
          logger.warn("[HCAL "+FMname+"] Could not get session ID, using default = " + sessionId + ". Exception: ",e1);
        }
      }
      else {
        logger.warn("[HCAL "+FMname+"] logSessionConnector = " + logSessionConnector + ", using default = " + sessionId + ".");      
      }

      // put the session ID into parameter set
      getParameterSet().get("SID").setValue(new IntegerT(sessionId));
    }

  // close session Id. This routine is called always when functionmanager gets destroyed.
  protected void closeSessionId() {
    if (logSessionConnector != null) {
      int sessionId = 0;
      try {
        sessionId = ((IntegerT)getParameterSet().get("SID").getValue()).getInteger();
      }
      catch (Exception e) {
        logger.error("[HCAL " + FMname + "] Could not get sessionId for closing session. Not closing session. Exception:"+e.getMessage());
      }
      try {
        logger.debug("[HCAL " + FMname + "] Trying to close log sessionId = " + sessionId );
        logSessionConnector.closeSession(sessionId);
        logger.debug("[HCAL " + FMname + "] ... closed log sessionId = " + sessionId );
      }
      catch (LogSessionException e1) {
        logger.error("[HCAL " + FMname + "] Could not close sessionId, but sessionId was requested and used. Exception: "+e1.getMessage());
      }
    }
  }

  protected void checkHCALPartitionFEDListConsistency() {
    if (FMrole.equals("Level2_HBHEa")) {
      if (!HBHEain) {
        logger.warn("[HCAL " + FMname + "] The FED_ENABLE_MASK does not provide any valid FedIds for this HBHEa FM with role: " + FMrole);  
      }    
    }
    else if (FMrole.equals("Level2_HBHEb")) {
      if (!HBHEbin) {   
        logger.warn("[HCAL " + FMname + "] The FED_ENABLE_MASK does not provide any valid FedIds for this HBHEb FM with role: " + FMrole);      
      }
    }
    else if (FMrole.equals("Level2_HBHEc")) {
      if (!HBHEcin) {   
        logger.warn("[HCAL " + FMname + "] The FED_ENABLE_MASK does not provide any valid FedIds for this HBHEc FM with role: " + FMrole);      
      }
    }
    else if (FMrole.equals("Level2_HF")) {
      if (!HFin) {
        logger.warn("[HCAL " + FMname + "] The FED_ENABLE_MASK does not provide any valid FedIds for this HF FM with role: " + FMrole);      
      }
    }
    else if (FMrole.equals("Level2_HO") ) {
      if (!HOin) {
        logger.warn("[HCAL " + FMname + "] The FED_ENABLE_MASK does not provide any valid FedIds for this HO FM with role: " + FMrole);      
      }
    }
    else {
      logger.info("[HCAL " + FMname + "] Cannot check for this FM the consistency.\nThis FM has the role: " + FMrole + "\nThis is OK for FMs which control only e.g. fanouts.");      
    }
  }

  @SuppressWarnings("unchecked")
    protected void sendCMSError(String errMessage){

      // create a new error notification msg
      CMSError error = getErrorFactory().getCMSError();
      error.setDateTime(new Date().toString());
      error.setMessage(errMessage);

      // update error msg parameter for GUI
      getParameterSet().get("ERROR_MSG").setValue(new StringT(errMessage));

      // send error
      try {
        getParentErrorNotifier().sendError(error);
      }
      catch (Exception e) {
        logger.warn("[HCAL " + FMname + "] " + getClass().toString() + ": Failed to send error message " + errMessage);
      }
    }

    // access the HCALParameters object
    public HCALParameters getHCALparameterSet() {
      return this.hcalParameters;
    }


  /**----------------------------------------------------------------------
   * set the current Action
   */
  public void setAction(String action) {

    getParameterSet().put(new FunctionManagerParameter<StringT>
        ("ACTION_MSG",new StringT(action)));
    return;
  }

  /**----------------------------------------------------------------------
  */
  String findApplicationName( String id ) throws Exception {

    List<XdaqApplication> apps = containerXdaqApplication.getApplications();
    Iterator appIterator = apps.iterator();
    while( appIterator.hasNext( ) ) {
      XdaqApplication app = (XdaqApplication)appIterator.next();
      if ( app.getURI().toString().equals( id ) )
        return app.getApplication();
    }

    return "";
  }

  /**----------------------------------------------------------------------
   * go to the error state, setting messages and so forth, with exception
   */
  public void goToError(String errMessage, Exception e) {
    errMessage += " Message from the caught exception is: "+e.getMessage();
    goToError(errMessage);
  }

  /**----------------------------------------------------------------------
   * go to the error state, setting messages and so forth, without exception
   */
  public void goToError(String errMessage) {
    logger.error(errMessage);
    sendCMSError(errMessage);
    getParameterSet().put(new FunctionManagerParameter<StringT>("STATE",new StringT("Error")));
    getParameterSet().put(new FunctionManagerParameter<StringT>("ACTION_MSG",new StringT(errMessage)));
    getParameterSet().put(new FunctionManagerParameter<StringT>("SUPERVISOR_ERROR",new StringT(errMessage)));
    Input errInput = new Input(HCALInputs.SETERROR);
    errInput.setReason(errMessage);
    if (theEventHandler.TestMode.equals("off")) { firePriorityEvent(errInput); ErrorState = true; }
  }

  // FIXME: TCDS apps are virtual in HCAL configurations for RCMS_464.
  // We hard code the TCDS URLs here for now. Plan to move these URLs to supervisor infospace later.
  // When we do, we should fill containers with these tcdsXDAQ apps so that each LV2 FM can halt their own TCDS apps.  
  // Those containersshould not be used for sending RCMS state-transition commands.
  public void haltTCDSControllersWithURLs() {
      HashMap<String , String > TCDS_Laser904 = new HashMap<String, String>();
      HashMap<String , String > TCDS_HBHE904  = new HashMap<String, String>();
      HashMap<String , String > TCDS_HF904    = new HashMap<String, String>();
      HashMap<String , String > TCDS_LPM904   = new HashMap<String, String>();
      HashMap<String , String > TCDS_HBHEa  = new HashMap<String, String>();
      HashMap<String , String > TCDS_HBHEb  = new HashMap<String, String>();
      HashMap<String , String > TCDS_HBHEc  = new HashMap<String, String>();
      HashMap<String , String > TCDS_HO     = new HashMap<String, String>();
      HashMap<String , String > TCDS_HF     = new HashMap<String, String>();
      HashMap<String , String > TCDS_Laser  = new HashMap<String, String>();
      HashMap<String , String > TCDS_LPM    = new HashMap<String, String>();
      HashMap<String , String > loopMap      = null; 
      TCDS_Laser904.put("tcds::ici::ICIController_0","http://tcds-control-904.cms904:2101/urn:xdaq-application:lid=301");
      TCDS_Laser904.put("tcds::pi::PIController_0"  ,"http://tcds-control-904.cms904:2101/urn:xdaq-application:lid=501");
      TCDS_HF904.put("tcds::ici::ICIController_1"   ,"http://tcds-control-904.cms904:2101/urn:xdaq-application:lid=302");
      TCDS_HF904.put("tcds::pi::PIController_1"     ,"http://tcds-control-904.cms904:2101/urn:xdaq-application:lid=502");
      TCDS_HBHE904.put("tcds::ici::ICIController_2" ,"http://tcds-control-904.cms904:2101/urn:xdaq-application:lid=303");
      TCDS_HBHE904.put("tcds::pi::PIController_2"   ,"http://tcds-control-904.cms904:2101/urn:xdaq-application:lid=503");
      TCDS_LPM904.put("tcds::lpm::LPMController_0"  ,"http://tcds-control-904.cms904:2101/urn:xdaq-application:lid=201");

      TCDS_HBHEa.put("tcds::ici::ICIController_0","http://tcds-control-hcal-pri.cms:2108/urn:xdaq-application:lid=301");
      TCDS_HBHEa.put("tcds::pi::PIController_0"  ,"http://tcds-control-hcal-pri.cms:2108/urn:xdaq-application:lid=501");
      TCDS_HBHEb.put("tcds::ici::ICIController_1","http://tcds-control-hcal-pri.cms:2108/urn:xdaq-application:lid=302");
      TCDS_HBHEb.put("tcds::pi::PIController_1"  ,"http://tcds-control-hcal-pri.cms:2108/urn:xdaq-application:lid=502");
      TCDS_HBHEc.put("tcds::ici::ICIController_2","http://tcds-control-hcal-pri.cms:2108/urn:xdaq-application:lid=303");
      TCDS_HBHEc.put("tcds::pi::PIController_2"  ,"http://tcds-control-hcal-pri.cms:2108/urn:xdaq-application:lid=503");
      TCDS_HO.put("tcds::ici::ICIController_3"   ,"http://tcds-control-hcal-pri.cms:2108/urn:xdaq-application:lid=304");
      TCDS_HO.put("tcds::pi::PIController_3"     ,"http://tcds-control-hcal-pri.cms:2108/urn:xdaq-application:lid=504");
      TCDS_HF.put("tcds::ici::ICIController_4"   ,"http://tcds-control-hcal-pri.cms:2108/urn:xdaq-application:lid=305");
      TCDS_HF.put("tcds::pi::PIController_4"     ,"http://tcds-control-hcal-pri.cms:2108/urn:xdaq-application:lid=505");
      TCDS_Laser.put("tcds::ici::ICIController_5","http://tcds-control-hcal-pri.cms:2108/urn:xdaq-application:lid=306");
      TCDS_Laser.put("tcds::pi::PIController_5"  ,"http://tcds-control-hcal-pri.cms:2108/urn:xdaq-application:lid=506");
      TCDS_LPM.put("tcds::lpm::LPMController_0"  ,"http://tcds-control-hcal-pri.cms:2108/urn:xdaq-application:lid=201");

      String QRtype = "rcms.fm.resource.qualifiedresource.XdaqApplication";
      int sessionId = ((IntegerT)getParameterSet().get("SID").getValue()).getInteger();
      if (FMuri.contains(".cms904")){
        if (FMpartition.contains("Laser904")) {          loopMap  = TCDS_Laser904;        }
        if (FMpartition.contains("HBHE904"))  {          loopMap  = TCDS_HBHE904;         }
        if (FMpartition.contains("HF904"))    {          loopMap  = TCDS_HF904;           }
        if (FMrole.contains("Level2_TCDSLPM")){          loopMap  = TCDS_LPM904;          }
      }else{
        if (FMpartition.contains("HBHEa"))  {          loopMap  = TCDS_HBHEa;        }
        if (FMpartition.contains("HBHEb"))  {          loopMap  = TCDS_HBHEb;        }
        if (FMpartition.contains("HBHEc"))  {          loopMap  = TCDS_HBHEc;        }
        if (FMpartition.equals("HBHE"))  { 
            loopMap  = TCDS_HBHEa; 
            loopMap.putAll(TCDS_HBHEb);
            loopMap.putAll(TCDS_HBHEc);
        }
        if (FMpartition.contains("HO"))     {          loopMap  = TCDS_HO;           }
        if (FMpartition.contains("HF"))     {          loopMap  = TCDS_HF;           }
        if (FMpartition.contains("Laser"))  {          loopMap  = TCDS_Laser;        }
        if (FMrole.contains("Level2_TCDSLPM")){        loopMap  = TCDS_LPM;          }
      }
      if (loopMap!=null){
        for (Map.Entry<String, String> urlmap : loopMap.entrySet()){ 
          try{
            String tcdsname = urlmap.getKey();
            String tcdsURI  = urlmap.getValue();
            XdaqApplicationResource tcdsAppRsc = new XdaqApplicationResource(getGroup().getDirectory(), tcdsname, tcdsURI , QRtype, null, null);
            XdaqApplication  tcdsApp = new XdaqApplication(tcdsAppRsc);
            logger.info("[HCAL " + FMname + "] Sending halt to "+tcdsname+" at URI:" +tcdsURI);
            tcdsApp.execute(HCALInputs.HALT,Integer.toString(sessionId),null);
          }
          catch (CommandException e) {
            String errMessage = "[HCAL " + FMname + "] failed HALT of TCDS applications with reason: "+ e.getFaultString();
            logger.warn(errMessage);
          }
          catch (ResourceException e){
            String errMessage = "[HCAL " + FMname + "] failed HALT of TCDS applications with reason: "+ e.getMessage();
            logger.warn(errMessage);
          }
        }
      }
  }
  /**----------------------------------------------------------------------
   * halt the TCDS controllers 
   */
  public void haltTCDSControllers(boolean isServiceApp) throws UserActionException{
    try{
      int sessionId       = ((IntegerT)getParameterSet().get("SID").getValue()).getInteger();
      //logger.info("[HCAL LVL2 " + FMname + "] haltTCDSControllers: Halting with SID= "+sessionId+" and RCMSURL = "+rcmsStateListenerURL);

      if (!containerlpmController.isEmpty()) {
        if(isServiceApp){
          logger.info("[HCAL LVL2 " + FMname + "] haltTCDSControllers: Sending halt to LPM with SID="+sessionId);
          containerlpmController.execute(HCALInputs.HALT);
        }
        else{
          for(XdaqApplication lpmApp : containerlpmController.getApplications()){
            logger.info("[HCAL LVL2 " + FMname + "] haltTCDSControllers: Sending halt to LPM with SID="+sessionId+" and RCMSURL = "+rcmsStateListenerURL);
            lpmApp.execute(HCALInputs.HALT,Integer.toString(sessionId),rcmsStateListenerURL);
          }
        }
      }
      if (!containerPIController.isEmpty()) {
        if(isServiceApp){
          logger.info("[HCAL LVL2 " + FMname + "] haltTCDSControllers: Sending halt to PI with SID="+sessionId);
          containerPIController.execute(HCALInputs.HALT);
        }else{
          for(XdaqApplication PIapp : containerPIController.getApplications()){
            logger.info("[HCAL LVL2 " + FMname + "] haltTCDSControllers: Sending halt to PI with SID="+sessionId+" and RCMSURL = "+rcmsStateListenerURL);
            PIapp.execute(HCALInputs.HALT,Integer.toString(sessionId),rcmsStateListenerURL);
          }
        }
      }
      if (!containerICIController.isEmpty()) {
        if(isServiceApp){
          logger.info("[HCAL LVL2 " + FMname + "] haltTCDSControllers: Sending halt to ICI with SID="+sessionId);
          containerICIController.execute(HCALInputs.HALT);
        }else{
          for(XdaqApplication ICIapp : containerICIController.getApplications()){
            logger.info("[HCAL LVL2 " + FMname + "] haltTCDSControllers: Sending halt to ICI with SID="+sessionId+" and RCMSURL = "+rcmsStateListenerURL);
            ICIapp.execute(HCALInputs.HALT,Integer.toString(sessionId),rcmsStateListenerURL);
          }
        }
      }
    }
    catch (QualifiedResourceContainerException e) {
      String errMessage = " haltTCDSControllers: ";
      Map<QualifiedResource, CommandException> CommandExceptionMap = e.getCommandExceptionMap();
      for (QualifiedResource qr : CommandExceptionMap.keySet()){
      errMessage += " Failed to halt "+ qr.getName() + " with reason: " +  CommandExceptionMap.get(qr).getFaultString() +"\n";
      }
      throw new UserActionException(errMessage);
    }
    catch (CommandException e) {
      String errMessage = "[HCAL " + FMname + "] failed HALT of TCDS applications with reason: "+ e.getFaultString();
      throw new UserActionException(errMessage);
    }
  }

  /**----------------------------------------------------------------------
   * get all XDAQ executives and kill them
   */
  protected void destroyXDAQ() throws UserActionException {
    // see if there is an exec with a supervisor and kill it first
    URI supervExecURI = null;
    if (containerhcalSupervisor != null) {
      for (QualifiedResource qr : containerhcalSupervisor.getApplications()) {
        Resource supervResource = containerhcalSupervisor.getApplications().get(0).getResource();
        XdaqExecutiveResource qrSupervParentExec = ((XdaqApplicationResource)supervResource).getXdaqExecutiveResourceParent();
        supervExecURI = qrSupervParentExec.getURI();
        QualifiedResource qrExec = getQualifiedGroup().seekQualifiedResourceOfURI(supervExecURI);
        XdaqExecutive ex = (XdaqExecutive) qrExec;
        try{
            ex.destroy();
        }
        catch( Exception e){
          String errMessage="[HCAL "+FMname+"] Exception when destroying executive named:" + ex.getName()+ " with URI " + supervExecURI.toString(); 
          goToError(errMessage,e);
          throw (UserActionException) e;
        }
      }
    }

    // find all XDAQ executives and kill them
    if (getQualifiedGroup() != null) {
      List listExecutive = getQualifiedGroup().seekQualifiedResourcesOfType(new XdaqExecutive());
      Iterator it = listExecutive.iterator();
      while (it.hasNext()) {
        XdaqExecutive ex = (XdaqExecutive) it.next();
        if (!ex.getURI().equals(supervExecURI)) {
          try{
            ex.destroy();
          }
          catch(Exception e){
            String errMessage="[HCAL "+FMname+"] Exception when destroying executive named:" + ex.getName()+ " with URI " + supervExecURI.toString(); 
            goToError(errMessage,e);
            throw (UserActionException)e;
          }
        }
      }
    }

    // reset the qualified group so that the next time an init is sent all resources will be initialized again
    QualifiedGroup qg = getQualifiedGroup();
    if (qg != null) { qg.reset(); }
  }

  /**----------------------------------------------------------------------
   * get a configuration property
   */
  public String getProperty( String name ) throws Exception {

    List<ConfigProperty> propertiesList = getGroup().getThisResource().getProperties();

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

}
