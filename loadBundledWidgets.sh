#!/bin/bash

./runBase.sh -h owfserver:7443 -clientKeys testAdmin1.p12 -clientKeyPass password -serverKeys owfserver.jks -serverKeyPass changeit -widgets addOWFBundledWidgets.csv
