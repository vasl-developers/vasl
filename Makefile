SHELL:=/bin/bash

SRCDIR:=src
LIBDIR:=lib
CLASSDIR:=classes
TMPDIR:=tmp
JDOCDIR:=javadoc
DOCDIR:=doc
DISTDIR:=dist

VNUM:=5.9.0
#VERSION:=$(shell unzip -p dist/VASL.mod buildFile | grep \<VASSAL.launch.BasicModule  | sed -e 's/.*version="\([^"]*\)".*/\1/g')
VERSION:=5.9-beta3
BUILDDIR:=$(TMPDIR)/VASL-$(VERSION)

#CLASSPATH:=$(CLASSDIR):$(LIBDIR)/*
#JAVAPATH:=/usr/lib/jvm/java-1.6.0-sun

CLASSPATH:=$(CLASSDIR):$(shell echo $(LIBDIR)/*.jar | tr ' ' ':')
#JAVAPATH:=/usr/lib/jvm/java-1.5.0-sun
JAVAPATH:=/usr/lib/jdk

JC:=$(JAVAPATH)/bin/javac
JCFLAGS:=-d $(CLASSDIR) -source 5 -target 5 -Xlint -classpath $(CLASSPATH) -sourcepath $(SRCDIR)

JAR:=$(JAVAPATH)/bin/jar
JDOC:=$(JAVAPATH)/bin/javadoc

NSIS:=PATH=$$PATH:~/java/nsis makensis

LAUNCH4J:=~/java/launch4j/launch4j

SOURCES:=$(shell find $(SRCDIR) -name '*.java' | sed "s/^$(SRCDIR)\///")
CLASSES:=$(SOURCES:.java=.class)

vpath %.class $(shell find $(CLASSDIR) -type d)
vpath %.java  $(shell find $(SRCDIR) -type d -name .svn -prune -o -print)
vpath %.jar $(LIBDIR)

all: $(BUILDDIR) $(CLASSDIR) $(CLASSES) module

$(CLASSDIR):
	mkdir -p $(CLASSDIR)

%.class: %.java
	$(JC) $(JCFLAGS) $<

module: $(CLASSES) $(BUILDDIR)/VASL.mod

$(BUILDDIR)/VASL.mod: dist/VASL.mod $(BUILDDIR)
	cp dist/VASL.mod $(BUILDDIR)
	cd classes && zip -0 ../$(BUILDDIR)/VASL.mod -r VASL
	cd dist/moduleData && zip -0 ../../$(BUILDDIR)/VASL.mod -r *
	cd $(BUILDDIR) && unzip -p VASL.mod buildFile | sed -e 's/\(<VASSAL.launch.BasicModule.*version="\)[^"]*\(".*\)/\1$(VERSION)\2/g' > buildFile && zip -m -0 VASL.mod buildFile

fast: clean $(CLASSDIR) fast-compile 

fast-compile:
	$(JC) $(JCFLAGS) $(shell find $(SRCDIR) -name '*.java')

#show:
#	echo $(patsubst %,-C $(TMPDIR)/doc %,$(wildcard $(TMPDIR)/doc/*)) 

$(TMPDIR):
	mkdir -p $(BUILDDIR)

$(BUILDDIR): $(TMPDIR)
	mkdir -p $(BUILDDIR)

$(TMPDIR)/VASL.exe: $(TMPDIR)
	cp dist/windows/VASL.l4j.xml $(TMPDIR)
	sed -i -e 's/%NUMVERSION%/$(VNUM)/g' \
				 -e 's/%FULLVERSION%/$(VERSION)/g' $(TMPDIR)/VASL.l4j.xml
	$(LAUNCH4J) $(CURDIR)/$(TMPDIR)/VASL.l4j.xml

$(TMPDIR)/VASL-$(VERSION).app: all $(JARS) $(TMPDIR) $(BUILDDIR)/VASL.mod
	mkdir -p $@/Contents/{MacOS,Resources}
	cp dist/macosx/{PkgInfo,Info.plist} $@/Contents
	sed -i -e 's/%SVNVERSION%/$(SVNVERSION)/g' \
         -e 's/%NUMVERSION%/$(VNUM)/g' \
				 -e 's/%FULLVERSION%/$(VERSION)/g' $@/Contents/Info.plist
	cp dist/macosx/JavaApplicationStub $@/Contents/MacOS
	svn export $(LIBDIR) $@/Contents/Resources/Java
	cp $(BUILDDIR)/VASL.mod $@/Contents/Resources/Java
	rm $@/Contents/Resources/Java/AppleJavaExtensions.jar
	svn export $(DOCDIR) $@/Contents/Resources/doc

$(TMPDIR)/VASL-$(VERSION)-macosx.dmg: $(TMPDIR)/VASL-$(VERSION).app
	genisoimage -V VASL-$(VERSION) -r -apple -root VASL-$(VERSION).app -o $@ $<

$(TMPDIR)/VASL-$(VERSION)-generic.zip: all $(JARS)
	mkdir -p $(BUILDDIR)
	svn export $(DOCDIR) $(BUILDDIR)/doc
	svn export $(LIBDIR) $(BUILDDIR)/lib
	rm $(BUILDDIR)/lib/AppleJavaExtensions.jar
	cp dist/VASL.sh dist/windows/VASL.bat $(BUILDDIR)
	cd $(TMPDIR) ; zip -9rv $(notdir $@) VASL-$(VERSION) ; cd ..

$(TMPDIR)/VASL-$(VERSION)-windows.exe: release-generic $(TMPDIR)/VASL.exe
	rm $(BUILDDIR)/VASL.sh
	cp $(TMPDIR)/VASL.exe $(BUILDDIR)
	for i in `find $(BUILDDIR) -type d` ; do \
		echo SetOutPath \"\$$INSTDIR\\`echo $$i | \
			sed -e 's/tmp\/VASL-$(VERSION)\/\?//' -e 's/\//\\\/g'`\" ; \
		find $$i -maxdepth 1 -type f -printf 'File "%p"\n' ; \
	done >$(TMPDIR)/install_files.inc
	sed -e 's/^SetOutPath/RMDir/' \
			-e 's/^File "$(TMPDIR)\/VASL-$(VERSION)/Delete "$$INSTDIR/' \
			-e 's/\//\\/g' <$(TMPDIR)/install_files.inc | \
		tac	>$(TMPDIR)/uninstall_files.inc
	$(NSIS) -NOCD -DVERSION=$(VERSION) -DTMPDIR=$(TMPDIR) dist/windows/nsis/installer.nsi

release-macosx: $(TMPDIR)/VASL-$(VERSION)-macosx.dmg

release-windows: $(TMPDIR)/VASL-$(VERSION)-windows.exe

release-generic: $(TMPDIR)/VASL-$(VERSION)-generic.zip

release: release-generic release-windows release-macosx

clean-release:
	$(RM) -rf $(TMPDIR) 

clean: clean-release
	$(RM) -r $(CLASSDIR)

.PHONY: all fast fast-compile clean release release-macosx release-windows release-generic clean-release i18n images help javadoc clean-javadoc version
