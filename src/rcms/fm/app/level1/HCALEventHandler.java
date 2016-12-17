package rcms.fm.app.level1;

import java.util.ArrayList;
import java.util.Set;
import java.util.Date;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Hashtable;
import java.lang.Integer;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.lang.Double;
import java.math.BigInteger;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Random;
import java.net.URL;
import java.net.MalformedURLException;

import java.io.StringWriter;
import java.io.PrintWriter;

import net.hep.cms.xdaqctl.XDAQException;
import net.hep.cms.xdaqctl.XDAQTimeoutException;
import net.hep.cms.xdaqctl.XDAQMessageException;

import javax.xml.parsers.DocumentBuilder;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.Text;
import org.w3c.dom.DOMException;

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
import rcms.fm.resource.QualifiedResource;
import rcms.fm.resource.QualifiedResourceContainer;
import rcms.fm.resource.QualifiedResourceContainerException;
import rcms.fm.resource.qualifiedresource.XdaqApplication;
import rcms.fm.resource.qualifiedresource.XdaqApplicationContainer;
import rcms.fm.resource.qualifiedresource.XdaqExecutive;
import rcms.fm.resource.qualifiedresource.JobControl;
import rcms.fm.resource.qualifiedresource.FunctionManager;
import rcms.resourceservice.db.resource.fm.FunctionManagerResource;
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
import rcms.util.logsession.LogSessionConnector;

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
  public QualifiedGroup qualifiedGroup = null;
  public static final String XDAQ_NS = "urn:xdaq-soap:3.0";



  String configString  = ""; // Configuration documents for hcos
  String ConfigDoc     = "";
  String FullCfgScript = "not set";

  String FullTTCciControlSequence =  "not set";  // Config script for TTCci
  String FullLTCControlSequence   =  "not set";  // Config doc for LTC
  String FullTCDSControlSequence  =  "not set";  // Config doc for iCI
  String FullLPMControlSequence   =  "not set";  // Config doc for LPM
  String FullPIControlSequence    =  "not set";  // Config doc for PI

  public boolean UsePrimaryTCDS                =  true;   // Switch to use primary/secondary TCDS system (TODO: check implementation)
  public boolean OfficialRunNumbers            =  false;  // Query the database for a run number corresponding to the SID 
  public boolean RunInfoPublish                =  false;  // Switch to publish RunInfo or not
  public boolean RunInfoPublishfromXDAQ        =  false;  // Switch to publish additional runinfo from the hcalRunInfoServer or not
  public boolean stopMonitorThread             =  false;  // For turning off the level2 watch thread
  public boolean stopHCALSupervisorWatchThread =  false;  // For turning off the supervisor watch thread
  public boolean stopTriggerAdapterWatchThread =  false;  // For turning off the TA thread
  public boolean stopAlarmerWatchThread        =  false;  // For turning off the alarmer thread
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
  public String  RunKey           =  "";          // Current global run key
  public String  CachedRunKey     =  "";          // Previous global run key
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
  protected boolean LocalMultiPartitionReadOut =  false;  // Switch for enabling multipartition runs

  protected String WSE_FILTER                        =  "empty";                         // for XMAS -- TODO: is this needed?
  protected String ZeroSuppressionSnippetName        =  "/HTR/ZeroSuppression.cfg/pro";  //TODO: are these last three needed?
  protected String SpecialZeroSuppressionSnippetName =  "/HTR/SpecialZeroSuppression.cfg/pro";
  protected String VdMSnippetName                    =  "/LUMI/VdM.cfg/pro";

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
      functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("SEQ_NAME", new StringT(""+RunSequenceName)));
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


    // Get the CfgCVSBasePath in the userXML
    {
      String DefaultCfgCVSBasePath = "/nfshome0/hcalcfg/cvs/RevHistory/";
      //String DefaultCfgCVSBasePath = "/data/cfgcvs/cvs/RevHistory/";
      String theCfgCVSBasePath = "";
      try { theCfgCVSBasePath=xmlHandler.getHCALuserXMLelementContent("CfgCVSBasePath"); }
      catch (UserActionException e) { logger.warn(e.getMessage()); }
      if (!theCfgCVSBasePath.equals("")) {
        CfgCVSBasePath = theCfgCVSBasePath;
      } else{
        CfgCVSBasePath = DefaultCfgCVSBasePath;
      }
      //logger.debug("[HCAL base] CfgCVSBasePath: " +CfgCVSBasePath + " is used.");
      //logger.info("[HCAL ] CfgCVSBasePath: " +CfgCVSBasePath + " is used.");
      logger.info("[Martin Log HCAL " + functionManager.FMname + "] The CfgCVSBasePath for this FM is " + CfgCVSBasePath);
      functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("HCAL_CFGCVSBASEPATH",new StringT(CfgCVSBasePath)));
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

      NodeList nodes = null;
      nodes = xmlHandler.getHCALuserXML().getElementsByTagName("RunConfig");
      String availableRunConfigs="";
      for (int i=0; i < nodes.getLength(); i++) {
        logger.debug("[HCAL " + functionManager.FMname + "]: Item " + i + " has node name: " + nodes.item(i).getAttributes().getNamedItem("name").getNodeValue() 
            + ", snippet name: " + nodes.item(i).getAttributes().getNamedItem("snippet").getNodeValue()+ ", and maskedapps: " + nodes.item(i).getAttributes().getNamedItem("maskedapps").getNodeValue());

        availableRunConfigs += nodes.item(i).getAttributes().getNamedItem("name").getNodeValue() + ":" + nodes.item(i).getAttributes().getNamedItem("snippet").getNodeValue() + ":" + nodes.item(i).getAttributes().getNamedItem("maskedapps").getNodeValue() + ";";

        logger.debug("[HCAL " + functionManager.FMname + "]: availableRunConfigs is now: " + availableRunConfigs);
      }
      functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("AVAILABLE_RUN_CONFIGS",new StringT(availableRunConfigs)));
    }
    catch (DOMException | UserActionException e) {
      logger.error("[HCAL " + functionManager.FMname + "]: Got an error when trying to manipulate the userXML: " + e.getMessage());
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
                                           String TCDSControlSequence, String LPMControlSequence, String PIControlSequence, 
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
              logger.info("[HCAL " + functionManager.FMname + "]: the ConfigurationDoc to be sent to the supervisor is: " + CfgScript);
              pam.setValue("ConfigurationDoc",CfgScript);
              pam.setValue("Partition",functionManager.FMpartition);
              pam.setValue("RunSessionNumber",Sid.toString());
              pam.setValue("hardwareConfigurationStringTCDS", TCDSControlSequence);
              pam.setValue("hardwareConfigurationStringLPM", LPMControlSequence);
              pam.setValue("hardwareConfigurationStringPI", PIControlSequence);
              pam.setValue("fedEnableMask", FedEnableMask);
              pam.setValue("usePrimaryTCDS", new Boolean(UsePrimaryTCDS).toString());
              logger.debug("[HCAL " + functionManager.FMname + "] sending TCDSControl sequence:\n" + TCDSControlSequence);
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
            functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("STATE",new StringT("Error")));
            functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("ACTION_MSG",new StringT("oops - technical difficulties ...")));
            if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; return;}
          }
          catch (XDAQException e) {
            String errMessage = "[HCAL " + functionManager.FMname + "] Error! XDAQException: getTriggerAdapter()";
            logger.error(errMessage,e);
            functionManager.sendCMSError(errMessage);
            functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("STATE",new StringT("Error")));
            functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("ACTION_MSG",new StringT("oops - technical difficulties ...")));
            if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; return;}
          }
        }

        functionManager.containerTriggerAdapter = new XdaqApplicationContainer(functionManager.containerXdaqApplication.getApplicationsOfClass(TriggerAdapter));

        if (functionManager.containerTriggerAdapter.isEmpty()) {
          String errMessage = "[HCAL " + functionManager.FMname + "] Error! Not at least one TriggerAdapter with name " +  TriggerAdapter + " found. This is not good ...";
          logger.error(errMessage);
          functionManager.sendCMSError(errMessage);
          functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("STATE",new StringT("Error")));
          functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("ACTION_MSG",new StringT("oops - technical difficulties ...")));
          if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; return;}
        }

      }
      else if (!functionManager.FMrole.equals("Level2_TCDSLPM")){
        String errMessage = "[HCAL " + functionManager.FMname + "] Error! No HCAL supervisor found: getTriggerAdapter()";
        logger.error(errMessage);
        functionManager.sendCMSError(errMessage);
        functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("STATE",new StringT("Error")));
        functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("ACTION_MSG",new StringT(errMessage)));
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
      if (hostName.equals("tcds-control-hcal.cms") || hostName.equals("tcds-control-904.cms904") ) {
        usingTCDS = true;
        logger.info("[HCAL " + functionManager.FMname + "] initXDAQ() -- the TCDS executive on hostName " + hostName + " is being handled in a special way.");
        qr.setInitialized(true);
      }
    }

    List<QualifiedResource> jobControlList = qg.seekQualifiedResourcesOfType(new JobControl());
    for (QualifiedResource qr: jobControlList) {
      if (qr.getResource().getHostName().equals("tcds-control-hcal.cms") || qr.getResource().getHostName().equals("tcds-control-904.cms904") ) {
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
      StringWriter sw = new StringWriter();
      e.printStackTrace( new PrintWriter(sw) );
      System.out.println(sw.toString());
      String errMessage = "[HCAL " + functionManager.FMname + "] " + this.getClass().toString() + " failed to initialize resources. Printing stacktrace: "+ sw.toString();
      functionManager.goToError(errMessage,e);
    }

    // find xdaq applications
    List<QualifiedResource> xdaqList = qg.seekQualifiedResourcesOfType(new XdaqApplication());
    functionManager.containerXdaqApplication = new XdaqApplicationContainer(xdaqList);
    logger.debug("[HCAL " + functionManager.FMname + "] Number of XDAQ applications controlled: " + xdaqList.size() );

    // fill applications for level one role
    functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("ACTION_MSG",new StringT("Retrieving the possible defined function managers for different HCAL partitions ...")));

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
    List<QualifiedResource> allChildFMs = functionManager.containerFMChildren.getActiveQRList();
    functionManager.containerFMChildren   = new QualifiedResourceContainer(allChildFMs);
    
    if (functionManager.containerFMChildren.isEmpty()) {
      String debugMessage = ("[HCAL " + functionManager.FMname + "] No FM childs found.\nThis is probably OK for a level 2 HCAL FM.\nThis FM has the role: " + functionManager.FMrole);
      logger.debug(debugMessage);
    }

    // see if we have any "special" FMs
    List<FunctionManager> evmTrigList = new ArrayList<FunctionManager>();
    List<FunctionManager> normalList = new ArrayList<FunctionManager>();
    // see if we have any "special" FMs; store them in containers
    {
      Iterator it = functionManager.containerFMChildren.getQualifiedResourceList().iterator();
      FunctionManager fmChild = null;
      while (it.hasNext()) {
        fmChild = (FunctionManager) it.next();
        if (fmChild.getRole().toString().equals("EvmTrig")) {
          evmTrigList.add(fmChild);
        }
        else {
          normalList.add(fmChild);
        }
      }
    }
    functionManager.containerFMChildrenEvmTrig = new QualifiedResourceContainer(evmTrigList);
    functionManager.containerFMChildrenNormal = new QualifiedResourceContainer(normalList);

    // fill applications for level two role
    functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("ACTION_MSG",new StringT("Retrieving HCAL XDAQ applications ...")));

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

    functionManager.containerEVM               = new XdaqApplicationContainer(functionManager.containerXdaqApplication.getApplicationsOfClass("EVM"));
    functionManager.containerBU                = new XdaqApplicationContainer(functionManager.containerXdaqApplication.getApplicationsOfClass("BU"));
    functionManager.containerRU                = new XdaqApplicationContainer(functionManager.containerXdaqApplication.getApplicationsOfClass("RU"));
    functionManager.containerFUResourceBroker  = new XdaqApplicationContainer(functionManager.containerXdaqApplication.getApplicationsOfClass("evf::FUResourceBroker"));
    functionManager.containerFUEventProcessor  = new XdaqApplicationContainer(functionManager.containerXdaqApplication.getApplicationsOfClass("evf::FUEventProcessor"));
    functionManager.containerStorageManager    = new XdaqApplicationContainer(functionManager.containerXdaqApplication.getApplicationsOfClass("StorageManager"));
    functionManager.containerFEDStreamer       = new XdaqApplicationContainer(functionManager.containerXdaqApplication.getApplicationsOfClass("FEDStreamer"));
    functionManager.containerPeerTransportATCP = new XdaqApplicationContainer(functionManager.containerXdaqApplication.getApplicationsOfClass("pt::atcp::PeerTransportATCP"));
    functionManager.containerhcalRunInfoServer = new XdaqApplicationContainer(functionManager.containerXdaqApplication.getApplicationsOfClass("hcalRunInfoServer"));


    if (!functionManager.containerPeerTransportATCP.isEmpty()) {
      logger.debug("[HCAL " + functionManager.FMname + "] Found PeerTransportATCP applications - will handle them ...");
    }

    // find out if HCAL supervisor is ready for async SOAP communication
    if (!functionManager.containerhcalSupervisor.isEmpty()) {


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

    // finally, halt all LPM apps
    functionManager.haltLPMControllers();

    // define the condition state vectors only here since the group must have been qualified before and all containers are filled
    functionManager.defineConditionState();
    functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("ACTION_MSG",new StringT("")));
  }

  //Set instance numbers and HandleLPM after initXDAQ()
  protected void initXDAQinfospace() {
      List<QualifiedResource> xdaqApplicationList = qualifiedGroup.seekQualifiedResourcesOfType(new XdaqApplication());
      QualifiedResourceContainer qrc = new QualifiedResourceContainer(xdaqApplicationList);
      for (QualifiedResource qr : qrc.getActiveQRList()) {
          try {
            XDAQParameter pam = null;
            pam = ((XdaqApplication)qr).getXDAQParameter();
            String ruInstance = ((StringT)functionManager.getHCALparameterSet().get("RU_INSTANCE").getValue()).getString();
            if (ruInstance==""){
              logger.warn("HCAL LVL2 " + functionManager.FMname + "]: HCALparameter RU_INSTANCE is not set before calling initXDAQinfospace()"); 
            }
            for (String pamName : pam.getNames()){
              if (pamName.equals("RUinstance")) {
                pam.select(new String[] {"RUinstance"});
                pam.setValue("RUinstance", ruInstance.split("_")[1]);
                pam.send();
                logger.info("[HCAL LVL2 " + functionManager.FMname + "]: Just set the RUinstance for " + qr.getName() + " to " +  ruInstance.split("_")[1]);
              }
              if (pamName.equals("BUInstance")) {
                pam.select(new String[] {"BUInstance"});
                pam.setValue("BUInstance", ruInstance.split("_")[1]);
                pam.send();
                logger.info("[HCAL LVL2 " + functionManager.FMname + "]: Just set the BUInstance for " + qr.getName() + " to " +  ruInstance.split("_")[1]);
              }
              if (pamName.equals("EVMinstance")) {
                pam.select(new String[] {"EVMinstance"});
                pam.setValue("EVMinstance", ruInstance.split("_")[1]);
                pam.send();
                logger.info("[HCAL LVL2 " + functionManager.FMname + "]: Just set the EVMinstance for " + qr.getName() + " to " +  ruInstance.split("_")[1]);
              }
              if (pamName.equals("HandleLPM")) {
                pam.select(new String[] {"HandleLPM"});
                pam.setValue("HandleLPM", "true");
                pam.send();
                logger.info("[HCAL LVL2 " + functionManager.FMname + "]: Just set the HandleLPM for " + qr.getName() + " to true");
              }
              ////XXX SIC TODO FIXME WHY DOES THIS CRASH?
              //if (pamName.equals("usePrimaryTCDS")) {
              //  logger.info("[HCAL LVL2 " + functionManager.FMname + "]: Found an xdaqparameter named ReportStateToRCMS (actually usePrimaryTCDS); try to set ReportStateToRCMS (actually usePrimaryTCDS) for " + qr.getName() + " to true");
              //  pam.select(new String[] {"usePrimaryTCDS"});
              //  pam.setValue("usePrimaryTCDS", "false");
              //  pam.send();
              //  logger.info("[HCAL LVL2 " + functionManager.FMname + "]: Just set ReportStateToRCMS (actually usePrimaryTCDS) for " + qr.getName() + " to true");
              //}
            }
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
    Sid = tempSessionId;
    // put the session ID into parameter set
    functionManager.getHCALparameterSet().put(new FunctionManagerParameter<IntegerT>("SID",new IntegerT(Sid)));
    logger.info("[HCAL " + functionManager.FMname + "] Reach the end of getsessionId() ");
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

  // method which returns a password free string
  protected String PasswordFree(String Input) {
    return Input.replaceAll("PASSWORD=[A-Za-z_0-9]+\"|PASSWORD=[A-Za-z_0-9]+,|OracleDBPassword=\"[A-Za-z_0-9]+\"","here_was_something_removed_because_of_security");
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
    String globalParams[] = new String[] {"HCAL_LPMCONTROL", "HCAL_TCDSCONTROL", "HCAL_PICONTROL", "HCAL_TTCCICONTROL", "SUPERVISOR_ERROR", "HCAL_COMMENT", "HCAL_CFGSCRIPT", "RUN_KEY",  "HCAL_TIME_OF_FM_START"};
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

    Hashtable<String, String> globalRenamedParams = new Hashtable<String, String>();
    globalRenamedParams.put(  "LOCAL_RUN_KEY"  ,                 "RUN_CONFIG_SELECTED"        );

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
        Set<String> renamedGlobalParamKeys = globalRenamedParams.keySet();
        Iterator<String> gpi = renamedGlobalParamKeys.iterator();
        String gpKey;
        while (gpi.hasNext()) {
          gpKey = gpi.next();
          publishGlobalParameter( gpKey,globalRenamedParams.get(gpKey));
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
        if (!functionManager.containerhcalRunInfoServer.isEmpty()) {
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
      logger.error(errMessage,e);
      functionManager.sendCMSError(errMessage);
      functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("STATE",new StringT("Error")));
      functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("ACTION_MSG",new StringT(errMessage)));
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
        functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("STATE",new StringT("Error")));
        functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("ACTION_MSG",new StringT(errMessage)));
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
                functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("STATE",new StringT("Error")));
                functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("ACTION_MSG",new StringT("oops - technical difficulties ...")));
                if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; return;}
              }
            }
            else if (toState.equals(HCALStates.INITIAL.getStateString())) {
              if (actualState.equals(HCALStates.RECOVERING.getStateString())) { functionManager.fireEvent(HCALInputs.SETINITIAL); }
              else {
                logger.error(errMessage);
                functionManager.sendCMSError(errMessage);
                functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("STATE",new StringT("Error")));
                functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("ACTION_MSG",new StringT("oops - technical difficulties ...")));
                if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; return;}
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
                functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("STATE",new StringT("Error")));
                functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("ACTION_MSG",new StringT("oops - technical difficulties ...")));
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
                functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("STATE",new StringT("Error")));
                functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("ACTION_MSG",new StringT("oops - technical difficulties ...")));
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
                logger.warn("[HCAL " + functionManager.FMname + "] HCALEventHandler actualState is "+actualState+", but SETSTART ...");
                functionManager.fireEvent(HCALInputs.SETSTART);
              }
              else if (actualState.equals(HCALStates.RESUMING.getStateString()))   { functionManager.fireEvent(HCALInputs.SETRESUME); }
              else if (actualState.equals(HCALStates.HALTING.getStateString()))    { /* do nothing */ }
              else {
                logger.error(errMessage);
                functionManager.sendCMSError(errMessage);
                functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("STATE",new StringT("Error")));
                functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("ACTION_MSG",new StringT("oops - technical difficulties ...")));
                if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; return;}
              }
            }
            else if (toState.equals(HCALStates.PAUSED.getStateString())) {
              if (actualState.equals(HCALStates.PAUSING.getStateString()))         { functionManager.fireEvent(HCALInputs.SETPAUSE); }
              else if (actualState.equals(HCALStates.RECOVERING.getStateString())) { functionManager.fireEvent(HCALInputs.SETPAUSE); }
              else {
                logger.error(errMessage);
                functionManager.sendCMSError(errMessage);
                functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("STATE",new StringT("Error")));
                functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("ACTION_MSG",new StringT("oops - technical difficulties ...")));
                if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; return;}
              }
            }
            else if (toState.equals(HCALStates.STOPPING.getStateString())) {
              if (actualState.equals(HCALStates.RUNNING.getStateString()) || actualState.equals(HCALStates.RUNNINGDEGRADED.getStateString()))       { functionManager.fireEvent(HCALInputs.STOP); }
              else if (actualState.equals(HCALStates.STARTING.getStateString())) { /* do nothing */ }
              else {
                logger.error(errMessage);
                functionManager.sendCMSError(errMessage);
                functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("STATE",new StringT("Error")));
                functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("ACTION_MSG",new StringT("oops - technical difficulties ...")));
                if (TestMode.equals("off")) { functionManager.firePriorityEvent(HCALInputs.SETERROR); functionManager.ErrorState = true; return;}
              }
            }
            else {
              String errMessage2 = "[HCAL " + functionManager.FMname + "] Error! transitional state not found in computeNewState() for FM\n@ URI: " + functionManager.getURI() + "\nthe Resource: " + newState.getIdentifier() + " reports an error state!\nFrom state: " + functionManager.getState().getStateString() + " \nstate: " + functionManager.calcState.getStateString();
              logger.error(errMessage2);
              functionManager.sendCMSError(errMessage2);
              functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("STATE",new StringT("Error")));
              functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("ACTION_MSG",new StringT("oops - technical difficulties ...")));
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
        // See comment for function parseFedEnableMask for information about the fedMaskWord. In short, fedMaskWord==3 means enabled.
        fedStatusMap.put(fedId, (fedMaskWord.testBit(0) && fedMaskWord.testBit(1) && !fedMaskWord.testBit(2) && !fedMaskWord.testBit(3)));
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
        if (functionManager.FMrole.equals("HCAL") && icount%2==0) {
          // move the little fishy when configuring
          if ((functionManager != null) && (functionManager.isDestroyed() == false) && (functionManager.getState().getStateString().equals(HCALStates.CONFIGURING.toString()))) {
            LittleA.movehim();
          }
          // move the little fishy when running
          if ((functionManager != null) && (functionManager.isDestroyed() == false) && functionManager.getState().getStateString().equals(HCALStates.RUNNING.toString()) ) {
            LittleB.movehim();
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
        Date now = Calendar.getInstance().getTime();

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
              String progress = "undefined";
              String taname   = "undefined";

              // ask for the status of the HCAL supervisor
              for (QualifiedResource qr : functionManager.containerhcalSupervisor.getApplications() ){
                try {
                  pam =((XdaqApplication)qr).getXDAQParameter();
                  pam.select(new String[] {"TriggerAdapterName", "PartitionState", "InitializationProgress", "stateName"});
                  pam.get();

                  status = pam.getValue("PartitionState");
                  stateName = pam.getValue("stateName");

                  if (status==null || stateName==null) {
                    String errMessage = "[HCAL " + functionManager.FMname + "] Error! Asking the hcalSupervisor for the PartitionState and stateName to see if it is alive or not resulted in a NULL pointer - this is bad!";
                    functionManager.goToError(errMessage);
                  }

                  logger.debug("[HCAL " + functionManager.FMname + "] asking for the HCAL supervisor PartitionState to see if it is still alive.\n The PartitionState is: " + status);
                }
                catch (XDAQTimeoutException e) {
                  String errMessage = "[HCAL " + functionManager.FMname + "] Error! XDAQTimeoutException: HCALSupervisorWatchThread()\nProbably the HCAL supervisor application is dead.\nCheck the corresponding jobcontrol status ...\nHere is the exception: " +e;
                  functionManager.goToError(errMessage);
                }
                catch (XDAQException e) {
                  String errMessage = "[HCAL " + functionManager.FMname + "] Error! XDAQException: HCALSupervisorWatchThread()\nProbably the HCAL supervisor application is in a bad condition.\nCheck the corresponding jobcontrol status, etc. ...\nHere is the exception: " +e;
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

                    logger.info("[HCAL " + functionManager.FMname + "] state of the TriggerAdapter stateName is: " + status + ".\nThe NextEventNumberString is: " + NextEventNumberString + ". \nThe local completion is: " + localcompletion + " (" + NextEventNumber + "/" + TriggersToTake.doubleValue() + ")");

                    functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("ACTION_MSG",new StringT("The state of the TriggerAdapter is: " + status + ".\nThe NextEventNumberString is: " + NextEventNumberString + ". \nThe local completion is: " + localcompletion + " (" + NextEventNumber + "/" + TriggersToTake.doubleValue() + ")")));

                  }
                  catch (XDAQTimeoutException e) {
                    String errMessage = "[HCAL " + functionManager.FMname + "] Error! XDAQTimeoutException: TriggerAdapterWatchThread()\n Perhaps this application is dead!?";
                    functionManager.goToError(errMessage,e);

                  }
                  catch (XDAQException e) {
                    String errMessage = "[HCAL " + functionManager.FMname + "] Error! XDAQException: TriggerAdapterWatchThread()";
                    functionManager.goToError(errMessage,e);
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
                    logger.warn("[SethLog HCAL " + functionManager.FMname + "] Do functionManager.fireEvent(HCALInputs.STOP)");
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
      logger.warn("[SethLog HCAL " + functionManager.FMname + "] ... stopping TriggerAdapter watchdog thread done.");
      TriggerAdapterWatchThreadList.remove(this);
    }
  }

  // thread which checks the alarmer state
  protected class AlarmerWatchThread extends Thread {
    public AlarmerWatchThread() {
      AlarmerWatchThreadList.add(this);
    }

    public void run() {
      stopAlarmerWatchThread = false;
      try {
        URL alarmerURL = new URL(functionManager.alarmerURL);
      } catch (MalformedURLException e) {
        // in case the URL is bogus, just don't run the thread
        stopAlarmerWatchThread = true;
        logger.warn("[HCAL " + functionManager.FMname + "] HCALEventHandler: alarmerWatchThread: value of alarmerURL is not valid: " + functionManager.alarmerURL + "; not checking alarmer status");
      }

      // poll alarmer status in the Running/RunningDegraded states every 30 sec to see if it is still OK/alive
      while ((stopAlarmerWatchThread == false) && (functionManager != null) && (functionManager.isDestroyed() == false)) {
        Date now = Calendar.getInstance().getTime();

        if (functionManager.getState().getStateString().equals(HCALStates.RUNNING.toString()) ||
            functionManager.getState().getStateString().equals(HCALStates.RUNNINGDEGRADED.toString()) ) {
          try {
            // ask for the status of the HCAL alarmer
            // ("http://hcalmon.cms:9945","hcalAlarmer",0);
            XDAQParameter pam = new XDAQParameter(functionManager.alarmerURL,"hcalAlarmer",0);
            // this does a lazy get. do we need to force the update before getting it?
            //logger.info("[SethLog] HCALEventHandler: alarmerWatchThread: value of alarmer parameter GlobalStatus is " + pam.getValue("GlobalStatus"));
            String alarmerStatusValue = "";
            String alarmerStatusName  = "GlobalStatus";
            if (functionManager.RunType.equals("global") ){
              logger.info("[Martin Log "+functionManager.FMname +"] Going to watch this alarmer status: "+functionManager.alarmerPartition); 
              if(functionManager.alarmerPartition.equals("HBHEHO")){
                alarmerStatusName = "GlobalStatus";
              }
              if(functionManager.alarmerPartition.equals("HF")){
                alarmerStatusName = "HFStatus";
              }
              pam.select(new String[] {alarmerStatusName});
              pam.get();
              alarmerStatusValue = pam.getValue(alarmerStatusName);

              if( alarmerStatusValue.equals("")){
                String errMessage="[Martin Log "+functionManager.FMname +"] Cannot get alarmerStatusValue with parition name: "+functionManager.alarmerPartition;
                logger.warn(errMessage); 
              }
              if( alarmerStatusValue.equals("OK")){
                logger.info("[Martin Log "+functionManager.FMname +"] The alarmerStatus of partition "+functionManager.alarmerPartition+" with name "+alarmerStatusName+" is OK"); 
              }
              if (!alarmerStatusValue.equals("OK")) {
                // go to degraded state if needed
                if(!functionManager.getState().getStateString().equals(HCALStates.RUNNINGDEGRADED.toString())) {
                  logger.warn("[HCAL " + functionManager.FMname + "] HCALEventHandler: alarmerWatchThread: value of alarmer parameter "+ alarmerStatusName +" is " + alarmerStatusValue + " which is not OK; going to RUNNINGDEGRADED state");
                  functionManager.fireEvent(HCALInputs.SETRUNNINGDEGRADED);
                  if(functionManager.alarmerPartition.equals("HBHEHO")) functionManager.setAction("><))),> : HCAL is in RunningDegraded, please contact HCAL DOC!");
                  if(functionManager.alarmerPartition.equals("HF"))     functionManager.setAction("><))),> : HF is in RunningDegraded, please contact HCAL DOC!");
                }
                else {
                  logger.warn("[HCAL " + functionManager.FMname + "] HCALEventHandler: alarmerWatchThread: value of alarmer parameter "+alarmerStatusName +" is " +alarmerStatusValue +" which is not OK; going to stay in RUNNINGDEGRADED state");
                  if(functionManager.alarmerPartition.equals("HBHEHO")) functionManager.setAction("><))),> : HCAL is in RunningDegraded, please contact HCAL DOC!");
                  if(functionManager.alarmerPartition.equals("HF"))     functionManager.setAction("><))),> : HF is in RunningDegraded, please contact HCAL DOC!");
                }
              }
              else if(functionManager.getState().getStateString().equals(HCALStates.RUNNINGDEGRADED.toString())) {
                // if we got back to OK, go back to RUNNING
                logger.warn("[HCAL " + functionManager.FMname + "] HCALEventHandler: alarmerWatchThread: value of alarmer parameter "+alarmerStatusName+" is " + alarmerStatusValue + " which should be OK; going to get out of RUNNINGDEGRADED state now");
                functionManager.fireEvent(HCALInputs.UNSETRUNNINGDEGRADED);
              }
            }
            else {
              // Assume we're in local if the RunType is not global. Watch both HBHEHO and HF status.
              logger.info("[Martin Log "+functionManager.FMname +"] We are in local, going to watch both HBHEHO and HF status "); 
              pam.select(new String[] {"GlobalStatus","HFStatus"});
              pam.get();
              String alarmerStatusValue_HBHEHO = pam.getValue("GlobalStatus");
              String alarmerStatusValue_HF     = pam.getValue("HFStatus");

              if( alarmerStatusValue_HBHEHO.equals("")|| alarmerStatusValue_HF.equals("") ){
                String errMessage="[Martin Log "+functionManager.FMname +"] Cannot get alarmerStatusValue in local mode";
                logger.warn(errMessage); 
              }

              if (!alarmerStatusValue_HBHEHO.equals("OK") || !alarmerStatusValue_HF.equals("OK") ) {
                // go to degraded state if needed
                if(!functionManager.getState().getStateString().equals(HCALStates.RUNNINGDEGRADED.toString())) {
                  logger.warn("[HCAL " + functionManager.FMname + "] HCALEventHandler: alarmerWatchThread: value of alarmer parameter GlobalStatus is " + pam.getValue("GlobalStatus") + " and HFStatus is "+ pam.getValue("HFStatus") +" which is not both OK; going to RUNNINGDEGRADED state");
                  functionManager.fireEvent(HCALInputs.SETRUNNINGDEGRADED);
                  functionManager.setAction("><))),> : HCAL/HF is in RunningDegraded, please contact HCAL DOC!!!");
                }
                else {
                  logger.warn("[HCAL " + functionManager.FMname + "] HCALEventHandler: alarmerWatchThread: value of alarmer parameter GlobalStatus is " + pam.getValue("GlobalStatus") + " and HFStatus is "+ pam.getValue("HFStatus") +" which is not both OK; going to stay in RUNNINGDEGRADED state"); 
                  functionManager.setAction("><))),> : HCAL/HF is in RunningDegraded, please contact HCAL DOC!!!");
                }
              }
              else if(functionManager.getState().getStateString().equals(HCALStates.RUNNINGDEGRADED.toString())) {
                // if we got back to OK, go back to RUNNING
                logger.warn("[HCAL " + functionManager.FMname + "] HCALEventHandler: alarmerWatchThread: value of alarmer parameter GlobalStatus is " + pam.getValue("GlobalStatus") + " and HFStatus is "+ pam.getValue("HFStatus") +" which is both OK; going to get out of RUNNINGDEGRADED state"); 
                functionManager.fireEvent(HCALInputs.UNSETRUNNINGDEGRADED);
              }
            }
          }
          catch (Exception e) {
            // on exceptions, we go to degraded, or stay there
            if(!functionManager.getState().getStateString().equals(HCALStates.RUNNINGDEGRADED.toString())) {
              String errMessage = "[HCAL " + functionManager.FMname + "] Error! Got an exception: AlarmerWatchThread()\n...\nHere is the exception: " +e+"\n...going to change to RUNNINGDEGRADED state";
              logger.error(errMessage);
              functionManager.fireEvent(HCALInputs.SETRUNNINGDEGRADED);
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
      //logger.debug("[HCAL " + functionManager.FMname + "] ... stopping HCAL supervisor watchdog thread done.");
      AlarmerWatchThreadList.remove(this);
    }
  }
  
  // Function to receive parameter
  void CheckAndSetParameter(ParameterSet pSet , String PamName) throws UserActionException{

    if( pSet.get(PamName) != null){
      if (pSet.get(PamName).getType().equals(StringT.class)){
        String PamValue = ((StringT)pSet.get(PamName).getValue()).getString();
        functionManager.getParameterSet().put(new FunctionManagerParameter<StringT>(PamName, new StringT(PamValue)));
        logger.info("[HCAL "+ functionManager.FMname +" ] Received and set "+ PamName +" from last input. Here it is: \n"+ PamValue);
      }
      if (pSet.get(PamName).getType().equals(IntegerT.class)){
        Integer PamValue = ((IntegerT)pSet.get(PamName).getValue()).getInteger();
        functionManager.getParameterSet().put(new FunctionManagerParameter<IntegerT>(PamName, new IntegerT(PamValue)));
        logger.info("[HCAL "+ functionManager.FMname +" ] Received and set "+ PamName +" from last input. Here it is: \n"+ PamValue);
      }
      if (pSet.get(PamName).getType().equals(BooleanT.class)){
        Boolean PamValue = ((BooleanT)pSet.get(PamName).getValue()).getBoolean();
        functionManager.getParameterSet().put(new FunctionManagerParameter<BooleanT>(PamName, new BooleanT(PamValue)));
        logger.info("[HCAL "+ functionManager.FMname +" ] Received and set "+ PamName +" from last input. Here it is: \n"+ PamValue);
      }
    }
    else{
      String errMessage =" Did not receive "+ PamName +" from last input! Please check if "+ PamName+ " was filled";
      logger.warn(errMessage);
      throw new UserActionException(errMessage);
    }
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

}
