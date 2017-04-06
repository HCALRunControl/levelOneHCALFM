/* Transition Array */
var transitions = null;

/* Command Array */
var commands = null;

/* Transition Object definition */
function Transition(fromState, toState, input) {
  this.fromState = fromState;
  this.toState = toState;
  this.input = input;
}

/* Overrides Transition Object toString */
Transition.prototype.toString = transitionToString;
function transitionToString() {
  var result = '[';
  result += this.fromState + ', ';
  result += this.toState + ', ';
  result += this.input;
  result += ']';
  return result;
}

/* Command Object definition */
function Command(name, input) {
  this.name = name;
  this.input = input;
}

/* Overrides Command Object toString */
Command.prototype.toString = commandToString;
function commandToString() {
  var result = '[';
  result += this.name + ', ';
  result += this.input;
  result += ']';
  return result;
}

/* State Object definition */
function State(name, value) {
  this.name = name;
  this.value = value;
}

/* CommandParameter Object definition */
function CommandParameter(commandName, parameterName, parameterType) {
  this.commandName = commandName;
  this.parameterName = parameterName;
  this.parameterType = parameterType;
}

/* Overrides CommandParameter Object toString */
CommandParameter.prototype.toString = commandParameterToString;
function commandParameterToString() {
  var result = '[';
  result += this.commandName + ', ';
  result += this.parameterName;
  result += ']';
  return result;
}

/* Overrides State Object toString */
State.prototype.toString = stateToString;
function stateToString() {
  var result = '[';
  result += this.name + ', ';
  result += this.value;
  result += ']';
  return result;
}

/* 
   @param transitions The Transition Array.
   @param command The current Command.
   @param state The current State.
   @return true if current command is enabled according to transitions.
   */
function isCommandEnabled(transitions, command, state) {
  var result = false;
  if (transitions != null) {
    for (var i = 0; i < transitions.length; i++) {
      if (transitions[i] != null && command != null && state != null) {
        if (transitions[i].input == command.input && transitions[i].fromState == state.value) {
          //var ttsTestCommand = JSON.stringify(command).indexOf('TTS');
          //var haltedState = JSON.stringify(state).indexOf('Halted');
          var haltCommand = JSON.stringify(command).indexOf('Halt');
          var pausedState = JSON.stringify(state).indexOf('Paused');
          //if (!( haltedState !== -1  &&  ttsTestCommand !== -1 ) ) {
          if (!( pausedState !== -1  &&  haltCommand !== -1 ) ) {
            result = true;
            break;
          }
        }
      }
    }
    checkSpectator();
  }
  return result;
}
