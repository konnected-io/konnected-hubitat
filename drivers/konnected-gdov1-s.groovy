/**
 *  MIT License
 *  Copyright 2023 Konnected Inc (help@konnected.io)
 *
 *  Derived from: https://github.com/bradsjm/hubitat-public/blob/main/ESPHome/ESPHome-GarageDoor.groovy
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
metadata {
    definition(
        name: 'Konnected Garage Door',
        namespace: 'konnected',
        author: 'Konnected Inc',
        singleThreaded: true,
        importUrl: 'https://github.com/konnected-io/konnected-hubitat/blob/master/drivers/konnected-gdov1-s.groovy') {

        capability 'Actuator'
        capability 'Sensor'
        capability 'GarageDoorControl'
        capability 'ContactSensor'
        capability 'Switch'
        capability 'Refresh'
        capability 'SignalStrength'
        capability 'Initialize'

        // attribute populated by ESPHome API Library automatically
        attribute 'networkStatus', 'enum', [ 'connecting', 'online', 'offline' ]
        attribute 'sensorDistance', 'number'
        attribute 'calibratedDistance', 'number'

        command 'calibrate'
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

public void updated() {
    log.info "${device} driver configuration updated"
    initialize()
}

public void uninstalled() {
    closeSocket('driver uninstalled') // make sure the socket is closed when uninstalling
    log.info "${device} driver uninstalled"
}

// driver commands
public void open() {
    String doorState = device.currentValue('door')
    if (doorState != 'closed') {
        log.info "${device} ignoring open request (door is ${doorState})"
        return
    }
    // API library cover command, entity key for the cover is required
    if (logTextEnable) { log.info "${device} open" }
    espHomeCoverCommand(key: state.cover as Long, position: 1.0)
}

public void close() {
    String doorState = device.currentValue('door')
    if (doorState != 'open') {
        log.info "${device} ignoring close request (door is ${doorState})"
        return
    }
    // API library cover command, entity key for the cover is required
    if (logTextEnable) { log.info "${device} close" }
    espHomeCoverCommand(key: state.cover as Long, position: 0.0)
}

public void refresh() {
    log.info "${device} refresh"
    state.clear()
    state.requireRefresh = true
    espHomeDeviceInfoRequest()
}

public void on() {
  if (logTextEnable) { log.info "${device} switch on" }
  espHomeSwitchCommand(key: state.switch as Long, state: 1)
}

public void off() {
  if (logTextEnable) { log.info "${device} switch off" }
  espHomeSwitchCommand(key: state.switch as Long, state: 0)
}

public void calibrate() {
  log.info "${device} calibrate"
  espHomeCallService('calibrate_open_garage')
}

// the parse method is invoked by the API library when messages are received
public void parse(Map message) {
    if (logEnable) { log.debug "ESPHome received: ${message}" }

    switch (message.type) {
        case 'device':
            // Device information
            break

        case 'entity':
            // This will populate the cover entity
            if (message.platform == 'cover') {
                state['cover'] = message.key
                return
            }

            if (message.platform == 'sensor' && message.deviceClass == 'signal_strength' && message.unitOfMeasurement == 'dBm') {
                state['signalStrength'] = message.key
                return
            }

            if (message.platform == 'sensor' && message.objectId == 'sensor_distance') {
                state['sensorDistance'] = message.key
                return
            }

            if (message.platform == 'number' && message.objectId == 'sensor_calibration') {
                state['calibratedDistance'] = message.key
                return
            }

            if (message.platform == 'binary' && message.objectId == 'wired_sensor') {
                state['wiredSensor'] = message.key
                return
            }

            if (message.platform == 'switch' && message.objectId == 'switch') {
                state['switch'] = message.key
                return
            }
            break

        case 'state':
            String type = message.isDigital ? 'digital' : 'physical'
            // Check if the entity key matches the message entity key received to update device state
            if (state.cover as Long == message.key) {
                String value
                switch (message.currentOperation) {
                    case COVER_OPERATION_IDLE:
                        value = message.position > 0 ? 'open' : 'closed'
                        break
                    case COVER_OPERATION_IS_OPENING:
                        value = 'opening'
                        break
                    case COVER_OPERATION_IS_CLOSING:
                        value = 'closing'
                        break
                }
                if (device.currentValue('door') != value) {
                    descriptionText = "${device} door is ${value}"
                    sendEvent(name: 'door', value: value, type: type, descriptionText: descriptionText)
                    if (logTextEnable) { log.info descriptionText }
                }
                return
            }

            if (state.signalStrength as Long == message.key && message.hasState) {
                Integer rssi = Math.round(message.state as Float)
                String unit = 'dBm'
                if (device.currentValue('rssi') != rssi) {
                    descriptionText = "${device} rssi is ${rssi}"
                    sendEvent(name: 'rssi', value: rssi, unit: unit, type: type, descriptionText: descriptionText)
                    if (logTextEnable) { log.info descriptionText }
                }
                return
            }

            if (state.wiredSensor as Long == message.key && message.hasState) {
              String value = message.state ? 'open' : 'closed'
              if (device.currentValue('contact') != value) {
                descriptionText = "Contact is ${value}"
                sendEvent([
                  name: 'contact',
                  value: value, type: type,
                  descriptionText: descriptionText
                ])
                if (logTextEnable) { log.info descriptionText }
              }
              return              
            }

            if (state.sensorDistance as Long == message.key && message.hasState) {
              BigDecimal value = message.state.setScale(2, BigDecimal.ROUND_HALF_UP)
              if (value == 0.0) { value = null }
              if (device.currentValue('sensorDistance') != value) {
                descriptionText = "Distance sensor got ${value}"
                sendEvent([
                  name: 'sensorDistance',
                  value: value, unit: 'm', type: type,
                  descriptionText: descriptionText
                ])
                if (logTextEnable) { log.info descriptionText }
              }
              return
            }

            if (state.calibratedDistance as Long == message.key && message.hasState) {
              BigDecimal value = message.state.setScale(2, BigDecimal.ROUND_HALF_UP)
              if (device.currentValue('calibratedDistance') != value) {
                descriptionText = "Calibrated distance changed to ${value}"
                sendEvent([
                  name: 'calibratedDistance',
                  value: value, unit: 'm', type: type,
                  descriptionText: descriptionText
                ])
                if (logTextEnable) { log.info descriptionText }
              }
              return
            }

            if (state.switch as Long == message.key) {
              String value = message.state ? 'on' : 'off'
              if (device.currentValue('switch') != value) {
                descriptionText = "Switch turned ${value}"
                sendEvent([
                  name: 'switch',
                  value: value, type: type,
                  descriptionText: descriptionText
                ])
                if (logTextEnable) { log.info descriptionText }
              }
              return
            }

            break
    }
}

// Put this line at the end of the driver to include the ESPHome API library helper
#include esphome.espHomeApiHelper