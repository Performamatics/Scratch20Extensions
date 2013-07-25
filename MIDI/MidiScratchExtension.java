// MidiScratchExtension.java
// Copyright (c) MIT Media Laboratory, 2011
// John Maloney
//
// This Scratch Extension helper runs a server that allows Scratch to send MIDI commands
// to control a software or hardware music synthesizer. Only a subset of MIDI is
// supported: noteOn, noteOff, programChange, controllerChange, and pitchBend.
// For a summary of the MIDI protocol, see http://www.midi.org/techspecs/midimessages.php
//
// Built using the JSON-simple library from http://code.google.com/p/json-simple/
// Download the small (~25k) .jar file and put it in your Java extensions folder.
// (This library is built into MidiScratchExtension.jar.)

// This version of the library was modified by Alex Ruthmann to listen for channel values of 1-16,
// rather than 0-15.

import java.net.*;
import java.io.*;
import java.util.*;
import javax.sound.midi.*;
import org.json.simple.*;

public class MidiScratchExtension {

	public static void main(String[] args) throws IOException, MidiUnavailableException {
		// Run the server. Create a server socket, accept connections, and start an instance of MsgServer
		// for each connection to handle that connection a separate thread. Throws an exceptions and exits
		// if the server socket cannot be opened (e.g. that port is in use) or the MIDI system is not available.
		InetAddress addr = InetAddress.getLocalHost();
		System.out.println("MidiScratchExtension started on " + addr.toString());
		
		ServerSocket serverSock = new ServerSocket(MsgServer.PORT);
		MidiSystem.getReceiver(); // ensure that the MIDI system is available
		while (true) {
			Socket sock = serverSock.accept();
			MsgServer msgServer = new MsgServer(sock);
			new Thread(msgServer).start();
		}
	}
}

class MsgServer implements Runnable {

	public static final int PORT = 17801; // the MidiScratchExtension port

	private Socket sock;
	InputStream sockIn;
	OutputStream sockOut;

	private Receiver midiOut;
	private Synthesizer synth;
	private Receiver synthOut;
	private Boolean useSynth = true;
	private long baseTime = new Date().getTime();

	private String msgBuf = "";	// incomplete message awaiting more data (does not include '\n')
	private JSONArray params;	// parameters for the current message

	public MsgServer(Socket socket) throws MidiUnavailableException {
		// Initialize this instance to handle requests on the given socket.
		sock = socket;
		midiOut = MidiSystem.getReceiver();
		synth = MidiSystem.getSynthesizer();
		synth.open();
		synthOut = synth.getReceiver();
	}

	public void run() {
		try {
			msgLoop();
		} catch (IOException e) {
			try { sock.close(); } catch (IOException ignored) { } // try to close socket, ignore errors
			e.printStackTrace(System.err);
		}
		if (synth != null) {
			synth.close();
			synthOut = null;
			synth = null;
		}
	}
	
	private void msgLoop() throws IOException {
		// The loop in this method handles all communications for this server session.
		// Communications in both directions broken into a sequence of messages separated by
		// newline characters ('\n'). Messages are respresented in JSON with no embedded newlines.
		// (Newlines within JSON string are encoded as '\n').

		sockIn = sock.getInputStream();
		sockOut = sock.getOutputStream();
		byte[] buf = new byte[5000];

		System.err.println("-----Connected-----.");
		while (true) {
			int bytes_read = sockIn.read(buf, 0, buf.length);
			if (bytes_read < 0) break; // client closed socket
			String s = new String(Arrays.copyOf(buf, bytes_read));
			if (s.equals("<policy-file-request/>\0")) {
				// To support the Flash security model, the server responds to a policy file request
				// by sending a policy file string that allows Flash to connect to this port. The
				// policy request and response are terminated with null characters ('\0') rather than
				// newlines. The policy exchange happens before message exchange begins.
				sendPolicyRequest(sockOut);
			} else {
				msgBuf += s;
				int i;
				while ((i = msgBuf.indexOf('\n')) >= 0) {
					// While msgBuf includes a newline, extract a message and handle it.
					// If a message is not yet complete (no newline), msgBuf holds the partial
					// message until more data arrives.
					String msg = msgBuf.substring(0, i);
					msgBuf = msgBuf.substring(i + 1, msgBuf.length());
					try {
						handleMsg(msg);
					} catch (Exception e) {
						// Errors while handling a message print the stack but do not kill the server.
						System.err.println("problem handling: " + msg);
						e.printStackTrace(System.err);
					}
				}
			}
		}
		sock.close();
		System.err.println("-----Closed-----");
	}
	
