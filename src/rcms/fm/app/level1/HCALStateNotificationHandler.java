package rcms.fm.app.level1;
 
import rcms.fm.fw.parameter.FunctionManagerParameter;
import rcms.fm.fw.parameter.type.StringT;
import rcms.fm.fw.user.UserActionException;
import rcms.fm.fw.user.UserEventHandler;
import rcms.stateFormat.StateNotification;
import rcms.statemachine.definition.State;
import rcms.util.logger.RCMSLogger;
import rcms.utilities.fm.task.Task;
import rcms.utilities.fm.task.TaskSequence;
 
 
/**
 * StateNotificationHandler for HCAL
 *
 * @author Seth I. Cooper
 */
public class HCALStateNotificationHandler extends UserEventHandler  {
 
 
    static RCMSLogger logger = new RCMSLogger(HCALStateNotificationHandler.class);
 
    HCALFunctionManager fm = null;
 
    TaskSequence taskSequence = null;
 
    Boolean isTimeoutActive = false;
 
    Thread timeoutThread = null;
 
    Task activeTask = null;

    static final int COLDINITTIMEOUT = 1000*1200; // 20 minutes in ms
//    public Boolean interruptedTransition = false;
    //this is active only in global mode..
 
    public HCALStateNotificationHandler() throws rcms.fm.fw.EventHandlerException {
        subscribeForEvents(StateNotification.class);
        addAnyStateAction("processNotice");
    }
 
 
    public void init() throws rcms.fm.fw.EventHandlerException {
        fm = (HCALFunctionManager) getUserFunctionManager();
    }
 
