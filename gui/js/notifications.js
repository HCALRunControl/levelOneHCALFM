function drawMyCommandButtons(state) {
  //console.log("called drawMyCommandButtons with state: " + state);
}

function myUpdateParameters(message) {
    var pArray = message.getElementsByTagName('PARAMETER');

    if (pArray != null) {
        // loop through the array of parameters contained in this notification
        for (var i = 0; i < pArray.length; i++) {
            if (pArray[i] != null) {
                pNameNode = pArray[i].getElementsByTagName('NAME')[0];
                pValueNode = pArray[i].getElementsByTagName('VALUE')[0];
                if (pNameNode != null && pValueNode != null) {
                    // get the parameter name and its value
                    pName = pNameNode.childNodes[0].nodeValue;
                    pValue = pValueNode.textContent;
                    //if (pName == "SUPERVISOR_ERROR") { showsupervisorerror(pValue); }
                    //if (pName == "NUMBER_OF_EVENTS") { getfullpath(pValue); }
                    if (pName == "HCAL_EVENTSTAKEN") { setProgress(pValue); }
                }
            }
        }
    }
}
