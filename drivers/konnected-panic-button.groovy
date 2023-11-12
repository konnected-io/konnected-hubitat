/**
 *  Konnected Panic Button
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
  definition (name: "Konnected Panic Button", namespace: "konnected-io", author: "konnected.io", mnmn: "SmartThings", vid: "generic-contact") {
    capability "Contact Sensor"
    capability "Sensor"
  }

  preferences {
    input name: "normalState", type: "enum", title: "Normal State",
	    options: ["Normally Closed", "Normally Open"],
      defaultValue: "Normally Closed",
      description: "By default, the alarm state is triggered when the sensor circuit is open (NC). Select Normally Open (NO) when a closed circuit indicates an alarm."
      input name: "txtEnable", type: "bool", title: "Enable descriptionText logging", defaultValue: true
  }

}

def isClosed() {
  normalState == "Normally Open" ? "open" : "closed"
}

def isOpen() {
  normalState == "Normally Open" ? "closed" : "open"
}

// Update state sent from parent app
def setStatus(state) {
  def stateValue = state == "1" ? isOpen() : isClosed()
  sendEvent(name: "contact", value: stateValue)
 if (txtEnable) log.info "$device.label is $stateValue"
}
