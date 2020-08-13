/**
 *  Konnected
 *
 *  Copyright 2020 konnected.io
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 */
public static String version() { return "1.0.0" }

definition(
  name:        "Konnected Pro Device",
  parent:      "konnected-io:Konnected Pro",
  namespace:   "konnected-io",
  author:      "konnected.io",
  description: "Do not install this directly, use Konnected Pro instead",
  category:    "Safety & Security",
  iconUrl:     "https://raw.githubusercontent.com/konnected-io/docs/master/assets/images/KonnectedSecurity.png",
  iconX2Url:   "https://raw.githubusercontent.com/konnected-io/docs/master/assets/images/KonnectedSecurity@2x.png",
  iconX3Url:   "https://raw.githubusercontent.com/konnected-io/docs/master/assets/images/KonnectedSecurity@3x.png",
  singleInstance: false
)

mappings {
  path("/device/:id") { action: [ POST: "childDeviceStateUpdate", GET: "getDeviceState" ] }
  path("/ping") { action: [ GET: "devicePing"] }
}

preferences {
  page(name: "pageWelcome",       install: false, uninstall: true, content: "pageWelcome", nextPage: "pageConfiguration")
  page(name: "pageDiscovery",     install: false, content: "pageDiscovery" )
  page(name: "pageConfiguration")
}

def installed() {
  log.info "installed(): Installing Konnected Device: " + state.device?.serialNumber
  parent.registerKnownDevice(state.device.serialNumber)
  initialize()
}

def updated() {
  log.info "updated(): Updating Konnected Device: " + state.device?.serialNumber
  unsubscribe()
  unschedule()
  initialize()
}

def uninstalled() {
  def device = state.device
  log.info "uninstall(): Removing Konnected Device $device?.mac"
	
  getAllChildDevices().each {
	log.info "deleting device: $it"
	deleteChildDevice(it.deviceNetworkId)
  }

  def body = [
    token : "",
    apiUrl : "",
    sensors : [],
    actuators : [],
    dht_sensors: [],
    ds18b20_sensors: []
  ]

  if (device) {
    parent.removeKnownDevice(device.serialNumber)
    sendHubCommand(new hubitat.device.HubAction([
      method: "PUT",
      path: "/settings",
      headers: [ HOST: getDeviceIpAndPort(device), "Content-Type": "application/json" ],
      body : groovy.json.JsonOutput.toJson(body)
    ], getDeviceIpAndPort(device) ))
  }
}

def initialize() {
  discoverySubscription()
  if (app.label != deviceName()) { app.updateLabel(deviceName()) }
  childDeviceConfiguration()
  updateSettingsOnDevice()
}

def deviceName() {
  if (name) {
    return name
  } else if (state.device) {
    return "konnected-" + state.device.serialNumber[-6..-1]
  } else {
    return "New Konnected device"
  }
}

// Page : 1 : Welcome page - Manuals & links to devices
def pageWelcome() {
  def device = state.device
  dynamicPage( name: "pageWelcome", title: deviceName(), nextPage: "pageConfiguration") {
    section() {
      if (device) {
        href(
          name:        "device_" + device.serialNumber,
          image:       "https://docs.konnected.io/assets/favicons/apple-touch-icon.png",
          title:       "Device status",
          description: getDeviceIpAndPort(device),
          url:         "http://" + getDeviceIpAndPort(device)
        )
      } else {
        href(
          name:        "discovery",
          title:       "Click here to start discovery",
          page:        "pageDiscovery"
        )
      }
    }

    section("Help & Support") {
      href(
        name:        "pageWelcomeManual",
        title:       "Instructions & Knowledge Base",
        description: "View the support portal at help.konnected.io",
        required:    false,
        image:       "https://raw.githubusercontent.com/konnected-io/docs/master/assets/images/manual-icon.png",
        url:         "https://help.konnected.io"
      )
      paragraph "Konnected Service Manager v${version()}"
    }
  }
}

// Page : 2 : Discovery page
def pageDiscovery() {
  if(!state.accessToken) { createAccessToken() }

  // begin discovery protocol if device has not been found yet
  if (!state.device) {
    discoverySubscription()
    parent.discoverySearch()
  }

  dynamicPage(name: "pageDiscovery", install: false, refreshInterval: 3) {
    if (state.device?.verified) {
      section() {
        href(
          name: "discoveryComplete",
          title: "Found konnected-" + state.device.serialNumber[-6..-1] + "!",
          description: "Click here to continue",
          page: "pageConfiguration"
        )
      }
    } else {
      section("Please wait while we discover your device") {
        paragraph "This may take up to a minute."
      }
    }
  }
}

