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
  singleInstance: false,
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
  state.isLowSet = false
  state.isHighSet = false
  state.lastRun = 'never'

  mainRouter()
  runEvery1Hour(mainRouter)
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

  if (hvac_mode == "cool") {
    thermostats.cool()
    thermostats.fanAuto()
    thermostats.setCoolingSetpoint(adj_temp)
  }

  if (hvac_mode == "heat") {
    thermostats.heat()
    thermostats.fanAuto()
    thermostats.setHeatingSetpoint(adj_temp)
  }
  
  
  def thermostatStates = thermostats.findAll { thermostatsVal ->
    log.debug "HVACC: ${thermostatsVal.coolingSetpoint}"
  }
  
  log.info "HVACC coolingSetpoint: ${thermostats.coolingSetpoint}"
  log.info "HVACC heatingSetpoint: ${thermostats.heatingSetpoint}"
  log.info "HVACC thermostatFanMode: ${thermostats.thermostatFanMode}"

  log.info "HVACC set_temp: ${set_temp}"
  
  log.info "HVACC PWS outtemp: ${pwsData.pws.outtemp}"
  log.info "HVACC PWS set_temp: ${pwsData.hvac.set_temp}"
  log.info "HVACC PWS adj_temp: ${pwsData.hvac.adj_temp}"
  log.info "HVACC PWS diff_temp: ${pwsData.hvac.diff_temp}"
  log.info "HVACC PWS hvac_mode: ${pwsData.hvac.hvac_mode}"

  sendNotificationEvent("HVACC: Set mode to ${hvac_mode}, set temperature to ${pwsData.hvac.set_temp} (${adj_temp} adjusted.)")
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