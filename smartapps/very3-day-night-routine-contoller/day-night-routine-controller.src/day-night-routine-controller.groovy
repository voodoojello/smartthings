//////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
//
//  Day/Night Routine Controller for SmartThings
//  Copyright (c)2019-2020 Mark Page (mark@very3.net)
//  Modified: Fri Dec  6 19:38:50 CST 2019
//
//  The Day/Night Routine Controller for SmartThings allows conditional firing of specific routines based on
//  changes to presence and daylight (illumination). Secondary routines can be fired after a set interval.
//
//////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
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
  name: "Day/Night Routine Controller",
  version: "19.12.6.19",
  namespace: "very3-day-night-routine-contoller",
  author: "Mark Page",
  description: "Day/Night Routine Controller for SmartThings",
  singleInstance: true,
  category: "SmartThings Internal",
  iconUrl: "https://raw.githubusercontent.com/voodoojello/smartthings/master/very3-256px.png",
  iconX2Url: "https://raw.githubusercontent.com/voodoojello/smartthings/master/very3-512px.png",
  iconX3Url: "https://raw.githubusercontent.com/voodoojello/smartthings/master/very3-512px.png"
)

preferences {
  page(name: "mainPage", title: "Presence, Illuminance and App Settings", nextPage: "daySettings", uninstall: true) {
    section("Select Presence Users") {
      input ("userPresenceList", "capability.presenceSensor", title: "Monitor These Presences", required: true, multiple: true, submitOnChange: true)
    }
    
    section("Select Illuminance Measurement Source") {
      input ("illuminanceValue", "capability.illuminanceMeasurement", title: "Illuminance Measurement Source:", required: true)
    }
    
    section("Low Light Threshold") {
      paragraph ("The low light threshold is represented in lumens per square meter (lux). Dusk is typically about 200-400 lux depending on time of year and geographical location.")
      input ("lightThreshold", "number", title: "Low light change threshold (lux)", default: 300, required: true)
    }
    
    section ("Notify on change") {
      paragraph "Notify me when the Presence Monitor makes changes"
      input("presenceMonitorNotify", "bool", title: "Notify on change")
    }
    
    section ("Enable/Disable (bypass) Day/Night Routine Controller") {
      input("presenceMonitorEnable", "bool", title: "Enable Presence Monitor", default: true)
    }
  }
  
  page(name: "daySettings", title: "Day Settings", nextPage: "nightSettings")
  page(name: "nightSettings", title: "Night Settings", install: true)
}

def daySettings() {
  def actions = location.helloHome?.getPhrases()*.label
  actions.sort()
    
  dynamicPage(name: "daySettings", title: "Day Settings") {
    section("Day Presence Change Routines") {
      paragraph("Routines to run when presence mode changes during the day (above low light threshold).")
      input ("illuminanceHighAwayRoutine", "enum", title: "Day AWAY Routine:", options: actions, required: false)
      input ("illuminanceHighHomeRoutine", "enum", title: "Day HOME Routine:", options: actions, required: false)
    }

    section("Delayed Day Settings") {
      paragraph("The changes below will be run after the initial presence change based on the delay interval (in minutes) provided. Resonable values are between 1 and 10 minutes.")
      input ("illuminanceHighDelay", "number", title: "Delay interval (in minutes)", required: false)
    }
    
    section("Day Presence Change Delayed Routines") {
      paragraph("Routines to run after delay when presence mode changes during the day (above low light threshold).")
      input ("illuminanceHighAwayDelayedRoutine", "enum", title: "Delayed Day AWAY Routine:", options: actions, required: false)
      input ("illuminanceHighHomeDelayedRoutine", "enum", title: "Delayed Day HOME Routine:", options: actions, required: false)
    }
  }
}

