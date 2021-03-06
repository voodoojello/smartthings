//////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
//
//  Tasmota-Sonoff Device Handler for SmartThings
//  Copyright (c)2019-2020 Mark Page (mark@very3.net)
//  Modified: Sat Dec 21 05:51:01 CST 2019
//
//  This device handler is for ESP8266 based Sonoff Basic/POW devices running Tasmota 6.6.0 or higher.
//  In theory this *should* work with any single-relay ESP8266 device running Tasmota but YMMV.
//  Devices can act as standard on/off switches or as "toggles" (on/off/on, off/on/off) with preset
//  timers. The toggles are handy for rebooting routers, DOCSIS modems, or even the ST hub =)
//
//  For more  info on Tasmota and the Sonoff Basic see:
//
//      BASICR2 Wi-Fi DIY Smart Switch
//      https://sonoff.tech/product/wifi-diy-smart-switches/basicr2
//      Note: The BASICR2 does *not* support power monitoring.
//
//      POWR2 Smart Switch
//      https://sonoff.tech/product/wifi-diy-smart-switches/powr2
//
//      Tasmota: Alternative firmware for ESP8266 based devices
//      https://github.com/arendst/Tasmota
//
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
    name: "Tasmota/Sonoff Device Handler",
    namespace: "very3-sonoff-tasmota-device-handler",
    author: "Mark Page",
    singleInstance: true,
    description: "Virtual device handler for Sonoff devices (ESP8266) running Tasmota firware",
    category: "SmartThings Internal",
    iconUrl: "https://raw.githubusercontent.com/voodoojello/smartthings/master/very3-256px.png",
    iconX2Url: "https://raw.githubusercontent.com/voodoojello/smartthings/master/very3-512px.png",
    iconX3Url: "https://raw.githubusercontent.com/voodoojello/smartthings/master/very3-512px.png"
  ) {
      capability "Actuator"
      capability "Switch"
      capability "Refresh"
      capability "Configuration"
      capability "Outlet"
      
      command "deviceToggle"
    }

  simulator {
    // TODO (maybe): define status and reply messages here
  }
    
  preferences {
    section {
      input name: "lanDevIPAddr", type: "text", title: "LAN Device IP Address", description: "IP Address of the device", required: true, displayDuringSetup: true
      input name: "lanDevIPPort", type: "number", title: "LAN Device IP Port", description: "Port of the device",  defaultValue: "80", displayDuringSetup: true
    }
    section {
      input name: "tasmotaUser", type: "text", title: "Tasmota Username", description: "Username to manage the device", required: true, displayDuringSetup: true
      input name: "tasmotaPass", type: "password", title: "Tasmota Password", description: "Username to manage the device", required: true, displayDuringSetup: true
    }
    section {
      input name: "switchMode", type: "enum", title: "Default Switch Mode", description: "Standard or Toggle Mode", options: ["Standard","Toggle"], required: true, displayDuringSetup: true
      input name: "toggleTime", type: "number", title: "Toggle Time (secs)", description: "Toggle time in seconds (for toggle mode)", defaultValue: "60" ,displayDuringSetup: true
    }
  }

  tiles (scale: 2) {
    multiAttributeTile(name:"switch", type: "generic", width: 6, height: 4, canChangeIcon: false){
      tileAttribute ("device.switch", key: "PRIMARY_CONTROL") {
        attributeState "on", label:'${name}', action:"switch.off", icon:"st.switches.switch.on", backgroundColor:"#00a0dc", nextState:"turningOff"
        attributeState "off", label:'${name}', action:"switch.on", icon:"st.switches.switch.off", backgroundColor:"#ffffff", nextState:"turningOn"
        attributeState "turningOn", label:'${name}', action:"switch.off", icon:"st.switches.switch.on", backgroundColor:"#00a0dc", nextState:"turningOff"
        attributeState "turningOff", label:'${name}', action:"switch.on", icon:"st.switches.switch.off", backgroundColor:"#ffffff", nextState:"turningOn"
      }
      tileAttribute("device.wifi_info", key: "SECONDARY_CONTROL") {
        attributeState "wifi_info", label:'${currentValue}', defaultState: true
      }
    }
    standardTile("toggle", "device.switch", inactiveLabel: false, decoration: "flat", width: 3, height: 2) {
      state "default", label:'Toggle', action:"deviceToggle", icon:"st.Health & Wellness.health7"
    }
    standardTile("refresh", "device.switch", inactiveLabel: false, decoration: "flat", width: 3, height: 2) {
      state "default", label:"Refresh", action:"refresh.refresh", icon:"st.secondary.refresh"
    }
    valueTile("current", "device.current", decoration: "flat", width: 3, height: 2) {
      state "current", label:'Watts: ${currentValue}', icon:"st.Lighting.light14"
    }
    valueTile("voltage", "device.voltage", decoration: "flat", width: 3, height: 2) {
      state "voltage", label:'Volts: ${currentValue}', icon:"st.Entertainment.entertainment15"
    }
  }

  main(["switch"])
  details(["switch", "toggle", "refresh","current","voltage"])
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
  poll()
}

def initialize() {
  state.logMode     = 3
  state.logHandle   = 'TSDH'
  state.deviceState = ""
  logger('info','initialize',"Logging set to ${state.debugMode}")
  
  // Set time on device
  def unixEpoch = new Date().getTime()/1000
  sendCmnd("Time ${unixEpoch}")
  sendCmnd("Time 0")
  logger('info','initialize',"Setting time to ${unixEpoch}")
  
  sendEvent(name: "checkInterval", value: 60, displayed: false, data: [protocol: "lan", hubHardwareId: device.hub.hardwareID, offlinePingable: "1"])
  
  poll()
  runEvery1Minute(poll)
}

