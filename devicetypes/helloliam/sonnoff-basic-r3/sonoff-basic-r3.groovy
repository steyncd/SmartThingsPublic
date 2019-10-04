metadata {
	definition(name: "Sonoff-Basic-R3", namespace: "HelloLiam", author: "Christo Steyn", ocfDeviceType: "oic.d.smartplug", vid: "generic-switch") {
		capability "Switch"
		capability "Refresh"
		capability "Polling"
		capability "Actuator"
        capability "Signal Strength"
        capability "Configuration"

        attribute "startup", "string"
        attribute "pulse", "string"
        attribute "pulseDuration", "string"
        attribute "ssid", "string"
        attribute "signal", "string"
	}

	// UI tile definitions
	tiles(scale: 2) {
		standardTile("switch", "device.switch", width: 6, height: 3, decoration: "flat", canChangeIcon: true) {
            state "on", label: 'On', action: "switch.off", icon: "st.switches.switch.on", backgroundColor: "#00A0DC", nextState: "turningOff"
            state "off", label: 'Off', action: "switch.on", icon: "st.switches.switch.off", backgroundColor: "#ffffff", nextState: "turningOn"
            state "turningOn", label: 'Turning On', action: "switch.off", icon: "st.switches.switch.on", backgroundColor: "#00A0DC", nextState: "turningOff"
            state "turningOff", label: 'Turning Off', action: "switch.on", icon: "st.switches.switch.off", backgroundColor: "#ffffff", nextState: "turningOn"
		}

		standardTile("refresh", "device.switch", inactiveLabel: false, decoration: "flat", width: 6, height: 2) {
			state "default", label: '', action: "refresh.refresh", icon: "st.secondary.refresh"
		}

        valueTile("startup", "device.startup", width: 2, height: 1) {
			state "default", label: 'Startup:\n ${currentValue}', backgroundColor: "#ffffff"
		}
        
        valueTile("pulse", "device.pulse", width: 2, height: 1) {
			state "default", label: 'Pulse:\n ${currentValue}', backgroundColor: "#ffffff"
		}
        
        valueTile("pulseDuration", "device.pulseDuration", width: 2, height: 1) {
			state "default", label: 'Pulse Duration:\n ${currentValue}', backgroundColor: "#ffffff"
		}
                
        valueTile("ssid", "device.ssid", width: 3, height: 1) {
			state "default", label: 'SSID:\n ${currentValue}'
		}

        valueTile("signal", "device.signal", width: 3, height: 1) {
			state "default", label: 'Signal Strength:\n ${currentValue}'
		}

		main("switch")
		details(["switch", "refresh", "startup", "pulse", "pulseDuration", "ssid", "signal"])
	}

	preferences {
		input(name: "ipAddress", type: "string", title: "IP Address", description: "IP Address of Sonoff", displayDuringSetup: true, required: true)
		input(name: "port", type: "number", title: "Port", description: "Port", displayDuringSetup: true, required: true, defaultValue: 80)
		input(name: "deviceId", type: "string", title: "Device ID", description: "Device ID", displayDuringSetup: true, required: true)
		
		section("Sonoff Host") {
			
		}

		section("Authentication") {
			input(name: "username", type: "string", title: "Username", description: "Username", displayDuringSetup: false, required: false)
			input(name: "password", type: "password", title: "Password (sent cleartext)", description: "Caution: password is sent cleartext", displayDuringSetup: false, required: false)
		}
	}
}

def installed() {
    sendEvent(name: "startup", value: "refresh first")
    sendEvent(name: "pulse", value: "refresh first")
    sendEvent(name: "pulseDuration", value: "refresh first")
    sendEvent(name: "ssid", value: "refresh first")
    sendEvent(name: "signal", value: "refresh first")
}

def parse(String description) {
	def message = parseLanMessage(description)	

	// parse result from current and legacy formats
	def resultJson = {}
	if (message?.json) {
		// current json data format
		resultJson = message.json
	}
	else {
		// legacy Content-Type: text/plain
		// with json embedded in body text
		def STATUS_PREFIX = "STATUS = "
		def RESULT_PREFIX = "RESULT = "
		if (message?.body?.startsWith(STATUS_PREFIX)) {
			resultJson = new groovy.json.JsonSlurper().parseText(message.body.substring(STATUS_PREFIX.length()))
		}
		else if (message?.body?.startsWith(RESULT_PREFIX)) {
			resultJson = new groovy.json.JsonSlurper().parseText(message.body.substring(RESULT_PREFIX.length()))
		}
	}

	// consume and set switch state
	if ((resultJson?.data.switch in ["on", 1, "1"]) || (resultJson?.data?.switch in [1, "1"])) {
		setSwitchState(true)
	}
	else if ((resultJson?.data.switch in ["off", 0, "0"]) || (resultJson?.data?.switch in [0, "0"])) {
		setSwitchState(false)
	}
	else {
		log.error "can not parse result with header: $message.header"
		log.error "...and raw body: $message.body"
	}
}

