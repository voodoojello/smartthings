/**
 *  Ambient PWS JSON Proxy Device Handler
 *
 *  This device handler is for the Ambient Weather Station and depends on a JSON proxy to pull data 
 *  from the Ambient Weather API and/or a METAR source. This script is available at:
 *  https://gist.github.com/voodoojello.
 * 
 *  Copyright (c)2018 Mark Page (mark@very3.net)
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
metadata {
  definition (
    name: "Ambient PWS JSON Proxy Device Handler",
    version: "18.7.22.14c",
    namespace: "apws-device-handler",
    author: "Mark Page",
    description: "Virtual device handler for Ambient PWS JSON proxy",
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
    }

  simulator {
  // TODO: define status and reply messages here
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
      state "default", label:'${currentValue}', icon:"st.Weather.weather14"
    }

    valueTile("humidity_composite", "device.humidity_composite", decoration:"flat", width:6, height:2, wordWrap:false) {
      state "default", label:'${currentValue}', icon:"st.Weather.weather2"
    }

    valueTile("pressure_composite", "device.pressure_composite", decoration:"flat", width:6, height:2, wordWrap:false) {
      state "default", label:'${currentValue}', icon:"st.Weather.weather2"
    }

    valueTile("wind_composite", "device.wind_composite", decoration:"flat", width:6, height:2, wordWrap:false) {
      state "default", label:'${currentValue}', icon:"st.Weather.weather1"
    }

    valueTile("rain_composite", "device.rain_composite", decoration:"flat", width:6, height:2, wordWrap:false) {
      state "default", label:'${currentValue}', icon:"st.Weather.weather5"
    }

    standardTile("updated", "device.updated", decoration:"flat", width:6, height:2, wordWrap:false) {
      state "default", label:'Updated: ${currentValue}', action:"refresh.refresh", icon:"st.Health & Wellness.health7"
    }

    main(["temperature"])
    details(["temperature","uv_composite","humidity_composite","pressure_composite","wind_composite","rain_composite","updated","refresh"])
  }
}

def installed() {
  initialize()
}

def updated() {
  initialize()
}

def refresh() {
  log.debug "PWS Tile Refreshed!"
  initialize()
}

def initialize() {
  state.pws = [:]

  poll()
  runEvery5Minutes(poll)
}

def poll() {
  def pwsData = fetchJSON("https://pws.very3.net")
  def spacer  = "\t○\t"

  sendEvent(name:"temperature", value:pwsData.pws.tempf, unit:"F")
  sendEvent(name:"ultravioletIndex", value:pwsData.pws.uv)
  sendEvent(name:"illuminance", value:pwsData.pws.solarradiation, unit:"lux")
  sendEvent(name:"humidity", value:pwsData.pws.humidity, unit:"%")
  sendEvent(name:"updated", value: pwsData.app.updated_long)

  sendEvent(name:"tempf", value:pwsData.pws.tempf, unit:"F")
  sendEvent(name:"feelsLike", value:pwsData.pws.feelsLike, unit:"F")
  sendEvent(name:"temp_composite", value:"Feels Like: ${pwsData.pws.feelsLike}°F")

  sendEvent(name:"solarradiation", value:pwsData.pws.solarradiation)
  sendEvent(name:"uv", value:pwsData.pws.uv)
  sendEvent(name:"uv_composite", value:"SR: ${pwsData.pws.solarradiation} w/m2${spacer}UVI: ${pwsData.pws.uv}")
  
  sendEvent(name:"dewPoint", value:pwsData.pws.dewPoint, unit:"F")
  sendEvent(name:"humidity", value:pwsData.pws.humidity, unit:"%")
  sendEvent(name:"humidity_composite", value: "Humidity: ${pwsData.pws.humidity}%${spacer}Dewpoint: ${pwsData.pws.dewPoint}°F")
  
  sendEvent(name:"baromabsin", value:pwsData.pws.baromabsin, unit:"in")
  sendEvent(name:"baromrelin", value:pwsData.pws.baromrelin, unit:"in")
  sendEvent(name:"pressure_composite", value: "ABSP: ${pwsData.pws.baromabsin} inMg${spacer}RELP: ${pwsData.pws.baromrelin} inMg")

  sendEvent(name:"hourlyrainin", value:pwsData.pws.hourlyrainin, unit:"in")
  sendEvent(name:"dailyrainin", value:pwsData.pws.dailyrainin, unit:"in")
  sendEvent(name:"weeklyrainin", value:pwsData.pws.weeklyrainin, unit:"in")
  sendEvent(name:"monthlyrainin", value:pwsData.pws.monthlyrainin, unit:"in")
  sendEvent(name:"yearlyrainin", value:pwsData.pws.yearlyrainin, unit:"in")
  sendEvent(name:"totalrainin", value:pwsData.pws.totalrainin, unit:"in")
  sendEvent(name:"rain_composite", value: "D: ${pwsData.pws.dailyrainin}\"${spacer}W: ${pwsData.pws.weeklyrainin}\"${spacer}M: ${pwsData.pws.monthlyrainin}\"${spacer}Y: ${pwsData.pws.totalrainin}\"")

  def cardinalPoints = ["N","NNE","NE","ENE","E","ESE","SE","SSE","S","SSW","SW","WSW","W","WNW","NW","NNW","N"]
  def cardinalIndex  = pwsData.pws.winddir%360
      cardinalIndex  = Math.round(cardinalIndex/22.5)
  
  sendEvent(name:"winddir", value:pwsData.pws.winddir, unit:"dir")
  sendEvent(name:"windspeedmph", value:pwsData.pws.windspeedmph, unit:"mph")
  sendEvent(name:"windgustmph", value:pwsData.pws.windgustmph, unit:"mph")
  sendEvent(name:"maxdailygust", value:pwsData.pws.maxdailygust, unit:"mph")
  sendEvent(name:"wind_composite", value: "S: ${pwsData.pws.windspeedmph} mph${spacer}G: ${pwsData.pws.windgustmph} mph${spacer}D: ${cardinalPoints[cardinalIndex.toInteger()]} (${pwsData.pws.winddir}°)")
  
  sendEvent(name:"battout", value:pwsData.pws.battout)
}

def fetchJSON(pwsURI) {
  def params = [
    uri: pwsURI,
    format: 'json',
    contentType: 'application/json'
  ]

  try {
    httpGet(params) { response ->
      return response.data
    }
  }
  catch (e) {
    log.error "HTTPGET: Failed! ($e)"
    return "error: $e"
  }
}
