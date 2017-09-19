package rcms.fm.app.level1;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.util.Arrays;
import java.util.Map;
import java.util.HashMap;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;

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
import org.w3c.dom.DOMException;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import rcms.fm.fw.user.UserActionException;

import rcms.fm.fw.parameter.FunctionManagerParameter;
import rcms.fm.fw.parameter.ParameterSet;
import rcms.fm.fw.parameter.type.StringT;
import rcms.fm.fw.parameter.type.IntegerT;
import rcms.fm.fw.parameter.type.BooleanT;
import rcms.fm.fw.parameter.type.VectorT;
import rcms.fm.fw.parameter.type.ByteT;
import rcms.fm.fw.parameter.type.DateT;
import rcms.fm.fw.parameter.type.DoubleT;
import rcms.fm.fw.parameter.type.FloatT;
import rcms.fm.fw.parameter.type.LongT;
import rcms.fm.fw.parameter.type.ShortT;
import rcms.fm.fw.parameter.type.UnsignedIntegerT;
import rcms.fm.fw.parameter.type.UnsignedShortT;
import rcms.fm.fw.parameter.type.MapT;

import rcms.resourceservice.db.resource.fm.FunctionManagerResource;
import rcms.util.logger.RCMSLogger;

/**
 *  @author John Hakala
 *
 */

public class HCALxmlHandler {

  protected HCALFunctionManager functionManager = null;
  static RCMSLogger logger = null;
  public DocumentBuilder docBuilder;
  public String[] ValidMasterSnippetTags = new String[] {"CfgScript","ICIControlSingle","ICIControlMulti","TTCciControl","LPMControl","PIControlSingle","PIControlMulti","LTCControl","AlarmerURL","AlarmerStatus","FedEnableMask","FMSettings","FMParameter","DQM_TASK"};

  public HCALxmlHandler(HCALFunctionManager parentFunctionManager) {
    this.logger = new RCMSLogger(HCALFunctionManager.class);
    logger.warn("Constructing xmlHandler.");
    this.functionManager = parentFunctionManager;
    logger.warn("Done constructing xmlHandler.");
  }


  public Element getHCALuserXML() throws UserActionException {
    try {
      // return the userXML
      String userXmlString = "<userXML>" + ((FunctionManagerResource)functionManager.getQualifiedGroup().getGroup().getThisResource()).getUserXml() + "</userXML>";

      logger.debug("[HCAL " + functionManager.FMname + "]: got the userXML.");
      docBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
      InputSource inputSource = new InputSource();
      inputSource.setCharacterStream(new StringReader(userXmlString));
      Document hcalUserXML = docBuilder.parse(inputSource);
      hcalUserXML.getDocumentElement().normalize();
      logger.debug("[HCAL " + functionManager.FMname + "]: formatted the userXML.");
      return hcalUserXML.getDocumentElement();
    }
    catch (DOMException | ParserConfigurationException | SAXException | IOException e) {
      String errMessage = "[HCAL " + functionManager.FMname + "]: Got an error when trying to retrieve the userXML: " + e.getMessage();
      logger.error(errMessage);
      throw new UserActionException(errMessage);
    }
  }

  // Get userXML from a CfgCVS path
  public Element getHCALuserXML(String CfgCVSBasePath,String fileName) throws UserActionException {
    try {
      // return the userXML
      File grandMaster = new File(CfgCVSBasePath+fileName+"/pro");
      String userXmlString ="";
      if (grandMaster.exists()){
        userXmlString = "<userXML>" + new String(Files.readAllBytes(Paths.get(CfgCVSBasePath+fileName+"/pro"))) + "</userXML>";
      }
      else{
        String errMessage="[HCAL "+functionManager.FMname+"] Cannot find grandMaster snippet with CfgCVSBasePath ="+CfgCVSBasePath+" and MasterSnippetList="+fileName+".";
        throw new UserActionException(errMessage);
        //functionManager.goToError(errMessage);
      }
      logger.debug("[HCAL " + functionManager.FMname + "]: got the userXML :"+ userXmlString);
      docBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
      InputSource inputSource = new InputSource();
      inputSource.setCharacterStream(new StringReader(userXmlString));
      Document hcalUserXML = docBuilder.parse(inputSource);
      hcalUserXML.getDocumentElement().normalize();
      return hcalUserXML.getDocumentElement();
    }
    catch (DOMException | ParserConfigurationException | SAXException | IOException e) {
      String errMessage = "[HCAL " + functionManager.FMname + "]: Got an error when trying to retrieve the grandmaster snippet:" + e.getMessage();
      functionManager.goToError(errMessage);
      throw new UserActionException(errMessage);
    }
  }

