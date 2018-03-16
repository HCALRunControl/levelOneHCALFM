package rcms.fm.app.level1;

import java.util.Date;
import java.util.Iterator;
import java.util.List;

import net.hep.cms.xdaqctl.XDAQException;
import net.hep.cms.xdaqctl.XDAQTimeoutException;
import net.hep.cms.xdaqctl.XDAQMessageException;

import rcms.fm.fw.StateEnteredEvent;
import rcms.fm.fw.parameter.CommandParameter;
import rcms.fm.fw.parameter.FunctionManagerParameter;
import rcms.fm.fw.parameter.ParameterSet;
import rcms.fm.fw.parameter.type.IntegerT;
import rcms.fm.fw.parameter.type.DoubleT;
import rcms.fm.fw.parameter.type.StringT;
import rcms.fm.fw.parameter.type.BooleanT;
import rcms.fm.fw.parameter.type.VectorT;
import rcms.fm.fw.parameter.type.MapT;
import rcms.fm.fw.user.UserActionException;
import rcms.resourceservice.db.resource.Resource;
import rcms.resourceservice.db.resource.xdaq.XdaqApplicationResource;
import rcms.resourceservice.db.resource.xdaq.XdaqExecutiveResource;
import rcms.fm.resource.QualifiedGroup;
import rcms.fm.resource.QualifiedResource;
import rcms.fm.resource.QualifiedResourceContainerException;
import rcms.fm.resource.qualifiedresource.FunctionManager;
import rcms.fm.resource.qualifiedresource.XdaqApplication;
import rcms.fm.resource.qualifiedresource.XdaqExecutive;
import rcms.fm.resource.qualifiedresource.XdaqExecutiveConfiguration;
import rcms.util.logger.RCMSLogger;
import rcms.xdaqctl.XDAQParameter;

