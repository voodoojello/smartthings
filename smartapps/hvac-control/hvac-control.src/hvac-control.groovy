/**
 *
 * ============================================
 *  PWS JSON Proxy - HVAC Control
 * ============================================
 *
 *  Control HVAC based on presence and various climate levels from the very3 Ambient PWS JSON proxy
 *
 *  Copyright (c)2017 Mark Page (mark@very3.net)
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 */

definition (
  name: "HVAC Control",
  version: "17.11.05.02",
  namespace: "hvac-control",
  author: "Mark Page",
  description: "Control HVAC based on presence and various climate levels from the very3 Ambient PWS JSON proxy. (17.11.05.02)",
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
      paragraph "Control HVAC based on presence and various climate levels from the very3 Ambient PWS JSON proxy. Polls in 30 minute intervals. (v17.11.05.02)"
    }

    section ("Select Thermostats") {
      input "thermostats", "capability.thermostat", title: "Select thermostats:", multiple: true, required: true
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

def installed() {
  log.debug "HVACC: Installed with settings: ${settings}"
  initialize()
}

def updated() {
  log.debug "HVACC: Updated with settings: ${settings}"
  unsubscribe()
  initialize()
}

def initialize() {
  mainRouter()
  runEvery30Minutes(mainRouter)
}

def mainRouter() {
  log.info "HVACC: Starting..."
  def getUrl  = "https://pws.very3.net/?_k="+appKey+"&_dc="+dayCool+"&_nc="+nightCool+"&_dh="+dayHeat+"&_nh="+nightHeat+"&_ct="+modeThresCool+"&_ht="+modeThresHeat+"&_nb="+nightStart+"+&_ne="+nightStop+"&_tt="+tweakTemp+"&_th="+tweakHumi
  def pwsData = fetchJSON(getUrl)
  
  def set_temp  = pwsData.hvac.set_temp
  def adj_temp  = pwsData.hvac.adj_temp
  def hvac_mode = pwsData.hvac.hvac_mode
  def currMode  = location.mode
  
  if (currMode.toLowerCase() == "away" && hvac_mode == "heat") {
  	adj_temp = awayHeatTemp
  }

  if (currMode.toLowerCase() == "away" && hvac_mode == "cool") {
  	adj_temp = awayCoolTemp
  }

  if (hvac_mode == "cool" && thermHoldSwitch.currentSwitch == 'off') {
    thermostats.cool()
    thermostats.fanAuto()
    thermostats.setCoolingSetpoint(adj_temp)
  }

  if (hvac_mode == "heat" && thermHoldSwitch.currentSwitch == 'off') {
    thermostats.heat()
    thermostats.fanAuto()
    thermostats.setHeatingSetpoint(adj_temp)
  }

  if (thermHoldSwitch.currentSwitch == 'on') {
    sendNotificationEvent("HVACC: thermHoldSwitch is ON, no action taken.")
  }
  else {
    sendNotificationEvent("HVACC: Set HVAC mode to ${hvac_mode}, set temperature to ${pwsData.hvac.set_temp} (${adj_temp} adjusted, ${currMode} mode, Night ${pwsData.hvac.nightmode}). OS Temperature: ${pwsData.pws.outtemp}°. OS Humidity: ${pwsData.pws.outhumi}%.")
  }

  log.trace "HVACC [${thermostats[0].label}] latestTempValue: ${thermostats[0].latestValue("temperature")}"
  log.trace "HVACC [${thermostats[0].label}] latestModeValue: ${thermostats[0].latestValue("thermostatMode")}"
  log.trace "HVACC [${thermostats[0].label}] latestCoolingSetPoint: ${thermostats[0].latestValue("coolingSetpoint")}"

  log.trace "HVACC [${thermostats[1].label}] latestTempValue: ${thermostats[1].latestValue("temperature")}"
  log.trace "HVACC [${thermostats[1].label}] latestModeValue: ${thermostats[1].latestValue("thermostatMode")}"
  log.trace "HVACC [${thermostats[1].label}] latestHeatingSetPoint: ${thermostats[1].latestValue("heatingSetpoint")}"

  log.info "HVACC thermHoldSwitchState: ${thermHoldSwitch.currentSwitch}"
  log.info "HVACC set_temp: ${set_temp}"
  log.info "HVACC night_mode: ${pwsData.hvac.nightmode}"
  log.info "HVACC PWS outtemp: ${pwsData.pws.outtemp}"
  log.info "HVACC PWS outhumi: ${pwsData.pws.outhumi}"
  log.info "HVACC PWS apptemp: ${pwsData.pws.apptemp}"
  log.info "HVACC PWS set_temp: ${pwsData.hvac.set_temp}"
  log.info "HVACC PWS adj_temp: ${pwsData.hvac.adj_temp}"
  log.info "HVACC PWS diff_temp: ${pwsData.hvac.diff_temp}"
  log.info "HVACC PWS apdiff_temp: ${pwsData.hvac.apdiff_temp}"
  log.info "HVACC PWS hvac_mode: ${pwsData.hvac.hvac_mode}"
}

def fetchJSON(pwsURI) {
  def params = [
    uri: pwsURI,
    format: 'json',
    contentType: 'application/json'
  ]

  try {
    httpGet(params) { response ->
      log.info "HVACC: httpGet (OK)"
      return response.data
    }
  }
  catch (e) {
    log.error "HVACC: httpGet (ERROR: $e)"
    return "ERROR: $e"
  }
}