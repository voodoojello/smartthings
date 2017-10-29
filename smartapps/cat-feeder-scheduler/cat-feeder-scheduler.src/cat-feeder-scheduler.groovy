/**
 *
 * ============================================ 
 *  Cat Feeder Scheduler
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
  name: "Cat Feeder Scheduler",
  namespace: "cat-feeder-scheduler",
  author: "Mark Page",
  description: "Daily run times and intervals for automatic cat feeder. Allows for two feedings per day.",
  singleInstance: true,
  category: "SmartThings Internal",
  iconUrl: "https://raw.githubusercontent.com/voodoojello/smartthings-apps/master/very3-256px.png",
  iconX2Url: "https://raw.githubusercontent.com/voodoojello/smartthings-apps/master/very3-512px.png",
  iconX3Url: "https://raw.githubusercontent.com/voodoojello/smartthings-apps/master/very3-512px.png"
)

preferences {
  section {
    paragraph "Daily run times and intervals for automatic cat feeder. Allows for two feedings per day."
  }
  section {
    input "catfeederDevice", "capability.switch", title: "Select device to control cat feeder:", required: true
  }
  section {
    input "morningFeeding","time",title:"Morning Feeding:", required: true
  }
  section {
    input "eveningFeeding","time",title:"Evening Feeding:", required: true
   }
  section {
    input "runTime","number",title:"Motor run time in minutes:", required: true
   }
  section {
    input "minOffset","number",title:"Cron offset minutes (0-60):", required: true
    input "secOffset","number",title:"Cron offset seconds (0-60):", required: true
   }
  section {
    input "msgText","text",title:"Notification message:", required: true
   }
}

def installed() {
  initialize()
}

def updated() {
  unschedule()
  unsubscribe()
  initialize()
}

def initialize() {
  def tz = location.timeZone

  def morningStartTime  = timeToday(morningFeeding,tz)
  def morningStartHour  = morningStartTime.format("H",tz)
  def morningStartMin   = morningStartTime.format("m",tz).toInteger() + minOffset.toInteger()
  def morningStopMin    = morningStartMin.toInteger() + runTime.toInteger() + minOffset.toInteger()
  def morningStartCron  = "${secOffset} ${morningStartMin} ${morningStartHour} ? * *"
  def morningStopCron   = "${secOffset} ${morningStopMin} ${morningStartHour} ? * *"

  schedule("${morningStartCron}", firstFeedingOn)
  schedule("${morningStopCron}", firstFeedingOff)
  log.debug "CRON INIT (First Feeding): [${morningStartCron}] to [${morningStopCron}]"

  def eveningStartTime  = timeToday(eveningFeeding,tz)
  def eveningStartHour  = eveningStartTime.format("H",tz)
  def eveningStartMin   = eveningStartTime.format("m",tz).toInteger() + minOffset.toInteger()
  def eveningStopMin    = eveningStartMin.toInteger() + runTime.toInteger() + minOffset.toInteger()
  def eveningStartCron  = "${secOffset} ${eveningStartMin} ${eveningStartHour} ? * *"
  def eveningStopCron   = "${secOffset} ${eveningStopMin} ${eveningStartHour} ? * *"

  schedule("${eveningStartCron}", secondFeedingOn)
  schedule("${eveningStopCron}", secondFeedingOff)
  log.debug "CRON INIT (Second Feeding): [${eveningStartCron}] to [${eveningStopCron}]"
}

def firstFeedingOn() {
  catfeederDevice.on()
  sendPush("${msgText}")
}
def firstFeedingOff() {
  catfeederDevice.off()
}

def secondFeedingOn() {
  catfeederDevice.on()
  sendPush("${msgText}")
}
def secondFeedingOff() {
  catfeederDevice.off()
}