def setSwitchState(Boolean on) {
	log.info "setSwitchState: switch is " + (on ? "ON" : "OFF")
	sendEvent(name: "switch", value: on ? "on" : "off")
}

def on() {
	log.debug "on: ON"
    def payload = "{ \"switch\": \"on\"  }"
	def command = createCommand("switch", payload, setPowerOnCallback);;

    sendHubCommand(command);
}

def off() {
	log.debug "off: OFF"
    def payload = "{ \"switch\": \"off\"  }"
	log.debug payload
	def command = createCommand("switch", payload, setPowerOffCallback);;

    sendHubCommand(command);
}

def poll() {
	log.debug "poll: POLL"
    def payload = "{ }"
	def command = createCommand("info", payload, refreshCallback);;

    sendHubCommand(command);
}

def refresh() {
	log.debug "refresh: REFRESH"
    def payload = "{ }"
	def command = createCommand("info", payload, refreshCallback);;

    sendHubCommand(command);
}

def createCommand(String command, String payload, callback){

    def ipAddress = ipAddress ?: settings?.ipAddress ?: device.latestValue("ipAddress");
    def port = port ?: settings?.port ?: device.latestValue("port");
    def deviceId = deviceId ?: settings?.deviceId ?: device.latestValue("deviceId");
    def jsonSlurper = new groovy.json.JsonSlurper()

    log.debug "createCommandAction(${command}:${payload}) to device at ${ipAddress}:${port}"

	if (!ipAddress) {
		log.warn "aborting. ip address of device not set"
		return null;
	}

	def path = "/zeroconf/${command}";

	def dni = null;

    def body = "{ \"deviceid\": \"${deviceId}\", \"data\": ${payload} }";

    def params = [
        method: "POST",
        path: path,
        body: body,
        headers: [
            HOST: "${ipAddress}:${port}"
        ]
    ]

    log.debug params

    def options = [
        callback : callback
    ];

	def hubAction = new physicalgraph.device.HubAction(params, dni, options);
}

def refreshCallback(physicalgraph.device.HubResponse response){
    def jsobj = response?.json;

    log.debug "refreshCallback: response received"
    log.debug jsobj
    def jsonSlurper = new groovy.json.JsonSlurper()
    def dataObj = jsonSlurper.parseText(response?.json?.data)
    log.debug dataObj

    sendEvent(name : "startup", value : dataObj.startup)
    sendEvent(name : "pulse", value : dataObj.pulse)
    sendEvent(name : "pulseDuration", value : dataObj.pulseWidth)
    sendEvent(name : "ssid", value : dataObj.ssid)

    def on = dataObj.switch == "on";

    setSwitchState(on);

	log.debug "refreshCallback: GETSignal Strength"
    def payload = "{ }"
	def command = createCommand("signal_strength", payload, signalStrengthCallBack);;

    sendHubCommand(command);
}

def signalStrengthCallBack(physicalgraph.device.HubResponse response) {
    def jsobj = response?.json;

    log.debug "signalStrengthCallBack: response received"
    log.debug jsobj
    def jsonSlurper = new groovy.json.JsonSlurper()
    def dataObj = jsonSlurper.parseText(response?.json?.data)
    log.debug dataObj

    sendEvent(name : "signal", value : dataObj.signalStrength)
    //sendEvent(name : "lqi", value : dataObj.signalStrength)
    sendEvent(name : "rssi", value : dataObj.signalStrength)
}

def setPowerOffCallback(physicalgraph.device.HubResponse response){
	log.debug "setPowerOffCallback: response received"
	log.debug response.json

    def on = response.json.error == 0;
        
    setSwitchState(!on);
    
    def payload = "{ }"
	def command = createCommand("info", payload, refreshCallback);;

    sendHubCommand(command);
}

def setPowerOnCallback(physicalgraph.device.HubResponse response){
	log.debug "setPowerOnCallback: response received"
	log.debug response.json

    def on = response.json.error == 0;

    setSwitchState(on);
    
    def payload = "{ }"
	def command = createCommand("info", payload, refreshCallback);;

    sendHubCommand(command);
}

def setPowerCallback(physicalgraph.device.HubResponse response){
	log.debug "setPowerCallback: response received"
	log.debug response.json

    def on = response.json.error == 0;

    setSwitchState(on);
}

private String convertIPtoHex(ipAddress) { 
	String hex = ipAddress.tokenize( '.' ).collect {  String.format( '%02x', it.toInteger() ) }.join()
	return hex
}

private String convertPortToHex(port) {
	String hexport = port.toString().format('%04x', port.toInteger())
	return hexport
}