  public String getHCALuserXMLelementContent(String tagName,Boolean isGrandMaster) throws UserActionException {
      String CfgCVSBasePath    = ((StringT) functionManager.getHCALparameterSet().get("HCAL_CFGCVSBASEPATH").getValue()).getString();
      String MasterSnippetList = ((StringT) functionManager.getHCALparameterSet().get("HCAL_MASTERSNIPPETLIST").getValue()).getString();
      Element hcalUserXML = null;
    try {
      if (!isGrandMaster){
        hcalUserXML = getHCALuserXML();
      }
      else{
        hcalUserXML = getHCALuserXML(CfgCVSBasePath,MasterSnippetList);
      }
    }
    catch(UserActionException e){
      throw e;
    }
    try{
      if (!hcalUserXML.equals(null) && !hcalUserXML.getElementsByTagName(tagName).equals(null)) {
        if (hcalUserXML.getElementsByTagName(tagName).getLength()==1) {
          return hcalUserXML.getElementsByTagName(tagName).item(0).getTextContent();
        }
        else {
          String errMessage = (hcalUserXML.getElementsByTagName(tagName).getLength()==0) ? " was not found in the userXML. Will use value supplied by level1 or default value." : " was found with more than one occurrance in the userXML.";
          throw new UserActionException("[HCAL " + functionManager.FMname + "]: The userXML element with tag name '" + tagName + "'" + errMessage);
        }
      }
      else return "";
    }     
    catch (UserActionException e) {throw e;}
  }

  public String getNamedUserXMLelementAttributeValue (String tag, String name, String attribute, Boolean isGrandMaster ) throws UserActionException {
    try {
      boolean foundTheRequestedNamedElement = false;
      String CfgCVSBasePath    = ((StringT) functionManager.getHCALparameterSet().get("HCAL_CFGCVSBASEPATH").getValue()).getString();
      String MasterSnippetList = ((StringT) functionManager.getHCALparameterSet().get("HCAL_MASTERSNIPPETLIST").getValue()).getString();
      Element hcalUserXML=null;
      if (!isGrandMaster){
        hcalUserXML = getHCALuserXML();
      }
      else{
        hcalUserXML = getHCALuserXML(CfgCVSBasePath,MasterSnippetList);
      }
      if (!hcalUserXML.equals(null) && !hcalUserXML.getElementsByTagName(tag).equals(null)) {
        if (hcalUserXML.getElementsByTagName(tag).getLength()!=0) {
          NodeList nodes = hcalUserXML.getElementsByTagName(tag); 
          logger.warn("[JohnLog3] " + functionManager.FMname + ": the length of the list of nodes with tag name '" + tag + "' is: " + nodes.getLength());
          for (int iNode = 0; iNode < nodes.getLength(); iNode++) {
            logger.warn("[JohnLog3] " + functionManager.FMname + " found a userXML element with tagname '" + tag + "' and name '" + ((Element)nodes.item(iNode)).getAttributes().getNamedItem("name").getNodeValue()  + "'"); 
            if (((Element)nodes.item(iNode)).getAttributes().getNamedItem("name").getNodeValue().equals(name)) {
               foundTheRequestedNamedElement = true;
               if ( ((Element)nodes.item(iNode)).hasAttribute(attribute)) {
                  return ((Element)nodes.item(iNode)).getAttributes().getNamedItem(attribute).getNodeValue();
               }else{
                  logger.warn("[Martin log "+functionManager.FMname+"] Does not found the attribute='"+attribute+"' with name='"+name+"' in tag='"+tag+"'. Empty string will be returned");
                  String emptyString = "";
                  return emptyString;
               }
            }
          }
          if (!foundTheRequestedNamedElement) {
            String errMessage = "[JohnLog3] " + functionManager.FMname + ": this FM requested the value of the attribute '" + attribute + "' from a userXML element with tag '" + tag + "' and name '" + name + "' but that name did not exist in that element. Empty String is returned.";
            logger.warn(errMessage);
            String emptyString = "";
            return emptyString;
            //throw new UserActionException("[HCAL " + functionManager.FMname + "]: " + errMessage);
          }
        }
        else {
          //throw new UserActionException("[HCAL " + functionManager.FMname + "]: A userXML element with tag name '" + tag + "'" + "was not found in the userXML. Empty String will be returned.");
          logger.warn("[HCAL " + functionManager.FMname + "]: A userXML element with tag name '" + tag + "'" + "was not found in the userXML. Empty String will be returned.");
          String emptyElement="";
          return  emptyElement;
        }
      }
      else {
        throw new UserActionException("[HCAL " + functionManager.FMname + "]: The userXML or the userXML element with tag name '" + tag + "'" + "was null.");
      }
    }     
    catch (UserActionException e) {throw e;}
    logger.warn("[JohnLog3] " + functionManager.FMname + ": Got to a bad place!");
    return null;
  }

