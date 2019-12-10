//////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
//
//  Day/Night/Presence (DNP) Routine Controller for SmartThings
//  Copyright (c)2019-2020 Mark Page (mark@very3.net)
//  Modified: Tue Dec 10 05:06:29 CST 2019
//
//  The Day/Night/Presence (DNP) Routine Controller for SmartThings allows conditional firing of multiple routines 
//  based on changes to presence and daylight (illumination). Secondary multiple routines can be fired after a set 
//  interval.
//
//  This SmartApp requires a illuminanceMeasurement capable device such as the Aeotec TriSensor (Z-Wave Plus S2) or
//  the Monoprice Z-Wave Plus PIR Multi Sensor. Any illuminance sensor that reports outdoor light levels (in lux)
//  should do the trick, including some weather station DTHs.
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
  name: "DNP Routine Controller",
  namespace: "very3-dnp-routine-contoller",
  released: "Tue Dec 10 05:06:29 CST 2019",
  version: "19.12.10.5",
  author: "Mark Page",
  description: "The Day/Night/Presence (DNP) Routine Controller for SmartThings allows conditional firing of multiple routines based on changes to presence and/or daylight (illumination). Secondary multiple routines can be fired after a set interval.",
  singleInstance: true,
  category: "My Apps",
  iconUrl: "https://raw.githubusercontent.com/voodoojello/smartthings/master/very3-256px.png",
  iconX2Url: "https://raw.githubusercontent.com/voodoojello/smartthings/master/very3-512px.png",
  iconX3Url: "https://raw.githubusercontent.com/voodoojello/smartthings/master/very3-512px.png"
)

