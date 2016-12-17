function turn_off_visibility(tbid) {
    document.getElementById(tbid).style.display = "none";
}

function turn_on_visibility(tbid) {
    document.getElementById(tbid).style.display = "table";
}

function toggle_visibility(tbid) {
    if (document.getElementById(tbid).style.display != "table") {
        document.getElementById(tbid).style.display = "table";
    }
    else {
        document.getElementById(tbid).style.display = "none";
    }
}


function copyContents(element, tgt) {
    tgt.appendChild(element);
}

function makecheckbox(checkbox, parameter) {
    document.getElementById(checkbox).innerHTML = '<input id=\"globalParameterCheckBox' + document.getElementById(parameter).getAttribute("name").substring(20) + '\" type=\"checkbox\" onclick=\"onClickGlobalParameterCheckBox(\'' + document.getElementById(parameter).getAttribute("name").substring(20) + '\', \'' + parameter + '\')\">';
}

function removeduplicatecheckbox(parameter) {
    document.getElementById("globalParameterCheckBox" + document.getElementById(parameter).getAttribute("name").substring(20)).parentNode.removeChild(document.getElementById("globalParameterCheckBox" + document.getElementById(parameter).getAttribute("name").substring(20)));
}

function hideduplicatefield(parameter) {
    document.getElementById("globalParameterName" + document.getElementById(parameter).getAttribute("name").substring(20)).parentNode.style.display = "none";
}

//function showsupervisorerror(errMessage) {
function showsupervisorerror() {
    var errMessage = document.getElementById("SUPERVISOR_ERROR").value;
    if (errMessage != "not set" && errMessage != "") {
        document.getElementById("supervisorRow").style.display = "";
        document.getElementById("supervisorError").innerHTML = errMessage;
    }
}

// The scripts below use jQuery.
$(document).ready(function () {
    var initcolor = $('#currentState').text();
    $('#currentState').attr("class", "hcal_control_" + initcolor);
    if ($('#currentState').text() == "Configured") {$('#Destroy').hide();}
    var cachedRunNo = $('#RUN_NUMBER').val();
    var cachedNevents = $('#NUMBER_OF_EVENTS').val();
    var cachedSupErr = $('#SUPERVISOR_ERROR').val();
    //$('#commandParameterCheckBox').attr("onclick", "onClickCommandParameterCheckBox(); toggle_visibility('Blork');");


    setInterval(function () {

        hidelocalparams();
        var currentState = $('#currentState').text();
        $('#currentState').attr("class", "hcal_control_" + currentState);
        if (currentState == "Initial") {
            $('#newRUN_CONFIG_SELECTEDcheckbox :checkbox').show();
        }
        else {
            $('#newRUN_CONFIG_SELECTEDcheckbox :checkbox').hide();
        }
        if (currentState == "Configured") {$('#Destroy').hide();}
        if ($('#SUPERVISOR_ERROR').val() !=  cachedSupErr) { showsupervisorerror(); }
        if ($('#RUN_NUMBER').val() !=  cachedRunNo) { getfullpath(); }
        if ($('#NUMBER_OF_EVENTS').val() !=  cachedNevents) { getfullpath(); }
        //$('#commandParameterCheckBox').attr("onclick", "onClickCommandParameterCheckBox(); toggle_visibility('Blork');");
        cachedRunNo = $('#RUN_NUMBER').val();
        cachedNevents = $('#NUMBER_OF_EVENTS').val();
        cachedSupErr = $('#SUPERVISOR_ERROR').val();
        if ($('#EXIT').val() == "true" && currentState=="Halted") { onDestroyButton(); }
    }, 750);


    $('#dropdowndiv').on('change', 'select', function () {
        $('#masked_resourses_td').show();
    });
});

function setProgress(progress) {
    var numberOfEvents = $("#NUMBER_OF_EVENTS").val();
    var width = $(".container").width();
    var progressPercent = 100 * progress / numberOfEvents;
    var progressBarWidth = progressPercent * (width / 100);
    //$(".progressbar").width(progressBarWidth);
    progressPercent = progressPercent.toFixed(2);
    //console.log("progressPercent is: " + progressPercent);
    $(".progressbar").width(progressBarWidth).html(progressPercent + "% &nbsp; &nbsp;");
}

function mirrorSelection() {
    $('#CFGSNIPPET_KEY_SELECTED').val($('#dropdown option:selected').text());
    $('#RUN_CONFIG_SELECTED').val($('#dropdown option:selected').val());
    //$('#MASKED_RESOURCES').val($('#dropdown option:selected').attr("maskedresources"));
}

function checkCheckboxes() {
    $('#newCFGSNIPPET_KEY_SELECTEDcheckbox :checkbox').prop('checked', true);
    $('#newRUN_CONFIG_SELECTEDcheckbox :checkbox').prop('checked', true);
    $('#newMASKED_RESOURCEScheckbox :checkbox').prop('checked', true);
}

