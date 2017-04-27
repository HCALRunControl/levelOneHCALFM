package rcms.fm.app.level1;

import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Pattern;
import java.util.HashMap;
import java.lang.Math;

import java.io.StringReader; 
import java.io.IOException;

import rcms.fm.resource.qualifiedresource.XdaqExecutive;
import rcms.fm.resource.qualifiedresource.XdaqExecutiveConfiguration;
import rcms.fm.resource.qualifiedresource.XdaqApplication;
import rcms.fm.resource.qualifiedresource.XdaqApplicationContainer;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.w3c.dom.DOMException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import rcms.fm.fw.StateEnteredEvent;
import rcms.fm.fw.parameter.CommandParameter;
import rcms.fm.fw.parameter.FunctionManagerParameter;
import rcms.fm.fw.parameter.ParameterSet;
import rcms.fm.fw.service.parameter.ParameterServiceException;
import rcms.fm.fw.parameter.type.IntegerT;
import rcms.fm.fw.parameter.type.DoubleT;
import rcms.fm.fw.parameter.type.StringT;
import rcms.fm.fw.parameter.type.BooleanT;
import rcms.fm.fw.parameter.type.VectorT;
import rcms.fm.fw.parameter.type.MapT;
import rcms.fm.fw.user.UserActionException;
import rcms.fm.fw.user.UserStateNotificationHandler;
import rcms.common.db.DBConnectorException;
import rcms.resourceservice.db.Group;
import rcms.resourceservice.db.resource.Resource;
import rcms.fm.resource.QualifiedGroup;
import rcms.fm.resource.QualifiedResource;
import rcms.fm.resource.QualifiedResourceContainer;
import rcms.fm.resource.QualifiedResourceContainerException;
import rcms.resourceservice.db.resource.fm.FunctionManagerResource;
import rcms.resourceservice.db.resource.config.ConfigProperty;
import rcms.fm.resource.qualifiedresource.FunctionManager;
import rcms.fm.resource.qualifiedresource.XdaqApplication;
import rcms.stateFormat.StateNotification;
import rcms.util.logger.RCMSLogger;
import rcms.utilities.fm.task.SimpleTask;
import rcms.utilities.fm.task.TaskSequence;
import rcms.utilities.runinfo.RunNumberData;
import rcms.statemachine.definition.Input;
import rcms.fm.resource.CommandException;
import rcms.xdaqctl.XDAQParameter;
import net.hep.cms.xdaqctl.XDAQException;

/**
 * Event Handler class for HCAL Function Managers
 *
 * @maintaner John Hakala
 *
 */

public class HCALlevelOneEventHandler extends HCALEventHandler {

  static RCMSLogger logger = new RCMSLogger(HCALlevelOneEventHandler.class);
  public HCALxmlHandler xmlHandler = null;
  public HCALMasker masker = null;
  private AlarmerWatchThread alarmerthread = null;

  private Double  progress           = 0.0;
  private Integer nChildren          = 0;
  private Boolean stopProgressThread = false;

  public HCALlevelOneEventHandler() throws rcms.fm.fw.EventHandlerException {
    addAction(HCALStates.RUNNINGDEGRADED,                 "runningAction");
  }

  public void init() throws rcms.fm.fw.EventHandlerException {

    functionManager = (HCALFunctionManager) getUserFunctionManager();
    xmlHandler = new HCALxmlHandler(this.functionManager);
    masker = new HCALMasker(this.functionManager);

    super.init();  // this method calls the base class init and has to be called _after_ the getting of the functionManager

    // Get the CfgCVSBasePath in the userXML
    {
      String DefaultCfgCVSBasePath = "/nfshome0/hcalcfg/cvs/RevHistory/";
      //String DefaultCfgCVSBasePath = "/data/cfgcvs/cvs/RevHistory/";
      String theCfgCVSBasePath = "";
      try {
        NodeList NodesOfTag = xmlHandler.getHCALuserXML().getElementsByTagName("CfgCVSBasePath");
        if(xmlHandler.hasUniqueTag(NodesOfTag,"CfgCVSBasePath")){
          theCfgCVSBasePath=xmlHandler.getHCALuserXML().getElementsByTagName("CfgCVSBasePath").item(0).getTextContent(); 
        }
      }
      catch (UserActionException e) { logger.warn(e.getMessage()); }
      if (!theCfgCVSBasePath.equals("")) {
        CfgCVSBasePath = theCfgCVSBasePath;
      } else{
        CfgCVSBasePath = DefaultCfgCVSBasePath;
      }
      logger.info("[HCAL " + functionManager.FMname + "] The CfgCVSBasePath for this FM is " + CfgCVSBasePath);
      functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("HCAL_CFGCVSBASEPATH",new StringT(CfgCVSBasePath)));
    }

