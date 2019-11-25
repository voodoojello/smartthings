//////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
//
//  Ambient HVAC Control for SmartThings
//  Copyright (c)2019-2020 Mark Page (mark@very3.net)
//  Modified: Sun Nov 24 08:28:56 CST 2019
//
//  Control HVAC based via published capabilities of the very3 Ambient PWS Device Handler.
//
//      https://github.com/voodoojello/smartthings/tree/master/devicetypes/apws-device-handler
//
//  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
//  in compliance with the License. You may obtain a copy of the License at:
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
//  Unless required by applicable law or agreed to in writing, software distributed under the License is
//  distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//  See the License for the specific language governing permissions and limitations under the License.
//
//////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

definition (
  name: "HVAC Control",
  version: "19.11.24.6",
  namespace: "hvac-control",
  author: "Mark Page",
  description: "Control HVAC based via published capabilities of the very3 Ambient PWS Device Handler",
  singleInstance: true,
  category: "SmartThings Internal",
  iconUrl: "https://raw.githubusercontent.com/voodoojello/smartthings/master/very3-256px.png",
  iconX2Url: "https://raw.githubusercontent.com/voodoojello/smartthings/master/very3-512px.png",
  iconX3Url: "https://raw.githubusercontent.com/voodoojello/smartthings/master/very3-512px.png"
)

preferences {
  page(name: "mainPage", install: true, uninstall: true)
}

def mainPage() {
  dynamicPage(name: "mainPage", title: "") {

    section ("HVAC Control") {
      paragraph "Control HVAC based on presence and various climate levels from the very3 Ambient PWS JSON server. Polls in 30 minute intervals. (v19.11.24.6)"
    }

    section ("Select Thermostats") {
      input "thermostats", "capability.thermostat", title: "Select thermostats:", multiple: true, required: true
    }
    
    section ("Select Thermostat Setpoint Source") {
      input "thermostatModeValue", "capability.thermostatMode", title: "Mode Source:", required: true
      input "thermostatCoolingSetpointValue", "capability.thermostatCoolingSetpoint", title: "Cooling Setpoint Source:", required: true
      input "thermostatHeatingSetpointValue", "capability.thermostatHeatingSetpoint", title: "Heating Setpoint Source:", required: true
    }

    section ("Day/Night Temperature Presets") {
      input "dayCool", "decimal", title: "Day cooling temperature:", required: true
      input "nightCool", "decimal", title: "Night cooling temperature:", required: true
      input "dayHeat", "decimal", title: "Day heating temperature:", required: true
      input "nightHeat", "decimal", title: "Night heating temperature:", required: true
    }

    section ("Night Start/Stop") {
      input "nightStart", "time", title: "Night cycle starts at hour:", required: true
      input "nightStop", "time", title: "Night cycle stops at hour:", required: true
    }

    section ("Heating/Cooling Changeover Temperatures") {
      input "modeThresCool", "decimal", title: "Cooling temperature threshold:", required: true
      input "modeThresHeat", "decimal", title: "Heating temperature threshold:", required: true
    }

    section ("Default Away Mode Temperatures") {
	  input "awayCoolTemp", "number", title: "Default away cooling temperature:", multiple: false, required: true
	  input "awayHeatTemp", "number", title: "Default away heating temperature:", multiple: false, required: true
    }

    section ("Virtual Hold Switch for Override") {
      input "thermHoldSwitch", "capability.switch", required: true, title: "Choose the virtual hold switch:"
    }

    section ("Application Authentication Key") {
      input "appKey", "text", title: "Numbers or text, 8 character minimum:", multiple: false, required: true
    }
    
    section ("Advanced: Comfort Calculation Tweaks") {
      paragraph title: "Warning!", "Setting these values out of range from the assigned defaults can have disasterous effects. Don't change these values without thoroughly testing. Safe tweak ranges for temperature are .1 to .5. Safe tweak ranges for humidity are .001 to .008."
      input "tweakTemp", "decimal", title: "Tweak temperature:", required: true, defaultValue:0.3
      input "tweakHumi", "decimal", title: "Tweak humidity:", required: true, defaultValue:0.001
    }

  }
}

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

def installed() {
  logger('info','installed',"Installed with settings: ${settings}")
  initialize()
}

