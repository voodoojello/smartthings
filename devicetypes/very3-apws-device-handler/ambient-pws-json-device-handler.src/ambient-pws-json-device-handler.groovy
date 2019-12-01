//////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
//
//  Ambient PWS JSON Device Handler for SmartThings
//  Copyright (c)2019-2020 Mark Page (mark@very3.net)
//  Modified: Sat Nov 30 06:42:39 CST 2019
//
//  This SmartThings device handler is for the Ambient Weather Station and depends on a JSON source to pull 
//  data from the Ambient Weather ObserverIP, the Ambient Weather API, and/or a METAR source. This has been 
//  tested with Ambient weather stations WS-1900, WS-2000, and the WS-2600 using the Ambient Weather ObserverIP.
//  A scraper to convert the ObserverIP Live Data to JSON is available at:
//
//      https://gist.github.com/voodoojello/3a53c1c6df78766862e45601acbeca2e
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

import groovy.json.JsonSlurper
include 'asynchttp_v1'

metadata {
  definition (
    name: "Ambient PWS JSON Device Handler",
    version: "19.11.30.6",
    namespace: "very3-apws-device-handler",
    author: "Mark Page",
    description: "Virtual device handler for Ambient PWS JSON sources",
    singleInstance: true,
    category: "SmartThings Internal",
    iconUrl: "https://raw.githubusercontent.com/voodoojello/smartthings/master/very3-256px.png",
    iconX2Url: "https://raw.githubusercontent.com/voodoojello/smartthings/master/very3-512px.png",
    iconX3Url: "https://raw.githubusercontent.com/voodoojello/smartthings/master/very3-512px.png"
  ) {
      capability "Illuminance Measurement"
      capability "Relative Humidity Measurement"
      capability "Temperature Measurement"
      capability "Ultraviolet Index"
      capability "Refresh"
      
      attribute "feelsLikeTemp", "number"
      attribute "dewPoint", "number"
      attribute "windSpeed", "number"
      attribute "windGust", "number"
      attribute "windDirectionCardinal", "string"
      attribute "windDirectionDegrees", "number"
      attribute "relativeBarometricPressure", "number"
      attribute "absoluteBarometricPressure", "number"
      attribute "sunRise", "date"
      attribute "sunSet", "date"
      attribute "cloudCover", "string"
      attribute "visibility", "string"
    }

  simulator {
  // TODO: define status and reply messages here
  }

  preferences {
    section {
      input name: "lanDevIPAddr", type: "text", title: "LAN PWS JSON Source IP Address", description: "IP Address of the PWS JSON source", required: true, displayDuringSetup: true
      input name: "lanDevIPPort", type: "number", title: "LAN PWS JSON Source IP Port", description: "Port of the PWS JSON source",  defaultValue: "80", displayDuringSetup: true
      input name: "runInSecs", type: "number", title: "PWS Polling Frequency", description: "Polling PWS Every XXX Seconds",  defaultValue: "360", displayDuringSetup: true
    }
  }

  tiles (scale:2) {
    multiAttributeTile(name:"temperature", type:"generic", width:6, height:4, wordWrap:false) {
      tileAttribute("device.temperature", key: "PRIMARY_CONTROL") {
        attributeState "temperature", label:'${currentValue}°F', unit:"F", defaultState:true, icon:"st.Weather.weather2", backgroundColors:[
          [value: 31, color: "#153591"],
          [value: 44, color: "#1e9cbb"],
          [value: 59, color: "#90d2a7"],
          [value: 74, color: "#44b621"],
          [value: 84, color: "#f1d801"],
          [value: 95, color: "#d04e00"],
          [value: 96, color: "#bc2323"]
        ]
      }
      tileAttribute("device.temp_composite", key: "SECONDARY_CONTROL") {
        attributeState "temp_composite", label:'${currentValue}', icon:"st.Weather.weather2"
      }
    }

    valueTile("uv_composite", "device.uv_composite", decoration:"flat", width:6, height:2, wordWrap:false, backgroundColor:"#ff0000") {
      state "val", label:'${currentValue}', icon:"st.Weather.weather14"
    }

    valueTile("humidity_composite", "device.humidity_composite", decoration:"flat", width:6, height:2, wordWrap:false) {
      state "val", label:'${currentValue}', icon:"st.Weather.weather2"
    }

    valueTile("pressure_composite", "device.pressure_composite", decoration:"flat", width:6, height:2, wordWrap:false) {
      state "val", label:'${currentValue}', icon:"st.Weather.weather2"
    }

    valueTile("wind_composite", "device.wind_composite", decoration:"flat", width:6, height:2, wordWrap:false) {
      state "val", label:'${currentValue}', icon:"st.Weather.weather1"
    }

    valueTile("rain_composite", "device.rain_composite", decoration:"flat", width:6, height:2, wordWrap:false) {
      state "val", label:'${currentValue}', icon:"st.Weather.weather10"
    }

    valueTile("cloud_composite", "device.cloud_composite", decoration:"flat", width:6, height:2, wordWrap:false) {
      state "val", label:'${currentValue}', icon:"st.Weather.weather11"
    }

    standardTile("updated", "device.updated", decoration:"flat", width:6, height:2, wordWrap:false) {
      state "val", label:'${currentValue}', action:"refresh.refresh", icon:"st.Health & Wellness.health7"
    }

    main(["temperature"])
    details(["temperature","uv_composite","humidity_composite","pressure_composite","wind_composite","rain_composite","cloud_composite","updated","refresh"])
  }
}

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