  public String stripExecXML(String execXMLstring, ParameterSet<FunctionManagerParameter> parameterSet) throws UserActionException{
    try {

      // Get the list of master snippets from the userXML and use it to find the mastersnippet file.

      docBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
      InputSource inputSource = new InputSource();
      inputSource.setCharacterStream(new StringReader(execXMLstring));
      Document execXML = docBuilder.parse(inputSource);
      execXML.getDocumentElement().normalize();

      //String maskedAppArray[] = maskedAppsString.substring(0, maskedAppsString.length()-1).split(";");
      VectorT<StringT> maskedAppsVector = (VectorT<StringT>)parameterSet.get("MASKED_RESOURCES").getValue();
      StringT[] maskedAppArray = maskedAppsVector.toArray(new StringT[maskedAppsVector.size()]);
      String newExecXMLstring = "";
      int NxcContexts = 0;
      int removedContexts = 0;
      int removedApplications = 0;
      for (StringT maskedApp: maskedAppArray) {
        //logger.info("[JohnLogVector] " + functionManager.FMname + ": about to start masking " + maskedApp.getString());
        String[] maskedAppParts = maskedApp.getString().split("_");
        //Remove masked applications from xc:Context nodes
        NodeList xcContextNodes = execXML.getDocumentElement().getElementsByTagName("xc:Context");
        NxcContexts = xcContextNodes.getLength();
        removedContexts = 0;
        removedApplications = 0;
        for (int i=0; i < NxcContexts; i++) {
          Element currentContextNode = (Element) xcContextNodes.item(i-removedContexts);
          NodeList xcApplicationNodes = currentContextNode.getElementsByTagName("xc:Application");
          removedApplications = 0;
          for (int j=0; j < xcApplicationNodes.getLength(); j++) {
            Node currentApplicationNode = xcApplicationNodes.item(j-removedApplications);
            String xcApplicationClass = currentApplicationNode.getAttributes().getNamedItem("class").getNodeValue();
            String xcApplicationInstance = xcApplicationNodes.item(j-removedApplications).getAttributes().getNamedItem("instance").getNodeValue();
            if (xcApplicationClass.equals(maskedAppParts[0]) && xcApplicationInstance.equals(maskedAppParts[1])){
              currentApplicationNode.getParentNode().removeChild(currentApplicationNode);
              removedApplications+=1;
            }
            if (currentContextNode.getElementsByTagName("xc:Application").getLength()==0) {
              currentContextNode.getParentNode().removeChild(currentContextNode);
              removedContexts +=1;
            }
          }
        }

        //Remove masked applications' i2o connections from i2o:protocol node
        NodeList i2oTargetNodes = execXML.getDocumentElement().getElementsByTagName("i2o:target");
        int Ni2oTargetNodes = i2oTargetNodes.getLength();
        int removedi2oTargets = 0;
        for (int i=0; i < Ni2oTargetNodes; i++) {
          Node i2oTargetNode = i2oTargetNodes.item(i-removedi2oTargets);
          if (i2oTargetNode.getAttributes().getNamedItem("class").getNodeValue().equals(maskedAppParts[0]) && i2oTargetNode.getAttributes().getNamedItem("instance").getNodeValue().equals(maskedAppParts[1])){
            i2oTargetNode.getParentNode().removeChild(i2oTargetNode);
            removedi2oTargets+=1;
          }
        }
        
        //Remove masked applications' i2o connections from i2o:unicasts node
        NodeList xcUnicastNodes = execXML.getDocumentElement().getElementsByTagName("xc:Unicast");
        int NxcUnicastNodes = xcUnicastNodes.getLength();
        int removedxcUnicasts = 0;
        for (int i=0; i < NxcUnicastNodes; i++) {
          Node xcUnicastNode = xcUnicastNodes.item(i-removedxcUnicasts);
          if (xcUnicastNode.getAttributes().getNamedItem("instance") != null && xcUnicastNode.getAttributes().getNamedItem("class").getNodeValue().equals(maskedAppParts[0]) && xcUnicastNode.getAttributes().getNamedItem("instance").getNodeValue().equals(maskedAppParts[1])){
            logger.debug("[HCAL " + functionManager.FMname + "]: About to remove xc:Unicast node for maskedapp with class " + maskedAppParts[0] + " and instance " + maskedAppParts[1]);
            xcUnicastNode.getParentNode().removeChild(xcUnicastNode);
            removedxcUnicasts+=1;
          }
        }

        //Move the lpm application node into the context that holds the pi and ici
        String  lpm = "tcds::lpm::LPMController";
        //String  pi = "tcds::pi::PIController";
        String  ici = "tcds::ici::ICIController";
        String  ttcci = "ttc::TTCciControl";
        Element lpmApplicationElement = null;
        Element newLPMnodeContext = null;
        xcContextNodes = execXML.getDocumentElement().getElementsByTagName("xc:Context");
        NxcContexts = xcContextNodes.getLength();
        for (int i=0; i < NxcContexts; i++) {
          Element currentContextNode = (Element) xcContextNodes.item(i);
          NodeList xcApplicationNodes = currentContextNode.getElementsByTagName("xc:Application");
          for (int j=0; j < xcApplicationNodes.getLength(); j++) {
            Node currentApplicationNode = xcApplicationNodes.item(j);
            String xcApplicationClass = currentApplicationNode.getAttributes().getNamedItem("class").getNodeValue();
            System.out.println("Item " + i + " has class " + xcApplicationClass + " and instance " + currentApplicationNode.getAttributes().getNamedItem("instance").getNodeValue());
            if (xcApplicationClass.equals(lpm)){
              lpmApplicationElement = (Element) currentApplicationNode.cloneNode(true);
              if (!functionManager.FMrole.equals("Level2_TCDSLPM")) currentApplicationNode.getParentNode().removeChild(currentApplicationNode);
            }
            if (xcApplicationClass.equals(ttcci)){
              //if (!functionManager.FMrole.equals("EvmTrig") && !functionManager.FMname.contains("HCALFM_904Int_TTCci")) {
              if (!functionManager.FMrole.equals("EvmTrig") && !functionManager.FMname.contains("TTCci")) {
                currentApplicationNode.getParentNode().removeChild(currentApplicationNode);
                logger.warn("[JohnLog3] " + functionManager.FMname + ": just removed the ttcci context in an executive.");
              }
            }
            if (xcApplicationClass.equals(ici)){
              newLPMnodeContext = (Element) currentApplicationNode.getParentNode();
            }
          }
        }
        if (lpmApplicationElement!=null && functionManager.FMrole.equals("EvmTrig")){
          if (newLPMnodeContext==null) {
            logger.error("[HCAL " + functionManager.FMname + "]: Could not find a context in this executive that has a PI");
          }
          else {
            newLPMnodeContext.appendChild(lpmApplicationElement);
          }
        }
        
        xcContextNodes = execXML.getDocumentElement().getElementsByTagName("xc:Context");
        NxcContexts = xcContextNodes.getLength();
        int removedLPMContexts = 0;
        for (int i=0; i < NxcContexts; i++) {
          Element currentContextNode = (Element) xcContextNodes.item(i-removedLPMContexts);
          if ( currentContextNode!=null && currentContextNode.getElementsByTagName("*").getLength()==0) {
               currentContextNode.getParentNode().removeChild(currentContextNode);
               removedLPMContexts++;
           }
        } 

        DOMSource domSource = new DOMSource(execXML);
        StringWriter writer = new StringWriter();
        StreamResult result = new StreamResult(writer);
        TransformerFactory tf = TransformerFactory.newInstance();
        Transformer transformer = tf.newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
        transformer.transform(domSource, result);
        newExecXMLstring = writer.toString();
        newExecXMLstring = newExecXMLstring.replaceAll("(?m)^[ \t]*\r?\n", "");
        //logger.info("[JohnLogVector] " + functionManager.FMname + ": done masking " + maskedApp.getString());
      }
      return newExecXMLstring;
    }
    catch (DOMException | IOException | ParserConfigurationException | SAXException | TransformerException e) {
      logger.error("[HCAL " + functionManager.FMname + "]: Got an error while parsing an XDAQ executive's configurationXML: " + e.getMessage());
      throw new UserActionException("[HCAL " + functionManager.FMname + "]: Got an error while parsing an XDAQ executive's configurationXML: " + e.getMessage());
    }
  }  

