  This is the source code distribution for VASL. Usage is subject to
the terms of the Library Gnu Public Licesne (LGPL), described in the
LICENSE.txt file and also available from http://www.opensource.org.

  The included source code comprises a library, not a complete
standalone application. The library is used by VASSAL
(http://www.vassalengine.org), which is a standalone application.

  This is the source code used in VASL, the VASSAL module for Advanced
Squad Leader.

  To begin development on your own, you'll need this source code, an
installation of VASSAL, and the VASL module.

  To build VASL use Maven 3.1. Copy the "settings.xml.TEMPLATE" to the .m2
directory in your home folder and rename to "settings.xml". Adapt the
path to your local JDK installation that you would like to use for
compiling VASL in the settings file.

  In order to create the VASL module, run "mvn install" from the command
line. This will create the module in the target folder.

  In order to run VASL, run "mvn exec:exec" from the command line.