function undisable(paramNumber) {
    var parameterInputBoxID = "#globalParameterName".concat(paramNumber);
    $(parameterInputBoxID).removeAttr("disabled");
}
function clickboxes() {
    if ($('#newCFGSNIPPET_KEY_SELECTEDcheckbox :checkbox:checked').length == 0) {
        $('#newCFGSNIPPET_KEY_SELECTEDcheckbox :checkbox').click();
        $('#newRUN_CONFIG_SELECTEDcheckbox :checkbox').click();
        $('#newMASKED_RESOURCEScheckbox :checkbox').click();
    }
}

function makedropdown(availableRunConfigs) {
    availableRunConfigs = availableRunConfigs.substring(0, availableRunConfigs.length - 1);
    var array = availableRunConfigs.split(';');
    var dropdownoption = "<select id='dropdown' > <option value='not set' maskedresources=''> --SELECT-- </option>";

    for (var i = 0, l = array.length; i < l; i++) {
        var option = array[i].split(':');
        dropdownoption = dropdownoption + "<option value='" + option[1] + "' maskedresources='" + option[2] + ";'>" + option[0] + "</option>";
    }
    dropdownoption = dropdownoption + "</select>";
    $('#dropdowndiv').html(dropdownoption);
    var cfgSnippetKeyNumber = $('#CFGSNIPPET_KEY_SELECTED').attr("name").substring(20);
    var cfgSnippetArgs = "'" + cfgSnippetKeyNumber + "', 'CFGSNIPPET_KEY_SELECTED'";
    var masterSnippetNumber = $('#RUN_CONFIG_SELECTED').attr("name").substring(20);
    var masterSnippetArgs = "'" + masterSnippetNumber + "', 'RUN_CONFIG_SELECTED'";
    var maskedResourcesNumber = $('#MASKED_RESOURCES').attr("name").substring(20);
    var maskedResourcesArgs = "'" + maskedResourcesNumber + "', 'MASKED_RESOURCES'";
    var onchanges = "onClickGlobalParameterCheckBox(" + cfgSnippetArgs + "); onClickGlobalParameterCheckBox(" + masterSnippetArgs + "); onClickGlobalParameterCheckBox(" + maskedResourcesArgs + "); clickboxes(); mirrorSelection(); fillMask();";
    $('#dropdown').attr("onchange", onchanges);
}

function fillMask() {
    var finalMasks=[];
    $('#multiPartitionSelection :checked').each(function () {
        finalMasks.push($(this).val());
    });
    $('#MASKED_RESOURCES').val(JSON.stringify(finalMasks));
    $('#maskTest').html($('#MASKED_RESOURCES').val());
}

