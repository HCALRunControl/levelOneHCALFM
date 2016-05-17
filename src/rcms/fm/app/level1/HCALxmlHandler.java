package rcms.fm.app.level1;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.util.Scanner;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.URL;
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
import org.w3c.dom.Text;
import org.w3c.dom.DOMException;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import rcms.fm.fw.user.UserActionException;

import rcms.fm.fw.parameter.FunctionManagerParameter;
import rcms.fm.fw.parameter.ParameterSet;
import rcms.fm.fw.parameter.type.StringT;
import rcms.resourceservice.db.resource.fm.FunctionManagerResource;
import rcms.util.logger.RCMSLogger;
import rcms.util.logsession.LogSessionException;

/**
 *  @author John Hakala
 *
 */

public class HCALxmlHandler {

  protected HCALFunctionManager functionManager = null;
  static RCMSLogger logger = null;
  public DocumentBuilder docBuilder;

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
  public String getHCALuserXMLelementContent(String tagName) throws UserActionException {
    try {
      Element hcalUserXML = getHCALuserXML();
      if (!hcalUserXML.equals(null) && !hcalUserXML.getElementsByTagName(tagName).equals(null)) {
        if (hcalUserXML.getElementsByTagName(tagName).getLength()==1) {
          return hcalUserXML.getElementsByTagName(tagName).item(0).getTextContent();
        }
        else {
          String errMessage = (hcalUserXML.getElementsByTagName(tagName).getLength()==0) ? " was not found in the userXML. Will use value supplied by level1 or default value." : " was found with more than one occurrance in the userXML.";
          throw new UserActionException("[HCAL " + functionManager.FMname + "]: The userXML element with tag name '" + tagName + "'" + errMessage);
        }
      }
      else return null;
    }     
    catch (UserActionException e) {throw e;}
  }