def installed() {
  logger('info','installed',"Installed with settings: ${settings}")
  initialize()
}

def configure() {
  logger('info','configure',"Configured with settings: ${settings}")
  initialize()
}

def updated() {
  logger('info','updated',"Updated with settings: ${settings}")
  unschedule()
  initialize()
}

def refresh() {
  initialize()
}

def initialize() {
  state.logMode   = 0
  state.logHandle = 'APWS'
  state.pws       = [:]
  logger('info','initialize',"Logging set to ${state.debugMode}")

  poll()
  runIn(runInSecs, poll)
}

def poll() {
  logger('info','poll',"Starting polling cycle (${runInSecs}s intervals)")
  fetchJSON()
}

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

def parse(description) {
  def spacer  = "\t○\t"
  def pwsData = parseLanMessage(description).json

  def cardinalPoints = ["N","NNE","NE","ENE","E","ESE","SE","SSE","S","SSW","SW","WSW","W","WNW","NW","NNW","N"]
  def cardinalIndex  = pwsData.pws.winddir%360
      cardinalIndex  = Math.round(cardinalIndex/22.5)
  
  logger('trace','parse',"pwsData: ${pwsData}")

  sendEvent(name:"temp_composite", value:"Feels Like: ${pwsData.pws.feelsLike}°F")
  sendEvent(name:"uv_composite", value:"SR: ${pwsData.pws.solarradiation} w/m2${spacer}UVI: ${pwsData.pws.uv}")
  sendEvent(name:"cloud_composite", value:"Sky: ${pwsData.metar.sky_condition}${spacer}Visibility: ${pwsData.metar.visibility_statute_mi} mi")
  sendEvent(name:"humidity_composite", value: "Humidity: ${pwsData.pws.humidity}%${spacer}Dewpoint: ${pwsData.pws.dewPoint}°F")
  sendEvent(name:"pressure_composite", value: "ABSP: ${pwsData.pws.baromabsin} inMg${spacer}RELP: ${pwsData.pws.baromrelin} inMg")
  sendEvent(name:"rain_composite", value: "D: ${pwsData.pws.dailyrainin}\"${spacer}W: ${pwsData.pws.weeklyrainin}\"${spacer}M: ${pwsData.pws.monthlyrainin}\"${spacer}Y: ${pwsData.pws.totalrainin}\"")
  sendEvent(name:"wind_composite", value: "S: ${pwsData.pws.windspeedmph} mph${spacer}G: ${pwsData.pws.windgustmph} mph${spacer}D: ${cardinalPoints[cardinalIndex.toInteger()]} (${pwsData.pws.winddir}°)")
  sendEvent(name:"updated", value: "Updated: ${pwsData.app.updated_long} (${runInSecs}s)")

  sendEvent(name:"temperature", value:pwsData.pws.tempf, unit:"F")
  sendEvent(name:"illuminance", value:pwsData.pws.solarradiation, unit:"lux")
  sendEvent(name:"uv", value:pwsData.pws.uv)
  sendEvent(name:"solarradiation", value:pwsData.pws.solarradiation)
  sendEvent(name:"humidity", value:pwsData.pws.humidity, unit:"%")

  sendEvent(name:"relativeHumidityMeasurement", value:pwsData.pws.humidity, unit:"%")
  sendEvent(name:"temperatureMeasurement", value:pwsData.pws.tempf, unit:"F")
  sendEvent(name:"illuminanceMeasurement", value:pwsData.pws.solarradiation, unit:"lux")
  sendEvent(name:"ultravioletIndex", value:pwsData.pws.uv)
  sendEvent(name:"absoluteBarometricPressure", value:pwsData.pws.baromabsin)
  sendEvent(name:"relativeBarometricPressure", value:pwsData.pws.baromrelin)
  sendEvent(name:"windSpeed", value:pwsData.pws.windspeedmph, unit:"mph")
  sendEvent(name:"windGust", value:pwsData.pws.windgustmph, unit:"mph")
  sendEvent(name:"windDirectionCardinal", value:cardinalPoints[cardinalIndex.toInteger()])
  sendEvent(name:"windDirectionDegrees", value:pwsData.pws.winddir)
  sendEvent(name:"dewPoint", value:pwsData.pws.dewPoint, unit:"F")
  sendEvent(name:"feelsLikeTemp", value:pwsData.pws.feelsLike, unit:"F")
  sendEvent(name:"sunRise", value:pwsData.pws.sunrise)
  sendEvent(name:"sunSet", value:pwsData.pws.sunset)
  sendEvent(name:"cloudCover", value:pwsData.metar.sky_condition)
  sendEvent(name:"visibility", value:pwsData.metar.visibility)
}

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

