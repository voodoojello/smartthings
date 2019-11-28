//////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
//
//  Ambient HVAC Control for SmartThings
//  Copyright (c)2019-2020 Mark Page (mark@very3.net)
//  Modified: Tue Nov 26 9:11:21 CST 2019
//
//  Control HVAC based via published capabilities of the very3 Ambient PWS Device Handler. For more
//  information see: https://github.com/voodoojello/smartthings/tree/master/devicetypes/apws-device-handler
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
  name: "HVAC Control",
  version: "19.11.25.16",
  namespace: "hvac-control",
  author: "Mark Page",
  description: "Control HVAC based via published capabilities of the very3 Ambient PWS Device Handler",
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
      paragraph "Control HVAC based on presence and various climate levels from the very3 Ambient PWS JSON server."
    }

    section ("Select Thermostats") {
      input ("thermostats", "capability.thermostat", title: "Select thermostats:", multiple: true, required: true)
    }
    
    section ("Select Weather Source") {
      input ("temperatureValue", "capability.temperatureMeasurement", title: "Outside Temperature Source:", required: true)
      input ("humidityValue", "capability.relativeHumidityMeasurement", title: "Outside Humidity Source:", required: true)
    }

    section ("Day/Night Temperature Presets") {
      input ("dayCool", "decimal", title: "Day cooling temperature:", required: true)
      input ("nightCool", "decimal", title: "Night cooling temperature:", required: true)
      input ("dayHeat", "decimal", title: "Day heating temperature:", required: true)
      input ("nightHeat", "decimal", title: "Night heating temperature:", required: true)
    }

    section ("Heating/Cooling Changeover Temperatures") {
      input ("modeThresCool", "decimal", title: "Cooling temperature threshold:", required: true)
      input ("modeThresHeat", "decimal", title: "Heating temperature threshold:", required: true)
    }

    section ("Default Away Mode Temperatures") {
	  input ("awayCoolTemp", "number", title: "Default away cooling temperature:", multiple: false, required: true)
	  input ("awayHeatTemp", "number", title: "Default away heating temperature:", multiple: false, required: true)
    }

    section ("Night Start/Stop") {
      input ("nightStart", "time", title: "Night cycle starts at hour:", required: true)
      input ("nightStop", "time", title: "Night cycle stops at hour:", required: true)
    }

    section ("Enable / Disable HVAC Control") {
      input("hvaccEnable", "enum", title: "Enable / Disable HVACC Control", options: ["Enable","Disable"], defaultValue:"Enable")
    }
    
    section ("Humidity Scaling") {
      paragraph "Inversely scale thermostat set values by humidity changes for comfort. Higher values make it cooler or warmer. Sane ranges for humidity scaling are .001 to .008."
      input ("humidityAdjust", "decimal", title: "Humidity Scaling:", required: true, defaultValue:0.001)
    }
    
    section ("Change Threshold") {
      paragraph "Minimum set temperature change required to trigger thermostat update. Sane ranges for humidity scaling are 0.0 (always) to 1.0."
      input ("changeThreshold", "decimal", title: "Change Threshold:", required: true, defaultValue:0.5)
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
  state.logMode   = 1
  state.shmStatus = 'off'
  state.logHandle = 'HVACC'
  
  subscribe(location, "alarmSystemStatus" , shmHandler)
  subscribe(temperatureValue, "temperatureMeasurement" , pwsHandler)
  subscribe(humidityValue, "relativeHumidityMeasurement" , pwsHandler)
  
  poll()
}

def shmHandler(evt) {
  logger('info','shmHandler',"Smart Home Monitor ${evt.name} changed to ${evt.value}")
  state.shmStatus = evt.value
  poll()
}

def pwsHandler(evt) {
  logger('info','pwsHandler',"PWS ${evt.name} changed to ${evt.value}")
  poll()
}

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

