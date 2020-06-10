SHELL:=/bin/bash

SRCDIR:=src
LIBDIR:=lib
CLASSDIR:=classes
TMPDIR:=tmp
DOCDIR:=doc
DISTDIR:=dist

VNUM:=6.6.0
VERSION:=$(VNUM)-test-$(shell git rev-parse --short HEAD)

CLASSPATH:=$(CLASSDIR):$(shell echo $(LIBDIR)/*.jar | tr ' ' ':'):$(shell echo $(LIBDIRND)/*.jar | tr ' ' ':')
JAVAPATH:=/usr/bin

JC:=$(JAVAPATH)/javac
JCFLAGS:=-d $(CLASSDIR) -source 11 -target 11 -Xlint -classpath $(CLASSPATH) -sourcepath $(SRCDIR) -Xmaxwarns 500

JAR:=$(JAVAPATH)/jar

vpath %.class $(shell find $(CLASSDIR) -type d)
vpath %.java  $(shell find $(SRCDIR) -type d -name .svn -prune -o -print)
vpath %.jar $(LIBDIR)

all: $(CLASSDIR) fast-compile module

$(CLASSDIR):
	mkdir -p $(CLASSDIR)

%.class: %.java
	$(JC) $(JCFLAGS) $<

module: $(CLASSES) $(TMPDIR)/VASL-$(VERSION).vmod

$(TMPDIR)/VASL-$(VERSION).vmod: $(TMPDIR)
	mkdir -p $(TMPDIR)/vmod
	cp -a dist/* $(TMPDIR)/vmod
	cp -a classes/VASL $(TMPDIR)/vmod
	pushd $(TMPDIR)/vmod && unzip ../../$(LIBDIR)/jdom-2.0.5.jar && popd
	perl -pi -e 's/(<VASSAL.launch.BasicModule.*version=")[^"]*(".*)/$${1}$(VERSION)$${2}/' $(TMPDIR)/vmod/buildFile
	perl -pi -e 's/(<version>).*?(<\/version>)/$${1}$(VERSION)$${2}/' $(TMPDIR)/vmod/moduledata
	cd $(TMPDIR)/vmod && zip -9rv ../VASL-$(VERSION).vmod *

fast-compile:
	$(JC) $(JCFLAGS) $(shell find $(SRCDIR) -name '*.java')

$(TMPDIR):
	mkdir -p $(TMPDIR)

clean-release:
	$(RM) -r $(TMPDIR) 

clean: clean-release
	$(RM) -r $(CLASSDIR)

.PHONY: all fast-compile clean release clean-release
