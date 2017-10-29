/**
 *
 * ============================================
 *  Pool Sprayer and Pump Control
 * ============================================
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
  name: "Pool Sprayer and Pump Control",
  namespace: "pool-sprayer-and-pump-control",
  author: "Mark Page",
  description: "Control pool pump and sprayer by air temperature and wind speed from the very3 Ambient PWS JSON proxy (v17.9.16.15a)",
  singleInstance: true,
  category: "SmartThings Internal",
  iconUrl: "https://raw.githubusercontent.com/voodoojello/smartthings-apps/master/very3-256px.png",
  iconX2Url: "https://raw.githubusercontent.com/voodoojello/smartthings-apps/master/very3-512px.png",
  iconX3Url: "https://raw.githubusercontent.com/voodoojello/smartthings-apps/master/very3-512px.png"
)

preferences {
  section {
    paragraph "This app controls the pool pump and sprayer based on the outside air temperature and wind speed as provided by the very3 Ambient PWS JSON proxy."
  }
  section {
  	paragraph "Pool Pump Settings"
    input "pumpSwitch", "capability.switch", required: true, title: "Choose the pump switch to control:"
    input "pumpHighTempThres", "number", required: true, title: "Turn off the pump if temperature goes above:"
    input "pumpLowTempThres", "number", required: true, title: "Turn off the pump if the temperature drops below:"
    input "pumpHoldSwitch", "capability.switch", required: true, title: "Choose the pool pump virtual hold switch:"
  }
  section {
  	paragraph "Pool Sprayer Settings"
    input "sprayerSwitch", "capability.switch", required: true, title: "Choose the sprayer switch to control:"
    input "sprayerHighWindThres", "number", required: true, title: "Turn off the sprayer if windspeed goes above:"
    input "sprayerLowTempThres", "number", required: true, title: "Turn off the sprayer if the temperature drops below:"
    input "sprayerHoldSwitch", "capability.switch", required: true, title: "Choose the sprayer virtual hold switch:"
  }
}

def installed() {
  initialize()
}

def updated() {
  unsubscribe()
  initialize()
}

def initialize() {
  mainRouter()
  runEvery15Minutes(mainRouter)
}

def mainRouter() {
  def pwsData = fetchJSON("http://pws.very3.net")
  log.trace "PWS Data: ${pwsData.pws}"

  // Pump Switch
  if (pwsData.pws.outtemp >= pumpHighTempThres) {
    state.pumpSwitchState = 'OFF'
    state.pumpSwitchCause = "Temperature: ${pwsData.pws.outtemp} / ${pumpHighTempThres}"
  }
  else if (pwsData.pws.outtemp <= pumpLowTempThres) {
    state.pumpSwitchState = 'OFF'
    state.pumpSwitchCause = "Temperature: ${pwsData.pws.outtemp} / ${pumpLowTempThres}"
  }
  else {
    state.pumpSwitchState = 'ON'
    state.pumpSwitchCause = "Temperature: ${pwsData.pws.outtemp} / ${pumpLowTempThres} / ${pumpHighTempThres}"
  }

  if (pumpHoldSwitch.currentSwitch == 'on') {
   state.pumpSwitchCause = "Pool Pump is in a hold state"
  }

  if (state.pumpSwitchState == 'ON' && pumpSwitch.currentSwitch == 'off' && pumpHoldSwitch.currentSwitch == 'off') {
    sendNotificationEvent("Pool Pump turned ${state.pumpSwitchState}: (${state.pumpSwitchCause})")
    pumpSwitch.on()
  }
  if (state.pumpSwitchState == 'OFF' && pumpSwitch.currentSwitch == 'on' && pumpHoldSwitch.currentSwitch == 'off') {
    sendNotificationEvent("Pool Pump turned ${state.pumpSwitchState}: (${state.pumpSwitchCause})")
    pumpSwitch.off()
  }
  log.trace "Pool Pump turned ${state.pumpSwitchState}: ${state.pumpSwitchCause}"

  // Sprayer Switch
  if (pwsData.pws.outtemp <= sprayerLowTempThres) {
    state.sprayerSwitchState = 'OFF'
    state.sprayerSwitchCause = "Temperature: ${pwsData.pws.outtemp} / ${sprayerLowTempThres}"
  }
  else if (pwsData.pws.avgwind >= sprayerHighWindThres) {
    state.sprayerSwitchState = 'OFF'
    state.sprayerSwitchCause = "Wind Speed: ${pwsData.pws.avgwind} / ${sprayerHighWindThres}"
  }
  else {
    state.sprayerSwitchState = 'ON'
    state.sprayerSwitchCause = "Temperature: ${pwsData.pws.outtemp} / ${sprayerLowTempThres} - ${pwsData.pws.avgwind} / ${sprayerHighWindThres}"
  }

  if (sprayerHoldSwitch.currentSwitch == 'on') {
   state.sprayerSwitchCause = "Pool Sprayer is in a hold state"
  }
 
  if (state.sprayerSwitchState == 'ON' && sprayerSwitch.currentSwitch == 'off' && sprayerHoldSwitch.currentSwitch == 'off') {
    sendNotificationEvent("Pool Sprayer turned ${state.sprayerSwitchState}: (${state.sprayerSwitchCause})")
    sprayerSwitch.on()
  }
  if (state.sprayerSwitchState == 'OFF' && sprayerSwitch.currentSwitch == 'on' && sprayerHoldSwitch.currentSwitch == 'off') {
    sendNotificationEvent("Pool Sprayer turned ${state.sprayerSwitchState}: (${state.sprayerSwitchCause})")
    sprayerSwitch.off()
  }
  log.trace "Pool Sprayer turned ${state.sprayerSwitchState}: ${state.sprayerSwitchCause}"
}

def fetchJSON(pwsURI) {
  def params = [
    uri: pwsURI,
    format: 'json',
    contentType: 'application/json'
  ]

  try {
    httpGet(params) { response ->
      log.debug "HTTPGET: Success!"
      return response.data
    }
  }
  catch (e) {
    log.debug "HTTPGET: Failed! ($e)"
    return "error: $e"
  }
}