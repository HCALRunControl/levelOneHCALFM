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
    $('#commandParameterCheckBox').attr("onclick", "onClickCommandParameterCheckBox(); toggle_visibility('Blork');");


    setInterval(function () {

        hidelocalparams();
        var currentState = $('#currentState').text();
        var currentState = $('#currentState').text();
        $('#currentState').attr("class", "hcal_control_" + currentState);
        if (currentState == "Initial") {
            $('#newRUN_CONFIG_SELECTEDcheckbox :checkbox').show();
            $('#newOLD_MASKED_RESOURCEScheckbox :checkbox').show();
        }
        else {
            $('#newRUN_CONFIG_SELECTEDcheckbox :checkbox').hide();
            $('#newOLD_MASKED_RESOURCEScheckbox :checkbox').hide();
        }
        $('#commandParameterCheckBox').attr("onclick", "onClickCommandParameterCheckBox(); toggle_visibility('Blork');");
    }, 750);


    $('#dropdowndiv').on('change', 'select', function () {
        $('#masked_resourses_td').show();
    });
});

function setProgress(progress) {
    var numberOfEvents = $("#NUMBER_OF_EVENTS").val(),
        containerWidth = $(".container").width();
    var progressPercent = 100 * progress / numberOfEvents;
    var progressBarWidth = progressPercent * (containerWidth / 100);
    $(".progressbar").width(progressBarWidth);
    console.log("progressPercent is: " + progressPercent);
    //$(".progressbar").width(progressBarWidth).html(progressPercent + "% &nbsp; &nbsp;");
}

function mirrorSelection() {
    $('#CFGSNIPPET_KEY_SELECTED').val($('#dropdown option:selected').text());
    $('#RUN_CONFIG_SELECTED').val($('#dropdown option:selected').val());
    //$('#MASKED_RESOURCES').val($('#dropdown option:selected').attr("maskedresources"));
}

function checkCheckboxes() {
    $('#newCFGSNIPPET_KEY_SELECTEDcheckbox :checkbox').prop('checked', true);
    $('#newRUN_CONFIG_SELECTEDcheckbox :checkbox').prop('checked', true);
    $('#newOLD_MASKED_RESOURCEScheckbox :checkbox').prop('checked', true);
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
        $('#newOLD_MASKED_RESOURCEScheckbox :checkbox').click();
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
    var maskedResourcesNumber = $('#OLD_MASKED_RESOURCES').attr("name").substring(20);
    var maskedResourcesArgs = "'" + maskedResourcesNumber + "', 'OLD_MASKED_RESOURCES'";
    var onchanges = "onClickGlobalParameterCheckBox(" + cfgSnippetArgs + "); onClickGlobalParameterCheckBox(" + masterSnippetArgs + "); onClickGlobalParameterCheckBox(" + maskedResourcesArgs + "); clickboxes(); mirrorSelection();";
    $('#dropdown').attr("onchange", onchanges);
}

function fillMask() {
    var old_allMasks="";
    var finalMasks=[];
    $('#masks :checked').each(function () {
        //old_allMasks += $(this).val() + ";";
        finalMasks.push($(this).val());
    });
    //$('#OLD_MASKED_RESOURCES').val($('#OLD_MASKED_RESOURCES').val() + old_allMasks);
    $('#MASKED_RESOURCES').val(JSON.stringify(finalMasks));
    //$('#maskTest').html($('#OLD_MASKED_RESOURCES').val() + old_allMasks);
    $('#maskTest').html(JSON.stringify(finalMasks));
}

function makecheckboxes(availableResources) {
    availableResources = availableResources.substring(0, availableResources.length - 1);
    var array = availableResources.split(';');
    var maskDivContents = "<ul>";
    for (var i = 0, l = array.length; i < l; i++) {
        var option = array[i].split(':');
        var checkbox = "<li><input type='checkbox' onchange='fillMask();' value='" + option + "'>" + option + "</li>";
        maskDivContents += checkbox;
    }
    maskDivContents += "</ul>";
    $('#masks').html(maskDivContents);
}

function hidecheckboxes() {
    var currentState = $('#currentState').text();
    if (currentState == "Initial") {
        $('#dropdowndiv').show();
        $('#newCFGSNIPPET_KEY_SELECTEDcheckbox :checkbox').show();
        $('#newRUN_CONFIG_SELECTEDcheckbox :checkbox').show();
        $('#newOLD_MASKED_RESOURCEScheckbox :checkbox').show();
    }
    else {
        $('#dropdowndiv').hide();
        $('#newCFGSNIPPET_KEY_SELECTEDcheckbox :checkbox').hide();
        $('#newRUN_CONFIG_SELECTEDcheckbox :checkbox').hide();
        $('#newOLD_MASKED_RESOURCEScheckbox :checkbox').hide();
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


function hcalOnLoad() {
    activate_relevant_table('AllParamTables');
    onClickCommandParameterCheckBox();
    removeduplicatecheckbox('CFGSNIPPET_KEY_SELECTED');
    removeduplicatecheckbox('RUN_CONFIG_SELECTED');
    removeduplicatecheckbox('OLD_MASKED_RESOURCES');
    removeduplicatecheckbox('NUMBER_OF_EVENTS');
    removeduplicatecheckbox('ACTION_MSG');
    removeduplicatecheckbox('RUN_NUMBER');
    copyContents(CFGSNIPPET_KEY_SELECTED, newCFGSNIPPET_KEY_SELECTED);
    makecheckbox('newCFGSNIPPET_KEY_SELECTEDcheckbox', 'CFGSNIPPET_KEY_SELECTED');
    copyContents(RUN_CONFIG_SELECTED, newRUN_CONFIG_SELECTED);
    makecheckbox('newRUN_CONFIG_SELECTEDcheckbox', 'RUN_CONFIG_SELECTED');
    copyContents(OLD_MASKED_RESOURCES, newOLD_MASKED_RESOURCES);
    makecheckbox('newOLD_MASKED_RESOURCEScheckbox', 'OLD_MASKED_RESOURCES');
    copyContents(MASKED_RESOURCES, newMASKED_RESOURCES);
    makecheckbox('newMASKED_RESOURCEScheckbox', 'MASKED_RESOURCES');
    copyContents(NUMBER_OF_EVENTS, newNUMBER_OF_EVENTS);
    makecheckbox('newNUMBER_OF_EVENTScheckbox', 'NUMBER_OF_EVENTS');
    copyContents(ACTION_MSG, newACTION_MSG);
    copyContents(HCAL_EVENTSTAKEN, newHCAL_EVENTSTAKEN);
    copyContents(RUN_NUMBER, newRUN_NUMBER);
    makecheckbox('newRUN_NUMBERcheckbox', 'RUN_NUMBER');
    copyContents(HCAL_TIME_OF_FM_START, newHCAL_TIME_OF_FM_START);
    hidecheckboxes();
    hideinitializebutton();
    hidelocalparams();
    hideduplicatefield('CFGSNIPPET_KEY_SELECTED');
    hideduplicatefield('RUN_CONFIG_SELECTED');
    hideduplicatefield('OLD_MASKED_RESOURCES');
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
}
