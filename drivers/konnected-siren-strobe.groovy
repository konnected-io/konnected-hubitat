/**
 *  Konnected Siren/Strobe
 *
 *  Copyright 2017 konnected.io
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
  definition (name: "Konnected Siren/Strobe", namespace: "konnected-io", author: "konnected.io", mnmn: "SmartThings", vid: "generic-siren") {
    capability "Alarm"
    capability "Switch"
    capability "Actuator"
  }

  preferences {
  	input (
      name: "invertTrigger", type: "bool", title: "Low Level Trigger",
  	  description: "Select if the attached relay uses a low-level trigger. Default is high-level trigger"
      )
    input (
      type: "bool",
			name: "txtEnable",
			title: "Enable descriptionText logging",
			required: false,
			defaultValue: false
      )
  }

}

def logInfo(msg) {
	if (txtEnable) {
		log.info msg
	}
}

def updated() {
  parent.updateSettingsOnDevice()
}

def updatePinState(Integer state) {
  def val
  if (state == 0) {
    val = invertTrigger ? "on" : "off"
  } else {
    val = invertTrigger ? "off" : "on"
  }
  def descriptionText = "$device is $val"
  logInfo descriptionText
  sendEvent(name: "switch", value: val, displayed: false)
  if (val == "on") { val = "both" }
  sendEvent(name: "alarm", value: val)
}

def off() {
  def val = invertTrigger ? 1 : 0
  parent.deviceUpdateDeviceState(device.deviceNetworkId, val)
}

def on() {
  def val = invertTrigger ? 0 : 1
  parent.deviceUpdateDeviceState(device.deviceNetworkId, val)
}

def both() { on() }

def strobe() { on() }

def siren() { on() }

def triggerLevel() {
  return invertTrigger ? 0 : 1
}

def currentBinaryValue() {
  if (device.currentValue('switch') == 'on') {
    invertTrigger ? 0 : 1
  } else {
    invertTrigger ? 1 : 0
  }
}