  public String addStateListenerContext(String execXMLstring, String rcmsStateListenerURL) throws UserActionException{
    String newExecXMLstring = "";
    try {

      //System.out.println(execXMLstring);
      docBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
      InputSource inputSource = new InputSource();
      inputSource.setCharacterStream(new StringReader(execXMLstring));
      Document execXML = docBuilder.parse(inputSource);
      execXML.getDocumentElement().normalize();
      DOMSource domSource = new DOMSource(execXML);

      Element stateListenerContext = execXML.createElement("xc:Context");
      //stateListenerContext.setAttribute("url", "http://cmsrc-hcal.cms:16001/rcms");
      //stateListenerContext.setAttribute("url", "http://cmshcaltb02.cern.ch:16001/rcms");
      //logger.info("[SethLog] " + functionManager.FMname + ": adding the RCMStateListener with url: " + rcmsStateListenerProtocol+"://"+rcmsStateListenerHost+":"+rcmsStateListenerPort+"/rcms" );
      stateListenerContext.setAttribute("url", rcmsStateListenerURL);
      Element stateListenerApp=execXML.createElement("xc:Application");
      stateListenerApp.setAttribute("class", "RCMSStateListener");
      stateListenerApp.setAttribute("id", "50");
      stateListenerApp.setAttribute("instance", "0");
      stateListenerApp.setAttribute("network", "local");
      stateListenerApp.setAttribute("path", "/services/replycommandreceiver");
      stateListenerContext.appendChild(stateListenerApp);
      if (execXML.getDocumentElement().getTagName().equals("xc:Partition")) {
        execXML.getDocumentElement().appendChild(stateListenerContext);
      }

      StringWriter writer = new StringWriter();
      StreamResult result = new StreamResult(writer);
      TransformerFactory tf = TransformerFactory.newInstance();
      Transformer transformer = tf.newTransformer();
      transformer.setOutputProperty(OutputKeys.INDENT, "yes");
      transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
      transformer.transform(domSource, result);
      newExecXMLstring = writer.toString();
      newExecXMLstring = newExecXMLstring.replaceAll("(?m)^[ \t]*\r?\n", "");
      return newExecXMLstring;
    }
    catch (DOMException | IOException | ParserConfigurationException | SAXException | TransformerException e) {
      logger.error("[HCAL " + functionManager.FMname + "]: Got an error while trying to add the RCMSStateListener context to the executive xml: " + e.getMessage());
      throw new UserActionException("[HCAL " + functionManager.FMname + "]: Got an error while trying to add the RCMSStateListener context to the executive xml: " + e.getMessage());
    }
  }


