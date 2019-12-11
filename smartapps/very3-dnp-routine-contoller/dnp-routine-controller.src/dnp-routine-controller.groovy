//////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
//
//  Day/Night/Presence (DNP) Routine Controller for SmartThings
//  Copyright (c)2019-2020 Mark Page (mark@very3.net)
//  Modified: Wed Dec 11 05:42:47 CST 2019
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
  released: "Wed Dec 11 05:42:47 CST 2019",
  version: "19.12.11.5",
  author: "Mark Page",
  description: "The Day/Night/Presence (DNP) Routine Controller for SmartThings allows conditional firing of multiple routines based on changes to presence and/or daylight (illumination). Secondary multiple routines can be fired after a set interval.",
  singleInstance: true,
  category: "My Apps",
  iconUrl: "https://raw.githubusercontent.com/voodoojello/smartthings/master/very3-256px.png",
  iconX2Url: "https://raw.githubusercontent.com/voodoojello/smartthings/master/very3-512px.png",
  iconX3Url: "https://raw.githubusercontent.com/voodoojello/smartthings/master/very3-512px.png"
)

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

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
      paragraph("Push and SMS messages can be somewhat verbose depending on the number of routines selected, espcially if your routines are sending notifications as well.", title: "Heads Up!", required: true, image: "http://cdn.device-icons.smartthings.com/Lighting/light11-icn@2x.png")
      input("appNotify", "bool", title: "Notify everyone in \"Hello Home\"", defaultValue: true)
      input("devNotify", "bool", title: "Notify everyone via push", defaultValue: false)
      input("smsNotify", "bool", title: "Notify only me via SMS", defaultValue: false)
      input("smsNumber", "phone", title: "Phone number for SMS messages", hideWhenEmpty: "smsNotify", required: false)
    }
    
    section("Enable/Disable (bypass) DNP Routine Controller") {
      paragraph("Enable/Disable (bypass) the DNP Controller to prevent presence changes. Handy if you're leaving non-presence guests (like babysitters) at home alone.")
      input("appEnable", "bool", title: "Enable Presence Monitor", defaultValue: true)
    }
    
    section(hideable: true, hidden: true, "Developer Options"){
      paragraph("Logging level shown in SmartThings IDE (0 = off, 1 = info, 2 = info/trace, 3 = anything)")
      input("ideLogLevel", "number", title: "Logging Level")
    }
  }
  
  page(name: "daySettings", title: "Day Settings", nextPage: "nightSettings")
  page(name: "nightSettings", title: "Night Settings", install: true)
}

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

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
      input ("illuminanceHighAwayDelayedRoutine", "enum", title: "Delayed routines to run when it's day and everyone leaves:", options: actions, multiple: true, required: false)
      input ("illuminanceHighHomeDelayedRoutine", "enum", title: "Delayed routines to run when it's day and anyone is home:", options: actions, multiple: true, required: false)
    }
    
    section("Day Presence Change Wait Interval") {
      paragraph("Interval (in minutes) to wait before running delayed day routines.")
      input ("illuminanceHighDelay", "number", title: "Day delay interval (in minutes)", defaultValue: 10, required: false)
    }
  }
}

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

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
      input ("illuminanceLowAwayDelayedRoutine", "enum", title: "Delayed routines to run when it's night and everyone leaves:", options: actions, multiple: true, required: false)
      input ("illuminanceLowHomeDelayedRoutine", "enum", title: "Delayed routines to run when it's night and anyone is home:", options: actions, multiple: true, required: false)
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
  state.logMode   = ideLogLevel
  state.logHandle = 'DNPRC'
  state.appTitle  = 'DNP Routine Controller'

  state.trigger   = 'initialize'
  state.isHome    = presenceCheck().isHome
  state.isNight   = illuminanceCheck().isNight
  state.wasHome   = null
  state.wasNight  = null
  
  state.presenceStatus    = userPresenceList.latestValue("presence")
  state.illuminanceStatus = illuminanceSource.latestValue("illuminance")
  
  subscribe(userPresenceList, "presence", presenceHandler)
  subscribe(illuminanceSource, "illuminance" , illuminanceHandler)

  router()
}

def presenceHandler(evt) {
  logger('info','presenceHandler',"Presence ${evt.name} changed to ${evt.value}")
  state.presenceStatus = evt.value
  router()
}

def illuminanceHandler(evt) {
  logger('info','illuminanceHandler',"Illuminance ${evt.name} changed to ${evt.value}")
  state.illuminanceStatus = evt.value
  router()
}

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

