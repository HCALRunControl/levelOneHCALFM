package rcms.fm.app.level1;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.TimeZone;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.lang.Integer;
import java.util.Iterator;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.Vector;
import java.lang.Double;
import java.util.Iterator;
import java.math.BigInteger;
import java.net.URI;
import java.net.URL;
import java.net.URISyntaxException;
import java.util.Iterator;
import java.text.DecimalFormat;
import java.util.Random;

import java.io.BufferedReader;
import java.io.StringReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Scanner;
import java.util.regex.MatchResult;
import java.io.StringReader;
import java.io.StringWriter;

import net.hep.cms.xdaqctl.XDAQException;
import net.hep.cms.xdaqctl.XDAQTimeoutException;
import net.hep.cms.xdaqctl.XDAQMessageException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.Text;
import org.w3c.dom.DOMException;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.soap.SOAPException;

import rcms.fm.context.RCMSConstants;
import rcms.fm.fw.StateEnteredEvent;
import rcms.fm.fw.parameter.Parameter;
import rcms.fm.fw.parameter.CommandParameter;
import rcms.fm.fw.parameter.FunctionManagerParameter;
import rcms.fm.fw.parameter.ParameterSet;
import rcms.fm.fw.parameter.type.ParameterType;
import rcms.fm.fw.service.parameter.ParameterServiceException;
import rcms.fm.fw.parameter.type.IntegerT;
import rcms.fm.fw.parameter.type.StringT;
import rcms.fm.fw.parameter.type.DoubleT;
import rcms.fm.fw.parameter.type.DateT;
import rcms.fm.fw.parameter.type.VectorT;
import rcms.fm.fw.parameter.type.BooleanT;
import rcms.fm.fw.parameter.ParameterException;
import rcms.statemachine.definition.Input;
import rcms.fm.fw.user.UserEvent;
import rcms.fm.fw.user.UserActionException;
import rcms.fm.fw.user.UserStateNotificationHandler;
import rcms.fm.fw.user.UserEventHandler;
import rcms.resourceservice.db.Group;
import rcms.resourceservice.db.resource.Resource;
import rcms.common.db.DBConnectorException;
import rcms.fm.resource.QualifiedGroup;
import rcms.fm.resource.QualifiedResource;
import rcms.fm.resource.QualifiedResourceContainer;
import rcms.fm.resource.QualifiedResourceContainerException;
import rcms.fm.resource.qualifiedresource.XdaqApplication;
import rcms.fm.resource.qualifiedresource.XdaqApplicationContainer;
import rcms.fm.resource.qualifiedresource.XdaqExecutive;
import rcms.fm.resource.qualifiedresource.JobControl;
import rcms.fm.resource.qualifiedresource.FunctionManager;
import rcms.resourceservice.db.resource.fm.FunctionManagerResource;
import rcms.resourceservice.db.resource.config.ConfigProperty;
import rcms.resourceservice.db.resource.ResourceException;
import rcms.stateFormat.StateNotification;
import rcms.util.logger.RCMSLogger;
import rcms.util.logsession.LogSessionException;
import rcms.xdaqctl.XDAQParameter;
import rcms.xdaqctl.XDAQMessage;
import rcms.utilities.runinfo.RunInfo;
import rcms.utilities.runinfo.RunInfoConnectorIF;
import rcms.utilities.runinfo.RunInfoException;
import rcms.utilities.runinfo.RunNumberData;
import rcms.utilities.runinfo.RunSequenceNumber;
import rcms.utilities.fm.task.CompositeTask;
import rcms.utilities.fm.task.SimpleTask;
import rcms.utilities.fm.task.TaskSequence;
import rcms.fm.resource.CommandException;
import rcms.util.logsession.LogSessionConnector;
import rcms.util.logsession.LogSessionException;

import net.hep.cms.xdaqctl.WSESubscription;
import net.hep.cms.xdaqctl.XDAQMessageException;
import net.hep.cms.xdaqctl.XDAQTimeoutException;
import net.hep.cms.xdaqctl.XMASMessage;
import net.hep.cms.xdaqctl.xdata.FlashList;
import net.hep.cms.xdaqctl.xdata.SimpleItem;
import net.hep.cms.xdaqctl.xdata.XDataType;

/**
 * Event Handler base class for HCAL Function Managers
 * @maintainer John Hakala
 */

public class HCALEventHandler extends UserEventHandler {

  public static final String XDAQ_NS = "urn:xdaq-soap:3.0";

  static RCMSLogger logger = new RCMSLogger(HCALEventHandler.class);

  protected HCALFunctionManager functionManager = null;

  public DocumentBuilder docBuilder;

  public QualifiedGroup qualifiedGroup = null;

  public HCALxmlHandler xmlHandler = null;


  public Integer Sid = 0;
  public String  GlobalConfKey = "";
  public String  RunType = "";
  public String  RunKey = "";
  public String  CachedRunKey = "";
  public String  TpgKey = "";
  public String  CachedTpgKey = "";
  public Integer TriggersToTake = 0;
  public Integer RunSeqNumber = 0;
  public String  FedEnableMask = "";

  // HCAL CfgScript which will be sent to the HCAL supervisor - kind of global CfgScript to which a local CfgScript, RBXManager, etc. definition could be added
  String configString = "";
  String ConfigDoc = "";
  String FullCfgScript = "not set";

  // TTCciControl which will be sent to the TTCci - kind of global TTCciControl to which a local TTCciControl, etc. definition could be added
  String FullTTCciControlSequence = "not set";

  // LTCControl which will be sent to the LTC - kind of global LTCControl to which a local LTCControl, etc. definition could be added
  String FullLTCControlSequence = "not set";

  // TCDS configuration documents -- LPMControl is sent to the LPM, TCDSControl is sent to the ICI's, and PIControl is sent to the PI's
  String FullTCDSControlSequence = "not set";
  String FullLPMControlSequence = "not set";
  String FullPIControlSequence = "not set";

  // Switch to select primary or secondary TCDS system--should be true until we get a secondary TCDS system
  public boolean UsePrimaryTCDS = true;

  // Switch for whether a TriggerAdapter is used in the configuration. Default is false, as in global runs
  //public Boolean HandleTriggerAdapter = false;

  // Switch to be able to ignore any errors which would cause the FM state machine to end in an error state
  public String TestMode = "off";

  // Connector to log session db, used to create session identifiers
  public LogSessionConnector logSessionConnector;

  // Start and stopping time of a run // TODO these are broken
  public Date StartTime = null;
  public Date StopTime = null;

  // Unique session Id needed for getting "official" run numbers
  public Integer sessionId = 0;

  // Toggle the usage of offical run numbers
  public boolean OfficialRunNumbers = false;
  public boolean RunInfoPublish = false;
  public boolean RunInfoPublishfromXDAQ = false;

  // Sequence Name for getting a run sequence number
  public String RunSequenceName = "HCAL test";

  // Completion status incorporates also possible child FM
  public Double completion = -1.0;
  public Double localcompletion = -1.0;

  // Events taken for local runs
  public Integer eventstaken = -1;
  public Integer localeventstaken = -1;

  // Threads and switches for handling monitoring, e.g. of the completion status
  private List<Thread> MonitorThreadList = new ArrayList<Thread>();
  public boolean stopMonitorThread = false;
  private List<Thread> HCALSupervisorWatchThreadList = new ArrayList<Thread>();
  public boolean stopHCALSupervisorWatchThread = false;
  public boolean HCALSuperVisorIsOK = false;
  public boolean AllButHCALSuperVisorIsOK = false;
  private List<Thread> TriggerAdapterWatchThreadList = new ArrayList<Thread>();
  public boolean stopTriggerAdapterWatchThread = false;
  public boolean NotifiedControlledFMs = false;

  // Switch which indicates whether "special" function managers are controlled, e.g. HCAL_Master or RCT_Master
  protected boolean SpecialFMsAreControlled = false;

  // Switch to enable the AsyncEnable feature of the HCAL supervisor
  protected boolean HCALSupervisorAsyncEnable = false;

  // Switch to enable multiple hcalSupervisors controlled by means of one FM plus a TriggerAdapter
  protected boolean LocalMultiPartitionReadOut = false;

  // Switch to perform specific actions in case of a re-configuring after a LHC/internal CMS clock change
  public boolean ClockChanged = false;

  // Switch to disable the "Recover" statemachine behavior and instead replace it with doing a "Reset" behavior
  public boolean UseResetForRecover = true;

  // String which stores an error retrieved from the hcalSupervisor. 
  public String SupervisorError = "";

  // The name of the HCAL CFG zero suppression, VdM snippets, etc.
  protected String ZeroSuppressionSnippetName="/HTR/ZeroSuppression.cfg/pro";
  protected String SpecialZeroSuppressionSnippetName="/HTR/SpecialZeroSuppression.cfg/pro";
  protected String VdMSnippetName="/LUMI/VdM.cfg/pro";

  // CfgCVSBasePath = Base path to read CVS Cfgs
  public String CfgCVSBasePath ="";

  // XMAS related stuff
  protected String WSE_FILTER = "empty";

  public HCALEventHandler() throws rcms.fm.fw.EventHandlerException {

    // Let's register the StateEnteredEvent triggered when the FSM enters in a new state.
    subscribeForEvents(StateEnteredEvent.class);
    //subscribeForEvents(UserEvent.class);

    addAction(HCALStates.INITIALIZING,            "initAction");
    addAction(HCALStates.CONFIGURING,             "configureAction");
    addAction(HCALStates.HALTING,                 "haltAction");
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

    // Get the setting of whether RunInfo should be published from the userXML
    {
      String doRunInfoPublish =  "";
      try{
        if(!xmlHandler.getHCALuserXML().equals(null) && !xmlHandler.getHCALuserXML().getElementsByTagName("RunInfoPublish").equals(null) && xmlHandler.getHCALuserXML().getElementsByTagName("RunInfoPublish").getLength() == 1 ) { 
          
          doRunInfoPublish = xmlHandler.getHCALuserXML().getElementsByTagName("RunInfoPublish").item(0).getTextContent();
        }
      }
      catch (UserActionException e) { 
        logger.warn(e.getMessage());
      }
      if (doRunInfoPublish.equals("true_butwithnoRunInfoFromXDAQ")) {
        logger.warn("[HCAL base] This session: " + sessionId.toString() + " will be published to the RunInfo database.");
        RunInfoPublish = true;
      }
      if (doRunInfoPublish.equals("true")) {
        logger.warn("[HCAL base] This session: " + sessionId.toString() + " will be published to the RunInfo database.");
        RunInfoPublish = true;
        RunInfoPublishfromXDAQ = true;
      }
    }

    // Get the setting for whether official run numbers should be used from the userXML
    {
      String useOfficialRunNumbers = "";
      try {
        if ( !xmlHandler.getHCALuserXML().equals(null) && !xmlHandler.getHCALuserXML().getElementsByTagName("OfficialRunNumbers").equals(null) && xmlHandler.getHCALuserXML().getElementsByTagName("OfficialRunNumbers").getLength() == 1  ) {
         useOfficialRunNumbers = xmlHandler.getHCALuserXML().getElementsByTagName("OfficialRunNumbers").item(0).getTextContent();
        } 
      }
      catch (UserActionException e) { 
        logger.warn(e.getMessage());
      }
      if (useOfficialRunNumbers.equals("true_butwithnoRunInfoFromXDAQ")) {
        logger.warn("[HCAL base] using offical run numbers for this session: " + sessionId.toString() + " (publishing to RunInfo is therefore switched on too)");
        OfficialRunNumbers = true;
        RunInfoPublish = true;
      }
      if (useOfficialRunNumbers.equals("true")) {
        logger.warn("[HCAL base] using offical run numbers for this session: " + sessionId.toString() + " (publishing to RunInfo and the info from the RunInfo XDAQ application is therefore switched on too)");
        OfficialRunNumbers = true;
        RunInfoPublish = true;
        RunInfoPublishfromXDAQ = true;
      }
    }

    // Get the RunSequenceName from the userXML
    {
      String NewRunSequenceName = "";
      try {
        NewRunSequenceName = xmlHandler.getHCALuserXMLelementContent("RunSequenceName");
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
      functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.SEQ_NAME, new StringT(""+RunSequenceName)));
    }

    // Check if TestMode has been specified in the userXML
    {
      String useTestMode = "";
      try {
          useTestMode = xmlHandler.getHCALuserXMLelementContent("TestMode");
      }
      catch (UserActionException e) { 
        logger.warn(e.getMessage());
      }
      if (!useTestMode.equals("")) {
        TestMode = useTestMode;
        logger.warn("[HCAL base] TestMode: " + TestMode + " enabled - ignoring anything which would set the state machine to an error state!");
      }
    }

//    // XMAS initalization and subscription
//    // TODO: is this useful?
//    {
//      functionManager.RunInfoFlashlistName = GetUserXMLElement("RunInfoFlashlistName");
//      if (!functionManager.RunInfoFlashlistName.equals("")) {
//        logger.info("[HCAL base] This FM will try to subscribe to a WSE to retrieve a flashlist named: " + functionManager.RunInfoFlashlistName);
//
//        functionManager.XMASMonitoringEnabled = true;
//
//        functionManager.wseMap = new HashMap<String,WSESubscription>();
//
//        WSE_FILTER = "//xmas:sample[(@flashlist='urn:xdaq-flashlist:"+functionManager.RunInfoFlashlistName+"')]";
//
//        // Get WSEs
//        functionManager.wseList = functionManager.getQualifiedGroup().seekQualifiedResourcesOfRole("WSE");
//
//        logger.info("[HCAL base] WSE subscription: feedback to "+"http://"+qualifiedGroup.getFMURI().getHost()+":"+ qualifiedGroup.getFMURI().getPort() + "/" + RCMSConstants.MONITOR_SERVLET_SUFFIX);
//
//    //    // Subscription to the WSEs
//        for (QualifiedResource qr : functionManager.wseList) {
//          try {
//            logger.info("[HCAL base] Start WSE subscription to " + qr.getURI().toASCIIString()+"  --->  "+"http://"+qualifiedGroup.getFMURI().getHost() + ":" + qualifiedGroup.getFMURI().getPort() + "/" + RCMSConstants.MONITOR_SERVLET_SUFFIX);
//            functionManager.wsSubscription = new WSESubscription("http://"+qualifiedGroup.getFMURI().getHost() + ":" + qualifiedGroup.getFMURI().getPort() + "/" + RCMSConstants.MONITOR_SERVLET_SUFFIX,qr.getURI().toASCIIString());
//            logger.info("[HCAL base] WSE subscription, set filter to  " + WSE_FILTER);
//            functionManager.wsSubscription.setFilter(WSE_FILTER);
//
//            functionManager.wsSubscription.setExpires("PT20S"); // PT20S for 20sec or PT10M for 10min etc.
//
//            try {
//              logger.debug("[HCAL base] wsSubscription.subscribe() called now ...");
//              functionManager.wsSubscription.subscribe();
//            }
//            catch (XDAQTimeoutException e) {
//              String errMessage = "[HCAL base] Error! XDAQTimeoutException when subscribing to WSEs ...\n Perhaps this application is dead!?";
//              logger.error(errMessage,e);
//              functionManager.sendCMSError(errMessage);
//            }
//            catch (Exception e){
//              String errMessage = "[HCAL base] Error! Exception when subscribing to WSEs ...";
//              logger.error(errMessage,e);
//              functionManager.sendCMSError(errMessage);
//            }
//
//          }
//          catch (XDAQMessageException e) {
//            String errMessage = "[HCAL base] Error! XDAQMessageException when subscribing to WSEs ...";
//            logger.error(errMessage,e);
//            functionManager.sendCMSError(errMessage);
//          }
//
//          // Store subscriptions for later use e.g. unsubscriptions, etc.
//          functionManager.wseMap.put(qr.getName(),functionManager.wsSubscription);
//
//          logger.info("[HCAL base] WSE subscription done successfully for: " + qr.getURI().toASCIIString());
//        }
//      }
//    }


    // Check if the userXML specifies whether the AsyncEnable feature should be used
    {
      String useHCALSupervisorAsyncEnable = "";
      try { useHCALSupervisorAsyncEnable = xmlHandler.getHCALuserXMLelementContent("HCALSupervisorAsyncEnable"); }
      catch (UserActionException e) { logger.warn(e.getMessage()); }
      if (!useHCALSupervisorAsyncEnable.equals("")) {
        HCALSupervisorAsyncEnable = true;
      }
      if (HCALSupervisorAsyncEnable) {
        logger.warn("[HCAL base] HCALSupervisorAsyncEnable: " + HCALSupervisorAsyncEnable + " - this means \"AsyncEnable\" is sent to the HCAL supervisor instead of \"Enable\"");
      }
      else {
        logger.warn("[HCAL base] HCALSupervisorAsyncEnable: " + HCALSupervisorAsyncEnable + " - this means \"Enable\" is sent to the HCAL supervisor instead of \"AsyncEnable\"");
      }
    }


    // Check if the userXML specifies whether ATCP connections should be stopped
    {
      String useStopATCP = "";
      try {
          useStopATCP = xmlHandler.getHCALuserXMLelementContent("StopATCP");
      }
      catch (UserActionException e) { 
        logger.warn(e.getMessage());
      }
      if (!useStopATCP.equals("")) {
        functionManager.StopATCP = true;
      }
      if (functionManager.StopATCP) {
        logger.warn("[HCAL base] StopATCP: " + functionManager.StopATCP + " - this means ATCP XDAQ apps are operated normally, i.e. started and stopped in the corresponding transitions.");
      }
      else {
        logger.warn("[HCAL base] StopATCP: " + functionManager.StopATCP + " - this means ATCP XDAQ apps are started once during the starting transition but never ever stopped in a run config.");
      }
    }

    // Check if the userXML specifies that FEDStreamer applications should be stopped
    {
      String useStopFEDStreamer = "";
      try { useStopFEDStreamer=xmlHandler.getHCALuserXMLelementContent("StopFEDStreamer"); }
      catch (UserActionException e) { logger.warn(e.getMessage()); }
      if (!useStopFEDStreamer.equals("")) {
        functionManager.StopATCP = true;
      }
      if (functionManager.StopFEDStreamer) {
        logger.warn("[HCAL base] StopFEDStreamer: " + functionManager.StopFEDStreamer + " - this means FEDStreamer XDAQ apps are operated normally, i.e. started and stopped in the corresponding transitions.");
      }
      else {
        logger.warn("[HCAL base] StopFEDStreamer: " + functionManager.StopFEDStreamer + " - this means FEDStreamer XDAQ apps are started once during the starting transition but never ever stopped in a run config.");
      }
    }