    //State notification callback
    public void processNotice( Object notice ) throws UserActionException {

      StateNotification notification = (StateNotification)notice;
      //logger.warn("["+fm.FMname+"]: State notification received "+
      //    "from " + notification.getIdentifier() +
      //    " from state: " + notification.getFromState()+
      //    " to: " + notification.getToState());
      
      String actualState = fm.getState().getStateString();
      //logger.warn("["+fm.FMname+"]: FM is in state: "+actualState);

      if ( fm.getState().equals(HCALStates.ERROR) ) {
        return;
      }

      if ( notification.getToState().equals(HCALStates.ERROR.toString()) || notification.getToState().compareToIgnoreCase(HCALStates.FAILED.toString())==0) {
        String appName = "";
        try {
          appName = fm.findApplicationName( notification.getIdentifier() );
        } catch(Exception e){}
        String actionMsg = appName+"["+notification.getIdentifier()+"] is in ERROR";
        //Default errMessage
        String errMsg    = actionMsg;
        //Check if notification comes from my supervisor
        if (!fm.containerhcalSupervisor.isEmpty() && appName.contains("hcalSupervisor")) {
          ((HCALlevelTwoFunctionManager)fm).getSupervisorErrorMessage();
          errMsg = "[HCAL Level2 " + fm.getName().toString() + "] got an error from the hcalSupervisor: " + ((StringT)fm.getHCALparameterSet().get("SUPERVISOR_ERROR").getValue()).getString();
        }
        // Handles error from TCDS
        else if (!fm.containerTCDSControllers.isEmpty()){
          errMsg = "[HCAL LV2 " + fm.FMname+ "] "+ appName+" is in ERROR, the reason is: "+ notification.getReason();
        }
        else if (!fm.containerFMChildren.isEmpty()) {
          errMsg = "[HCAL LVL1 " + fm.FMname + "] Error received: " + notification.getReason();
          fm.getHCALparameterSet().put(new FunctionManagerParameter<StringT>("SUPERVISOR_ERROR", new StringT(errMsg)));
        }

        handleError(errMsg,actionMsg);
        //logger.warn("["+fm.FMname+"]: Going to error, reset taskSequence to null. ");
        taskSequence = null;  //Reset taskSequence if we are in error
        return;
      }
      //INFO [SethLog HCAL HCAL_HO] 2 received id: http://hcalvme05.cms:16601/urn:xdaq-application:lid=50, ToState: Ready

      // process the notification from the FM when initializing
      if ( fm.getState().equals(HCALStates.INITIALIZING) ) {

        // ignore notifications to INITIALIZING but set timeout
        if ( notification.getToState().equals(HCALStates.INITIALIZING.toString()) ) {
          String msg = "HCAL is initializing ";
          fm.setAction(msg);
          setTimeoutThread(true);
          return;
        }

        // ignore notifications to HALTING (from TCDS apps) but reset timeout
        if ( notification.getToState().equals(HCALStates.HALTING.toString()) ) {
          String msg = "HCAL is initializing ";
          fm.setAction(msg);
          setTimeoutThread(true);
          return;
        }
        // for level2's, we fire the set halt at the end of initAction unless there's an error, so we don't care about any notifications
        // for the level1, in this case we need to compute the new state
        else if ( notification.getToState().equals(HCALStates.HALTED.toString()) ) {
          // if it has children FMs, it's a level-1
          if(!fm.containerFMChildren.isEmpty()) {
            //logger.warn("HCALStateNotificationHandler: got notification to HALTED while FM is in INITIALIZING and this is a level-1 FM: call computeNewState()");
            // calculate the updated state
            fm.theEventHandler.computeNewState(notification);
            return;
          }
        }
      }

      // process the notification from the FM when halting
      if ( fm.getState().equals(HCALStates.HALTING) ) {

        // ignore notifications to HALTING (like from TCDS apps) but set timeout
        if ( notification.getToState().equals(HCALStates.HALTING.toString()) ) {
          String msg = "HCAL is halting ";
          fm.setAction(msg);
          setTimeoutThread(true);
          return;
        }
      }

      // process the notification from the FM when stopping 
      if ( fm.getState().equals(HCALStates.STOPPING) ) {

        // ignore notifications to Stopping but set timeout
        if ( notification.getToState().equals(HCALStates.STOPPING.toString()) ) {
          String msg = "HCAL is stopping ";
          fm.setAction(msg);
          setTimeoutThread(true);
          return;
        }
      }

      // process the notification from the FM when configuring
      if ( fm.getState().equals(HCALStates.CONFIGURING) ) {

        if ( notification.getToState().equals(HCALStates.CONFIGURING.toString()) ) {

          //String services = notification.getReason().trim();
          //if ( services == null | services.length() == 0 ) return;

          //String transMsg = String.format( "services ["+fm.getConfiguredServices()+"] done : ["+services+"] in progress");
          //fm.setTransitionMessage( transMsg );
          //fm.addConfiguredServices(services);
          String msg = "HCAL is configuring ";
          fm.setAction(msg);

          setTimeoutThread(true);
          return;
        } else if ( notification.getToState().equals(HCALStates.PREINIT.toString()) ) {
          String services = notification.getReason().trim();
          String msg = "HCAL is preconfiguring ";
          fm.setAction(msg);
          setTimeoutThread(true);
          return;
        } else if ( notification.getToState().equals(HCALStates.INIT.toString()) ) {
          String msg = "HCAL is configuring ";
          fm.setAction(msg);
          setTimeoutThread(true);
          return;
        } else if ( notification.getToState().equals(HCALStates.FAILED.toString()) ) {
          String appName = "";
          try {
            appName = fm.findApplicationName( notification.getIdentifier() );
          } catch(Exception e){}
          String actionMsg = appName+"["+notification.getIdentifier()+"] is in Error";
          String errMsg =  actionMsg;
          if (!fm.containerhcalSupervisor.isEmpty()) {
            ((HCALlevelTwoFunctionManager)fm).getSupervisorErrorMessage();
            errMsg = "[HCAL Level 2 FM with name " + fm.getName().toString() + " reports error from the hcalSupervisor: " + ((StringT)fm.getHCALparameterSet().get("SUPERVISOR_ERROR").getValue()).getString();
          }
          handleError(errMsg,actionMsg);
          return;
        } else if ( notification.getToState().equals(HCALStates.COLDINIT.toString()) ) {
          fm.setAction("HCAL is in Cold-Init");
          logger.info("[HCAL FM with name " + fm.getName().toString() + " got notification for Cold-Init; setting timeout thread to a longer value");
          setTimeoutThread(true,COLDINITTIMEOUT);
          return;
        }
      }

      // process the notification from the FM when starting
      if ( fm.getState().equals(HCALStates.STARTING) ) {

        if ( notification.getToState().equals(HCALStates.STARTING.toString()) ) {

          String services = notification.getReason().trim();
          if ( services == null | services.length() == 0 ) return;

          //String transMsg = String.format( "services ["+fm.getConfiguredServices()+"] done : ["+services+"] in progress");
          //fm.setTransitionMessage( transMsg );
          //fm.addConfiguredServices(services);
          String msg = "HCAL is starting "+services;
          fm.setAction(msg);

          setTimeoutThread(true);
          return;
        } else if ( notification.getToState().equals(HCALStates.FAILED.toString()) ) {
          String appName = "";
          try {
            appName = fm.findApplicationName( notification.getIdentifier() );
          } catch(Exception e){}
          String actionMsg = appName+"["+notification.getIdentifier()+"] is in Error";
          String errMsg =  actionMsg;
          if (!fm.containerhcalSupervisor.isEmpty()) {
            ((HCALlevelTwoFunctionManager)fm).getSupervisorErrorMessage();
            errMsg = "[HCAL Level 2 FM with name " + fm.getName().toString() + " reports error from the hcalSupervisor: " + ((StringT)fm.getHCALparameterSet().get("SUPERVISOR_ERROR").getValue()).getString();
          }
          handleError(errMsg,actionMsg);
          return;
        }
      }

      if(taskSequence == null) {

        setTimeoutThread(false);
        String infomsg = "Received a State Notification while taskSequence is null \n";

        logger.debug("FM is in local mode");
        fm.theEventHandler.computeNewState(notification);
        return;
      }

      // do a while loop to cover synchronous tasks which finish immediately (adapted from top FM code)
      //
      //
      while (activeTask==null || activeTask.isCompleted()) {
        if (taskSequence.isEmpty()) {
          String completionMsg = "Transition completed";
          fm.setAction(completionMsg);
          logger.info(completionMsg);
          try {
            completeTransition();
          } catch (Exception e) {
            taskSequence = null;
            String errmsg = "Exception while completing taskSequence ["+taskSequence.getDescription()+"]: "+e.getMessage();
            handleError(errmsg,errmsg);
          }
          break;
        }
        else {
          activeTask = (Task) taskSequence.removeFirst();
          logger.info("Start new task: " + activeTask.getDescription());
          fm.setAction("Executing: " + activeTask.getDescription());
          try {
            activeTask.startExecution();
          } catch (Exception e) {
            taskSequence = null;
            String errmsg = "Exception while stepping to the next task: "+e.getMessage();
            handleError(errmsg,errmsg);
          }
        }
      }
}
 