	private void sendPolicyRequest(OutputStream sockOut) throws IOException {
		// Send a Flash null-teriminated cross-domain policy file.
		String policyFile =
			"<cross-domain-policy>\n" +
			"  <allow-access-from domain=\"*\" to-ports=\"" + PORT + "\"/>\n" +
			"</cross-domain-policy>\n\0";
		byte[] outBuf = policyFile.getBytes();
		sockOut.write(outBuf, 0, outBuf.length);
		sockOut.flush();
	}
	
	private void handleMsg(String msg) throws Exception {
		// Handle a MIDI command.
		JSONObject msgObj = (JSONObject) JSONValue.parse(msg);
		String cmd = (String) msgObj.get("method");
		params = (JSONArray) msgObj.get("params");
		if (cmd.equals("midiReset")) {
			midiReset();
		} else if (cmd.equals("noteOn")) {
			noteOn(intarg(0), intarg(1), intarg(2)); // key, vel, chan
		} else if (cmd.equals("noteOff")) {
			noteOn(intarg(0), 0, intarg(1)); // key, chan (noteOn with zero velocity turns the note off)
		} else if (cmd.equals("controller")) {
			controlChange(intarg(0), intarg(1), intarg(2)); // controllerNum, value, chan
		} else if (cmd.equals("pitchBend")) {
			pitchBend(intarg(0), intarg(1)); // bend, chan
		} else if (cmd.equals("program")) {
			programChange(intarg(0), intarg(1)); // instrument, chan
		} else if (cmd.equals("poll") || cmd.equals("update-poll")) { // Note: "update-poll" is only for backward compatibility with an old version
			long msecs = new Date().getTime() - baseTime;
			String reply = "{\"method\": \"update\", \"params\": [ [ \"time\", " + msecs + "] ]}\n";
			byte[] outBuf = reply.getBytes();
			sockOut.write(outBuf, 0, outBuf.length);
		} else if (cmd.equals("useJavaSynth")) {
			useSynth = (Boolean) params.get(0);
			System.out.println("useJavaSynth: " + useSynth);
//			String reply = "{\"method\": \"update\", \"params\": [[ [ \"time\", " + new Date().getTime() + "] ]]}\n";
//			byte[] outBuf = reply.getBytes();
//			sockOut.write(outBuf, 0, outBuf.length);
		} else {
			System.err.println("unknown: " + msg);
		}
	}

	private int intarg(int index) {
		// Return the integer parameter at the given index.
		// Assume the given parameter is a Number and coerce it to an int.
		return ((Number) params.get(index)).intValue();
	}

	private void controlChange(int controllerNum, int value, int chan) throws InvalidMidiDataException {
		ShortMessage msg = new ShortMessage();
		chan = chan - 1; // resets chan value to 0 offset from 1 offset
		msg.setMessage(ShortMessage.CONTROL_CHANGE, chan, controllerNum, value);
		midiOut.send(msg, -1);
		if (useSynth) synthOut.send(msg, -1);
	}
	
	private void midiReset() throws InvalidMidiDataException {
		int chan;
		for (chan = 0; chan < 16; chan++) {
			controlChange(121, 0, chan); // reset controllers to default values
			controlChange(123, 0, chan); // turn all notes off
		}
	}

	private void noteOn(int key, int velocity, int chan) throws InvalidMidiDataException {
		ShortMessage msg = new ShortMessage();
		chan = chan - 1; // resets chan value to 0 offset from 1 offset
		msg.setMessage(ShortMessage.NOTE_ON, chan, key, velocity);
		midiOut.send(msg, -1);
		if (useSynth) synthOut.send(msg, -1);
	}

	private void pitchBend(int bend, int chan) throws InvalidMidiDataException {
		ShortMessage msg = new ShortMessage();
		chan = chan - 1; // resets chan value to 0 offset from 1 offset
		msg.setMessage(ShortMessage.PITCH_BEND, chan, bend & 0x7F, (bend >> 7) & 0x7F);
		midiOut.send(msg, -1);
		if (useSynth) synthOut.send(msg, -1);
	}
	
	private void programChange(int instrument, int chan) throws InvalidMidiDataException {
		ShortMessage msg = new ShortMessage();
		chan = chan - 1; // resets chan value to 0 offset from 1 offset
		msg.setMessage(ShortMessage.PROGRAM_CHANGE, chan, instrument, 0);
		midiOut.send(msg, -1);
		if (useSynth) synthOut.send(msg, -1);
	}

}