def poll() {
  logger('info','poll',"Starting polling cycle...")
  sendCmnd("Status 0")
}

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

private String ipToHex(ipaddr,ipport) {
  String hexipaddr = ipaddr.tokenize('.').collect {String.format('%02X',it.toInteger())}.join()
  String hexipport = ipport.toString().format('%04X',lanDevIPPort.toInteger())
  return hexipaddr+':'+hexipport
}
  
private sendCmnd(cmnd) {
  def hubip   = device.hub.getDataValue("localIP")
  def hubport = device.hub.getDataValue("localSrvPortTCP")
  def lanHost = ipToHex(lanDevIPAddr,lanDevIPPort)
  
  device.deviceNetworkId = lanHost
    
  cmnd = cmnd.replaceAll(' ','%20')
  cmnd = cmnd.replaceAll(';','%3B')

  try {
    def hubResponse = new physicalgraph.device.HubAction(
      method: "GET",
      path: "/cm?user=${tasmotaUser}&password=${tasmotaPass}&cmnd=${cmnd}",
      headers: [
        HOST: "${lanHost}"
      ]
    )
    sendHubCommand(hubResponse)
    
    logger('debug','sendCmnd',"cmnd: ${cmnd}, hubResponse: ${hubResponse}")

    return hubResponse
  }
  catch (Exception e) {
    logger('error','sendCmnd',"Exception: ${e}, hubResponse: ${hubResponse}")
  }
  
  return null
}
  
def parse(description) {
  def msg = parseLanMessage(description)
  logger('debug','parse',"${msg.body}")

  if (msg.status == 200) {
    if (msg.json != null) {
      if (msg.json.StatusSTS?.Wifi?.RSSI != null && msg.json.StatusNET?.IPAddress != null) {
        sendEvent(name: "wifi_info", value: msg.json.StatusSTS.Wifi.SSId+' (-'+msg.json.StatusSTS.Wifi.RSSI+"dB)\n"+msg.json.StatusNET.IPAddress+"\n")
		logger('debug','wifi_info',"SSId: ${msg.json.StatusSTS.Wifi.SSId}, RSSI: ${msg.json.StatusSTS.Wifi.RSSI}")
      }

      if (msg.json.StatusSNS?.ENERGY?.Current != null) {
        sendEvent(name: "current", value: msg.json.StatusSNS.ENERGY.Current)
		logger('debug','current',"Current: ${msg.json.StatusSNS.ENERGY.Current}")
      }
      else {
        sendEvent(name: "current", value: '0.00')
      }

      if (msg.json.StatusSNS?.ENERGY?.Voltage != null) {
        sendEvent(name: "voltage", value: msg.json.StatusSNS.ENERGY.Voltage)
		logger('debug','voltage',"Voltage: ${msg.json.StatusSNS.ENERGY.Voltage}")
      }
      else {
        sendEvent(name: "voltage", value: '0.00')
      }

      if (msg.json.POWER != null) {
        if (msg.json.POWER == 'ON') {
          state.deviceState = 'on'
          sendEvent(name: "switch", value: 'on')
        }
        if (msg.json.POWER == 'OFF') {
          state.deviceState = 'off'
          sendEvent(name: "switch", value: 'off')
        }
        
		logger('debug','msg.json.POWER',"${msg.json.POWER}, switchMode: ${switchMode}, deviceState: ${state.deviceState}, toggleTime: ${toggleTime}")
      }
      
      if (msg.json.Status?.Power != null) {
        if (msg.json.Status.Power == 1) {
          state.deviceState = 'on'
          sendEvent(name: "switch", value: 'on')
        }
        if (msg.json.Status.Power == 0) {
          state.deviceState = 'off'
          sendEvent(name: "switch", value: 'off')
        }
        
		logger('debug','msg.json.Status?.Power',"${msg.json.Status.Power}, switchMode: ${switchMode}, deviceState: ${state.deviceState}, toggleTime: ${toggleTime}")
      }
      
    }
  }
  else {
    logger('error','parse',"${msg.status}, ${msg.body}")
  }
}

def deviceToggle() {
  def blinkTime = toggleTime*10

  sendCmnd("BlinkCount 1")
  sendCmnd("BlinkTime ${blinkTime}")
  sendCmnd("Power 3")

  if (state.deviceState == 'on') {
    sendEvent(name: "switch", value: 'off')
  }
  if (state.deviceState == 'off') {
    sendEvent(name: "switch", value: 'on')
  }
  
  logger('debug','deviceToggle',"switchMode: ${switchMode}, deviceState: ${state.deviceState}, toggleTime: ${toggleTime}")
}

def on() {
  if ("${switchMode}" == "Toggle") {
    deviceToggle()
  }
  else {
    sendCmnd("Power ON")
    sendCmnd("Power")
  }
  
  logger('debug','deviceOn',"switchMode: ${switchMode}, deviceState: ${state.deviceState}, toggleTime: ${toggleTime}")
}

def off() {
  if ("${switchMode}" == "Toggle") {
    deviceToggle()
  }
  else {
    sendCmnd("Power OFF")
    sendCmnd("Power")
  }
  
  logger('debug','deviceOff',"switchMode: ${switchMode}, deviceState: ${state.deviceState}, toggleTime: ${toggleTime}")
}

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

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