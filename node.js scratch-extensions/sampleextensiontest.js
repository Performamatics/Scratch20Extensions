var libscratchextension = require('./libscratchextension.js')
  , app = require('http').createServer(handler)
  , io = require('socket.io').listen(app)
  , xml2js = require('xml2js')
  , parser = new xml2js.Parser()
  , fs = require('fs');

// creating the server ( localhost:8000 ) 
app.listen(8000);

// on server started we can load our client.html page
function handler ( req, res ) {
  fs.readFile( __dirname + '/client.html' ,
  function ( err, data ) {
    if ( err ) {
      console.log( err );
      res.writeHead(500);
      return res.end( 'Error loading client.html' );
    }
    res.writeHead( 200 );
    res.end( data );
  });
};

// creating a new websocket to keep the content updated without any AJAX request
io.sockets.on( 'connection', function ( socket ) {
  console.log(__dirname);
  // watching the xml file
  fs.watch( __dirname + '/example.xml', function ( curr, prev ) {
    // on file change we can read the new xml
    fs.readFile( __dirname + '/example.xml', function ( err, data ) {
      if ( err ) throw err;
      // parsing the new xml data and converting them into json file
      parser.parseString( data );
    });
  });
  // when the parser ends the parsing we are ready to send the new data to the frontend
  parser.addListener('end', function( result ) {

    // adding the time of the last update
    result.time = new Date();
    socket.volatile.emit( 'notification' , result );
  });
});



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