import rcms.utilities.fm.task.TaskSequence;
import rcms.utilities.fm.task.SimpleTask;

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
    xmlHandler = new HCALxmlHandler(this.functionManager);
    super.init();
      

    logger.debug("[HCAL LVL2] init() called: functionManager = " + functionManager );
  }

  public void initAction(Object obj) throws UserActionException {
    if (obj instanceof StateEnteredEvent) {
      System.out.println("[HCAL LVL2 " + functionManager.FMname + "] Executing initAction");
      logger.debug("[HCAL LVL2 " + functionManager.FMname + "] Executing initAction");

      // reset the non-async error state handling
      functionManager.ErrorState = false;

      // set actions
      functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("STATE",new StringT("calculating state")));
      functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("ACTION_MSG",new StringT("Initializing ...")));

      // get the parameters of the command
      ParameterSet<CommandParameter> parameterSet = getUserFunctionManager().getLastInput().getParameterSet();
      
      if (parameterSet.get("EVM_TRIG_FM") != null) {
        String evmTrigFM = ((StringT)parameterSet.get("EVM_TRIG_FM").getValue()).getString();
        functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("EVM_TRIG_FM",new StringT(evmTrigFM)));
      }
      if ( ((StringT)parameterSet.get("EVM_TRIG_FM").getValue()).getString().equals(functionManager.FMname) ) {
        functionManager.FMrole="EvmTrig";
      }

      // convert TCDS apps to service apps and reset QG to modified one
      //QualifiedGroup qg = ConvertTCDSAppsToServiceApps(functionManager.getQualifiedGroup());
      // use normal QG for now
      QualifiedGroup qg = functionManager.getQualifiedGroup();
      // reset QG to modified one
      functionManager.setQualifiedGroup(qg);

      // check run type passed from Level-1
      // get SID from from LV1
      if(((StringT)parameterSet.get("HCAL_RUN_TYPE").getValue()).getString().equals("local")) {

        RunType = "local";

        // Get SID from LV1:
        if (parameterSet.get("SID") != null) {
          Sid = ((IntegerT)parameterSet.get("SID").getValue()).getInteger();
          functionManager.getParameterSet().put(new FunctionManagerParameter<IntegerT>("SID",new IntegerT(Sid)));
          functionManager.getParameterSet().put(new FunctionManagerParameter<IntegerT>("INITIALIZED_WITH_SID",new IntegerT(Sid)));
          logger.info("[Martin log HCAL LVL2 " + functionManager.FMname + "] Received the following SID from LV1 :"+ Sid) ;
        }
        else {
          String warnMessage = "[HCAL LVL2 " + functionManager.FMname + "] Did not receive a SID from LV1...";
          logger.warn(warnMessage);
        }


        GlobalConfKey = "not used";

        // set the run type in the function manager parameters
        functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("HCAL_RUN_TYPE",new StringT(RunType)));
        functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("GLOBAL_CONF_KEY",new StringT(GlobalConfKey)));
      }
      else {

        RunType = "global";

        // get the Sid from the init command
        if (parameterSet.get("SID") != null) {
          Sid = ((IntegerT)parameterSet.get("SID").getValue()).getInteger();
          functionManager.getHCALparameterSet().put(new FunctionManagerParameter<IntegerT>("SID",new IntegerT(Sid)));
          functionManager.getHCALparameterSet().put(new FunctionManagerParameter<IntegerT>("INITIALIZED_WITH_SID",new IntegerT(Sid)));
        }
        else {
          String warnMessage = "[HCAL LVL2 " + functionManager.FMname + "] Did not receive a SID from LV1...";
          logger.warn(warnMessage);
        }

        // get the GlobalConfKey from the init command
        if (parameterSet.get("GLOBAL_CONF_KEY") != null) {
          GlobalConfKey = ((StringT)parameterSet.get("GLOBAL_CONF_KEY").getValue()).getString();
          functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("GLOBAL_CONF_KEY",new StringT(GlobalConfKey)));
          functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("INITIALIZED_WITH_GLOBAL_CONF_KEY",new StringT(GlobalConfKey)));
        }
        else {
          String warnMessage = "[HCAL LVL2 " + functionManager.FMname + "] Did not receive a GlobalConfKey ...";
          logger.warn(warnMessage);
        }
      }

      //Set SID of QG for service App
      qg = functionManager.getQualifiedGroup();
      if( qg.getRegistryEntry("SID") ==null){
        Integer sid = ((IntegerT)functionManager.getHCALparameterSet().get("SID").getValue()).getInteger();
        qg.putRegistryEntry("SID", Integer.toString(sid));
        logger.info("[HCAL "+ functionManager.FMname+"] Just set the SID of QG to "+ sid);
      }
      else{
        logger.info("[HCAL "+ functionManager.FMname+"] SID of QG is "+ qg.getRegistryEntry("SID"));
      }

      List<QualifiedResource> xdaqApplicationList = qg.seekQualifiedResourcesOfType(new XdaqApplication());
      List<QualifiedResource> xdaqExecutiveList   = qg.seekQualifiedResourcesOfType(new XdaqExecutive());
      boolean doMasking = parameterSet.get("MASKED_RESOURCES") != null && ((VectorT<StringT>)parameterSet.get("MASKED_RESOURCES").getValue()).size()!=0;
      if (doMasking) {
        VectorT<StringT> MaskedResources = (VectorT<StringT>)parameterSet.get("MASKED_RESOURCES").getValue();
        functionManager.getHCALparameterSet().put(new FunctionManagerParameter<VectorT<StringT>>("MASKED_RESOURCES",MaskedResources));
        StringT[] MaskedResourceArray = MaskedResources.toArray(new StringT[MaskedResources.size()]);
        for (StringT MaskedApplication : MaskedResourceArray) {
          //String MaskedAppWcolonsNoCommas = MaskedApplication.replace("," , ":");
          //logger.info("[JohnLog2] " + functionManager.FMname + ": " + functionManager.FMname + ": Starting to mask application " + MaskedApplication);
          //logger.info("[JohnLogVector] " + functionManager.FMname + ": Starting to mask application " + MaskedApplication.getString());
          for (QualifiedResource qr : xdaqApplicationList) {
            //logger.info("[JohnLogVector] " + functionManager.FMname + ": For masking application " + MaskedApplication.getString() + "checking for match with " + qr.getName());
            if (qr.getName().equals(MaskedApplication.getString())) {
              //logger.info("[HCAL LVL2 " + functionManager.FMname + "]: found the matching application in the qr list, calling setActive(false): " + qr.getName());
              logger.info("[HCAL LVL2 " + functionManager.FMname + "]: Going to call setActive(false) on "+qr.getName());
              qr.setActive(false);
            }
          }
          for (QualifiedResource qr : xdaqExecutiveList) {
            if (qr.getName().equals(MaskedApplication.getString())) {
              logger.info("[HCAL LVL2 " + functionManager.FMname + "]: Going to call setActive(false) on "+qr.getName());
              qr.setActive(false);
            }
          }
          //logger.info("[JohnLogVector] " + functionManager.FMname + ": Done masking application " + MaskedApplication.getString());
        }
      }
      //else {
      //  String warnMessage = "[HCAL LVL2 " + functionManager.FMname + "] Did not receive any applications requested to be masked.";
      //  logger.warn(warnMessage);
      //}
      logger.info("[HCAL LVL2 " + functionManager.FMname + "]: This FM has role: " + functionManager.FMrole);
      List<QualifiedResource> xdaqExecList = qg.seekQualifiedResourcesOfType(new XdaqExecutive());
      // loop over the executives and strip the connections
     
      logger.info("[HCAL LVL2 " + functionManager.FMname + "]: about to set the xml for the xdaq executives.");
      //Boolean addedContext = false;
      for( QualifiedResource qr : xdaqExecList) {
        XdaqExecutive exec = (XdaqExecutive)qr;
        //logger.info("[JohnLog3] " + functionManager.FMname + " Found qualified resource: " + qr.getName());
        //logger.info("[HCAL LVL2 " + functionManager.FMname + "]: Found qualified resource: " + qr.getName());
        XdaqExecutiveConfiguration config =  exec.getXdaqExecutiveConfiguration();
        String oldExecXML = config.getXml();
        try {
          String intermediateXML = "";
          if (doMasking)
            intermediateXML = xmlHandler.stripExecXML(oldExecXML, getUserFunctionManager().getParameterSet());
          else
            intermediateXML = oldExecXML;
          //String newExecXML = intermediateXML;
          //TODO
          //if (functionManager.FMrole.equals("EvmTrig") && !addedContext) {
          String newExecXML = xmlHandler.addStateListenerContext(intermediateXML, functionManager.rcmsStateListenerURL);
          //  addedContext = true;
            System.out.println("Set the statelistener context.");
          //}
          newExecXML = xmlHandler.setUTCPConnectOnRequest(newExecXML);
          System.out.println("Set the utcp connectOnRequest attribute.");
          config.setXml(newExecXML);
        }
        catch (UserActionException e) {
          String errMessage = "[HCAL LVL2 " + functionManager.FMname + "]: got an error while trying to modify the ExecXML: ";
          functionManager.goToError(errMessage,e);
        }
        XdaqExecutiveConfiguration configRetrieved =  exec.getXdaqExecutiveConfiguration();
        System.out.println("[HCAL LVL2 System] " +qr.getName() + " has final executive xml: " +  configRetrieved.getXml());
      }

      String ruInstance = "";
      if (parameterSet.get("RU_INSTANCE") != null) {
        ruInstance = ((StringT)parameterSet.get("RU_INSTANCE").getValue()).getString();
        functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("RU_INSTANCE",new StringT(ruInstance)));
      }
      String lpmSupervisor = "";
      if (parameterSet.get("LPM_SUPERVISOR") != null) {
        lpmSupervisor = ((StringT)parameterSet.get("LPM_SUPERVISOR").getValue()).getString();
        functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("LPM_SUPERVISOR",new StringT(lpmSupervisor)));
      }

      // initialize all XDAQ executives
      try{
        initXDAQ();
      }catch(UserActionException e){
        String errMessage ="[HCAL LV2 "+functionManager.FMname+"] initXDAQ(): ";
        functionManager.goToError(errMessage,e);
        return;
      }
      //Set instance numbers and HandleLPM in the infospace
      initXDAQinfospace();

      // start the monitor thread
      System.out.println("[HCAL LVL2 " + functionManager.FMname + "] Starting Monitor thread ...");
      logger.debug("[HCAL LVL2 " + functionManager.FMname + "] Starting Monitor thread ...");
      LevelOneMonitorThread thread1 = new LevelOneMonitorThread();
      thread1.start();

      // start the HCALSupervisor watchdog thread
      System.out.println("[HCAL LVL2 " + functionManager.FMname + "] Starting HCAL supervisor watchdog thread ...");
      logger.debug("[HCAL LVL2 " + functionManager.FMname + "] Starting HCAL supervisor watchdog thread ...");
      if (!functionManager.containerhcalSupervisor.isEmpty()) {
        HCALSupervisorWatchThread thread2 = new HCALSupervisorWatchThread();
        thread2.start();
      } 

      // start the TriggerAdapter watchdog thread. Note: containerTriggerAdapter is filled after this. Start watchThread with role check.
      if (functionManager.FMrole.equals("EvmTrig")){
        System.out.println("[HCAL LVL2 " + functionManager.FMname + "] Starting TriggerAdapter watchdog thread ...");
        logger.debug("[HCAL LVL2 " + functionManager.FMname + "] StartingTriggerAdapter watchdog thread ...");
        TriggerAdapterWatchThread thread3 = new TriggerAdapterWatchThread();
        thread3.start();
        functionManager.parameterSender.start();
      }


      //Receive selected runkey name, mastersnippet file name, runkey map from LV1 
      CheckAndSetParameter( parameterSet, "MASTERSNIPPET_SELECTED");
      CheckAndSetParameter( parameterSet, "LOCAL_RUNKEY_SELECTED");
      //TODO: Extend checkAndSetParameter for MapT<?>
      if( parameterSet.get("LOCAL_RUNKEY_MAP") != null){
        MapT<MapT<StringT>> localRunkeyMap = ((MapT<MapT<StringT>>)parameterSet.get("LOCAL_RUNKEY_MAP").getValue());
        logger.info("[HCAL LVL2 " + functionManager.FMname + "] Received local runkey map: "+localRunkeyMap.toString());
        functionManager.getParameterSet().put(new FunctionManagerParameter<MapT<MapT<StringT>>>("LOCAL_RUNKEY_MAP", localRunkeyMap));
      }
      else{
        logger.error("[HCAL LVL2 " + functionManager.FMname + "] initAction: Did not receive LOCAL_RUNKEY_MAP during initAction");
      }
      // give the RunType to the controlling FM
      functionManager.RunType = RunType;
      logger.info("[HCAL LVL2 " + functionManager.FMname + "] initAction: We are in " + RunType + " mode ...");
        
      //sendMaskedApplications();
      // ask the HCAL supervisor for the TriggerAdapter name
      //
      
      if (functionManager.FMrole.equals("EvmTrig")) {
        logger.info("[HCAL LVL2 " + functionManager.FMname + "] Going to ask the HCAL supervisor fo the TriggerAdapter name.");
        getTriggerAdapter();
      }

      // go to HALT
      if (!functionManager.ErrorState) {
        logger.info("[SethLog HCAL LVL2 " + functionManager.FMname + "] Fire the SETHALT since we're done initializing");
        functionManager.fireEvent( HCALInputs.SETHALT );
      }
      // set actions
      functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("STATE",new StringT(functionManager.getState().getStateString())));
      functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("ACTION_MSG",new StringT("initAction executed ...")));

      // publish the initialization time for this FM to the paramterSet
      functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("HCAL_TIME_OF_FM_START", new StringT(functionManager.utcFMtimeofstart)));

      logger.info("[HCAL LVL2 " + functionManager.FMname + "] initAction executed ...");
    }
  }

  public void resetAction(Object obj) throws UserActionException {
    if (obj instanceof StateEnteredEvent) {
      System.out.println("[HCAL LVL2 " + functionManager.FMname + "] Executing resetAction");
      logger.debug("[HCAL LVL2 " + functionManager.FMname + "] Executing resetAction");

      publishRunInfoSummary();
      publishRunInfoSummaryfromXDAQ(); 
      functionManager.HCALRunInfo = null; // make RunInfo ready for the next round of run info to store


      // reset the non-async error state handling
      functionManager.ErrorState = false;
      functionManager.FMWasInPausedState = false;
      // set actions
      functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("STATE",new StringT("calculating state")));
      functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("ACTION_MSG",new StringT("Resetting")));
      functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("SUPERVISOR_ERROR",new StringT("not set")));
      functionManager.getHCALparameterSet().put(new FunctionManagerParameter<DoubleT>("PROGRESS",new DoubleT(0.0)));


      // KKH: TODO xDAQ should implement resetAction which should bring the apps to "halted"(Uninitialized) state
      // When that is done, we should use xdaq::reset() for resetting the xdaq apps by asking all xdaqs (via supervisor) to do reset 
      // In resetAction, we can keep the more invasive destroy/init+haltLPM cycle.

      // kill all XDAQ executives
      functionManager.destroyXDAQ();

      // init all XDAQ executives
      try{
        initXDAQ();
      }catch(UserActionException e){
        String errMessage ="[HCAL LV2 "+functionManager.FMname+"] initXDAQ():";
        functionManager.goToError(errMessage,e);
        return;
      }
      
      //Set instance numbers and HandleLPM in the infospace
      initXDAQinfospace();


      //Reset all EmptyFMs as we are going to halted
      VectorT<StringT> EmptyFMs = new VectorT<StringT>();
      functionManager.getHCALparameterSet().put(new FunctionManagerParameter<VectorT<StringT>>("EMPTY_FMS",EmptyFMs));

      if (functionManager.FMrole.equals("EvmTrig")) {
        logger.info("[HCAL LVL2 " + functionManager.FMname + "] Going to ask the HCAL supervisor fo the TriggerAdapter name.");
        getTriggerAdapter();
      }

      // go to Halted 
      if (!functionManager.ErrorState) {
        functionManager.fireEvent( HCALInputs.SETHALT );
      }

      // set actions
      functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("STATE",new StringT(functionManager.getState().getStateString())));
      functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("ACTION_MSG",new StringT("resetAction executed ...")));

      logger.info("[HCAL LVL2 " + functionManager.FMname + "] resetAction executed ...");
    }
  }

  public void recoverAction(Object obj) throws UserActionException {
    Boolean UseResetForRecover = ((BooleanT)functionManager.getHCALparameterSet().get("USE_RESET_FOR_RECOVER").getValue()).getBoolean();
    if (UseResetForRecover) {
      resetAction(obj); return;
    }
    if (obj instanceof StateEnteredEvent) {
      System.out.println("[HCAL LVL2 " + functionManager.FMname + "] Executing recoverAction");
      logger.info("[HCAL LVL2 " + functionManager.FMname + "] Executing recoverAction");

      // reset the non-async error state handling
      functionManager.ErrorState = false;

      // set actions
      functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("STATE",new StringT("calculating state")));
      functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("ACTION_MSG",new StringT("recovering")));

      if (!functionManager.containerhcalSupervisor.isEmpty()) {
        {
          String debugMessage = "[HCAL LVL2 " + functionManager.FMname + "] HCAL supervisor for recovering found- good!";
          logger.debug(debugMessage);
        }

        try {
          functionManager.containerhcalSupervisor.execute(HCALInputs.HCALASYNCRESET);
        }
        catch (QualifiedResourceContainerException e) {
          String errMessage = "[HCAL LVL2 " + functionManager.FMname + "] Error! QualifiedResourceContainerException: recovering failed ...";
          functionManager.goToError(errMessage,e);
        }

      }
      else if (!functionManager.FMrole.equals("Level2_TCDSLPM")) {
        String errMessage = "[HCAL LVL2 " + functionManager.FMname + "] Error! No HCAL supervisor found: recoverAction()";
        functionManager.goToError(errMessage);
      }

      // set actions
      functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("STATE",new StringT(functionManager.getState().getStateString())));
      functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("ACTION_MSG",new StringT("recoverAction executed ...")));

      logger.info("[HCAL LVL2 " + functionManager.FMname + "] recoverAction executed ...");
    }
  }

  public void configureAction(Object obj) throws UserActionException {
    if (obj instanceof StateEnteredEvent) {
      System.out.println("[HCAL LVL2 " + functionManager.FMname + "] Executing configureAction");
      logger.info("[HCAL LVL2 " + functionManager.FMname + "] Executing configureAction");

      // reset the non-async error state handling
      functionManager.ErrorState = false;

      // set actions
      functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("STATE",new StringT("calculating state")));
      functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("ACTION_MSG",new StringT("configuring")));

      if (!functionManager.containerTTCciControl.isEmpty()) {
        TTCciWatchThread ttcciwatchthread = new TTCciWatchThread(functionManager);
        ttcciwatchthread.start();
      }

      if (functionManager.containerhcalSupervisor.isEmpty()) {
       // TODO: handle this case appropriately for FMs with no hcalSupervisor
       functionManager.getHCALparameterSet().put(new FunctionManagerParameter<DoubleT>("PROGRESS",new DoubleT(1.0)));
      }
     
      String CfgCVSBasePath           = "not set";

      // Set the default value everytime we configure
      boolean isSinglePartition   = false; 
      String ICIControlSequence   = "not set";
      String PIControlSequence    = "not set";


      // get the parameters of the command
      ParameterSet<CommandParameter> parameterSet = getUserFunctionManager().getLastInput().getParameterSet();

      if (parameterSet.size()==0)  {
        // LV2 should receive parameters from LV1 for both local and global run.
        logger.warn("[HCAL LVL2 " + functionManager.FMname + "] Did not receive any parameters during ConfigureAction! Check if LV1 sends any.");
      }
      else {
        try{
          // Determine the run type from the configure command
          CheckAndSetParameter(       parameterSet, "HCAL_RUN_TYPE" );
          CheckAndSetParameter(       parameterSet, "RUN_KEY");
          CheckAndSetTargetParameter( parameterSet, "RUN_KEY" ,"CONFIGURED_WITH_RUN_KEY",true);

          // Check and receive TPG key
          CheckAndSetTargetParameter( parameterSet, "TPG_KEY" ,"CONFIGURED_WITH_TPG_KEY",true);

          // get the info from the LVL1 if special actions due to a central CMS clock source change are indicated
          ClockChanged = false;
          CheckAndSetParameter(       parameterSet, "CLOCK_CHANGED" );
          ClockChanged = ((BooleanT)functionManager.getHCALparameterSet().get("CLOCK_CHANGED").getValue()).getBoolean();
          if (ClockChanged) {
            logger.warn("[HCAL LVL2 " + functionManager.FMname + "] Did receive a request to perform special actions due to central CMS clock source change during the configureAction().");
          }
          UseResetForRecover = true;
          CheckAndSetParameter( parameterSet, "USE_RESET_FOR_RECOVER");

          UsePrimaryTCDS = true;
          CheckAndSetParameter( parameterSet, "USE_PRIMARY_TCDS");

          // get the supervisor error from the lvl1 
          SupervisorError = "not set";
          CheckAndSetParameter( parameterSet, "SUPERVISOR_ERROR");

          // get the FED list from the configure command in global run
          CheckAndSetParameter(       parameterSet, "FED_ENABLE_MASK");
          CheckAndSetTargetParameter( parameterSet, "FED_ENABLE_MASK" ,"CONFIGURED_WITH_FED_ENABLE_MASK",true);
        }
        catch (UserActionException e){
          String warnMessage = "[HCAL LVL2 " + functionManager.FMname + "] ConfigureAction: "+e.getMessage();
          logger.error(warnMessage);
        }


        // get the HCAL CfgCVSBasePath from LVL1 if the LVL1 has sent something
        try{
          CheckAndSetParameter( parameterSet , "OFFICIAL_RUN_NUMBERS");
          CheckAndSetParameter( parameterSet , "HCAL_CFGCVSBASEPATH" );
          CheckAndSetParameter( parameterSet , "SINGLEPARTITION_MODE");
          isSinglePartition   = ((BooleanT)functionManager.getHCALparameterSet().get("SINGLEPARTITION_MODE").getValue()).getBoolean();
        }
        catch (UserActionException e){
          String warnMessage = "[HCAL LVL2 " + functionManager.FMname + "] ConfigureAction: "+e.getMessage();
          logger.warn(warnMessage);
        }
      }

      // Parse the mastersnippet 
      String mastersnippet       = ((StringT)functionManager.getHCALparameterSet().get("MASTERSNIPPET_SELECTED").getValue()).getString();
      CfgCVSBasePath           = ((StringT)functionManager.getHCALparameterSet().get("HCAL_CFGCVSBASEPATH").getValue()).getString();
      // Reset HCAL_CFGSCRIPT:
      functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("HCAL_CFGSCRIPT",new StringT("not set")));
      // Parse MasterSnippet
      try{
        //Parse common+main mastersnippet to pick up all-partition settings
        xmlHandler.parseMasterSnippet(mastersnippet,CfgCVSBasePath,"");
        //Parse common+main mastersnippet for partition specific settings
        xmlHandler.parseMasterSnippet(mastersnippet,CfgCVSBasePath,functionManager.FMpartition);
      }
      catch(UserActionException e){
        String errMessage = "[HCAL LVL2"+functionManager.FMname+"]: Failed to parse mastersnippets:";
        functionManager.goToError(errMessage,e);
        return;
      }

      //Append CfgScript from runkey (if any)
      StringT runkeyName                 = (StringT) functionManager.getHCALparameterSet().get("LOCAL_RUNKEY_SELECTED").getValue();
      MapT<MapT<StringT>> LocalRunKeyMap = (MapT<MapT<StringT>>)functionManager.getHCALparameterSet().get("LOCAL_RUNKEY_MAP").getValue();
      if (LocalRunKeyMap.get(runkeyName).get(new StringT("CfgToAppend"))!=null){
        StringT MasterSnippetCfgScript = ((StringT)functionManager.getHCALparameterSet().get("HCAL_CFGSCRIPT").getValue());
        StringT RunkeyCfgScript        = LocalRunKeyMap.get(runkeyName).get(new StringT("CfgToAppend"));
        
        logger.info("[HCAL LVL2 "+ functionManager.FMname +"] Adding Runkey CfgScript from this runkey: "+ runkeyName.getString()+" and it looks like this "+RunkeyCfgScript);
        functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("HCAL_CFGSCRIPT",MasterSnippetCfgScript.concat(RunkeyCfgScript)));
      }

      //Put Mastersnippet results into local variables to be sent to supervisor
      FullCfgScript               = ((StringT)functionManager.getHCALparameterSet().get("HCAL_CFGSCRIPT"     ).getValue()).getString();
      FedEnableMask               = ((StringT)functionManager.getHCALparameterSet().get("FED_ENABLE_MASK"    ).getValue()).getString();
      String TTCciControlSequence = ((StringT)functionManager.getHCALparameterSet().get("HCAL_TTCCICONTROL"  ).getValue()).getString();
      String LTCControlSequence   = ((StringT)functionManager.getHCALparameterSet().get("HCAL_LTCCONTROL"    ).getValue()).getString();
      String LPMControlSequence   = ((StringT)functionManager.getHCALparameterSet().get("HCAL_LPMCONTROL"    ).getValue()).getString();
      if(isSinglePartition){
        ICIControlSequence   = ((StringT)functionManager.getHCALparameterSet().get("HCAL_ICICONTROL_SINGLE" ).getValue()).getString();
        PIControlSequence    = ((StringT)functionManager.getHCALparameterSet().get("HCAL_PICONTROL_SINGLE"   ).getValue()).getString();
      }
      else{
        ICIControlSequence   = ((StringT)functionManager.getHCALparameterSet().get("HCAL_ICICONTROL_MULTI" ).getValue()).getString();
        PIControlSequence    = ((StringT)functionManager.getHCALparameterSet().get("HCAL_PICONTROL_MULTI"   ).getValue()).getString();
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
          functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("STATE",new StringT("Error")));
          functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("ACTION_MSG",new StringT("oops - technical difficulties ...")));
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

        // Update the parameterset if we have modification from TpgKey
        functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>("HCAL_CFGSCRIPT", new StringT(FullCfgScript)));

      }
      else {
        logger.warn("[HCAL LVL2 " + functionManager.FMname + "] Warning! Did not receive any TPG_KEY.\nPerhaps this is OK for local runs ... ");

        if (!RunType.equals("local")) {
          logger.error("[HCAL LVL2 " + functionManager.FMname + "] Error! For global runs we should have received a TPG_KEY.\nPlease check if HCAL is in the trigger.\n If HCAL is in the trigger and you see this message please call an expert - this is bad!!");
        }
      }

      // Instead of setting infospace, destoryXDAQ if this FM is mentioned in EmptyFM
      if (parameterSet.get("EMPTY_FMS")!=null ) {
        VectorT<StringT> EmptyFMs  = (VectorT<StringT>)parameterSet.get("EMPTY_FMS").getValue();
        functionManager.getHCALparameterSet().put(new FunctionManagerParameter<VectorT<StringT>>("EMPTY_FMS",EmptyFMs));
        if (!EmptyFMs.contains(new StringT(functionManager.FMname))){
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
              functionManager.goToError(errMessage,e);
            }
          }

          if (functionManager.containerTriggerAdapter!=null) {
            if (!functionManager.containerTriggerAdapter.isEmpty()) {
              //TODO do here
              Resource taResource = functionManager.containerTriggerAdapter.getApplications().get(0).getResource();
              logger.info("[JohnLog]: " + functionManager.FMname + " about to get the TA's parent executive.");
              XdaqExecutiveResource qrTAparentExec = ((XdaqApplicationResource)taResource).getXdaqExecutiveResourceParent() ;
              logger.info("[JohnLog]: " + functionManager.FMname + " about to get the TA's siblings group.");
              List<XdaqApplicationResource> taSiblingsList = qrTAparentExec.getApplications();
              logger.info("[JohnLog]: " + functionManager.FMname + " about to loop over the TA's siblings group.");
              if (taResource.getName().contains("DummyTriggerAdapter")) { 
                for (XdaqApplicationResource taSibling : taSiblingsList) {
                  logger.info("[JohnLog]: " + functionManager.FMname + " has a trigger adapter with a sibling named: " + taSibling.getName());
                  if (taSibling.getName().contains("DTCReadout")) { 
                    try {
                      XDAQParameter pam = null;
                      XdaqApplication taSiblingApp = new XdaqApplication(taSibling);
                      pam =taSiblingApp.getXDAQParameter();

                      pam.select(new String[] {"PollingReadout"});
                      pam.setValue("PollingReadout", "true");
                      pam.send();
                    }
                    catch (XDAQTimeoutException e) {
                      String errMessage = "[HCAL " + functionManager.FMname + "] Error! XDAQTimeoutException: configAction() when trying to send IsLocalRun and TriggerKey to the HCAL supervisor\n Perhaps this application is dead!?";
                      functionManager.goToError(errMessage,e);
                    }
                    catch (XDAQException e) {
                      String errMessage = "[HCAL " + functionManager.FMname + "] Error! XDAQException: onfigAction() when trying to send IsLocalRun and TriggerKey to the HCAL supervisor";
                      functionManager.goToError(errMessage,e);
                    }
                  }
                }
              }
            }
          }
          for (QualifiedResource qr : functionManager.containerhcalSupervisor.getApplications() ){
            try {
              XDAQParameter pam = null;
              pam =((XdaqApplication)qr).getXDAQParameter();

              pam.select(new String[] {"IsLocalRun", "TriggerKey", "ReportStateToRCMS"});
              pam.setValue("IsLocalRun", String.valueOf(RunType.equals("local")));
              logger.info("[HCAL " + functionManager.FMname + "] Set IsLocalRun to: " + String.valueOf(RunType.equals("local")));
              pam.setValue("TriggerKey", TpgKey);
              pam.setValue("ReportStateToRCMS", "true");
              logger.info("[HCAL " + functionManager.FMname + "] Set ReportStateToRCMS to: true.");

              pam.send();
            }
            catch (XDAQTimeoutException e) {
              String errMessage = "[HCAL " + functionManager.FMname + "] Error! XDAQTimeoutException: configAction() when trying to send IsLocalRun and TriggerKey to the HCAL supervisor\n Perhaps this application is dead!?";
              functionManager.goToError(errMessage,e);
            }
            catch (XDAQException e) {
              String errMessage = "[HCAL " + functionManager.FMname + "] Error! XDAQException: onfigAction() when trying to send IsLocalRun and TriggerKey to the HCAL supervisor";
              functionManager.goToError(errMessage,e);
            }
          }

          // configuring all created HCAL applications by means of sending the RunType to the HCAL supervisor
          if (!functionManager.ErrorState) {
            sendRunTypeConfiguration(FullCfgScript,TTCciControlSequence,LTCControlSequence,ICIControlSequence,LPMControlSequence,PIControlSequence, FedEnableMask, UsePrimaryTCDS);
          }
        }
        else{
          //Destroy XDAQ() for this FM
          logger.info("[HCAL LV2 "+ functionManager.FMname +"] Going to destroyXDAQ for this FM as it is masked from FED list");
          stopHCALSupervisorWatchThread = true;
          functionManager.destroyXDAQ();
          functionManager.fireEvent( HCALInputs.SETCONFIGURE );
        }
      }
      else{
        logger.info("[HCAL LV2 "+ functionManager.FMname +"] Did not receive EMPTY_FMS from LV1.");
      }
      // set actions
      functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("STATE",new StringT(functionManager.getState().getStateString())));
      functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("ACTION_MSG",new StringT("configureAction executed ... - we're close ...")));

      logger.info("[HCAL LVL2 " + functionManager.FMname + "] configureAction executed.");
    }
  }

  public void startAction(Object obj) throws UserActionException {
    if (obj instanceof StateEnteredEvent) {
      System.out.println("[HCAL LVL2 " + functionManager.FMname + "] Executing startAction");
      logger.info("[HCAL LVL2 " + functionManager.FMname + "] Executing startAction");

      // reset the non-async error state handling
      functionManager.ErrorState = false;

      // set actions
      functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("STATE",new StringT("calculating state")));
      functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("ACTION_MSG",new StringT("Starting ...")));

      // get the parameters of the command
      ParameterSet<CommandParameter> parameterSet = getUserFunctionManager().getLastInput().getParameterSet();

      // check parameter set
      if (parameterSet.size()==0) {

        String errMessage = "[HCAL LVL2 " + functionManager.FMname +"] Did not receive parameters from LV1 in StartAction!";
	functionManager.goToError(errMessage);
      }
      else {

        // get the run number from the start command

        try{
          CheckAndSetParameter(parameterSet, "RUN_NUMBER");
          functionManager.RunNumber = ((IntegerT)parameterSet.get("RUN_NUMBER").getValue()).getInteger();
          functionManager.getHCALparameterSet().put(new FunctionManagerParameter<IntegerT>("STARTED_WITH_RUN_NUMBER",new IntegerT(functionManager.RunNumber)));
        } 
        catch (UserActionException e){
          String errMessage = "[HCAL LVL2 " + functionManager.FMname + "] Error! Did not receive a run number ...";
					functionManager.goToError(errMessage,e);
        }

        try{
          CheckAndSetParameter(parameterSet, "RUN_SEQ_NUMBER");
          CheckAndSetParameter(parameterSet, "NUMBER_OF_EVENTS");
          TriggersToTake = ((IntegerT)parameterSet.get("NUMBER_OF_EVENTS").getValue()).getInteger();
        } 
        catch (UserActionException e){
          if (RunType.equals("local")){ 
            logger.error("[HCAL LVL2 " + functionManager.FMname + "] Warning! Did not receive a run sequence number or Number of Events to take!"); 
          }
          else{
            logger.warn("[HCAL LVL2 " + functionManager.FMname + "] Warning! Did not receive a run sequence number or Number of Events to take. This is OK for global runs."); 
          }
        }
        //// get the run sequence number from the start command
        //if (parameterSet.get("RUN_SEQ_NUMBER") != null) {
        //  RunSeqNumber = ((IntegerT)parameterSet.get("RUN_SEQ_NUMBER").getValue()).getInteger();
        //  functionManager.getHCALparameterSet().put(new FunctionManagerParameter<IntegerT>("RUN_SEQ_NUMBER",new IntegerT(RunSeqNumber)));
        //}
        //else {
        //  if (RunType.equals("local")) { logger.warn("[HCAL LVL2 " + functionManager.FMname + "] Warning! Did not receive a run sequence number.\nThis is OK for global runs."); }
        //}
        
      }
      VectorT<StringT> EmptyFMs  = (VectorT<StringT>)functionManager.getHCALparameterSet().get("EMPTY_FMS").getValue();
      if (EmptyFMs.contains(new StringT(functionManager.FMname))){
        //Start EmptyFM
        logger.info("[HCAL LV2 "+ functionManager.FMname +"] This FM is empty. Starting EmptyFM");
        functionManager.fireEvent( HCALInputs.SETSTART ); 
        // set action
        functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("STATE",new StringT(functionManager.getState().getStateString())));
        functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("ACTION_MSG",new StringT("startAction executed ...")));
        return;
      }

      publishRunInfoSummary();
      publishRunInfoSummaryfromXDAQ();
      
      if (functionManager.containerTriggerAdapter!=null) {
        if (!functionManager.containerTriggerAdapter.isEmpty()) {
        //  //TODO do here
        //  // determine run number and run sequence number and overwrite what was set before
        //  try {
        //    Resource qrTAparentExec = functionManager.containerTriggerAdapter.getApplications().get(0).getResource();
        //    Group taSiblingsGroup = functionManager.getQualifiedGroup().rs.retrieveLightGroup(qrTAparentExec);
        //    List<Resource> taSiblingsList = taSiblingsGroup.getChildrenResources();
        //    for (Resource taSibling : taSiblingsList) {
        //      logger.info("[JohnLog]: " + functionManager.FMname + " has a trigger adapter with a sibling named: " + taSibling.getName());
        //    }
        //  }
        //  catch (DBConnectorException ex) {
        //    logger.error("[JohnLog]: " + functionManager.FMname + " Got a DBConnectorException when trying to retrieve TA sibling resources: " + ex.getMessage());
        //  }
            
         // KKH For standalone LV2 runs. Deprecated.
         // OfficialRunNumbers = ((BooleanT)functionManager.getHCALparameterSet().get("OFFICIAL_RUN_NUMBERS").getValue()).getBoolean();
         // if (OfficialRunNumbers) {
         //   RunNumberData rnd = getOfficialRunNumber();

         //   functionManager.RunNumber    = rnd.getRunNumber();
         //   RunSeqNumber = rnd.getSequenceNumber();

         //   functionManager.getHCALparameterSet().put(new FunctionManagerParameter<IntegerT>("RUN_NUMBER", new IntegerT(functionManager.RunNumber)));
         //   functionManager.getHCALparameterSet().put(new FunctionManagerParameter<IntegerT>("RUN_SEQ_NUMBER", new IntegerT(RunSeqNumber)));
         // }
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
							functionManager.goToError(errMessage,e);
            }
            catch (XDAQException e) {
              String errMessage = "[HCAL " + functionManager.FMname + "] Error! XDAQException: startAction() when trying to send the functionManager.RunNumber to the HCAL supervisor";
							functionManager.goToError(errMessage,e);
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
							functionManager.goToError(errMessage,e);
            }
          }
        }

        try {

          // define start time
          StartTime = new Date();

          functionManager.containerhcalSupervisor.execute(HCALInputs.HCALASYNCSTART);
          logger.info("[HCAL LVL2 " + functionManager.FMname + "] Starting, sending ASYNCSTART to supervisor");
        }
        catch (QualifiedResourceContainerException e) {
          String errMessage = "[HCAL LVL2 " + functionManager.FMname + "] Error! QualifiedResourceContainerException: starting (HCAL=Enable) failed ...";
					functionManager.goToError(errMessage,e);
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
									functionManager.goToError(errMessage,e);
                }
                catch (XDAQException e) {
                  String errMessage = "[HCAL LVL2 " + functionManager.FMname + "] Error! XDAQException: startAction()";
									functionManager.goToError(errMessage,e);
                }
              }
            }
          }
          else {
            if (functionManager.FMrole.equals("EvmTrig")) {
              String errMessage = "[HCAL LVL2 " + functionManager.FMname + "] Error! No TriggerAdapter found: startAction()";
						  functionManager.goToError(errMessage);
            }
          }
        }
      }
      else if (!functionManager.FMrole.equals("Level2_TCDSLPM")) {
        String errMessage = "[HCAL LVL2 " + functionManager.FMname + "] Error! No HCAL supervisor found: startAction()";
				functionManager.goToError(errMessage);
      }

      if (functionManager.FMrole.contains("TTCci")) {
        functionManager.fireEvent( HCALInputs.SETSTART ); //TODO revisit this, a proper fix would get rid of this.
      } 

      // set action
      functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("STATE",new StringT(functionManager.getState().getStateString())));
      functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("ACTION_MSG",new StringT("startAction executed ...")));

      functionManager.RunWasStarted = true; // switch to enable writing to runInfo when run was destroyed

      logger.debug("startAction executed ...");
    }
  }

  public void runningAction(Object obj) throws UserActionException {
    if (obj instanceof StateEnteredEvent) {
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
              functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("STATE",new StringT("Error")));
              functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("ACTION_MSG",new StringT("oops - technical difficulties ...")));
              if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; return;}
            }
          }
        }

        // set actions for local runs
        functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("STATE",new StringT(functionManager.getState().getStateString())));
        functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("ACTION_MSG",new StringT("waiting for run to finish ...")));

        logger.debug("[HCAL LVL2 " + functionManager.FMname + "] runningAction executed ...");

      }
      else {
        // set actions for gloabl runs
        functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("STATE",new StringT(functionManager.getState().getStateString())));
        functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("ACTION_MSG",new StringT("running like hell ...")));
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
      //    functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("STATE",new StringT("Error")));
      //    functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("ACTION_MSG",new StringT("oops - technical difficulties ...")));
      //    if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; return;}
      //  }
      //}
      // patch for pause-resume behavior
      functionManager.FMWasInPausedState = false;
    }
  }

  public void pauseAction(Object obj) throws UserActionException {
    if (obj instanceof StateEnteredEvent) {
      System.out.println("[HCAL LVL2 " + functionManager.FMname + "] Executing pauseAction");
      logger.info("[HCAL LVL2 " + functionManager.FMname + "] Executing pauseAction");

      // reset the non-async error state handling
      functionManager.ErrorState = false;
      functionManager.FMWasInPausedState = true;
      // set action
      functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("STATE",new StringT("calculating state")));
      functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("ACTION_MSG",new StringT("pausing")));

      // FireEvent and return if this FM is empty
      VectorT<StringT> EmptyFMs  = (VectorT<StringT>)functionManager.getHCALparameterSet().get("EMPTY_FMS").getValue();
      if (EmptyFMs.contains(new StringT(functionManager.FMname))){
        //Stop EmptyFM
        logger.info("[HCAL LV2 "+ functionManager.FMname +"] This FM is empty. Pausing EmptyFM");
        functionManager.fireEvent( HCALInputs.SETPAUSE ); 
        // set actions
        functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("STATE",new StringT(functionManager.getState().getStateString())));
        functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("ACTION_MSG",new StringT("pasusingAction executed ...")));
        return;
      }

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
            String errMessage = "[HCAL LVL2 " + functionManager.FMname + "] Error! QualifiedResourceContainerException: pausing (Suspend to trigger adapter) failed ...";
            functionManager.goToError(errMessage,e);
          }

        }
        else {
          if (functionManager.FMrole.equals("EvmTrig")) {
            String errMessage = "[HCAL LVL2 " + functionManager.FMname + "] Error! No TriggerAdapter found: pauseAction()";
            functionManager.goToError(errMessage);
          }
        }
      }

      // leave intermediate state
      if (!functionManager.ErrorState) {
        functionManager.fireEvent( HCALInputs.SETPAUSE );
      }
      // set action
      functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("STATE",new StringT(functionManager.getState().getStateString())));
      functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("ACTION_MSG",new StringT("pausingAction executed ...")));

      logger.debug("[HCAL LVL2 " + functionManager.FMname + "] pausingAction executed ...");

    }
  }

  public void resumeAction(Object obj) throws UserActionException {
    if (obj instanceof StateEnteredEvent) {
      System.out.println("[HCAL LVL2 " + functionManager.FMname + "] Executing resumeAction");
      logger.info("[HCAL LVL2 " + functionManager.FMname + "] Executing resumeAction");

      // reset the non-async error state handling
      functionManager.ErrorState = false;

      // set action
      functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("STATE",new StringT("calculating state")));
      functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("ACTION_MSG",new StringT("resuming")));

      // FireEvent and return if this FM is empty
      VectorT<StringT> EmptyFMs  = (VectorT<StringT>)functionManager.getHCALparameterSet().get("EMPTY_FMS").getValue();
      if (EmptyFMs.contains(new StringT(functionManager.FMname))){
        // Resume EmptyFM
        logger.info("[HCAL LV2 "+ functionManager.FMname +"] This FM is empty. Resuming EmptyFM");
        functionManager.fireEvent( HCALInputs.SETRESUME ); 
        // set actions
        functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("STATE",new StringT(functionManager.getState().getStateString())));
        functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("ACTION_MSG",new StringT("resumeAction executed ...")));
        return;
      }
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
            String errMessage = "[HCAL LVL2 " + functionManager.FMname + "] Error! QualifiedResourceContainerException: resume to trigger adapter failed ...";
            functionManager.goToError(errMessage,e);
          }

        }
        else {
          if (functionManager.FMrole.equals("EvmTrig")) {
            String errMessage = "[HCAL LVL2 " + functionManager.FMname + "] Error! No TriggerAdapter found: resumeAction()";
            functionManager.goToError(errMessage);
          }
        }
      }

      // leave intermediate state
      if (!functionManager.ErrorState) {
        functionManager.fireEvent( HCALInputs.SETRESUME );
      }

      // set actions
      functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("STATE",new StringT(functionManager.getState().getStateString())));
      functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("ACTION_MSG",new StringT("resumeAction executed ...")));

      logger.debug("resumeAction executed ...");

    }
  }

  public void haltAction(Object obj) throws UserActionException {
    if (obj instanceof StateEnteredEvent) {
      System.out.println("[HCAL LVL2 " + functionManager.FMname + "] Executing haltAction");
      logger.info("[HCAL LVL2 " + functionManager.FMname + "] Executing haltAction");

      // reset the non-async error state handling
      functionManager.ErrorState = false;
      functionManager.FMWasInPausedState = false;
      // set action
      functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("STATE",new StringT("calculating state")));
      functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("ACTION_MSG",new StringT("halting")));

      VectorT<StringT> EmptyFMs  = (VectorT<StringT>)functionManager.getHCALparameterSet().get("EMPTY_FMS").getValue();
      if (EmptyFMs.contains(new StringT(functionManager.FMname))){
        // Bring back the destroyed XDAQ
        logger.info("[HCAL LV2 " + functionManager.FMname + "] Bringing back the XDAQs");
        try{
          initXDAQ();
        }catch(UserActionException e){
          String errMessage ="[HCAL LV2 "+functionManager.FMname+"] initXDAQ():";
          functionManager.goToError(errMessage,e);
          return;
        }
        initXDAQinfospace();
        if (stopHCALSupervisorWatchThread){
            logger.info("[HCAL LV2 " + functionManager.FMname + "] Restarting supervisor watchthread");
            HCALSupervisorWatchThread thread2 = new HCALSupervisorWatchThread();
            thread2.start();
            stopHCALSupervisorWatchThread = false;
        }
        else{
          logger.warn("[HCAL LV2 " + functionManager.FMname + "]WARNING: supervisorWatchthred is still running. Turn off the supervisorWatchthread before destroying XDAQs");
        }
        functionManager.fireEvent(HCALInputs.SETHALT);
        // Reset the EmptyFMs for all LV2s
        functionManager.getHCALparameterSet().put(new FunctionManagerParameter<VectorT<StringT>>("EMPTY_FMS",new VectorT<StringT>()));
        // set action
        functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("STATE",new StringT(functionManager.getState().getStateString())));
        functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("ACTION_MSG",new StringT("haltAction executed ...")));
        return;
      }

      // publish info of the actual run taken
      publishRunInfoSummary();
      publishRunInfoSummaryfromXDAQ();
      functionManager.HCALRunInfo = null; // make RunInfo ready for the next round of run info to store


      // Schedule the tasks for normal FMs 
      TaskSequence LV2haltTaskSeq = new TaskSequence(HCALStates.HALTING,HCALInputs.SETHALT);
      if ( functionManager.getState().equals(HCALStates.EXITING) )  {
        LV2haltTaskSeq = new TaskSequence(HCALStates.EXITING,HCALInputs.SETHALT);
      }
      // 1) Stop the TA
      if (functionManager.containerTriggerAdapter!=null) {
        if (!functionManager.containerTriggerAdapter.isEmpty()) {
          SimpleTask evmTrigTask = new SimpleTask(functionManager.containerTriggerAdapter,HCALInputs.HCALDISABLE,HCALStates.READY,HCALStates.READY,"LV2 HALT TA:stop");
          LV2haltTaskSeq.addLast(evmTrigTask);
        }
      }
      // 2) Stop the supervisor
      if (functionManager.containerhcalSupervisor!=null) {
        if (!functionManager.containerhcalSupervisor.isEmpty()) {
          //Bring supervisor from RunningToConfigured (stop)
          SimpleTask SupervisorStopTask = new SimpleTask(functionManager.containerhcalSupervisor,HCALInputs.HCALDISABLE,HCALStates.READY,HCALStates.READY,"LV2 HALT Supervisor step1/2:stop");
          //Bring supervisor from ConfiguredToHalted (reset)
          SimpleTask SupervisorResetTask = new SimpleTask(functionManager.containerhcalSupervisor,HCALInputs.HCALASYNCRESET,HCALStates.UNINITIALIZED,HCALStates.UNINITIALIZED,"LV2 HALT Supervisor step2/2:reset");
          LV2haltTaskSeq.addLast(SupervisorStopTask);
          LV2haltTaskSeq.addLast(SupervisorResetTask);
        }
      }
      else if (!functionManager.FMrole.equals("Level2_TCDSLPM")) {
        String errMessage = "[HCAL LVL2 " + functionManager.FMname + "] Error! No HCAL supervisor found: haltAction()";
        functionManager.goToError(errMessage);
      } 
      logger.info("[HCAL LVL2 " + functionManager.FMname + "] executing Halt TaskSequence.");
      functionManager.theStateNotificationHandler.executeTaskSequence(LV2haltTaskSeq);

      // Reset the EmptyFMs for all LV2s
      functionManager.getHCALparameterSet().put(new FunctionManagerParameter<VectorT<StringT>>("EMPTY_FMS",new VectorT<StringT>()));

      // stop the event building gracefully
      if (functionManager.FMrole.equals("EvmTrig")) {

        // include scheduling ToDo

        // stop the PeerTransportATCPs
        if (functionManager.StopATCP) {
          if (!functionManager.containerPeerTransportATCP.isEmpty()) {
            try {
              logger.debug("[HCAL LVL2 " + functionManager.FMname + "] stopping PeerTransportATCPs ...");
              functionManager.containerPeerTransportATCP.execute(HCALInputs.HALT);
            }
            catch (QualifiedResourceContainerException e) {
              String errMessage = "[HCAL LVL2 " + functionManager.FMname + "] Error! QualifiedResourceContainerException: stopping PeerTransportATCPs failed ...";
              functionManager.goToError(errMessage,e);
            }
          }
        }
      }
      // LPM is halt by LPMsupervisor during Supervisor RESET

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
                functionManager.goToError(errMessage,e);
              }
              catch (XDAQException e) {
                String errMessage = "[HCAL LVL2 " + functionManager.FMname + "] Error! XDAQException: preparingTTSTestModeAction";
                functionManager.goToError(errMessage,e);
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
                functionManager.goToError(errMessage,e);
              }
            }
          }
        }
        else {
          String errMessage = "[HCAL LVL2 " + functionManager.FMname + "] Error! No DCC (HCAL FED) found: haltAction()";
          functionManager.goToError(errMessage);
        }

      }

      // set action
      functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("STATE",new StringT(functionManager.getState().getStateString())));
      functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("ACTION_MSG",new StringT("haltAction executed ...")));

      logger.debug("[HCAL LVL2 " + functionManager.FMname + "] haltAction executed ...");
    }
  }

  public void exitAction(Object obj) throws UserActionException {

    if (obj instanceof StateEnteredEvent) {
      System.out.println("[HCAL LVL1 " + functionManager.FMname + "] Executing exitAction");
      logger.info("[HCAL LVL1 " + functionManager.FMname + "] Executing exitAction");

      haltAction(obj);
      logger.debug("[JohnLog " + functionManager.FMname + "] exitAction executed ...");
    }
  }

  public void coldResetAction(Object obj) throws UserActionException {
    if (obj instanceof StateEnteredEvent) {
      System.out.println("[HCAL LVL2 " + functionManager.FMname + "] Executing coldResetAction");
      logger.info("[HCAL LVL2 " + functionManager.FMname + "] Executing coldResetAction");

      // reset the non-async error state handling
      functionManager.ErrorState = false;
      functionManager.FMWasInPausedState = false;
      // set action
      functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("STATE",new StringT("calculating state")));
      functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("ACTION_MSG",new StringT("brrr - cold resetting ...")));

      //
      // perhaps nothing have to be done here for HCAL !?
      //

      publishRunInfoSummary();
      publishRunInfoSummaryfromXDAQ(); 
      functionManager.HCALRunInfo = null; // make RunInfo ready for the next round of run info to store

      if (!functionManager.ErrorState) {
        functionManager.fireEvent( HCALInputs.SETCOLDRESET );
      }


      // set action
      functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("STATE",new StringT(functionManager.getState().getStateString())));
      functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("ACTION_MSG",new StringT("coldResetAction executed ...")));

      logger.debug("[HCAL LVL2 " + functionManager.FMname + "] coldResetAction executed ...");
    }
  }

  public void stoppingAction(Object obj) throws UserActionException {
    if (obj instanceof StateEnteredEvent) {
      System.out.println("[HCAL LVL2 " + functionManager.FMname + "] Executing stoppingAction");
      logger.info("[HCAL LVL2 " + functionManager.FMname + "] Executing stoppingAction");

      // reset the non-async error state handling
      functionManager.ErrorState = false;
      functionManager.FMWasInPausedState = false;
      // set action
      functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("STATE",new StringT("calculating state")));
      functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("ACTION_MSG",new StringT("stopping")));

      // FireEvent and return if this FM is empty
      VectorT<StringT> EmptyFMs  = (VectorT<StringT>)functionManager.getHCALparameterSet().get("EMPTY_FMS").getValue();
      if (EmptyFMs.contains(new StringT(functionManager.FMname))){
        //Stop EmptyFM
        logger.info("[HCAL LV2 "+ functionManager.FMname +"] This FM is empty. Stopping EmptyFM");
        functionManager.fireEvent( HCALInputs.SETCONFIGURE ); 
        // set actions
        functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("STATE",new StringT(functionManager.getState().getStateString())));
        functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("ACTION_MSG",new StringT("stoppingAction executed ...")));

        return;
      }
      // stop the triggering
      if (functionManager.FMrole.equals("EvmTrig")) {
        if (functionManager.containerTriggerAdapter!=null) {
          if (!functionManager.containerTriggerAdapter.isEmpty()) {

            try {
              logger.info("[HCAL LVL2 " + functionManager.FMname + "] EvmTrig FM: send HCALDISABLE to TriggerAdapter");
              functionManager.containerTriggerAdapter.execute(HCALInputs.HCALDISABLE);
            }
            catch (QualifiedResourceContainerException e) {
              String errMessage = "[HCAL LVL2 " + functionManager.FMname + "] Error! QualifiedResourceContainerException: step 1/2 (TriggerAdapter Disable) failed ...";
              functionManager.goToError(errMessage,e);
            }

            // waits for the TriggerAdapter to be in the Ready or Failed state, the timeout is 10s
            logger.info("[HCAL LVL2 " + functionManager.FMname + "] EvmTrig FM: waitForTriggerAdapter to be in state \"Ready\" for up to 10s");
            waitforTriggerAdapter(10);

          }
          else {
            if (functionManager.FMrole.equals("EvmTrig")) {
              String errMessage = "[HCAL LVL2 " + functionManager.FMname + "] Error! No TriggerAdapter found: stoppingAction()";
              functionManager.goToError(errMessage);
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

        try {

          // define stop time
          StopTime = new Date();

          logger.info("[HCAL LVL2 " + functionManager.FMname + "]  Sending AsyncDisable to supervisor");
          functionManager.containerhcalSupervisor.execute(HCALInputs.HCALASYNCDISABLE);
        }
        catch (QualifiedResourceContainerException e) {
          String errMessage = "[HCAL LVL2 " + functionManager.FMname + "] Error! QualifiedResourceContainerException:  step 2/2 (AsyncDisable to hcalSupervisor) failed ...";
          functionManager.goToError(errMessage,e);
        }
      }
      else if (!functionManager.FMrole.equals("Level2_TCDSLPM")) {
        String errMessage = "[HCAL LVL2 " + functionManager.FMname + "] Error! No HCAL supervisor found: stoppingAction()";
        functionManager.goToError(errMessage);
      }

      // stop the event building gracefully
      if (functionManager.FMrole.equals("EvmTrig")) {

        // include scheduling ToDo

        // stop the PeerTransportATCPs
        if (functionManager.StopATCP) {
          if (!functionManager.containerPeerTransportATCP.isEmpty()) {
            try {
              logger.debug("[HCAL LVL2 " + functionManager.FMname + "] stopping PeerTransportATCPs ...");
              functionManager.containerPeerTransportATCP.execute(HCALInputs.HALT);
            }
            catch (QualifiedResourceContainerException e) {
              String errMessage = "[HCAL LVL2 " + functionManager.FMname + "] Error! QualifiedResourceContainerException: stopping PeerTransportATCPs failed ...";
              functionManager.goToError(errMessage,e);
            }
          }
        }
      }

      if (functionManager.FMrole.equals("Level2_TCDSLPM") || functionManager.FMrole.contains("TTCci")) {
        functionManager.fireEvent( HCALInputs.SETCONFIGURE ); //TODO revisit this, a proper fix would get rid of this.
      } 

      logger.info("[HCAL LVL2 " + functionManager.FMname +"] about to call publishRunInfoSummary");
      publishRunInfoSummary();
      publishRunInfoSummaryfromXDAQ(); 
      //functionManager.parameterSender.shutdown();
      functionManager.HCALRunInfo = null; // make RunInfo ready for the next round of run info to store


      // set actions
      functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("STATE",new StringT(functionManager.getState().getStateString())));
      functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("ACTION_MSG",new StringT("stoppingAction executed ...")));

      logger.debug("[HCAL LVL2 " + functionManager.FMname + "] stoppingAction executed ...");

    }
  }

  public void preparingTTSTestModeAction(Object obj) throws UserActionException {
    if (obj instanceof StateEnteredEvent) {
      System.out.println("[HCAL LVL2 " + functionManager.FMname + "] Executing preparingTestModeAction");
      logger.info("[HCAL LVL2 " + functionManager.FMname + "] Executing preparingTestModeAction");

      // reset the non-async error state handling
      functionManager.ErrorState = false;
      functionManager.FMWasInPausedState = false;
      // set actions
      functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("STATE",new StringT("calculating state")));
      functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("ACTION_MSG",new StringT("preparingTestMode")));

      // get the parameters of the command
      ParameterSet<CommandParameter> parameterSet = getUserFunctionManager().getLastInput().getParameterSet();

      // check parameter set, if it is not set see if we are in local mode
      if (parameterSet.size()!=0)  {

        try{
          CheckAndSetParameter( parameterSet , "HCAL_CFGSCRIPT"      );
          CheckAndSetParameter( parameterSet , "HCAL_TTCCICONTROL"   );
          CheckAndSetParameter( parameterSet , "HCAL_LTCCONTROL"     );
          CheckAndSetParameter( parameterSet , "HCAL_CFGCVSBASEPATH" );
        }
        catch (UserActionException e){
          String warnMessage = "[HCAL LVL2 " + functionManager.FMname + "] prepareTTStestModeAction: "+e.getMessage();
          logger.warn(warnMessage);
        }
        
        // set the function manager parameters
        functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("HCAL_RUN_TYPE",new StringT(RunType)));
      }

      CfgCVSBasePath           = ((StringT)functionManager.getHCALparameterSet().get("HCAL_CFGCVSBASEPATH").getValue()).getString();
      FullCfgScript            = ((StringT)functionManager.getHCALparameterSet().get("HCAL_CFGSCRIPT"     ).getValue()).getString();

      // configuring all created HCAL applications by means of sending the RunType to the HCAL supervisor
      // KKH: resurrect this if you want to fix prepareTTStestmode
      //sendRunTypeConfiguration(FullCfgScript,FullTTCciControlSequence,FullLTCControlSequence,FullTCDSControlSequence,FullLPMControlSequence,FullPIControlSequence,FedEnableMask,UsePrimaryTCDS);

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
              functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("STATE",new StringT("Error")));
              functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("ACTION_MSG",new StringT("oops - technical difficulties ...")));
              if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; return;}

            }
            catch (XDAQException e) {
              String errMessage = "[HCAL LVL2 " + functionManager.FMname + "] Error! XDAQException: preparingTTSTestModeAction";
              logger.error(errMessage,e);
              functionManager.sendCMSError(errMessage);
              functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("STATE",new StringT("Error")));
              functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("ACTION_MSG",new StringT("oops - technical difficulties ...")));
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
              functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("STATE",new StringT("Error")));
              functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("ACTION_MSG",new StringT("oops - technical difficulties ...")));
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
      functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("STATE",new StringT(functionManager.getState().getStateString())));
      functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("ACTION_MSG",new StringT("preparingTestModeAction executed ...")));

      logger.debug("[HCAL LVL2 " + functionManager.FMname + "] preparingTestModeAction executed ...");
    }
  }

  public void testingTTSAction(Object obj) throws UserActionException {
    if (obj instanceof StateEnteredEvent) {
      System.out.println("[HCAL LVL2 " + functionManager.FMname + "] Executing testingTTSAction");
      logger.info("[HCAL LVL2 " + functionManager.FMname + "] Executing testingTTSAction");

      // reset the non-async error state handling
      functionManager.ErrorState = false;
      functionManager.FMWasInPausedState = false;
      // set actions
      functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("STATE",new StringT("calculating state")));
      functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("ACTION_MSG",new StringT("testing TTS")));

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
        functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("STATE",new StringT("Error")));
        functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("ACTION_MSG",new StringT(errMessage)));
        if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; return;}

      }
      else {

        logger.debug("[HCAL LVL2 " + functionManager.FMname + "] Getting parameters for sTTS test now ...");

        FedId = ((IntegerT)parameterSet.get("TTS_TEST_FED_ID").getValue()).getInteger();
        mode = ((StringT)parameterSet.get("TTS_TEST_MODE").getValue()).getString();
        pattern = ((StringT)parameterSet.get("TTS_TEST_PATTERN").getValue()).getString();
        cycles = ((IntegerT)parameterSet.get("TTS_TEST_SEQUENCE_REPEAT").getValue()).getInteger();
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
              functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("STATE",new StringT("Error")));
              functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("ACTION_MSG",new StringT("oops - technical difficulties ...")));
              if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; return;}

            }
          }
          catch (XDAQMessageException e) {
            String errMessage = "[HCAL LVL2 " + functionManager.FMname + "] Error! XDAQMessageException: testingTTSAction()";
            logger.error(errMessage,e);
            functionManager.sendCMSError(errMessage);
            functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("STATE",new StringT("Error")));
            functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("ACTION_MSG",new StringT("oops - technical difficulties ...")));
            if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; return;}

          }
        }
      }
      else {
        String errMessage = "[HCAL LVL2 " + functionManager.FMname + "] Error! No DCC (HCAL FED) found: testingTTSAction()";
        logger.error(errMessage);
        functionManager.sendCMSError(errMessage);
        functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("STATE",new StringT("Error")));
        functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("ACTION_MSG",new StringT(errMessage)));
        if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; return;}

      }

      // leave intermediate state
      if (!functionManager.ErrorState) {
        functionManager.fireEvent( HCALInputs.SETTTSTEST_MODE );
      }

      // set actions
      functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("STATE",new StringT(functionManager.getState().getStateString())));
      functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("ACTION_MSG",new StringT("testingTTSAction executed ...")));

      logger.debug("[HCAL LVL2 " + functionManager.FMname + "] testingTTSAction executed ...");
    }
  }
  public class TTCciWatchThread extends Thread {
    protected HCALFunctionManager functionManager = null;
    RCMSLogger logger = null;

    public TTCciWatchThread(HCALFunctionManager parentFunctionManager) {
      this.logger = new RCMSLogger(HCALFunctionManager.class);
      logger.info("Constructing TTCciWatchThread");
      this.functionManager = parentFunctionManager;
      logger.info("Done construction TTCciWatchThread for " + functionManager.FMname + ".");
    }
    public void run() {
      while (!stopTTCciWatchThread && !functionManager.isDestroyed() && functionManager != null) {
          for (QualifiedResource ttcciControlResource : functionManager.containerTTCciControl.getApplications()) {
            XdaqApplication ttcciControl = (XdaqApplication) ttcciControlResource;
            logger.info("[HCAL " + functionManager.FMname + "]: " + ttcciControl.getName() + " has state: " + ttcciControl.refreshState().toString());
            //Poll the xdaq to issue transitions
            if (ttcciControl.refreshState().toString().equals("configured") ) {
              // Running To Stopping
              if(  (functionManager.getState().getStateString().equals(HCALStates.RUNNING.toString())) ||
                        (functionManager.getState().getStateString().equals(HCALStates.RUNNINGDEGRADED.toString()))  ){
                stopTTCciWatchThread = true;
                functionManager.firePriorityEvent(HCALInputs.STOP);
              }
              // Configuring To configured
              else if(functionManager.getState().getStateString().equals(HCALStates.CONFIGURING.toString())){
                functionManager.firePriorityEvent(HCALInputs.SETCONFIGURE);
              //}else if(functionManager.getState().getStateString().equals(HCALStates.CONFIGURED.toString())){
              }else 
              {
                //Sleep when we are in configured
                try {
                    Thread.sleep(15000);
                  }
                  catch (Exception e) {
                    logger.error("[" + functionManager.FMname + "] Error during TTCciWatchThread.");
                  }
                //logger.info("[" + functionManager.FMname + "] TTCciWatchThread: slept 4s in configured");
              }
            }
            else  if ( ttcciControl.refreshState().toString().equals("halted")) {
              //TODO: Running To Stopping
              if(  (functionManager.getState().getStateString().equals(HCALStates.RUNNING.toString())) ||
                        (functionManager.getState().getStateString().equals(HCALStates.RUNNINGDEGRADED.toString()))  ){
               // stopTTCciWatchThread = true;
               // functionManager.firePriorityEvent(HCALInputs.STOP);
                 logger.error("["+functionManager.FMname+"] TTCciWatchThread: Should not halt from Running!");
              }
              // Configured To Halted
              else if(functionManager.getState().getStateString().equals(HCALStates.CONFIGURED.toString())){
                functionManager.firePriorityEvent(HCALInputs.SETHALT);
              }
              // Halting to Halted
              else if(functionManager.getState().getStateString().equals(HCALStates.HALTING.toString())){
                functionManager.firePriorityEvent(HCALInputs.SETHALT);
              }
              else 
              {
                  //Sleep when we are in HALTED
                  try {
                      Thread.sleep(15000);
                    }
                    catch (Exception e) {
                      logger.error("[" + functionManager.FMname + "] Error during TTCciWatchThread.");
                    }
                 // logger.info("[" + functionManager.FMname + "] TTCciWatchThread: slept 4s in halted");
              }
            }  
            else
            {
              //Sleep when we are not in configured or halted
              try {
                  Thread.sleep(4000);
                }
                catch (Exception e) {
                  logger.error("[" + functionManager.FMname + "] Error during TTCciWatchThread.");
                }
              logger.info("[" + functionManager.FMname + "] TTCciWatchThread: waiting to reach configured");
            }
        } 
      }
      logger.info("[HCAL " + functionManager.FMname + "] ... stopping TTCci watchdog thread done.");
    }
  }
}

