//////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
//
//  Day/Night/Presence (DNP) Routine Controller for SmartThings
//  Copyright (c)2019-2020 Mark Page (mark@very3.net)
//  Modified: Sun Dec  8 06:46:29 CST 2019
//
//  The Day/Night/Presence Routine Controller for SmartThings allows conditional firing of multiple routines based on
//  changes to presence and daylight (illumination). Secondary multiple routines can be fired after a set interval.
//
//  This SmartApp requires a illuminanceMeasurement capable device such as the Aeotec TriSensor (Z-Wave Plus S2) or
//  the Monoprice Z-Wave Plus PIR Multi Sensor. Any illuminance sensor that reports outdoor light levels (in lux)
//  should do the trick.
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
  name: "Day/Night/Presence (DNP) Routine Controller",
  version: "19.12.8.6",
  namespace: "very3-dnp-routine-contoller",
  author: "Mark Page",
  description: "The Day/Night/Presence (DNP) Routine Controller for SmartThings allows conditional firing of multiple routines based on changes to presence and daylight (illumination). Secondary multiple routines can be fired after a set interval.",
  singleInstance: true,
  category: "SmartThings Internal",
  iconUrl: "https://raw.githubusercontent.com/voodoojello/smartthings/master/very3-256px.png",
  iconX2Url: "https://raw.githubusercontent.com/voodoojello/smartthings/master/very3-512px.png",
  iconX3Url: "https://raw.githubusercontent.com/voodoojello/smartthings/master/very3-512px.png"
)

preferences {
  page(name: "mainPage", title: "DNP Contoller General Settings", nextPage: "daySettings", uninstall: true) {
    section("Select Presence Users") {
      input ("userPresenceList", "capability.presenceSensor", title: "Monitor These Presences", required: true, multiple: true, submitOnChange: true)
    }
    
    section("Select Illuminance Measurement Source") {
      input ("illuminanceValue", "capability.illuminanceMeasurement", title: "Illuminance Measurement Source:", required: true)
    }
    
    section("Low Light Threshold") {
      paragraph ("Light level to trigger day-to-night and night-to-day changes, represented in lumens per square meter (lux). Dusk and morning are typically about 200-400 lux but may vary wildly depending on sensor position, time of year, and geographical location.")
      input ("lightThreshold", "number", title: "Low light change threshold (lux)", defaultValue: 300, required: false)
    }

    section("Light Change Tolerance") {
      paragraph ("Dampens the \"Low Light Threshold\" setting to prevent the DNP Controller from bouncing between day and night. Default is ±10 lux.")
      input ("lightTolerance", "number", title: "Light change tolerance (lux)", defaultValue: 10, required: false)
    }

    section ("Notify on Change") {
      paragraph ("Notify me in \"Hello Home\" when the DNP Routine Controller makes changes")
      input("appNotify", "bool", title: "Notify on change", defaultValue: true)
    }
    
    section ("Enable/Disable (bypass) DNP Routine Controller") {
      paragraph ("Enable/Disable (bypass) the DNP Controller to prevent presence changes. Handy if you're leaving non-presence guests (like babysitters) at home alone.")
      input("appEnable", "bool", title: "Enable Presence Monitor", defaultValue: true)
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
      input ("illuminanceHighAwayRoutine", "enum", title: "When it's day and everyone leaves:", options: actions, multiple: true, required: false)
      input ("illuminanceHighHomeRoutine", "enum", title: "When it's day and anyone arrives:", options: actions, multiple: true, required: false)
    }
    
    section("Day Presence Change Delayed Routines") {
      paragraph("Routines to run after delay when presence mode changes during the day (above low light threshold).")
      input ("illuminanceHighAwayDelayedRoutine", "enum", title: "Delayed routines to run when it's day, after everyone has left:", options: actions, multiple: true, required: false)
      input ("illuminanceHighHomeDelayedRoutine", "enum", title: "Delayed routines to run when it's day, after anyone arrives:", options: actions, multiple: true, required: false)
    }
    
    section("Day Presence Change Wait Interval") {
      paragraph("Interval (in minutes) to wait before running delayed day routines.")
      input ("illuminanceHighDelay", "number", title: "Day delay interval (in minutes)", defaultValue: 10, required: false)
    }
  }
}

