var events = require("events");
var net = require('net');
var util = require("util");

function ScratchExtension(port) {
    events.EventEmitter.call(this);

    this.port = port;
	this.methodTable = {};
	this.variableTable = {};
	var self = this;

    this.server = net.createServer(function(c) { //'connection' listener
		self.emit('connect', null);

		c.setNoDelay(true); // This is a local connection, so we should be able to handle this
		
		c.on('end', function() {
			self.emit('disconnect', null);
		});

		c.on('data', function(data) {
		    self.emit('data', data.toString());
		    
			// AS3 policy file request
			if (data.toString().indexOf("<policy-file-request/>") > -1) {
				POLICY = '<cross-domain-policy>\n';
				POLICY += '  <allow-access-from domain="*" to-ports="' + port.toString() + '"/>\n';
				POLICY += '</cross-domain-policy>\n\0';
				c.write(POLICY);
				return;
			}

			try {
				cmd = JSON.parse(data.toString());
			} catch (err) {
				util.error("Error parsing data: " + data);
				util.error(err);
				return; 
			}

			if (cmd.method == 'poll') {
				var response = {method : 'update', params : []};

				for (var variable in self.variableTable) {
					response.params.push([variable, self.variableTable[variable]]);
				}

				c.write(JSON.stringify(response)+'\n');

				self.emit('poll', null);
			}
			else {
				try {	
					self.methodTable[cmd.method].apply(null, cmd.params);
				} catch (err) {
					util.error("Error: Unknown command from Scratch.");
					util.error(err);
				}
			}

		});

	});
}

util.inherits(ScratchExtension, events.EventEmitter);

ScratchExtension.prototype.listen = function() {
	var self = this;

	self.server.listen(this.port, function() { //'listening' listener
		return;
	});
}

ScratchExtension.prototype.registerMethod = function(methodName, method) {
	var self = this;

	self.methodTable[methodName] = method;
}

ScratchExtension.prototype.pushVariable = function(variableName, value) {
	var self = this;

	this.variableTable[variableName] = value;
}


exports.ScratchExtension = ScratchExtension;
