metadata {
	definition(name: "Tasmota-SV", namespace: "HelloLiam", author: "Christo Steyn", ocfDeviceType: "oic.d.smartplug", vid: "generic-switch") {
        capability "Momentary"
        capability "Power Meter"
		capability "Refresh"
		capability "Polling"
		capability "Actuator"
        capability "Signal Strength"
        capability "Configuration"

        command "reload"
        command "updateStatus"

        attribute "refresh", "string"
        attribute "module", "string"
        attribute "version", "string"
        attribute "ssid", "string"
        attribute "signalStregnth", "string"
        attribute "hostname", "string"
        attribute "macAddress", "string"
        attribute "ipAddress", "string"
        attribute "upTime", "string"
	}

	// UI tile definitions
	tiles(scale: 2) {
		// standardTile("switch", "device.switch", width: 6, height: 3, decoration: "flat", canChangeIcon: true) {
        //     state "on", label: 'On', action: "switch.off", icon: "st.switches.switch.on", backgroundColor: "#00A0DC", nextState: "turningOff"
        //     state "off", label: 'Off', action: "switch.on", icon: "st.switches.switch.off", backgroundColor: "#ffffff", nextState: "turningOn"
        //     state "turningOn", label: 'Turning On', action: "switch.off", icon: "st.switches.switch.on", backgroundColor: "#00A0DC", nextState: "turningOff"
        //     state "turningOff", label: 'Turning Off', action: "switch.on", icon: "st.switches.switch.off", backgroundColor: "#ffffff", nextState: "turningOn"
		// }

        multiAttributeTile(name:"switch", type:"generic", width:6, height:4) {
			tileAttribute("device.switch", key: "PRIMARY_CONTROL") {
				attributeState "on", label:'On', action:"switch.off", icon:"st.switches.switch.on", backgroundColor:"#00A0DC", nextState:"turningOff"
				attributeState "off", label:'Off', action:"switch.on", icon:"st.switches.switch.off", backgroundColor:"#ffffff", nextState:"turningOn"
				attributeState "turningOn", label:'Turning On', action:"switch.off", icon:"st.switches.switch.on", backgroundColor:"#00A0DC", nextState:"turningOff"
				attributeState "turningOff", label:'Turning Off', action:"switch.on", icon:"st.switches.switch.off", backgroundColor:"#ffffff", nextState:"turningOn"
			}
		}

        standardTile("refresh", "refresh", width: 6, height: 2, decoration: "flat", canChangeIcon: true) {
             state "refresh", label: 'Refresh', action: "refresh.refresh", icon:"st.secondary.refresh", backgroundColor: "#ffffff", nextState: "refreshing"
             state "refreshing", label: 'Refreshing', icon:"st.secondary.refresh", nextState: "refresh"
		 }

        valueTile("ssid", "ssid", width: 3, height: 2) {
			state "ssid", label: 'SSID:\n ${currentValue}', backgroundColor: "#ffffff"
		}

        valueTile("macAddress", "macAddress", width: 3, height: 1) {
			state "macAddress", label: 'MAC Address:\n ${currentValue}', backgroundColor: "#ffffff"
		}

        valueTile("ipAddress", "ipAddress", width: 3, height: 1) {
			state "ipAddress", label: 'IP Address:\n ${currentValue}', backgroundColor: "#ffffff"
		}

        valueTile("module", "module", width: 3, height: 1) {
			state "module", label: 'Module:\n ${currentValue}', backgroundColor: "#ffffff"
		}

        valueTile("version", "version", width: 3, height: 1) {
			state "version", label: 'Tasmota Version:\n ${currentValue}', backgroundColor: "#ffffff"
		}

        valueTile("hostname", "hostname", width: 3, height: 1) {
			state "hostname", label: 'Hostname:\n ${currentValue}', backgroundColor: "#ffffff"
		}

        valueTile("upTime", "upTime", width: 3, height: 1) {
			state "upTime", label: 'Up Time:\n ${currentValue}', backgroundColor: "#ffffff"
		}

		main "switch"
		details(["switch", "refresh", "ssid", "macAddress", "ipAddress", "module", "version", "hostname", "upTime"])
	}

    
    preferences {
        
        input(name: "ipAddress", type: "string", title: "IP Address", description: "IP Address of Sonoff", displayDuringSetup: true, required: true)

		section("Sonoff Host") {
			
		}

		section("Authentication") {
			input(name: "username", type: "string", title: "Username", description: "Username", displayDuringSetup: false, required: false)
			input(name: "password", type: "password", title: "Password (sent cleartext)", description: "Caution: password is sent cleartext", displayDuringSetup: false, required: false)
		}
	}
}