preferences {
  page(name: "mainPage", title: "DNP Contoller General Settings", nextPage: "daySettings", uninstall: true) {
    section() {
      paragraph("The Day/Night/Presence (DNP) Routine Controller for SmartThings allows conditional firing of multiple routines based on changes to presence and/or daylight (illumination). Secondary multiple routines can be fired after a set interval.", image: "https://github.com/voodoojello/smartthings/blob/master/very3-256px.png?raw=true")
    }
    
    section("Select Presence Users") {
      input("userPresenceList", "capability.presenceSensor", title: "Monitor These Presences", required: true, multiple: true, submitOnChange: true)
    }
    
    section("Select Illuminance Measurement Source") {
      input("illuminanceSource", "capability.illuminanceMeasurement", title: "Illuminance Measurement Source:", required: true)
    }
    
    section("Low Light Threshold") {
      paragraph("Light level to trigger day-to-night and night-to-day changes, represented in lumens per square meter (lux). Dusk and morning are typically about 200-400 lux but may vary wildly depending on sensor position, time of year, and geographical location.")
      input("lightThreshold", "number", title: "Low light change threshold (lux)", defaultValue: 300, required: false)
    }

    section("Light Change Tolerance") {
      paragraph("Dampens the \"Low Light Threshold\" setting to prevent the DNP Controller from bouncing between day and night. Default is ±10 lux.")
      input("lightTolerance", "number", title: "Light change tolerance (lux)", defaultValue: 10, required: false)
    }

    section("Light Changes Trigger Routines") {
      paragraph("By default routines will only be triggered by presence changes (when anyone arrives or everyone leaves). Enable this setting and routines will run with day/night changes in the current presence context.")
      input("enableLightRoutineChanges", "bool", title: "Run routines for day/night changes", defaultValue: false)
    }

    section("Notify on Change") {
      paragraph("Send notifications when the DNP Routine Controller makes changes")
      paragraph("Push and SMS notifications can be somewhat overwhelming depending on the number of routines selected.", title: "Heads Up!", required: true, image: "http://cdn.device-icons.smartthings.com/Lighting/light11-icn@2x.png")
      input("appNotify", "bool", title: "Notify everyone in \"Hello Home\"", defaultValue: true)
      input("devNotify", "bool", title: "Notify everyone via push notification", defaultValue: false)
      input("smsNotify", "bool", title: "Notify only me via SMS", defaultValue: false)
      input("smsNumber", "phone", title: "Phone number for SMS messages", hideWhenEmpty: "smsNotify", required: false)
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
      input ("illuminanceHighHomeRoutine", "enum", title: "When it's day and anyone is home:", options: actions, multiple: true, required: false)
    }
    
    section("Day Presence Change Delayed Routines") {
      paragraph("Routines to run after delay when presence mode changes during the day (above low light threshold).")
      input ("illuminanceHighAwayDelayedRoutine", "enum", title: "Delayed routines to run when it's day, after everyone has left:", options: actions, multiple: true, required: false)
      input ("illuminanceHighHomeDelayedRoutine", "enum", title: "Delayed routines to run when it's day, after anyone is home:", options: actions, multiple: true, required: false)
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
      input ("illuminanceLowHomeRoutine", "enum", title: "When it's night and anyone is home:", options: actions, multiple: true, required: false)
    }
    
    section("Night Presence Change Delayed Routines") {
      paragraph("Routines to run after delay when presence mode changes at night (below low light threshold).")
      input ("illuminanceLowAwayDelayedRoutine", "enum", title: "Delayed routines to run when it's night, after everyone has left:", options: actions, multiple: true, required: false)
      input ("illuminanceLowHomeDelayedRoutine", "enum", title: "Delayed routines to run when it's night, after anyone is home:", options: actions, multiple: true, required: false)
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
  state.logMode   = 0 // [0 = off, 1 = info, 2 = info/trace, 3 = anything]
  state.logHandle = 'DNPRC'
  state.appTitle  = 'DNP Routine Controller'

  state.isHome    = null
  state.wasHome   = null
  state.isNight   = null
  state.wasNight  = null
  
  state.presenceStatus    = userPresenceList.latestValue("presence")
  state.illuminanceStatus = illuminanceSource.latestValue("illuminance")
  
  subscribe(userPresenceList, "presence", presenceHandler)
  subscribe(illuminanceSource, "illuminance" , illuminanceHandler)

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
  def msg = []
  logger('info','router',"Router event fired...")
  
  if (appEnable == false) {
    notify(state.appTitle,"DNP disabled. No action taken.")
    logger('info','router',"DNP disabled. No action taken.")
    return
  }

  int homeCheck  = presenceCheck()
  int lumenCheck = illuminanceCheck()
  def hasChange  = false

  // Determine state changes
  if (state.isHome != state.wasHome) {
    state.wasHome = state.isHome
    hasChange = true
    logger('debug','homeChange',"${state.wasHome} => ${state.isHome}")
  }
  if (state.isNight != state.wasNight) {
    state.wasNight = state.isNight
    if (enableLightRoutineChanges) {
      hasChange = true
    }
    logger('debug','nightChange',"${state.wasNight} => ${state.isNight}, enableLightRoutineChanges: ${enableLightRoutineChanges}")
  }

  msg.push("homeCheck: ${homeCheck}, lumenCheck: ${lumenCheck}, hasChange: ${hasChange}, [state.isHome: ${state.isHome}, state.illuminanceStatus: ${state.illuminanceStatus}, state.isNight: ${state.isNight}, state.presenceStatus: ${state.presenceStatus}]")
  logger('info','router',"${msg}")

  // Route on change
  if (hasChange) {
    if (state.isNight == true && state.isHome == true) {
      notify(state.appTitle,"Detected state change (Night/Home), starting run...")
      unschedule() // Clear pending schedules on return. Awkward.
      nightHome()
    }
    if (state.isNight == true && state.isHome == false) {
      notify(state.appTitle,"Detected state change (Night/Away), starting run...")
      nightAway()
    }
    if (state.isNight == false && state.isHome == true) {
      notify(state.appTitle,"Detected state change (Day/Home), starting run...")
      unschedule() // Clear pending schedules on return. Awkward.
      dayHome()
    }
    if (state.isNight == false && state.isHome == false) {
      notify(state.appTitle,"Detected state change (Day/Away), starting run...")
      dayAway()
    }
  }
}

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

private presenceCheck() {
  int homeCheck = 0
  state.isHome  = false

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
  
  return homeCheck
}

private illuminanceCheck() {
  state.isNight   = false
  int illuminance = illuminanceSource.latestValue("illuminance")
  
  if ((illuminanceSource.latestValue("illuminance") - lightThreshold) < lightTolerance) {
    state.isNight = true
  }
  
  return illuminance
}

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

private nightHome() {
  def msg   = []
  def name  = 'Night Home'
  int count = 0
  
  if (illuminanceLowHomeRoutine != null) {
    illuminanceLowHomeRoutine.each {
      location.helloHome?.execute("${it}")
      msg.push("Ran Routine: ${it}")
    }
  }
    
  if (illuminanceLowHomeDelayedRoutine != null) {
    illuminanceLowHomeDelayedRoutine.each {
      int runInSecs = (illuminanceLowDelay * 60) + count
      runIn(runInSecs, "delayRunner", [overwrite: false, data: [routine: it, source: "${name} Delayed"]])
      msg.push("Queued Routine: ${it} (delayed ${illuminanceLowDelay} minutes)")
      count = count + 1
    }
  }
      
  logger('trace','nightHomeRoute',"${msg}")
  notify(state.appTitle,"(${name}) ${msg}")
}

private nightAway() {
  def msg   = []
  def name  = 'Night Away'
  int count = 0

  if (illuminanceLowAwayRoutine != null) {
    illuminanceLowAwayRoutine.each {
      location.helloHome?.execute("${it}")
      msg.push("Ran Routine: ${it}")
    }
  }
  
  if (illuminanceLowAwayDelayedRoutine != null) {
    illuminanceLowAwayDelayedRoutine.each {
      int runInSecs = (illuminanceLowDelay * 60) + count
      runIn(runInSecs, "delayRunner", [overwrite: false, data: [routine: it, source: "${name} Delayed"]])
      msg.push("Queued Routine: ${it} (delayed ${illuminanceLowDelay} minutes)")
      count = count + 1
    }
  }

  logger('trace','nightAwayRoute',"${msg}")
  notify(state.appTitle,"(${name}) ${msg}")
}

private dayHome() {
  def msg   = []
  def name  = 'Day Home'
  int count = 0

  if (illuminanceHighHomeRoutine != null) {
    illuminanceHighHomeRoutine.each {
      location.helloHome?.execute("${it}")
      msg.push("Ran Routine: ${it}")
    }
  }

  if (illuminanceHighHomeDelayedRoutine != null) {
    illuminanceHighHomeDelayedRoutine.each {
      int runInSecs = (illuminanceHighDelay * 60) + count
      runIn(runInSecs, "delayRunner", [overwrite: false, data: [routine: it, source: "${name} Delayed"]])
      msg.push("Queued Routine: ${it} (delayed ${illuminanceHighDelay} minutes)")
      count = count + 1
    }
  }
    
  logger('trace','dayHomeRoute',"${msg}")
  notify(state.appTitle,"(${name}) ${msg}")
}

private dayAway() {
  def msg   = []
  def name  = 'Day Away'
  int count = 0

  if (illuminanceHighAwayRoutine != null) {
    illuminanceHighAwayRoutine.each {
      location.helloHome?.execute("${it}")
      msg.push("Ran Routine: ${it}")
    }
  }

  if (illuminanceHighAwayDelayedRoutine != null) {
    illuminanceHighAwayDelayedRoutine.each {
      int runInSecs = (illuminanceHighDelay * 60) + count
      runIn(runInSecs, "delayRunner", [overwrite: false, data: [routine: it, source: "${name} Delayed"]])
      msg.push("Queued Routine: ${it} (delayed ${illuminanceHighDelay} minutes)")
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
  def msgStr = "[${title}]: ${msg}"
  
  if (appNotify) {
    sendNotificationEvent(msgStr)
  }
  if (devNotify) {
    sendPush(msgStr)
  }
  if (smsNotify && smsNumber) {
    sendSms(smsNumber,msgStr)
  }
}

private logger(level,loc,msg) {
  def msgStr = "[[${state.logHandle}]] [${loc}]: ${msg}"
  
  if (state.logMode > 0 && "${level}" == 'info') {
    log."${level}" "${msgStr}"
  }
  else if (state.logMode > 1 && "${level}" == 'trace') {
    log."${level}" "${msgStr}"
  }
  else if (state.logMode > 2) {
    log."${level}" "${msgStr}"
  }
}