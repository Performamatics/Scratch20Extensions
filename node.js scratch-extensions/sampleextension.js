var libscratchextension = require('./libscratchextension.js');

// Simple function to print a message on screen
function writeToConsole(message) {
	console.log(message);
}

// Create an instance of the ScratchExtension object
scratchextension = new libscratchextension.ScratchExtension(12345);

// Register the method writeToConsole, with the identifier 'write', which
// is how it is referred to in the definition in the  sampleextension.json file
//
//      [" ", "write %s to console", "write", "hello world"],
//
scratchextension.registerMethod('write', writeToConsole);

// Push a variable (actually, a constant)
// Corresponding definition in sampleextension.json:
//      ["r", "π", "PI"],
scratchextension.pushVariable('PI', 3.1415926536);

// Push another variable -- we update this later as a part of the 
// 'poll' event handler
// Corresponding definition in sampleextension.json:
//      ["r", "random number", "random"],
scratchextension.pushVariable('random', Math.random());

// Listen for a bunch of events.
scratchextension.on('connect', function() {
    console.log("Scratch connected");
});
scratchextension.on('disconnect', function() {
    console.log("Scratch disconnected");
});
scratchextension.on('poll', function() {
    // This updates the value of 'random' everytime a poll request comes in
    // from the Scratch 2.0 SWF.
    //
    // NOTE: The new value will be sent to the SWF only when the next 'poll' 
    // request comes in.
    scratchextension.pushVariable('random', Math.random());
});
scratchextension.on('data', function(data) {
    // This gets called whenever any data comes in from the 
    // Scratch 2.0 SWF. 
    //console.log(data);
});

// Start listening on the socket (this needs to be done at the end for now)
scratchextension.listen();
