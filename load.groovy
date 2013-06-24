/*
   Import widget definitions to OWF via a CSV file.
   Implemented as a script for the purpose of being lightweight.
   That may change depending on requirements.

   last update: 6/24/2013 
   author: kbutler@nextcentury.com
*/
import javax.net.ssl.KeyManagerFactory;


import java.io.File;
import java.io.FileInputStream;
import java.security.KeyStore;

import javax.net.ssl.SSLSocket;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.conn.ssl.TrustSelfSignedStrategy;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.HttpParams;
import org.apache.http.util.EntityUtils;
import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.protocol.HTTP;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.client.CookieStore;
import org.apache.http.cookie.Cookie;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.client.protocol.ClientContext;

import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.CoreConnectionPNames;


import net.sf.json.JSONSerializer;
import net.sf.ezmorph.Morpher;


   // ** Constants ** 
   //   Normally these would be static finals - but this is a Groovy script

   // Data positions in the widget CSV layout
   DISPLAY_NAME = 0
   WIDGET_URL = 1
   IMAGE_URL_SMALL = 2
   IMAGE_URL_LARGE = 3
   WIDTH = 4
   HEIGHT = 5
   SINGLETON = 6
   BACKGROUND = 7
   VISIBLE = 8
   GUID = 9
   VERSION = 10
   UNIV_NAME = 11
   DESCRIPTOR_URL = 12
   DESCRIPTION = 13
   ASSIGN_BY = 14

   // Data positions in the dashboard CSV layout
   DASH_NAME = 0
   DASH_GUID = 1
   DASH_ADMIN = 2
   DASH_GROUP = 3
   DASH_DEFINITION = 4
   DASH_ASSIGN_BY = 5

   // Tokens representing how to assign
   ASSIGN_BY_GROUP = "groups"
   ASSIGN_BY_USER = "users" 

    //Default buffer size.
    BUFFER_SIZE = 1024 * 2;


   // Housekeeping - see what the User wants
   //   Groovy note: not declaring a var puts it into "the binding", which makes it globally accessible
   //      * this is only valid for Groovy scripts
   //      * declaring a type (or def) of a var automatically scopes it locally 
   config = [:]
   parseInput(args)

   httpclient = null  // required here for scoping
   def ctx = config?.ctx?:'owf'
   baseUrl = "https://${config.host}:${config.port}/$ctx/"

   debug "\tBase URL: " + baseUrl

   println "Loading keys..."

   // Create client keystore - this needs to contain:
   //   Client's certificate (i.e., Subject CN == clientname)
   //   Client's private key
   // Needs to be a JKS keystore
   clientKeys  = createKeystore(config.clientKeyFile, config.clientKeyPass)
                                                                                            
   // Truststore for server verification - this needs to contain:
   //   Server certificate: Subject CN == hostname
   //   Server private key
   // Needs to be a JKS keystore
   serverTrust  = createKeystore(config.serverKeyFile, config.serverKeyPass) 

   debug "\tclient key: " + clientKeys
   debug "\tserver key: " + serverTrust

   try {
      // ** Create Connection **
      socketFactory = new SSLSocketFactory(
                   SSLSocketFactory.TLS,
                   clientKeys,
                   config.clientKeyPass,
                   serverTrust,
                   null,
                   new TrustSelfSignedStrategy(),
                   SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER);

      // Connect socket to enable client PKI authentication
      HttpParams params = new BasicHttpParams();
      params.setParameter(CoreConnectionPNames.CONNECTION_TIMEOUT, 5000);

      SSLSocket socket = (SSLSocket) socketFactory.createSocket(params);
      socket.setEnabledCipherSuites("SSL_RSA_WITH_RC4_128_MD5"); 

      InetSocketAddress address = new InetSocketAddress(config.host, Integer.parseInt(config.port));
      socketFactory.connectSocket(socket, address, null, params);

      sch = new Scheme("https", new Integer(config.port), socketFactory);

      httpclient = new DefaultHttpClient();
      httpclient.getConnectionManager().getSchemeRegistry().register(sch);

      // Create a local instance of cookie store
      cookieStore = new BasicCookieStore();

      // Create local HTTP context
      localContext = new BasicHttpContext();
      // Bind custom cookie store to the local context
      localContext.setAttribute(ClientContext.COOKIE_STORE, cookieStore);

                                                                                            
      if (config.testOnly) {
         // ** Test page access **
         debug "===== Testing connection.... ====="
         post (baseUrl , [])
         debug "===== Terminating .... ====="
         System.exit(0)
      }

      // ** Load Files **
      ['widget':config.widgetFile, 'dashboard':config.dashboardFile].each() { type, file ->
         // Either may not be given
         if (!file) return

         println "Starting $type load...."
         debug "Opening $type file: $file" 

         /* Yes, we could parse this way */
         int lineNum = 0
         new File(file).eachLine { 
            // Parse each line of the CSV file
            if(it.startsWith("displayName")) {
               // Deprecated, but retain for compat
               debug "Not parsing first row"
            }
            else if(it.startsWith("#")) {
               debug "Not parsing comment line: " + it
            }
            else {
               String[] row = getCsvCells(it)
               if (type == "widget") {
                   processWidget(++lineNum, row)
               } 
               else if (type == "dashboard") {
                   processDashboard(++lineNum, row)
               } 
               else {
                   println "Unknown input type $type, skipping file"
               }
            }
         }
      }
      // Try using the OpenCSV lib; provides more error handling

      println "Completed load...." 

  } catch (Exception e) {
      e.printStackTrace()
  } finally {
     // When HttpClient instance is no longer needed,
     // shut down the connection manager to ensure
     // immediate deallocation of all system resources
     httpclient?.connectionManager?.shutdown();
  }

  def processWidget (def lineNum, def row) {
     String guid = addListing (lineNum, row)
     if (guid && guid.length() > 0) {
        assignListing (lineNum, row, guid)
     }
  }

  def processDashboard (def lineNum, def row) {
     String guid = addDashboard (lineNum, row)
     if (guid && guid.length() > 0) {
        assignDashboard (lineNum, row, guid)
     }
  }


  /**
  * <pre>
  * Add a listing wiht the details given in the row as:
  *  Field 0: Name
  *  Field 1: widgetURL
  *  Field 2: imageURL
  *  Field 3: imageURLSmall
  *  Field 4: width
  *  Field 5: height
  *
  * </pre>
  * @return the generated GUID of the new listing
  */
  def String addListing (int lineNum, String[] row) {
      // GUARD CONDITIONS
      if (!row) {
          println "Line " + lineNum + ": Not parsing empty row"
          return
      }
      if (!row[DISPLAY_NAME]) {
          println "Line " + lineNum + ": Widget Name is required"
          return
      }
      if (!row[WIDGET_URL]) {
          println "Line " + lineNum + ": WidgetURL is required"
          return
      }

      // PROCESS
      def guid = row[GUID]?:generateGuid()
      def created = false

      // This is the map-based impl - produces same output as the object-based
      def data = [
         "_method" : "POST" ,
         "displayName" : cleanStr(row[DISPLAY_NAME]),
         "url" : cleanStr(row[WIDGET_URL]),
         "imageUrlSmall" : cleanStr(row[IMAGE_URL_SMALL]),
         "imageUrlLarge" : cleanStr(row[IMAGE_URL_LARGE]),
         "width" : cleanStr(row[WIDTH], '400'),
         "height" : cleanStr(row[HEIGHT], '400'),
         "singleton" : Boolean.parseBoolean(row[SINGLETON]),
         "background" : Boolean.parseBoolean(row[BACKGROUND]),
         "visible" : Boolean.parseBoolean(row[VISIBLE]),
         "version": cleanStr(row[VERSION], '1.0'),
         "widgetGuid": cleanStr(guid),
         "universalName": cleanStr(row[UNIV_NAME]),
         "descriptorUrl": cleanStr(row[DESCRIPTOR_URL]),
         "description": cleanStr(row[DESCRIPTION])
      ]
      /* These fields not necessary::
          "tags":[]
          "user_ids":row[8]}

          Fields changed in 4.0::
          "widgetVersion"  changed to "version"
          added "singleton" and "background"
      */

      String dataStr = JSONSerializer.toJSON(data)
      try {
         int retries = 5
         while (!created && retries-- > 0) {
         rsp = post (baseUrl + "widget", ["data":dataStr])

         matcher = rsp =~ /\"success\":([\w]+)/

         if (matcher && matcher[0]) {
            val =  matcher[0][1]
            debug "\tCREATE RESPONSE: ${val}"

            created = Boolean.parseBoolean(val)
         }

         // This is just for logging
         if (!created) {
            println "ERROR: Widget creation failed with response: ${rsp}"

            // Detect message failure and retry
            //   Detect failure by GUID invalid format
            matcher = rsp =~ /Property [widgetGuid].*does not match the required pattern/
            if (matcher && matcher[0]) {
               debug "\tERROR RECEIVED:: widget GUID is not correct format: ${guid}"
            }
            else {
               //  Detect failure by GUID collision 
               matcher = rsp =~ /constraint.*widget_guid/
               if (matcher && matcher[0]) {
                  debug "\tERROR RECEIVED:: widget GUID is not correct format: ${guid}"
               }
            }
         }
         } 
      }
      catch (Exception e) {
          debug "Error creating listing: " + e
          e.printStackTrace()
          guid = null 
      }
      return guid
  } 

  /**
  * <pre>
  * Assign a new listing using details given in the row as:
  *  Field x: assignBy
  *  Field y+: list of group or user names
  *
  *  If assignBy == "groups" then followon list is taken as group names
  *  If assignBy == "users" then followon list is taken as user names
  *
  * </pre>
  * @return the generated GUID of the new listing
  */
  def String assignListing (int lineNum, String[] row, String guid) {
      // Field in CSV file must be defined
      if (!row[ASSIGN_BY]) {
          println "Line " + lineNum + ": no widget assignment specified; skipping assignment"
          return
      }

      switch (row[ASSIGN_BY]) {
          case ASSIGN_BY_GROUP:
             assignByGroup (lineNum, row[ASSIGN_BY+1..-1], guid)
             break
          case ASSIGN_BY_USER:
             throw new RuntimeException("assign by user not yet supported")
             assignByGroup (lineNum, row[ASSIGN_BY+1..-1], guid)
             break
          default:
             println "Line " + lineNum + ": widget assignment "+row[ASSIGN_BY]+" unrecognized; skipping assignment"
      }
  }

  def String assignByGroup (int lineNum, List names, String guid) {
      def ids = loadGroups(names)

      // Complete assignment
      if (ids.size() > 0) {

         ids.each {
            // Groups sub-object
            def groups = ["id" : it]
            String groupDataStr = JSONSerializer.toJSON(groups)

            def data = [
               "_method" : "PUT" ,
               "update_action" : "add" ,
               "widget_id" : guid.toString(),
               "data" : "[" + groupDataStr + "]",
               "tab" : "groups" 
            ]

            //dataStr = JSONSerializer.toJSON(data)
   
            try {
               rsp = post (baseUrl + "widget", data)

               if (rsp) {
                   matcher = rsp =~ /\"success\":([\w]+)/

                   if (matcher && matcher[0]) {
                      val =  matcher[0][1]
                      debug "\tASSIGN RESPONSE: ${val}"

                      if (!Boolean.parseBoolean(val)) {
                          println "ERROR: Assignment to group ${it} failed with response: ${rsp}"
                      }
                   }
               }
            }
            catch (Exception e) {
                debug "Error assigning listing: " + e
                e.printStackTrace()
            }
         }
      }
      else {
         debug "No group IDs identified for assignment"
      }
  }

  def String assignByUser (int lineNum, List names, String guid) {
  }

  /**
  * <pre>
  * Add a dashboard with the details given in the row as:
  *  Field 0: Name
  *  Field 1: Description
  *  Field 2: admin
  *  Field 3: group (currently must be true)
  *  Field 4: definition (ensure the CSV cell is surrounded by double-quotes)
  *  Field 5: assign by: 'group'  - only current choice
  *
  * </pre>
  * @return the generated GUID of the new listing
  */
  def String addDashboard (int lineNum, String[] row) {
      // GUARD CONDITIONS
      if (!row) {
          println "Line " + lineNum + ": Not parsing empty row"
          return
      }
      if (!row[DASH_NAME]) {
          println "Line " + lineNum + ": Dashboard Name is required"
          return
      }
      if (!row[DASH_DEFINITION]) {
          println "Line " + lineNum + ": Dashboard Definition is required"
          return
      }

      // PROCESS
      def guid = row[DASH_GUID]?:generateGuid()
      def created = false

      // Note all fields must go as Strings into the post() method data map
      def dashStr = cleanStr(row[DASH_DEFINITION])
      if (dashStr && dashStr[0] != "[") {
          dashStr = "[$dashStr]"
      }

      def postData = [
         "_method" : "POST" ,
         "adminEnabled" : Boolean.valueOf(row[DASH_ADMIN]).toString(),
         "isGroupDashboard" : Boolean.valueOf(row[DASH_GROUP]).toString(),
         "data" : dashStr
      ]


      try {
         int retries = 5
         while (!created && retries-- > 0) {
         rsp = post (baseUrl + "dashboard", postData)

         matcher = rsp =~ /\"success\":([\w]+)/

         if (matcher && matcher[0]) {
            val =  matcher[0][1]
            debug "\tCreate RESPONSE: ${val}"

            created = Boolean.parseBoolean(val)
         }

         // This is just for logging
         if (!created) {
            println "ERROR: Dashboard creation failed with response: ${rsp}"

            // Detect message failure and retry
            //   Detect failure by GUID invalid format
            matcher = rsp =~ /Property [widgetGuid].*does not match the required pattern/
            if (matcher && matcher[0]) {
               debug "\tERROR RECEIVED:: widget GUID is not correct format: ${guid}"
            }
            else {
               //  Detect failure by GUID collision 
               matcher = rsp =~ /constraint.*widget_guid/
               if (matcher && matcher[0]) {
                  debug "\tERROR RECEIVED:: widget GUID is not correct format: ${guid}"
               }
            }
         }
         } 
      }
      catch (Exception e) {
          debug "Error creating listing: " + e
          e.printStackTrace()
          guid = null 
      }
      return guid
  } 


  /**
  * <pre>
  * Assign a new listing using details given in the row as:
  *  Field x: assignBy
  *  Field y+: list of group or user names
  *
  *  If assignBy == "groups" then followon list is taken as group names
  *  If assignBy == "users" then followon list is taken as user names
  *
  * </pre>
  * @return the generated GUID of the new listing
  */
  def String assignDashboard (int lineNum, String[] row, String guid) {
      if (!row[DASH_ASSIGN_BY]) {
          println "Line " + lineNum + ": no dashboard assignment specified; skipping assignment"
          return
      }

      def assignby = cleanStr(row[DASH_ASSIGN_BY])
      debug "Assigning dashboards by: $assignby"

      switch (assignby) {
          case ASSIGN_BY_GROUP:
             assignDashByGroup (lineNum, row[DASH_ASSIGN_BY+1..-1], guid)
             break
          case ASSIGN_BY_USER:
             throw new RuntimeException("assign by user not yet supported")
             assignByGroup (lineNum, row[ASSIGN_BY+1..-1], guid)
             break
          default:
             println "Line " + lineNum + ": dashboard assignment "+row[DASH_ASSIGN_BY]+" unrecognized; skipping assignment"
      }
  }

  /**
   *  Load the groups for the given List of group names
   */
  def loadGroups (List names) {
      def ids = []

      // For each Group
      names.each {
         debug ("## Looking up Group: " + it)

         filters = [
               "filterField" : "name",
               "filterValue" : it.toString() ]
         String filterStr = JSONSerializer.toJSON(filters)

         // Look up Group given the name via a search
         //   Filters field must be a JSON string
         def data = [
            "max" : "3",
            "_method" : "GET" ,
            "order" : "ASC" ,
            "sort" : "name",
            "filters" : "[" + filterStr + "]"
         ]

         //String dataStr = JSONSerializer.toJSON(data)
         try {
            // Send each parameter individually 
            rsp = post (baseUrl + "group", data)

            debug "Matching on response:: ${rsp}"

            // Find number of results
            matcher = rsp =~ /\"results\":([\d]+)/
            if (matcher && matcher[0]) {
                int numGroups =  Integer.parseInt(matcher[0][1])
                debug "\tNUM MATCHING GROUPS FOUND: ${numGroups}"

                if (numGroups > 0) {
                   // Pull ID out of response
                   //matcher = rsp =~ /\"id\":([\d]+)/
                   matcher = rsp =~ /\"id\":([\d]+),\"totalWidgets\":[\d]+,\"name\":"${it}"/

                   if (matcher && matcher[0]) {
                      id =  matcher[0][1]
                      debug "\tFOUND GROUP ID: ${id}"

                      if (id) {
                         // Add ID to update string
                         ids.add(id)
                      }
                      else {
                         println "Valid Id not found for group ${it}"
                      }
                   }
                   else {
                      debug "## Group ID for name ${it} could not be parsed out of: ${rsp}"
                   }
                }
                else {
                    debug "## No groups matched name: ${it}"
                }

                //}
            }
            else {
                debug "## Unable to process group lookup; empty response returned"
            }
         }
         catch (Exception e) {
             debug "Error creating listing: " + e
             e.printStackTrace()
         }
      }

      // Located group ID list?
      debug "\tLOCATED GROUP IDs: ${ids}"
      return ids
  }