    /*--------------------------------------------------------------------------------
     *
     */
    protected void executeTaskSequence( TaskSequence taskSequence ) {
 
        this.taskSequence = taskSequence;
   
        State SequenceState =  taskSequence.getState();
        State FMState = fm.getState();
        if ( SequenceState != FMState ) {
            String errmsg = "taskSequence does not belong to this state \n " +
                "Function Manager state = " + fm.getState() +
                "\n while taskSequence is for state = " + taskSequence.getState();
 
            taskSequence = null;
            handleError(errmsg," ");
            return;
        }
 
        try {
            //logger.info("Martin log: Start Execution: "+taskSequence.getDescription());
            taskSequence.startExecution();

            setTimeoutThread(true,COLDINITTIMEOUT); // set maximum timeout for level-1; level-2s can still time out earlier
            try {
                fm.getParameterSet().get("ACTION_MSG")
                    .setValue(new StringT(""+taskSequence.getDescription()));
 
            } catch (Exception e) {
                logger.warn("failed to set action parameter");
            }
 
        } catch (Exception e){
            taskSequence = null;
            String errmsg = "process notice error: "+e.getMessage();
            handleError(errmsg," ");
        }
    }
 
    /*--------------------------------------------------------------------------------
     *
     */
    protected void completeTransition() throws UserActionException, Exception {
 
        State FMState = fm.getState();
 
        fm.setAction("Transition Completed");
 
        //fm.setTransitionEndTime();
        setTimeoutThread(false);
        logger.info("completeTransition: fire taskSequence completion event "+taskSequence.getCompletionEvent().toString());
        fm.fireEvent(taskSequence.getCompletionEvent());
        activeTask = null;
        taskSequence = null;
 
    }
 
    /*--------------------------------------------------------------------------------
     * Start or stop the timeout thread for transitions; default timeout is 4 minutes
     */
    public void setTimeoutThread(Boolean action) {
        setTimeoutThread(action,240000);
    }
    public void setTimeoutThread(Boolean action, int msTimeout) {
 
        if (timeoutThread!=null) {
            try {
                isTimeoutActive = false;
                timeoutThread.interrupt();
                timeoutThread=null;
            } catch (Exception e) {
                logger.error("couldn't destroy timer");
                isTimeoutActive = false;
                return;
            }
        }
        if (action==false) {
            isTimeoutActive=false;
            return;
        } else {
            isTimeoutActive = true;
            timeoutThread = new Thread( new Runnable()
                {
                    int milliSecondSleepTime = msTimeout;
 
                    public void run() {    
                        try {
                            Thread.sleep(this.milliSecondSleepTime);
                            if (isTimeoutActive) {
                                //CLEANUP and set error
                                String errmsg = "Application transition timeout error";
                                fm.goToError(errmsg);
                                taskSequence = null;
                                isTimeoutActive=false;
                                return;
                            }
                        }
                        catch (InterruptedException ie) {
                        }
                        catch (Exception e) {
                            logger.error( "Exception in timeout HCALFM thread");
                        }
                    }
                } );
   
 
            //Sets the thread's priority to the minimum value.
            timeoutThread.setPriority(Thread.MIN_PRIORITY);
            //Starts the thread.
            timeoutThread.start();
            return;
        }
    }

    /*--------------------------------------------------------------------------------
     *
     */
    protected void handleError(String errMsg, String actionMsg) {
        fm.setAction(actionMsg);
        setTimeoutThread(false);
        fm.goToError(errMsg);
    }
 
}