    // Get the MasterSnippetList in the userXML
    {
      String theMasterSnippetList = "";
      try {
        theMasterSnippetList=xmlHandler.getHCALuserXMLelementContent("MasterSnippetList",false);
      }
      catch (UserActionException e) {
        logger.error(e.getMessage()); 
        //functionManager.goToError("[HCAL LV1] Cannot find  \"MasterSnippetList\" tag in LV1 FM's userXML. Example: %lt MasterSnippetList %gt Filename %lt /MasterSnippetList %gt");
        functionManager.goToError("[HCAL LV1] Cannot find  \"MasterSnippetList\" tag in LV1 FM's userXML. Example: <MasterSnippetList>Filename</MasterSnippetList>");
      }
      if (!theMasterSnippetList.equals("")) {
        logger.info("[HCAL " + functionManager.FMname + "] The MasterSnippetList for this FM is " + theMasterSnippetList);
      }
      functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("HCAL_MASTERSNIPPETLIST",new StringT(theMasterSnippetList)));
    }
    // Get the RunSequenceName from the userXML
    {
      String NewRunSequenceName = "";
      try {
        NewRunSequenceName = xmlHandler.getHCALuserXMLelementContent("RunSequenceName",true);
      }
      catch (UserActionException e) { 
        logger.warn(e.getMessage());
      }
      if (!NewRunSequenceName.equals("")) {
        RunSequenceName = NewRunSequenceName;
        logger.info("[HCAL base] using RunSequenceName: " + RunSequenceName);
      }
      else {
        logger.debug("[HCAL base] using RunSequenceName: " + RunSequenceName);
      }
      functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("SEQ_NAME", new StringT(""+RunSequenceName)));
    }

    // Check if TestMode has been specified in the userXML
    {
      String useTestMode = "";
      try {
        useTestMode = xmlHandler.getHCALuserXMLelementContent("TestMode",true);
      }
      catch (UserActionException e) { 
        logger.warn(e.getMessage());
      }
      if (!useTestMode.equals("")) {
        TestMode = useTestMode;
        logger.warn("[HCAL base] TestMode: " + TestMode + " enabled - ignoring anything which would set the state machine to an error state!");
      }
    }

    // Check if we want the "Recover" button to actually perform a "Reset"
    {
      String useResetForRecover = ""; 
      try { useResetForRecover=xmlHandler.getHCALuserXMLelementContent("UseResetForRecover",true); }
      catch (UserActionException e) { logger.warn(e.getMessage()); }
      if (useResetForRecover.equals("false")) {
        functionManager.getHCALparameterSet().put(new FunctionManagerParameter<BooleanT>("USE_RESET_FOR_RECOVER",new BooleanT(false)));
        logger.debug("[HCAL base] UseResetForRecover: " + useResetForRecover + " - this means the \"Recover\" button will perform \"Reset\" unless the user overrides this setting.");
      }
      else if (useResetForRecover.equals("true")) {
        logger.debug("[HCAL base] UseResetForRecover: " + useResetForRecover + " - this means the \"Recover\" button will peform its default behavior unless the user overrides this setting.");
      }
      else {
        logger.debug("[HCAL base] UseResetForRecover is not a valid boolean.");
      }
    }

    logger.debug("[HCAL base] base class init() called: functionManager = " + functionManager );
    try {

      // Get the list of master snippets from the userXML and use it to find the mastersnippet file.
      MapT<MapT<StringT>> LocalRunKeyMap = new MapT<MapT<StringT>>();
      VectorT<StringT> LocalRunKeys = new VectorT<StringT>();

      String CfgCVSBasePath    = ((StringT) functionManager.getHCALparameterSet().get("HCAL_CFGCVSBASEPATH").getValue()).getString();
      String MasterSnippetList = ((StringT) functionManager.getHCALparameterSet().get("HCAL_MASTERSNIPPETLIST").getValue()).getString();

      if(MasterSnippetList!=""){
        NodeList nodes = xmlHandler.getHCALuserXML(CfgCVSBasePath,MasterSnippetList).getElementsByTagName("RunConfig");
        for (int i=0; i < nodes.getLength(); i++) {
          logger.debug("[HCAL " + functionManager.FMname + "]: Item " + i + " has node name: " + nodes.item(i).getAttributes().getNamedItem("name").getNodeValue() 
              + ", snippet name: " + nodes.item(i).getAttributes().getNamedItem("snippet").getNodeValue()+ ", and maskedapps: " + nodes.item(i).getAttributes().getNamedItem("maskedapps").getNodeValue());
          
          MapT<StringT> RunKeySetting = new MapT<StringT>();
          StringT runkeyName =new StringT(nodes.item(i).getAttributes().getNamedItem("name").getNodeValue());

          if ( ((Element)nodes.item(i)).hasAttribute("snippet")){
            RunKeySetting.put(new StringT("snippet")   ,new StringT(nodes.item(i).getAttributes().getNamedItem("snippet"   ).getNodeValue()));
          }
          else{
            String errMessage="Cannot find attribute snippet in this Runkey"+runkeyName+", check the RunConfig entry in userXML!";
            functionManager.goToError(errMessage);
          }
          if ( ((Element)nodes.item(i)).hasAttribute("maskedapps")){
            RunKeySetting.put(new StringT("maskedapps"),new StringT(nodes.item(i).getAttributes().getNamedItem("maskedapps").getNodeValue()));
          }
          if ( ((Element)nodes.item(i)).hasAttribute("maskedFM")){
            RunKeySetting.put(new StringT("maskedFM")  ,new StringT(nodes.item(i).getAttributes().getNamedItem("maskedFM"  ).getNodeValue()));
          }
          if ( ((Element)nodes.item(i)).hasAttribute("singlePartitionFM")){
            RunKeySetting.put(new StringT("singlePartitionFM")  ,new StringT(nodes.item(i).getAttributes().getNamedItem("singlePartitionFM").getNodeValue()));
          }

          logger.debug("[HCAL " + functionManager.FMname + "]: RunkeySetting  is :"+ RunKeySetting.toString());

          LocalRunKeys.add(runkeyName);
          LocalRunKeyMap.put(runkeyName,RunKeySetting);

        }
      }

      logger.debug("[HCAL " + functionManager.FMname + "]: LocalRunKeyMap is :"+ LocalRunKeyMap.toString());

      functionManager.getHCALparameterSet().put(new FunctionManagerParameter<VectorT<StringT>>   ("AVAILABLE_LOCALRUNKEYS",LocalRunKeys));
      functionManager.getHCALparameterSet().put(new FunctionManagerParameter<MapT<MapT<StringT>>>("AVAILABLE_RUN_CONFIGS" ,LocalRunKeyMap));
      
    }
    catch (DOMException | UserActionException e) {
      String errMessage = "[HCAL " + functionManager.FMname + "]: Got an error when trying to manipulate the userXML: ";
      functionManager.goToError(errMessage,e);
    }



    logger.debug("[HCAL LVL1] HCALlevelOneEventHandler::init() called: functionManager = " + functionManager );
  }

  public void initAction(Object obj) throws UserActionException {

    if (obj instanceof StateEnteredEvent) {
      String RunConfigSelected = "";
      String CfgSnippetKeySelected = "";
      // get the parameters of the command
      ParameterSet<CommandParameter> parameterSet = getUserFunctionManager().getLastInput().getParameterSet();



      // check parameter set
      if (parameterSet.size()==0 || parameterSet.get("SID") == null )  {

        RunType = "local";
        // below: this is a hack for testing
        // RunType = "global";

        // request a session ID
        functionManager.getSessionId();
        // get the Sid from the init command
        if (functionManager.getParameterSet().get("SID") != null) {
          logger.info("[HCAL LVL1 " + functionManager.FMname + "] Going to pass the SID just obtained ");
          Sid = ((IntegerT)functionManager.getParameterSet().get("SID").getValue()).getInteger();
          logger.info("[HCAL LVL1 " + functionManager.FMname + "] The session ID is " + Sid);
        }
        else {
          String warnMessage = "[HCAL LVL1 " + functionManager.FMname + "] Did not set a SID properly in getSessionID()...";
          logger.warn(warnMessage);
        }

        GlobalConfKey = "not used";

        // set the run type in the function manager parameters
        functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("HCAL_RUN_TYPE",new StringT(RunType)));
        functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("GLOBAL_CONF_KEY",new StringT(GlobalConfKey)));

        // RUN_CONFIG_SELECTED = LocalRunKey; CFGSNIPPET_KEY_SELECTED = MasterSnippet file of LocalRunKey
        RunConfigSelected = ((StringT)functionManager.getHCALparameterSet().get("RUN_CONFIG_SELECTED").getValue()).getString();
        CfgSnippetKeySelected = ((StringT)functionManager.getHCALparameterSet().get("CFGSNIPPET_KEY_SELECTED").getValue()).getString();
      }
      else {

        RunType = "global";

        // set the run type in the function manager parameters
        functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("HCAL_RUN_TYPE",new StringT(RunType)));

        // get the Sid from the init command
        if (parameterSet.get("SID") != null) {
          Sid = ((IntegerT)parameterSet.get("SID").getValue()).getInteger();
          functionManager.getHCALparameterSet().put(new FunctionManagerParameter<IntegerT>("SID",new IntegerT(Sid)));
          functionManager.getHCALparameterSet().put(new FunctionManagerParameter<IntegerT>("INITIALIZED_WITH_SID",new IntegerT(Sid)));
        }
        else {
          String warnMessage = "[HCAL LVL1 " + functionManager.FMname + "] Did not receive a SID ...";
          logger.warn(warnMessage);
        }

        // get the GlobalConfKey from the init command
        if (parameterSet.get("GLOBAL_CONF_KEY") != null) {
          GlobalConfKey = ((StringT)parameterSet.get("GLOBAL_CONF_KEY").getValue()).getString();
          functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("GLOBAL_CONF_KEY",new StringT(GlobalConfKey)));
          functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("INITIALIZED_WITH_GLOBAL_CONF_KEY",new StringT(GlobalConfKey)));
        }
        else {
          String warnMessage = "[HCAL LVL1 " + functionManager.FMname + "] Did not receive a GlobalConfKey ...";
          logger.warn(warnMessage);
        }
        //Set the CfgSnippetKeySelected for global 
        try {
          if (functionManager.FMrole.equals("HCAL")) {
            CfgSnippetKeySelected = "global_HCAL";
            RunConfigSelected = xmlHandler.getNamedUserXMLelementAttributeValue("RunConfig", CfgSnippetKeySelected, "snippet",true);
            logger.warn("[JohnLog3] " + functionManager.FMname + ": This level1 with role " + functionManager.FMrole + " thinks we are in global mode and thus picked the RunConfigSelected = " + RunConfigSelected );
          }
          else if (functionManager.FMrole.equals("HF")) {
            CfgSnippetKeySelected = "global_HF";
            RunConfigSelected = xmlHandler.getNamedUserXMLelementAttributeValue("RunConfig", CfgSnippetKeySelected, "snippet",true);
            logger.warn("[JohnLog3] " + functionManager.FMname + ": This level1 with role " + functionManager.FMrole + " thinks we are in global mode and thus picked the RunConfigSelected = " + RunConfigSelected );
          }
          else {
            String errMessage = "[JohnLog3] " + functionManager.FMname + ": This FM is a level1 in global but it has neither the role 'HCAL' nor 'HF'. This is probably bad. Make sure the role is correctly assigned in the configuration.";  
            functionManager.goToError(errMessage);
          }
        }
        catch (UserActionException ex) { 
          functionManager.goToError( ex.getMessage() );
        }
      }

      if (!RunConfigSelected.equals("") && !CfgSnippetKeySelected.equals("")){
        functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("RUN_CONFIG_SELECTED", new StringT(RunConfigSelected)));
        functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("CFGSNIPPET_KEY_SELECTED", new StringT(CfgSnippetKeySelected)));
      }else{
        logger.warn("[Martin log] "+functionManager.FMname + ": Did not get mastersnippet info from GUI (for local run) or from LV0(for global).");
      }

      masker.pickEvmTrig();
      masker.setMaskedFMs();
      
      // convert TCDS apps to service apps
      QualifiedGroup qg = ConvertTCDSAppsToServiceApps(functionManager.getQualifiedGroup());
      // reset QG to modified one
      functionManager.setQualifiedGroup(qg);
      //Set SID of QG for service App
      qg = functionManager.getQualifiedGroup();
      if( qg.getRegistryEntry("SID") ==null){
        Integer sid = ((IntegerT)functionManager.getHCALparameterSet().get("SID").getValue()).getInteger();
        qg.putRegistryEntry("SID", sid);
        logger.warn("[HCAL "+ functionManager.FMname+"] Just set the SID of QG to "+ sid);
      }
      else{
        logger.info("[HCAL "+ functionManager.FMname+"] SID of QG is "+ qg.getRegistryEntry("SID"));
      }

      List<QualifiedResource> xdaqExecList = qg.seekQualifiedResourcesOfType(new XdaqExecutive());
      // loop over the executives to strip the connections

      VectorT<StringT> MaskedResources = (VectorT<StringT>)functionManager.getHCALparameterSet().get("MASKED_RESOURCES").getValue();

      if (MaskedResources.size() > 0) {
        logger.info("[HCAL LVL1 " + functionManager.FMname + "]: about to set the xml for the xdaq executives.");
        for( QualifiedResource qr : xdaqExecList) {
          XdaqExecutive exec = (XdaqExecutive)qr;
          XdaqExecutiveConfiguration config =  exec.getXdaqExecutiveConfiguration();
          String oldExecXML = config.getXml();
          try {
            String newExecXML = xmlHandler.stripExecXML(oldExecXML, functionManager.getHCALparameterSet());
            config.setXml(newExecXML);
            logger.info("[HCAL LVL1 " + functionManager.FMname + "]: Just set the xml for executive " + qr.getName());
          }
          catch (UserActionException e) {
            String errMessage = "[HCAL LVL1 "+functionManager.FMname+"] got an error during StripExecXML:";
            functionManager.goToError(errMessage,e);
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
      functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("STATE",new StringT("calculating state")));
      functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("ACTION_MSG",new StringT("Initializing")));

      // initialize all XDAQ executives
      initXDAQ();
      functionManager.parameterSender.start();

      // start the monitor thread
      System.out.println("[HCAL LVL1 " + functionManager.FMname + "] Starting Monitor thread ...");
      logger.debug("[HCAL LVL1 " + functionManager.FMname + "] Starting Monitor thread ...");
      LevelOneMonitorThread thread1 = new LevelOneMonitorThread();
      thread1.start();

      // give the RunType to the controlling FM
      functionManager.RunType = RunType;
      logger.info("[HCAL LVL1 " + functionManager.FMname + "] initAction: We are in " + RunType + " mode ...");

      // prepare run number to be passed to level 2
      ParameterSet<CommandParameter> pSet = new ParameterSet<CommandParameter>();
      pSet.put(new CommandParameter<StringT>("HCAL_RUN_TYPE", new StringT(RunType)));
      pSet.put(new CommandParameter<IntegerT>("SID", new IntegerT(Sid)));
      pSet.put(new CommandParameter<StringT>("GLOBAL_CONF_KEY", new StringT(GlobalConfKey)));

      pSet.put(new CommandParameter<StringT>("RUN_CONFIG_SELECTED", new StringT(RunConfigSelected)));
      pSet.put(new CommandParameter<StringT>("CFGSNIPPET_KEY_SELECTED", new StringT(CfgSnippetKeySelected)));
      functionManager.getHCALparameterSet().put(new FunctionManagerParameter<VectorT<StringT>>("MASKED_RESOURCES", MaskedResources));
      pSet.put(new CommandParameter<VectorT<StringT>>("MASKED_RESOURCES", MaskedResources));

      String ruInstance =  ((StringT)functionManager.getHCALparameterSet().get("RU_INSTANCE").getValue()).getString();
      logger.info("[HCAL LVL1 " + functionManager.FMname + "]: This level1 has the RU_INSTANCE " + ruInstance);
      functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("RU_INSTANCE", new StringT(ruInstance)));
      pSet.put(new CommandParameter<StringT>("RU_INSTANCE", new StringT(ruInstance)));

      String evmTrigFM =  ((StringT)functionManager.getHCALparameterSet().get("EVM_TRIG_FM").getValue()).getString();
      logger.info("[HCAL LVL1 " + functionManager.FMname + "]: This level1 has the EVM_TRIG_FM " + evmTrigFM);
      functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("EVM_TRIG_FM", new StringT(evmTrigFM)));
      pSet.put(new CommandParameter<StringT>("EVM_TRIG_FM", new StringT(evmTrigFM)));

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
              functionManager.goToError(errMessage,e);
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
      functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("STATE",new StringT(functionManager.getState().getStateString())));
      functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("ACTION_MSG",new StringT("initAction executed ...")));

      // publish the initialization time for this FM to the paramterSet
      functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("HCAL_TIME_OF_FM_START", new StringT(functionManager.utcFMtimeofstart)));

      logger.info("[HCAL LVL1 " + functionManager.FMname + "] initAction executed ...");
    }
  }

  public void resetAction(Object obj) throws UserActionException {

    if (obj instanceof StateEnteredEvent) {
      System.out.println("[HCAL LVL1 " + functionManager.FMname + "] Executing resetAction");
      logger.debug("[HCAL LVL1 " + functionManager.FMname + "] Executing resetAction");

      publishRunInfoSummary();
      functionManager.HCALRunInfo = null; // make RunInfo ready for the next round of run info to store

      // reset the non-async error state handling
      functionManager.ErrorState = false;

      // set actions
      functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("STATE",new StringT("calculating state")));
      functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("ACTION_MSG",new StringT("Resetting")));
      functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("SUPERVISOR_ERROR",new StringT("")));

      if (!functionManager.containerFMChildren.isEmpty()) {

        // reset all FMs
        try {
          logger.debug("[HCAL LVL1 " + functionManager.FMname + "] Found FM childs - good! fireEvent: " + HCALInputs.RESET);
          functionManager.containerFMChildren.execute(HCALInputs.RESET);
        }
        catch (QualifiedResourceContainerException e) {
          String errMessage = "[HCAL LVL1 " + functionManager.FMname + "] Error! QualifiedResourceContainerException: sending: " + HCALInputs.RESET + " failed ...";
          functionManager.goToError(errMessage,e);
        }
      }
      else {
        if (!functionManager.ErrorState) {
          logger.debug("[HCAL LVL1 " + functionManager.FMname + "] fireEvent: " + HCALInputs.SETHALT);
          functionManager.fireEvent(HCALInputs.SETHALT);
        }
      }

      // set actions
      functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("STATE",new StringT(functionManager.getState().getStateString())));
      functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("ACTION_MSG",new StringT("resetAction executed ...")));

      logger.info("[HCAL LVL1 " + functionManager.FMname + "] resetAction executed ...");
    }
  }

  public void recoverAction(Object obj) throws UserActionException {
    Boolean UseResetForRecover = ((BooleanT)functionManager.getHCALparameterSet().get("USE_RESET_FOR_RECOVER").getValue()).getBoolean();
    if (UseResetForRecover) {
      resetAction(obj); return;
    }
    if (obj instanceof StateEnteredEvent) {
      System.out.println("[HCAL LVL1 " + functionManager.FMname + "] Executing recoverAction");
      logger.info("[HCAL LVL1 " + functionManager.FMname + "] Executing recoverAction");

      // reset the non-async error state handling
      functionManager.ErrorState = false;

      // set actions
      functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("STATE",new StringT("calculating state")));
      functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("ACTION_MSG",new StringT("recovering")));

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
          functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("STATE",new StringT("Error")));
          functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("ACTION_MSG",new StringT("oops - problems ...")));
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
      functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("STATE",new StringT(functionManager.getState().getStateString())));
      functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("ACTION_MSG",new StringT("recoverAction executed ...")));

      logger.info("[HCAL LVL1 " + functionManager.FMname + "] recoverAction executed ...");
    }
  }

  public void configureAction(Object obj) throws UserActionException {
    
    if (obj instanceof StateEnteredEvent) {
      System.out.println("[HCAL LVL1 " + functionManager.FMname + "] Executing configureAction");
      logger.info("[HCAL LVL1 " + functionManager.FMname + "] Executing configureAction");

      // reset the non-async error state handling
      functionManager.ErrorState = false;

      // set actions
      functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("STATE",new StringT("calculating state")));
      functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("ACTION_MSG",new StringT("configuring")));

      // get the parameters of the command
      ParameterSet<CommandParameter> parameterSet = getUserFunctionManager().getLastInput().getParameterSet();

      // check parameter set, if it is not set see if we are in local mode
      if (parameterSet.size()==0)  {
        RunType = "local";
        functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("HCAL_RUN_TYPE",new StringT(RunType)));
        //getFedEnableMask();
      }
      else {
        RunType = "global";

        // set the run type in the function manager parameters
        functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("HCAL_RUN_TYPE",new StringT(RunType)));

        // get the run key from the configure command
        if (parameterSet.get("RUN_KEY") != null) {
          RunKey = ((StringT)parameterSet.get("RUN_KEY").getValue()).getString();
          // set the run key in the function manager parameters
          functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("RUN_KEY",new StringT(RunKey)));
          functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("CONFIGURED_WITH_RUN_KEY",new StringT(RunKey)));

        }
        else {
          String warnMessage = "[HCAL LVL1 " + functionManager.FMname + "] Did not receive a run key.\nThis is probably OK for normal HCAL LVL1 operations ...";
          logger.warn(warnMessage);
        }

        // get the tpg key from the configure command
        if (parameterSet.get("TPG_KEY") != null) {
          TpgKey = ((StringT)parameterSet.get("TPG_KEY").getValue()).getString();
          functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("CONFIGURED_WITH_TPG_KEY",new StringT(TpgKey)));
          String warnMessage = "[HCAL LVL1 " + functionManager.FMname + "] Received a L1 TPG key: " + TpgKey;
          logger.warn(warnMessage);
        }
        else {
          String warnMessage = "[HCAL LVL1 " + functionManager.FMname + "] Did not receive a L1 TPG key.\nThis is only OK for HCAL local run operations or if HCAL is out of the trigger for global runs ...";
          logger.warn(warnMessage);
        }

        // get the run number from the configure command and cache this one
        if (parameterSet.get("RUN_NUMBER") != null) {
          functionManager.CachedRunNumber = ((IntegerT)parameterSet.get("RUN_NUMBER").getValue()).getInteger();
          functionManager.getHCALparameterSet().put(new FunctionManagerParameter<IntegerT>("CONFIGURED_WITH_RUN_NUMBER",new IntegerT(functionManager.CachedRunNumber)));
          logger.info("[HCAL LVL1 " + functionManager.FMname + "] Did receive a run number during the configureAction().\nThe run number received was: " + functionManager.CachedRunNumber);
        }
        else {
          logger.info("[HCAL LVL1 " + functionManager.FMname + "] Did not receive a run number during the configureAction().\nThis is probably OK for normal HCAL LVL1 operations ...");
        }

        // get the info from the LVL1 if special actions due to a central CMS clock source change are indicated
        ClockChanged = false;
        if (parameterSet.get("CLOCK_CHANGED") != null) {
          ClockChanged = ((BooleanT)parameterSet.get("CLOCK_CHANGED").getValue()).getBoolean();
          functionManager.getHCALparameterSet().put(new FunctionManagerParameter<BooleanT>("CLOCK_CHANGED",new BooleanT(ClockChanged)));
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
        if (parameterSet.get("USE_RESET_FOR_RECOVER") != null) {
          UseResetForRecover = ((BooleanT)parameterSet.get("USE_RESET_FOR_RECOVER").getValue()).getBoolean();
          functionManager.getHCALparameterSet().put(new FunctionManagerParameter<BooleanT>("USE_RESET_FOR_RECOVER", new BooleanT(UseResetForRecover)));
        }

        UsePrimaryTCDS = true;
        if (parameterSet.get("USE_PRIMARY_TCDS") != null) {
          UsePrimaryTCDS = ((BooleanT)parameterSet.get("USE_PRIMARY_TCDS").getValue()).getBoolean();
          functionManager.getHCALparameterSet().put(new FunctionManagerParameter<BooleanT>("USE_PRIMARY_TCDS", new BooleanT(UsePrimaryTCDS)));
        }

        // Give the supervisor error to the level1FM
        SupervisorError = "";
        if (parameterSet.get("SUPERVISOR_ERROR") != null) {
          SupervisorError = ((StringT)parameterSet.get("SUPERVISOR_ERROR").getValue()).getString();
          functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("SUPERVISOR_ERROR", new StringT(SupervisorError)));
        }

        // get the FED list from the configure command
        if (parameterSet.get("FED_ENABLE_MASK") != null) {
          FedEnableMask = ((StringT)parameterSet.get("FED_ENABLE_MASK").getValue()).getString();
          functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("FED_ENABLE_MASK",new StringT(FedEnableMask)));
          functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("CONFIGURED_WITH_FED_ENABLE_MASK",new StringT(TpgKey)));

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
          functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("STATE",new StringT("Error")));
          functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("ACTION_MSG",new StringT("oops - problems ...")));
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

      // Parse the mastersnippet:
      String selectedRun = ((StringT)functionManager.getHCALparameterSet().get("RUN_CONFIG_SELECTED").getValue()).getString();
      String CfgCVSBasePath = ((StringT)functionManager.getParameterSet().get("HCAL_CFGCVSBASEPATH").getValue()).getString();
      // Reset HCAL_CFGSCRIPT:
      functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("HCAL_CFGSCRIPT",new StringT("not set")));

      // Try to find a common masterSnippet from MasterSnippet
      String CommonMasterSnippetFile ="";
      try{
        String TagName="CommonMasterSnippet";
        String attribute="file";
        CommonMasterSnippetFile = xmlHandler.getHCALMasterSnippetTagAttribute(selectedRun,CfgCVSBasePath,TagName,attribute);
      }
      catch(UserActionException e){
        logger.error("[HCAL LVL1"+functionManager.FMname+"]: Found more than one CommonMasterSnippet tag in the mastersnippet! This is not allowed!");
        functionManager.goToError(e.getMessage());
      }

      //Check if the NUMBER_OF_EVENTS parameter is already set from GUI, if so, ignore settings from mastersnippet
      boolean NeventIsSetFromGUI = !xmlHandler.hasDefaultValue("NUMBER_OF_EVENTS",1000);

      if(!CommonMasterSnippetFile.equals("")){    
          //parse and set HCAL parameters from CommonMasterSnippet
          logger.info("[HCAL LVL1 "+ functionManager.FMname +"] Going to parse CommonMasterSnippet : "+ CommonMasterSnippetFile);
          xmlHandler.parseMasterSnippet(CommonMasterSnippetFile,CfgCVSBasePath,NeventIsSetFromGUI);
      }
      //Parse and set HCAL parameters from MasterSnippet
      logger.info("[HCAL LVL1 "+ functionManager.FMname +"] Going to parse MasterSnippet : "+ selectedRun);
      xmlHandler.parseMasterSnippet(selectedRun,CfgCVSBasePath,NeventIsSetFromGUI);

      //Pring results from mastersnippet:
      logger.info("[HCAL LVL1 " + functionManager.FMname + "]  Printing results from parsing Mastersnippet(s): ");
      FullCfgScript = ((StringT)functionManager.getHCALparameterSet().get("HCAL_CFGSCRIPT").getValue()).getString();
      logger.debug("[HCAL LVL1 " + functionManager.FMname + "] The CfgScript from mastersnippet is like this: \n" + FullCfgScript);
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
      logger.debug("[HCAL LVL1 " + functionManager.FMname + "] The final CfgScript is like this: \n" + FullCfgScript);

      //Get the results from parseMasterSnippet      
      String TTCciControlSequence = ((StringT)functionManager.getHCALparameterSet().get("HCAL_TTCCICONTROL").getValue()).getString();
      String LTCControlSequence   = ((StringT)functionManager.getHCALparameterSet().get("HCAL_LTCCONTROL"  ).getValue()).getString();
      FedEnableMask            = ((StringT)functionManager.getHCALparameterSet().get("FED_ENABLE_MASK" ).getValue()).getString();
      // Get the value of runinfopublish from the results of parseMasterSnippet
      RunInfoPublish           = ((BooleanT)functionManager.getHCALparameterSet().get("HCAL_RUNINFOPUBLISH").getValue()).getBoolean();
      OfficialRunNumbers       = ((BooleanT)functionManager.getHCALparameterSet().get("OFFICIAL_RUN_NUMBERS").getValue()).getBoolean();
      TriggersToTake           = ((IntegerT)functionManager.getHCALparameterSet().get("NUMBER_OF_EVENTS").getValue()).getInteger();
      // We default skipPLLreset to true for now. 
      BooleanT skipPLLreset    = ((BooleanT)functionManager.getHCALparameterSet().get("TCDS_SKIP_PLLRESET"     ).getValue());

      //Switch single/Multi partition
      boolean isSinglePartition   = ((BooleanT)functionManager.getHCALparameterSet().get("SINGLEPARTITION_MODE").getValue()).getBoolean();
      String LPMControlSequence="not set";
      String ICIControlSequence="not set";
      String PIControlSequence ="not set";
      if(isSinglePartition){
        ICIControlSequence   = ((StringT)functionManager.getHCALparameterSet().get("HCAL_ICICONTROL_SINGLE" ).getValue()).getString();
        PIControlSequence    = ((StringT)functionManager.getHCALparameterSet().get("HCAL_PICONTROL_SINGLE"   ).getValue()).getString();
      }
      else{
        LPMControlSequence   = ((StringT)functionManager.getHCALparameterSet().get("HCAL_LPMCONTROL"  ).getValue()).getString();
        ICIControlSequence   = ((StringT)functionManager.getHCALparameterSet().get("HCAL_ICICONTROL_MULTI" ).getValue()).getString();
        PIControlSequence    = ((StringT)functionManager.getHCALparameterSet().get("HCAL_PICONTROL_MULTI"   ).getValue()).getString();
      }
      logger.info("[HCAL LVL1 " + functionManager.FMname + "] ConfigureAction: We are in  Single Partition mode: " + isSinglePartition);
      logger.debug("[HCAL LVL1 " + functionManager.FMname + "] The final ICIControlSequence is like this: \n"   +ICIControlSequence              );
      logger.debug("[HCAL LVL1 " + functionManager.FMname + "] The final LPMControlSequence  is like this: \n"  +LPMControlSequence              );
      logger.debug("[HCAL LVL1 " + functionManager.FMname + "] The final PIControlSequence   is like this: \n"  +PIControlSequence               );
      logger.debug("[HCAL LVL1 " + functionManager.FMname + "] The final TTCciControlSequence is like this: \n" +TTCciControlSequence            );
      logger.debug("[HCAL LVL1 " + functionManager.FMname + "] The final LTCControlSequence is like this: \n"   +LTCControlSequence              );
      logger.info("[HCAL LVL1 " + functionManager.FMname + "] The final AlarmerURL is "                        +functionManager.alarmerURL          );
      logger.info("[HCAL LVL1 " + functionManager.FMname + "] The final AlarmerPartition is "                  +functionManager.alarmerPartition    );
      logger.info("[HCAL LVL1 " + functionManager.FMname + "] The FED_ENABLE_MASK used by the level-1 is: "    +FedEnableMask                       );
      logger.info("[HCAL LVL1 " + functionManager.FMname + "] The RunInfoPublish value is : "                  +RunInfoPublish                      );
      logger.info("[HCAL LVL1 " + functionManager.FMname + "] The OfficialRunNumbers value is : "              +OfficialRunNumbers                  );
      logger.info("[HCAL LVL1 " + functionManager.FMname + "] The NumberOfEvents is : "                        +TriggersToTake                      );

      //Set up infospace parameters needed by localDAQ FM
      SetLocalDAQinfospace();

      // start the alarmer watch thread here, now that we have the alarmerURL
      if (alarmerthread!=null){
        if (alarmerthread.isAlive()){
          logger.warn("[HCAL LVL1 " + functionManager.FMname + "] AlarmerWatchThread is alive, not creating a new one...");
        }else{
          logger.warn("[HCAL LVL1 " + functionManager.FMname + "] AlarmerWatchThread is not alive, creating a new one...");
          alarmerthread = new AlarmerWatchThread();
          alarmerthread.start();
        }
      }
      else{
        logger.warn("[HCAL LVL1 " + functionManager.FMname + "] Starting AlarmerWatchThread ...");
        alarmerthread = new AlarmerWatchThread();
        alarmerthread.start();
      }

      // Disable FMs based on FED_ENABLE_MASK, if all FEDs in the FM partition are masked.
      // First, make map <partition => fed list>
      HashMap<String, List<Integer> > childFMFedMap = new HashMap<String, List<Integer> >();
      List<QualifiedResource> fmChildrenList = functionManager.containerFMChildren.getQualifiedResourceList();
      for(QualifiedResource qr : fmChildrenList) {
        String childFMName = qr.getName();
        List<Integer> childFMFeds = null;
        List<ConfigProperty> propertiesList = qr.getResource().getProperties();
        for (ConfigProperty property : propertiesList) {
          if (property.getName().equals("FEDList")) {
            childFMFeds = new ArrayList<Integer>();
            String[] childFMFedsStr = property.getValue().replace("[","").replace("]","").split(";|,");
            if (childFMFedsStr.length == 0) {
              logger.error("[HCAL LVL 1 " + functionManager.FMname + "] DavidLog -- Child FM " + childFMName + " has property FEDList, but I failed to parse the feds out of string " + property.getValue());
            }
            for(String s : childFMFedsStr) {
              childFMFeds.add(Integer.valueOf(s));
            }
          }
        }
        if (childFMFeds == null) {
          logger.info("[HCAL LVL 1 " + functionManager.FMname + "] DavidLog -- For child FM " + childFMName + ", did not find list of FEDs. So, I won't consider disabling it with FED_ENABLE_MASK.");
        } else {
          logger.info("[HCAL LVL 1 " + functionManager.FMname + "] DavidLog -- For child FM " + childFMName + ", found FEDs: " + childFMFeds.toString() + ". I will consider disabling it based on FED_ENABLE_MASK.");
          childFMFedMap.put(childFMName, childFMFeds);
        }
      }

      // Use function HCALEventHandler::getMaskedChildFMsFromFedMask to get a list of the partitions to be masked, and destroy.
      List<String> maskedChildFMs = getMaskedChildFMsFromFedMask(FedEnableMask, childFMFedMap);
      VectorT<StringT> EmptyFMs   = new VectorT<StringT>();
      String evmTrigFM =  ((StringT)functionManager.getHCALparameterSet().get("EVM_TRIG_FM").getValue()).getString(); // For local runs, masking the evmTrigFM will cause problems, so forbid it.
      for(QualifiedResource qr : fmChildrenList) {
        String childFMName = qr.getName();
        if (maskedChildFMs.contains(childFMName)) {
          logger.warn("[HCAL LVL1 " + functionManager.FMname + "] DavidLog -- Based on FED_ENABLE_MASK, I am attempting to destroy FM XDAQ " + childFMName + "." );

          // Check that the partition is not responsible for event building/triggering
          if (childFMName.equals(evmTrigFM)) {
            functionManager.goToError("[HCAL LVL 1 " + functionManager.FMname + "] Error! I want to disable " + childFMName + " based on FED_ENABLE_MASK, but it is designated as EVM_TRIG_FM.");
          }
          // Add this FM to emptyFM           
          EmptyFMs.add(new StringT(childFMName));
        }
      }
      functionManager.getHCALparameterSet().put(new FunctionManagerParameter<VectorT<StringT>>("EMPTY_FMS",EmptyFMs));
      String emptyFMnames      ="";
      for(StringT FMname : EmptyFMs){
        emptyFMnames += FMname.getString()+";";
      }
      // END TEST PARTITION DISABLING
      
      // Start Progress Watchthread after updating EmptyFM
      nChildren    = functionManager.containerFMChildren.getQualifiedResourceList().size();
      int nEmptyFM = ((VectorT<StringT>)functionManager.getHCALparameterSet().get("EMPTY_FMS").getValue()).size();
      nChildren    = nChildren - nEmptyFM;
      ProgressThread progressThread = new ProgressThread(functionManager);
      progressThread.start();


      // prepare run mode to be passed to level 2
      //String CfgCVSBasePath = ((StringT)functionManager.getParameterSet().get(HCALParameters.HCAL_CFGCVSBASEPATH).getValue()).getString();
      ParameterSet<CommandParameter> pSet = new ParameterSet<CommandParameter>();
      pSet.put(new CommandParameter<IntegerT>("RUN_NUMBER"            , new IntegerT(functionManager.RunNumber)));
      pSet.put(new CommandParameter<StringT>("HCAL_RUN_TYPE"          , new StringT(RunType)));
      pSet.put(new CommandParameter<StringT>("RUN_KEY"                , new StringT(RunKey)));
      pSet.put(new CommandParameter<StringT>("TPG_KEY"                , new StringT(TpgKey)));
      pSet.put(new CommandParameter<StringT>("FED_ENABLE_MASK"        , new StringT(FedEnableMask)));
      pSet.put(new CommandParameter<StringT>("HCAL_CFGCVSBASEPATH"    , new StringT(CfgCVSBasePath)));
      pSet.put(new CommandParameter<StringT>("HCAL_CFGSCRIPT"         , new StringT(FullCfgScript)));
      pSet.put(new CommandParameter<StringT>("HCAL_TTCCICONTROL"      , new StringT(TTCciControlSequence)));
      pSet.put(new CommandParameter<StringT>("HCAL_LTCCONTROL"        , new StringT(LTCControlSequence)));
      pSet.put(new CommandParameter<BooleanT>("SINGLEPARTITION_MODE"  , new BooleanT(isSinglePartition)));
      // Only send the one to be used by LV2,so that run info will be consistent
      if(isSinglePartition){
        pSet.put(new CommandParameter<StringT>("HCAL_ICICONTROL_SINGLE"       , new StringT(ICIControlSequence)));
        pSet.put(new CommandParameter<StringT>("HCAL_PICONTROL_SINGLE"        , new StringT(PIControlSequence)));
      }
      else{
        pSet.put(new CommandParameter<StringT>("HCAL_ICICONTROL_MULTI"       , new StringT(ICIControlSequence)));
        pSet.put(new CommandParameter<StringT>("HCAL_PICONTROL_MULTI"        , new StringT(PIControlSequence)));
        pSet.put(new CommandParameter<StringT>("HCAL_LPMCONTROL"             , new StringT(LPMControlSequence)));
      }
      pSet.put(new CommandParameter<BooleanT>("CLOCK_CHANGED"         , new BooleanT(ClockChanged)));
      pSet.put(new CommandParameter<BooleanT>("USE_RESET_FOR_RECOVER" , new BooleanT(UseResetForRecover)));
      pSet.put(new CommandParameter<StringT>("HCAL_PICONTROL"         , new StringT(PIControlSequence)));
      pSet.put(new CommandParameter<BooleanT>("USE_PRIMARY_TCDS"      , new BooleanT(UsePrimaryTCDS)));
      pSet.put(new CommandParameter<StringT>("SUPERVISOR_ERROR"       , new StringT(SupervisorError)));
      pSet.put(new CommandParameter<BooleanT>("HCAL_RUNINFOPUBLISH"   , new BooleanT(RunInfoPublish)));
      pSet.put(new CommandParameter<BooleanT>("OFFICIAL_RUN_NUMBERS"  , new BooleanT(OfficialRunNumbers)));
      pSet.put(new CommandParameter<BooleanT>("TCDS_SKIP_PLLRESET"    , skipPLLreset));
      pSet.put(new CommandParameter<VectorT<StringT>>("EMPTY_FMS"              , EmptyFMs));

      // prepare command plus the parameters to send
      Input configureInput= new Input(HCALInputs.CONFIGURE.toString());
      configureInput.setParameters( pSet );

      if (!functionManager.containerFMChildren.isEmpty()) {
        logger.debug("[HCAL LVL1 " + functionManager.FMname + "] Found FM childs - good! fireEvent: " + configureInput);


        Boolean needtowait = false;

        // include scheduling
        TaskSequence configureTaskSeq = new TaskSequence(HCALStates.CONFIGURING,HCALInputs.SETCONFIGURE);

        // now configure the rest in parallel
        //List<QualifiedResource> fmChildrenList = functionManager.containerFMChildren.getQualifiedResourceList();
        //List<FunctionManager> normalFMsToConfigureList = new ArrayList<FunctionManager>();
        //for(QualifiedResource qr : fmChildrenList){
        //  normalFMsToConfigureList.add((FunctionManager)qr);
        //}
        //QualifiedResourceContainer normalFMsToConfigureContainer = new QualifiedResourceContainer(normalFMsToConfigureList);
        //SimpleTask fmChildrenTask = new SimpleTask(normalFMsToConfigureContainer,configureInput,HCALStates.CONFIGURING,HCALStates.CONFIGURED,"Configuring regular priority FM children");
        SimpleTask fmChildrenTask = new SimpleTask(functionManager.containerFMChildrenNormal,configureInput,HCALStates.CONFIGURING,HCALStates.CONFIGURED,"LV1: Configuring regular priority FM children");
        SimpleTask EvmTrigConfigureTask = new SimpleTask(functionManager.containerFMChildrenEvmTrig,configureInput,HCALStates.CONFIGURING,HCALStates.CONFIGURED,"LV1: Configuring EvmTrig FM");
        
        logger.info("[HCAL LVL1 " + functionManager.FMname +"] Configuring these regular LV2 FMs: ");
        PrintQRnames(functionManager.containerFMChildrenNormal);
        configureTaskSeq.addLast(fmChildrenTask);
        logger.info("[HCAL LVL1 " + functionManager.FMname +"] Configuring the EvmTrig FM last: ");
        PrintQRnames(functionManager.containerFMChildrenEvmTrig);
        configureTaskSeq.addLast(EvmTrigConfigureTask);
        logger.info("[HCAL LVL1 " + functionManager.FMname +"] Destroying XDAQ for these LV2 FMs: "+emptyFMnames);

        logger.info("[HCAL LVL1 " + functionManager.FMname + "] executeTaskSequence.");
        functionManager.theStateNotificationHandler.executeTaskSequence(configureTaskSeq);
        functionManager.FMsWereConfiguredOnce = true;
      }

      // set actions
      functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("STATE",new StringT(functionManager.getState().getStateString())));
      functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("ACTION_MSG",new StringT("configureAction executed.")));

      logger.info("[HCAL LVL1 " + functionManager.FMname + "] configureAction executed.");
    }
  }

  public void startAction(Object obj) throws UserActionException {

      System.out.println("[HCAL LVL1 " + functionManager.FMname + "] Executing startAction");
      logger.info("[HCAL LVL1 " + functionManager.FMname + "] Executing startAction");

      // reset the non-async error state handling
      functionManager.ErrorState = false;

      // delay the first poll of alarmerWatchThread
      delayAlarmerWatchThread    = true;

      // set actions
      functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("STATE",new StringT("calculating state")));
      functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("ACTION_MSG",new StringT("Starting ...")));

      // get the parameters of the command
      ParameterSet<CommandParameter> parameterSet = getUserFunctionManager().getLastInput().getParameterSet();

      // check parameter set
      if (parameterSet.size()==0) {

        functionManager.RunNumber = ((IntegerT)functionManager.getHCALparameterSet().get("RUN_NUMBER").getValue()).getInteger();
        RunSeqNumber = ((IntegerT)functionManager.getHCALparameterSet().get("RUN_SEQ_NUMBER").getValue()).getInteger();
        TriggersToTake = ((IntegerT)functionManager.getHCALparameterSet().get("NUMBER_OF_EVENTS").getValue()).getInteger();

        if (!RunType.equals("local")) {
          String errMessage = "[HCAL LVL1 " + functionManager.FMname + "] Error! command parameter problem for the startAction ...";
					functionManager.goToError(errMessage);
        }
        else {
          logger.info("[HCAL LVL1 " + functionManager.FMname + "] startAction: We are in local mode ...");
          logger.info("[HCAL LVL1 " + functionManager.FMname + "] startAction: Going to take "+TriggersToTake+" Events");

          // determine run number and run sequence number and overwrite what was set before
          OfficialRunNumbers       = ((BooleanT)functionManager.getParameterSet().get("OFFICIAL_RUN_NUMBERS").getValue()).getBoolean();
          if (OfficialRunNumbers) {

            //check availability of runInfo DB
            if(functionManager.getRunInfoConnector()!=null){
              RunNumberData rnd = getOfficialRunNumber();

              functionManager.RunNumber    = rnd.getRunNumber();
              RunSeqNumber = rnd.getSequenceNumber();

              functionManager.getHCALparameterSet().put(new FunctionManagerParameter<IntegerT>("RUN_NUMBER", new IntegerT(functionManager.RunNumber)));
              functionManager.getHCALparameterSet().put(new FunctionManagerParameter<IntegerT>("RUN_SEQ_NUMBER", new IntegerT(RunSeqNumber)));

              logger.info("[HCAL LVL1 " + functionManager.FMname + "] ... run number: " + functionManager.RunNumber + ", SequenceNumber: " + RunSeqNumber);
            }
            else{
              logger.error("[HCAL LVL1 "+functionManager.FMname+"] Official RunNumber requested, but cannot establish RunInfo Connection. Is there a RunInfo DB? or is RunInfo DB down?");
              logger.info("[HCAL LVL1 "+functionManager.FMname+"] Going to use run number ="+functionManager.RunNumber+", RunSeqNumber = "+ RunSeqNumber);
            }
          }
        }
      }
      else {

        // get the run number from the start command
        if (parameterSet.get("RUN_NUMBER") != null) {
          functionManager.RunNumber = ((IntegerT)parameterSet.get("RUN_NUMBER").getValue()).getInteger();
          functionManager.getHCALparameterSet().put(new FunctionManagerParameter<IntegerT>("RUN_NUMBER",new IntegerT(functionManager.RunNumber)));
          functionManager.getHCALparameterSet().put(new FunctionManagerParameter<IntegerT>("STARTED_WITH_RUN_NUMBER",new IntegerT(functionManager.RunNumber)));
        }
        else {
          String errMessage = "[HCAL LVL1 " + functionManager.FMname + "] Error! Did not receive a run number ...";
					functionManager.goToError(errMessage);
        }

        // get the run sequence number from the start command
        if (parameterSet.get("RUN_SEQ_NUMBER") != null) {
          RunSeqNumber = ((IntegerT)parameterSet.get("RUN_SEQ_NUMBER").getValue()).getInteger();
          functionManager.getHCALparameterSet().put(new FunctionManagerParameter<IntegerT>("RUN_SEQ_NUMBER", new IntegerT(RunSeqNumber)));
        }
        else {
          if (RunType.equals("local")) { logger.warn("[HCAL LVL1 " + functionManager.FMname + "] Warning! Did not receive a run sequence number.\nThis is OK for global runs."); }
        }

        // get the number of requested events
        if (parameterSet.get("NUMBER_OF_EVENTS") != null) {
          TriggersToTake = ((IntegerT)parameterSet.get("NUMBER_OF_EVENTS").getValue()).getInteger();
          functionManager.getHCALparameterSet().put(new FunctionManagerParameter<IntegerT>("NUMBER_OF_EVENTS",new IntegerT(TriggersToTake)));
        }
        else {
          if (RunType.equals("local")) { logger.warn("[HCAL LVL1 " + functionManager.FMname + "] Warning! Did not receive the number of events to take.\nThis is OK for global runs."); }

          // fix for global run configs running a local HCAL DAQ partition like the uTCA one
          TriggersToTake = ((IntegerT)functionManager.getHCALparameterSet().get("NUMBER_OF_EVENTS").getValue()).getInteger();
        }

      }

      publishRunInfoSummary();

      // prepare run number,etc. to be passed to level 2
      ParameterSet<CommandParameter> pSet = new ParameterSet<CommandParameter>();
      pSet.put(new CommandParameter<IntegerT>("RUN_NUMBER", new IntegerT(functionManager.RunNumber)));
      pSet.put(new CommandParameter<IntegerT>("RUN_SEQ_NUMBER", new IntegerT(RunSeqNumber)));
      pSet.put(new CommandParameter<IntegerT>("NUMBER_OF_EVENTS", new IntegerT(TriggersToTake)));

      // prepare command plus the parameters to send
      Input startInput= new Input(HCALInputs.START.toString());
      startInput.setParameters( pSet );

      if (!functionManager.containerFMChildren.isEmpty()) {
        //Schedule Task with active QR in the containers
        List<QualifiedResource> fmChildrenList          = functionManager.containerFMChildren.getActiveQRList();
        List<QualifiedResource> EvmTrigFMtoStartList    = functionManager.containerFMChildrenEvmTrig.getActiveQRList();
        List<QualifiedResource> lpmOrTTCciFMtoStartList = functionManager.containerFMTCDSLPM.getActiveQRList();

        List<FunctionManager> normalFMsToStartList = new ArrayList<FunctionManager>();
        for(QualifiedResource qr : fmChildrenList){
            normalFMsToStartList.add((FunctionManager)qr);
        }
        normalFMsToStartList.removeAll(EvmTrigFMtoStartList);
        normalFMsToStartList.removeAll(lpmOrTTCciFMtoStartList);

        QualifiedResourceContainer normalFMsToStartContainer    = new QualifiedResourceContainer(normalFMsToStartList);
        QualifiedResourceContainer EvmTrigFMtoStartContainer    = new QualifiedResourceContainer(EvmTrigFMtoStartList);
        QualifiedResourceContainer lpmOrTTCciFMtoStartContainer = new QualifiedResourceContainer(lpmOrTTCciFMtoStartList);
        
        // no reason not to always prioritize FM starts
        // include scheduling
        TaskSequence startTaskSeq = new TaskSequence(HCALStates.STARTING,HCALInputs.SETSTART);
        // 1) Everyone besides EvmTrig FMs in parallel
        if(!normalFMsToStartContainer.isEmpty()) {
          SimpleTask fmChildrenTask = new SimpleTask(normalFMsToStartContainer,startInput,HCALStates.STARTING,HCALStates.RUNNING,"Starting regular priority FM children");
          logger.info("[HCAL LVL1 " + functionManager.FMname +"]  StartAction: Adding normal FMs to startTask: ");
          PrintQRnames(normalFMsToStartContainer);
          startTaskSeq.addLast(fmChildrenTask);
        }
        // 2) LPM FM / TTCci FM should do a do-nothing transition to be enabled by the TA 
        if(!lpmOrTTCciFMtoStartContainer.isEmpty()) {
          SimpleTask lpmOrTTCciTask = new SimpleTask(lpmOrTTCciFMtoStartContainer,startInput,HCALStates.STARTING,HCALStates.RUNNING,"Starting LPM or TTCci child FMs");
          logger.info("[HCAL LVL1 " + functionManager.FMname +"]  StartAction Adding LPM or TTCci FMs to startTask: ");
          PrintQRnames(lpmOrTTCciFMtoStartContainer);
          startTaskSeq.addLast(lpmOrTTCciTask);
        }

        // 3) EvmTrig FM has to be absolute last
        if(!EvmTrigFMtoStartContainer.isEmpty()) {
          SimpleTask evmTrigTask = new SimpleTask(EvmTrigFMtoStartContainer,startInput,HCALStates.STARTING,HCALStates.RUNNING,"Starting EvmTrig child FMs");
          logger.info("[HCAL LVL1 " + functionManager.FMname +"]  Adding EvmTrig FMs to startTask: ");
          PrintQRnames(EvmTrigFMtoStartContainer);
          startTaskSeq.addLast(evmTrigTask);
        }
        logger.info("[HCAL LVL1 " + functionManager.FMname + "] executeTaskSequence.");
        functionManager.theStateNotificationHandler.executeTaskSequence(startTaskSeq);
      }


    // set action
    functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("STATE",new StringT(functionManager.getState().getStateString())));
    functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("ACTION_MSG",new StringT("startAction executed ...")));

    functionManager.RunWasStarted = true; // switch to enable writing to runInfo when run was destroyed

    logger.info("startAction executed ...");

  }

  public void runningAction(Object obj) throws UserActionException {

    if (obj instanceof StateEnteredEvent) {
      System.out.println("[HCAL LVL1 " + functionManager.FMname + "] Executing runningAction");
      logger.info("[HCAL LVL1 " + functionManager.FMname + "] Executing runningAction");

      // reset the non-async error state handling
      functionManager.ErrorState = false;

      // remember that this FM was in the running State
      functionManager.FMWasInRunningStateOnce = true;

      functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("STATE",new StringT(functionManager.getState().getStateString())));
      functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("ACTION_MSG",new StringT("running like hell ...")));

      logger.info("[HCAL LVL1 " + functionManager.FMname + "] runningAction executed ...");

    }
  }

  public void pauseAction(Object obj) throws UserActionException {

    if (obj instanceof StateEnteredEvent) {
      System.out.println("[HCAL LVL1 " + functionManager.FMname + "] Executing pauseAction");
      logger.info("[HCAL LVL1 " + functionManager.FMname + "] Executing pauseAction");

      // reset the non-async error state handling
      functionManager.ErrorState = false;

      // set action
      functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("STATE",new StringT("calculating state")));
      functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("ACTION_MSG",new StringT("pausing")));

      if (!functionManager.containerFMChildren.isEmpty()) {
        try {
          logger.debug("[HCAL LVL1 " + functionManager.FMname + "] Found FM childs - good! fireEvent: " + HCALInputs.PAUSE);
          functionManager.containerFMChildren.execute(HCALInputs.PAUSE);
        }
        catch (QualifiedResourceContainerException e) {
          String errMessage = "[HCAL LVL1 " + functionManager.FMname + "] Error! QualifiedResourceContainerException: sending: " + HCALInputs.PAUSE + " failed ...";
          logger.error(errMessage,e);
          functionManager.sendCMSError(errMessage);
          functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("STATE",new StringT("Error")));
          functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("ACTION_MSG",new StringT("oops - problems ...")));
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
      functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("STATE",new StringT(functionManager.getState().getStateString())));
      functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("ACTION_MSG",new StringT("pausingAction executed ...")));

      logger.debug("[HCAL LVL1 " + functionManager.FMname + "] pausingAction executed ...");

    }
  }

  public void resumeAction(Object obj) throws UserActionException {

    if (obj instanceof StateEnteredEvent) {
      System.out.println("[HCAL LVL1 " + functionManager.FMname + "] Executing resumeAction");
      logger.info("[HCAL LVL1 " + functionManager.FMname + "] Executing resumeAction");

      // reset the non-async error state handling
      functionManager.ErrorState = false;

      // set action
      functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("STATE",new StringT("calculating state")));
      functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("ACTION_MSG",new StringT("resuming")));

      if (!functionManager.containerFMChildren.isEmpty()) {
        try {
          logger.debug("[HCAL LVL1 " + functionManager.FMname + "] Found FM childs - good! fireEvent: " + HCALInputs.RESUME);
          functionManager.containerFMChildren.execute(HCALInputs.RESUME);
        }
        catch (QualifiedResourceContainerException e) {
          String errMessage = "[HCAL LVL1 " + functionManager.FMname + "] Error! QualifiedResourceContainerException: sending: " + HCALInputs.RESUME + " failed ...";
          logger.error(errMessage,e);
          functionManager.sendCMSError(errMessage);
          functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("STATE",new StringT("Error")));
          functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("ACTION_MSG",new StringT("oops - problems ...")));
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
      functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("STATE",new StringT(functionManager.getState().getStateString())));
      functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("ACTION_MSG",new StringT("resumeAction executed ...")));

      logger.debug("resumeAction executed ...");

    }
  }

  public void haltAction(Object obj) throws UserActionException {

    if (obj instanceof StateEnteredEvent) {
      System.out.println("[HCAL LVL1 " + functionManager.FMname + "] Executing haltAction");
      logger.info("[HCAL LVL1 " + functionManager.FMname + "] Executing haltAction");

      // reset the non-async error state handling
      functionManager.ErrorState = false;
      stopProgressThread = true;
      progress = 0.0;

      // set action
      functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("STATE",new StringT("calculating state")));
      functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("ACTION_MSG",new StringT("haaaalting ...")));

      publishRunInfoSummary();
      functionManager.HCALRunInfo = null; // make RunInfo ready for the next round of run info to store

      TaskSequence  haltTaskSeq = new TaskSequence(HCALStates.HALTING,HCALInputs.SETHALT);
      if (!functionManager.containerFMChildren.isEmpty()) {

        // define stop time
        StopTime = new Date();

        // Remember if FM was in running state once
        functionManager.FMWasInRunningStateOnce = false;

        // halt all FMs
        // Derive the FM containers from Active FMs in containerFMChildren
        List<QualifiedResource> fmChildrenList    = functionManager.containerFMChildren.getActiveQRList();
        List<QualifiedResource> ActiveEvmTrigList = functionManager.containerFMChildrenEvmTrig.getActiveQRList();
        List<QualifiedResource> ActiveTCDSLPMList = functionManager.containerFMTCDSLPM.getActiveQRList();

        List<FunctionManager> normalFMsToHaltList = new ArrayList<FunctionManager>();
        for(QualifiedResource qr : fmChildrenList){
          normalFMsToHaltList.add((FunctionManager)qr);
        }
        normalFMsToHaltList.removeAll(ActiveEvmTrigList);
        normalFMsToHaltList.removeAll(ActiveTCDSLPMList);
        QualifiedResourceContainer normalFMsToHaltContainer = new QualifiedResourceContainer(normalFMsToHaltList);
        QualifiedResourceContainer EvmTrigFMToHaltContainer = new QualifiedResourceContainer(ActiveEvmTrigList);
        QualifiedResourceContainer TCDSLPMToHaltContainer   = new QualifiedResourceContainer(ActiveTCDSLPMList);

        // Schedule the tasks
        haltTaskSeq = new TaskSequence(HCALStates.HALTING,HCALInputs.SETHALT);
	// Allow halt to happen during the exiting state
	if ( functionManager.getState().equals(HCALStates.EXITING) )  {
          haltTaskSeq = new TaskSequence(HCALStates.EXITING,HCALInputs.SETHALT);
          functionManager.getHCALparameterSet().put(new FunctionManagerParameter<BooleanT>("EXIT", new BooleanT(true)));
	}
        // 1) EvmTrig (TA) FM
        if(!EvmTrigFMToHaltContainer.isEmpty()) {
          SimpleTask evmTrigTask = new SimpleTask(EvmTrigFMToHaltContainer,HCALInputs.HALT,HCALStates.HALTING,HCALStates.HALTED,"LV1_HALT_EVMTRIG_FM");
          haltTaskSeq.addLast(evmTrigTask);
          logger.info("[HCAL LVL1 " + functionManager.FMname +"]  Adding EvmTrig FMs to haltTask: ");
          PrintQRnames(EvmTrigFMToHaltContainer);
        }
        
        // 2) TCDSLPM FM
        if(!TCDSLPMToHaltContainer.isEmpty()) {
          SimpleTask tcdslpmTask = new SimpleTask(TCDSLPMToHaltContainer,HCALInputs.HALT,HCALStates.HALTING,HCALStates.HALTED,"LV1_HALT_TCDS_FM");
          haltTaskSeq.addLast(tcdslpmTask);
          logger.info("[HCAL LVL1 " + functionManager.FMname +"]  Adding TCDSLPM FMs to haltTask: ");
          PrintQRnames(TCDSLPMToHaltContainer);
        }
        // 3) Everyone else besides L2_Laser and EvmTrig FMs in parallel
        if(!normalFMsToHaltContainer.isEmpty()) {
          SimpleTask fmChildrenTask = new SimpleTask(normalFMsToHaltContainer,HCALInputs.HALT,HCALStates.HALTING,HCALStates.HALTED,"LV1_HALT_NORMAL_FM");
          haltTaskSeq.addLast(fmChildrenTask);
          logger.info("[HCAL LVL1 " + functionManager.FMname +"]  Adding other LV2 FMs to haltTask: ");
          PrintQRnames(normalFMsToHaltContainer);
        }
        logger.warn("[SethLog HCAL LVL1 " + functionManager.FMname + "] executeTaskSequence.");

        functionManager.theStateNotificationHandler.executeTaskSequence(haltTaskSeq);
      }
      else {
        if (!functionManager.ErrorState) {
          logger.debug("[HCAL LVL1 " + functionManager.FMname + "] fireEvent: " + HCALInputs.SETHALT);
          functionManager.fireEvent(HCALInputs.SETHALT);
        }
      }

      //All EmptyFMs should be back after halted.
      VectorT<StringT> EmptyFMs = new VectorT<StringT>();
      functionManager.getHCALparameterSet().put(new FunctionManagerParameter<VectorT<StringT>>("EMPTY_FMS",EmptyFMs));

      // set action
      functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("STATE",new StringT(functionManager.getState().getStateString())));
      functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("ACTION_MSG",new StringT("haltAction executed ...")));

      logger.debug("[HCAL LVL1 " + functionManager.FMname + "] haltAction executed ...");
    }
  }

  public void coldResetAction(Object obj) throws UserActionException {
    if (obj instanceof StateEnteredEvent) {
      System.out.println("[HCAL LVL1 " + functionManager.FMname + "] Executing coldResetAction");
      logger.info("[HCAL LVL1 " + functionManager.FMname + "] Executing coldResetAction");

      // reset the non-async error state handling
      functionManager.ErrorState = false;

      // set action
      functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("STATE",new StringT("calculating state")));
      functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("ACTION_MSG",new StringT("brrr - cold resetting ...")));

      publishRunInfoSummary();
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
                functionManager.goToError(errMessage,e);
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
      functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("STATE",new StringT(functionManager.getState().getStateString())));
      functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("ACTION_MSG",new StringT("coldResetAction executed ...")));

      logger.debug("[HCAL LVL1 " + functionManager.FMname + "] coldResetAction executed ...");
    }
  }

  public void stoppingAction(Object obj) throws UserActionException {

    if (obj instanceof StateNotification) {

      // triggered by State Notification from child resource
      logger.warn("[SethLog HCAL LVL1 " + functionManager.FMname + "] Received state notification inside stoppingAction(); computeNewState()");
      computeNewState((StateNotification) obj);
      return;

    }
    else if (obj instanceof StateEnteredEvent) {
      System.out.println("[HCAL LVL1 " + functionManager.FMname + "] Executing stoppingAction");
      logger.info("[HCAL LVL1 " + functionManager.FMname + "] Executing stoppingAction");

      // reset the non-async error state handling
      functionManager.ErrorState = false;

      // set action
      functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("STATE",new StringT("calculating state")));
      functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("ACTION_MSG",new StringT("stopping")));
      //logger.info("[JohnLog] LVL1 about to call publishRunInfoSummary");
      logger.info("[HCAL LVL1 " + functionManager.FMname + "] LVL1 about to call publishRunInfoSummary");
      publishRunInfoSummary();
      functionManager.HCALRunInfo = null; // make RunInfo ready for the next round of run info to store

      //if (!functionManager.containerFMChildren.isEmpty()) {

      //  // define stop time
      //  StopTime = new Date();

      //  // stop all FMs
      //  Iterator it = functionManager.containerFMChildren.getQualifiedResourceList().iterator();
      //  //logger.warn("[SethLog HCAL LVL1 " + functionManager.FMname + "] ContainerFMChildren has size: " + functionManager.containerFMChildren.getQualifiedResourceList().size());
      //  FunctionManager fmChild = null;
      //  while (it.hasNext()) {
      //    fmChild = (FunctionManager) it.next();
      //    if (fmChild.isActive()) {
      //      if (! (fmChild.refreshState().toString().equals(HCALStates.STOPPING.toString()) || fmChild.refreshState().toString().equals(HCALStates.CONFIGURED.toString())) ) {
      //        try {
      //          fmChild.execute(HCALInputs.STOP);
      //        }
      //        catch (CommandException e) {
      //          String errMessage = "[HCAL LVL1 " + functionManager.FMname + "] Error! CommandException: sending: " + HCALInputs.STOP + " during stoppingAction() failed ...";
      //          logger.error(errMessage,e);
      //          functionManager.sendCMSError(errMessage);
      //          functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("STATE",new StringT("Error")));
      //          functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("ACTION_MSG",new StringT("oops - problems ...")));
      //          if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; return;}
      //        }
      //      }
      //      else {
      //        logger.debug("[HCAL LVL1 " + functionManager.FMname + "] This FM is already \"Configured\".\nWill sent not send" + HCALInputs.STOP + " to FM named: " + fmChild.getResource().getName().toString() + "\nThe role is: " + fmChild.getResource().getRole().toString() + "\nAnd the URI is: " + fmChild.getResource().getURI().toString());
      //      }
      //    }
      //  }


      //}
      //else {
      //  if (!functionManager.ErrorState) {
      //    logger.debug("[HCAL LVL1 " + functionManager.FMname + "] fireEvent: " + HCALInputs.SETCONFIGURE);
      //    if (!functionManager.getState().getStateString().equals(HCALStates.CONFIGURED.toString())) {
      //      functionManager.fireEvent(HCALInputs.SETCONFIGURE);
      //    }
      //  }
      //}
      TaskSequence LVL1StopTaskSeq = new TaskSequence(HCALStates.STOPPING,HCALInputs.SETCONFIGURE);
      // 1) Stop the EVMTrig FM first
      if(!functionManager.containerFMEvmTrig.isEmpty()){
        SimpleTask EvmFMstopTask = new SimpleTask(functionManager.containerFMEvmTrig,HCALInputs.STOP,HCALStates.STOPPING,HCALStates.CONFIGURED,"Stopping EVMtrig FM");
        logger.info("[HCAL LVL1 " + functionManager.FMname +"]  StoppingAction: Adding EVM trig FMs to stopTask: ");
        PrintQRnames(functionManager.containerFMEvmTrig);
        LVL1StopTaskSeq.addLast(EvmFMstopTask);
      }
      // 2) Stop all other LV2s in parallel 
      if(!functionManager.containerFMChildrenNormal.isEmpty()){
        SimpleTask normalFMstopTask = new SimpleTask(functionManager.containerFMChildrenNormal,HCALInputs.STOP,HCALStates.STOPPING,HCALStates.CONFIGURED,"Stopping normal FMs");
        logger.info("[HCAL LVL1 " + functionManager.FMname +"]  StoppingAction: Adding non-EVM trig FMs to stopTask: ");
        PrintQRnames(functionManager.containerFMChildrenNormal);
        LVL1StopTaskSeq.addLast(normalFMstopTask);
      }

      logger.info("[HCAL LVL1 " + functionManager.FMname + "] executeTaskSequence.");
      functionManager.theStateNotificationHandler.executeTaskSequence(LVL1StopTaskSeq);

      // set actions
      functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("STATE",new StringT(functionManager.getState().getStateString())));
      functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("ACTION_MSG",new StringT("stoppingAction executed ...")));

      logger.debug("[HCAL LVL1 " + functionManager.FMname + "] stoppingAction executed ...");

    }
  }

  public void preparingTTSTestModeAction(Object obj) throws UserActionException {

    if (obj instanceof StateEnteredEvent) {
      System.out.println("[HCAL LVL1 " + functionManager.FMname + "] Executing preparingTestModeAction");
      logger.info("[HCAL LVL1 " + functionManager.FMname + "] Executing preparingTestModeAction");

      // reset the non-async error state handling
      functionManager.ErrorState = false;

      // set actions
      functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("STATE",new StringT("calculating state")));
      functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("ACTION_MSG",new StringT("preparingTestMode")));

      if (!functionManager.containerFMChildren.isEmpty()) {
        try {
          logger.debug("[HCAL LVL1 " + functionManager.FMname + "] Found FM childs - good! fireEvent: " + HCALInputs.TTSTEST_MODE);
          functionManager.containerFMChildren.execute(HCALInputs.TTSTEST_MODE);
        }
        catch (QualifiedResourceContainerException e) {
          String errMessage = "[HCAL LVL1 " + functionManager.FMname + "] Error! QualifiedResourceContainerException: sending: " + HCALInputs.TTSTEST_MODE + " failed ...";
          logger.error(errMessage,e);
          functionManager.sendCMSError(errMessage);
          functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("STATE",new StringT("Error")));
          functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("ACTION_MSG",new StringT("oops - problems ...")));
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
      functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("STATE",new StringT(functionManager.getState().getStateString())));
      functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("ACTION_MSG",new StringT("preparingTestModeAction executed ...")));

      logger.debug("[HCAL LVL1 " + functionManager.FMname + "] preparingTestModeAction executed ...");
    }
  }

  public void testingTTSAction(Object obj) throws UserActionException {

    if (obj instanceof StateEnteredEvent) {
      System.out.println("[HCAL LVL1 " + functionManager.FMname + "] Executing testingTTSAction");
      logger.info("[HCAL LVL1 " + functionManager.FMname + "] Executing testingTTSAction");

      // reset the non-async error state handling
      functionManager.ErrorState = false;

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
        String errMsg = "[HCAL LVL1 " + functionManager.FMname + "] Error! No parameters given with TestTTS command: testingTTSAction";
        logger.error(errMsg);
        functionManager.sendCMSError(errMsg);
        if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; return;}

      }
      else {

        logger.debug("[HCAL LVL1 " + functionManager.FMname + "] Getting parameters for sTTS test now ...");

        // get the paramters from the command
        FedId = ((IntegerT)parameterSet.get("TTS_TEST_FED_ID").getValue()).getInteger();
        mode = ((StringT)parameterSet.get("TTS_TEST_MODE").getValue()).getString();
        pattern = ((StringT)parameterSet.get("TTS_TEST_PATTERN").getValue()).getString();
        cycles = ((IntegerT)parameterSet.get("TTS_TEST_SEQUENCE_REPEAT").getValue()).getInteger();
      }

      // prepare parameters to be passed to level 2
      ParameterSet<CommandParameter> pSet = new ParameterSet<CommandParameter>();
      pSet.put(new CommandParameter<IntegerT>("TTS_TEST_FED_ID", new IntegerT(FedId)));
      pSet.put(new CommandParameter<StringT>("TTS_TEST_MODE", new StringT(mode)));
      pSet.put(new CommandParameter<StringT>("TTS_TEST_PATTERN", new StringT(pattern)));
      pSet.put(new CommandParameter<IntegerT>("TTS_TEST_SEQUENCE_REPEAT", new IntegerT(cycles)));

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
          functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("STATE",new StringT("Error")));
          functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("ACTION_MSG",new StringT("oops - problems ...")));
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
      functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("STATE",new StringT(functionManager.getState().getStateString())));
      functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("ACTION_MSG",new StringT("testingTTSAction executed ...")));
      logger.debug("[HCAL LVL1 " + functionManager.FMname + "] testingTTSAction executed ...");
    }
  }
  protected class ProgressThread extends Thread {
    protected HCALFunctionManager functionManager = null;
    RCMSLogger logger = null;
    double lvl2progress = 0.0;

    public ProgressThread(HCALFunctionManager parentFunctionManager) {
      this.logger = new RCMSLogger(HCALFunctionManager.class);
      this.functionManager = parentFunctionManager;
      logger.info("Done constructing ProgressThread " + functionManager.FMname + ".");
    }

    public void run() {
      stopProgressThread = false;
      progress = 0.0;
      while ( stopProgressThread == false && functionManager.isDestroyed() == false && Math.abs(progress-1.0)>0.001) {

        Iterator it = functionManager.containerFMChildren.getQualifiedResourceList().iterator();
        VectorT<StringT> EmptyFMs  = (VectorT<StringT>)functionManager.getHCALparameterSet().get("EMPTY_FMS").getValue();
        progress = 0.0;
        while (it.hasNext()) {
          FunctionManager childFM = (FunctionManager) it.next();
          if (childFM.isInitialized() && !EmptyFMs.contains(new StringT(childFM.getName()))) {
            ParameterSet<FunctionManagerParameter> lvl2pars;
            try {
              lvl2pars = childFM.getParameter(functionManager.getHCALparameterSet());
            }
            catch (ParameterServiceException e) {
              logger.warn("[HCAL " + functionManager.FMname + "] Could not update parameters for FM client: " + childFM.getResource().getName() + " The exception is:", e);
              return;
            }
            lvl2progress = ((DoubleT)lvl2pars.get("PROGRESS").getValue()).getDouble();
            if(lvl2progress>0){
              progress += lvl2progress;
              logger.debug("[JohnLogProgress] " + functionManager.FMname + ": From "+childFM.getName()+", got progress = "+lvl2progress);
            }
          }
        }
        progress = progress/(nChildren.doubleValue());
        logger.debug("[JohnLogProgress] " + functionManager.FMname + ": Total progress =" + progress + " from nChildren = "+ nChildren);
        functionManager.getHCALparameterSet().put(new FunctionManagerParameter<DoubleT>("PROGRESS", new DoubleT(progress)));

        // delay between polls
        try { Thread.sleep(1000); }
        catch (Exception ignored) { 
	  logger.warn("JohnDebug: Got an exception during progress thread polling. Exception message: " + ignored.getMessage());
	  return;
	}
      }

      // stop the Monitor watchdog thread
      logger.info("[HCAL " + functionManager.FMname + "]: Total progress is " + progress+ ". Done configuring. Stopping ProgressThread.");
      logger.debug("[HCAL " + functionManager.FMname + "] ... stopping ProgressThread.");
    }
  }

  public void exitAction(Object obj) throws UserActionException {

    if (obj instanceof StateEnteredEvent) {
      System.out.println("[HCAL LVL1 " + functionManager.FMname + "] Executing exitAction");
      logger.info("[HCAL LVL1 " + functionManager.FMname + "] Executing exitAction");

      haltAction(obj);
      functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("STATE",new StringT("EXITING")));
      logger.debug("[JohnLog " + functionManager.FMname + "] exitAction executed ...");
    }
  }

  public void SetLocalDAQinfospace(){
    List<QualifiedResource> EvmTrigFMlist = functionManager.containerFMEvmTrig.getActiveQRList();
    Boolean isUsingLocalDAQ = false;
    for (QualifiedResource qr : EvmTrigFMlist){
      if (qr.getName().contains("localDAQ")){
        isUsingLocalDAQ = true;
        logger.info("[HCAL LVL1 "+functionManager.FMname+"] SetLocalDAQinfospace: We have a localDAQ FM!");
      }
    }
    // Set infospace of first LV2 with DTCReadout
    if (isUsingLocalDAQ){
      List<QualifiedResource> level2list    = functionManager.containerFMChildrenNormal.getActiveQRList();
      Boolean foundDTCReadout = false;
      // getQualifiedGroup() returns the Group in which the FM is created with. ie.: 
      // ((FunctionManager)level2).getQualifiedGroup() still returns the LV1 QG
      QualifiedGroup level1group = functionManager.getQualifiedGroup();
      for (QualifiedResource level2 : level2list){
        if (!foundDTCReadout){
          try {
            //Get the light LV2 group from rs connector of the group
            Group lv2Group = level1group.rs.retrieveLightGroup(level2.getResource());
            logger.debug("[HCAL LVL1 "+functionManager.FMname+"] SetLocalDAQinfospace: Printing this LV2: "+level2.getName()+"'s Group after getting from DB: "+lv2Group.toString());
            //Create a QG from the group
            //KKH: XdaqApplications lost their class info, i.e. xdaq.getApplication()=null;
            QualifiedGroup lv2QG = new QualifiedGroup(lv2Group);
       
            List<QualifiedResource> xdaqList = lv2QG.seekQualifiedResourcesOfType(new XdaqApplication());
            for (QualifiedResource qr: xdaqList){
              XdaqApplication xdaq = (XdaqApplication)qr;
              logger.info("[HCAL LVL1 "+functionManager.FMname+"] SetLocalDAQinfospace: This LV2: "+level2.getName()+"   has   xdaq named:"+xdaq.getName());
              if(xdaq.getName().contains("hcal::DTCReadout")){
                logger.info("[HCAL LVL1 "+functionManager.FMname+"] SetLocalDAQinfospace: found a DTCReadout in "+level2.getName());
                try{
                  XDAQParameter pam = null;
                  XdaqApplication DTCReadout = xdaq ; 
                  pam = DTCReadout.getXDAQParameter();
                  pam.select(new String[]{"TriggerBlockDestClassname","TriggerBlockDestInstance","PollingReadout"});
                  pam.setValue("TriggerBlockDestClassname","DummyTriggerAdapter");
                  pam.setValue("TriggerBlockDestInstance","0");
                  pam.setValue("PollingReadout","true");
                  pam.send();
                  logger.info("[HCAL LVL1 "+functionManager.FMname+"] SetLocalDAQinfospace: Just set "+DTCReadout.getName()+" to send trigger blocks");
                  foundDTCReadout=true;
                }
                catch (XDAQException e) {
                  String errMessage="[HCAL LVL1 "+functionManager.FMname+"] SetLocalDAQinfospace: Failed to get infospace from "+level2.getName();
                  functionManager.goToError(errMessage,e);
                }
              }
            }
          }
          catch (DBConnectorException ex) {
            logger.error("[HCAL " + functionManager.FMname + "]: Got a DBConnectorException when trying to retrieve level2s' children resources: " + ex.getMessage());
          }
        }
      }
    }
  }
}
