package rcms.fm.app.level1;

import java.util.List;

import rcms.fm.app.level1.HCALqgMapper.level1qgMapper;
import rcms.fm.fw.parameter.type.MapT;
import rcms.fm.fw.parameter.type.ParameterType;
import rcms.fm.fw.parameter.type.StringT;
import rcms.fm.fw.parameter.type.VectorT;
import rcms.fm.fw.user.UserActionException;
import rcms.fm.resource.QualifiedGroup;
import rcms.fm.resource.QualifiedResource;
import rcms.fm.resource.QualifiedResourceContainer;
import rcms.fm.resource.qualifiedresource.FunctionManager;
import rcms.fm.resource.qualifiedresource.XdaqExecutive;
import rcms.resourceservice.db.Group;
import rcms.resourceservice.db.resource.Resource;
import rcms.resourceservice.db.resource.config.ConfigProperty;
import rcms.resourceservice.db.resource.xdaq.XdaqApplicationResource;
import rcms.resourceservice.db.resource.xdaq.XdaqExecutiveResource;
import rcms.common.db.DBConnectorException;
import rcms.util.logger.RCMSLogger;

/**
 * @author John Hakala
 * class for mapping out the qualified group of a particular configuration
 * 
 * the most important thing here is the level1qgMapper
 * if you call level1qgMapper it should return a full representation of the qualified group (or at least only what we care about)
 * Example (this is made up, for clarity):
 * {
 *   HCAL_HBHE: {
 *                 Executive_0: {non-crate:[hcalSupervisor_0, hcalPartitionViewer_0]},
 *                 Executive_1: {41 :[hcal::DTCManager_0, hcalCrate_0]},
 *              },
 *   HCAL_HF  : {
 *                  Executive_2: {non-crate: [hcalPartitionViewer_1, hcalSupervisor_1]}
 *                  Executive_3: {42 : [hcalTrivialFU_0, hcalEventBuilder_0, hcal::DTCReadout_0, DummyTriggerAdapter_0, hcal::uHTRManager_1, hcal::DTCManager_2, hcalCrate_1]},
 *              }
 *  }
 *  
 *  the whole map is called the "l1qgMap"
 *  an individual key-value pair in the l1qgMap is called the "l2map"
 *  the value in that key-value pair is called the "execMap"
 *  the execMap should have only one key-value pair
 *  the key of the exec map is the crate number it corresponds to, or "non-crate" if not applicable
 *  the key-value pair of the execMap is called the "crateMap"
 *  the value of the crateMap is a list of applications and is called the "appList"
 */
public class HCALqgMapper {

  static RCMSLogger logger = new RCMSLogger(HCALqgMapper.class);
  /**
   * abstract class for various kinds of maps of qualified groups
   */
  abstract private static class abstractQGmapper {
    static MapT<?> qgMap = null;

    /**
     * generic getter for the qg map
     */
     public MapT<?> getMap() {
      return qgMap;
    }
  }

  /**
   * class that does bookkeeping of the level2 FM qualified groups
   */
  private class level2qgMapper extends abstractQGmapper {

    /**
     * method that creates the map for a level2 fm
     * @param l2childList a list of the level2's children [in this case the execs and apps are mixed together]
     * @throws UserActionException if there are problems mapping it out
     */
    protected level2qgMapper(List<Resource> level2childList) throws UserActionException {
      MapT<MapT<VectorT<StringT>>> execMap = new MapT<MapT<VectorT<StringT>>>();
      MapT<VectorT<StringT>> crateMap = new MapT<VectorT<StringT>>();
      String crateNumber = "non-crate"; // if it is not an executive corresponding to the crate
      String execName = "";
      VectorT appList = new VectorT();
      for(Resource qr : level2childList) {
        // this list has apps and execs mixed together
        crateNumber = "non-crate"; // if it is not an executive corresponding to the crate
        execName = "NoExecName";
        if (qr.getQualifiedResourceType().contains("Executive")){
          execName = qr.getName();
          appList = new VectorT();

          XdaqExecutiveResource execResource = ((XdaqExecutiveResource)qr);

          for( XdaqApplicationResource app : execResource.getApplications()){
            appList.add(new StringT(app.getName()));
            // get the crate number from the hcalCrate app property
            if (app.getName().contains("hcalCrate")) {
              for (ConfigProperty crateAppProperty : app.getProperties()){
                if (crateAppProperty.getName().equals("crateId")){
                  crateNumber = crateAppProperty.getValue();
                }
              }
            }
          }
          crateMap.put(crateNumber, appList); 
          execMap.put(execName, crateMap);
          crateMap = new MapT<VectorT<StringT>>();
        }
      }
      qgMap = execMap;
    }
  }

  /**
   * class for mapping out a level1 FM's QG 
   * it makes the l1QGmap
   */
  public class level1qgMapper extends abstractQGmapper {
    protected Resource functionManagerResource = null;
    protected QualifiedGroup qg = null;