def updated() {
  logger('info','updated',"Updated with settings: ${settings}")
  
  def getURL  = "https://pws.very3.net/?_k="+appKey+"&_dc="+dayCool+"&_nc="+nightCool+"&_dh="+dayHeat+"&_nh="+nightHeat+"&_ct="+modeThresCool+"&_ht="+modeThresHeat+"&_nb="+nightStart+"+&_ne="+nightStop+"&_tt="+tweakTemp+"&_th="+tweakHumi
  logger('debug','updated',"[getURL] ${getURL}")
  def pwsData = updateServer(getURL)
  
  logger('trace','updated',"PWS night_mode: ${pwsData.hvac.nightmode}")
  logger('trace','updated',"PWS set_temp: ${pwsData.hvac.set_temp}")
  logger('trace','updated',"PWS adj_temp: ${pwsData.hvac.adj_temp}")
  logger('trace','updated',"PWS diff_temp: ${pwsData.hvac.diff_temp}")
  logger('trace','updated',"PWS apdiff_temp: ${pwsData.hvac.apdiff_temp}")
  logger('trace','updated',"PWS hvac_mode: ${pwsData.hvac.hvac_mode}")
 
  unsubscribe()
  initialize()
}

def initialize() {
  state.logMode   = 0
  state.logHandle = 'hvacc-sa'
  
  poll()
  runEvery5Minutes(poll)
}

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

def poll() {
  logger('info','poll',"Starting...")
  
  def hvac_mode = thermostatModeValue.latestValue("thermostatMode")
  def currMode  = location.mode
  def adj_temp  = ''
  
  if (hvac_mode == "heat" && thermHoldSwitch.currentSwitch == 'off') {
  	adj_temp = thermostatHeatingSetpointValue.latestValue("thermostatHeatingSetpoint")
    
    if (currMode.toLowerCase() == "away") {
      adj_temp = awayHeatTemp
    }
    
    thermostats.heat()
    thermostats.fanAuto()
    thermostats.setHeatingSetpoint(adj_temp)
  }
  
  if (hvac_mode == "cool" && thermHoldSwitch.currentSwitch == 'off') {
  	adj_temp = thermostatHeatingSetpointValue.latestValue("thermostatCoolingSetpoint")
    
    if (currMode.toLowerCase() == "away") {
      adj_temp = awayCoolTemp
    }
    
    thermostats.cool()
    thermostats.fanAuto()
    thermostats.setCoolingSetpoint(adj_temp)
  }
  
  logger('debug','poll',"hvac_mode: ${hvac_mode}, adj_temp: ${adj_temp}, currMode: ${currMode}, thermHoldSwitch: ${thermHoldSwitch.currentSwitch}")

  if (thermHoldSwitch.currentSwitch == 'on') {
    sendNotificationEvent("${state.logHandle}: thermHoldSwitch is ON, no action taken.")
    logger('info','poll',"thermHoldSwitch is ON, no action taken.")
  }
  else {
    sendNotificationEvent("${state.logHandle}: Set HVAC mode to ${hvac_mode}, set temperature to ${adj_temp}")
    logger('info','poll',"Set HVAC mode to ${hvac_mode}, set temperature to ${adj_temp}")
  }

  logger('debug','poll',"[Device Handler] thermostatModeValue: ${thermostatModeValue.latestValue("thermostatMode")}")
  logger('debug','poll',"[Device Handler] thermostatCoolingSetpointValue: ${thermostatCoolingSetpointValue.latestValue("thermostatCoolingSetpoint")}")
  logger('debug','poll',"[Device Handler] thermostatHeatingSetpointValue: ${thermostatHeatingSetpointValue.latestValue("thermostatHeatingSetpoint")}")

  logger('debug','poll',"[${thermostats[0].label}] latestTempValue: ${thermostats[0].latestValue("temperature")}")
  logger('debug','poll',"[${thermostats[0].label}] latestModeValue: ${thermostats[0].latestValue("thermostatMode")}")
  logger('debug','poll',"[${thermostats[0].label}] latestCoolingSetPoint: ${thermostats[0].latestValue("coolingSetpoint")}")

  logger('debug','poll',"[${thermostats[1].label}] latestTempValue: ${thermostats[1].latestValue("temperature")}")
  logger('debug','poll',"[${thermostats[1].label}] latestModeValue: ${thermostats[1].latestValue("thermostatMode")}")
  logger('debug','poll',"[${thermostats[1].label}] latestHeatingSetPoint: ${thermostats[1].latestValue("heatingSetpoint")}")
}

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

def updateServer(pushURI) {
  def params = [
    uri: pushURI,
    format: 'json',
    contentType: 'application/json'
  ]

  try {
    httpGet(params) { response ->
      logger('info','updateServer',"httpGet (OK)")
      logger('debug','updateServer',"${response.data}")
      return response.data
    }
  }
  catch (e) {
    logger('error','updateServer',"httpGet (ERROR: ${e})")
    return "ERROR: $e"
  }
}

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

private logger(type,loc,msg) {
  // type: error, warn, info, debug, trace
  if ("${type}" == 'info') {
    log."${type}" "${state.logHandle} [${loc}]: ${msg}"
  }
  else if (state.logMode > 0) {
    log."${type}" "${state.logHandle} [${loc}]: ${msg}"
  }
}
