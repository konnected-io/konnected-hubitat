metadata {
    definition(
        name: 'Konnected Button Trigger',
        namespace: 'konnected',
        author: 'Konnected Inc.',
        singleThreaded: true,
        importUrl: 'https://github.com/konnected-io/konnected-hubitat/blob/master/drivers/konnected-button-trigger.groovy'
    ){
        capability "Momentary"
    }
}

public void push(_) {
    parent?.componentPush(this.device)
}
