//////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
//
//  Illuminance (solar radiation) Device Control for SmartThings
//  Copyright (c)2019-2020 Mark Page (mark@very3.net)
//  Modified: Thu Nov 28 9:11:21 CST 2019
//
//  Control lighting routines and devices based on time of day and solar radiation levels from the very3 Ambient PWS
//  Device Handler. For more information see:
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
  name: "SR Lighting Control",
  version: "19.11.28.9",
  namespace: "sr-lighting-control",
  author: "Mark Page",
  description: "Control lighting routines and devices based on time of day and solar radiation levels from the very3 Ambient PWS Device Handler",
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
    def actions = location.helloHome?.getPhrases()*.label
    actions.sort()

    section ("SR Lighting Control") {
      paragraph "Control lighting routines and devices based on time of day and solar radiation levels from the very3 Ambient PWS Device Handler."
    }

    section ("Select Illuminance Source") {
      input ("illuminanceValue", "capability.illuminanceMeasurement", title: "Illuminance Measurement Source:", required: true)
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

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

def installed() {
  initialize()
}

def updated() {
  logger('info','updated',"Updated with settings: ${settings}")
  unsubscribe()
  unschedule()
  initialize()
}

def initialize() {
  state.logMode     = 1
  state.shmStatus   = 'off'
  state.logHandle   = 'SRLC'
  
  state.isLowSet    = false
  state.isHighSet   = false
  state.lastRun     = 'never'
  state.illuminance = 0

  subscribe(illuminanceValue, "illuminanceMeasurement" , illuminanceHandler)
  subscribe(location, "alarmSystemStatus" , shmHandler)
  
  poll()
}

def illuminanceHandler(evt) {
  logger('info','illuminanceHandler',"Illuminance Measurement ${evt.name} changed from ${state.illuminance} to ${evt.value}")
  state.illuminance = evt.value
  poll()
}

def shmHandler(evt) {
  logger('info','shmHandler',"Smart Home Monitor ${evt.name} changed from ${state.shmStatus} to ${evt.value}")
  state.shmStatus = evt.value
  poll()
}

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

def poll() {
  logger('info','poll',"Starting...")

  def tz = location.timeZone
  def dontStartLowTime  = timeToday(dontRunLowBefore,tz)
  def dontStartHighTime = timeToday(dontRunHighBefore,tz)
  
  BigDecimal illuminanceLevel = new BigDecimal(state.illuminance)

  // High Light Conditions and Actions
  if (illuminanceLevel >= srStartHighLevel && illuminanceLevel >= srStopLevel && now() >= dontStartHighTime.time && location.mode != dontRunHighMode) {
    if (state.isHighSet == false) {
      def srHighMsg = "${state.logHandle} HIGH (${illuminanceLevel}):\n"
      
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
      
      logger('info','poll',"${srHighMsg}")
      sendNotificationEvent("${srHighMsg}")
    }
  }
  else {
    state.isHighSet = false
  }

  // Low Light Conditions and Actions
  if (illuminanceLevel <= srStartLowLevel && illuminanceLevel >= srStopLevel && now() >= dontStartLowTime.time && location.mode != dontRunLowMode) {
    if (state.isLowSet == false) {
      def srLowMsg = "${state.logHandle} LOW (${illuminanceLevel}):\n"
      
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

  logger('info','poll',"Solar Radiation: ${illuminanceLevel}")
  logger('info','poll',"Result: HIGH:${state.isHighSet} LOW:${state.isLowSet} (${state.lastRun})")
}

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

private static double round(double value, int precision) {
  if (precision == 0) {
   return (int) Math.round(value)
  }
  
  int scale = (int) Math.pow(10,precision)
  return (double) Math.round(value*scale)/scale
}

private logger(type,loc,msg) {
  // type: error, warn, info, debug, trace
  if ("${type}" == 'info') {
    log."${type}" "${state.logHandle} [${loc}]: ${msg}"
  }
  else if (state.logMode > 0) {
    log."${type}" "${state.logHandle} [${loc}]: ${msg}"
  }
}