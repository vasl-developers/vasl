#!/bin/sh

# Execute this file to launch VASL on MacOS or Linux
cd `dirname "$0"` && java -classpath lib/Vengine.jar VASSAL.launch.Player --load VASL.mod "$@"