// Page : 3 : Configure things wired to the Konnected board
def pageConfiguration(params) {

  def device = state.device
  dynamicPage(name: "pageConfiguration", install: true) {
    section() {
      input(
        name: "name",
        type: "text",
        title: "Device name",
        required: false,
        defaultValue: "konnected-" + device?.serialNumber[-6..-1]
      )
    }
    section(title: "Configure things wired to each zone") {
      pinMapping().each { i, label ->
        def deviceTypeDefaultValue = (settings."deviceType_${i}") ? settings."deviceType_${i}" : ""
        def deviceLabelDefaultValue = (settings."deviceLabel_${i}") ? settings."deviceLabel_${i}" : ""

        input(
          name: "deviceType_${i}",
          type: "enum",
          title: label,
          required: false,
          multiple: false,
          options: pageConfigurationGetDeviceType(i),
          defaultValue: deviceTypeDefaultValue,
          submitOnChange: true
        )

        if (settings."deviceType_${i}") {
          input(
            name: "deviceLabel_${i}",
            type: "text",
            title: "${label} device name",
            description: "Name the device connected to ${label}",
            required: (settings."deviceType_${i}" != null),
            defaultValue: deviceLabelDefaultValue
          )
        }
      }
    }
    section(title: "Advanced settings") {
    	input(
          name: "blink",
          type: "bool",
          title: "Blink LED on transmission",
          required: false,
          defaultValue: true
        )
        input(
          name: "enableDiscovery",
          type: "bool",
          title: "Enable device discovery",
          required: false,
          defaultValue: true
        )
    }
  }
}

private Map pageConfigurationGetDeviceType(i) {
  def deviceTypes = [:]
  def sensorPins = [1,2,3,4,5,6,7,8,9,10,11,12]
  def digitalSensorPins = [1,2,3,4,5,6,7,8]
  def actuatorPins = [1,2,3,4,5,6,7,8,'alarm1','out1','alarm2_out2']

  if (sensorPins.contains(i)) {
    deviceTypes << sensorsMap()
  }

  if (actuatorPins.contains(i)) {
    deviceTypes << actuatorsMap()
  }

  if (digitalSensorPins.contains(i)) {
  	deviceTypes << digitalSensorsMap()
  }

  return deviceTypes
}

def getDeviceIpAndPort(device) {
  "${convertHexToIP(device.networkAddress)}:${convertHexToInt(device.deviceAddress)}"
}

// Device Discovery : Subscribe to SSDP events
def discoverySubscription() {
  subscribe(location, "ssdpTerm.${parent.discoveryDeviceType()}", discoverySearchHandler, [filterEvents:false])
}

// Device Discovery : Handle search response
def discoverySearchHandler(evt) {
  def event = parseLanMessage(evt.description)
  log.debug event
  event << ["hub":evt?.hubId]
  String ssdpUSN = event.ssdpUSN.toString()
  def chipId = (ssdpUSN =~ /([0-9a-f]{12})::/)[0][1]
  def device = state.device
  if (device?.ssdpUSN == ssdpUSN) {
    device.networkAddress = event.networkAddress
    device.deviceAddress = event.deviceAddress
    log.debug "Refreshed attributes of device $device"
  } else if (device == null && parent.isNewDevice(chipId)) {
    state.device = event
    log.debug "Discovered new device $event"
    unsubscribe()
    discoveryVerify(event)
  }
}

// Device Discovery : Verify a Device
def discoveryVerify(Map device) {
  log.debug "Verifying communication with device $device"
  String host = getDeviceIpAndPort(device)
  sendHubCommand(
    new hubitat.device.HubAction(
      """GET ${device.ssdpPath} HTTP/1.1\r\nHOST: ${host}\r\n\r\n""",
      hubitat.device.Protocol.LAN,
      host,
      [callback: discoveryVerificationHandler]
    )
  )
}

//Device Discovery : Handle verification response
def discoveryVerificationHandler(hubitat.device.HubResponse hubResponse) {
  def body = hubResponse.xml
  def device = state.device
  if (device?.ssdpUSN.contains(body?.device?.UDN?.text())) {
    log.debug "Verification Success: $body"
    device.name =  body?.device?.roomName?.text()
    device.model = body?.device?.modelName?.text()
    device.serialNumber = body?.device?.serialNumber?.text().replaceAll('0x','')
    device.verified = true
  }
}

