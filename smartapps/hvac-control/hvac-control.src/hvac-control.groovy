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
  version: "17.10.29.1a",
  namespace: "hvac-control",
  author: "Mark Page",
  description: "Control HVAC based on presence and various climate levels from the very3 Ambient PWS JSON proxy. (17.10.29.1a)",
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
      paragraph "Control HVAC based on presence and various climate levels from the very3 Ambient PWS JSON proxy. Polls in 1 hour intervals."
    }

    section ("Select thermostats to control...") {
      input "thermostats", "capability.thermostat", title: "Select thermostats:", multiple: true, required: true
    }

    section ("Select virtual hold switch to override auto set...") {
      input "thermHoldSwitch", "capability.switch", required: true, title: "Choose the HVAC virtual hold switch:"
    }

    section ("Set default away mode temperatures...") {
	  input "awayCoolTemp", "number", title: "Default away cooling temperature:", multiple: false, required: true
	  input "awayHeatTemp", "number", title: "Default away heating temperature:", multiple: false, required: true
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
  def pwsData = fetchJSON("http://pws.very3.net")
  
  def set_temp  = pwsData.hvac.set_temp
  def adj_temp  = pwsData.hvac.adj_temp
  def hvac_mode = pwsData.hvac.hvac_mode
  def currMode  = location.mode
  
  if (currMode == "away" && hvac_mode == "heat") {
  	adj_temp = awayHeatTemp
  }

  if (currMode == "away" && hvac_mode == "cool") {
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
    sendNotificationEvent("HVACC: Set mode to ${hvac_mode}, set temperature to ${pwsData.hvac.set_temp} (${adj_temp} adjusted). Outside temperature reported as ${pwsData.pws.outtemp} degrees.")
  }

  log.info "HVACC latestTempValue [0]: ${thermostats[0].latestValue("temperature")}"
  log.info "HVACC latestModeValue [0]: ${thermostats[0].latestValue("thermostatMode")}"
  log.info "HVACC latestCoolSetPointValue [0]: ${thermostats[0].latestValue("coolingSetpoint")}"

  log.info "HVACC latestTempValue [1]: ${thermostats[1].latestValue("temperature")}"
  log.info "HVACC latestModeValue [1]: ${thermostats[1].latestValue("thermostatMode")}"
  log.info "HVACC latestHeatSetPointValue [1]: ${thermostats[1].latestValue("heatingSetpoint")}"

  log.info "HVACC thermHoldSwitchState: ${thermHoldSwitch.currentSwitch}"
  log.info "HVACC set_temp: ${set_temp}"
  log.info "HVACC PWS outtemp: ${pwsData.pws.outtemp}"
  log.info "HVACC PWS apptemp: ${pwsData.pws.apptemp}"
  log.info "HVACC PWS set_temp: ${pwsData.hvac.set_temp}"
  log.info "HVACC PWS adj_temp: ${pwsData.hvac.adj_temp}"
  log.info "HVACC PWS diff_temp: ${pwsData.hvac.diff_temp}"
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