/**
 *  MIT License
 *  Copyright 2024 Konnected Inc (help@konnected.io)
 *
 *  Adatped from: https://github.com/bradsjm/hubitat-public/blob/main/ESPHome/ESPHome-ratGDO.groovy
 *  Copyright 2022 Jonathan Bradshaw (jb@nrgup.net)
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in all
 *  copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 *  SOFTWARE.
 */
import com.hubitat.app.DeviceWrapper

metadata {
    definition(
        name: 'Konnected Alarm Panel',
        namespace: 'konnected',
        author: 'Konnected Inc.',
        singleThreaded: true,
        importUrl: 'https://github.com/konnected-io/konnected-hubitat/blob/master/drivers/konnected-alarm-panel.groovy'
    ){
        capability 'Refresh'
        capability 'Initialize'
        capability 'SignalStrength'

        attribute 'esphomeVersion', 'string'
        attribute 'uptime', 'number'

    }
    preferences {
        input name: 'ipAddress',    // required setting for API library
            type: 'text',
            title: 'Device IP Address',
            required: true

        input name: 'logEnable',    // if enabled the library will log debug details
            type: 'bool',
            title: 'Enable Debug Logging',
            required: false,
            defaultValue: false

        input name: 'logTextEnable',
            type: 'bool',
            title: 'Enable descriptionText logging',
            required: false,
            defaultValue: true
    }

}

public void initialize() {
    // API library command to open socket to device, it will automatically reconnect if needed
    openSocket()

    if (logEnable) {
        runIn(1800, 'logsOff')
    }
}

public void installed() {
    log.info "${device} driver installed"
}

public void logsOff() {
    espHomeSubscribeLogs(LOG_LEVEL_INFO, false) // disable device logging
    device.updateSetting('logEnable', false)
    log.info "${device} debug logging disabled"
}

public void uninstalled() {
    closeSocket('driver uninstalled') // make sure the socket is closed when uninstalling
    log.info "${device} driver uninstalled"
}

public void refresh() {
    log.info "${device} refresh"
    state.clear()
    state.requireRefresh = true
    espHomeDeviceInfoRequest()
}

// the parse method is invoked by the API library when messages are received
public void parse(Map message) {
    if (logEnable) { log.debug "ESPHome received: ${message}" }

    switch (message.type) {
        case 'device':
            doParseDevice(message)
            break

        case 'entity':
            doParseEntity(message)
            break

        case 'state':
            doParseState(message)
            break
    }
}

private void doParseDevice(Map message) {
    if (!device.label && message.macAddress) {
        device.label = "Alarm Panel " + message.macAddress.replaceAll(':','').toLowerCase().substring(6)
    }
}

private void doParseEntity(Map message) {
    
    // Add binary sensors as child devices
    if (message.platform == 'binary') {
        def deviceType

        switch (message.deviceClass) {
          case 'motion':
            deviceType = "Motion Sensor"
            break
          case 'smoke':
            deviceType = "Smoke Detector"
            break
          case 'carbon_monoxide':
            deviceType = "Carbon Monoxide Detector"
            break
          case 'moisture':
            deviceType = "Water Sensor"
            break
          case 'sound':
            deviceType = "Sound Sensor"
            break
          default:
            deviceType = "Contact Sensor"
        }
        getOrCreateDevice(message.key as Long, deviceType, message.name)
        return
    }

    if (message.platform == 'sensor') {
        if (message.deviceClass == 'signal_strength' && message.unitOfMeasurement == 'dBm') {
            state.signalStrengthKey = message.key
        }
        if (message.objectId == "uptime") {
            state.uptimeKey = message.key as Long
        }
        return
    }

}

private void doParseState(Map message) {
  if (!message.key) { return }

  // update the state of a child device that matches the key
  def childDevice = getChildDevice("${device.id}-${message.key}")
  if (childDevice && message.hasState) {
    String attr = childDevice.getSupportedAttributes().first()
    String value
    String description

    switch (attr) {
        case 'contact':
            value = message.state ? 'open' : 'closed'
            description = 'Contact'
            break
        case 'motion':
            value = message.state ? 'active' : 'inactive'
            description = 'Motion'
            break
        case 'smoke':
            value = message.state ? 'detected' : 'clear'
            description = 'Smoke'
            break
        case 'carbonMonoxide':
            value = message.state ? 'detected' : 'clear'
            description = 'Carbon Monoxide'
            break
        case 'sound':
            value = message.state ? 'detected' : 'not detected'
            description = 'Sound'
            break
    }
    if (!value) { return }
    sendDeviceEvent(attr, value, type, description, childDevice, attr)    
  }
  
  if (state.signalStrengthKey as Long == message.key && message.hasState) {
    Integer rssi = Math.round(message.state as Float)
    String unit = "dBm"
    if (device.currentValue("rssi") != rssi) {
        descriptionText = "${device} rssi is ${rssi}"
        sendEvent(name: "rssi", value: rssi, unit: unit, type: type, descriptionText: descriptionText)
        if (logTextEnable) { log.info descriptionText }
    }
    return
  }

  if (state.uptimeKey as Long == message.key) {
      int value = message.state as int
      sendDeviceEvent("uptime", value, type, "Uptime")
      return
  }

}

// child device management
private void sendDeviceEvent(name, value, type, description, child = null, childEventName = null, isStateChange = null) {
    String descriptionText = "${description} is ${value} (${type})"
    def event = [name: name, value: value, type: type, descriptionText: descriptionText]
    if (isStateChange) {
        event.isStateChange = true
    }
    if (!child && (isStateChange || device.currentValue(name) != value)) {
        if (logTextEnable) { log.info descriptionText }
        sendEvent(event)
    }
    if (child) {
        if (isStateChange || child.currentValue(name) != value) {
            if (childEventName) {
                event.name = childEventName
            }
            child.parse([event])
        }
    }
}

private DeviceWrapper getOrCreateDevice(key, deviceType, label = null) {
    if (key == null) {
        return null
    }
    String dni = "${device.id}-${key}" as String
    def d = getChildDevice(dni)
    if (!d) {
        d = addChildDevice(
            "hubitat",
            "Generic Component ${deviceType}",
            dni                            
        )
        d.name = label?:deviceType
        d.label = label?:deviceType
    }
    return d
}

#include esphome.espHomeApiHelper