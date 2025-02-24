metadata {
    definition(
        name: 'Konnected Button Trigger',
        namespace: 'konnected',
        author: 'Konnected Inc.',
        singleThreaded: true,
        importUrl: 'https://github.com/konnected-io/konnected-hubitat/blob/master/drivers/konnected-button-trigger.groovy'
    ){
        capability "Actuator"
        capability "Momentary"
        capability "PushableButton"
    }
}

def installed() {
    initialize()
}

def updated() {
    intialize()
}

def initialize() {
    sendEvent(name: "numberOfButtons", value: 1)
}

public void push(_) {
    parent?.componentPush(this.device)
}