private String ipToHex(ipaddr,ipport) {
  String hexipaddr = ipaddr.tokenize('.').collect {String.format('%02X',it.toInteger())}.join()
  String hexipport = ipport.toString().format('%04X',lanDevIPPort.toInteger())
  
  logger('debug','ipToHex',hexipaddr+':'+hexipport)
  
  return hexipaddr+':'+hexipport
}
  
private fetchJSON() {
  def hubip   = device.hub.getDataValue("localIP")
  def hubport = device.hub.getDataValue("localSrvPortTCP")
  def lanHost = ipToHex(lanDevIPAddr,lanDevIPPort)
  
  device.deviceNetworkId = lanHost
  
  try {
    def hubResponse = new physicalgraph.device.HubAction(
      method: "GET",
      path: "/",
      headers: [
        HOST: "${lanHost}"
      ]
    )
    
    sendHubCommand(hubResponse)   
    logger('debug', 'fetchJSON', "hubResponse: ${hubResponse}")
    
    return hubResponse
  }
  catch (Exception e) {
    logger('error', 'fetchJSON', "Exception: ${e}, hubResponse: ${hubResponse}")
  }
  
  return null
}

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

private logger(level,loc,msg) {
  // type: error, warn, info, debug, trace
  if ("${level}" == 'info') {
    log."${level}" "${state.logHandle} [${loc}]: ${msg}"
  }
  else if (state.logMode > 0) {
    log."${level}" "${state.logHandle} [${loc}]: ${msg}"
  }
}
