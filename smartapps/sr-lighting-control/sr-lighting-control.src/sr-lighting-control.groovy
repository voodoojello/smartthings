/**
 *
 * ============================================
 *  PWS JSON Proxy - SR Lighting Control
 * ============================================
 *
 *  Control lighting routines and devices based on time of day and solar radiation levels from the very3 Ambient PWS JSON proxy
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
  name: "SR Lighting Control",
  version: "17.10.29.1a",
  namespace: "sr-lighting-control",
  author: "Mark Page",
  description: "Control lighting routines and devices based on time of day and solar radiation levels from the very3 Ambient PWS JSON proxy. (17.10.29.1a)",
  singleInstance: false,
  category: "SmartThings Internal",
  iconUrl: "https://raw.githubusercontent.com/voodoojello/smartthings-apps/master/very3-256px.png",
  iconX2Url: "https://raw.githubusercontent.com/voodoojello/smartthings-apps/master/very3-512px.png",
  iconX3Url: "https://raw.githubusercontent.com/voodoojello/smartthings-apps/master/very3-512px.png"
)

preferences {
  page(name: "mainPage", install: true, uninstall: true)
}

def mainPage() {
  dynamicPage(name: "mainPage", title: "") {
    def actions = location.helloHome?.getPhrases()*.label
    actions.sort()

    section ("SR Lighting Control") {
      paragraph "Control lighting routines and devices based on time of day and solar radiation levels from the very3 Ambient PWS JSON proxy. Polls in 5 minute intervals. Note: light levels are based on watts per square meter, not lumens."
    }

    section ("Setting for low-light conditions") {
      input "srStartLowLevel", "number", title: "Start low-light check when solar radiation level is less than:", required: true
      input "dontRunLowBefore","time",title:"Do not run before this time:", required: true
      input "srLowOffDevices", "capability.switch", title: "Select devices to turn OFF in low-light:", multiple: true, required: false
      input "srLowOnDevices", "capability.switch", title: "Select devices to turn ON in low-light:", multiple: true, required: false
      input "srLowDoors", "capability.garageDoorControl", title: "Select doors to CLOSE in low-light:", multiple: true, required: false
      input "srLowRoutine", "enum", title: "Routine to execute when low-light conditions meet:", options: actions, required: false
      input "dontRunLowMode", "mode", title: "Stop low-light checks if mode is:", multiple: false, required: true
    }

    section ("Setting for high-light conditions") {
      input "srStartHighLevel", "number", title: "Start high-light check when solar radiation level is greater than:", required: true
      input "dontRunHighBefore","time",title:"Do not run before this time:", required: true
      input "srHighOffDevices", "capability.switch", title: "Select devices to turn OFF in high-light:", multiple: true, required: false
      input "srHighOnDevices", "capability.switch", title: "Select devices to turn ON in high-light:", multiple: true, required: false
      input "srHighDoors", "capability.garageDoorControl", title: "Select doors to CLOSE in high-light:", multiple: true, required: false
      input "srHighRoutine", "enum", title: "Routine to execute when high-light conditions meet:", options: actions, required: false
      input "dontRunHighMode", "mode", title: "Stop high-light checks if mode is:", multiple: false, required: true
    }

    section ("Global Settings") {
      paragraph "These settings apply to both low and high light condition checks"
      input "srStopLevel", "number", title: "Stop all checks when solar radiation level is less than:", required: true
    }
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
  state.isLowSet = false
  state.isHighSet = false
  state.lastRun = 'never'

  mainRouter()
  runEvery5Minutes(mainRouter)
}

def mainRouter() {
  log.info "SRLC: Starting..."
  def pwsData = fetchJSON("http://pws.very3.net")

  def tz = location.timeZone
  def dontStartLowTime = timeToday(dontRunLowBefore,tz)
  def dontStartHighTime = timeToday(dontRunHighBefore,tz)

  // High Light Conditions and Actions
  if (pwsData.pws.solarrad >= srStartHighLevel && pwsData.pws.solarrad >= srStopLevel && now() >= dontStartHighTime.time && location.mode != dontRunHighMode) {
    if (state.isHighSet == false) {
      def srHighMsg = "SRLC HIGH (${pwsData.pws.solarrad}):\n"
      
      if (srHighOffDevices != null) {
        srHighOffDevices.off()
        srHighMsg += "Turned OFF: ${srHighOffDevices}\n\n"
      }
      
      if (srHighOnDevices != null) {
        srHighOnDevices.on()
        srHighMsg += "Turned ON: ${srHighOnDevices}\n\n"
      }
      
      if (srHighDoors != null) {
        srHighDoors.close()
        srHighMsg += "CLOSED Doors: ${srHighOnDevices}\n\n"
      }
      
      if (srHighRoutine != null) {
        location.helloHome?.execute(srHighRoutine)
        srHighMsg += "RAN Routine: ${srHighRoutine}"
      }

      state.isHighSet = true
      state.lastRun = now()
      log.info $srHighMsg
      sendNotificationEvent("${srHighMsg}")
    }
  }
  else {
    state.isHighSet = false
  }

  // Low Light Conditions and Actions
  if (pwsData.pws.solarrad <= srStartLowLevel && pwsData.pws.solarrad >= srStopLevel && now() >= dontStartLowTime.time && location.mode != dontRunLowMode) {
    if (state.isLowSet == false) {
      def srLowMsg = "SRLC LOW (${pwsData.pws.solarrad}):\n"
      
      if (srLowOffDevices != null ) {
      	srLowOffDevices.off()
        srLowMsg += "Turned OFF: ${srLowOffDevices}\n\n"
      }
      
      if (srLowOnDevices != null) {
        srLowOnDevices.on()
        srLowMsg += "Turned ON: ${srLowOnDevices}\n\n"
      }
      
      if (srLowDoors != null) {
        srLowDoors.close()
        srLowMsg += "CLOSED Doors: ${srLowDoors}\n\n"
      }
      
      if (srLowRoutine != null) {
        location.helloHome?.execute(srLowRoutine)
        srLowMsg += "RAN Routine: ${srLowRoutine}"
      }

      state.isLowSet = true
      state.lastRun = now()
      log.info $srLowMsg
      sendNotificationEvent("${srLowMsg}")
    }
  }
  else {
    state.isLowSet = false
  }

  log.info "SRLC Solar Radiation: ${pwsData.pws.solarrad}"
  log.info "SRLC PWS Verification: ${pwsData.app.updated_long}"
  log.info "SRLC Result: HIGH:${state.isHighSet} LOW:${state.isLowSet} (${state.lastRun})"
}

def fetchJSON(pwsURI) {
  def params = [
    uri: pwsURI,
    format: 'json',
    contentType: 'application/json'
  ]

  try {
    httpGet(params) { response ->
      log.info "SRLC: httpGet (OK)"
      return response.data
    }
  }
  catch (e) {
    log.error "SRLC: httpGet (ERROR: $e)"
    return "ERROR: $e"
  }
}
