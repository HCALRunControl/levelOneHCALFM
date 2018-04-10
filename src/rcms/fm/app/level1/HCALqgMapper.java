package rcms.fm.app.level1;

import java.util.List;

import rcms.fm.app.level1.HCALqgMapper.level1qgMapper;
import rcms.fm.fw.parameter.type.MapT;
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
      String crateNumber = "N/A"; // if it is not an executive corresponding to the crate
      VectorT appList = new VectorT();
      String execName = "";
      for(Resource qr : level2childList) {
        // this list has apps and execs mixed together
        logger.warn("l2 outer loop: qr: " + qr.getName());
        if (qr.getQualifiedResourceType().contains("Executive")){
          execName = qr.getName();
          execMap = new MapT<MapT<VectorT<StringT>>>();
          crateMap = new MapT<VectorT<StringT>>();
          appList = new VectorT();
          logger.warn("qr " + qr.getName() + " has getQualifiedResourceType: " + qr.getQualifiedResourceType());
          XdaqExecutiveResource execResource = ((XdaqExecutiveResource)qr);
          logger.warn("executive " + qr.getName() + " has number of applications " + execResource.getNumApplications());

          for( XdaqApplicationResource app : execResource.getApplications()){
            logger.warn("l2 inner loop: app: " + app.getName());
            logger.warn("exec" + execResource.getName() + "has app with name " + app.getName());
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
        }
      }
      crateMap.put(crateNumber, appList); 
      execMap.put(execName, crateMap);
      qgMap = execMap;
    }
  }

  /**
   * class for mapping out a level1 FM's QG 
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
      MapT<MapT<MapT<VectorT<StringT>>>> l2Map = new MapT<MapT<MapT<VectorT<StringT>>>>();
      List<QualifiedResource> l2FMlist = qg.seekQualifiedResourcesOfType(new FunctionManager());
      for (QualifiedResource qr: l2FMlist) {
        logger.warn("l1 loop: qr: " + qr.getName());
        try {
          Group l2group = qg.rs.retrieveLightGroup(qr.getResource());
          List<Resource> level2execs = l2group.getChildrenResources();
          level2qgMapper level2mapper = new level2qgMapper(level2execs);
          MapT<MapT<VectorT<StringT>>> level2map = (MapT<MapT<VectorT<StringT>>>) level2mapper.getMap();
          l2Map.put(qr.getName(), level2map);
        }
        catch (UserActionException | DBConnectorException e) {
          throw new UserActionException(e.getMessage());
        }
      }
      qgMap = l2Map;
    }
  }
}