// Child Devices : create/delete child devices from Hubitat app selection
def childDeviceConfiguration() {
  def device = state.device
  settings.each { name , value ->
    def nameValue = name.split("\\_")
    if (nameValue[0] == "deviceType") {
      def deviceDNI = [ device.serialNumber, "${nameValue[1]}"].join('|')
      def deviceLabel = settings."deviceLabel_${nameValue[1]}"
      def deviceType = value

	  // multiple ds18b20 sensors can be connected to one pin, so skip creating child devices here
      // child devices will be created later when they report state for the first time
	  if (deviceType == "Konnected Temperature Probe (DS18B20)") { return }

      def deviceChild = getChildDevice(deviceDNI)
      if (!deviceChild) {
        if (deviceType != "") {
          addChildDevice("konnected-io", deviceType, deviceDNI, device.hub, [ "label": deviceLabel ? deviceLabel : deviceType , "completedSetup": true ])
        }
      } else {
        // Change name if it's set here
        if (deviceChild.label != deviceLabel)
          deviceChild.label = deviceLabel

        // Change Type, you will lose the history of events. delete and add back the child
        if (deviceChild.name != deviceType) {
          
			
			
			Device(deviceDNI)
          if (deviceType != "") {
            addChildDevice("konnected-io", deviceType, deviceDNI, device.hub, [ "label": deviceLabel ? deviceLabel : deviceType , "completedSetup": true ])
          }
        }
      }
    }
  }

  def deleteChildDevices = getAllChildDevices().findAll {
    settings."deviceType_${it.deviceNetworkId.split("\\|")[1]}" == null
  }

  deleteChildDevices.each {
    log.debug "Deleting device $it.deviceNetworkId"
    deleteChildDevice(it.deviceNetworkId)
  }
}

// Child Devices : update state of child device sent from nodemcu
def childDeviceStateUpdate() {
  def zone = request.JSON.zone
  def addr = request.JSON?.addr?.replaceAll(':','')
  def deviceId = params.id + "|" + zone
  if (addr) { deviceId = "$deviceId|$addr" }
  def device = getChildDevice(deviceId)
  if (device) {
  	if (request.JSON?.temp) {
        log.debug "Temp: $request.JSON"
    	device.updateStates(request.JSON)
    } else {
	    def newState = params.deviceState ?: request.JSON.state.toString()
      log.debug "Received sensor update from Konnected device: $deviceId = $newState"
	    device.setStatus(newState)
    }
  } else {
  	if (addr) {
      // New device found at this address, create it
      log.debug "Adding new thing attached to Konnected: $deviceId"
      device = addChildDevice("konnected-io", settings."deviceType_$pin", deviceId, state.device.hub, [ "label": addr , "completedSetup": true ])
      device.updateStates(request.JSON)
    } else {
	    log.warn "Device $deviceId not found!"
    }
  }
}

def getDeviceState() {
  def zone = request.JSON?.zone
  def deviceId = params.id + "|" + zone
  def device = getChildDevice(deviceId)
  if (device) {
    return [zone: zone, state: device.currentBinaryValue()]
  }
}

//Device: Ping from device
def devicePing() {
  return ""
}

