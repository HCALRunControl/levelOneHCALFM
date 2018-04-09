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
import rcms.resourceservice.db.resource.Resource;
import rcms.resourceservice.db.resource.config.ConfigProperty;
import rcms.resourceservice.db.resource.xdaq.XdaqApplicationResource;
import rcms.resourceservice.db.resource.xdaq.XdaqExecutiveResource;
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
  abstract public static class abstractQGmapper {
    protected Resource functionManagerResource = null;
    protected QualifiedGroup qg = null;
    static MapT<?> qgMap = null;


    /**
     * abstract class on which the QG mappers for level1 QGs level2 QGs are based
     * @param fmResource the qualified resource of the FM for which to make a map of
     */
    abstractQGmapper(Resource fmResource, QualifiedGroup qg) {
      // extract the qualified group from the fm's Resource object
      this.qg = qg;
    }

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
  public class level2qgMapper extends abstractQGmapper {

    /**
     * method that creates the map for a level2 fm
     * @param l2FMqr the qualified resource of a level2 fm
     * @throws UserActionException if there are problems mapping it out
     */
    protected level2qgMapper(Resource l2FMqr, QualifiedGroup l2qg) throws UserActionException {
      super(l2FMqr, l2qg);
      //if (! l2FMqr.getClass().equals(new HCALlevelTwoFunctionManager().getClass())){
      //  throw new UserActionException("tried to construct a level2 qualified group for FM " + l2FMqr.getName() + "but it is not a level2 FM!");
      //}
      List<QualifiedResource> xdaqExecList = l2qg.seekQualifiedResourcesOfType(new XdaqExecutive());
      logger.warn("QRs in l2qg:");
      //TODO: nothing is in here
      for (QualifiedResource qr : xdaqExecList) {
        logger.warn(qr.getName());
      }
      MapT<MapT<VectorT<StringT>>> execMap = new MapT<MapT<VectorT<StringT>>>();
      MapT<VectorT<StringT>> crateMap = new MapT<VectorT<StringT>>();
      for( QualifiedResource qr : xdaqExecList) {
        String crateNumber = "N/A";
        VectorT appList = new VectorT();
        XdaqExecutiveResource execResource = (XdaqExecutiveResource)(qr.getResource());
        for( XdaqApplicationResource app : execResource.getApplications()){
          appList.add(new StringT(app.getName()));
          if (app.getName().contains("hcalCrate")) {
            for (ConfigProperty crateAppProperty : app.getProperties()){
              if (crateAppProperty.getName().equals("crateId")){
                crateNumber = crateAppProperty.getValue();
              }
            }
          }
        }
        crateMap.put(crateNumber, appList); 
        execMap.put(qr.getName(), crateMap);
      }
      qgMap = execMap;
    }
  }

  /**
   * class for mapping out a level1 FM's QG 
   */
  public class level1qgMapper extends abstractQGmapper {

    /**
     * method that creates a map of a level1 FM's qualified group
     * @param l1FMqr the level1 FM qualified resource
     * @throws UserActionException if it has issues
     */
    public level1qgMapper(Resource l1FMqr, QualifiedGroup qg) throws UserActionException {
      super(l1FMqr, qg);
      //if (! l1FMqr.getClass().equals(new HCALlevelOneFunctionManager().getClass())) {
      //  throw new UserActionException("tried to construct a level2 qualified group for FM " + l1FMqr.getName() + "but it is not a level1 FM!\n  l1FMqr.getClass()=" + l1FMqr.getClass() + ", new HCALlevelOneFunctionManager().getClass()=" + new HCALlevelOneFunctionManager().getClass());
      //}
      MapT<MapT<MapT<VectorT<StringT>>>> l2Map = new MapT<MapT<MapT<VectorT<StringT>>>>();
      List<QualifiedResource> l2FMlist = qg.seekQualifiedResourcesOfType(new FunctionManager());
      for (QualifiedResource qr: l2FMlist) {
        try {
          level2qgMapper level2mapper = new level2qgMapper(qr.getResource(), qr.getQualifiedGroup());
          // TODO: fix this
          // level2qgMapper level2mapper = new level2qgMapper(qr.getResource(), qr.getChildrenResources());
          MapT<MapT<VectorT<StringT>>> level2map = (MapT<MapT<VectorT<StringT>>>) level2mapper.getMap();
          l2Map.put(qr.getName(), level2map);
        }
        catch (UserActionException e) {
          throw e;
        }
      }
      qgMap = l2Map;
    }
  }
}