  public String getNamedUserXMLelementAttributeValue (String tag, String name, String attribute ) throws UserActionException {
    try {
      boolean foundTheRequestedNamedElement = false;
      Element hcalUserXML = getHCALuserXML();
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
          throw new UserActionException("[HCAL " + functionManager.FMname + "]: A userXML element with tag name '" + tag + "'" + "was not found in the userXML.");
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


      String maskedAppsString= ((StringT)parameterSet.get(HCALParameters.MASKED_RESOURCES).getValue()).getString();
      String maskedAppArray[] = maskedAppsString.substring(0, maskedAppsString.length()-1).split(";");
      String newExecXMLstring = "";
      int NxcContexts = 0;
      int removedContexts = 0;
      int removedApplications = 0;
      for (String maskedApp: maskedAppArray) {
        String[] maskedAppParts = maskedApp.split("_");

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
              if (!functionManager.FMrole.equals("EvmTrig") && !functionManager.FMname.contains("HCALFM_904Int_TTCci")) {
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
      }
      return newExecXMLstring;
    }
    catch (DOMException | IOException | ParserConfigurationException | SAXException | TransformerException e) {
      logger.error("[HCAL " + functionManager.FMname + "]: Got an error while parsing an XDAQ executive's configurationXML: " + e.getMessage());
      throw new UserActionException("[HCAL " + functionManager.FMname + "]: Got an error while parsing an XDAQ executive's configurationXML: " + e.getMessage());
    }
  }  
  public String addStateListenerContext(String execXMLstring, String rcmsStateListenerURL) throws UserActionException{
    logger.info("[JohnLog] " + functionManager.FMname + ": adding the RCMStateListener context to the executive xml.");   
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

  public String getHCALControlSequence(String selectedRun, String CfgCVSBasePath, String CtrlSequenceTagName) throws UserActionException{
    String tmpCtrlSequence ="";
    try{
        // Get ControlSequences from mastersnippet
        docBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        Document masterSnippet = docBuilder.parse(new File(CfgCVSBasePath + selectedRun + "/pro"));

        masterSnippet.getDocumentElement().normalize();
        DOMSource domSource = new DOMSource(masterSnippet);
        StringWriter writer = new StringWriter();
        StreamResult result = new StreamResult(writer);
        TransformerFactory tf = TransformerFactory.newInstance();
        Transformer transformer = tf.newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
        transformer.transform(domSource, result);

        //NodeList TTCciControl =  masterSnippet.getDocumentElement().getElementsByTagName("TTCciControl");
        NodeList CtrlSequence =  masterSnippet.getDocumentElement().getElementsByTagName(CtrlSequenceTagName);
        if (CtrlSequence.getLength()>1){
            logger.warn("[Martin log HCAL " + functionManager.FMname + "]: Found more than one ctrl sequence of "+ CtrlSequenceTagName+ " in mastersnippet. Only the first one will be used ");
        }else if (CtrlSequence.getLength()==0){
            logger.warn("[Martin log HCAL " + functionManager.FMname + "]: Cannot find "+ CtrlSequenceTagName+ " in mastersnippet. Empty string will be returned. ");
            return tmpCtrlSequence;
        }else if (CtrlSequence.getLength()==1){
           logger.info("[Martin log HCAL " + functionManager.FMname + "]: Found 1 "+ CtrlSequenceTagName+ " in mastersnippet. Going to parse it. ");

           Element el = (Element) CtrlSequence.item(0);
           NodeList childlist = el.getElementsByTagName("include"); 
           for(int iFile=0; iFile< childlist.getLength() ; iFile++){
               Element iElement = (Element) childlist.item(iFile);
               String fname = CfgCVSBasePath + iElement.getAttribute("file").substring(1)+"/"+ iElement.getAttribute("version");
		           logger.info("[Martin log HCAL " + functionManager.FMname + "]: Going to read the file of this node from " + fname) ;
               tmpCtrlSequence += readFile(fname,Charset.defaultCharset());
           }
				}
    }
    catch (TransformerException | DOMException | ParserConfigurationException | SAXException | IOException e) {
        logger.error("[HCAL " + functionManager.FMname + "]: Got a error when parsing the "+ CtrlSequenceTagName +" xml: " + e.getMessage());
    }
    String FullCtrlSequence = tmpCtrlSequence;
    return FullCtrlSequence;
  }
  public String getHCALMasterSnippetTag(String selectedRun, String CfgCVSBasePath, String TagName) throws UserActionException{
    String TagContent ="";
    try{
        // Get ControlSequences from mastersnippet
        docBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        Document masterSnippet = docBuilder.parse(new File(CfgCVSBasePath + selectedRun + "/pro"));

        masterSnippet.getDocumentElement().normalize();
        DOMSource domSource = new DOMSource(masterSnippet);
        StringWriter writer = new StringWriter();
        StreamResult result = new StreamResult(writer);
        TransformerFactory tf = TransformerFactory.newInstance();
        Transformer transformer = tf.newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
        transformer.transform(domSource, result);

        //NodeList TTCciControl =  masterSnippet.getDocumentElement().getElementsByTagName("TTCciControl");
        NodeList TagNodeList =  masterSnippet.getDocumentElement().getElementsByTagName(TagName);
        if (TagNodeList.getLength()>1){
            logger.warn("[Martin log HCAL " + functionManager.FMname + "]: Found more than one ctrl sequence of "+ TagName+ " in mastersnippet. Only the first one will be used ");
        }else if (TagNodeList.getLength()==0){
            logger.warn("[Martin log HCAL " + functionManager.FMname + "]: Cannot find "+ TagName+ " in mastersnippet. Empty string will be returned. ");
            return TagContent;
        }else if (TagNodeList.getLength()==1){
           logger.info("[Martin log HCAL " + functionManager.FMname + "]: Found 1 "+ TagName+ " in mastersnippet. Going to parse it. "); 
           TagContent = TagNodeList.item(0).getTextContent();
				}
    }
    catch (TransformerException | DOMException | ParserConfigurationException | SAXException | IOException e) {
        logger.error("[HCAL " + functionManager.FMname + "]: Got a error when parsing the "+ TagName +" xml: " + e.getMessage());
    }
    return TagContent;
  }

  static String readFile(String path, Charset encoding) throws IOException {
      byte[] encoded = Files.readAllBytes(Paths.get(path));
      return new String(encoded, encoding);
   }  
}
