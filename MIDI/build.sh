#!/bin/sh
javac MidiScratchExtension.java
jar -cfm MidiScratchExtension.jar manifest.mf *.class org/json/simple/*.class org/json/simple/parser/*.class
rm *.class org/json/simple/*.class org/json/simple/parser/*.class 