    /**
     * method that creates a map of a level1 FM's qualified group
     * @param l1FMqr the level1 FM qualified resource
     * @throws UserActionException if it has issues
     */
    public level1qgMapper(Resource l1FMqr, QualifiedGroup qg) throws UserActionException {
      this.qg = qg;
      MapT<MapT<MapT<VectorT<StringT>>>> l1QGmap = new MapT<MapT<MapT<VectorT<StringT>>>>();
      List<QualifiedResource> l2FMlist = qg.seekQualifiedResourcesOfType(new FunctionManager());
      for (QualifiedResource qr: l2FMlist) {
        try {
          Group l2group = qg.rs.retrieveLightGroup(qr.getResource());
          List<Resource> level2execs = l2group.getChildrenResources();
          level2qgMapper level2mapper = new level2qgMapper(level2execs);
          MapT<MapT<VectorT<StringT>>> execMap = (MapT<MapT<VectorT<StringT>>>) level2mapper.getMap();
          l1QGmap.put(qr.getName(), execMap);
        }
        catch (UserActionException | DBConnectorException e) {
          throw new UserActionException(e.getMessage());
        }
      }
      qgMap = l1QGmap;
      if (!isQGmapValid()) {
        throw new UserActionException("Error creating the QG map!");
      }
    }
    
    /**
     * get the executive corresponding to a given crate
     * @param crateNumber, the crate number
     * @return a string of the executive's name, e.g. Executive_5
     * @throws UserActionException if the crate couldn't be found
     */
    public String getExecOfCrate(Integer crateNumber) throws UserActionException{
      for (ParameterType<?> l2FMmap : qgMap.getMap().values()) {
        MapT<MapT<VectorT<StringT>>> l2Map = ((MapT<MapT<VectorT<StringT>>>) l2FMmap);
        for (StringT execKey : l2Map.getMap().keySet()) {
          if (l2Map.getMap().get(execKey).getMap().keySet().contains(new StringT(Integer.toString(crateNumber)))){
            return execKey.getString(); 
          }
        }
      }
      throw new UserActionException("Could not find executive corresponding to crate " + crateNumber);
    }

    /**
     * return the crate corresponding to an application, specifying which FM the app belongs to
     * @param appName the application's name
     * @param fmName the FM's name
     * @return a string of the crate number
     */
    public String getCrateOfApp(String appName, StringT fmName) {
      String crate = "non-crate";
      MapT<MapT<VectorT<StringT>>> execMap = (MapT<MapT<VectorT<StringT>>>) qgMap.getMap().get(fmName);
      for (MapT<VectorT<StringT>> crateMap : execMap.getMap().values()) {
        for (StringT crateKey : crateMap.getMap().keySet()) {
          VectorT<StringT>appList = crateMap.getMap().get(crateKey);
          if (appList.contains(new StringT(appName))) {
            crate = crateKey.getString(); 
          }
        }
      }
      return crate;
    }

    /**
     * return the crate corresponding to an application
     * @param appName the app's name
     * @return a string of the crate number
     */
    public String getCrateOfApp(String appName) {
      String crate = "non-crate";
      for (StringT fmName : qgMap.getMap().keySet()) {
         crate = getCrateOfApp(appName, fmName);
         if (!crate.equals("non-crate")) break;
      }
      return crate;
    }

    /**
     * method for getting all the apps in an executive
     * @param execName the name of the executive, e.g. Executive_5
     * @return a VectorT of app names
     * @throws UserActionException if it fails to find the executive
     */
    public VectorT<StringT> getAppsOfExec(String execName) throws UserActionException {
      MapT<MapT<MapT<VectorT<StringT>>>> l1qgMap = (MapT<MapT<MapT<VectorT<StringT>>>>) qgMap.getMap();
      for (StringT execKey : l1qgMap.getMap().keySet()) {
        for (MapT<VectorT<StringT>> crateMap : l1qgMap.getMap().get(execKey).getMap().values()) {
          for (VectorT<StringT> appList : crateMap.getMap().values()) {
            if (execKey.getString().equals(execName)) {
              return appList;
            }
          }
        }
      }
      throw new UserActionException("Did not find executive with name " + execName + "in QG!");
    }

    /**
     * method for getting all the apps corresponding to a crate
     * @param crateNumber the crate number
     * @return a VectorT of app names
     * @throws UserActionException if the crate number is bad or if it can't find the executive of that crate
     */
    public VectorT<StringT> getAppsOfCrate(String crateNumber) throws UserActionException {
      try {
        return getAppsOfExec(getExecOfCrate(Integer.parseInt(crateNumber)));
      }
      catch (NumberFormatException | UserActionException e) {
        throw new UserActionException("Problem getting apps corresponding to crate " + crateNumber + " : " + e.getMessage());
      }
    }

    /**
     * sanity check that the l1qgMap is valid
     * the rules for validity are:
     * a) there is exactly one crate corresponding to one executive
     * b) there is exactly one appList per executive
     * @return a bool where 1 is valid and 0 is not valid
     */
    private boolean isQGmapValid () {
      for (StringT level2key : qgMap.getMap().keySet()) {
        MapT<MapT<VectorT<StringT>>> execMap = (MapT<MapT<VectorT<StringT>>>) qgMap.getMap().get(level2key);
        for (MapT<VectorT<StringT>> crateMap : execMap.getMap().values()) {
          if (crateMap.getMap().keySet().size() > 1) {
            return false;
          }
        }
      }
      return true;
    }
  }
}
