package rcms.fm.app.level1;

import java.util.Map;
import java.util.Arrays;

import rcms.fm.fw.parameter.FunctionManagerParameter;
import rcms.fm.fw.parameter.ParameterException;
import rcms.fm.fw.parameter.ParameterSet;
import rcms.fm.fw.parameter.type.DoubleT;
import rcms.fm.fw.parameter.type.IntegerT;
import rcms.fm.fw.parameter.type.BooleanT;
import rcms.fm.fw.parameter.type.StringT;
import rcms.fm.fw.parameter.type.VectorT;
import rcms.fm.fw.parameter.type.MapT;

import rcms.util.logger.RCMSLogger;

/**
 * HCAL Function Manager global parameters.
 *
 */

public class HCALParameters extends ParameterSet<FunctionManagerParameter> {

	static RCMSLogger logger = new RCMSLogger(HCALFunctionManager.class);

	private static HCALParameters instance;
  private static final String guiParams[] = new String[] {"HCAL_EVENTSTAKEN", "NUMBER_OF_EVENTS", "ACTION_MSG", "SUPERVISOR_ERROR", "RUN_NUMBER", "CONFIGURED_WITH_RUN_NUMBER", "STARTED_WITH_RUN_NUMBER", "PROGRESS","EXIT","DRIVER_IDENTIFIER"};

	private HCALParameters() {
		super();
		this.logger = new RCMSLogger(HCALFunctionManager.class);
		try {
			this.initializeParameters();
		} catch (ParameterException ex) {
			logger.error("Encountered ParameterException while initializing parameter set.", ex);
		}
	}

