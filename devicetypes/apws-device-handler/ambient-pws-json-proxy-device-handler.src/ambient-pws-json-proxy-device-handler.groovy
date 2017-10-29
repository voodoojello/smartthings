/**
 *  PWS JSON Proxy Device Handler
 *
 *  This device handler is for the Ambient Weather Station and depends on a JSON proxy to scrape the weather station
 *  "livedata" (livedata.htm) page. This script is available at https://gist.github.com/voodoojello.
 *
 *  Copyright 2017 MARK PAGE
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
    version: "17.10.29.1b",
    namespace: "apws-device-handler",
    author: "Mark Page",
    description: "Virtual device handler for Ambient PWS JSON proxy",
    singleInstance: true,
    category: "SmartThings Internal",
    iconUrl: "https://raw.githubusercontent.com/voodoojello/smartthings-apps/master/very3-256px.png",
    iconX2Url: "https://raw.githubusercontent.com/voodoojello/smartthings-apps/master/very3-512px.png",
    iconX3Url: "https://raw.githubusercontent.com/voodoojello/smartthings-apps/master/very3-512px.png"
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
  def pwsData = fetchJSON("http://pws.very3.net")
  def spacer  = "\t●\t"

  sendEvent(name:"temperature", value:pwsData.pws.outtemp, unit:"F")
  sendEvent(name:"ultravioletIndex", value:pwsData.pws.uvi)
  sendEvent(name:"illuminance", value:pwsData.pws.solarrad, unit:"lux")
  sendEvent(name:"humidity", value:pwsData.pws.outhumi, unit:"%")
  sendEvent(name:"updated", value: pwsData.app.updated_long)

  sendEvent(name:"outtemp", value:pwsData.pws.outtemp, unit:"F")
  sendEvent(name:"apptemp", value:pwsData.pws.apptemp, unit:"F")
  sendEvent(name:"heatindex", value:pwsData.pws.heatindex, unit:"F")
  sendEvent(name:"temp_composite", value:"Heat Index: ${pwsData.pws.heatindex}°F\nFeels Like: ${pwsData.pws.apptemp}°F")

  sendEvent(name:"solarrad", value:pwsData.pws.solarrad)
  sendEvent(name:"uvi", value:pwsData.pws.uvi)
  sendEvent(name:"uv", value:pwsData.pws.uv)
  sendEvent(name:"uv_composite", value:"SR: ${pwsData.pws.solarrad} w/m2${spacer}UV: ${pwsData.pws.uv} w/m2${spacer}UVI: ${pwsData.pws.uvi}")
  
  sendEvent(name:"dewpoint", value:pwsData.pws.dewpoint, unit:"F")
  sendEvent(name:"outhumi", value:pwsData.pws.outhumi, unit:"%")
  sendEvent(name:"humidity_composite", value: "Humidity: ${pwsData.pws.outhumi}%${spacer}Dewpoint: ${pwsData.pws.dewpoint}°F")
  
  sendEvent(name:"rainofdaily", value:pwsData.pws.rainofdaily, unit:"in")
  sendEvent(name:"rainofhourly", value:pwsData.pws.rainofhourly, unit:"in")
  sendEvent(name:"rainofweekly", value:pwsData.pws.rainofweekly, unit:"in")
  sendEvent(name:"rainofmonthly", value:pwsData.pws.rainofmonthly, unit:"in")
  sendEvent(name:"rainofyearly", value:pwsData.pws.rainofyearly, unit:"in")
  sendEvent(name:"rain_composite", value: "Today: ${pwsData.pws.rainofdaily}\"${spacer}Week: ${pwsData.pws.rainofweekly}\"${spacer}Month: ${pwsData.pws.rainofmonthly}\"${spacer}Year: ${pwsData.pws.rainofyearly}\"")

  def cardinalPoints = ["N","NNE","NE","ENE","E","ESE","SE","SSE","S","SSW","SW","WSW","W","WNW","NW","NNW","N"]
  def cardinalIndex  = pwsData.pws.windir%360
      cardinalIndex  = Math.round(cardinalIndex/22.5)
  
  sendEvent(name:"avgwind", value:pwsData.pws.avgwind, unit:"mph")
  sendEvent(name:"windir", value:pwsData.pws.windir, unit:"dir")
  sendEvent(name:"gustspeed", value:pwsData.pws.gustspeed, unit:"mph")
  sendEvent(name:"wind_composite", value: "Speed: ${pwsData.pws.avgwind} mph${spacer}Gust: ${pwsData.pws.gustspeed} mph${spacer}Dir: ${cardinalPoints[cardinalIndex.toInteger()]} (${pwsData.pws.windir}°)")
  
  sendEvent(name:"abspress", value:pwsData.pws.abspress, unit:"in")
  sendEvent(name:"relpress", value:pwsData.pws.relpress, unit:"in")
  sendEvent(name:"pressure_composite", value: "ABS Pressure: ${pwsData.pws.abspress}\"${spacer}REL Pressure: ${pwsData.pws.relpress}\"")
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