/* ----------------------------------------------------  COMMANDS ---------------------------------------------------- */
def parse(String description) {
    log.debug "PARSE MESSAGE: ${description}"
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

def installed(){
    reload();
}

def updated(){
    reload();
}

def reload(){
    refresh();
}

def poll() {
	log.debug "COMMAND - POLL"
	sendCommand("Status", "0", refreshCallback)
}

def refresh() {
	log.debug "COMMAND - REFRESH"
	sendCommand("Status", "0", refreshCallback)
}

def on(){
	log.debug "COMMAND - ON"
    setPower("on")
}

def off(){
	log.debug "COMMAND - OFF"
    setPower("off")
}

def updateStatus(status){
    // it doesnt look like what we get from status contains necessary information
    // So let's make our own HTTP call to get all status changes.

    refresh();
}

def setPower(power){
	log.debug "Setting power to: $power"

    sendHubCommand(createCommand("Power", power, setPowerCallback));
}

/* ----------------------------------------------------  COMMANDS END ---------------------------------------------------- */


/* ----------------------------------------------------  CALLBACK METHODS ---------------------------------------------------- */
def refreshCallback(physicalgraph.device.HubResponse response){
    def jsobj = response?.json;
    log.debug "JSON: ${jsobj}";

    sendEvent(name : "module", value : response?.json?.Status?.Module)
    sendEvent(name : "version", value : response?.json?.StatusFWR?.Version)
    sendEvent(name : "ssid", value : response?.json?.StatusSTS?.Wifi?.SSId)
    sendEvent(name : "signalStregnth", value : response?.json?.StatusSTS?.Wifi?.RSSI)
    sendEvent(name : "hostname", value : response?.json?.StatusNET?.Hostname)
    sendEvent(name : "macAddress", value : response?.json?.StatusNET?.Mac)
    sendEvent(name : "ipAddress", value : response?.json?.StatusNET?.IPAddress)
    sendEvent(name : "upTime", value : response?.json?.StatusSTS?.Uptime)

    def fName = "";
    if (response?.json?.Status?.FriendlyName instanceof Collection && response?.json?.Status?.FriendlyName.size() > 0){
        fName = response?.json?.Status?.FriendlyName[0];
    }
    else
    {
        fName = response?.json?.Status?.FriendlyName;
    }

    sendEvent(name : "friendlyName", value : fName)
    
    //Send this to reset the refresh tile
    log.debug "Resetting Refresh button";
    sendEvent(name : "refresh", value : "refresh", isStateChange: true)

    //Refresh the switch state
    log.debug "Resetting Switch State";
    def on = response?.json?.Status?.Power == "ON" || response?.json?.Status?.Power == 1;
	
    setSwitchState(on);
}

def setPowerCallback(physicalgraph.device.HubResponse response){
	log.debug "Finished Setting power, JSON: ${response.json}"

    def on = response.json.POWER == "ON";
	
    //setSwitchState(on);
    refresh();
}


//HELPER METHODS
def setSwitchState(on){
	log.debug "Setting switch to ${on ? 'ON' : 'OFF'}"

	sendEvent(name: "switch", value: on ? "on" : "off")
}

def sendCommand(String command, callback) {
    return sendCommand(command, null);
}

def sendCommand(String command, payload, callback) {
	sendHubCommand(createCommand(command, payload, callback))
}

def createCommand(String command, payload, callback){
    def ipAddress = ipAddress ?: settings?.ipAddress ?: device.latestValue("ipAddress");
    def username = username ?: settings?.username ?: device.latestValue("username");
    def password = password ?: settings?.password ?: device.latestValue("password");

    log.debug "createCommandAction(${command}:${payload}) to device at ${ipAddress}:80"

	if (!ipAddress) {
		log.warn "aborting. ip address of device not set"
		return null;
	}

	def path = "/cm"
	if (payload){
		path += "?cmnd=${command}%20${payload}"
	}
	else{
		path += "?cmnd=${command}"
	}

	if (username){
		path += "&user=${username}"
		if (password){
			path += "&password=${password}"
		}
	}

    def dni = null;

    def params = [
        method: "GET",
        path: path,
        headers: [
            HOST: "${ipAddress}:80"
        ]
    ]

    def options = [
        callback : callback
    ];

	def hubAction = new physicalgraph.device.HubAction(params, dni, options);
}

private String convertIPtoHex(ipAddress) { 
	String hex = ipAddress.tokenize( '.' ).collect {  String.format( '%02x', it.toInteger() ) }.join()
	return hex
}

private String convertPortToHex(port) {
	String hexport = port.toString().format('%04x', port.toInteger())
	return hexport
}