def nightSettings() {
  def actions = location.helloHome?.getPhrases()*.label
  actions.sort()
  
  dynamicPage(name: "nightSettings", title: "Night Settings") {
    section("Night Presence Change Routines") {
      paragraph("Routines to run when presence mode changes at night (below low light threshold).")
      input ("illuminanceLowAwayRoutine", "enum", title: "When it's night and everyone leaves:", options: actions, multiple: true, required: false)
      input ("illuminanceLowHomeRoutine", "enum", title: "When it's night and anyone arrives:", options: actions, multiple: true, required: false)
    }
    
    section("Night Presence Change Delayed Routines") {
      paragraph("Routines to run after delay when presence mode changes at night (below low light threshold).")
      input ("illuminanceLowAwayDelayedRoutine", "enum", title: "Delayed routines to run when it's night, after everyone has left:", options: actions, multiple: true, required: false)
      input ("illuminanceLowHomeDelayedRoutine", "enum", title: "Delayed routines to run when it's night, after anyone arrives:", options: actions, multiple: true, required: false)
    }

    section("Night Presence Change Wait Interval") {
      paragraph("Interval (in minutes) to wait before running delayed night routines.")
      input ("illuminanceLowDelay", "number", title: "Night delay interval (in minutes)", defaultValue: 10, required: false)
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
  state.logHandle = 'DNPRC'
  state.appTitle  = 'DNP Routine Controller'

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
  
  if (appEnable == false) {
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
  if ((illuminanceValue.latestValue("illuminance") - lightThreshold) < lightTolerance) {
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
  logger('info','router',"${msg}")

  // Route only on change
  if (hasChange) {
    if (state.isNight == true && state.isHome == true) {
      unschedule()
      nightHome()
    }
    if (state.isNight == true && state.isHome == false) {
      nightAway()
    }
    if (state.isNight == false && state.isHome == true) {
      unschedule()
      dayHome()
    }
    if (state.isNight == false && state.isHome == false) {
      dayAway()
    }
  }
  
}

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

private nightHome() {
  def msg   = [:]
  def name  = 'Night Home'
  int count = 0
  
  if (illuminanceLowHomeRoutine != null) {
    illuminanceLowHomeRoutine.each {
      location.helloHome?.execute("${it}")
      msg["Ran Routine: ${it}"] = 'OK'
    }
  }
    
  if (illuminanceLowHomeDelayedRoutine != null) {
    illuminanceLowHomeDelayedRoutine.each {
      int runInSecs = (illuminanceLowDelay * 60) + count
      runIn(runInSecs, "delayRunner", [overwrite: false, data: [routine: it, source: "${name} Delayed"]])
      msg["Queued Routine: ${it}"] = 'OK'
      count = count + 1
    }
  }
      
  logger('trace','nightHomeRoute',"${msg}")
  notify(state.appTitle,"(${name}) ${msg}")
}

private nightAway() {
  def msg   = [:]
  def name  = 'Night Away'
  int count = 0

  if (illuminanceLowAwayRoutine != null) {
    illuminanceLowAwayRoutine.each {
      location.helloHome?.execute("${it}")
      msg["Ran Routine: ${it}"] = 'OK'
    }
  }
  
  if (illuminanceLowAwayDelayedRoutine != null) {
    illuminanceLowAwayDelayedRoutine.each {
      int runInSecs = (illuminanceLowDelay * 60) + count
      runIn(runInSecs, "delayRunner", [overwrite: false, data: [routine: it, source: "${name} Delayed"]])
      msg["Queued Routine: ${it}"] = 'OK'
      count = count + 1
    }
  }

  logger('trace','nightAwayRoute',"${msg}")
  notify(state.appTitle,"(${name}) ${msg}")
}

private dayHome() {
  def msg   = [:]
  def name  = 'Day Home'
  int count = 0

  if (illuminanceHighHomeRoutine != null) {
    illuminanceHighHomeRoutine.each {
      location.helloHome?.execute("${it}")
      msg["Ran Routine: ${it}"] = 'OK'
    }
  }

  if (illuminanceHighHomeDelayedRoutine != null) {
    illuminanceHighHomeDelayedRoutine.each {
      int runInSecs = (illuminanceHighDelay * 60) + count
      runIn(runInSecs, "delayRunner", [overwrite: false, data: [routine: it, source: "${name} Delayed"]])
      msg["Queued Routine: ${it}"] = 'OK'
      count = count + 1
    }
  }
    
  logger('trace','dayHomeRoute',"${msg}")
  notify(state.logHandle,"(${name}) ${msg}")
}

private dayAway() {
  def msg   = [:]
  def name  = 'Day Away'
  int count = 0

  if (illuminanceHighAwayRoutine != null) {
    illuminanceHighAwayRoutine.each {
      location.helloHome?.execute("${it}")
      msg["Ran Routine: ${it}"] = 'OK'
    }
  }

  if (illuminanceHighAwayDelayedRoutine != null) {
    illuminanceHighAwayDelayedRoutine.each {
      int runInSecs = (illuminanceHighDelay * 60) + count
      runIn(runInSecs, "delayRunner", [overwrite: false, data: [routine: it, source: "${name} Delayed"]])
      msg["Queued Routine: ${it}"] = 'OK'
      count = count + 1
    }
  }
    
  logger('trace','dayAwayRoute',"${msg}")
  notify(state.appTitle,"(${name}) ${msg}")
}

def delayRunner(data) {  
  if (data.routine != null) {
    location.helloHome?.execute("${data.routine}")
    logger('debug','delayRunner',"Ran Delayed Routine: ${data.routine}")
    notify(state.appTitle,"(${data.source}) Ran Delayed Routine: ${data.routine}")
  }
}

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

private notify(title,msg) {
  if (appNotify) {
    sendNotificationEvent("[${title}]: ${msg}")
  }
}

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