//Device : update NodeMCU with token, url, sensors, actuators from SmartThings
def updateSettingsOnDevice() {
  if(!state.accessToken) { createAccessToken() }

  def device    = state.device
  def sensors   = []
  def actuators = []
  def dht_sensors = []
  def ds18b20_sensors = []
  def ip        = getDeviceIpAndPort(device)
  def chipId    = device.serialNumber

  getAllChildDevices().each {
    def zone = it.deviceNetworkId.split("\\|")[1]
    if (it.name.contains("DHT")) {
      dht_sensors = dht_sensors + [ zone : zone, poll_interval : it.pollInterval() ]
    } else if (sensorsMap()[it.name]) {
      sensors = sensors + [ zone : zone ]
    } else if (actuatorsMap()[it.name]) {
      actuators = actuators + [ zone : zone, trigger : it.triggerLevel() ]
    }
  }

  settings.each { name , value ->
    def nameValue = name.split("\\_")
    if (nameValue[0] == "deviceType" && value.contains("DS18B20")) {
      ds18b20_sensors = ds18b20_sensors + [ zone : nameValue[1], poll_interval : 3 ]
    }
  }

  log.debug "Configured sensors on $chipId: $sensors"
  log.debug "Configured actuators on $chipId: $actuators"
  log.debug "Configured DHT sensors on $chipId: $dht_sensors"
  log.debug "Configured DS18B20 sensors on $chipId: $ds18b20_sensors"

  log.debug "Blink is: ${settings.blink}"
  def body = [
    token : state.accessToken,
    apiUrl : getFullLocalApiServerUrl(),
    blink: settings.blink,
    discovery: settings.enableDiscovery,
    sensors : sensors,
    actuators : actuators,
    dht_sensors : dht_sensors,
    ds18b20_sensors : ds18b20_sensors
  ]

  log.debug "Updating settings on device $chipId at $ip"
  sendHubCommand(new hubitat.device.HubAction([
    method: "PUT",
    path: "/settings",
    headers: [ HOST: ip, "Content-Type": "application/json" ],
    body : groovy.json.JsonOutput.toJson(body)
  ], ip ))
}

// Device: update NodeMCU with state of device changed from SmartThings
def deviceUpdateDeviceState(deviceDNI, deviceState, Map actuatorOptions = [:]) {
  def deviceId = deviceDNI.split("\\|")[1]
  def chipId = deviceDNI.split("\\|")[0]
  def body = [ zone : deviceId, state : deviceState ] << actuatorOptions
  def device = state.device

  if (device && device.serialNumber == chipId) {
    log.debug "Updating device $chipId zone $deviceId to $deviceState at " + getDeviceIpAndPort(device)
    sendHubCommand(new hubitat.device.HubAction([
      method: "PUT",
      path: "/zone",
      headers: [ HOST: getDeviceIpAndPort(device), "Content-Type": "application/json" ],
      body : groovy.json.JsonOutput.toJson(body)
    ], getDeviceIpAndPort(device), [callback: "syncChildPinState"]))
  }
}

void syncChildPinState(hubitat.device.HubResponse hubResponse) {
  def chipIdPart = hubResponse.mac.substring(0,9).toLowerCase()
  def device = getAllChildDevices().find {     
      def chipId = it.deviceNetworkId.split("\\|")[0]
      def zone = it.deviceNetworkId.split("\\|")[1]
      chipIdPart == chipId.substring(2,11) && zone == hubResponse.json.zone 
  }
  device?.updatePinState(hubResponse.json.state)
}

private Map pinMapping() {
    return [
      1: "Zone 1",
      2: "Zone 2",
      3: "Zone 3",
      4: "Zone 4",
      5: "Zone 5",
      6: "Zone 6",
      7: "Zone 7",
      8: "Zone 8",
      9: "Zone 9",
      10: "Zone 10",
      11: "Zone 11",
      12: "Zone 12",
      alarm1: "ALARM1",
      out1: "OUT1",
      alarm2_out2: "ALARM2/OUT2"  
    ]
}

private Map actuatorsMap() {
  return [
    "Konnected Siren/Strobe"      : "Siren/Strobe",
    "Konnected Switch"            : "Switch",
    "Konnected Momentary Switch"  : "Momentary Switch",
    "Konnected Beep/Blink"        : "Beep/Blink Switch"
  ]
}

private Map sensorsMap() {
  return [
    "Konnected Contact Sensor"    : "Open/Close Sensor",
    "Konnected Motion Sensor"     : "Motion Sensor",
    "Konnected Smoke Sensor"      : "Smoke Detector",
    "Konnected CO Sensor"         : "Carbon Monoxide Detector",
    "Konnected Panic Button"      : "Panic Button",
    "Konnected Water Sensor"      : "Water Sensor"
  ]
}

private Map digitalSensorsMap() {
  return [
	  "Konnected Temperature & Humidity Sensor (DHT)" : "Temperature & Humidity Sensor",
    "Konnected Temperature Probe (DS18B20)" : "Temperature Probe(s)"
  ]
}

private Integer convertHexToInt(hex) { Integer.parseInt(hex,16) }
private String convertHexToIP(hex) { [convertHexToInt(hex[0..1]),convertHexToInt(hex[2..3]),convertHexToInt(hex[4..5]),convertHexToInt(hex[6..7])].join(".") }