def poll() {
  logger('info','poll',"Polling...")
  
  def adjTemp     = 72
  def hvacMode    = 'off'
  def currMode    = location.mode
  def isNight     = timeOfDayIsBetween(nightStart, nightStop, new Date(), location.timeZone)
  def osTemp      = (temperatureValue.latestValue("temperatureMeasurement") as BigDecimal)
  def osHumi      = (humidityValue.latestValue("relativeHumidityMeasurement") as BigDecimal)
  def feelsLike   = (temperatureValue.latestValue("feelsLikeTemp") as BigDecimal)
  def windSpeed   = (temperatureValue.latestValue("windSpeed") as BigDecimal)
  def dewPoint    = (temperatureValue.latestValue("dewPoint") as BigDecimal)
  def absPressure = (temperatureValue.latestValue("absoluteBarometricPressure") as BigDecimal)
  def relPressure = (temperatureValue.latestValue("relativeBarometricPressure") as BigDecimal)

  if (osTemp >= modeThresCool) {
    hvacMode = 'cool'
  }

  if (osTemp <= modeThresHeat) {
    hvacMode = 'heat'
  }

  logger('debug','poll',"osTemp: ${osTemp}, osHumi: ${osHumi}, feelsLike: ${feelsLike}, windSpeed: ${windSpeed}, dewPoint: ${dewPoint}, absPressure: ${absPressure}, relPressure: ${relPressure}, modeThresHeat: ${modeThresHeat}, modeThresCool: ${modeThresCool}, hvacMode: ${hvacMode}")
  
  if (hvaccEnable == 'Enable') {
  	if (hvacMode == "heat") {
      adjTemp = adjustTemp(dayHeat,osTemp,feelsLike,osHumi)
      
      if (isNight) {
        adjTemp = adjustTemp(nightHeat,osTemp,feelsLike,osHumi)
      }
      
      if (currMode.toLowerCase() == "away" || state.shmStatus.toLowerCase() == 'away') {
        adjTemp = awayHeatTemp
      }
    
      if (hasChange(hvacMode,adjTemp)) {
        thermostats.heat()
        thermostats.fanAuto()
        thermostats.setHeatingSetpoint(adjTemp)
        
        sendNotificationEvent("${state.logHandle}: Set HVAC mode to ${hvacMode}, set temperature to ${adjTemp} (osTemp: ${osTemp}, feelsLike: ${feelsLike}, osHumi ${osHumi})")
        logger('info','poll',"Set HVAC mode to ${hvacMode}, set temperature to ${adjTemp}")
      }
    }
  
    if (hvacMode == "cool") {
      adjTemp = adjustTemp(dayCool,osTemp,feelsLike,osHumi)

      if (isNight) {
        adjTemp = adjustTemp(nightCool,osTemp,feelsLike,osHumi)
      }

      if (currMode.toLowerCase() == "away") {
        adjTemp = awayCoolTemp
      }
    
      if (hasChange(hvacMode,adjTemp)) {
        thermostats.cool()
        thermostats.fanAuto()
        thermostats.setCoolingSetpoint(adjTemp)
        
        sendNotificationEvent("${state.logHandle}: Set HVAC mode to ${hvacMode}, set temperature to ${adjTemp} (osTemp: ${osTemp}, feelsLike: ${feelsLike}, osHumi ${osHumi})")
        logger('info','poll',"Set HVAC mode to ${hvacMode}, set temperature to ${adjTemp}")
      }
    }

    if (hvacMode == "off") {
      if (hasChange(hvacMode,adjTemp)) {
        thermostats.off()
        
        sendNotificationEvent("${state.logHandle}: Set HVAC mode to ${hvacMode}, set temperature to ${adjTemp} (osTemp: ${osTemp}, feelsLike: ${feelsLike}, osHumi ${osHumi})")
        logger('info','poll',"Set HVAC mode to ${hvacMode}, set temperature to ${adjTemp}")
      }
    }
  }
  else {
    if (hasChange(hvacMode,adjTemp)) {
      sendNotificationEvent("${state.logHandle}: Override is ${hvaccEnable}, no action taken.")
      logger('info','poll',"Override is ${hvaccEnable}, no action taken.")
    }
  }
  
  logger('debug','poll',"hvacMode: ${hvacMode}, adjTemp: ${adjTemp}, currMode: ${currMode}, isNight: ${isNight}, hvaccEnable: ${hvaccEnable}")
  logger('debug','poll',getThermStates())
}

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

private hasChange(hvacMode,adjTemp) {
  def stateReturn     = false
  def currentSetTemp  = 0
  def currentHvacMode = "none"
  def currentState    = getThermStates()
  def currentAdjTemp  = round(adjTemp,2)

  currentState.each {
    def thisReturn  = false
    def thisChange  = "none"
    
    currentSetTemp  = round(it.value.temperature,2)
    currentHvacMode = it.value.thermostatMode
    
  	if ((currentAdjTemp - currentSetTemp) > changeThreshold) {
      thisChange  = (currentAdjTemp - currentSetTemp)
      thisReturn  = true
      stateReturn = true
    }
    
  	if ((currentSetTemp - currentAdjTemp) > changeThreshold) {
      thisChange  = (currentSetTemp - currentAdjTemp)
      thisReturn  = true
      stateReturn = true
    }
    
  	if (hvacMode != currentHvacMode) {
      thisChange  = 'hvacMode'
      thisReturn  = true
      stateReturn = true
    }
    
    logger('debug','hasChange',"thermostatName: ${it.value.thermostatName}, hvacMode: ${hvacMode} (${currentHvacMode}), adjTemp: ${currentAdjTemp} (${currentSetTemp}), thisChange: ${thisChange}, changeThreshold ${changeThreshold}, thisReturn: ${thisReturn}, stateReturn: ${stateReturn}")
  }
    
  return stateReturn
}

private getThermStates() {
  def funcReturn = [:]

  thermostats.each {
    def key   = "${it.label}"
  	def inner = [:]
    
    inner.thermostatName           = it.label
    inner.thermostatOperatingState = it.currentValue("thermostatOperatingState")
    inner.temperature              = it.currentValue("temperature")
    inner.thermostatMode           = it.currentValue("thermostatMode")
    inner.thermostatFanMode        = it.currentValue("thermostatFanMode")
    inner.coolingSetpoint          = it.currentValue("coolingSetpoint")
    inner.heatingSetpoint          = it.currentValue("heatingSetpoint")
    
    logger('debug','getThermStates',inner)
    
    funcReturn.put((key),inner)
  }
  
  return funcReturn
}

private adjustTemp(setTemp,osTemp,feelsLike,humidity) {
  def ret
  def difTemp = 0
  
  if (feelsLike >= osTemp) {
    difTemp = osTemp - (osTemp - (humidity * humidityAdjust));
    ret = (setTemp - difTemp)
  }
  
  if (feelsLike <= osTemp) {
    difTemp = osTemp - (osTemp + (humidity * humidityAdjust));
    ret = (setTemp - difTemp)
  }
  
  logger('info','adjustTemp',"setTemp: ${setTemp}, difTemp: ${difTemp}, return: ${ret}, osTemp: ${osTemp}, feelsLike: ${feelsLike}, humidity: ${humidity}")
  return round(ret,1)
}

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