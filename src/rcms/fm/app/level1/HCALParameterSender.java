package rcms.fm.app.level1;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import rcms.util.logger.RCMSLogger;

//import rcms.fm.ajaxFM.MyFunctionManager;
//import rcms.fm.ajaxFM.myParameters.MyParameterSet;
import rcms.fm.fw.parameter.FunctionManagerParameter;
import rcms.fm.fw.parameter.Parameter;
import rcms.fm.fw.parameter.ParameterSet;
import rcms.fm.fw.parameter.type.CollectionT;
import rcms.fm.fw.parameter.util.ParameterUtil;
import rcms.ns.event.NotificationEvent;
import rcms.ns.utils.NotificationHelper;
import rcms.ns.utils.NotificationParameterContainer;

/**
 * A {@link Runnable} class used to send updated {@link Parameter}s to the GUI.
 * Thanks to Philip Brummer for this!
 */
public class HCALParameterSender implements Runnable {

  static RCMSLogger logger = null;

	/**
	 * The interval in which a check for changed parameters is performed.
	 */
	private static final long intervalInMs = 500;

  protected HCALFunctionManager functionManager;
	private final ScheduledExecutorService executorService;

	private volatile boolean isRunning = false;
	private volatile boolean parameterUpdateRequired = true;

  String cachedNEvents = "";

	@SuppressWarnings("rawtypes")
	private ParameterSet<FunctionManagerParameter> previousParameterSetSnapshot;

	/**
	 * Creates a new {@link ChangedParameterSender} instance. Also creates an
	 * {@link ExecutorService} for it to run in.
	 * 
	 * @param parentFM
	 */
	public HCALParameterSender(HCALFunctionManager parentFunctionManager) {
    this.logger = new RCMSLogger(HCALFunctionManager.class);
		this.functionManager = parentFunctionManager;
    this.previousParameterSetSnapshot = this.functionManager.getHCALparameterSet().getClonedParameterSet();
		this.executorService = Executors.newSingleThreadScheduledExecutor();
	}

	/**
	 * Calling this method ensures that parameters are checked and updated on
	 * the next scheduled run.
	 */
	public void requireParameterUpdate() {
		this.parameterUpdateRequired = true;
	}

	/**
	 * Starts this instance. Every instance can only be started once.
	 */
	public void start() {
		if (!this.isRunning) {
			this.isRunning = true;
			this.executorService.scheduleAtFixedRate(this, 0, intervalInMs, TimeUnit.MILLISECONDS);
		}
	}

	/**
	 * Shuts down this instance, stopping the {@link ExecutorService}. It is not
	 * possible to start it again afterwards.
	 */
	public void shutdown() {
		this.executorService.shutdownNow();
		try {
			if (!this.executorService.awaitTermination(1000, TimeUnit.MILLISECONDS)) {
			}
		} catch (InterruptedException intEx) {
			logger.error("Thread was interrupted while awaiting Executor Service termination.", intEx);
		}
	}

	@SuppressWarnings("rawtypes")
	@Override
	public void run() {
		if (!this.parameterUpdateRequired) {
			return;
		}
		//this.parameterUpdateRequired = true;

		/*
		 * copied from LV0 and adapted
		 * 
		 * The idea here is that we make a snapshot of the parameters and
		 * compare it with the previous snapshot, only sending parameters that
		 * changed. Of course, one could also queue only the updated
		 * parameter(s) when the update actually happens, which might be a
		 * better and more efficient approach when dealing with just a few
		 * parameters or if every parameter update needs to get sent to the GUI
		 * without getting lost due to only sending the latest parameter values
		 * in a specified interval.
		 */

		try {
			NotificationParameterContainer npc = new NotificationParameterContainer();
			boolean empty = true;

			/*
			 * work with a copy to avoid Concurrent Modification Exceptions and
			 * to ensure no updates are missed
			 */
			HCALParameters parameterSetSnapshot = this.functionManager.getHCALparameterSet().getClonedParameterSet();

			// get the parameters that have changed
			ParameterSet<FunctionManagerParameter> changedParams = parameterSetSnapshot.getChanged(this.previousParameterSetSnapshot);

			/*
			 * Check all changed parameters, filtering out those that are meant
			 * to be sent to the GUI.
			 * 
			 * Parameters that are of type CollectionT (namely MapT, VectorT)
			 * are converted to JSON and their names get prefixed with "JSON_".
			 * This can be detected on the client side and the JSON can be
			 * parsed back.
			 */
			 for (FunctionManagerParameter<?> param : changedParams.getParameters()) {
			 	if (HCALParameters.isForGUI(param.getName())) {
			 		empty = false;
			 		if (param.getValue() instanceof CollectionT) {
			 			npc.addParameter("JSON_" + param.getName(), ParameterUtil.toJSON(param.getValue()));
			 		} else {
			 			npc.addParameter(param.getName(), param.getValue().toString());
			 		}
			 	}

			 }
			
			// update the previous snapshot to the current one
			this.previousParameterSetSnapshot = parameterSetSnapshot;

			/*
			 * XXX: This is the relevant code required to send notifications to
			 * the GUI. It can be used to send any kind of Parameter and is not
			 * limited to actual FM parameters.
			 * 
			 * It converts the previously populated NotificationParamerContainer
			 * (and the contained parameters) to XML, wraps the XML into a
			 * NotificationEvent and passes it to the notification event manager
			 * which then sends it to the GUI.
			 */
			if (!empty) {
				NotificationEvent ne = new NotificationEvent();
				String xml = npc.toXML();
				ne.setContent(xml);
				ne.setType(NotificationHelper.PARAMETER_TYPE);
				this.functionManager.sendNotificationEvent(ne);
			}
		} catch (Exception ex) {
			logger.error("Parameter update failed.", ex);
		}
	}

}