    // Check if the userXML specifies that the async communication is disabled
    {
      String doForceNotToUseAsyncCommunication = "";
      try { doForceNotToUseAsyncCommunication=xmlHandler.getHCALuserXMLelementContent("ForceNotToUseAsyncCommunication"); }
      catch (UserActionException e) { logger.warn(e.getMessage()); }
      if (!doForceNotToUseAsyncCommunication.equals("")) {
        functionManager.ForceNotToUseAsyncCommunication = true;
      }
      if (functionManager.ForceNotToUseAsyncCommunication) {
        logger.warn("[HCAL base] ForceNotToUseAsyncCommunication: " + functionManager.ForceNotToUseAsyncCommunication + " - this means all async communication is swichted off completely!!");
      }
      else {
        logger.warn("[HCAL base] ForceNotToUseAsyncCommunication: " + functionManager.ForceNotToUseAsyncCommunication + " - this means async communication is possible if async XDAQ apps were detected, configured ...");
      }
    }


    // Get the default number of events requested specified in the userXML
    {
      Integer DefaultNumberOfEvents = 0;
      String theNumberOfEvents = "";
      try { theNumberOfEvents=xmlHandler.getHCALuserXMLelementContent("NumberOfEvents"); }
      catch (UserActionException e) { logger.warn(e.getMessage()); }
      if (!theNumberOfEvents.equals("")) {
        DefaultNumberOfEvents = Integer.valueOf(theNumberOfEvents);
        logger.info("[HCAL base] Default number of events to take set to: " + DefaultNumberOfEvents.toString());
        functionManager.getParameterSet().put(new FunctionManagerParameter<IntegerT>(HCALParameters.NUMBER_OF_EVENTS,new IntegerT(DefaultNumberOfEvents)));
      }
    }

    // Get the CfgCVSBasePath in the userXML
    {
      //String DefaultCfgCVSBasePath = "/nfshome0/hcalcfg/cvs/RevHistory/";
      String DefaultCfgCVSBasePath = "/data/cfgcvs/cvs/RevHistory/";
      String theCfgCVSBasePath = "";
      try { theCfgCVSBasePath=xmlHandler.getHCALuserXMLelementContent("CfgCVSBasePath"); }
      catch (UserActionException e) { logger.warn(e.getMessage()); }
      if (!theCfgCVSBasePath.equals("")) {
        CfgCVSBasePath = theCfgCVSBasePath;
      } else{
        CfgCVSBasePath = DefaultCfgCVSBasePath;
      }
      //logger.debug("[HCAL base] CfgCVSBasePath: " +CfgCVSBasePath + " is used.");
      logger.info("[HCAL base] CfgCVSBasePath: " +CfgCVSBasePath + " is used.");
     
      
    }

    // Check if a default ZeroSuppressionSnippetName is given in the userXML
    {
      String theZeroSuppressionSnippetName = "";
      try { theZeroSuppressionSnippetName=xmlHandler.getHCALuserXMLelementContent("ZeroSuppressionSnippetName"); }
      catch (UserActionException e) { logger.warn(e.getMessage()); }
      if (!theZeroSuppressionSnippetName.equals("")) {
        ZeroSuppressionSnippetName = theZeroSuppressionSnippetName;
      }
      logger.debug("[HCAL base] The ZeroSuppressionSnippetName: " + ZeroSuppressionSnippetName + " is used.");
    }

    // Check if a default SpecialZeroSuppressionSnippetName is given in the userXML
    {
      String theSpecialZeroSuppressionSnippetName = "";
      try { theSpecialZeroSuppressionSnippetName=xmlHandler.getHCALuserXMLelementContent("SpecialZeroSuppressionSnippetName"); }
      catch (UserActionException e) { logger.warn(e.getMessage()); }
      if (!theSpecialZeroSuppressionSnippetName.equals("")) {
        SpecialZeroSuppressionSnippetName = theSpecialZeroSuppressionSnippetName;
      }
      logger.debug("[HCAL base] The special ZeroSuppressionSnippetName: " + SpecialZeroSuppressionSnippetName + " is used.");
    }

    // Check if a default VdMSnippetName is given in the userXML
    {
      String theVdMSnippetName = "";
      try {theVdMSnippetName=xmlHandler.getHCALuserXMLelementContent("VdMSnippetName"); }
      catch (UserActionException e) { logger.warn(e.getMessage()); }
      if (!theVdMSnippetName.equals("")) {
        VdMSnippetName = theVdMSnippetName;
      }
      logger.debug("[HCAL base] The VdMSnippetName: " + VdMSnippetName + " is used.");
    }