If a stack is deleted by an administrator, the user will no longer have a copy of the stack and the dashboards and widgets included in the stack.
  def String assignDashByGroup (int lineNum, List names, String guid) {
      def ids = loadGroups(names)

      // Complete assignment
      if (!ids || ids.size() <= 0) {
         debug "No group IDs identified for assignment"
         return
      }

      // Just pull the id fields out into a JSON list, that should be enough
      def groups = new net.sf.json.JSONArray()
      ids.each {
         groups.add(JSONSerializer.toJSON([id : Integer.parseInt(it)]))
      }

      def groupStr = groups.toString()
      debug "GROUP JSON: $groupStr"

      def data = [
         "_method" : "PUT" ,
         "tab" : "groups",
         "update_action" : "add",
         "dashboard_id" : guid.toString(),
         "data" :  groupStr 
      ]
   
      try {
         rsp = post (baseUrl + "dashboard", data)

         if (rsp) {
            matcher = rsp =~ /\"success\":([\w]+)/

            if (matcher && matcher[0]) {
               val =  matcher[0][1]
               debug "\tASSIGN RESPONSE: ${val}"

               if (!Boolean.parseBoolean(val)) {
                  println "ERROR: Assignment to group ${it} failed with response: ${rsp}"
               }
            }
         }
      }
      catch (Exception e) {
         debug "Error assigning listing: " + e
         e.printStackTrace()
      }
  }


  def post(url, paramMap) {
      HttpPost httpPost = new HttpPost(url)
      String result
      /*

      This parameter style does not seem to be used by HttpClient; odd
         not what their examples use....

      // These params always present
      httpPost.getParams().setParameter("_method", "POST")
      httpPost.getParams().setParameter("owfversion", "3.6.0-GA")
      httpPost.getParams().setParameter("dojo.preventCache", "1302893700594")
      */

      // Add specific params
      /*
      for (e in paramMap) {
         String v = paramMap.get(e)
         httpPost.getParams().setParameter(e, JSONSerializer.toJSON(v))
      }
      */

      List <NameValuePair> nvps = new ArrayList <NameValuePair>();
      // These params always present
      nvps.add(new BasicNameValuePair("owfversion", "4.0.0-GA"))
      nvps.add(new BasicNameValuePair("dojo.preventCache", "1302893700594"))

      paramMap.each() { key, value ->
         debug "${key} ==> ${value}"
         // Expect the caller to JSON-ize each param, as required 
         //httpPost.getParams().setParameter(key, value)
         if (value instanceof List) {
             // Add value multiple times with same key
            value.each {
               nvps.add(new BasicNameValuePair(key, it.toString()))
            }
         }
         else {
            nvps.add(new BasicNameValuePair(key, value))
         }
      }

      httpPost.setEntity(new UrlEncodedFormEntity(nvps, HTTP.UTF_8))

      // Debug
      debug(dump(httpPost))

      debug "Posting request: " + httpPost
      HttpResponse response = httpclient.execute(httpPost, localContext);
      //String response = httpclient.execute(httpPost, new BasicResponseHandler());  

      debug "#### RESPONSE TYPE: " + response.class.name

      HttpEntity entity = response.getEntity();

      debug "#### RESPONSE BODY RECEIVED: " + entity.toString()

      // ** Dump results **
      debug "\t" + response.getStatusLine()  
      StringBuilder sb = new StringBuilder(255)
      if (entity != null) {

         /*
         // this failed to get any content at all

         InputStream ris = entity.getContent()
         BufferedReader r = new BufferedReader(new InputStreamReader (ris))

         while (r.ready()) {
             sb.append(r.readLine())
         }
         r.close()

         BufferedReader r = new BufferedReader(new InputStreamReader (entity.getContent()))
         while (!entity.eofDetected(ris)) {
             sb.append(r.readLine())
         }

         */

         InputStream ris = entity.getContent()
         OutputStream os = new ByteArrayOutputStream(BUFFER_SIZE)
         byte[] buffer = new byte[BUFFER_SIZE];
         try {
            while ((l = ris.read(buffer)) != -1) {
                os.write(buffer, 0, l)
                sb.append(os.toString())
                os.reset()
            }
         } finally {
             ris.close()
         }

         debug "\tEntity contentLength: " + sb.length()

         result = sb?.toString()

         debug "\t-------------------------------------"
         List<Cookie> cookies = cookieStore.getCookies();
         for (int i = 0; i < cookies.size(); i++) {
            println("\tCookie: " + cookies.get(i));
         }                                                                                      
         debug "\t-------------------------------------"

      }
      else {
          println "Error connecting...check connection details"
          //!! EXIT HERE
          return
      }

      debug "-------------------------------------"
      if (result && result.size() > 256) {
         debug "RESPONSE: " + result.substring(0, 256)
      }
      else {
         debug "RESPONSE: " + result
      }
      debug "-------------------------------------"

      return result
  }


  /** 
  *  Return a Keystore from a given filename and password
  */
  def KeyStore createKeystore (filename, password) {
      debug "Default KeyAlgo: " + KeyManagerFactory.getDefaultAlgorithm() 
      debug "Default KeyStore: " + KeyStore.getDefaultType()  // jks 

      // Pull off file extension
      keyType = filename[-3..-1]
      debug "Requested KeyType: " + keyType 

      // Client keystore, for client authentication
      KeyStore keys  
      switch (keyType) {
         case "p12":
            keys  = KeyStore.getInstance("pkcs12");
            break
         default:
            keys  = KeyStore.getInstance(KeyStore.getDefaultType());
      }

      FileInputStream instream = new FileInputStream(new File(filename));
      try {
         keys.load(instream, password.toCharArray());
      } finally {
         try { instream.close(); } catch (Exception ignore) {}
      }
      return keys
   }

   def parseInput(args) {
       if (!args || args.length < 1) {
           usage()
       }
       for(int i=0; i < args?.length; i++) {
           it = args[i]
           println "Processing arg: " + it
           switch (it) {
           case "-v":
               debug "Verbose output enabled " 
               config.verbose = true
               break
           case "-h":
               it = args[++i]
               debug "Setting Host/Port from: $it"
               // Parse out host and port
               def serverAddr = it.split(":")
               try {
                  if (serverAddr.length > 0 && serverAddr[0] != null) {
                     config.host = serverAddr[0] 
                  }
                  if (serverAddr.length > 1 && serverAddr[1] != null) {
                     config.port = serverAddr[1] 
                  }
               } catch(Exception e) {
                   usage()
               }
               break
           case "-clientKeys":
               it = args[++i]
               debug "Setting client keyfile to: $it"
               config.clientKeyFile = it 
               break
           case "-clientKeyPass":
               it = args[++i]
               debug "Setting client keyfile pwd to: $it"
               config.clientKeyPass = it 
               break
           case "-serverKeys":
               it = args[++i]
               debug "Setting server keyfile to: $it"
               config.serverKeyFile = it 
               break
           case "-serverKeyPass":
               it = args[++i]
               debug "Setting server keyfile pwd to: $it"
               config.serverKeyPass = it 
               break
           case "-c":
              // same as -context
           case "-context":
               it = args[++i]
               debug "Setting context to: $it"
               config.ctx = it 
               break
           case "-testOnly":
               debug "Entering testOnly mode" 
               config.testOnly = true 
               break
           case "-widgets":
               it = args[++i]
               debug "Setting widget file to: $it" 
               config.widgetFile = it;
               break
           case "-dashboards":
               it = args[++i]
               debug "Setting dashboard file to: $it" 
               config.dashboardFile = it;
               break
           default:
               println "Unknown parameter given: $it"
           }
       }
   }

   String[] getCsvCells(String row) {
      // cannot merely split as this does not respect cells containing commas
      //row.split(',')
      def cur = 0, len = row.size(), curToken = "", inBlock = false
      def cells = []
      while (cur < len) {
         //println "$cur: ${row[cur]}"
         if (row[cur] == "\"") {
            //println "found quote"
            // quotes begin or end a block
            // read ahead to see if this is an escaped quote
            if ((cur+1 < len) && row[cur+1] == "\"") {
               // escaped quote - skip the escape and add one
               //println "including quote"
               curToken +=  "\""
               ++cur
            }
            else if (inBlock) {
               // exiting block
               inBlock = false
            }
            else {
               // start block
               inBlock = true
            }
         }
         else if (!inBlock && row[cur] == ",") {
            // comma terminates current field
            //println "next cell: $curToken"
            cells << curToken.toString()
            curToken = ""
         }
         else {
            //println "adding char"
            curToken += row[cur]
         }
         ++cur
      }
      if (curToken) cells << curToken.toString()
      return cells
   }
   


   String cleanStr(def ins, def defval='') {
      if(!ins) return defval
      return ins.trim()
   }

   // This is taken directly from OWF js code
   String generateGuid() {
      return (S4()+S4()+"-"+S4()+"-"+S4()+"-"+S4()+"-"+S4()+S4()+S4());
   }

   String S4() {
      // This was the original
      //return (((1+Math.random())*0x10000)|0).toString(16).substring(1);
      def v = (Math.random())
        //debug "VAL: ${v}" 
      v = (int) ((v)*0x1000000)
        //debug "VAL: ${v}" 
      v = (int)v|0
        //debug "VAL: ${v}" 
      v = Integer.toHexString((int)v)
        //debug "VAL: ${v}" 
      v = v[v.length()-4..-1]
        //debug "VAL: ${v}" 
      return v
   }

   def usage() {
       println "\nUsage: load.groovy [-v] [-h <host>:[<port>]] [-clientKeys <keyfile>] [-clientKeyPass <password>] [-serverKeys <keyfile>] [-serverKeyPass <password>] [-widgets <filename>] [-dashboards <filename>]"
       println "\twhere: "
       println "\t <filename> - a CSV text file containing widget definitions"
       println "\t -h <host>:<port> - host location of the OWF server; optionally specify the port number, separated by a colon"
       println "\t [-c | -context] <contextName> - optional context name of the OWF server; default is 'owf'"
       println "\t -clientKeys <file> - keyfile for client authentication to OWF; currently must be PKCS12 (.p12)"
       println "\t -clientKeyPass <password> - password for client keyfile"
       println "\t -dashboards - treat the given file as a dashboard CSV file"
       println "\t -serverKeys <file> - certificates for server validation; currently must be a JKS keystore"
       println "\t -serverKeyPass <password> - password for server keystore"
       println "\t -v - verbose output"
       println "\t -widgets - treat the given file as a widget CSV file"
       System.exit(0)
   }

   def debug(stmt) {
       if (config.verbose) {
           println stmt
       }
   }

   def dump(HttpPost post) {
       StringBuilder sb = new StringBuilder(128)

       sb.append("\n\t------------------------------")
       sb.append("\n\tRequestURL: ").append(post.getURI())
       sb.append("\n\tType: ").append(post.getMethod())
       sb.append("\n\tParams: ").append(post.getParams())
       org.apache.http.params.BasicHttpParams
       sb.append("\n\t------------------------------")

       return sb.toString()
   }
           
  class Config {
      String host = "localhost"
      String port = "8443"
      boolean verbose = false
      String widgetFile 
      String dashboardFile 
      String clientKeyFile
      String clientKeyPass
      String serverKeyFile
      String serverKeyPass
  }

  class Data {
      String displayName 
      String widgetUrl 
      String imageUrlSmall 
      String imageUrlLarge 
      String width 
      String height 
  }

