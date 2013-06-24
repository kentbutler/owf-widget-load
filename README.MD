##OWF Widget Load Tool
This is a command-line script used to load widget and dashboard definitions 
into OWF from data in a CSV file.  Will assign widgets and dashboards to groups if the 
data is provided. Uses HTTPS against a running OWF server.

*__Compatibility__*: OWF 6, 7.x,

*__Requirements__*:
* Java JRE installed on the path
* A valid p12 certificate of an OWF administrator for logging into the server
* The server's JKS keystore for validation of the server's certificate

__TODO__: 
* Assignment by user is not currently implemented.
* Upload of Stack definitions

__Last UpDate__: 21 Jun 2013
__author__: kent.butler@nextcentury.com


###Quick Start

1. Launch the OWF server that is to be loaded 

2. Acquire a login certificate(p12) and keystore(jks) of server certificates for connecting
   to the server.  Examples for the default OWF instance are testAdmin1.p12 and keystore.jks.

3. Execute the load.groovy script with appropriate parameters, including the name of a CSV load
   file. A number of sample execution scripts exist:

* loadBundledDashboards.bat -- adds OWF demo dashboards, normally in the demo database, to localhost
* loadBundledWidgets.bat    -- adds OWF demo widgets, normally in the demo database, to localhost
* loadCommunityWidgets.bat  -- adds Community samples WorldwindMap, CityList, etc. to localhost
* loadSampleWidgets.bat     -- adds OWF packaged samples to localhost
* loadTrainingWidgets.bat   -- adds widgets from WDW training to localhost
* loadSynapse24Widgets.bat  -- adds Synapse 2.4 widgets to localhost
* loadSynapse30Widgets.bat  -- adds Synapse 3.0 widgets to localhost

* testServerConnection.bat  -- only tests the connection to the server, does not load widgets


#### Tool Usage Detail

It is easiest to use one of the existing scripts and modify it, but FYI here is the tool command-line interface:

        Usage: load.groovy [-v] [-h <host>:[<port>]] [-clientKeys <keyfile>] [-clientKeyPass <password>] [-serverKeys <keyfile>]
         [-serverKeyPass <password>] [-widgets|-dashboards] <filename>
          where:
           <filename> - a CSV text file containing widget definitions
           -h <host>:<port> - host location of the OWF server; optionally specify the port number, separated by a colon
           [-c | -context] <contextName> - optional context name of the OWF server; default is 'owf'
           -clientKeys <file> - keyfile for client authentication to OWF; currently must be PKCS12 (.p12)
           -clientKeyPass <password> - password for client keyfile
           -dashboards - treat the given file as a dashboard CSV file
           -serverKeys <file> - certificates for server validation; currently must be a JKS keystore
           -serverKeyPass <password> - password for server keystore
           -v - verbose output
           -widgets - treat the given file as a widget CSV file


### Data Specification

Data must be in CSV format.  Fields may optionally be contained within double quotes; this is required if the data contains commas or double-quotes. Double-quotes should be escaped with a second quote. If using MS Excel to prepare the CSV file, Excel will automatically escape double-quotes this way, but it will NOT automatically surround the entire data field with double quotes. 

Rows beginning with # are considered comments and will be ignored.

It is a good idea to verify the data file using a regular text editor before trying to load it using the load tool.


The Widget CSV data file requires the following fields. 

        Field            Pos  Description 
        -------------    ---- ----------------------------------------
        DISPLAY_NAME      0      widget name
        WIDGET_URL        1      widget URL
        IMAGE_URL_SMALL   2      an image URL
        IMAGE_URL_LARGE   3      an image URL
        WIDTH             4      a number
        HEIGHT            5      a number
        SINGLETON         6      true/false is widget a singleton
        BACKGROUND        7      true/false does widget run in background
        VISIBLE           8      true/false is widget visible in launch menu
        GUID              9      optional - widget GUID
        VERSION           10     optional version string
        UNIV_NAME         11     optional Universal Name, defaults to display name
        DESCRIPTOR_URL    12     optional URL to widget descriptor 
        DESCRIPTION       13     optional text description of widget
        ASSIGN_BY         14     'groups' -- FUTURE, this should support 'users' also
        ASSIGN NAMES      15     comma-separated list of groups to assign to -- FUTURE could be usernames

The Dashboard CSV data file requires the following fields, and may include a header row for labelling the columns:

        Field            Pos  Description 
        -------------    ---- ----------------------------------------
        DASH_NAME         0    dashboard name
        DASH_GUID         1    dashboard GUID, copy/paste from the dashboard description
        DASH_ADMIN        2    true/false is this an administrator dashboard
        DASH_GROUP        3    true/false is this a group dashboard
        DASH_DEFINITION   4    JSON string defining the dashboard; normally copied out of existing OWF
        DASH_ASSIGN_BY    5    current must be 'groups'
        DASH_ASSIGN       6    comma-separated list of groups to assign to -- FUTURE could be usernames

