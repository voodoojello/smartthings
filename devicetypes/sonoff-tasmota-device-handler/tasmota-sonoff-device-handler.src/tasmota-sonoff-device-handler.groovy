/*
 *  Tasmota-Sonoff Device Handler
 *  Copyright (c)2019-2020 Mark Page (mark@very3.net)
 *  Modified: Mon Nov 18 19:06:17 CST 2019
 *
 *  This device handler is for ESP8266 based Sonoff devices running Tasmota 6.6.0 or higher.
 *  In theory this *should* work with any ESP8266 device running Tasmota, YMMV. Devices may act
 *  as standard on/off switches or as on/off/on, off/on/off timed toggles. For more info on
 *  Tasmota and the Sonoff Basic see:
 *
 *      https://sonoff.tech/product/wifi-diy-smart-switches/basicr2
 *      https://github.com/arendst/Tasmota
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
 
import groovy.json.JsonSlurper
include 'asynchttp_v1'

metadata {
  definition (
    name: "Tasmota-Sonoff Device Handler",
    namespace: "sonoff-tasmota-device-handler",
    author: "Mark Page",
    singleInstance: true,
    description: "Virtual device handler for Sonoff devices running Tasmota firware",
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
      command "deviceOn"
      command "deviceOff"
    }

  simulator {
    // TODO: define status and reply messages here
  }
    
  preferences {
    input name: "lanDevIPAddr", type: "text", title: "LAN Device IP Address", description: "IP Address of the device", required: true, displayDuringSetup: true
    input name: "lanDevIPPort", type: "number", title: "LAN Device IP Port", description: "Port of the device",  defaultValue: "80", displayDuringSetup: true
    input name: "toggleTime", type: "number", title: "Toggle Time (secs)", description: "Toggle time in seconds (for toggle switch mode)", defaultValue: 60 ,displayDuringSetup: true
    input name: "tasmotaUser", type: "text", title: "Tasmota Username", description: "Username to manage the device", required: true, displayDuringSetup: true
    input name: "tasmotaPass", type: "password", title: "Tasmota Password", description: "Username to manage the device", required: true, displayDuringSetup: true
  }

  tiles (scale: 2) {
    multiAttributeTile(name:"switch", type: "generic", width: 6, height: 4, canChangeIcon: true){
      tileAttribute ("device.switch", key: "PRIMARY_CONTROL") {
        attributeState "on", label:'${name}', action:"deviceOff", backgroundColor:"#00a0dc", icon: "st.switches.switch.on", nextState:"turningOff"
        attributeState "off", label:'${name}', action:"deviceOn", backgroundColor:"#ffffff", icon: "st.switches.switch.off", nextState:"turningOn"
        attributeState "turningOn", label:'Turning On', action:"deviceOff", backgroundColor:"#00a0dc", icon: "st.switches.switch.off", nextState:"turningOn"
        attributeState "turningOff", label:'Turning Off', action:"deviceOn", backgroundColor:"#ffffff", icon: "st.switches.switch.on", nextState:"turningOff"
      }
    }
    standardTile("toggle", "device.switch", inactiveLabel: false, decoration: "flat", width: 6, height: 2) {
      state "default", label:'Timed Toggle', action:"deviceToggle", icon:"st.Health & Wellness.health7"
    }
    standardTile("refresh", "device.switch", inactiveLabel: false, decoration: "flat", width: 6, height: 2) {
      state "default", label:"Refresh", action:"refresh.refresh", icon:"st.secondary.refresh"
    }
 }

  main(["switch"])
  details(["switch", "toggle", "refresh", "deviceURL"])
}

def installed() {
  initialize()
}

def configure() {
  initialize()
}

def refresh() {
  initialize()
}
  
def updated() {
  sendEvent(name: "checkInterval", value: 1, displayed: false, data: [protocol: "lan", hubHardwareId: device.hub.hardwareID])
  initialize()
}

def initialize() {
  callDevice("Status")
  state.deviceState = ""
}

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

private getCallBackAddress() {
  return device.hub.getDataValue("localIP")+":"+device.hub.getDataValue("localSrvPortTCP")
}

private String ipToHex(ipaddr,ipport) {
  String hexipaddr = ipaddr.tokenize('.').collect {String.format('%02X',it.toInteger())}.join()
  String hexipport = ipport.toString().format('%04X',lanDevIPPort.toInteger())
  return hexipaddr+':'+hexipport
}
  
private callDevice(cmnd) {
  def hubip   = device.hub.getDataValue("localIP")
  def hubport = device.hub.getDataValue("localSrvPortTCP")
  def lanHost = ipToHex(lanDevIPAddr,lanDevIPPort)
    
  device.deviceNetworkId = lanHost

  try {
    def hubResponse = new physicalgraph.device.HubAction(
      method: "GET",
      path: "/cm?user=${tasmotaUser}&password=${tasmotaPass}&cmnd=${cmnd}",
      headers: [
        HOST: "${lanHost}"
      ]
    )
    sendHubCommand(hubResponse)
    log.debug "TDH [callDevice]: cmnd: ${cmnd}, hubResponse: ${hubResponse}"
    return hubResponse
  }
  catch (Exception e) {
    log.debug "TDH [callDevice]: Exception [${e} on ${hubResponse}]"
  }
  
  return null
}
  
def parse(description) {
  def msg = parseLanMessage(description)
  log.debug "TDH [parse]: msg.json: ${msg.json}"

  if (msg.status == 200) {
    if (msg.json != null) {
      if (msg.json.Status?.Power != null) {
        if (msg.json.Status.Power == 1) {
          state.deviceState = 'on'
          sendEvent(name: "switch", value: 'on')
        }
        if (msg.json.Status.Power == 0) {
          state.deviceState = 'off'
          sendEvent(name: "switch", value: 'off')
        }
      }
      if (msg.json.Status?.POWER != null) {
        if (msg.json.Status.POWER == 'ON') {
          state.deviceState = 'on'
          sendEvent(name: "switch", value: 'on')
        }
        if (msg.json.Status.POWER == 'OFF') {
          state.deviceState = 'off'
          sendEvent(name: "switch", value: 'off')
        }
      }
    }
  }
  else {
    log.debug "TDH [Parse Error]: ${msg.status}, ${msg.body}"
  }
}

def deviceToggle() {
  def delay = toggleTime*10
  def uri = "Backlog%20Power%20ON%3BDelay%20${delay}%3BPower%20OFF"
  
  if (state.deviceState == 'on') {
    uri = "Backlog%20Power%20OFF%3BDelay%20${delay}%3BPower%20ON"
  }
  
  log.debug "TDH [deviceToggle]: State: ${state.deviceState}, URI:${uri}"
  
  callDevice(uri)
  callDevice("Status")
}

def deviceOn() {
  callDevice("Power%20ON")
  callDevice("Status")
}

def deviceOff() {
  callDevice("Power%20OFF")
  callDevice("Status")
}