  public String setUTCPConnectOnRequest(String execXMLstring) throws UserActionException{
    try {
      String newExecXMLstring = "";

      docBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
      InputSource inputSource = new InputSource();
      inputSource.setCharacterStream(new StringReader(execXMLstring));
      Document execXML = docBuilder.parse(inputSource);
      execXML.getDocumentElement().normalize();
      DOMSource domSource = new DOMSource(execXML);

      // add the magical attribute to the Endpoints
      NodeList xcEndpointNodes = execXML.getDocumentElement().getElementsByTagName("xc:Endpoint");
      int NxcEndpointNodes = xcEndpointNodes.getLength();
      for (int i=0; i < NxcEndpointNodes; i++) {
        Element currentEndpointElement = (Element) xcEndpointNodes.item(i);
        currentEndpointElement.setAttribute("connectOnRequest", "true");
      }

      StringWriter writer = new StringWriter();
      StreamResult result = new StreamResult(writer);
      TransformerFactory tf = TransformerFactory.newInstance();
      Transformer transformer = tf.newTransformer();
      transformer.setOutputProperty(OutputKeys.INDENT, "yes");
      transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
      transformer.transform(domSource, result);
      newExecXMLstring = writer.toString();
      newExecXMLstring = newExecXMLstring.replaceAll("(?m)^[ \t]*\r?\n", "");
      return newExecXMLstring;
    }
    catch (DOMException | IOException | ParserConfigurationException | SAXException | TransformerException e) {
      logger.error("[HCAL " + functionManager.FMname + "]: setUTCPConnectOnRequest(): Got an error while parsing an XDAQ executive's configurationXML: " + e.getMessage());
      throw new UserActionException("[HCAL " + functionManager.FMname + "]: setUTCPConnectOnRequest(): Got an error while parsing an XDAQ executive's configurationXML: " + e.getMessage());
    }
  }


  // Return the Tag content of TagName in MasterSnippet
  public String getHCALMasterSnippetTag(String selectedRun, String CfgCVSBasePath, String TagName) throws UserActionException{
    String TagContent ="";
    try{
        // Get ControlSequences from mastersnippet
        docBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        Document masterSnippet = docBuilder.parse(new File(CfgCVSBasePath + selectedRun + "/pro"));

        masterSnippet.getDocumentElement().normalize();

        //NodeList TTCciControl =  masterSnippet.getDocumentElement().getElementsByTagName("TTCciControl");
        NodeList TagNodeList =  masterSnippet.getDocumentElement().getElementsByTagName(TagName);
        TagContent = getTagTextContent( TagNodeList, TagName );
    }
    catch ( DOMException | ParserConfigurationException | SAXException | IOException e) {
        logger.error("[HCAL " + functionManager.FMname + "]: Got a error when parsing the "+ TagName +" xml: " + e.getMessage());
    }
    return TagContent;
  }
  
  // Return the attribute value of TagName in MasterSnippet
  public String getHCALMasterSnippetTagAttribute(String selectedRun, String CfgCVSBasePath, String TagName,String attribute) throws UserActionException{
    String tmpAttribute ="";
    try{
        docBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        Document masterSnippet = docBuilder.parse(new File(CfgCVSBasePath + selectedRun + "/pro"));

        masterSnippet.getDocumentElement().normalize();
        NodeList TagNodeList =  masterSnippet.getDocumentElement().getElementsByTagName(TagName);
        
        tmpAttribute = getTagAttribute( TagNodeList, TagName, attribute);
    }
    catch ( DOMException | ParserConfigurationException | SAXException | IOException e) {
        logger.error("[HCAL " + functionManager.FMname + "]: Got a error when parsing the "+ TagName +" xml: " + e.getMessage());
    }
    return tmpAttribute;
  }

  // Fill parameters from MasterSnippet
  public void parseMasterSnippet(String selectedRun, String CfgCVSBasePath, boolean NeventIsSetFromGUI ) throws UserActionException{
    try{
        // Get ControlSequences from mastersnippet
        docBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        Document masterSnippet = docBuilder.parse(new File(CfgCVSBasePath + selectedRun + "/pro"));
        
        masterSnippet.getDocumentElement().normalize();
        Element masterSnippetElement = masterSnippet.getDocumentElement();

        NodeList listOfTags = masterSnippetElement.getChildNodes();
        for(int i =0;i< listOfTags.getLength();i++){
          if( listOfTags.item(i).getNodeType()== Node.ELEMENT_NODE){
            Element iElement = (Element) listOfTags.item(i);
            String  iTagName = iElement.getNodeName();
            Boolean isValidTag = Arrays.asList(ValidMasterSnippetTags).contains( iTagName );
            logger.info("[HCAL "+functionManager.FMname+" ] parseMasterSnippet: Found TagName = "+ iTagName );

            if(isValidTag){
              if (iTagName == "FMParameter") {
                SetHCALFMParameter(iElement);
              } else {
                NodeList iNodeList = masterSnippetElement.getElementsByTagName( iTagName ); 
                SetHCALParameterFromTagName( iTagName , iNodeList, CfgCVSBasePath, NeventIsSetFromGUI);
              }
            }
          }
        }
    }
    catch ( DOMException | ParserConfigurationException | SAXException | IOException e) {
        logger.error("[HCAL " + functionManager.FMname + "]: Got a error when parsing masterSnippet:: " + e.getMessage());
    }
  }