	public synchronized void initializeParameters() throws ParameterException {
    // Read-only parameters
		this.put( new FunctionManagerParameter<IntegerT> ("SID"                              ,  new IntegerT(0)        ,  FunctionManagerParameter.Exported.READONLY) );  // Database connection session identifier
		this.put( new FunctionManagerParameter<IntegerT> ("RUN_SEQ_NUMBER"                   ,  new IntegerT(0)        ,  FunctionManagerParameter.Exported.READONLY) );  // Run sequence number
		this.put( new FunctionManagerParameter<IntegerT> ("CONFIGURED_WITH_RUN_NUMBER"       ,  new IntegerT(0)        ,  FunctionManagerParameter.Exported.READONLY) );  // Configuration information for l0: run number of last configure
		this.put( new FunctionManagerParameter<IntegerT> ("STARTED_WITH_RUN_NUMBER"          ,  new IntegerT(0)        ,  FunctionManagerParameter.Exported.READONLY) );  // Configuration information for l0: run number at initialization
		this.put( new FunctionManagerParameter<IntegerT> ("HCAL_EVENTSTAKEN"                 ,  new IntegerT(-1)       ,  FunctionManagerParameter.Exported.READONLY) );  // Events taken. Really the number of triggers the TA claims to have issued

		this.put( new FunctionManagerParameter<DoubleT>  ("COMPLETION"                       ,  new DoubleT(-1)        ,  FunctionManagerParameter.Exported.READONLY) );  // Completion meter
		this.put( new FunctionManagerParameter<DoubleT>  ("PROGRESS"                         ,  new DoubleT(-1)        ,  FunctionManagerParameter.Exported.READONLY) );  // Completion meter

		this.put( new FunctionManagerParameter<StringT>  ("FED_ENABLE_MASK"                  ,  new StringT("")        ,  FunctionManagerParameter.Exported.READONLY) );  // FED enable mask, typically handed to us by the level0
		this.put( new FunctionManagerParameter<StringT>  ("STATE"                            ,  new StringT("")        ,  FunctionManagerParameter.Exported.READONLY) );  // State the Function Manager is currently in
		this.put( new FunctionManagerParameter<StringT>  ("SEQ_NAME"                         ,  new StringT("")        ,  FunctionManagerParameter.Exported.READONLY) );  // Run sequence name currently
		this.put( new FunctionManagerParameter<StringT>  ("RUN_KEY"                          ,  new StringT("")        ,  FunctionManagerParameter.Exported.READONLY) );  // Global run key
		this.put( new FunctionManagerParameter<StringT>  ("RUN_MODE"                         ,  new StringT("")        ,  FunctionManagerParameter.Exported.READONLY) );  // Skeletor comment: "mode can be "Normal" or "Debug". Influences the behaviour of the top FM."
		this.put( new FunctionManagerParameter<StringT>  ("ACTION_MSG"                       ,  new StringT("")        ,  FunctionManagerParameter.Exported.READONLY) );  // Action message (fishy zone), visible in level0 and local GUIs
		this.put( new FunctionManagerParameter<StringT>  ("ERROR_MSG"                        ,  new StringT("")        ,  FunctionManagerParameter.Exported.READONLY) );  // Error message visible in red in level0 gui
		this.put( new FunctionManagerParameter<StringT>  ("SUPERVISOR_ERROR"                 ,  new StringT("not set")        ,  FunctionManagerParameter.Exported.READONLY) );  // Error message received from hcalSupervisor
		this.put( new FunctionManagerParameter<StringT>  ("GLOBAL_CONF_KEY"                  ,  new StringT("not set") ,  FunctionManagerParameter.Exported.READONLY) );  // Global configuration key for current run
		this.put( new FunctionManagerParameter<StringT>  ("HCAL_CFGSCRIPT"                   ,  new StringT("not set") ,  FunctionManagerParameter.Exported.READONLY) );  // Configuration script for supervisors
		this.put( new FunctionManagerParameter<StringT>  ("HCAL_CFGCVSBASEPATH"              ,  new StringT("not set") ,  FunctionManagerParameter.Exported.READONLY) );  // CfgCVS basepath, i.e. where to look for snippets
		this.put( new FunctionManagerParameter<StringT>  ("HCAL_MASTERSNIPPETLIST"           ,  new StringT("not set") ,  FunctionManagerParameter.Exported.READONLY) );  // GrandMasterSnippet
		this.put( new FunctionManagerParameter<StringT>  ("HCAL_TTCCICONTROL"                ,  new StringT("not set") ,  FunctionManagerParameter.Exported.READONLY) );  // Configuration script for TTCci
		this.put( new FunctionManagerParameter<StringT>  ("HCAL_LTCCONTROL"                  ,  new StringT("not set") ,  FunctionManagerParameter.Exported.READONLY) );  // Configuration script for LTC (obsolete)
		this.put( new FunctionManagerParameter<StringT>  ("HCAL_LPMCONTROL"                  ,  new StringT("not set") ,  FunctionManagerParameter.Exported.READONLY) );  // Configuration script for LPM
		this.put( new FunctionManagerParameter<StringT>  ("HCAL_ICICONTROL_SINGLE"           ,  new StringT("not set") ,  FunctionManagerParameter.Exported.READONLY) );  // Configuration script for iCI in single Partition mode
		this.put( new FunctionManagerParameter<StringT>  ("HCAL_ICICONTROL_MULTI"            ,  new StringT("not set") ,  FunctionManagerParameter.Exported.READONLY) );  // Configuration script for iCI in multi-partition mode
		this.put( new FunctionManagerParameter<StringT>  ("HCAL_PICONTROL_SINGLE"            ,  new StringT("not set") ,  FunctionManagerParameter.Exported.READONLY) );  // Configuration script for PI in single Partition mode
		this.put( new FunctionManagerParameter<StringT>  ("HCAL_PICONTROL_MULTI"             ,  new StringT("not set") ,  FunctionManagerParameter.Exported.READONLY) );  // Configuration script for PI in multi-partition mode
		this.put( new FunctionManagerParameter<StringT>  ("HCAL_TIME_OF_FM_START"            ,  new StringT("not set") ,  FunctionManagerParameter.Exported.READONLY) );  // Date and time of when FM was initialized
		this.put( new FunctionManagerParameter<IntegerT>  ("INITIALIZED_WITH_SID"            ,  new IntegerT(-1)       ,  FunctionManagerParameter.Exported.READONLY) );  // Configuration information for l0:  SID on initialize
		this.put( new FunctionManagerParameter<StringT>  ("INITIALIZED_WITH_GLOBAL_CONF_KEY" ,  new StringT("not set") ,  FunctionManagerParameter.Exported.READONLY) );  // Configuration information for l0: Current global configuration key
		this.put( new FunctionManagerParameter<StringT>  ("CONFIGURED_WITH_RUN_KEY"          ,  new StringT("not set") ,  FunctionManagerParameter.Exported.READONLY) );  // Configuration information for l0: Global configuration key at last configure
		this.put( new FunctionManagerParameter<StringT>  ("CONFIGURED_WITH_TPG_KEY"          ,  new StringT("not set") ,  FunctionManagerParameter.Exported.READONLY) );  // Configuration information for l0: Trigger key at last configure
		this.put( new FunctionManagerParameter<StringT>  ("CONFIGURED_WITH_FED_ENABLE_MASK"  ,  new StringT("not set") ,  FunctionManagerParameter.Exported.READONLY) );  // Configuration information for l0:  FED enable mask at last configure
		this.put( new FunctionManagerParameter<StringT>  ("DQM_TASK"                         ,  new StringT("pedestal") , FunctionManagerParameter.Exported.READONLY) );  // Used for Local Run to specify which DQM task to be run on this run 

		this.put( new FunctionManagerParameter<BooleanT> ("USE_PRIMARY_TCDS"                 ,  new BooleanT(true)     ,  FunctionManagerParameter.Exported.READONLY) );  // Switch for using the secondary TCDS system
		this.put( new FunctionManagerParameter<BooleanT> ("HCAL_RUNINFOPUBLISH"              ,  new BooleanT(true)    ,  FunctionManagerParameter.Exported.READONLY) );  // Switch for publishing RunInfo 
		this.put( new FunctionManagerParameter<BooleanT> ("OFFICIAL_RUN_NUMBERS"             ,  new BooleanT(true)    ,  FunctionManagerParameter.Exported.READONLY) );  // Switch for using official Run number 

    // User editable parameters. These can also be dictated by the level0
		this.put( new FunctionManagerParameter<IntegerT> ("RUN_NUMBER"                       ,  new IntegerT(0)           ) );  // Current run number
		this.put( new FunctionManagerParameter<IntegerT> ("NUMBER_OF_EVENTS"                 ,  new IntegerT(1000)        ) );  // Requested number of events for local runs

		this.put( new FunctionManagerParameter<StringT>  ("HCAL_RUN_TYPE"                    ,  new StringT("local")      ) );  // Run type -- local or global
		this.put( new FunctionManagerParameter<StringT>  ("HCAL_COMMENT"                     ,  new StringT("")           ) );  // User-input comment
		this.put( new FunctionManagerParameter<MapT<MapT<StringT>>>  ("AVAILABLE_RUN_CONFIGS"  ,  new MapT<MapT<StringT>>()));  // Local run types available
		this.put( new FunctionManagerParameter<VectorT<StringT>> ("AVAILABLE_LOCALRUNKEYS"   ,  new VectorT<StringT>()    ) );  // List of local run keys
		this.put( new FunctionManagerParameter<StringT>  ("RUN_CONFIG_SELECTED"              ,  new StringT("not set")    ) );  // User selected local run type
		this.put( new FunctionManagerParameter<StringT>  ("CFGSNIPPET_KEY_SELECTED"          ,  new StringT("not set")    ) );  // Key name for the local run type selected by the user (aka Mastersnippet file)
		this.put( new FunctionManagerParameter<StringT>  ("RU_INSTANCE"                      ,  new StringT("")           ) );  // EventBuilder classname_instanceNumber of the active one for the run
		this.put( new FunctionManagerParameter<StringT>  ("LPM_SUPERVISOR"                   ,  new StringT("")           ) );  // Supervisor configuring the LPM
		this.put( new FunctionManagerParameter<StringT>  ("EVM_TRIG_FM"                      ,  new StringT("")           ) );  // Function manager doing eventbuilding and triggeradapting duties
		this.put( new FunctionManagerParameter<StringT>  ("FM_PARTITION"                     ,  new StringT("not set")    ) );  // TCDS partition of LV2 FM 
		this.put( new FunctionManagerParameter<StringT>  ("DRIVER_IDENTIFIER"                ,  new StringT("not set")    ) );  // MD5 hash to identify browser session in charge 

		this.put( new FunctionManagerParameter<BooleanT> ("CLOCK_CHANGED"                    ,  new BooleanT(false)       ) );  // Information from level0 on whether the clock source has changed
		this.put( new FunctionManagerParameter<BooleanT> ("USE_RESET_FOR_RECOVER"            ,  new BooleanT(true)        ) );  // Switch for changing behavior of recover button
		this.put( new FunctionManagerParameter<BooleanT> ("EXIT"                             ,  new BooleanT(false)       ) );  // Switch for triggering a halt+destroy
		this.put( new FunctionManagerParameter<BooleanT> ("SINGLEPARTITION_MODE"             ,  new BooleanT(false)       ) );  // Switch for toggling singlepartition mode based on user selection

		this.put( new FunctionManagerParameter<VectorT<StringT>> ("AVAILABLE_RESOURCES"      ,  new VectorT<StringT>()    ) );  // Full list of qualified resources
		this.put( new FunctionManagerParameter<VectorT<StringT>> ("MASKED_RESOURCES"         ,  new VectorT<StringT>()    ) );  // List of masked resources
		this.put( new FunctionManagerParameter<VectorT<StringT>> ("MASK_SUMMARY"             ,  new VectorT<StringT>()    ) );  // Summary of masked FMs for user understandability
		this.put( new FunctionManagerParameter<VectorT<StringT>> ("EMPTY_FMS"                ,  new VectorT<StringT>()    ) );  // LV2 FMs without XDAQs
	}