    // Check if we want the "Recover" button to actually perform a "Reset"
    {
      String useResetForRecover = ""; 
      try { useResetForRecover=xmlHandler.getHCALuserXMLelementContent("UseResetForRecover"); }
      catch (UserActionException e) { logger.warn(e.getMessage()); }
      if (useResetForRecover.equals("false")) {
        functionManager.getParameterSet().put(new FunctionManagerParameter<BooleanT>(HCALParameters.USE_RESET_FOR_RECOVER,new BooleanT(false)));
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

      NodeList nodes = null;
      nodes = xmlHandler.getHCALuserXML().getElementsByTagName("RunConfig");
      String availableRunConfigs="";
      for (int i=0; i < nodes.getLength(); i++) {
        logger.debug("[HCAL " + functionManager.FMname + "]: Item " + i + " has node name: " + nodes.item(i).getAttributes().getNamedItem("name").getNodeValue() 
            + ", snippet name: " + nodes.item(i).getAttributes().getNamedItem("snippet").getNodeValue()+ ", and maskedapps: " + nodes.item(i).getAttributes().getNamedItem("maskedapps").getNodeValue());

        availableRunConfigs += nodes.item(i).getAttributes().getNamedItem("name").getNodeValue() + ":" + nodes.item(i).getAttributes().getNamedItem("snippet").getNodeValue() + ":" + nodes.item(i).getAttributes().getNamedItem("maskedapps").getNodeValue() + ";";

        logger.debug("[HCAL " + functionManager.FMname + "]: availableRunConfigs is now: " + availableRunConfigs);
      }
      functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.AVAILABLE_RUN_CONFIGS,new StringT(availableRunConfigs)));
    }
    catch (DOMException | UserActionException e) {
      logger.error("[HCAL " + functionManager.FMname + "]: Got an error when trying to manipulate the userXML: " + e.getMessage());
    }

    String availableResources = "";

    QualifiedGroup qg = functionManager.getQualifiedGroup();
    List<QualifiedResource> qrList = qg.seekQualifiedResourcesOfType(new FunctionManager());
    for (QualifiedResource qr : qrList) availableResources += qr.getName() + ";";

    qrList = qg.seekQualifiedResourcesOfType(new XdaqExecutive());
    for (QualifiedResource qr : qrList) availableResources += qr.getName() + ";";

    qrList = qg.seekQualifiedResourcesOfType(new JobControl());
    for (QualifiedResource qr : qrList) availableResources += qr.getName() + ";";

    qrList = qg.seekQualifiedResourcesOfType(new XdaqApplication());
    for (QualifiedResource qr : qrList) availableResources += qr.getName() + ";";

    functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.AVAILABLE_RESOURCES,new StringT(availableResources)));
  }

  public void destroy() {
    // Stop all threads
    stopMonitorThread = true;
    stopHCALSupervisorWatchThread = true;
    stopTriggerAdapterWatchThread = true;

    // Destroy the FM
    super.destroy();
  }

  @SuppressWarnings("unchecked")
    // Returns the embeded String of the User XML field
    // If not found, an empty string is returned
    // TODO kill this and make it look at the found mastersnippet xml
    protected String GetUserXMLElement(String elementName) {

      // Get the FM's resource configuration
      String myConfig = configString;
      logger.debug("[HCAL base] GetUserXMLElement: looking for element " + elementName + " in : " + myConfig );

      // Get element value
      String elementValue = getXmlRscConf(myConfig, elementName);

      return elementValue;
    }

  // Returns the xml string of element "ElementName"
  // If not found, an empty string is returned
  // TODO remove custom XML parsing and replace with something non-idiotic
  static private String getXmlRscConf(String xmlRscConf, String elementName) {
    String response = "";

    // Check if the xmlRscConf is filled
    if (xmlRscConf == null || xmlRscConf.equals("") ) return response;

    // Check for a valid argument
    if (elementName == null || elementName.equals("") ) return response;

    int beginIndex = xmlRscConf.indexOf("<"+elementName+">") + elementName.length() + 2;
    int endIndex   = xmlRscConf.indexOf("</"+elementName+">");

    // Check if the element is available in the userXML, and if so, get the info
    if (beginIndex >= (elementName.length() + 2)) response = xmlRscConf.substring(beginIndex, endIndex);

    return response;
  }

  // Creates a configuration document ("CfgScript") that can be sent to the hcalSupervisor
  // Compiles all the information from the snippets and userxml in the order it appears in the userxml
  // If two conflicting settings (or settings defined in snippets) are defined in the userXMl, then the last one overwrites anything before it
  protected void getCfgScript() {
    // TODO HCALFM mastersnippeting

    try {

      NodeList nodes = null;
      nodes = xmlHandler.getHCALuserXML().getElementsByTagName("RunConfig");

      for (int i=0; i < nodes.getLength(); i++) {
        logger.debug("[HCAL " + functionManager.FMname + "]: Item " + i + " has node name: " + nodes.item(i).getAttributes().getNamedItem("name").getNodeValue() 
            + " and snippet name: " + nodes.item(i).getAttributes().getNamedItem("snippet").getNodeValue());
      }
      String selectedRun = ((StringT)functionManager.getParameterSet().get(HCALParameters.RUN_CONFIG_SELECTED).getValue()).getString();
      logger.info("[HCAL " + functionManager.FMname + "]: The selected snippet was: " + selectedRun);
      HCALFunctionManager fmChild = null;

      try {
        // Load the master snippet from the found file and parse it.

        docBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        if (selectedRun == "not set" ) {
          logger.info("[HCAL " + functionManager.FMname + "]: This FM did not get the selected run. It will now look for one from the LVL1");
          ParameterSet<FunctionManagerParameter> parameterSet = getUserFunctionManager().getParameterSet();
          selectedRun = ((StringT)parameterSet.get(HCALParameters.RUN_CONFIG_SELECTED).getValue()).getString();
          logger.info("[HCAL " + functionManager.FMname + "]: This FM looked for the selected run from the LVL1 and got: " + selectedRun);
          if (selectedRun == "not set") {
            ParameterSet<CommandParameter> commandParameterSet = getUserFunctionManager().getLastInput().getParameterSet();
            selectedRun = ((StringT)commandParameterSet.get(HCALParameters.RUN_CONFIG_SELECTED).getValue()).getString();
            logger.info("[HCAL " + functionManager.FMname + "]: This FM looked again for the selected run from the LVL1 and got: " + selectedRun);
          }
        } 
        //Document masterSnippet = docBuilder.parse(new File("/data/cfgcvs/cvs/RevHistory/" + selectedRun + "/pro"));
        Document masterSnippet = docBuilder.parse(new File( CfgCVSBasePath + selectedRun + "/pro"));

        masterSnippet.getDocumentElement().normalize();
        DOMSource domSource = new DOMSource(masterSnippet);
        StringWriter writer = new StringWriter();
        StreamResult result = new StreamResult(writer);
        TransformerFactory tf = TransformerFactory.newInstance();
        Transformer transformer = tf.newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
        transformer.transform(domSource, result);

        logger.info("[HCAL " + functionManager.FMname + "]: ===========================");
        logger.info("[HCAL " + functionManager.FMname + "]: ===========================");
        logger.info("[HCAL " + functionManager.FMname + "]: XML loaded from the master snippet is:");
        logger.info("[HCAL " + functionManager.FMname + "]: ---------------------------");
        logger.info(writer.toString());
        logger.info("[HCAL " + functionManager.FMname + "]: ---------------------------");

        NodeList CfgScripts =  masterSnippet.getDocumentElement().getElementsByTagName("CfgScript");
        logger.info("[HCAL " + functionManager.FMname + "]: The CfgScript has this in it:");
        logger.info("[HCAL " + functionManager.FMname + "]: ---------------------------");
        ConfigDoc = CfgScripts.item(0).getTextContent();
        logger.info(ConfigDoc);
        logger.info("[HCAL " + functionManager.FMname + "]: ---------------------------");



        CfgScripts.item(0).getParentNode().removeChild(CfgScripts.item(0));        

        domSource = new DOMSource(masterSnippet);
        writer = new StringWriter();
        result = new StreamResult(writer);
        transformer = tf.newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
        transformer.transform(domSource, result);
        String xmlString = "<userXML>" + ((FunctionManagerResource)functionManager.getQualifiedGroup().getGroup().getThisResource()).getUserXml() + "</userXML>";
        xmlString = writer.toString();
        xmlString = xmlString.replace("<mastersnippet>\n","").replace("\n</mastersnippet>\n","");
        xmlString = xmlString.replace("&amp;","&");
        logger.debug("[HCAL " + functionManager.FMname + "]: XML with CfgScript element removed and root element tags removed is now:");
        logger.debug("[HCAL " + functionManager.FMname + "]: ---------------------------");
        logger.debug(xmlString);
        logger.debug("[HCAL " + functionManager.FMname + "]: ---------------------------");
        configString = xmlString;
      }
      catch (TransformerException e) {
        logger.error("[HCAL " + functionManager.FMname + "]: Got a TransformerException when trying to transform modified mastersnippet xml: " + e.getMessage());
      }
    }
    catch (DOMException | ParserConfigurationException | SAXException | IOException  | UserActionException e) {
      logger.error("[HCAL " + functionManager.FMname + "]: Got an error when trying to manipulate the userXML: " + e.getMessage());
    }
    FullCfgScript=configString;
    logger.debug("[HCAL " + functionManager.FMname + "] The FullCfgScript which was successfully compiled for this FM.\nIt looks like this:\n" + FullCfgScript);

    functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.HCAL_CFGSCRIPT,new StringT(FullCfgScript)));
  }

  // Function, which compiles a TTCciControl sequence, which can then "be sent" to the HCAL supervisor application.
  // It can get the info from the userXML to find a sequence or parts of it from text files or the definition
  // can be done directly in the userXML.
  protected void getTTCciControl() {
    String tmpTTCciControlSequence="";
    // Load the master snippet from the found file and parse it.
    String selectedRun = ((StringT)functionManager.getParameterSet().get(HCALParameters.RUN_CONFIG_SELECTED).getValue()).getString();
    logger.info("[HCAL " + functionManager.FMname + "]: The selected snippet was: " + selectedRun);    
    try{
        docBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        if (selectedRun == "not set" ) {
          logger.info("[HCAL " + functionManager.FMname + "]: This FM did not get the selected run. It will now look for one from the LVL1");
          ParameterSet<FunctionManagerParameter> parameterSet = getUserFunctionManager().getParameterSet();
          selectedRun = ((StringT)parameterSet.get(HCALParameters.RUN_CONFIG_SELECTED).getValue()).getString();
          logger.info("[HCAL " + functionManager.FMname + "]: This FM looked for the selected run from the LVL1 and got: " + selectedRun);
          if (selectedRun == "not set") {
            ParameterSet<CommandParameter> commandParameterSet = getUserFunctionManager().getLastInput().getParameterSet();
            selectedRun = ((StringT)commandParameterSet.get(HCALParameters.RUN_CONFIG_SELECTED).getValue()).getString();
            logger.info("[HCAL " + functionManager.FMname + "]: This FM looked again for the selected run from the LVL1 and got: " + selectedRun);
          }
        }
        String TagName = "TTCciControl";
        tmpTTCciControlSequence = xmlHandler.getHCALControlSequence(selectedRun,CfgCVSBasePath,TagName);
    }
    catch ( ParserConfigurationException |  UserActionException e) {
          logger.error("[HCAL " + functionManager.FMname + "]: Got a error when parsing the TTCciControl xml: " + e.getMessage());
    }
    FullTTCciControlSequence = tmpTTCciControlSequence;
    logger.info("[Martin Log HCAL " + functionManager.FMname + "] The FullTTCciControlSequence which was successfully compiled for this FM.\nIt looks like this:\n" + FullTTCciControlSequence);
    functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.HCAL_TTCCICONTROL,new StringT(FullTTCciControlSequence)));
  }

  // Function, which compiles a LTCControl sequence, which can then "be sent" to the HCAL supervisor application.
  // It can get the info from the userXML to find a sequence or parts of it from text files or the definition
  // can be done directly in the userXML.
  protected void getLTCControl() {
    String tmpLTCControlSequence="";
    // Load the master snippet from the found file and parse it.
    String selectedRun = ((StringT)functionManager.getParameterSet().get(HCALParameters.RUN_CONFIG_SELECTED).getValue()).getString();
    logger.info("[HCAL " + functionManager.FMname + "]: The selected snippet was: " + selectedRun);    
    try{
        docBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        if (selectedRun == "not set" ) {
          logger.info("[HCAL " + functionManager.FMname + "]: This FM did not get the selected run. It will now look for one from the LVL1");
          ParameterSet<FunctionManagerParameter> parameterSet = getUserFunctionManager().getParameterSet();
          selectedRun = ((StringT)parameterSet.get(HCALParameters.RUN_CONFIG_SELECTED).getValue()).getString();
          logger.info("[HCAL " + functionManager.FMname + "]: This FM looked for the selected run from the LVL1 and got: " + selectedRun);
          if (selectedRun == "not set") {
            ParameterSet<CommandParameter> commandParameterSet = getUserFunctionManager().getLastInput().getParameterSet();
            selectedRun = ((StringT)commandParameterSet.get(HCALParameters.RUN_CONFIG_SELECTED).getValue()).getString();
            logger.info("[HCAL " + functionManager.FMname + "]: This FM looked again for the selected run from the LVL1 and got: " + selectedRun);
          }
        }
        String TagName = "LTCControl";
        tmpLTCControlSequence = xmlHandler.getHCALControlSequence(selectedRun,CfgCVSBasePath,TagName);
    }
    catch ( ParserConfigurationException |  UserActionException e) {
          logger.error("[HCAL " + functionManager.FMname + "]: Got a error when parsing the LTCControl xml: " + e.getMessage());
    }
    FullLTCControlSequence = tmpLTCControlSequence;
    logger.info("[Martin Log HCAL " + functionManager.FMname + "] The FullLTCControlSequence which was successfully compiled for this FM.\nIt looks like this:\n" + FullLTCControlSequence);

    functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.HCAL_LTCCONTROL,new StringT(FullLTCControlSequence)));
  }

  // Function, which compiles a TCDSControl sequence, which can then "be sent" to the HCAL supervisor application.
  // It can get the info from the userXML to find a sequence or parts of it from text files or the definition
  // can be done directly in the userXML.
  protected void getTCDSControl() {
    String tmpTCDSControlSequence="";
    // Load the master snippet from the found file and parse it.
    String selectedRun = ((StringT)functionManager.getParameterSet().get(HCALParameters.RUN_CONFIG_SELECTED).getValue()).getString();
    logger.info("[HCAL " + functionManager.FMname + "]: The selected snippet was: " + selectedRun);    
    try{
        docBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        if (selectedRun == "not set" ) {
          logger.info("[HCAL " + functionManager.FMname + "]: This FM did not get the selected run. It will now look for one from the LVL1");
          ParameterSet<FunctionManagerParameter> parameterSet = getUserFunctionManager().getParameterSet();
          selectedRun = ((StringT)parameterSet.get(HCALParameters.RUN_CONFIG_SELECTED).getValue()).getString();
          logger.info("[HCAL " + functionManager.FMname + "]: This FM looked for the selected run from the LVL1 and got: " + selectedRun);
          if (selectedRun == "not set") {
            ParameterSet<CommandParameter> commandParameterSet = getUserFunctionManager().getLastInput().getParameterSet();
            selectedRun = ((StringT)commandParameterSet.get(HCALParameters.RUN_CONFIG_SELECTED).getValue()).getString();
            logger.info("[HCAL " + functionManager.FMname + "]: This FM looked again for the selected run from the LVL1 and got: " + selectedRun);
          }
        }
        String TagName = "TCDSControl";
        tmpTCDSControlSequence = xmlHandler.getHCALControlSequence(selectedRun,CfgCVSBasePath,TagName);
    }
    catch ( ParserConfigurationException |  UserActionException e) {
          logger.error("[HCAL " + functionManager.FMname + "]: Got a error when parsing the TCDSControl xml: " + e.getMessage());
    }
    FullTCDSControlSequence = tmpTCDSControlSequence;
    logger.info("[Martin Log HCAL " + functionManager.FMname + "] The FullTCDSControlSequence which was successfully compiled for this FM.\nIt looks like this:\n" + FullTCDSControlSequence);

    functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.HCAL_TTCCICONTROL,new StringT(FullTCDSControlSequence)));
  }

  // Function, which compiles a LPMControl sequence, which can then "be sent" to the HCAL supervisor application.
  // It can get the info from the userXML to find a sequence or parts of it from text files or the definition
  // can be done directly in the userXML.
  protected void getLPMControl() {
    String tmpLPMControlSequence="";
    // Load the master snippet from the found file and parse it.
    String selectedRun = ((StringT)functionManager.getParameterSet().get(HCALParameters.RUN_CONFIG_SELECTED).getValue()).getString();
    logger.info("[HCAL " + functionManager.FMname + "]: The selected snippet was: " + selectedRun);    
    try{
        docBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        if (selectedRun == "not set" ) {
          logger.info("[HCAL " + functionManager.FMname + "]: This FM did not get the selected run. It will now look for one from the LVL1");
          ParameterSet<FunctionManagerParameter> parameterSet = getUserFunctionManager().getParameterSet();
          selectedRun = ((StringT)parameterSet.get(HCALParameters.RUN_CONFIG_SELECTED).getValue()).getString();
          logger.info("[HCAL " + functionManager.FMname + "]: This FM looked for the selected run from the LVL1 and got: " + selectedRun);
          if (selectedRun == "not set") {
            ParameterSet<CommandParameter> commandParameterSet = getUserFunctionManager().getLastInput().getParameterSet();
            selectedRun = ((StringT)commandParameterSet.get(HCALParameters.RUN_CONFIG_SELECTED).getValue()).getString();
            logger.info("[HCAL " + functionManager.FMname + "]: This FM looked again for the selected run from the LVL1 and got: " + selectedRun);
          }
        }
        String TagName = "LPMControl";
        tmpLPMControlSequence = xmlHandler.getHCALControlSequence(selectedRun,CfgCVSBasePath,TagName);
    }
    catch ( ParserConfigurationException |  UserActionException e) {
          logger.error("[HCAL " + functionManager.FMname + "]: Got a error when parsing the LPMControl xml: " + e.getMessage());
    }
    FullLPMControlSequence = tmpLPMControlSequence;
    logger.info("[Martin Log HCAL " + functionManager.FMname + "] The FullLPMControlSequence which was successfully compiled for this FM.\nIt looks like this:\n" + FullLPMControlSequence);

      functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.HCAL_TTCCICONTROL,new StringT(FullLPMControlSequence)));
  }

  // Function, which compiles a PIControl sequence, which can then "be sent" to the HCAL supervisor application.
  // It can get the info from the userXML to find a sequence or parts of it from text files or the definition
  // can be done directly in the userXML.
  protected void getPIControl() {
   String tmpPIControlSequence="";
    // Load the master snippet from the found file and parse it.
    String selectedRun = ((StringT)functionManager.getParameterSet().get(HCALParameters.RUN_CONFIG_SELECTED).getValue()).getString();
    logger.info("[HCAL " + functionManager.FMname + "]: The selected snippet was: " + selectedRun);    
    try{
        docBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        if (selectedRun == "not set" ) {
          logger.info("[HCAL " + functionManager.FMname + "]: This FM did not get the selected run. It will now look for one from the LVL1");
          ParameterSet<FunctionManagerParameter> parameterSet = getUserFunctionManager().getParameterSet();
          selectedRun = ((StringT)parameterSet.get(HCALParameters.RUN_CONFIG_SELECTED).getValue()).getString();
          logger.info("[HCAL " + functionManager.FMname + "]: This FM looked for the selected run from the LVL1 and got: " + selectedRun);
          if (selectedRun == "not set") {
            ParameterSet<CommandParameter> commandParameterSet = getUserFunctionManager().getLastInput().getParameterSet();
            selectedRun = ((StringT)commandParameterSet.get(HCALParameters.RUN_CONFIG_SELECTED).getValue()).getString();
            logger.info("[HCAL " + functionManager.FMname + "]: This FM looked again for the selected run from the LVL1 and got: " + selectedRun);
          }
        }
        String TagName = "PIControl";
        tmpPIControlSequence = xmlHandler.getHCALControlSequence(selectedRun,CfgCVSBasePath,TagName);
    }
    catch ( ParserConfigurationException |  UserActionException e) {
          logger.error("[HCAL " + functionManager.FMname + "]: Got a error when parsing the PIControl xml: " + e.getMessage());
    }
    FullPIControlSequence = tmpPIControlSequence;
    logger.info("[Martin Log HCAL " + functionManager.FMname + "] The FullPIControlSequence which was successfully compiled for this FM.\nIt looks like this:\n" + FullPIControlSequence);

     functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.HCAL_TTCCICONTROL,new StringT(FullPIControlSequence)));
  }

  // Function to "send" the FED_ENABLE_MASK aprameter to the HCAL supervisor application. It gets the info from the userXML.
  protected void getFedEnableMask(){
    String FedEnableMask = GetUserXMLElement("FedEnableMask");
    if (!FedEnableMask.equals("")){
      logger.info("[HCAL " + functionManager.FMname + "] FedEnableMask in userXML found.\nHere is it:\n" + FedEnableMask);
    }
    else {
      logger.info("[HCAL "+ functionManager.FMname + "] No FedEnableMask found in userXML.\n");
    }
    functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.FED_ENABLE_MASK,new StringT(FedEnableMask)));
    //more logging stuff here...?
  }

  // Function to "send" the USE_PRIMARY_TCDS aprameter to the HCAL supervisor application. It gets the info from the userXML.
  protected void getUsePrimaryTCDS(){
    boolean UsePrimaryTCDS = Boolean.parseBoolean(GetUserXMLElement("UsePrimaryTCDS"));
    if (GetUserXMLElement("UsePrimaryTCDS").equals("")){
      logger.info("[HCAL " + functionManager.FMname + "] UsePrimaryTCDS in userXML found.\nHere is it:\n" + GetUserXMLElement("UsePrimaryTCDS"));
    }
    else {
      logger.info("[HCAL "+ functionManager.FMname + "] No UsePrimaryTCDS found in userXML.\n");
    }
    functionManager.getParameterSet().put(new FunctionManagerParameter<BooleanT>(HCALParameters.USE_PRIMARY_TCDS,new BooleanT(UsePrimaryTCDS)));
    // more logging stuff here...?
  }

  // configuring all created HCAL applications by means of sending the HCAL CfgScript to the HCAL supervisor
  protected void sendRunTypeConfiguration(String CfgScript, String TTCciControlSequence, String LTCControlSequence, String TCDSControlSequence, String LPMControlSequence, String PIControlSequence, String FedEnableMask, boolean UsePrimaryTCDS) {
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
            functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT("Error")));
            functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT(errMessage)));
            if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; return;}

          }
          catch (XDAQException e) {
            String errMessage = "[HCAL " + functionManager.FMname + "] Error! XDAQException: sendRunTypeConfiguration()" + e.getMessage();
            logger.error(errMessage,e);
            functionManager.sendCMSError(errMessage);
            functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT("Error")));
            functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT(errMessage)));
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
            functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT("Error")));
            functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT(errMessage)));
            if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; return;}

          }
          catch (XDAQException e) {
            String errMessage = "[HCAL " + functionManager.FMname + "] Error! XDAQException: sendRunTypeConfiguration()" + e.getMessage();
            logger.error(errMessage,e);
            functionManager.sendCMSError(errMessage);
            functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT("Error")));
            functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT(errMessage)));
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
              logger.info("[HCAL " + functionManager.FMname + "]: the ConfigurationDoc to be sent to the supervisor is: " + ConfigDoc);
              pam.setValue("ConfigurationDoc",ConfigDoc);
              pam.setValue("Partition",functionManager.FMpartition);
              pam.setValue("RunSessionNumber",Sid.toString());
              pam.setValue("hardwareConfigurationStringTCDS", FullTCDSControlSequence);
              pam.setValue("hardwareConfigurationStringLPM", FullLPMControlSequence);
              pam.setValue("hardwareConfigurationStringPI", FullPIControlSequence);
              pam.setValue("fedEnableMask", FedEnableMask);
              pam.setValue("usePrimaryTCDS", new Boolean(UsePrimaryTCDS).toString());
              logger.debug("[HCAL " + functionManager.FMname + "] sending TCDSControl sequence:\n" + FullTCDSControlSequence);
              logger.debug("[HCAL " + functionManager.FMname + "] sending LPMControl sequence:\n" + FullLPMControlSequence);
              logger.debug("[HCAL " + functionManager.FMname + "] sending PIControl sequence:\n" + FullPIControlSequence);
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
            functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT("Error")));
            functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("oops - technical difficulties ...")));
            if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; return;}

          }
          catch (XDAQException e) {
            String errMessage = "[HCAL " + functionManager.FMname + "] Error! XDAQException: sendRunTypeConfiguration()";
            logger.error(errMessage,e);
            functionManager.sendCMSError(errMessage);
            functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT("Error")));
            functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("oops - technical difficulties ...")));
            if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; return;}

          }
        }
      }
      // send SOAP configure to the HCAL supervisor
      if (functionManager.asyncSOAP) { HCALSuperVisorIsOK = false; }  // handle the not async SOAP talking HCAL supervisor when there are async SOAP applications defined
      try {
        functionManager.containerhcalSupervisor.execute(HCALInputs.CONFIGURE);
      }
      catch (QualifiedResourceContainerException e) {
        String errMessage = "[HCAL " + functionManager.FMname + "] Error! QualifiedResourceContainerException: sendRunTypeConfiguration()";
        logger.error(errMessage,e);
        functionManager.sendCMSError(errMessage);
        functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT("Error")));
        functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT(errMessage)));
        if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; return;}

      }
    }
    else if (!functionManager.FMrole.equals("Level2_TCDSLPM")) {
      String errMessage = "[HCAL " + functionManager.FMname + "] Error! No HCAL supervisor found: sendRunTypeConfiguration()";
      logger.error(errMessage);
      functionManager.sendCMSError(errMessage);
      functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT("Error")));
      functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT(errMessage)));
      if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; return;}

    }
  }

  protected void setMaskedFMs() {

    // functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.MASKED_APPLICATIONS,new StringT(MaskedApplications)));

    QualifiedGroup qg = functionManager.getQualifiedGroup();
    // TODO send all masked applications defined in global parameter 
    String MaskedFMs =  ((StringT)functionManager.getParameterSet().get(HCALParameters.MASKED_RESOURCES).getValue()).getString();
    if (MaskedFMs.length() > 0) {
      MaskedFMs = MaskedFMs.substring(0, MaskedFMs.length()-1);
    }
    List<QualifiedResource> level2list = qg.seekQualifiedResourcesOfType(new FunctionManager());
    boolean somebodysHandlingTA = false;
    boolean itsThisLvl2 = false;
    boolean itsAdummy = false;
    String allMaskedResources = "";
    String ruInstance = "";
    String lpmSupervisor = "";
    String EvmTrigsApps = "";
    for (QualifiedResource qr : level2list) {
      itsThisLvl2 = false;
      try {
        QualifiedGroup level2group = ((FunctionManager)qr).getQualifiedGroup();
        logger.debug("[HCAL " + functionManager.FMname + "]: the qualified group has this DB connector" + level2group.rs.toString());
        Group fullConfig = level2group.rs.retrieveLightGroup(qr.getResource());
        // TODO see here
        List<Resource> fullconfigList = fullConfig.getChildrenResources();
        if (MaskedFMs.length() > 0) {
          logger.info("[HCAL " + functionManager.FMname + "]:: Got MaskedFMs " + MaskedFMs);
          String[] MaskedResourceArray = MaskedFMs.split(";");
          for (String MaskedFM: MaskedResourceArray) {
            logger.debug("[HCAL " + functionManager.FMname + "]: " + functionManager.FMname + ": Starting to mask FM " + MaskedFM);
            if (qr.getName().equals(MaskedFM)) {
              logger.debug("[HCAL " + functionManager.FMname + "]: Going to call setActive(false) on "+qr.getName());
              qr.setActive(false);
              
              //logger.info("[HCAL " + functionManager.FMname + "]: LVL2 " + qr.getName() + " has rs group " + level2group.rs.toString());
                allMaskedResources = ((StringT)functionManager.getParameterSet().get(HCALParameters.MASKED_RESOURCES).getValue()).getString();
                for (Resource level2resource : fullconfigList) {
                logger.debug("[HCAL " + functionManager.FMname + "]: The masked level 2 function manager " + qr.getName() + " has this in its XdaqExecutive list: " + level2resource.getName());
                allMaskedResources+=level2resource.getName();
                allMaskedResources+=";";
                logger.info("[HCAL " + functionManager.FMname + "]: The new list of all masked resources is: " + allMaskedResources);
              }
            }
          }
        }
        for (Resource level2resource : fullconfigList) {
          logger.debug("[HCAL " + functionManager.FMname + "]: the FM with name: " + qr.getName() + " has a resource named " + level2resource.getName() );
          if (!MaskedFMs.contains(qr.getName())) { 
            if (!allMaskedResources.contains(qr.getName()) && (level2resource.getName().contains("TriggerAdapter") || level2resource.getName().contains("FanoutTTCciTA")))          {
              if (somebodysHandlingTA && !itsAdummy) { 
                if (level2resource.getName().contains("DummyTriggerAdapter") ) {
                  itsThisLvl2=true;
                  itsAdummy=true;
                  logger.warn("[JohnLog] found a DummyTriggerAdapter in " + qr.getName() + " after somebody else is already handling the TA.");
                  allMaskedResources += EvmTrigsApps;
                  qr.getResource().setRole("EvmTrig");
                  logger.warn("[JohnLog] just set the role EvmTrig for the FM with name: " + qr.getName());
                  logger.warn("[JohnLog] starting to look for the old EvmTrig to be replaced.");
                  for (QualifiedResource otherLevel2FM : level2list) {
                    logger.warn("[JohnLog] found other level2 with name : " + otherLevel2FM.getName() + " and role: " + otherLevel2FM.getRole().toString());
                   
                    if (otherLevel2FM.getRole().toString().equals("EvmTrig") && !qr.getName().equals(otherLevel2FM.getName())) {
                       otherLevel2FM.getResource().setRole("HCAL");
                       logger.warn("[JohnLog] just reset the role HCAL for the FM with name: "  + otherLevel2FM.getName());
                       functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.EVM_TRIG_FM, new StringT(qr.getName())));
                       logger.warn("[JohnLog] just reset the role EVM_TRIG_FM");
                    }
                  }
                }
                else {
                  allMaskedResources+=level2resource.getName()+";"; 
                  logger.info("[HCAL " + functionManager.FMname + "]: Just masked the redundant trigger adapter " + level2resource.getName());
                }
              }

              else {
                qr.getResource().setRole("EvmTrig");
                logger.info("[HCAL " + functionManager.FMname + "]: The following FM is handling the trigger adapter: " + qr.getName());
                somebodysHandlingTA=true;
                itsThisLvl2=true;
                if (qr.getName().contains("DummyTriggerAdapter")){
                  itsAdummy = true;
                }
                logger.debug("[HCAL " + functionManager.FMname + "]: About to set EVM_TRIG_FM.");
                functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.EVM_TRIG_FM, new StringT(qr.getName())));
                logger.info("[HCAL " + functionManager.FMname + "]: Just set EVM_TRIG_FM.");
                EvmTrigsApps += level2resource.getName()+";";
                logger.warn("[JohnLog] filled the list of applications which may need to be masked if a DummyTriggerAdapter is found: " + EvmTrigsApps);
              }
            }
            if (!allMaskedResources.contains(qr.getName()) && level2resource.getName().contains("hcalTrivialFU"))          {
              if (somebodysHandlingTA && !itsThisLvl2) { 
                allMaskedResources+=level2resource.getName()+";"; 
                logger.info("[HCAL " + functionManager.FMname + "]: Just masked the redundant TrivialFU " + level2resource.getName());
              }
      	      else {
             	  EvmTrigsApps += level2resource.getName()+";";
              }
            }
            if (!allMaskedResources.contains(qr.getName()) && level2resource.getName().contains("hcalEventBuilder"))          {
              if (somebodysHandlingTA && !itsThisLvl2) { 
                allMaskedResources+=level2resource.getName()+";"; 
                logger.info("[HCAL " + functionManager.FMname + "]: Just masked the redundant EventBuilder " + level2resource.getName());
              }
       	      else {
            		EvmTrigsApps += level2resource.getName()+";";
                ruInstance=level2resource.getName();
                logger.info("[HCAL " + functionManager.FMname + "]: Just found the remaining EventBuilder " + level2resource.getName());
              }
            }
            if (!allMaskedResources.contains(qr.getName()) && level2resource.getName().contains("hcalSupervisor"))          {
              if (somebodysHandlingTA && !itsThisLvl2) { 
                logger.debug("[HCAL " + functionManager.FMname + "]: Found a Supervisor who is not handling the LPM." + level2resource.getName());
              }
              else if (somebodysHandlingTA && itsThisLvl2) {
                logger.info("[HCAL " + functionManager.FMname + "]: Found a Supervisor who is handling the LPM." + level2resource.getName());
                lpmSupervisor=level2resource.getName();
              }
              else {
                logger.info("[HCAL " + functionManager.FMname + "]: Found the Supervisor that is handling the LPM." + level2resource.getName());
                lpmSupervisor=level2resource.getName();
              }
            }
          }
        }
        logger.debug("[HCAL " + functionManager.FMname + "]: About to set the new MASKED_RESOURCES list.");
        functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.MASKED_RESOURCES, new StringT(allMaskedResources)));
        logger.info("[HCAL " + functionManager.FMname + "]: Just set the new MASKED_RESOURCES list.");
        logger.debug("[HCAL " + functionManager.FMname + "]: About to set the RU_INSTANCE.");
        functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.RU_INSTANCE, new StringT(ruInstance)));
        logger.info("[HCAL " + functionManager.FMname + "]: Just set the RU_INSTANCE to " + ruInstance);
        logger.debug("[HCAL " + functionManager.FMname + "]: About to set the LPM_SUPERVISOR.");
        functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.LPM_SUPERVISOR, new StringT(lpmSupervisor)));
        logger.info("[HCAL " + functionManager.FMname + "]: Just set the LPM_SUPERVISOR to " + lpmSupervisor);
      }
      catch (DBConnectorException ex) {
        logger.error("[HCAL " + functionManager.FMname + "]: Got a DBConnectorException when trying to retrieve level2s' children resources: " + ex.getMessage());
      }
    }
    if (!somebodysHandlingTA) logger.warn("[HCAL " + functionManager.FMname + "]: Got through the list of level2's but didn't find anybody to handle the triggeradapter! Bad...");
    
  }


  // get the TriggerAdapter name from the HCAL supervisor only if no trigger adapter was already set
  protected void getTriggerAdapter() {
    if (functionManager.containerTriggerAdapter==null) {
      if (!functionManager.containerhcalSupervisor.isEmpty()) {

        {
          String debugMessage = "[HCAL " + functionManager.FMname + "] HCAL supervisor found for asking the TriggerAdapter name- good!";
          logger.debug(debugMessage);
        }

        XDAQParameter pam = null;
        String TriggerAdapter = "undefined";

        // ask for the status of the HCAL supervisor and wait until it is Ready or Failed
        for (QualifiedResource qr : functionManager.containerhcalSupervisor.getApplications() ){
          try {
            pam =((XdaqApplication)qr).getXDAQParameter();
            pam.select("TriggerAdapterName");
            pam.get();

            if (!LocalMultiPartitionReadOut) {
              TriggerAdapter = pam.getValue("TriggerAdapterName");
            }
            if (TriggerAdapter.equals("DummyTriggerAdapter") ) {
              LocalMultiPartitionReadOut = true;
              logger.warn("[HCAL " + functionManager.FMname + "] TriggerAdapter named: " + TriggerAdapter + " found.\nWill switch to LocalMultiPartitionReadOut, which means only one TriggerAdapter is accepted.");
            }
            if (!TriggerAdapter.equals("")) {
              logger.info("[HCAL " + functionManager.FMname + "] TriggerAdapter named: " + TriggerAdapter + " found.");
            }
            else {
              logger.warn("[HCAL " + functionManager.FMname + "] no TriggerAdapter found.\nProbably this is OK if we are in LocalMultiPartitionReadOut.");
            }

          }
          catch (XDAQTimeoutException e) {
            String errMessage = "[HCAL " + functionManager.FMname + "] Error! XDAQTimeoutException: getTriggerAdapter()\n Perhaps the trigger adapter application is dead!?";
            logger.error(errMessage,e);
            functionManager.sendCMSError(errMessage);
            functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT("Error")));
            functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("oops - technical difficulties ...")));
            if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; return;}
          }
          catch (XDAQException e) {
            String errMessage = "[HCAL " + functionManager.FMname + "] Error! XDAQException: getTriggerAdapter()";
            logger.error(errMessage,e);
            functionManager.sendCMSError(errMessage);
            functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT("Error")));
            functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("oops - technical difficulties ...")));
            if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; return;}
          }
        }

        functionManager.containerTriggerAdapter = new XdaqApplicationContainer(functionManager.containerXdaqApplication.getApplicationsOfClass(TriggerAdapter));

        if (functionManager.containerTriggerAdapter.isEmpty()) {
          String errMessage = "[HCAL " + functionManager.FMname + "] Error! Not at least one TriggerAdapter with name " +  TriggerAdapter + " found. This is not good ...";
          logger.error(errMessage);
          functionManager.sendCMSError(errMessage);
          functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT("Error")));
          functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("oops - technical difficulties ...")));
          if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; return;}
        }

      }
      else if (!functionManager.FMrole.equals("Level2_TCDSLPM")){
        String errMessage = "[HCAL " + functionManager.FMname + "] Error! No HCAL supervisor found: getTriggerAdapter()";
        logger.error(errMessage);
        functionManager.sendCMSError(errMessage);
        functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT("Error")));
        functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT(errMessage)));
        if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; return;}
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
                functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT("Error")));
                functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("oops - technical difficulties ...")));
                if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; return;}

              }
              catch (XDAQException e) {
                String errMessage = "[HCAL " + functionManager.FMname + "] Error! XDAQException: waitforTriggerAdapter()";
                logger.error(errMessage,e);
                functionManager.sendCMSError(errMessage);
                functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT("Error")));
                functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("oops - technical difficulties ...")));
                if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; return;}

              }

            }

            logger.debug("[HCAL " + functionManager.FMname + "] The data was taken in about: " + elapsedseconds + " sec (+ " + TimeOut + " sec timeout)");

            functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("needed " + elapsedseconds + " sec (+60 sec)")));

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
                functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT("Error")));
                functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("oops - technical difficulties ...")));
                if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; return;}

              }
              catch (XDAQException e) {
                String errMessage = "[HCAL " + functionManager.FMname + "] Error! XDAQException: waitforTriggerAdapter()";
                logger.error(errMessage,e);
                functionManager.sendCMSError(errMessage);
                functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT("Error")));
                functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("oops - technical difficulties ...")));
                if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; return;}
              }
            }
          }
        }

        if (status.equals("Failed")) {
          String errMessage = "[HCAL " + functionManager.FMname + "] Error! TriggerAdapter reports error state: " + status + ". Please check log messages which were sent earlier than this one for more details ... (E1)";
          logger.error(errMessage);
          functionManager.sendCMSError(errMessage);
          functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT("Error")));
          functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("oops - technical difficulties ...")));
          if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; return;}
        }
        if (elapsedseconds>TimeOut) {
          String errMessage = "[HCAL " + functionManager.FMname + "] Error! TriggerAdapter timed out (> " + TimeOut + "sec). Please check log messages which were sent earlier than this one for more details ... (E2)";
          logger.error(errMessage);
          functionManager.sendCMSError(errMessage);
          functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT("Error")));
          functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("oops - technical difficulties ...")));
          if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; return;}
        }
      }
      else {
        String errMessage = "[HCAL " + functionManager.FMname + "] Error! No TriggerAdapter found: waitforTriggerAdapter()";
        logger.error(errMessage);
        functionManager.sendCMSError(errMessage);
        functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT("Error")));
        functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT(errMessage)));
        if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; return;}
      }
    }
  }

  // initialize qualified group, i.e. all XDAQ executives
  protected void initXDAQ() {
    // Look if the configuration uses TCDS and handle accordingly.
    // First check if TCDS is being used, and if so, tell RCMS that the TCDS executives are already initialized.
    Boolean usingTCDS = false;
    QualifiedGroup qg = functionManager.getQualifiedGroup();
    List<QualifiedResource> xdaqExecutiveList = qg.seekQualifiedResourcesOfType(new XdaqExecutive());
    for (QualifiedResource qr : xdaqExecutiveList) {
      String hostName = qr.getResource().getHostName();
      // ===WARNING!!!=== This hostname is hardcoded and should NOT be!!!
      // TODO This needs to be moved out into userXML or a snippet!!!
      if (hostName.equals("tcds-control-hcal.cms")) {
        usingTCDS = true;
        logger.info("[HCAL " + functionManager.FMname + "] initXDAQ() -- the TCDS executive on hostName " + hostName + " is being handled in a special way.");
				qr.setInitialized(true);
			}
		}

    List<QualifiedResource> jobControlList = qg.seekQualifiedResourcesOfType(new JobControl());
    for (QualifiedResource qr: jobControlList) {
      if (qr.getResource().getHostName().equals("tcds-control-hcal.cms")) {
        logger.info("[HCAL " + functionManager.FMname + "] Masking the  application with name " + qr.getName() + " running on host " + qr.getResource().getHostName() );
        qr.setActive(false);
      }
    }


    // Now if we are using TCDS, give all of the TCDS applications the URN that they need.

    try {
      qg.init();
    }
    catch (Exception e) {
      // failed to init
      String errMessage = "[HCAL " + functionManager.FMname + "] " + this.getClass().toString() + " failed to initialize resources";
      logger.error(errMessage,e);
      functionManager.sendCMSError(errMessage);
      functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT("Error")));
      functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT(errMessage)));
      if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; return;}
    }

    // find xdaq applications
    List<QualifiedResource> xdaqList = qg.seekQualifiedResourcesOfType(new XdaqApplication());
    functionManager.containerXdaqApplication = new XdaqApplicationContainer(xdaqList);
    logger.debug("[HCAL " + functionManager.FMname + "] Number of XDAQ applications controlled: " + xdaqList.size() );

    // fill applications for level one role
    functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("Retrieving the possible defined function managers for different HCAL partitions ...")));

		functionManager.containerFMChildren = new QualifiedResourceContainer(qualifiedGroup.seekQualifiedResourcesOfType(new rcms.fm.resource.qualifiedresource.FunctionManager()));
		// get the EvmTrig FM and handle it separately for sane state calculation
		List<QualifiedResource> childFMs = qualifiedGroup.seekQualifiedResourcesOfType(new rcms.fm.resource.qualifiedresource.FunctionManager());
		Iterator fmChItr = childFMs.iterator();
		while (fmChItr.hasNext()) {
			FunctionManager fmChild = (FunctionManager) fmChItr.next();
			//logger.warn("[HCAL " + functionManager.FMname + "] in containerFMChildren: FM named: " + fmChild.getName() + " found with role name: " + fmChild.getRole());
			// role is set at beginning of init() so it's already set here
			if (fmChild.getRole().toString().equals("EvmTrig") || fmChild.getRole().toString().equals("Level2_TCDSLPM")) {
				//logger.warn("[HCAL " + functionManager.FMname + "] in containerFMChildren: REMOVE FM named: " + fmChild.getName() + " with role name: " + fmChild.getRole());
				fmChItr.remove();
			}
		}
		functionManager.containerFMChildrenNoEvmTrigNoTCDSLPM = new QualifiedResourceContainer(childFMs);
    functionManager.containerFMEvmTrig = new QualifiedResourceContainer(qualifiedGroup.seekQualifiedResourcesOfRole("EvmTrig"));
    functionManager.containerFMTCDSLPM = new QualifiedResourceContainer(qualifiedGroup.seekQualifiedResourcesOfRole("Level2_TCDSLPM"));
    // get masked FMs and remove them from container
    //List<QualifiedResource> allChildFMs = qualifiedGroup.seekQualifiedResourcesOfType(new rcms.fm.resource.qualifiedresource.FunctionManager());
		List<QualifiedResource> allChildFMs = functionManager.containerFMChildren.getQualifiedResourceList();
    fmChItr = allChildFMs.iterator();
    while (fmChItr.hasNext()) {
      FunctionManager fmChild = (FunctionManager) fmChItr.next();
			if (!fmChild.isActive()) {
				//logger.warn("[HCAL " + functionManager.FMname + "] in containerFMChildren: FM named: " + fmChild.getName() + " found with role name: " + fmChild.getRole());
				// role is set at beginning of init() so it's already set here
				logger.warn("[HCAL " + functionManager.FMname + "] in containerFMChildren: REMOVE masked FM named: " + fmChild.getName() + " with role name: " + fmChild.getRole());
				fmChItr.remove();
			}
    }

    if (functionManager.containerFMChildren.isEmpty()) {
      String debugMessage = ("[HCAL " + functionManager.FMname + "] No FM childs found.\nThis is probably OK for a level 2 HCAL FM.\nThis FM has the role: " + functionManager.FMrole);
      logger.debug(debugMessage);
    }

    // see if we have any "special" FMs
		List<FunctionManager> l2LaserFMList = new ArrayList<FunctionManager>();
		List<FunctionManager> l2Priority1List = new ArrayList<FunctionManager>();
		List<FunctionManager> l2Priority2List = new ArrayList<FunctionManager>();
		List<FunctionManager> evmTrigList = new ArrayList<FunctionManager>();
		List<FunctionManager> normalList = new ArrayList<FunctionManager>();
    // see if we have any "special" FMs; store them in containers
    {
      Iterator it = functionManager.containerFMChildren.getQualifiedResourceList().iterator();
      FunctionManager fmChild = null;
      while (it.hasNext()) {
        fmChild = (FunctionManager) it.next();
				if (fmChild.getName().toString().equals("HCAL_Laser") || fmChild.getRole().toString().equals("Level2_Laser")) {
					l2LaserFMList.add(fmChild);
        }
				else if (fmChild.getName().toString().equalsIgnoreCase("HCAL_RCTMaster") || fmChild.getName().toString().equalsIgnoreCase("HCAL_HCALMaster") ||
						fmChild.getRole().toString().equals("Level2_Priority_1")) {
					l2Priority1List.add(fmChild);
				}
				else if (fmChild.getRole().toString().equals("Level2_Priority_2")) {
					l2Priority2List.add(fmChild);
				}
				else if (fmChild.getRole().toString().equals("EvmTrig")) {
					evmTrigList.add(fmChild);
				}
				else {
					normalList.add(fmChild);
				}
			}
		}
		functionManager.containerFMChildrenL2Laser = new QualifiedResourceContainer(l2LaserFMList);
		functionManager.containerFMChildrenL2Priority1 = new QualifiedResourceContainer(l2Priority1List);
		functionManager.containerFMChildrenL2Priority2 = new QualifiedResourceContainer(l2Priority2List);
		functionManager.containerFMChildrenEvmTrig = new QualifiedResourceContainer(evmTrigList);
		functionManager.containerFMChildrenNormal = new QualifiedResourceContainer(normalList);


    // fill applications for level two role
    functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("Retrieving HCAL XDAQ applications ...")));

    functionManager.containerhcalSupervisor = new XdaqApplicationContainer(functionManager.containerXdaqApplication.getApplicationsOfClass("hcalSupervisor"));
    // TCDS apps
    List<XdaqApplication> lpmList = functionManager.containerXdaqApplication.getApplicationsOfClass("tcds::lpm::LPMController");
		functionManager.containerlpmController = new XdaqApplicationContainer(lpmList);
		List<XdaqApplication> tcdsList = new ArrayList<XdaqApplication>();
		tcdsList.addAll(lpmList);
		tcdsList.addAll(functionManager.containerXdaqApplication.getApplicationsOfClass("tcds::ici::ICIController"));
		tcdsList.addAll(functionManager.containerXdaqApplication.getApplicationsOfClass("tcds::pi::PIController"));
		functionManager.containerTCDSControllers = new XdaqApplicationContainer(tcdsList);

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


    functionManager.containerEVM   = new XdaqApplicationContainer(functionManager.containerXdaqApplication.getApplicationsOfClass("EVM"));
    functionManager.containerBU    = new XdaqApplicationContainer(functionManager.containerXdaqApplication.getApplicationsOfClass("BU"));
    functionManager.containerRU    = new XdaqApplicationContainer(functionManager.containerXdaqApplication.getApplicationsOfClass("RU"));

    functionManager.containerFUResourceBroker  = new XdaqApplicationContainer(functionManager.containerXdaqApplication.getApplicationsOfClass("evf::FUResourceBroker"));
    functionManager.containerFUEventProcessor  = new XdaqApplicationContainer(functionManager.containerXdaqApplication.getApplicationsOfClass("evf::FUEventProcessor"));
    functionManager.containerStorageManager    = new XdaqApplicationContainer(functionManager.containerXdaqApplication.getApplicationsOfClass("StorageManager"));

    functionManager.containerFEDStreamer = new XdaqApplicationContainer(functionManager.containerXdaqApplication.getApplicationsOfClass("FEDStreamer"));

    functionManager.containerPeerTransportATCP = new XdaqApplicationContainer(functionManager.containerXdaqApplication.getApplicationsOfClass("pt::atcp::PeerTransportATCP"));

    functionManager.containerPeerTransportUTCP = new XdaqApplicationContainer(functionManager.containerXdaqApplication.getApplicationsOfClass("pt::atcp::PeerTransportUTCP"));

    functionManager.containerhcalRunInfoServer = new XdaqApplicationContainer(functionManager.containerXdaqApplication.getApplicationsOfClass("hcalRunInfoServer"));


    // find out if this FM controlls applications which talk asynchronous SOAP
    if (!(functionManager.containerFUResourceBroker.isEmpty() && functionManager.containerFUEventProcessor.isEmpty() && functionManager.containerStorageManager.isEmpty() && functionManager.containerFEDStreamer.isEmpty())) {
      functionManager.asyncSOAP = true;
    }

    if (!functionManager.containerPeerTransportATCP.isEmpty()) {
      logger.debug("[HCAL " + functionManager.FMname + "] Found PeerTransportATCP applications - will handle them ...");
    }
    if (!functionManager.containerPeerTransportUTCP.isEmpty()) {
      logger.info("[HCAL " + functionManager.FMname + "] Found PeerTransportUTCP applications - will handle them ...");
    }

    // find out if HCAL supervisor is ready for async SOAP communication
    if (!functionManager.containerhcalSupervisor.isEmpty()) {

      {
        String debugMessage = "[HCAL " + functionManager.FMname + "] HCAL supervisor found for checking if async SOAP communication is possible - good!";
        logger.debug(debugMessage);
      }

      XDAQParameter pam = null;

      String dowehaveanasynchcalSupervisor="undefined";

      // ask for the status of the HCAL supervisor and wait until it is Ready or Failed
      for (QualifiedResource qr : functionManager.containerhcalSupervisor.getApplications() ){

        try {
          pam =((XdaqApplication)qr).getXDAQParameter();
          pam.select(new String[] {"TriggerAdapterName", "PartitionState", "InitializationProgress","ReportStateToRCMS"});
          pam.get();

          //FIXME SIC set this ourselves!
          dowehaveanasynchcalSupervisor = pam.getValue("ReportStateToRCMS");

          logger.debug("[HCAL " + functionManager.FMname + "] asking for the HCAL supervisor ReportStateToRCMS results in: " + dowehaveanasynchcalSupervisor);

        }
        catch (XDAQTimeoutException e) {
          String errMessage = "[HCAL " + functionManager.FMname + "] Error! XDAQTimeoutException in initXDAQ() when checking the async SOAP capabilities ...\n Perhaps the HCAL supervisor application is dead!?";
          logger.error(errMessage,e);
          functionManager.sendCMSError(errMessage);
          functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT("Error")));
          functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("oops - technical difficulties ...")));
          if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; }

        }
        catch (XDAQException e) {
          String errMessage = "[HCAL " + functionManager.FMname + "] Error! XDAQException in initXDAQ() when checking the async SOAP capabilities ...";
          logger.error(errMessage,e);
          functionManager.sendCMSError(errMessage);
          functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT("Error")));
          functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("oops - technical difficulties ...")));
          if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; }

        }

        if (dowehaveanasynchcalSupervisor==null || dowehaveanasynchcalSupervisor.equals("undefined")) {
          logger.warn("[HCAL " + functionManager.FMname + "] could not check if  async SOAP communication with HCAL supervisor is possible ...");
        }
        else if (dowehaveanasynchcalSupervisor.equals("true")) {

          logger.info("[HCAL " + functionManager.FMname + "] using async SOAP communication with HCAL supervisor ...");

          functionManager.asynchcalSupervisor = true;  // yes we have a hcalSupervisor which can talk async SOAP
          functionManager.asyncSOAP = true;  // switch on the async HCAL level 2 FM behaviour
        }
      }
    }
    else {
      logger.info("[HCAL " + functionManager.FMname + "] Warning! No HCAL supervisor found in initXDAQ().\nThis happened when checking the async SOAP capabilities.\nThis is OK for a level1 FM.");
    }


    // here all async communication is switched off
    if (functionManager.ForceNotToUseAsyncCommunication) {
      functionManager.asynchcalSupervisor = false;
      functionManager.asyncSOAP = false;
    }


    // finally, halt all TCDS apps
    try {
      Iterator it = functionManager.containerTCDSControllers.getQualifiedResourceList().iterator();
      XdaqApplication tcdsApp = null;
      while (it.hasNext()) {
				tcdsApp = (XdaqApplication) it.next();
				logger.warn("[HCAL " + functionManager.FMname + "] HALT TCDS application: " + tcdsApp.getName() + " class: " + tcdsApp.getClass() + " instance: " + tcdsApp.getInstance());
        //SIC TODO FIXME: use real session ID (and possibly RCMS URL) here
        // SIC TODO XXX FIXME Why doesn't this work?
				//tcdsApp.execute(HCALInputs.HALT,"test","http://dev.null:10000");
				tcdsApp.execute(HCALInputs.HALT,"test","http://cmsrc-hcal.cms:16001/rcms");
			}
    }
    catch (Exception e) {
      // failed to halt
      String errMessage = "[HCAL " + functionManager.FMname + "] " + this.getClass().toString() + " failed to HALT TCDS applications";
      logger.error(errMessage,e);
      functionManager.sendCMSError(errMessage);
      functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT("Error")));
      functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT(errMessage)));
      if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; return;}
    }

    // define the condition state vectors only here since the group must have been qualified before and all containers are filled
    functionManager.defineConditionState();

    functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("")));
  }

  // get all XDAQ executives and kill them
  protected void destroyXDAQ() {
    if (functionManager.HCALRunInfo!=null) {
      {
        Date date = new Date();
        Parameter<DateT> stoptime = new Parameter<DateT>("TIME_ON_EXIT",new DateT(date));
        /*try {
          logger.debug("[HCAL " + functionManager.FMname + "] Publishing to the RunInfo DB TIME_ONE_EXIT: " + date.toString());
          if (functionManager.HCALRunInfo != null) { functionManager.HCALRunInfo.publish(stoptime); }
          }
          catch (RunInfoException e) {
          String errMessage = "[HCAL " + functionManager.FMname + "] Error! RunInfoException: something seriously went wrong when publishing the run time on exit ...\nProbably this is OK when the FM was destroyed.";
          logger.error(errMessage,e);
          functionManager.sendCMSError(errMessage);
          }*/
      }
      {
        Parameter<StringT> StateOnExit = new Parameter<StringT>("STATE_ON_EXIT",new StringT(functionManager.getState().getStateString()));
        /*try {
          logger.debug("[HCAL " + functionManager.FMname + "] Publishing to the RunInfo STATE_ON_EXIT: " + functionManager.getState().getStateString());
          if (functionManager.HCALRunInfo != null) { functionManager.HCALRunInfo.publish(StateOnExit); }
          }
          catch (RunInfoException e) {
          String errMessage = "[HCAL " + functionManager.FMname + "] Error! RunInfoException: something seriously went wrong when publishing the run state on exit ...\nProbably this is OK when the FM was destroyed.";
          logger.error(errMessage,e);
          functionManager.sendCMSError(errMessage);
          }*/
      }

      functionManager.HCALRunInfo = null; // make RunInfo ready for the next round of run info to store
    }

    // find all XDAQ executives and kill them
    {
      List listExecutive = qualifiedGroup.seekQualifiedResourcesOfType(new XdaqExecutive());
      Iterator it = listExecutive.iterator();
      while (it.hasNext()) {
        XdaqExecutive ex = (XdaqExecutive) it.next();
        ex.destroy();
      }
    }

    // reset the qualified group so that the next time an init is sent all resources will be initialized again
    if (functionManager != null) {
      QualifiedGroup qg = functionManager.getQualifiedGroup();
      if (qg != null) { qg.reset(); }
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

  // get and set a session ID (called only when in local run mode)
  protected void getSessionId() {
    String user = functionManager.getQualifiedGroup().getGroup().getDirectory().getUser();
    String description = functionManager.getQualifiedGroup().getGroup().getDirectory().getFullPath();
    logSessionConnector = functionManager.logSessionConnector;
    int tempSessionId = 0;

    logger.debug("[HCAL " + functionManager.FMname + "] HCALEventHandler: Log session connector: " + logSessionConnector );

    if (logSessionConnector != null) {
      try {
        tempSessionId = logSessionConnector.createSession( user, description );
        logger.info("[HCAL " + functionManager.FMname + "] New session Id obtained =" +tempSessionId );
      }
      catch (LogSessionException e1) {
        logger.warn("[HCAL " + functionManager.FMname + "] Could not get session ID, using default = " + tempSessionId + ". Exception: ",e1);
      }
    }
    else {
      logger.warn("[HCAL " + functionManager.FMname + "] logSessionConnector = " + logSessionConnector + ", using default = " + tempSessionId + ".");
    }

    // and put it into the instance variable
    sessionId = tempSessionId;
    // put the session ID into parameter set
    functionManager.getParameterSet().put(new FunctionManagerParameter<IntegerT>(HCALParameters.SID,new IntegerT(sessionId)));
  }

  // get official CMS run and sequence number
  protected RunNumberData getOfficialRunNumber() {

    // check availability of runInfo DB
    RunInfoConnectorIF ric = functionManager.getRunInfoConnector();
    if ( ric == null ) {

      logger.warn("[HCAL " + functionManager.FMname + "] RunInfoConnector is empty i.e. is RunInfo DB down?");

      // by default give run number 0
      return new RunNumberData(new Integer(sessionId),new Integer(0),functionManager.getOwner(),Calendar.getInstance().getTime());

    }
    else {
      RunSequenceNumber rsn = new RunSequenceNumber(ric,functionManager.getOwner(),RunSequenceName);
      RunNumberData rnd = rsn.createRunSequenceNumber(sessionId);

      logger.info("[HCAL " + functionManager.FMname + "] received run number: " + rnd.getRunNumber() + " and sequence number: " + rnd.getSequenceNumber());

      functionManager.HCALRunInfo = null; // make RunInfo ready for the next round of run info to store

      return rnd;
    }
  }

  // method which returns a password free string
  protected String PasswordFree(String Input) {
    return Input.replaceAll("PASSWORD=[A-Za-z_0-9]+\"|PASSWORD=[A-Za-z_0-9]+,|OracleDBPassword=\"[A-Za-z_0-9]+\"","here_was_something_removed_because_of_security");
  }

  // establish connection to RunInfoDB - if needed
  protected void checkRunInfoDBConnection() {
    if (functionManager.HCALRunInfo == null) {
      logger.info("[HCAL " + functionManager.FMname + "] creating new RunInfo accessor with namespace: " + functionManager.HCAL_NS + " now ...");

      RunInfoConnectorIF ric = functionManager.getRunInfoConnector();
      functionManager.HCALRunInfo =  new RunInfo(ric,sessionId,Integer.valueOf(functionManager.RunNumber));

      functionManager.HCALRunInfo.setNameSpace(functionManager.HCAL_NS);

      logger.info("[HCAL " + functionManager.FMname + "] ... RunInfo accessor available.");
    }
  }

  // make entry into the CMS run info database
  protected void publishRunInfoSummary() {

    logger.info("[HCAL " + functionManager.FMname + "]: publishingRunInfoSummary");
    if (OfficialRunNumbers || RunInfoPublish || TestMode.equals("RunInfoPublish") || TestMode.equals("OfficialRunNumbers")) {

      // check availability of RunInfo DB
      checkRunInfoDBConnection();

      if ( functionManager.HCALRunInfo == null) {
        logger.warn("[HCAL " + functionManager.FMname + "] Cannot publish run info summary!");
        logger.debug("[HCAL " + functionManager.FMname + "] RunInfoConnector is empty i.e.is RunInfo DB down? Please check the logs ...");
      }
      else {
        logger.debug("[HCAL " + functionManager.FMname + "] Start of publishing to the RunInfo DB ...");

        {
          if (RunType.equals("local")) {
            Parameter<IntegerT> events = new Parameter<IntegerT>("TRIGGERS",new IntegerT(Integer.valueOf(TriggersToTake)));
            try {
              logger.info("[HCAL " + functionManager.FMname + "] Publishing to the RunInfo DB TRIGGERS: " + events.getValue().toString());
              if (functionManager.HCALRunInfo!=null) { functionManager.HCALRunInfo.publish(events); }
            }
            catch (RunInfoException e) {
              String errMessage = "[HCAL " + functionManager.FMname + "] Error! RunInfoException: something seriously went wrong when publishing the number of events taken ...\nProbably this is OK when the FM was destroyed.";
              logger.error(errMessage,e);
            }
          }
        }
        /*{
          Parameter<DateT> starttime = new Parameter<DateT>("START_TIME",new DateT(StartTime));
          try {
          logger.info("[HCAL " + functionManager.FMname + "] Publishing to the RunInfo DB START_TIME: " + starttime.getValue().toString());
          if (functionManager.HCALRunInfo!=null) { functionManager.HCALRunInfo.publish(starttime); }
          }
          catch (RunInfoException e) {
          String errMessage = "[HCAL " + functionManager.FMname + "] Error! RunInfoException: something seriously went wrong when publishing the run start time ...\nProbably this is OK when the FM was destroyed.";
          logger.error(errMessage,e);
          }
          }
          {
          Parameter<DateT> stoptime = new Parameter<DateT>("STOP_TIME",new DateT(StopTime));
          try {
          logger.info("[HCAL " + functionManager.FMname + "] Publishing to the RunInfo DB STOP_TIME: " + stoptime.getValue().toString());
          if (functionManager.HCALRunInfo!=null) { functionManager.HCALRunInfo.publish(stoptime); }
          }
          catch (RunInfoException e) {
          String errMessage = "[HCAL " + functionManager.FMname + "] Error! RunInfoException: something seriously went wrong when publishing the run stop time ...\nProbably this is OK when the FM was destroyed.";
          logger.error(errMessage,e);
          }
          }*/

        {
          Parameter<StringT> FMfullpath;
          if (!functionManager.FMfullpath.equals("")) {
            FMfullpath = new Parameter<StringT>("FM_FULLPATH",new StringT(functionManager.FMfullpath));
          }
          else {
            FMfullpath = new Parameter<StringT>("FM_FULLPATH",new StringT("not set"));
          }
          try {
            logger.info("[HCAL " + functionManager.FMname + "] Publishing to the RunInfo DB FMfullpath: " + FMfullpath.getValue().toString());
            if (functionManager.HCALRunInfo!=null) { functionManager.HCALRunInfo.publish(FMfullpath); }
          }
          catch (RunInfoException e) {
            String errMessage = "[HCAL " + functionManager.FMname + "] Error! RunInfoException: something seriously went wrong when publishing the FMfullpath used ...\nProbably this is OK when the FM was destroyed.";
            logger.error(errMessage,e);
          }
        }

        {
          Parameter<StringT> FMname;
          if (!functionManager.FMname.equals("")) {
            FMname = new Parameter<StringT>("FM_NAME",new StringT(functionManager.FMname));
          }
          else {
            FMname = new Parameter<StringT>("FM_NAME",new StringT("not set"));
          }
          try {
            logger.info("[HCAL " + functionManager.FMname + "] Publishing to the RunInfo DB FMname: " + FMname.getValue().toString());
            if (functionManager.HCALRunInfo!=null) { functionManager.HCALRunInfo.publish(FMname); }
          }
          catch (RunInfoException e) {
            String errMessage = "[HCAL " + functionManager.FMname + "] Error! RunInfoException: something seriously went wrong when publishing the FMname used ...\nProbably this is OK when the FM was destroyed.";
            logger.error(errMessage,e);
          }
        }

        {
          Parameter<StringT> FMurl;
          if (!functionManager.FMurl.equals("")) {
            FMurl = new Parameter<StringT>("FM_URL",new StringT(functionManager.FMurl));
          }
          else {
            FMurl = new Parameter<StringT>("FM_URL",new StringT("not set"));
          }
          try {
            logger.info("[HCAL " + functionManager.FMname + "] Publishing to the RunInfo DB FMurl: " + FMurl.getValue().toString());
            if (functionManager.HCALRunInfo!=null) { functionManager.HCALRunInfo.publish(FMurl); }
          }
          catch (RunInfoException e) {
            String errMessage = "[HCAL " + functionManager.FMname + "] Error! RunInfoException: something seriously went wrong when publishing the FMurl used ...\nProbably this is OK when the FM was destroyed.";
            logger.error(errMessage,e);
          }
        }

        {
          Parameter<StringT> FMuri;
          if (!functionManager.FMuri.equals("")) {
            FMuri = new Parameter<StringT>("FM_URI",new StringT(functionManager.FMuri));
          }
          else {
            FMuri = new Parameter<StringT>("FM_URI",new StringT("not set"));
          }
          try {
            logger.info("[HCAL " + functionManager.FMname + "] Publishing to the RunInfo DB FMuri: " + FMuri.getValue().toString());
            if (functionManager.HCALRunInfo!=null) { functionManager.HCALRunInfo.publish(FMuri); }
          }
          catch (RunInfoException e) {
            String errMessage = "[HCAL " + functionManager.FMname + "] Error! RunInfoException: something seriously went wrong when publishing the FMuri used ...\nProbably this is OK when the FM was destroyed.";
            logger.error(errMessage,e);
          }
        }

        {
          Parameter<StringT> FMrole;
          if (!functionManager.FMrole.equals("")) {
            FMrole = new Parameter<StringT>("FM_ROLE",new StringT(functionManager.FMrole));
          }
          else {
            FMrole = new Parameter<StringT>("FM_ROLE",new StringT("not set"));
          }
          try {
            logger.info("[HCAL " + functionManager.FMname + "] Publishing to the RunInfo DB FMrole: " + FMrole.getValue().toString());
            if (functionManager.HCALRunInfo!=null) { functionManager.HCALRunInfo.publish(FMrole); }
          }
          catch (RunInfoException e) {
            String errMessage = "[HCAL " + functionManager.FMname + "] Error! RunInfoException: something seriously went wrong when publishing the FMrole used ...\nProbably this is OK when the FM was destroyed.";
            logger.error(errMessage,e);
          }
        }

        {
          Parameter<StringT> FMtimeofstart;
          if (!functionManager.FMtimeofstart.equals("")) {
            FMtimeofstart = new Parameter<StringT>("FM_TIME_OF_START",new StringT(functionManager.utcFMtimeofstart));
          }
          else {
            FMtimeofstart = new Parameter<StringT>("FM_TIME_OF_START",new StringT("not set"));
          }
          try {
            logger.info("[HCAL " + functionManager.FMname + "] Publishing to the RunInfo DB FMtimeofstart: " + FMtimeofstart.getValue().toString());
            if (functionManager.HCALRunInfo!=null) { functionManager.HCALRunInfo.publish(FMtimeofstart); }
          }
          catch (RunInfoException e) {
            String errMessage = "[HCAL " + functionManager.FMname + "] Error! RunInfoException: something seriously went wrong when publishing the FMtimeofstart used ...\nProbably this is OK when the FM was destroyed.";
            logger.error(errMessage,e);
          }
        }

        {
          Parameter<StringT> runkey;
          if (!RunKey.equals("")) {
            runkey = new Parameter<StringT>("RUN_KEY",new StringT(RunKey));
          }
          else {
            runkey = new Parameter<StringT>("RUN_KEY",new StringT("not set"));
          }
          try {
            logger.info("[HCAL " + functionManager.FMname + "] Publishing to the RunInfo DB RunKey: " + runkey.getValue().toString());
            if (functionManager.HCALRunInfo!=null) { functionManager.HCALRunInfo.publish(runkey); }
          }
          catch (RunInfoException e) {
            String errMessage = "[HCAL " + functionManager.FMname + "] Error! RunInfoException: something seriously went wrong when publishing the RunKey used ...\nProbably this is OK when the FM was destroyed.";
            logger.error(errMessage,e);
          }
        }

        {
          String localRunKeyOnStopping = ((StringT)functionManager.getParameterSet().get(HCALParameters.RUN_CONFIG_SELECTED).getValue()).getString();
          Parameter<StringT> localrunkey;
          if (!localRunKeyOnStopping.equals("")) {
            localrunkey = new Parameter<StringT>("LOCAL_RUN_KEY",new StringT(localRunKeyOnStopping));
          }
          else {
            localrunkey = new Parameter<StringT>("LOCAL_RUN_KEY",new StringT("not set"));
          }
          try {
            logger.info("[HCAL " + functionManager.FMname + "] Publishing to the RunInfo DB Local Run Key: " + localrunkey.getValue().toString());
            if (functionManager.HCALRunInfo!=null) { functionManager.HCALRunInfo.publish(localrunkey); }
          }
          catch (RunInfoException e) {
            String errMessage = "[HCAL " + functionManager.FMname + "] Error - RunInfoException occured  when publishing the RunKey used ...\nProbably this is OK when the FM was destroyed.";
            logger.error(errMessage,e);
          }
        }

        {
          Parameter<StringT> CfgScript;
          if (!FullCfgScript.equals("")) {
            CfgScript = new Parameter<StringT>("CfgScript",new StringT(PasswordFree(FullCfgScript)));
          }
          else {
            CfgScript = new Parameter<StringT>("CfgScript",new StringT("not set"));
          }
          try {

            logger.info("[HCAL " + functionManager.FMname + "] Publishing to the RunInfo DB CfgScript:\n" + CfgScript.getValue().toString());
            if (functionManager.HCALRunInfo!=null) { functionManager.HCALRunInfo.publish(CfgScript); }
          }
          catch (RunInfoException e) {
            String errMessage = "[HCAL " + functionManager.FMname + "] Error! RunInfoException: something seriously went wrong when publishing the CfgScript used ...\nProbably this is OK when the FM was destroyed.";
            logger.error(errMessage,e);
          }
        }

        {
          Parameter<StringT> TTCciControlSequence;
          if (!FullTTCciControlSequence.equals("")) {
            TTCciControlSequence = new Parameter<StringT>("TTCciControlSequence",new StringT(FullTTCciControlSequence));

          }
          else {
            TTCciControlSequence = new Parameter<StringT>("TTCciControlSequence",new StringT("not set"));
          }
          try {
            logger.info("[HCAL " + functionManager.FMname + "] Publishing to the RunInfo DB TTCciControlSequence:\n" + TTCciControlSequence.getValue().toString());
            if (functionManager.HCALRunInfo!=null) { functionManager.HCALRunInfo.publish(TTCciControlSequence); }
          }
          catch (RunInfoException e) {
            String errMessage = "[HCAL " + functionManager.FMname + "] Error! RunInfoException: something seriously went wrong when publishing the TTCciControlSequence used ...\nProbably this is OK when the FM was destroyed.";
            logger.error(errMessage,e);
          }
        }

        {
          Parameter<StringT> LTCControlSequence;
          if (!FullLTCControlSequence.equals("")) {
            LTCControlSequence = new Parameter<StringT>("LTCControlSequence",new StringT(FullLTCControlSequence));

          }
          else {
            LTCControlSequence = new Parameter<StringT>("LTCControlSequence",new StringT("not set"));
          }
          try {
            logger.info("[HCAL " + functionManager.FMname + "] Publishing to the RunInfo DB LTCControlSequence:\n" + LTCControlSequence.getValue().toString());
            if (functionManager.HCALRunInfo!=null) { functionManager.HCALRunInfo.publish(LTCControlSequence); }
          }
          catch (RunInfoException e) {
            String errMessage = "[HCAL " + functionManager.FMname + "] Error! RunInfoException: something seriously went wrong when publishing the LTCControlSequence used ...\nProbably this is OK when the FM was destroyed.";
            logger.error(errMessage,e);
          }
        }

        {
          Parameter<StringT> TCDSControlSequence;
          if (!FullTCDSControlSequence.equals("")) {
            TCDSControlSequence = new Parameter<StringT>("TCDSControlSequence",new StringT(FullTCDSControlSequence));

          }
          else {
            TCDSControlSequence = new Parameter<StringT>("TCDSControlSequence",new StringT("not set"));
          }
          try {
            logger.info("[HCAL " + functionManager.FMname + "] Publishing to the RunInfo DB TCDSControlSequence:\n" + TCDSControlSequence.getValue().toString());
            if (functionManager.HCALRunInfo!=null) { functionManager.HCALRunInfo.publish(TCDSControlSequence); }
          }
          catch (RunInfoException e) {
            String errMessage = "[HCAL " + functionManager.FMname + "] Error! RunInfoException: something seriously went wrong when publishing the TCDSControlSequence used ...\nProbably this is OK when the FM was destroyed.";
            logger.error(errMessage,e);
          }
        }

        {
          Parameter<StringT> LPMControlSequence;
          if (!FullLPMControlSequence.equals("")) {
            LPMControlSequence = new Parameter<StringT>("LPMControlSequence",new StringT(FullLPMControlSequence));

          }
          else {
            LPMControlSequence = new Parameter<StringT>("LPMControlSequence",new StringT("not set"));
          }
          try {
            logger.info("[HCAL " + functionManager.FMname + "] Publishing to the RunInfo DB LPMControlSequence:\n" + LPMControlSequence.getValue().toString());
            if (functionManager.HCALRunInfo!=null) { functionManager.HCALRunInfo.publish(LPMControlSequence); }
          }
          catch (RunInfoException e) {
            String errMessage = "[HCAL " + functionManager.FMname + "] Error! RunInfoException: something seriously went wrong when publishing the LPMControlSequence used ...\nProbably this is OK when the FM was destroyed.";
            logger.error(errMessage,e);
          }
        }

        {
          Parameter<StringT> PIControlSequence;
          if (!FullPIControlSequence.equals("")) {
            PIControlSequence = new Parameter<StringT>("PIControlSequence",new StringT(FullPIControlSequence));

          }
          else {
            PIControlSequence = new Parameter<StringT>("PIControlSequence",new StringT("not set"));
          }
          try {
            logger.info("[HCAL " + functionManager.FMname + "] Publishing to the RunInfo DB PIControlSequence:\n" + PIControlSequence.getValue().toString());
            if (functionManager.HCALRunInfo!=null) { functionManager.HCALRunInfo.publish(PIControlSequence); }
          }
          catch (RunInfoException e) {
            String errMessage = "[HCAL " + functionManager.FMname + "] Error! RunInfoException: something seriously went wrong when publishing the PIControlSequence used ...\nProbably this is OK when the FM was destroyed.";
            logger.error(errMessage,e);
          }
        }

//        {
//          String FullComment = "Preamble:\n";
//
//          // find comment tag in the userXML
//          String CommentuserXML = GetUserXMLElement("Comment");
//          if (CommentuserXML.equals("")) { FullComment += "not used"; }
//
//          FullComment += "\nUser comment:\n";
//
//          // if there is any user add in the comment field of the FM add this too
//          String CommentUserGUI = ((StringT)functionManager.getParameterSet().get(HCALParameters.HCAL_COMMENT).getValue()).getString();
//          if (!CommentUserGUI.equals("")) {
//            FullComment += CommentUserGUI;
//          }
//          else {
//            FullComment += "not set";
//          }
//
//          Parameter<StringT> Comment = new Parameter<StringT>("Comment",new StringT(FullComment));
//          try {
//            logger.info("[HCAL " + functionManager.FMname + "] Publishing to the RunInfo DB Comment:\n" + Comment.getValue().toString());
//            if (functionManager.HCALRunInfo!=null) { functionManager.HCALRunInfo.publish(Comment); }
//          }
//          catch (RunInfoException e) {
//            String errMessage = "[HCAL " + functionManager.FMname + "] Error! RunInfoException: something seriously went wrong when publishing the Comment used ...\nProbably this is OK when the FM was destroyed.";
//            logger.error(errMessage,e);
//          }
//        }

        /* {
           Date runStop = Calendar.getInstance().getTime();
           Parameter<DateT> StopTime = new Parameter<DateT>("TIME_ON_EXIT",new DateT(runStop));
           try {
           logger.debug("[HCAL " + functionManager.FMname + "] Publishing to the RunInfo DB TIME_ONE_EXIT: " + StopTime.getValue().toString());
           if (functionManager.HCALRunInfo!=null) { functionManager.HCALRunInfo.publish(StopTime); }
           }
           catch (RunInfoException e) {
           String errMessage = "[HCAL " + functionManager.FMname + "] Error! RunInfoException: something seriously went wrong when publishing the run time on exit ...\nProbably this is OK when the FM was destroyed.";
           logger.error(errMessage,e);
           }
           }*/
        {
          Parameter<StringT> StateOnExit = new Parameter<StringT>("STATE_ON_EXIT",new StringT(functionManager.getState().getStateString()));
          try {
            logger.info("[HCAL " + functionManager.FMname + "] Publishing to the RunInfo STATE_ON_EXIT: " + functionManager.getState().getStateString());
            if (functionManager.HCALRunInfo!=null) { functionManager.HCALRunInfo.publish(StateOnExit); }
          }
          catch (RunInfoException e) {
            String errMessage = "[HCAL " + functionManager.FMname + "] Error! RunInfoException: something seriously went wrong when publishing the run state on exit ...\nProbably this is OK when the FM was destroyed.";
            logger.error(errMessage,e);
          }
        }

      }

      logger.info("[HCAL " + functionManager.FMname + "] ... publishing to the RunInfo DB Done.");

    }
  }

  // make entry into the CMS run info database with info from hcalRunInfoServer
  protected void publishRunInfoSummaryfromXDAQ() {
    logger.info("[HCAL " + functionManager.FMname + "]: just called publishRunInfoSummaryfromXDAQ");
    if (functionManager.HCALRunInfo!=null) {
      logger.info("[HCAL " + functionManager.FMname + "]: publishRunInfoSummaryfromXDAQ: HCALRunInfo was not null. Good.");
      if (OfficialRunNumbers || RunInfoPublish || TestMode.equals("RunInfoPublish") || TestMode.equals("OfficialRunNumbers")) {
        logger.debug("[HCAL " + functionManager.FMname + "]: publishRunInfoSummaryfromXDAQ: attempting to publish runinfo from xdaq after checking userXML...");
        // check availability of RunInfo DB
        checkRunInfoDBConnection();

        if ( functionManager.HCALRunInfo == null) {
          logger.warn("[HCAL " + functionManager.FMname + "]: [HCAL " + functionManager.FMname + "] Cannot publish run info summary!");
          logger.info("[HCAL " + functionManager.FMname + "]: [HCAL " + functionManager.FMname + "] RunInfoConnector is empty i.e.is RunInfo DB down? Please check the logs ...");
        }
        else {

          // prepare and set for all HCAL supervisors the RunType
          if (!functionManager.containerhcalRunInfoServer.isEmpty()) {

            //VectorT<VectorT<StringT>> RunInfoFromXDAQ = new VectorT<VectorT<StringT>>();

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
                  if (functionManager.HCALRunInfo != null) {
                    logger.info("[HCAL " + functionManager.FMname + "] Publishing the XDAQ RunInfo parameter with key name: " + key + "to the RunInfo databse.");
                    functionManager.HCALRunInfo.publishWithHistory(new Parameter<StringT>(key, new StringT(setValue)));
                  }
                  else logger.info("[HCAL " + functionManager.FMname + "]HCALRunInfo was null when trying to publish the RunInfo from XDAQ.");
                }
                catch (RunInfoException e) {
                  String errMessage = "[HCAL " + functionManager.FMname + "] Error: caught a RunInfoException whemn attempting to publish XDAQ RunInfo parameter with key name: " + key;
                  logger.error(errMessage,e);
                }
              }
            }
            logger.info("[HCAL " + functionManager.FMname + "] ... publishing the info from the hcalRunInfoServer to the RunInfo DB Done.");
          }
          else {
            if (!functionManager.FMrole.equals("HCAL")) {
              String errMessage = "[HCAL " + functionManager.FMname + "] Error! publishRunInfoSummaryfromXDAQ() requested but no hcalRunInfoServer application found - please check!";
              logger.error(errMessage);
            }
          }
        }
      }
    }
    else {
      logger.info("[HCAL " + functionManager.FMname + "] publishRunInfofromXDAQ(): HCALRunInfo was null.... bad.");
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
      logger.error(errMessage,e);
      functionManager.sendCMSError(errMessage);
      functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT("Error")));
      functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT(errMessage)));
      if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; return;}
    }

    // check if the resource was a FM or xdaq app
    if (checkIfControlledResource(resource)) {
      // check if it's a tcds app
      for (QualifiedResource app : functionManager.containerTCDSControllers.getQualifiedResourceList()) {
				if(app.getURL().equals(resource.getURL())) {
					if(!functionManager.containerhcalSupervisor.isEmpty()) // we have a supervisor to listen to; ignore all TCDS notifications
						return;
					if(!functionManager.FMrole.equals("Level2_TCDSLPM")) { // no supervisor, but this is not a TCDS LPM FM: we are not expecting this to happen
						logger.warn("[HCAL " + functionManager.FMname + "] Warning: Ignoring TCDS state notification, but this FM is not a TCDSLPM FM and does not have a supervisor either! This is unexpected.");
						return; 
					}
				}
			}
      if (newState.getToState().equals(HCALStates.ERROR.getStateString()) || newState.getToState().equals(HCALStates.FAILED.getStateString())) {

        String errMessage = "[HCAL " + functionManager.FMname + "] Error! computeNewState() for FM\n@ URI: " + functionManager.getURI() + "\nthe Resource: " + newState.getIdentifier() + " reports an error state!";
        logger.error(errMessage);
        functionManager.sendCMSError(errMessage);
        functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT("Error")));
        functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT(errMessage)));
        if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; return;}

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
                logger.error(errMessage);
                functionManager.sendCMSError(errMessage);
                functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT("Error")));
                functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("oops - technical difficulties ...")));
                if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; return;}
              }
            }
            else if (toState.equals(HCALStates.INITIAL.getStateString())) {
              if (actualState.equals(HCALStates.RECOVERING.getStateString())) { functionManager.fireEvent(HCALInputs.SETINITIAL); }
              else {
                logger.error(errMessage);
                functionManager.sendCMSError(errMessage);
                functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT("Error")));
                functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("oops - technical difficulties ...")));
                if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; return;}
              }
            }
            else if (toState.equals(HCALStates.HALTED.getStateString())) {
              if (actualState.equals(HCALStates.INITIALIZING.getStateString()))    {
								if (!functionManager.containerFMChildren.isEmpty()) {
									//logger.warn("[SethLog HCAL " + functionManager.FMname + "] computeNewState() we are in initializing and have no FM children so functionManager.fireEvent(HCALInputs.SETHALT)");
									functionManager.fireEvent(HCALInputs.SETHALT);
								}
                functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("... task done.")));
              }
              else if (actualState.equals(HCALStates.HALTING.getStateString()))       {
								logger.warn("[SethLog HCAL " + functionManager.FMname + "] computeNewState() we are in halting so functionManager.fireEvent(HCALInputs.SETHALT)");
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
                logger.error(errMessage);
                functionManager.sendCMSError(errMessage);
                functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT("Error")));
                functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("oops - technical difficulties ...")));
                if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; return;}
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
                logger.error(errMessage);
                functionManager.sendCMSError(errMessage);
                functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT("Error")));
                functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("oops - technical difficulties ...")));
                if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; return;}
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
                logger.error(errMessage);
                functionManager.sendCMSError(errMessage);
                functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT("Error")));
                functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("oops - technical difficulties ...")));
                if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; return;}
              }
            }
            else if (toState.equals(HCALStates.PAUSED.getStateString())) {
              if (actualState.equals(HCALStates.PAUSING.getStateString()))         { functionManager.fireEvent(HCALInputs.SETPAUSE); }
              else if (actualState.equals(HCALStates.RECOVERING.getStateString())) { functionManager.fireEvent(HCALInputs.SETPAUSE); }
              else {
                logger.error(errMessage);
                functionManager.sendCMSError(errMessage);
                functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT("Error")));
                functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("oops - technical difficulties ...")));
                if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; return;}
              }
            }
            else if (toState.equals(HCALStates.STOPPING.getStateString())) {
              if (actualState.equals(HCALStates.RUNNING.getStateString()) || actualState.equals(HCALStates.RUNNINGDEGRADED.getStateString()))       { functionManager.fireEvent(HCALInputs.STOP); }
              else if (actualState.equals(HCALStates.STARTING.getStateString())) { /* do nothing */ }
              else {
                logger.error(errMessage);
                functionManager.sendCMSError(errMessage);
                functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT("Error")));
                functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("oops - technical difficulties ...")));
                if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; return;}
              }
            }
						else {
              String errMessage2 = "[HCAL " + functionManager.FMname + "] Error! transitional state not found in computeNewState() for FM\n@ URI: " + functionManager.getURI() + "\nthe Resource: " + newState.getIdentifier() + " reports an error state!\nFrom state: " + functionManager.getState().getStateString() + " \nstate: " + functionManager.calcState.getStateString();
              logger.error(errMessage2);
              functionManager.sendCMSError(errMessage2);
              functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT("Error")));
              functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("oops - technical difficulties ...")));
              if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; return;}
            }
          }
        }
      }
    }
  }

  // Checks if the FM resource is inside the StateVector
  private boolean checkIfControlledResource(QualifiedResource resource) {
    boolean foundResource = false;

    if (resource.getResource().getQualifiedResourceType().equals("rcms.fm.resource.qualifiedresource.FunctionManager") || resource.getResource().getQualifiedResourceType().equals("rcms.fm.resource.qualifiedresource.XdaqApplication")) {
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
      functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT("Error")));
      functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("oops - technical difficulties ...")));
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
              paraSet = aFMChild.getParameter(HCALParameters.GLOBAL_PARAMETER_SET);
            }
            catch (ParameterServiceException e) {
              logger.warn("[HCAL " + functionManager.FMname + "] Could not update parameters for FM client: " + aFMChild.getResource().getName() + " The exception is:", e);
              return;
            }

            Double lvl2completion = ((DoubleT)paraSet.get(HCALParameters.COMPLETION).getValue()).getDouble();
            completion += lvl2completion;

            localeventstaken = ((IntegerT)paraSet.get(HCALParameters.HCAL_EVENTSTAKEN).getValue()).getInteger();
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

    functionManager.getParameterSet().put(new FunctionManagerParameter<DoubleT>(HCALParameters.COMPLETION,new DoubleT(completion)));
    functionManager.getParameterSet().put(new FunctionManagerParameter<IntegerT>(HCALParameters.HCAL_EVENTSTAKEN,new IntegerT(eventstaken)));
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
            if (fmChild.isInitialized() && fmChild.refreshState().toString().equals(HCALStates.ERROR.toString())) {
              String errMessage = "[HCAL LVL1 " + functionManager.FMname + "] Error! state of the LVL2 FM with role: " + fmChild.getRole().toString() + "\nPlease check the chainsaw logs, jobcontrol, etc. The name of this FM is: " + fmChild.getName().toString() +"\nThe URI is: " + fmChild.getURI().toString();
              logger.error(errMessage);
              try {
                errMessage = "[HCAL LVL1 " + functionManager.FMname + "] Level 2 FM with name " + fmChild.getName().toString() + " has received an xdaq error from the hcalSupervisor: " + ((StringT)fmChild.getParameter().get(HCALParameters.SUPERVISOR_ERROR).getValue()).getString();
                logger.error(errMessage);
                if (!((StringT)functionManager.getParameterSet().get(HCALParameters.SUPERVISOR_ERROR).getValue()).getString().contains(((StringT)fmChild.getParameter().get(HCALParameters.SUPERVISOR_ERROR).getValue()).getString())){
                  String totalSupervisorError = ((StringT)functionManager.getParameterSet().get(HCALParameters.SUPERVISOR_ERROR).getValue()).getString() + ((StringT)fmChild.getParameter().get(HCALParameters.SUPERVISOR_ERROR).getValue()).getString() +  System.getProperty("line.separator") ;
                  functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.SUPERVISOR_ERROR, new StringT(totalSupervisorError)));
                  functionManager.sendCMSError(totalSupervisorError);
                }
              }
              catch (ParameterServiceException e) {
                errMessage = "[HCAL LVL1 " + functionManager.FMname + "] Level 2 FM with name " + fmChild.getName().toString() + " is in error, but the hcalSupervisor was unable to report an error message from xdaq.";
                logger.error(errMessage);
                functionManager.sendCMSError(errMessage);
              }
              //functionManager.sendCMSError(errMessage);
              functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT("Error")));
              if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); }
            }
          }
        }
      }
    }
  }


  // find out if all controlled EVMs are happy before stopping the run
  protected boolean isRUBuildersEmpty() {

    if (((FunctionManagerResource)functionManager.getQualifiedGroup().getGroup().getThisResource()).getRole().equals("EvmTrig")) {
      logger.warn("[HCAL " + functionManager.FMname + "] Checking if the RUs are empty ...");
    }

    boolean reply = true;

    XdaqApplication evmApp = null;
    Iterator evmIterator = functionManager.containerEVM.getQualifiedResourceList().iterator();
    while (evmIterator.hasNext()) {
      evmApp = (XdaqApplication) evmIterator.next();

      try {
        waitRUBuilderToEmpty(evmApp);
      }
      catch (Exception e) {
        String errMessage = "[HCAL " + functionManager.FMname + "] Could not flush RUBuilder\nEVM URI: " + evmApp.getResource().getURI().toString();
        logger.error(errMessage,e);
        functionManager.sendCMSError(errMessage);
        reply = false;
      }

    }
    return reply;
  }

  // find out if one EVM is happy
  private void waitRUBuilderToEmpty(XdaqApplication app) throws UserActionException {

    if(app == null) { return; }
    String nbEvtIdsValue;
    String freeEvtIdsValue;
    String freeEvtIdsInLastIteration;
    String freeEvtIdsInFirstIteration;
    XDAQParameter nbEvtIdsParm;
    XDAQParameter freeEvtIdsParm;
    int ntry = 0;

    String nbEvtIdsInBuilderName = "nbEvtIdsInBuilder";
    String freeEvtIdsName = "freeEventIdFIFOElements";
    try {
      nbEvtIdsParm = app.getXDAQParameter();
      nbEvtIdsParm.select(nbEvtIdsInBuilderName);
      nbEvtIdsValue = getValue(nbEvtIdsParm, nbEvtIdsInBuilderName);

      freeEvtIdsParm = app.getXDAQParameter();
      freeEvtIdsParm.select(freeEvtIdsName );
      freeEvtIdsInLastIteration = getValue(freeEvtIdsParm,freeEvtIdsName);
      freeEvtIdsInFirstIteration = freeEvtIdsInLastIteration;
    }
    catch (Exception e) {
      String errMessage = "[HCAL " + functionManager.FMname + "] RUBuilder: exception occured while getting parameter ...";
      logger.error(errMessage, e);
      throw new UserActionException(errMessage,e);
    }

    while(true) {
      try {
        Thread.sleep(10000);
      }
      catch (Exception e) {
        String errMessage = "[HCAL " + functionManager.FMname + "] Sleeping thread failed while waiting for the RU builder to flush!";
        logger.error(errMessage, e);
        throw new UserActionException(errMessage);
      }
      freeEvtIdsValue = getValue(freeEvtIdsParm,freeEvtIdsName);
      if(nbEvtIdsValue.equals(freeEvtIdsValue)) {
        break;
      }

      if(!freeEvtIdsInFirstIteration .equals(freeEvtIdsValue) && freeEvtIdsInLastIteration.equals(freeEvtIdsValue )) {
        ntry++;
        logger.warn("[HCAL " + functionManager.FMname + "] Free IDs: " + freeEvtIdsValue);
        if(ntry == 5) {
          String errMessage = "[HCAL " + functionManager.FMname + "] EVM on URI " + app.getResource().getURI().toString() + " seems to have stopped building when not flushed.\nLast number of fre events Ids was: " + freeEvtIdsInLastIteration;
          logger.error(errMessage);
          throw new UserActionException(errMessage);
        }
      }
      else {
        ntry = 0;
      }
      freeEvtIdsInLastIteration = freeEvtIdsValue;
    }

  }

  private String getValue(XDAQParameter param, String s) throws UserActionException {

    try {
      if(param.get()) {
        return param.getValue(s);
      }
      else {
        String errMessage = "[HCAL " + functionManager.FMname + "] Failed to get: "+ s;
        throw new UserActionException(errMessage);
      }
    }
    catch (Exception e) {
      throw new UserActionException("[HCAL " + functionManager.FMname + "] Could not get value of: " + s,e);
    }
  }

  // check the status of the HCAL supervisor and wait until it is in the "Ready" or "Failed" state
  // This method is needed when _not_ talking to applications which talk asynchronous SOAP
  protected void waitforHCALsupervisor() {
    if (!functionManager.containerhcalSupervisor.isEmpty()) {

      {
        String debugMessage = "[HCAL " + functionManager.FMname + "] HCAL supervisor found for asking its state - good!";
        logger.debug(debugMessage);
      }

      XDAQParameter pam = null;
      String status   = "undefined";
      String progress = "undefined";
      String supervisorError = "";
      int elapsedseconds = 0;
      int timetosleep    = 1000;

      // ask for the status of the HCAL supervisor and wait until it is Ready or Failed
      for (QualifiedResource qr : functionManager.containerhcalSupervisor.getApplications() ){
        while ((!status.equals("Ready")) && (!status.equals("Failed"))) {
          try {
            logger.debug("[HCAL " + functionManager.FMname + "] asking for the HCAL supervisor PartitionState after sending the RunType, which is: " + status + " (still to go: " + progress + ")");

            elapsedseconds +=(1*timetosleep/1000);
            try { Thread.sleep(timetosleep); }
            catch (Exception ignored) {}
            logger.debug("[HCAL " + functionManager.FMname + "] ... slept for " + elapsedseconds + " sec");

            pam =((XdaqApplication)qr).getXDAQParameter();
            pam.select(new String[] {"PartitionState", "InitializationProgress"});
            pam.get();
            status = pam.getValue("PartitionState");
            progress = pam.getValue("InitializationProgress");

            localcompletion = Double.parseDouble(progress);

            if (status.equals("Cold-Init")) {
              logger.info("[HCAL " + functionManager.FMname + "] HCAL supervisor PartitionState reports: " + status + " , which means that the configuring takes longer because of e.g. firmware uploads, LUTs changes, etc. (still to go: " + progress + ")");
              functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT(status)));
              functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("please be patient ...")));
              timetosleep = 10000;
            }

          }
          catch (XDAQTimeoutException e) {
            String errMessage = "[HCAL " + functionManager.FMname + "] Error! XDAQTimeoutException: waitforHCALsupervisor()\n Perhaps this application is dead!?";
            logger.error(errMessage,e);
            functionManager.sendCMSError(errMessage);
            functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT("Error")));
            functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("oops - technical difficulties ...")));
            if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; return;}

          }
          catch (XDAQException e) {
            String errMessage = "[HCAL " + functionManager.FMname + "] Error! XDAQException: waitforHCALsupervisor()";
            logger.error(errMessage,e);
            functionManager.sendCMSError(errMessage);
            functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT("Error")));
            functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("oops - technical difficulties ...")));
            if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; return;}

          }
        }
      }

      if (status.equals("Failed")) {
        for (QualifiedResource qr : functionManager.containerhcalSupervisor.getApplications() ){
          try {
            pam =((XdaqApplication)qr).getXDAQParameter();
            pam.select(new String[] {"Partition", "overallErrorMessage"});
            pam.get();
            supervisorError = "(" + pam.getValue("Partition") + ") " + pam.getValue("overallErrorMessage");
            functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.SUPERVISOR_ERROR, new StringT(supervisorError)));
            if (supervisorError==null) {
              String errMessage = "[HCAL " + functionManager.FMname + "] Error! overallErrorMessage was not retrieved.";
              logger.error(errMessage);
            }

          }
          catch (XDAQTimeoutException e) {
            String errMessage = "[HCAL " + functionManager.FMname + "] Error! XDAQTimeoutException: HCALSupervisorWatchThread()\n Perhaps this application is dead!?";
            logger.error(errMessage);
            functionManager.sendCMSError(errMessage);
            functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT("Error")));
            functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("oops - technical difficulties ...")));
            if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; }

          }
          catch (XDAQException e) {
            String errMessage = "[HCAL " + functionManager.FMname + "] Error! XDAQException: HCALSupervisorWatchThread()";
            logger.error(errMessage);
            functionManager.sendCMSError(errMessage);
            functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT("Error")));
            functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("oops - technical difficulties ...")));
            if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; }
          }
        }
        String errMessage = "[HCAL " + functionManager.FMname + "] Error! HCAL supervisor reports error state: " + status + ". Please check log messages which were sent earlier than this one for more details ...(E3)";
        logger.error(errMessage);
        functionManager.sendCMSError(errMessage);
        functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT("Error")));
        functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT(errMessage)));
        if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; return;}
      }

      logger.info("[HCAL " + functionManager.FMname + "] Supervisor did his job in about: " + elapsedseconds + " sec");

      functionManager.getParameterSet().put(new FunctionManagerParameter<DoubleT>(HCALParameters.COMPLETION,new DoubleT(elapsedseconds)));
      functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("needed " + elapsedseconds + " sec")));

    }
    else if (!(functionManager.FMrole.equals("Level2_TCDSLPM") || functionManager.FMrole.equals("HCALFM_904Int_TTCci")) ){
      String errMessage = "[HCAL " + functionManager.FMname + "] Error! No HCAL supervisor found: waitforHCALsupervisor()";
      logger.error(errMessage);
      functionManager.sendCMSError(errMessage);
      functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT("Error")));
      functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT(errMessage)));
      if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; return;}
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
            functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT("Error")));
            functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("oops - technical difficulties ...")));
            if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; }

          }
          catch (XDAQException e) {
            String errMessage = "[HCAL " + functionManager.FMname + "] Error! XDAQException: TriggerAdapterWatchThread()";
            logger.error(errMessage,e);
            functionManager.sendCMSError(errMessage);
            functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT("Error")));
            functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("oops - technical difficulties ...")));
            if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; }

          }
        }

        if (status.equals("Failed")) {
          String errMessage = "[HCAL " + functionManager.FMname + "] Error! TriggerAdapter reports error state: " + status + ". Please check log messages which were sent earlier than this one for more details ...(E4)";
          logger.error(errMessage);
          functionManager.sendCMSError(errMessage);
          functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT("Error")));
          functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("oops - technical difficulties ...")));
          if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; }
        }

        if (status.equals("Ready")) {
          logger.info("[HCAL " + functionManager.FMname + "] The Trigger adapter reports: " + status + " , which means that all Triggers were sent ...");
          functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT("")));
          functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("The TA is stopped ...")));
          TAisstopped = true;
        }
      }
      else {
        String errMessage = "[HCAL " + functionManager.FMname + "] Error! No TriggerAdapter found: TriggerAdapterWatchThread()";
        logger.error(errMessage);
        functionManager.sendCMSError(errMessage);
        functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT("Error")));
        functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT(errMessage)));
        if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; }
      }
    }

    return TAisstopped;

  }

  // read a simple text file
  protected String readTextFile(String FileName) {

    String LocalConfigFromFile = "";

    // reading the file charwise
    {
      int ch;
      try {
        BufferedReader in = new BufferedReader ( new FileReader (FileName) );
        try {
          while( (ch = in.read()) != -1 ) {
            LocalConfigFromFile += (char) ch;
          }
          in.close();
        }
        catch (IOException e) {
          String errMessage = "[HCAL " + functionManager.FMname + "] Error in reading file named: " + FileName;
          logger.error(errMessage,e);
          functionManager.sendCMSError(errMessage);
          functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT("Error")));
          functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("Problems when reading local CVS based files which include HCAL configurations ...")));
          if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; }
        }
      }
      catch (IOException e) {
        String errMessage = "[HCAL " + functionManager.FMname + "] Error in opening file named: " + FileName;
        logger.error(errMessage,e);
        functionManager.sendCMSError(errMessage);
        functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT("Error")));
        functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("Problems when opening local CVS based files which include HCAL configurations ...")));
        if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; }
      }
    }

    return LocalConfigFromFile;
  }

  // find out the state of a class of FMs with a given role and return true if this state is reached for all of them
  protected Boolean waitforFMswithRole(String Role, String toState) {

    Boolean OkToProceed = true;

    Iterator it = functionManager.containerFMChildren.getQualifiedResourceList().iterator();
    FunctionManager fmChild = null;
    while (it.hasNext()) {
      fmChild = (FunctionManager) it.next();
      if (fmChild.isActive()) {
        // if FM is in an error state do not block ...
        if ((fmChild!=null) && (fmChild.refreshState().equals(HCALStates.ERROR))) {
          String errMessage = "[HCAL " + functionManager.FMname + "] Error! for FM with name: " + fmChild.getName() + "\nThe role of this FM is: " + fmChild.getRole().toString() + " when checking its state ...";
          logger.error(errMessage);
          functionManager.sendCMSError(errMessage);
          functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT("Error")));
          functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT(errMessage)));
          if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; }
          break;
        }

        if ((fmChild!=null) && (fmChild.getRole().toString().equals(Role))) {
          // check if FMs with the role given have the desired state
          if ((fmChild!=null) && (!fmChild.refreshState().equals(HCALStates.ERROR)) && (!fmChild.refreshState().getStateString().equals(toState))) {
            OkToProceed = false;
          }
          else if (fmChild.refreshState().equals(HCALStates.ERROR)) {
            String errMessage = "[HCAL " + functionManager.FMname + "] Error! for FM with name: " + fmChild.getName() + "\nThe role of this FM is: " + fmChild.getRole().toString() + " when checking its state ...";
            logger.error(errMessage);
            functionManager.sendCMSError(errMessage);
            functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT("Error")));
            functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT(errMessage)));
            if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; }
            break;
          }
        }
      }
    }
    return OkToProceed;
  }

  // find out the state of a class of FMs with a given role substring and return true if this state is reached for all of them
  protected Boolean waitforFMswithRoleMatch(String Role, String toState) {

    Boolean OkToProceed = true;

    Iterator it = functionManager.containerFMChildren.getQualifiedResourceList().iterator();
    FunctionManager fmChild = null;
    while (it.hasNext()) {
      fmChild = (FunctionManager) it.next();
      if (fmChild.isActive()) {

        // if FM is in an error state do not block ...
        if ((fmChild!=null) && (fmChild.refreshState().equals(HCALStates.ERROR))) {
          String errMessage = "[HCAL " + functionManager.FMname + "] Error! for FM with name: " + fmChild.getName() + "\nThe role of this FM is: " + fmChild.getRole().toString() + " when checking its state ...";
          logger.error(errMessage);
          functionManager.sendCMSError(errMessage);
          functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT("Error")));
          functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT(errMessage)));
          if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; }
          break;
        }

        if ((fmChild!=null) && (fmChild.getRole().toString().matches(Role))) {
          // check if FMs with the role given have the desired state
          if ((fmChild!=null) && (!fmChild.refreshState().equals(HCALStates.ERROR)) && (!fmChild.refreshState().getStateString().equals(toState))) {
            OkToProceed = false;
          }
          else if (fmChild.refreshState().equals(HCALStates.ERROR)) {
            String errMessage = "[HCAL " + functionManager.FMname + "] Error! for FM with name: " + fmChild.getName() + "\nThe role of this FM is: " + fmChild.getRole().toString() + " when checking its state ...";
            logger.error(errMessage);
            functionManager.sendCMSError(errMessage);
            functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT("Error")));
            functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT(errMessage)));
            if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; }
            break;
          }
        }
      }
    }
    return OkToProceed;
  }

  // find out the state of an FMs with and return true if this state is reached
  protected Boolean waitforFM(FunctionManager fmChild, String toState) {
    
    Boolean OkToProceed = true;
    if (fmChild.isActive()) {

      // if FM is in an error state do not block ...
      if ((fmChild!=null) && (fmChild.refreshState().equals(HCALStates.ERROR))) {
        String errMessage = "[HCAL " + functionManager.FMname + "] Error! for FM with name: " + fmChild.getName() + "\nThe role of this FM is: " + fmChild.getRole().toString() + " when checking its state ...";
        logger.error(errMessage);
        functionManager.sendCMSError(errMessage);
        functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT("Error")));
        functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT(errMessage)));
        if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; }
      }

      // check if FM has the desired state
      if ((fmChild!=null) && (!fmChild.refreshState().equals(HCALStates.ERROR)) && (!fmChild.refreshState().getStateString().equals(toState))) {
        OkToProceed = false;
      }
      else if (fmChild.refreshState().equals(HCALStates.ERROR)) {
        String errMessage = "[HCAL " + functionManager.FMname + "] Error! for FM with name: " + fmChild.getName() + "\nThe role of this FM is: " + fmChild.getRole().toString() + " when checking its state ...";
        logger.error(errMessage);
        functionManager.sendCMSError(errMessage);
        functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT("Error")));
        functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT(errMessage)));
        if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; }
      }
    }
    return OkToProceed;
  }

  // find out the state of a class of FMs with a role _not_ given and return true if this state is reached for all of them
	protected Boolean waitforFMswithNotTheRole(String Role1 ,String Role2 ,String Role3 ,String Role4 ,String Role5 , String toState) {

		Boolean OkToProceed = true;

		Iterator it = functionManager.containerFMChildren.getQualifiedResourceList().iterator();
		FunctionManager fmChild = null;
		while (it.hasNext()) {
			fmChild = (FunctionManager) it.next();

			if (fmChild.isActive()) {
				// if FM is in an error state do not block ...
				if ((fmChild!=null) && (fmChild.refreshState().equals(HCALStates.ERROR))) {
					String errMessage = "[HCAL " + functionManager.FMname + "] Error! for FM with name: " + fmChild.getName() + "\nThe role of this FM is: " + fmChild.getRole().toString() + " when checking its state ...";
					logger.error(errMessage);
					functionManager.sendCMSError(errMessage);
					functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT("Error")));
					functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT(errMessage)));
					if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; }
					break;
				}

				// check if FMs with not the role given have the desired state
				if ((fmChild!=null) && (!(fmChild.getRole().toString().equals(Role1) || fmChild.getRole().toString().equals(Role2) || fmChild.getRole().toString().equals(Role3) || fmChild.getRole().toString().equals(Role4) || fmChild.getRole().toString().equals(Role5)))) {
					// check if the given toState is reached
					if ((fmChild!=null) && (!fmChild.refreshState().equals(HCALStates.ERROR)) && (!fmChild.refreshState().getStateString().equals(toState))) {
						OkToProceed = false;
					}
					else if (fmChild.refreshState().equals(HCALStates.ERROR)) {
						String errMessage = "[HCAL " + functionManager.FMname + "] Error! for FM with name: " + fmChild.getName() + "\nThe role of this FM is: " + fmChild.getRole().toString() + " when checking its state ...";
						logger.error(errMessage);
						functionManager.sendCMSError(errMessage);
						functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT("Error")));
						functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT(errMessage)));
						if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; }
						break;
					}
				}
			}
		}
		return OkToProceed;
	}

  // find out the state of a class of FMs with a role substring _not_ given and return true if this state is reached for all of them
  protected Boolean waitforFMswithNotTheRoleMatch(String Role1 ,String Role2 ,String Role3 ,String Role4, String Role5 , String toState) {

    Boolean OkToProceed = true;

    Iterator it = functionManager.containerFMChildren.getQualifiedResourceList().iterator();
    FunctionManager fmChild = null;
    if (fmChild.isActive()) {
      while (it.hasNext()) {
        fmChild = (FunctionManager) it.next();
        if (fmChild.isActive()) {
          // if FM is in an error state do not block ...
          if ((fmChild!=null) && (fmChild.refreshState().equals(HCALStates.ERROR))) {
            String errMessage = "[HCAL " + functionManager.FMname + "] Error! for FM with name: " + fmChild.getName() + "\nThe role of this FM is: " + fmChild.getRole().toString() + " when checking its state ...";
            logger.error(errMessage);
            functionManager.sendCMSError(errMessage);
            functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT("Error")));
            functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT(errMessage)));
            if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; }
            break;
          }

          // check if FMs with not the role given have the desired state
          if ((fmChild!=null) && (!(fmChild.getRole().toString().matches(Role1) || fmChild.getRole().toString().matches(Role2) || fmChild.getRole().toString().matches(Role3) || fmChild.getRole().toString().matches(Role4) || fmChild.getRole().toString().matches(Role5)))) {
            // check if the given toState is reached
            if ((fmChild!=null) && (!fmChild.refreshState().equals(HCALStates.ERROR)) && (!fmChild.refreshState().getStateString().equals(toState))) {
              OkToProceed = false;
            }
            else if (fmChild.refreshState().equals(HCALStates.ERROR)) {
              String errMessage = "[HCAL " + functionManager.FMname + "] Error! for FM with name: " + fmChild.getName() + "\nThe role of this FM is: " + fmChild.getRole().toString() + " when checking its state ...";
              logger.error(errMessage);
              functionManager.sendCMSError(errMessage);
              functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT("Error")));
              functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT(errMessage)));
              if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; }
              break;
            }
          }
        }
      }
    }
    return OkToProceed;
  }

  // determine the active HCAL FEDs from the ENABLE_FED_MASK string received in the configureAction()
  protected List<String> getEnabledHCALFeds(String FedEnableMask) {

    List<String> fedVector = new ArrayList<String>();

    // parse FED mask
    String[] FedValueArray = FedEnableMask.split("%");

    // list of misparsed FEDs
    String errorFEDs = "";

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
                logger.warn("[HCAL " + functionManager.FMname + "] FedId = " + FedId + " is in the HCAL HBHEa FED range.\nEnabling the HBHEa partition.");
              }
              functionManager.HBHEain = true;
            }
          }
          else if ( FedId >= functionManager.firstHBHEbFedId && FedId <= functionManager.lastHBHEbFedId ) {
            if(!functionManager.HBHEbin) {
              if (functionManager.FMrole.equals("HCAL")) {
                logger.warn("[HCAL " + functionManager.FMname + "] FedId = " + FedId + " is in the HCAL HBHEb FED range.\nEnabling the HBHEb partition.");
              }
              functionManager.HBHEbin = true;
            }
          }
          else if ( FedId >= functionManager.firstHBHEcFedId && FedId <= functionManager.lastHBHEcFedId ) {
            if(!functionManager.HBHEcin) {
              if (functionManager.FMrole.equals("HCAL")) {
                logger.warn("[HCAL " + functionManager.FMname + "] FedId = " + FedId + " is in the HCAL HBHEc FED range.\nEnabling the HBHEc partition.");
              }
              functionManager.HBHEcin = true;
            }
          }
          else if ( FedId >= functionManager.firstHFFedId && FedId <= functionManager.lastHFFedId ) {
            if(!functionManager.HFin) {
              if (functionManager.FMrole.equals("HCAL")) {
                logger.warn("[HCAL " + functionManager.FMname + "] FedId = " + FedId + " is in the HCAL HF FED range.\nEnabling the HF partition.");
              }
              functionManager.HFin = true;
            }
          }
          else if ( FedId >= functionManager.firstHOFedId && FedId <= functionManager.lastHOFedId ) {
            if(!functionManager.HOin) {
              if (functionManager.FMrole.equals("HCAL")) {
                logger.warn("[HCAL " + functionManager.FMname + "] FedId = " + FedId + " is in the HCAL HF FED range.\nEnabling the HO partition.");
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
        functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT("Error")));
        functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT(errMessage)));
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
      functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT(TheLine)));
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

        // delay between polls
        try { Thread.sleep(1000); }
        catch (Exception ignored) { return; }

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
            functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT(HCALStates.UNINITIALIZED.toString())));
            functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("... reached the \"" + HCALStates.UNINITIALIZED.toString() + "\" state.")));
            functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ERROR_MSG,new StringT("")));
          }
          if ((functionManager != null) && (functionManager.isDestroyed() == false) && (functionManager.getState().getStateString().equals(HCALStates.INITIAL.toString()))) {
            functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT(HCALStates.INITIAL.toString())));
            functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("... reached the \"" + HCALStates.INITIAL.toString() + "\" state.")));
            functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ERROR_MSG,new StringT("")));
          }
          if ((functionManager != null) && (functionManager.isDestroyed() == false) && (functionManager.getState().getStateString().equals(HCALStates.HALTED.toString()))) {
            functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT(HCALStates.HALTED.toString())));
            functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("... reached the \"" + HCALStates.HALTED.toString() + "\" state.")));
            functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ERROR_MSG,new StringT("")));
          }
          if ((functionManager != null) && (functionManager.isDestroyed() == false) && (functionManager.getState().getStateString().equals(HCALStates.CONFIGURED.toString()))) {
            pollCompletion(); // get the latest update of the LVL2 config times
            functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT(HCALStates.CONFIGURED.toString())));
            functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("... reached the \"" + HCALStates.CONFIGURED.toString() + "\" state in about " + elapsedseconds + " sec.")));
            functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ERROR_MSG,new StringT("")));
          }
          if ((functionManager != null) && (functionManager.isDestroyed() == false) && (functionManager.getState().getStateString().equals(HCALStates.PAUSED.toString()))) {
            functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT(HCALStates.PAUSED.toString())));
            functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("... reached the \"" + HCALStates.PAUSED.toString() + "\" state.")));
            functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ERROR_MSG,new StringT("")));
          }
          if ((functionManager != null) && (functionManager.isDestroyed() == false) && (functionManager.getState().getStateString().equals(HCALStates.RUNNING.toString()))) {
            functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT(HCALStates.RUNNING.toString())));
            functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ERROR_MSG,new StringT("")));
          }
          if ((functionManager != null) && (functionManager.isDestroyed() == false) && (functionManager.getState().getStateString().equals(HCALStates.RUNNINGDEGRADED.toString()))) {
            functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT(HCALStates.RUNNINGDEGRADED.toString())));
            functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ERROR_MSG,new StringT("")));
          }
        }

        // move the little fishys every 2s
        if (functionManager.FMrole.equals("HCAL") && icount%2==0) {
          // move the little fishy when configuring
          if ((functionManager != null) && (functionManager.isDestroyed() == false) && (functionManager.getState().getStateString().equals(HCALStates.CONFIGURING.toString()))) {
            LittleA.movehim();
          }
          // move the little fishy when running
          if ((functionManager != null) && (functionManager.isDestroyed() == false) && ((functionManager.getState().getStateString().equals(HCALStates.RUNNING.toString())) ||
               (functionManager.getState().getStateString().equals(HCALStates.RUNNINGDEGRADED.toString()))) ) {
            LittleB.movehim();
          }
        }

        // no fishys for the LVL2s give static message to the LVL2 action box
        Boolean noticedonce = false;
        if ((functionManager != null) && (functionManager.isDestroyed() == false) && (!functionManager.FMrole.equals("HCAL")) && (!noticedonce) && (functionManager.getState().getStateString().equals(HCALStates.RUNNING.toString()))) {
          noticedonce = true;
          functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("running like hell ...")));
        }

        // check the LVL2 health
        if (icount%10==0) {
          pollLVL2FMhealth();
        }

        // from time to time report the progress in some transitional states
        if (icount%120==0) {
          if ((functionManager != null) && (functionManager.isDestroyed() == false) && (functionManager.getState().getStateString().equals(HCALStates.CONFIGURING.toString()))) {
            functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("still executing configureAction ... - so we should be closer now...")));
          }
          if ((functionManager != null) && (functionManager.isDestroyed() == false) && (functionManager.getState().getStateString().equals(HCALStates.RUNNING.toString()))) {
            functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("still running like hell ...")));
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
              functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("still executing configureAction ... - we should be better done soon!!")));
            }
            if ((functionManager != null) && (functionManager.isDestroyed() == false) && (functionManager.getState().getStateString().equals(HCALStates.RUNNING.toString()))) {
              functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("catch the little fishy ;-)")));
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

        // delay between polls
        try { Thread.sleep(1000); }
        catch (Exception ignored) { return; }

        Date now = Calendar.getInstance().getTime();

        // poll HCAL supervisor status in the "Configured" and "Running" state every 20 sec to see if it is still alive  (dangerous because ERROR state is reported wrongly quite frequently)
        if (icount%20==0) {
          if ((functionManager.getState().getStateString().equals(HCALStates.CONFIGURED.toString()) || functionManager.getState().getStateString().equals(HCALStates.RUNNING.toString())
                || functionManager.getState().getStateString().equals(HCALStates.RUNNINGDEGRADED.toString()))) {
            if (!functionManager.containerhcalSupervisor.isEmpty()) {

              {
                String debugMessage = "[HCAL " + functionManager.FMname + "] HCAL supervisor found for checking its state i.e. health - good!";
                logger.debug(debugMessage);
              }

              XDAQParameter pam = null;
              String status   = "undefined";
              String progress = "undefined";
              String taname   = "undefined";

              // ask for the status of the HCAL supervisor
              for (QualifiedResource qr : functionManager.containerhcalSupervisor.getApplications() ){

                try {
                  pam =((XdaqApplication)qr).getXDAQParameter();
                  pam.select(new String[] {"TriggerAdapterName", "PartitionState", "InitializationProgress"});
                  pam.get();

                  status = pam.getValue("PartitionState");

                  if (status==null) {
                    String errMessage = "[HCAL " + functionManager.FMname + "] Error! Asking the hcalSupervisor for the PartitionState to see if it is alive or not resulted in a NULL pointer - this is bad!";
                    logger.error(errMessage);
                    functionManager.sendCMSError(errMessage);
                    functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT("Error")));
                    functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("oops - technical difficulties ...")));
                    if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; }
                  }

                  logger.debug("[HCAL " + functionManager.FMname + "] asking for the HCAL supervisor PartitionState to see if it is still alive.\n The PartitionState is: " + status);

                }
                catch (XDAQTimeoutException e) {
                  String errMessage = "[HCAL " + functionManager.FMname + "] Error! XDAQTimeoutException: HCALSupervisorWatchThread()\nProbably the HCAL supervisor application is dead.\nCheck the corresponding jobcontrol status ...\nHere is the exception: " +e;
                  logger.error(errMessage,e);
                  functionManager.sendCMSError(errMessage);
                  functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT("Error")));
                  functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("oops - technical difficulties ...")));
                  if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; }

                }
                catch (XDAQException e) {
                  String errMessage = "[HCAL " + functionManager.FMname + "] Error! XDAQException: HCALSupervisorWatchThread()\nProbably the HCAL supervisor application is in a bad condition.\nCheck the corresponding jobcontrol status, etc. ...\nHere is the exception: " +e;
                  logger.error(errMessage,e);
                  functionManager.sendCMSError(errMessage);
                  functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT("Error")));
                  functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("oops - technical difficulties ...")));
                  if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; }

                }

                if (status.equals("Failed") || status.equals("Faulty") || status.equals("Error")) {
                  String errMessage = "[HCAL " + functionManager.FMname + "] Error! HCAL supervisor reports error state: " + status + ".\nPlease check log messages which were sent earlier than this one for more details ... (E7)";
                  logger.error(errMessage);
                  functionManager.sendCMSError(errMessage);
                  functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT("Error")));
                  functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("oops - technical difficulties ...")));
                  if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; }
                }

              }
            }
            else {
              String errMessage = "[HCAL " + functionManager.FMname + "] Error! No HCAL supervisor found: HCALSupervisorWatchThread()";
              logger.error(errMessage);
              functionManager.sendCMSError(errMessage);
              functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT("Error")));
              functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("oops - technical difficulties ...")));
              if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; }
            }
          }
        }
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

        // delay between polls
        try { Thread.sleep(1000); }
        catch (Exception ignored) { return; }

        Date now = Calendar.getInstance().getTime();

        // poll TriggerAdapter status every 5 sec
        if (icount%5==0) {
          if ((functionManager != null) && (functionManager.isDestroyed() == false) && ((functionManager.getState().getStateString().equals(HCALStates.RUNNING.toString())) ||
                (functionManager.getState().getStateString().equals(HCALStates.RUNNINGDEGRADED.toString()))) ) {
            // check the state of the TriggerAdapter
            if (functionManager.containerTriggerAdapter!=null) {
              if (!functionManager.containerTriggerAdapter.isEmpty()) {

                {
                  String debugMessage = "[HCAL " + functionManager.FMname + "] TriggerAdapter found for asking its state - good!";
                  logger.info(debugMessage);
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
                      logger.error(errMessage);
                      functionManager.sendCMSError(errMessage);
                      functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT("Error")));
                      functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("oops - technical difficulties ...")));
                      if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; }
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
                      logger.error(errMessage);
                      functionManager.sendCMSError(errMessage);
                      functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT("Error")));
                      functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("oops - technical difficulties ...")));
                      if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; }

                    }

                    logger.info("[HCAL " + functionManager.FMname + "] state of the TriggerAdapter stateName is: " + status + ".\nThe NextEventNumberString is: " + NextEventNumberString + ". \nThe local completion is: " + localcompletion + " (" + NextEventNumber + "/" + TriggersToTake.doubleValue() + ")");

                    functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("The state of the TriggerAdapter is: " + status + ".\nThe NextEventNumberString is: " + NextEventNumberString + ". \nThe local completion is: " + localcompletion + " (" + NextEventNumber + "/" + TriggersToTake.doubleValue() + ")")));

                  }
                  catch (XDAQTimeoutException e) {
                    String errMessage = "[HCAL " + functionManager.FMname + "] Error! XDAQTimeoutException: TriggerAdapterWatchThread()\n Perhaps this application is dead!?";
                    logger.error(errMessage,e);
                    functionManager.sendCMSError(errMessage);
                    functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT("Error")));
                    functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("oops - technical difficulties ...")));
                    if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; }

                  }
                  catch (XDAQException e) {
                    String errMessage = "[HCAL " + functionManager.FMname + "] Error! XDAQException: TriggerAdapterWatchThread()";
                    logger.error(errMessage,e);
                    functionManager.sendCMSError(errMessage);
                    functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT("Error")));
                    functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("oops - technical difficulties ...")));
                    if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; }

                  }
                }

                if (status.equals("Failed")) {
                  String errMessage = "[HCAL " + functionManager.FMname + "] Error! TriggerAdapter reports error state: " + status + ". Please check log messages which were sent earlier than this one for more details ... (E9)";
                  logger.error(errMessage);
                  functionManager.sendCMSError(errMessage);
                  functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT("Error")));
                  functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("oops - technical difficulties ...")));
                  if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; }
                }

                if (status.equals("Ready")) {
                  logger.info("[HCAL " + functionManager.FMname + "] The Trigger adapter reports: " + status + " , which means that all Triggers were sent ...");
                  functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT("")));
                  functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("Stopping the TA ...")));

                  if (!SpecialFMsAreControlled) {
                    logger.warn("[SethLog HCAL " + functionManager.FMname + "] Do functionManager.fireEvent(HCALInputs.STOP)");
                    functionManager.fireEvent(HCALInputs.STOP);
                  }

                  logger.debug("[HCAL " + functionManager.FMname + "] TriggerAdapter should have reported to be in the Ready state, which means the events are taken ...");
                  logger.info("[HCAL " + functionManager.FMname + "] All L1As were sent, i.e. Trigger adapter is in the Ready state, changing back to Configured state ...");
                }
              }
              else {
                String errMessage = "[HCAL " + functionManager.FMname + "] Error! No TriggerAdapter found: TriggerAdapterWatchThread()";
                logger.error(errMessage);
                functionManager.sendCMSError(errMessage);
                functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.STATE,new StringT("Error")));
                functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(HCALParameters.ACTION_MSG,new StringT("oops - technical difficulties ...")));
                if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; }
              }
            }
          }
        }
      }

      // stop the TriggerAdapter watchdog thread
      System.out.println("[HCAL " + functionManager.FMname + "] ... stopping TriggerAdapter watchdog thread done.");
      logger.warn("[SethLog HCAL " + functionManager.FMname + "] ... stopping TriggerAdapter watchdog thread done.");

      TriggerAdapterWatchThreadList.remove(this);

    }
  }


}
 
