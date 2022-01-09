Markers; Custom (MC)

This folder serves as an image repository for images used as custom turn and phase track markers. These markers are located in:

VASL Counters > Other > Map > Custom

To use a Phase Wheel, Turn, or Reinforcement marker, right click on the piece to open its context window. Select sides from the drop down windows. Sides set for one of the components sets it for the other two as well.

To add additional side options, it's probably easiest to update the buildFile directly. A study of the buildFile should give a clue on proper formatting.

Update the CustomSides prototype (buildFile approx line 6930) and the two "Turn", "Reinf", and "Custom Phase Track" markers (buildFile approx lines 656,657; 660,661; 664,665).

The CustomSides prototype defines the Front Side and Back Side selection drop down menu and associates which Layer trait level is activated by that selection. The Layer trait level is simply the numeric position of the nationality in the listing of the nationalities. (For example: American is the first layer of the Turn, Reinforcement, and Custom Phase Tracks Layer trait; thus the CustomSides prototype associates that the selection "American" from the drop down window will call up a marker displaying Layer trait level 1).

The markers define the Layer trait with the name of the graphic file and level name.

It's best to add additional sides to the end of the lists for the markers and in Alphabetical order for the CustomSides Front Side and Back Side selection windows.

The lower Phase Wheel in the counter window is identical to the the top except alt+ctrl+A and alt+ctrl+B has been manually applied to activate the flip side of the Phase Wheel.

Custom Markers added by Allan Cannamore (bigal737 at gmail dot com), March 2021.
Special thanks to Gordon Molek for his work on the graphic images and Walter Parker for his work on customizing the Phase Wheel.