function makecheckboxes() {
    var param = $('#AVAILABLE_RESOURCES').html().replace("[", "").replace("]","");
    param =  param.replace(/['"]+/g, '');
    var array = param.split(',');
    var maskDivContents = "<ul>";
    var radioButtonDivContents = "<form action=''>";
    for (var i = 0, l = array.length; i < l; i++) {
        var option = array[i].split(':');
        var checkbox = "<li><input type='checkbox' onchange='fillMask();' value='" + option + "'>" + option + "</li>";
        var radiobutton = "<input type='radio' name='singlePart' onchange='" + 'picksinglepartition("' + option +'");' + "' value='" + option + "'>" + option;
        maskDivContents += checkbox;
        radioButtonDivContents += radiobutton;
    }
    maskDivContents += "</ul>";
    radioButtonDivContents +="</form>";
    $('#multiPartitionSelection').html(maskDivContents);
    $('#singlePartitionSelection').html(radioButtonDivContents);
}

function picksinglepartition(option) {
    $('#multiPartitionSelection :input').not("[value='"+option+"']").prop('checked', true);
    $("#multiPartitionSelection :input[value='"+option+"']").prop('checked', false);
    fillMask();
}

function hidecheckboxes() {
    var currentState = $('#currentState').text();
    if (currentState == "Initial") {
        $('#dropdowndiv').show();
        $('#newCFGSNIPPET_KEY_SELECTEDcheckbox :checkbox').show();
        $('#newRUN_CONFIG_SELECTEDcheckbox :checkbox').show();
    }
    else {
        $('#dropdowndiv').hide();
        $('#newCFGSNIPPET_KEY_SELECTEDcheckbox :checkbox').hide();
        $('#newRUN_CONFIG_SELECTEDcheckbox :checkbox').hide();
    }
}

function hideinitializebutton() {
    var button = $('#commandSection :input')
    button.each(function () {
        if ($('#CFGSNIPPET_KEY_SELECTED').val() == "not set") {
            button.hide();
        }
        else {
            button.show();
        }
    });
}


function hidelocalparams() {
    if ($('#CFGSNIPPET_KEY_SELECTED').val().indexOf("global") !== -1) {
        $('#newNUMBER_OF_EVENTScheckbox').parent().hide();
        $('#newHCAL_EVENTSTAKEN').parent().hide();
    }
}

function moveversionnumber() {

    $('#hcalfmVersion').css('font-size', '12');
    $('#hcalfmVersion').css('color', '#dddddd');
    $('#hcalfmVersion').css('font-family', 'Open Sans, sans-serif');
    $('#hcalfmVersion').appendTo('#versionSpot');
}


//    function getfullpath(nEvents) {
    function getfullpath() {
      var maskSummary = $("#MASK_SUMMARY").text();
      maskSummary = maskSummary.replace(/\"/g, "");
      maskSummary = maskSummary.replace("\[","");
      maskSummary = maskSummary.replace("\]","");
      maskSummary = maskSummary.replace(/,/g, ", ");
      if (maskSummary === "") {maskSummary = "none";}
      $("#elogInfo").text("Run # " + $("#RUN_NUMBER").val()  + " - " + $(".control_label2").first().text() + " - Local run key: "+ $("#CFGSNIPPET_KEY_SELECTED").val()  + " - " + $("#NUMBER_OF_EVENTS").val() + " events, masks: " + maskSummary);
    }



function hcalOnLoad() {
    activate_relevant_table('AllParamTables');
    removeduplicatecheckbox('CFGSNIPPET_KEY_SELECTED');
    removeduplicatecheckbox('RUN_CONFIG_SELECTED');
    removeduplicatecheckbox('MASKED_RESOURCES');
    removeduplicatecheckbox('MASK_SUMMARY');
    removeduplicatecheckbox('NUMBER_OF_EVENTS');
    removeduplicatecheckbox('ACTION_MSG');
    removeduplicatecheckbox('RUN_NUMBER');
    removeduplicatecheckbox('SINGLEPARTITION_MODE');
    copyContents(CFGSNIPPET_KEY_SELECTED, newCFGSNIPPET_KEY_SELECTED);
    makecheckbox('newCFGSNIPPET_KEY_SELECTEDcheckbox', 'CFGSNIPPET_KEY_SELECTED');
    copyContents(RUN_CONFIG_SELECTED, newRUN_CONFIG_SELECTED);
    makecheckbox('newRUN_CONFIG_SELECTEDcheckbox', 'RUN_CONFIG_SELECTED');
    copyContents(MASKED_RESOURCES, newMASKED_RESOURCES);
    makecheckbox('newMASKED_RESOURCEScheckbox', 'MASKED_RESOURCES');
    copyContents(MASK_SUMMARY, newMASK_SUMMARY);
    copyContents(NUMBER_OF_EVENTS, newNUMBER_OF_EVENTS);
    makecheckbox('newNUMBER_OF_EVENTScheckbox', 'NUMBER_OF_EVENTS');
    copyContents(ACTION_MSG, newACTION_MSG);
    copyContents(HCAL_EVENTSTAKEN, newHCAL_EVENTSTAKEN);
    copyContents(RUN_NUMBER, newRUN_NUMBER);
    makecheckbox('newRUN_NUMBERcheckbox', 'RUN_NUMBER');
    copyContents(HCAL_TIME_OF_FM_START, newHCAL_TIME_OF_FM_START);
    copyContents(SINGLEPARTITION_MODE, newSINGLEPARTITION_MODE);
    makecheckbox('newSINGLEPARTITION_MODEcheckbox', 'SINGLEPARTITION_MODE');
    hidecheckboxes();
    hideinitializebutton();
    hidelocalparams();
    hideduplicatefield('CFGSNIPPET_KEY_SELECTED');
    hideduplicatefield('RUN_CONFIG_SELECTED');
    hideduplicatefield('MASKED_RESOURCES');
    hideduplicatefield('MASK_SUMMARY');
    hideduplicatefield('NUMBER_OF_EVENTS');
    hideduplicatefield('ACTION_MSG');
    hideduplicatefield('RUN_NUMBER');
    hideduplicatefield('HCAL_EVENTSTAKEN');
    hideduplicatefield('HCAL_TIME_OF_FM_START');
    removeduplicatecheckbox('USE_RESET_FOR_RECOVER');
    removeduplicatecheckbox('USE_PRIMARY_TCDS');
    getfullpath();
    showsupervisorerror();
    moveversionnumber();
    onClickCommandParameterCheckBox();

    $('.maskModes').css("style", "display: inline");
    $('.maskModes').click(function () {
       $(this).siblings().css('color', 'black');
       $(this).css('color', 'blue');
       panelId = "#".concat($(this).attr("id")).concat("Selection");
       $(panelId).siblings().hide();
       $(panelId).show();
       if ($(this).attr("id") == "multiPartition") {
         $('#newSINGLEPARTITION_MODEcheckbox :checkbox').prop('checked', true);
         $('#SINGLEPARTITION_MODE').val("false");
       }
       else if ($(this).attr("id") == "singlePartition") {
         $('#newSINGLEPARTITION_MODEcheckbox :checkbox').prop('checked', true);
         $('#SINGLEPARTITION_MODE').val("true");
       }
       $(panelId).parent().find("input").prop('checked', false);
       fillMask();
    });
}