def router() {
  logger('info','router',"Router event fired...")
  
  if (appEnable == false) {
    notify(state.appTitle,"DNP disabled. No action taken.")
    logger('info','router',"DNP disabled. No action taken.")
    return
  }

  def msg = []
  
  def presence    = presenceCheck()
  def illuminance = illuminanceCheck()
  
  int homeCheck   = presence.homeCheck
  int lumenCheck  = illuminance.lumenCheck
  def hasChange   = false

  state.isHome    = presence.isHome
  state.isNight   = illuminance.isNight

  if (state.isHome != state.wasHome) {
    state.wasHome = state.isHome
    state.trigger = 'Presence'
    hasChange = true
    logger('debug','presenceChange',"${state.wasHome} => ${state.isHome}")
  }
  
  if (state.isNight != state.wasNight) {
    state.wasNight = state.isNight
    if (enableLightRoutineChanges) {
      state.trigger  = 'Illuminance'
      hasChange = true
    }
    logger('debug','illuminanceChange',"${state.wasNight} => ${state.isNight}, enableLightRoutineChanges: ${enableLightRoutineChanges}")
  }

  msg.push("presence: ${presence}, illuminance: ${illuminance}, homeCheck: ${homeCheck}, lumenCheck: ${lumenCheck}, hasChange: ${hasChange}, [state.isHome: ${state.isHome}, state.illuminanceStatus: ${state.illuminanceStatus}, state.isNight: ${state.isNight}, state.presenceStatus: ${state.presenceStatus}, state.logMode: ${state.logMode}]")
  logger('info','router',"${msg}")

  if (hasChange) {
    if (state.isNight == true && state.isHome == true) {
      def changedTo = 'Night/Home'
      unschedule(delayedScheduleHandler)
      routineHandler(changedTo,illuminanceLowHomeRoutine,illuminanceLowHomeDelayedRoutine,illuminanceLowDelay)
    }
    
    if (state.isNight == true && state.isHome == false) {
      def changedTo = 'Night/Away'
      routineHandler(changedTo,illuminanceLowAwayRoutine,illuminanceLowAwayDelayedRoutine,illuminanceLowDelay)
    }
    
    if (state.isNight == false && state.isHome == true) {
      def changedTo = 'Day/Home'
      unschedule(delayedScheduleHandler)
      routineHandler(changedTo,illuminanceHighHomeRoutine,illuminanceHighHomeDelayedRoutine,illuminanceHighDelay)
    }
    
    if (state.isNight == false && state.isHome == false) {
      def changedTo = 'Day/Away'
      routineHandler(changedTo,illuminanceHighAwayRoutine,illuminanceHighAwayDelayedRoutine,illuminanceHighDelay)
    }
  }
}

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

private routineHandler(changedTo,routines,delayedRoutines,delay) {
  def msg   = []

  notify(state.appTitle,"State changed to: ${changedTo}\nChanged by: ${state.trigger}\nLaunching routines...")
  
  if (routines != null) {
    routines.each {
      location.helloHome?.execute("${it}")
      msg.push("Ran Routine: ${it}")
    }
  }

  if (delayedRoutines != null) {
    int runInSecs = (delay * 60)
    runIn(runInSecs, "delayedScheduleHandler", [data: [routines: delayedRoutines, source: "${changedTo} Delayed"]])
    msg.push("Queued Routine(s): ${delayedRoutines} (delayed ${delay} minutes)")
  }

  logger('debug','routineHandler',"${msg}")
  notify(state.appTitle,msg.join("\n"))
}

def delayedScheduleHandler(data) {
  def msg = []
  
  if (data.routines != null) {
    data.routines.each {
      location.helloHome?.execute("${it}")
      logger('debug','delayedScheduleHandler',"Ran Delayed Routine: ${it}")
      msg.push("Ran Delayed Routine: ${it}")
    }
    
   notify(state.appTitle,msg.join("\n"))
  }
}

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

private presenceCheck() {
  def reply = [:]

  reply['isHome']    = false
  reply['homeCheck'] = 0

  state.presenceStatus.each {
    if (it == 'present') {
      reply['homeCheck'] = reply['homeCheck'] + 1
    }
  }
  userPresenceList.latestValue("presence").each {
    if (it == 'present') {
      reply['homeCheck'] = reply['homeCheck'] + 1
    }
  }
  userPresenceList.currentValue("presence").each {
    if (it == 'present') {
      reply['homeCheck'] = reply['homeCheck'] + 1
    }
  }
  
  if (reply['homeCheck'] > 0) {
    reply['isHome'] = true
  }
  
  return reply
}

private illuminanceCheck() {
  def reply = [:]
  
  reply['lumenCheck'] = illuminanceSource.latestValue("illuminance")
  reply['isNight']    = false
  
  if ((illuminanceSource.latestValue("illuminance") - lightThreshold) < lightTolerance) {
    reply['isNight'] = true
  }
  
  return reply
}

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

private notify(title,msg) {
  def msgStr = "[${title}]\n${msg}"
  
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

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