def nightSettings() {
  def actions = location.helloHome?.getPhrases()*.label
  actions.sort()
  
  dynamicPage(name: "nightSettings", title: "Night Settings") {
    section("Night Presence Change Routines") {
      paragraph("Routines to run when presence mode changes at night (below low light threshold).")
      input ("illuminanceLowAwayRoutine", "enum", title: "Night AWAY Routine:", options: actions, required: false)
      input ("illuminanceLowHomeRoutine", "enum", title: "Night HOME Routine:", options: actions, required: false)
    }
    
    section("Delayed Night Settings") {
      paragraph("The changes below will be run after the initial presence change based on the delay interval (in minutes) provided. Resonable values are between 1 and 10 minutes.")
      input ("illuminanceLowDelay", "number", title: "Delay interval (in minutes)", required: false)
    }
    
    section("Night Presence Change Delayed Routines") {
      paragraph("Routines to run after delay when presence mode changes at night (below low light threshold).")
      input ("illuminanceLowAwayDelayedRoutine", "enum", title: "Delayed Night AWAY Routine:", options: actions, required: false)
      input ("illuminanceLowHomeDelayedRoutine", "enum", title: "Delayed Night HOME Routine:", options: actions, required: false)
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
  state.logMode   = 3
  state.logHandle = 'DNRC'
  state.appTitle  = 'Day/Night Routine Controller'
  state.isHome    = null
  state.wasHome   = null
  state.isNight   = null
  state.wasNight  = null
  
  state.presenceStatus    = userPresenceList.latestValue("presence")
  state.illuminanceStatus = illuminanceValue.latestValue("illuminance")
  
  subscribe(userPresenceList, "presence", presenceHandler)
  subscribe(illuminanceValue, "illuminance" , illuminanceHandler)

  router()
}

// Presence event handler
def presenceHandler(evt) {
  logger('info','presenceHandler',"Presence ${evt.name} changed to ${evt.value}")
  state.presenceStatus = evt.value
  router()
}

// Illuminance event handler
def illuminanceHandler(evt) {
  logger('info','illuminanceHandler',"Illuminance ${evt.name} changed to ${evt.value}")
  state.illuminanceStatus = evt.value
  router()
}

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

def router() {
  logger('info','router',"Router event fired...")
  
  if (presenceMonitorEnable == false) {
    logger('info','router',"Presence Monitor disabled. No action taken.")
    return
  }

  int homeCheck = 0
  def hasChange = false
  
  // Presence check
  state.presenceStatus.each {
    if (it == 'present') {
      homeCheck = homeCheck + 1
    }
  }
  userPresenceList.latestValue("presence").each {
    if (it == 'present') {
      homeCheck = homeCheck + 1
    }
  }
  userPresenceList.currentValue("presence").each {
    if (it == 'present') {
      homeCheck = homeCheck + 1
    }
  }
  if (homeCheck > 0) {
    state.isHome = true
  }
  else {
    state.isHome = false
  }

  // Illuminance check
  if ((illuminanceValue.latestValue("illuminance") - lightThreshold) < 10) {
    state.isNight = true
  }
  else {
    state.isNight = false
  }

  // Detect state changes
  if (state.isHome != state.wasHome) {
    logger('debug','homeChange',"${state.wasHome} => ${state.isHome}")
    state.wasHome = state.isHome
    hasChange = true
  }
  if (state.isNight != state.wasNight) {
    logger('debug','nightChange',"${state.wasNight} => ${state.isNight}")
    state.wasNight = state.isNight
    hasChange = true
  }

  def msg = "homeCheck: ${homeCheck}, hasChange: ${hasChange}, [state.isHome: ${state.isHome}, state.illuminanceStatus: ${state.illuminanceStatus}, state.isNight: ${state.isNight}, state.presenceStatus: ${state.presenceStatus}]"

  // Route on change
  if (hasChange) {
    if (state.isNight == true && state.isHome == true) {
      nightHome()
    }
    if (state.isNight == true && state.isHome == false) {
      nightAway()
    }
    if (state.isNight == false && state.isHome == true) {
      dayHome()
    }
    if (state.isNight == false && state.isHome == false) {
      dayAway()
    }
  }
  
  logger('debug','router',"${msg}")
}

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

private nightHome() {
  def msg   = [:]
  def name  = 'Night Home'
  def delay = (illuminanceLowDelay * 60)
  
  if (illuminanceLowHomeRoutine != null) {
    location.helloHome.execute(settings.illuminanceLowHomeRoutine)
    msg["Ran ${name} Routine"] = illuminanceLowHomeRoutine
    
    if (illuminanceLowHomeDelayedRoutine != null) {
      runIn(delay, "delayRunner", [data: [routine: illuminanceLowHomeDelayedRoutine, source: "${name} Delayed"]])
      msg["Queued ${name} Delay Routine"] = illuminanceLowHomeDelayedRoutine
    }

    logger('trace','nightHomeRoute',"${msg}")
    notify(state.appTitle,"(${name}) ${msg}")
  }
}

private nightAway() {
  def msg   = [:]
  def name  = 'Night Away'
  def delay = (illuminanceLowDelay * 60)

  if (illuminanceLowAwayRoutine != null) {
    location.helloHome.execute(settings.illuminanceLowAwayRoutine)
    msg["Ran ${name} Routine"] = illuminanceLowAwayRoutine
    
    if (illuminanceLowAwayDelayedRoutine != null) {
      runIn(delay, "delayRunner", [data: [routine: illuminanceLowAwayDelayedRoutine, source: "${name} Delayed"]])
      msg["Queued ${name} Delay Routine"] = illuminanceLowAwayDelayedRoutine
    }

    logger('trace','nightAwayRoute',"${msg}")
    notify(state.appTitle,"(${name}) ${msg}")
  }
}

private dayHome() {
  def msg   = [:]
  def name  = 'Day Home'
  def delay = (illuminanceHighDelay * 60)
  
  if (illuminanceHighHomeRoutine != null) {
    location.helloHome.execute(settings.illuminanceHighHomeRoutine)
    msg["Ran ${name} Routine"] = illuminanceHighHomeRoutine
    
    if (illuminanceHighHomeDelayedRoutine != null) {
      runIn(delay, "delayRunner", [data: [routine: illuminanceHighHomeDelayedRoutine, source: "${name} Delayed"]])
      msg["Queued ${name} Delayed Routine"] = illuminanceHighHomeDelayedRoutine
    }
    
    logger('trace','dayHomeRoute',"${msg}")
    notify(state.logHandle,"(${name}) ${msg}")
  }
}

private dayAway() {
  def msg   = [:]
  def name  = 'Day Away'
  def delay = (illuminanceHighDelay * 60)
  
  if (illuminanceHighAwayRoutine != null) {
    location.helloHome.execute(settings.illuminanceHighAwayRoutine)
    msg["Ran ${name} Routine"] = illuminanceHighAwayRoutine
    
    if (illuminanceHighAwayDelayedRoutine != null) {
      runIn(delay, "delayRunner", [data: [routine: illuminanceHighAwayDelayedRoutine, source: "${name} Delayed"]])
      msg["Queued ${name} Delayed Routine"] = illuminanceHighAwayDelayedRoutine
    }
    
    logger('trace','dayAwayRoute',"${msg}")
    notify(state.appTitle,"(${name}) ${msg}")
  }
}

def delayRunner(data) {
  def msg = [:]
  
  if (data.routine != null) {
    location.helloHome.execute(settings."${data.routine}")
    msg["Ran Delayed Routine"] = data.routine
    
    logger('trace','delayRunner',"${msg}")
    notify(state.appTitle,"(${data.source}) ${msg}")
  }
}

private notify(title,msg) {
  if (presenceMonitorNotify) {
    sendNotificationEvent("[${title}]: ${msg}")
  }
}

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

private static double round(double value, int precision) {
  if (precision == 0) {
   return new BigDecimal(value).setScale(0, BigDecimal.ROUND_HALF_UP)
  }
  
  int scale = (int) Math.pow(10,precision)
  return (double) Math.round(value*scale)/scale
}

private logger(type,loc,msg) {
  if ("${type}" == 'info') {
    log."${type}" "[[${state.logHandle}]] [${loc}]: ${msg}"
  }
  else if (state.logMode > 0 && "${type}" == 'trace') {
    log."${type}" "[[${state.logHandle}]] [${loc}]: ${msg}"
  }
  else if (state.logMode > 1) {
    log."${type}" "[[${state.logHandle}]] [${loc}]: ${msg}"
  }
}