  public String getHCALParameterFromTagName(String TagName){
    String emptyString="";
    if(TagName.equals("ICIControlSingle") ) return "HCAL_ICICONTROL_SINGLE";
    if(TagName.equals("ICIControlMulti") ) return "HCAL_ICICONTROL_MULTI";
    if(TagName.equals("LPMControl")  ) return "HCAL_LPMCONTROL";
    if(TagName.equals("PIControlSingle")   ) return "HCAL_PICONTROL_SINGLE";
    if(TagName.equals("PIControlMulti" )   ) return "HCAL_PICONTROL_MULTI";
    if(TagName.equals("TTCciControl")) return "HCAL_TTCCICONTROL";
    if(TagName.equals("LTCControl")  ) return "HCAL_LTCCONTROL";
    logger.error("[Martin log HCAL "+ functionManager.FMname +"]: Cannot find HCALParameter corresponding to TagName "+ TagName +". Please check the mapping");
    return emptyString;
  }

  public void SetHCALFMParameter(Element fmParameterElement) {
    String parameterName  = fmParameterElement.getAttributes().getNamedItem("name").getNodeValue();
    String parameterType  = fmParameterElement.getAttributes().getNamedItem("type").getNodeValue();
    String parameterValue = "";
    if(!(parameterType.contains("VectorT") || parameterType.contains("MapT"))) {
      parameterValue = fmParameterElement.getAttributes().getNamedItem("value").getNodeValue();
    }
    String[] vectorValues = new String[0];
    if (parameterType.contains("VectorT")) {
      vectorValues = (parameterValue.split(","));
    }

    HashMap<String, String> mapValues = new HashMap<String, String>();
    if (parameterType.contains("MapT")) {
      NodeList childNodes = fmParameterElement.getChildNodes();
      Integer nNodes = childNodes.getLength();
      for (Integer iNode = 0; iNode < nNodes; iNode++) {
        Node thisNode = childNodes.item(iNode);
        if (thisNode.getNodeName() == "entry") {
          mapValues.put(thisNode.getAttributes().getNamedItem("key").getNodeValue(), thisNode.getTextContent());
        }
      }
    }

    try{
      switch (parameterType) {
        case "BooleanT":
        {
          functionManager.getHCALparameterSet().put(new FunctionManagerParameter<BooleanT>(parameterName, new BooleanT(parameterValue)));
          break;
        }
        case "ByteT":
        {
          functionManager.getHCALparameterSet().put(new FunctionManagerParameter<ByteT>(parameterName, new ByteT(parameterValue)));
          break;
        }
        case "DateT":
        {
          functionManager.getHCALparameterSet().put(new FunctionManagerParameter<DateT>(parameterName, new DateT(parameterValue)));
          break;
        }
        case "DoubleT ":
        {
          functionManager.getHCALparameterSet().put(new FunctionManagerParameter<DoubleT>(parameterName, new DoubleT(parameterValue)));
          break;
        }
        case "FloatT":
        {
          functionManager.getHCALparameterSet().put(new FunctionManagerParameter<FloatT>(parameterName, new FloatT(parameterValue)));
          break;
        }
        case "IntegerT":
        {
          functionManager.getHCALparameterSet().put(new FunctionManagerParameter<IntegerT>(parameterName, new IntegerT(parameterValue)));
          break;
        }
        case "LongT":
        {
          functionManager.getHCALparameterSet().put(new FunctionManagerParameter<LongT>(parameterName, new LongT(parameterValue)));
          break;
        }
        case "ShortT":
        {
          functionManager.getHCALparameterSet().put(new FunctionManagerParameter<ShortT>(parameterName, new ShortT(parameterValue)));
          break;
        }
        case "StringT":
        {
          functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>(parameterName, new StringT(parameterValue)));
          break;
        }
        case "UnsignedIntegerT":
        {
          functionManager.getHCALparameterSet().put(new FunctionManagerParameter<UnsignedIntegerT>(parameterName, new UnsignedIntegerT(parameterValue)));
          break;
        }
        case "UnsignedShortT":
        {
          functionManager.getHCALparameterSet().put(new FunctionManagerParameter<UnsignedShortT>(parameterName, new UnsignedShortT(parameterValue)));
          break;
        }
        case "VectorT(StringT)":
        {
          VectorT<StringT> tmpVector = new VectorT<StringT>();
          for (String vectorElement : vectorValues) {
            tmpVector.add(new StringT(vectorElement));
          }
          functionManager.getHCALparameterSet().put(new FunctionManagerParameter<VectorT<StringT> >(parameterName, tmpVector));
          break;
        }
        case "VectorT(IntegerT)":
        {
          VectorT<IntegerT> tmpVector = new VectorT<IntegerT>();
          for (String vectorElement : vectorValues) {
            tmpVector.add(new IntegerT(Integer.parseInt(vectorElement)));
          }
          functionManager.getHCALparameterSet().put(new FunctionManagerParameter<VectorT<IntegerT> >(parameterName, tmpVector));
          break;
        }
        case "MapT(StringT)":
        {
          MapT< StringT> tmpMap = new MapT<StringT>();
          for (Map.Entry<String, String> entry : mapValues.entrySet()) {
            tmpMap.put(new StringT(entry.getKey()), new StringT(entry.getValue()));
          }
          functionManager.getHCALparameterSet().put(new FunctionManagerParameter<MapT<StringT>>(parameterName, tmpMap));
          break;
        }
        case "MapT(VectorT(IntegerT))":
        {
          MapT< VectorT<IntegerT> > tmpMap = new MapT< VectorT<IntegerT> >();
          for (Map.Entry<String, String> entry : mapValues.entrySet()) {
            VectorT<IntegerT> tmpVector = new VectorT<IntegerT>();
            for (String vectorEntry : entry.getValue().split(",")) {
              tmpVector.add(new IntegerT(Integer.parseInt(vectorEntry)));
            }
            tmpMap.put(entry.getKey(), tmpVector);
          }
          functionManager.getHCALparameterSet().put(new FunctionManagerParameter<MapT<VectorT<IntegerT> > >(parameterName, tmpMap));
          break;
        }
        default:
        {
          String errMessage="[David log HCAL " + functionManager.FMname + "] Unknown FMParameter type (" + parameterType + ") for FMParameter named " + parameterName; 
          throw new UserActionException(errMessage);
        }
      }
    } catch (UserActionException e) {
      // Warn when found more than one tag name in mastersnippet
      functionManager.goToError(e.getMessage());
    }
  }


  public void SetHCALParameterFromTagName(String TagName, NodeList NodeListOfTagName ,String CfgCVSBasePath, boolean NeventIsSetFromGUI){
    try{
      if(TagName.equals("ICIControlSingle")|| TagName.equals("ICIControlMulti") || TagName.equals("LPMControl")|| TagName.equals("PIControlSingle")||TagName.equals("PIControlMulti") || TagName.equals("TTCciControl") || TagName.equals("LTCControl") ){
          String HCALParameter = getHCALParameterFromTagName(TagName);
          String ControlSequence  = getIncludeFiles( NodeListOfTagName, CfgCVSBasePath ,TagName );
          functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>(HCALParameter ,new StringT(ControlSequence)));
      }
      if(TagName.equals("AlarmerURL")){
          functionManager.alarmerURL        = getTagTextContent(NodeListOfTagName, TagName );
      }
      if(TagName.equals("AlarmerStatus")) {
          functionManager.alarmerPartition  = getTagAttribute(NodeListOfTagName,TagName,"partition" );
      }
      if(TagName.equals("FMSettings")){
          //Set the parameters if the attribute exists in the element, otherwise will use default in HCALParameter
          String StringNumberOfEvents       = getTagAttribute(NodeListOfTagName, TagName,"NumberOfEvents");
          if(NeventIsSetFromGUI){
            logger.info("[HCAL LVL1 "+functionManager.FMname+" Number of Events already set to "+ functionManager.getHCALparameterSet().get("NUMBER_OF_EVENTS").getValue()+" from GUI. Not over-writting");
          }
          else{
            if( !StringNumberOfEvents.equals("")){
               Integer NumberOfEvents           = Integer.valueOf(StringNumberOfEvents);
               functionManager.getHCALparameterSet().put(new FunctionManagerParameter<IntegerT>("NUMBER_OF_EVENTS",new IntegerT(NumberOfEvents)));
            }
          }
          //Set the parameters if the attribute exists in the element, otherwise will use default in HCALParameter
          String  StringRunInfoPublish      = getTagAttribute(NodeListOfTagName, TagName,"RunInfoPublish");
          if( !StringRunInfoPublish.equals("")){
            Boolean RunInfoPublish           = Boolean.valueOf(StringRunInfoPublish);
            functionManager.getHCALparameterSet().put(new FunctionManagerParameter<BooleanT>("HCAL_RUNINFOPUBLISH",new BooleanT(RunInfoPublish)));
          }

          //Set the parameters if the attribute exists in the element, otherwise will use default in HCALParameter
          String  StringOfficialRunNumbers  = getTagAttribute(NodeListOfTagName, TagName,"OfficialRunNumbers");
          if( !StringOfficialRunNumbers.equals("")){
            Boolean OfficialRunNumbers      = Boolean.valueOf(StringOfficialRunNumbers);
            functionManager.getHCALparameterSet().put(new FunctionManagerParameter<BooleanT>("OFFICIAL_RUN_NUMBERS",new BooleanT(OfficialRunNumbers)));
          }
      }
      if(TagName.equals("CfgScript")){
          String tmpCfgScript =""; 
          if( !hasDefaultValue("HCAL_CFGSCRIPT","not set") ){
            //If the parameter is filled (by CommonMasterSnippet), add that first instead of overwriting
            tmpCfgScript   = ((StringT)functionManager.getHCALparameterSet().get("HCAL_CFGSCRIPT").getValue()).getString();
            tmpCfgScript  += getTagTextContent( NodeListOfTagName, TagName);
          }
          else{
            //If the parameter has defaultValue, put what is in the current mastersnippet in the parameter
            tmpCfgScript   = getTagTextContent( NodeListOfTagName, TagName);
          }
          functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("HCAL_CFGSCRIPT",new StringT(tmpCfgScript)));
      }
      if(TagName.equals("FedEnableMask")){
        if (functionManager.RunType.equals("local")){
          String tmpFedEnableMask = getTagTextContent( NodeListOfTagName, TagName);
          functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("FED_ENABLE_MASK",new StringT(tmpFedEnableMask)));
        }
      }
      if(TagName.equals("DQM_TASK")){
          String dqmtask = getTagTextContent( NodeListOfTagName, TagName);
          functionManager.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("DQM_TASK",new StringT(dqmtask)));
      }
    } catch (UserActionException e) {
      // Warn when found more than one tag name in mastersnippet
      functionManager.goToError(e.getMessage());
    }
  }

  public boolean hasDefaultValue(String pam, String def_value){
        String present_value = ((StringT)functionManager.getHCALparameterSet().get(pam).getValue()).getString();
        //logger.info("[Martin log HCAL "+functionManager.FMname+"] the present value of "+pam+" is "+present_value);
        if (present_value.equals(def_value)){
          return true;
        }else{
          return false;
        }
  }
  public boolean hasDefaultValue(String pam, Integer def_value){
        Integer present_value = ((IntegerT)functionManager.getHCALparameterSet().get(pam).getValue()).getInteger();
        //logger.info("[Martin log HCAL "+functionManager.FMname+"] the present value of "+pam+" is "+present_value);
        if (present_value.equals(def_value)){
          return true;
        }else{
          return false;
        }
  }

  public boolean hasUniqueTag(NodeList inputlist, String TagName) throws UserActionException{
    boolean isUnique=false;
    if( inputlist.getLength()==0){
      //Return false if no Tagname is found
      logger.info("[HCAL " + functionManager.FMname + "]: Cannot find "+ TagName+ ".  Empty string will be returned. ");
    } 
    else if(inputlist.getLength()>1){
        //Throw execptions if more than 1 TagName is found, decide later what to do
        String errMessage="[HCAL " + functionManager.FMname + "]: Found more than one Tag of name: "+ TagName+ ".";
        throw new UserActionException(errMessage);
      }
      else if(inputlist.getLength()==1){
          //Return True if only 1 TagName is found.
          logger.debug("[HCAL " + functionManager.FMname + "]: Found 1 "+ TagName+ ". Going to parse it. ");
          isUnique=true;
      }
    return isUnique;
  }
  public String getTagTextContent(NodeList inputlist, String TagName) throws UserActionException{  
    String TagContent = "";
    boolean HasUniqueTag = false;
    //Return empty string if we do not have a unique Tag in mastersnippet. 
    if( !hasUniqueTag(inputlist,TagName) ){
      return TagContent;
    }
    else{
      TagContent = inputlist.item(0).getTextContent();  
      return TagContent;
    }
  } 
  public String getTagAttribute(NodeList inputlist,String TagName, String attribute) throws UserActionException{
    String tmpAttribute= "";
    //Return empty string if we do not have a unique Tag in mastersnippet. 
    if( !hasUniqueTag(inputlist,TagName) ){
      return tmpAttribute;
    }
    else{
      //Return the attribute content if the TagElement has the correct attribute
      Element TagElement = (Element) inputlist.item(0);
      if (TagElement.hasAttribute(attribute)){
          logger.info("[Martin log HCAL " + functionManager.FMname + "]: Found attribute "+attribute+ " in Tag named "+ TagName+ " in mastersnippet."); 
          tmpAttribute = TagElement.getAttributes().getNamedItem(attribute).getNodeValue();
      }
      else{
          logger.warn("[Martin log "+functionManager.FMname+"] Did not find the attribute='"+attribute+" in tag='"+TagName+"'.");
          return tmpAttribute;
      }
      return tmpAttribute;
    }
  } 

  //  get the TagName, loop over all the "include" sub-tags, read all the content in "file" with the "pro" version.
  public String getIncludeFiles(NodeList inputlist,String CfgCVSBasePath, String TagName) throws UserActionException{
    String tmpCtrlSequence ="";
    //Return empty string if we do not have a unique Tag in mastersnippet. 
    if( !hasUniqueTag(inputlist,TagName) ){
      return tmpCtrlSequence;
    }
    else{
      try{
        //Loop through all the files
        Element el = (Element) inputlist.item(0);
        NodeList childlist = el.getElementsByTagName("include"); 
        for(int iFile=0; iFile< childlist.getLength() ; iFile++){
          Element iElement = (Element) childlist.item(iFile);
          String fname = CfgCVSBasePath + iElement.getAttribute("file").substring(1)+"/"+ iElement.getAttribute("version");
          logger.info("[Martin log HCAL " + functionManager.FMname + "]: Going to read the file of this node from " + fname) ;
          tmpCtrlSequence += readFile(fname,Charset.defaultCharset());
        }
      }
      catch (IOException e){
        logger.error("[HCAL " + functionManager.FMname + "]: Got an IOExecption when parsing this TagName: "+ TagName +", with errorMessage: " + e.getMessage());        
      }
    }
    return tmpCtrlSequence;
  }

  static String readFile(String path, Charset encoding) throws IOException {
      byte[] encoded = Files.readAllBytes(Paths.get(path));
      return new String(encoded, encoding);
   }  
}