	public static HCALParameters getInstance() {
		if (instance == null) {
			synchronized (HCALParameters.class) {
				if (instance == null) {
					instance = new HCALParameters();
				}
			}
		}
		return instance;
	}

	public synchronized HCALParameters getClonedParameterSet() { 
    HCALParameters cloned = new HCALParameters();
		for (Map.Entry<String, FunctionManagerParameter> pair : this.getMap().entrySet()) {
			if (pair.getValue() instanceof FunctionManagerParameter) {
				cloned.put(new FunctionManagerParameter((FunctionManagerParameter) pair.getValue()));
			}
		}
	  return cloned;
	}

	public synchronized ParameterSet<FunctionManagerParameter> getChanged( ParameterSet<FunctionManagerParameter> earlier) {
		ParameterSet<FunctionManagerParameter> changed = new ParameterSet<FunctionManagerParameter>();
		for (Map.Entry<String, FunctionManagerParameter> pair : this.getMap().entrySet()) {
			try {
				if (earlier == null || earlier.get(pair.getKey()) == null || !pair.getValue().getValue().equals(earlier.get(pair.getKey()).getValue())) {
					changed.put(new FunctionManagerParameter((FunctionManagerParameter) pair.getValue()));
				}
			}
			catch (Exception e) {
				System.out.println("Error: failed to determine if parameter " + pair.getKey() + " changed.");
			}
		}
		return changed; 
	}

	public static boolean isForGUI(String parameterName) {
		boolean isForGUI=false;
		if (Arrays.asList(guiParams).contains(parameterName)) isForGUI = true;
		return isForGUI;
	} 
}
