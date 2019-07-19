import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Properties;
import java.util.StringTokenizer;
import java.util.UUID;

import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

public class Server implements Runnable
{
   public static final int PORT = 8080, CODE_CHANGE = 55, CODE_ARM = 65, CODE_DISARM = 75, CODE_SOUND_ALARM = 45, CODE_MUTE_ALARM = 35;
   public static final String ROOT = ".", DEFAULT_FILE = "index.html", INVALID_CODE_MESSAGE = "Code must be 5 digits.",
                              CODE_SET_MESSAGE = "Code set.", NOT_AUTH_MESSAGE = "Login required";
   public static HashMap<String, Session> sessions = new HashMap<String, Session>();
   public static HashMap<String, Esp> esps = new HashMap<String, Esp>();
   private static ServerGUI gui;
   private Esp esp;
   private Socket client;
   private String sessionId = "";
   private static boolean ledOn = false, authenticated = false;
   private volatile boolean running = true;
   enum MessageType 
   {
      INFO, WARNING, ERROR, DEBUG
   }
   
   public static void main(String[] args)
   {
      log("Starting Server...", MessageType.INFO);
      MySqlClient.startClient(); // Starts connection to MySQL database
      gui = new ServerGUI();     // Starts GUI window
      new Thread(gui).start(); // Launches server GUI
      listen();
   }
   
   /**
    * This method is where the server listens for new connections
    */
   public static void listen()
   {
      
      // Creates a ServerSocket to listen for clients
      try(ServerSocket serverSocket = new ServerSocket(PORT))  
      {
         log("Listening on port " + serverSocket.getLocalPort(), MessageType.INFO);
         while (true) 
         {
            Server s = new Server(serverSocket.accept()); // Hangs here until a connection is made
            Thread t = new Thread(s); // Creates a new thread for the server to communicate with the client on
            t.start();
         }
      }
      catch (Exception e)
      {
         log("Server connection error: " + e.getMessage(), MessageType.ERROR);
      }
   }

   public Server(Socket client)
   {
      this.client = client;
   }

   @Override
   public void run()
   {
      log("Client connection made", MessageType.INFO);
      BufferedReader in = null; // To receive data from client
      PrintWriter out = null;          // To send characters to the client
      BufferedOutputStream dataOut = null;    // To send data to the client     
      boolean ranAlready = false;
      while((esp != null && client.isConnected()) || (esp == null && !ranAlready))
      {
         if(!running)
            return;
         
         ranAlready = true;
         try 
         {
            in = new BufferedReader(new InputStreamReader(client.getInputStream())); // To receive data from client
            out = new PrintWriter(client.getOutputStream());                            // To send characters to the client
            dataOut = new BufferedOutputStream(client.getOutputStream());      // To send data to the client  

            String input = in.readLine(); // Reads line from client
            if(input == null)
               continue;
            StringTokenizer parse = new StringTokenizer(input);
            String cmd = "DEFAULT", message = "DEFAULT_MESSAGE";
            int contentLength = 0;
            if(parse.hasMoreTokens())
               cmd = parse.nextToken().toUpperCase(); // Get command
            
            // Reads all lines of webpage header and finds cookies
            if(!cmd.equals("ESP")) // If the line starts with ESP it is communication from the esp
            {
               String line = "";
               boolean sessionIdRead = false;
               while(!(line = in.readLine()).equals("")) // Blank line signifies end of header
               {
                  if(line.contains("Cookie: "))
                  {
                     String[] cookies = line.split(":")[1].split(";");
                     for(String s : cookies)
                     {
                        if(s.trim().split("=")[0].equals("sessionId"))
                        {
                           String idTemp = s.split("=")[1];
                           if(sessions.get(idTemp) != null && !sessions.get(idTemp).expired())
                           {
                              sessions.get(idTemp).resetLastActiveDate();
                              sessionId = idTemp;
                              sessionIdRead = true;
                           }
                        }
                     }
                  }
                  else if(line.toLowerCase().contains("content-length"))
                  {
                     contentLength = Integer.parseInt(line.split(" ")[1]);
                  }
               }
               
               if(contentLength != 0)
                  message = "";
               
               for(int i = 0; i < contentLength; i++)
               {
                  message += (char) in.read();
               }
               
               // If the user doesn't have a session id, assign a new one
               if((!sessionIdRead && sessionId.equals("")) || sessions.get(sessionId) == null || sessions.get(sessionId).expired())
               {
                  String newId = getNewSessionId();
                  sessionId = newId;
                  sessions.put(newId, new Session(newId));
               }
               
               sessions.get(sessionId).resetLastActiveDate();
               authenticated = sessions.get(sessionId).isAuthenticated();
            }
            
            // If a get command is sent, send a file
            if(cmd.equals("GET"))
            {
               String fileRequested = "";
               
               // A questionmark in the url indicates parameters were sent
               if(input.split("\\?").length == 2)
               {
                  fileRequested = input.split("\\?")[0].split(" ")[1];
                  String[] parameters = input.split("\\?")[1].split("&");
                  parameters[parameters.length - 1] = parameters[parameters.length - 1].split(" ")[0]; // Get rid of 'HTTP/1.1' for last parameter
                  for(String p : parameters)
                  {
                     String[] keyValPair = p.split("=");
                     if(keyValPair.length == 2 && sessions.get(sessionId) != null)
                     {
                        System.out.println("key: " + keyValPair[0]);
                        sessions.get(sessionId).parameters.put(keyValPair[0], keyValPair[1]);
                     }
                  }
                  // log("Parameters sent: " + Arrays.toString(parameters), MessageType.DEBUG);
               }
               else 
                  fileRequested = parse.nextToken().toLowerCase();  // Next token is file requested
               
               if(fileRequested.endsWith("/")) // Default file ends with just '/'
               {
                  fileRequested = File.separator + "" + DEFAULT_FILE;
               }
               
               File f = new File(ROOT, fileRequested);
               
               String viewDoorsString = "", viewLogsString = "", 
                      tableStyle = "table{font-family: arial, sans-serif; border-collapse: collapse;width: 75%;}"
                     + "td, th {border: 1px solid #dddddd; text-align:center; padding: 8px;}"
                     + "tr:nth-child(even) { background-color:#dddddd}";;
               // If the user is trying to view doors, a table is constructed and sent to them
               if((fileRequested).equals("/viewdoors.html") && sessions.get(sessionId) != null && sessions.get(sessionId).isAuthenticated()
                  && sessions.get(sessionId).getUser() != null)
               {
                  viewDoorsString += "<html>\n";
                  // Style for the table
                  // FIXME: This shouldn't be hard-coded
                  viewDoorsString += "<style>" + tableStyle;
                  // Style for dots
                  viewDoorsString += ".dot {height: 25px;width: 25px;background-color: #FF4000;border-radius: 50%;"
                  + "display: inline-block;}</style>";
                  viewDoorsString += "<script>";
                  /*viewDoorsString += "function loadManagePage(mac) { var request = new XMLHttpRequest(); "
                           + "request.open(\"GET\", \"http://192.168.1.10:8080/control.html?mac=\" + mac, false);"
                           + "request.setRequestHeader(\"Content-Type\", \"text/plain\");"
                           + "request.send(null);}");*/
                  viewDoorsString += "function loadManagePage(mac) {window.location.assign(\"http://192.168.1.10:8080/control.html?mac=\" + mac, '_self');}";
                  viewDoorsString += "function loadLogs(mac) {window.location.assign(\"http://192.168.1.10:8080/viewLogs.html?mac=\" + mac, '_self');}";
                  viewDoorsString += "</script>";
                  viewDoorsString += "<table style=\"float: right\">\n";
                  viewDoorsString += "<tr>\n<th>Online</th>\n<th>Options</th>\n<th>MAC Address</th>\n<th>Name</th>\n<th>Location</th>\n<th>Building</th>\n<th>Company</th>\n</tr>";
                  ArrayList<Door> doors = MySqlClient.getAllDoors("COMPANY = '" + sessions.get(sessionId).getUser().getCompany() + "'");
                  for(Door d : doors)
                  {
                     String color = "#FF4000"; // Default color is red for offline
                     if(esps.get(d.getMacAddress()) != null && esps.get(d.getMacAddress()).online())
                        color = "#2FBA10";
                     String dot = "<span class=\"dot\" style=\"background-color: " + color + "\"></span>";
                     // Writes all of the table rows
                     viewDoorsString += "<tr>\n<td>" + dot + "</td>\n";
                     viewDoorsString += "<td><button style=\"margin-right: 5px;\"  onClick=\"loadManagePage('" + d.getMacAddress()
                               + "')\">Manage</button><button onClick=\"loadLogs('" + d.getMacAddress() + "')\">View Logs</button></td>\n";
                     viewDoorsString += "<td>" + d.getMacAddress() + "</td>\n";
                     viewDoorsString += "<td>" + d.getName() + "</td>\n";
                     viewDoorsString += "<td>" + d.getLocation() + "</td>\n";
                     viewDoorsString += "<td>" + d.getBuilding() + "</td>\n";
                     viewDoorsString += "<td>" + d.getCompany() + "</td>\n</tr>\n";
                  }
                  viewDoorsString += "</table>\n</html>";
               }
               else if((fileRequested).equals("/viewLogs.html") && sessions.get(sessionId) != null && sessions.get(sessionId).isAuthenticated()
                     && sessions.get(sessionId).getUser() != null)
               {
                  viewLogsString += "<html>\n";
                  viewLogsString += "<style>" + tableStyle + "</style>";
                  viewLogsString += "<body>";
                  viewLogsString += "<table style=\"float: right\">\n";
                  viewLogsString += "<tr><th>Time Stamp</th><th>Event Type</th><th>Mac Address</th><th>Code Used</th><th>Employee</th></tr>";
                  ArrayList<Log> logs = MySqlClient.getAllLogs("doorMacAddress='" + sessions.get(sessionId).parameters.get("mac") + "'");
                  for(Log l : logs)
                  {
                     viewLogsString += "<tr>";
                     viewLogsString += "<td>" + l.getTimeStamp() + "</td>";
                     viewLogsString += "<td>" + l.getEventType() + "</td>";
                     viewLogsString += "<td>" + l.getDoorMacAddress() + "</td>";
                     viewLogsString += "<td>" + l.getCodeUsed() + "</td>";
                     viewLogsString += "<td>" + l.getEmployee() + "</td>";
                     viewLogsString += "</tr>";
                  }
                  viewLogsString += "</table>";
                  viewLogsString += "<a href=\"javascript:history.back()\" style=\"float: right\"><--Back</a>";
                  viewLogsString += "</body></html>";
               }
               
               int length;
               byte[] data;
               if(viewDoorsString.equals("") && viewLogsString.equals(""))
               {
                  data = readFile(f);
                  length = (int) f.length();
               }
               else if(!viewDoorsString.equals(""))
               {
                  data = viewDoorsString.getBytes();
                  length = viewDoorsString.getBytes().length;
               }
               else
               {
                  data = viewLogsString.getBytes();
                  length = viewLogsString.getBytes().length;
               }
               
               log("File " + f.getAbsolutePath() + " requested by " + client.getInetAddress(), MessageType.INFO);
               
               // Sends the header
               // If the client is authenticated or trying to get the login page or favicon then proceed
               if((sessions.get(sessionId) != null && sessions.get(sessionId).isAuthenticated()) || fileRequested.equals(File.separator + DEFAULT_FILE) || fileRequested.equals("/favicon.ico"))
               {
                  out.println("HTTP/1.1 200 OK"); // OK header
                  out.println("Date: " + new Date());
                  out.println("Content-type: text/html");
                  out.println("Content-length: " + length);
                  out.println("Set-Cookie: sessionId=" + sessionId);
                  out.println();
                  out.flush();

                  // Sends the actual file
                  dataOut.write(data, 0, length);
                  dataOut.flush();
               }
               else if(!authenticated)
               {
                  sendLoginPage(out, dataOut);
               }
            }
            else if(cmd.equals("POST"))
            {
               log("POST Received.", MessageType.DEBUG);
               log("POST Message: " + message, MessageType.DEBUG);
               
               if(message.contains("pass=") && message.contains("user="))
               {
                  String userTry = message.split(";")[0].split("=")[1],
                         passTry = message.split(";")[1].split("=")[1];
                  // User tries to login, login returns null if failed
                  if(MySqlClient.login(userTry, passTry) != null) 
                  {
                     log("User logged in with username: " + userTry, MessageType.INFO);
                     authenticated = true;
                     sessions.get(sessionId).setAuthenticated(true);
                     sessions.get(sessionId).setUser(MySqlClient.getUser(userTry));
                     sendOKHeader(out, sessionId);
                  }
                  else
                  {
                     // Failed to log in
                     log("User failed to login with username: " + userTry, MessageType.INFO);
                     sendBadRequestHeader(out, NOT_AUTH_MESSAGE.length());
                     dataOut.write(NOT_AUTH_MESSAGE.getBytes());
                     dataOut.flush();
                  }
               }
               else
               {
                  // A message was sent from a client that has not logged in
                  if(!authenticated && esp == null)
                  {
                     sendLoginPage(out, dataOut); // Send the user to the login page
                  }
                  else
                  {
                     // Send OK header to confirm post received and user is authorized
                     sendOKHeader(out, sessionId);
                     handleInput(message, out, dataOut);
                  }
               }
            }
            // Esp comminucation begins with 'ESP'
            else if(cmd.equals("ESP"))
            {
               // Gets the mac address
               String mac = "";
               if(parse.hasMoreTokens())
                  mac = parse.nextToken();
               
               if(esps.get(mac) == null || (esps.get(mac) != null && !esps.get(mac).online()))
                  log("Esp connected: " + mac, MessageType.INFO);
               
               if(esps.get(mac) == null && !mac.equals(""))
                  esps.put(mac, new Esp(mac, this.client));
               else if(esps.get(mac) != null)
                  esps.get(mac).resetLastOnline();
               
               esp = esps.get(mac);
               
               // Gets the message if one exists
               String espMessage = "";
               if(parse.hasMoreTokens())
                  espMessage = parse.nextToken().toUpperCase();
               
               // If the message is 'TRIG' the alarm was triggered
               if(espMessage.equals(">TRIG"))
               {
                  log("Alarm Triggered", MessageType.INFO);
                  try
                  {
                     log("Sending email alert...", MessageType.INFO);
                     // FIXME: Uncomment this
                     // sendAlarmTriggeredEmail();
                     log("Done.", MessageType.INFO);
                     
                     MySqlClient.createLogEntry("ALARM TRIGGERED", mac, "N/A", "N/A");
                  }
                  catch(Exception e)
                  {
                     log("Could not send email: " + e.getMessage(), MessageType.ERROR);
                  }
               }
               else if(espMessage.equals(">R:CODE_CHANGE"))
               {
                  MySqlClient.createLogEntry("CODE CHANGE", mac, "REMOTE", "REMOTE");
               }
               else if(espMessage.length() > 12 && espMessage.substring(0, 12).equals(">CODE_CHANGE"))
               {
                  MySqlClient.createLogEntry("CODE CHANGE", mac, espMessage.substring(12), "UNIMPLEMENTED");
               }
               else if(espMessage.length() >= 6 && espMessage.substring(0, 6).equals(">ARMED"))
               {
                  String code = "N / A";
                  if(espMessage.length() > 6)
                     code = espMessage.substring(6);
                  MySqlClient.createLogEntry("ALARM ARMED", mac, code, "UNIMPLEMENTED");
               }
               else if(espMessage.length() >= 9 && espMessage.substring(0, 9).equals(">DISARMED"))
               {
                  String code = "N / A";
                  if(espMessage.length() > 9)
                     code = espMessage.substring(9);
                  MySqlClient.createLogEntry("ALARM DISARMED", mac, code, "UNIMPLEMENTED");
               }
               else if(espMessage.equals(">OPENED"))
               {
                  MySqlClient.createLogEntry("DOOR OPENED", mac, "N/A", "N/A");
               }
               else if(espMessage.equals(">CLOSED"))
               {
                  MySqlClient.createLogEntry("DOOR CLOSED", mac, "N/A", "N/A");
               }
            }
            else
            {
               log("Unsupported command: " + cmd, MessageType.WARNING);
            }
         }
         catch(IOException e)
         {
            log("IO exception during client connection.\n" + e.getMessage(), MessageType.ERROR);
            running = false;
            System.out.println(e.getCause());
            System.out.println(esp == null);
            if(esp != null)
            {
               System.out.println(esp.getMacAddress());
               try
               {
                  esps.remove(esp.getMacAddress());
                  esp.getSocket().close();
               } 
               catch (IOException e1)
               {
                  log("Could not close esp socket socket", MessageType.ERROR);
               }
            }
         }
         finally 
         {
            // Close web resources only and not esp one's as those should be saved
            if(esp == null)
            {
               try
               {
                  if(in != null)
                     in.close();
                  if(out != null)
                     out.close();
                  if(dataOut != null)
                     dataOut.close();
               } catch (IOException e)
               {
                  // TODO Auto-generated catch block
                  e.printStackTrace();
               }
            }
            // log("Client connection closed.");
         }
      }
   }
   
   /**
    * Used to send a redirect to the login page
    * @param out
    * @param dataOut
    */
   public void sendLoginPage(PrintWriter out, BufferedOutputStream dataOut)
   {
      File defaultFile = new File(ROOT, DEFAULT_FILE);
      byte[] b = readFile(defaultFile);
      
      out.println("HTTP/1.1 401 UNAUTHORIZED");
      out.println("Set-Cookie: sessionId=" + sessionId);
      out.println("Date: " + new Date());
      out.println("Content-type: text/html");
      out.println("Content-length: " + (int) defaultFile.length());
      out.println();
      out.flush();
      
      try
      {
         dataOut.write(b, 0, (int) defaultFile.length());
         dataOut.flush();
      }
      catch (IOException e)
      {
         log("Error sending login page: " + e.getMessage(), MessageType.ERROR);
      }
   }
   
   public byte[] readFile(File f)
   {
      int length = (int) f.length();
      byte[] data = new byte[length];

      // Turns the file into a byte array to send
      try
      (
            FileInputStream fileIn = new FileInputStream(f);
      )
      {
         fileIn.read(data); // Read the data into the byte array
      }
      catch(IOException e1)
      {
         log("Error converting file to byte array.", MessageType.ERROR);
         log("File name:  " + f.getName(), MessageType.ERROR);
         log("File exists: " + f.exists(), MessageType.ERROR);
         log(e1.getMessage(), MessageType.ERROR);
      }
      return data;
   }

   public void handleInput(String input, PrintWriter out, BufferedOutputStream dataOut) throws IOException
   {
      // Gets the mac address of the Esp that we are trying to communicate with
      // checks if an esp with that mac address exists and is connected
      Esp currentEsp = null;
      if(sessions.get(sessionId) != null && esps.get(sessions.get(sessionId).parameters.get("mac")) != null
         && esps.get(sessions.get(sessionId).parameters.get("mac")).getSocket().isConnected())
         currentEsp = esps.get(sessions.get(sessionId).parameters.get("mac"));
      else
      {
         log("Warning: Esp not connected! ", MessageType.WARNING);
         return;
      }
      
      // toggle led
      if(input.equals("toggle"))
      {
         // Send signal to ep8266
         if(!ledOn)
            sendMessageToEsp("H", currentEsp);
         else
            sendMessageToEsp("L", currentEsp);
         ledOn = !ledOn;
      }
      else if(input.split(" ").length == 2 && input.split(" ")[0].equals(CODE_CHANGE + ""))
      {
         log("Attempting pass code change...", MessageType.INFO);
         // Makes sure the code is within guidelines (5 numbers only, no letters)
         // The webpage makes sure there are no non-numbers
         String newCode = input.split(" ")[1];

         // Makes sure the code is only digits
         for(int i = 0; i < newCode.length(); i++)
         {
            if(!Character.isDigit(newCode.charAt(i)))
            {
               sendBadRequestHeader(out);

               dataOut.write(INVALID_CODE_MESSAGE.getBytes());
               dataOut.flush();
            }
         }

         if(newCode.length() != 5)
         {
            // Tell the website that the code length is wrong
            sendBadRequestHeader(out, INVALID_CODE_MESSAGE.length());

            dataOut.write(INVALID_CODE_MESSAGE.getBytes());
            dataOut.flush();
         }
         else
         {
            // Tell the website the code was set
            sendOKHeader(out, sessionId);
            
            // Sends input to esp
            sendMessageToEsp(input, currentEsp);
            
            dataOut.write(CODE_SET_MESSAGE.getBytes());
            dataOut.flush();
         }
      }
      else if(input.length() == 2 && input.substring(0, 2).equals(CODE_DISARM + ""))
      {
         sendMessageToEsp(input, currentEsp);
      }
      else if(input.length() == 2 && input.substring(0, 2).equals(CODE_ARM + ""))
      {
         sendMessageToEsp(input, currentEsp);
      }
      else if(input.length() == 2 && input.substring(0, 2).equals(CODE_MUTE_ALARM + ""))
      {
         sendMessageToEsp(input, currentEsp);
      }
      else if(input.length() == 2 && input.substring(0, 2).equals(CODE_SOUND_ALARM + ""))
      {
         sendMessageToEsp(input, currentEsp);
      }
   }

   // This method attempts to send a message to the wifi module
   public void sendMessageToEsp(String message, Esp espToSendTo)
   {
      // Makes sure the message ends in new line
      if(message.charAt(message.length() - 1) != '\n')
         message += "\n";
      log("Sending message to esp...", MessageType.INFO);
      try
      {
         if(espToSendTo != null && espToSendTo.getOutputStream() != null && espToSendTo.getSocket().isConnected())
         {
            espToSendTo.getOutputStream().write(message.getBytes());
            espToSendTo.getOutputStream().flush();
            log("Message sent to esp: " + message, MessageType.DEBUG);
         }
         else
            log("ERROR: Esp output stream is null or esp not connected!", MessageType.ERROR);
      }
      catch(IOException e)
      {
         log("ERROR: Failed to send signal to esp!\n" + e.getMessage(), MessageType.ERROR);
      }
   }

   // Sends a bad request (400) header
   public static void sendBadRequestHeader(PrintWriter out)
   {
      sendBadRequestHeader(out, 0);
   }
   
   public static void sendBadRequestHeader(PrintWriter out, int dataLength)
   {
      out.println("HTTP/1.1 400 Bad Request");
      out.println("Date: " + new Date());
      out.println("Content-type: text/plain");
      if(dataLength > 0)
         out.println("Content-Length: " + dataLength);
      out.println();
      out.flush();
   }
   
   // Sends an OK (200) header
   public static void sendOKHeader(PrintWriter out, String session)
   {
      out.println("HTTP/1.1 200 OK"); 
      out.println("Date: " + new Date());
      out.println("Content-type: text/plain");
      out.println("Set-Cookie: sessionId=" + session);
      out.println();
      out.flush();
   }

   public static synchronized void log(String message, MessageType type)
   {
      Date d = new Date();
      String m = "[" + d.toString() + "][" + type + "] " + message;
      try
      {
         // Writes the log file to a file in path: logs/[year]/[month]/[date].txt
         File f = new File(".", "log.txt");
         if(!f.exists())
            f.createNewFile();
         FileWriter fr = new FileWriter(f, true);
         fr.write(m + "\n");
         fr.close();
      }
      catch(IOException e)
      {
         System.out.println("Could not write log message to file: " + e.getMessage());
      }
      System.out.println(m); // Prints message to console
      if(gui != null)
         gui.updateLog(m);   // Prints message to gui
   } 
   
   /**
    * Creates a new session ID
    * TODO: Create more complicated session ids
    * @return
    */
   public static String getNewSessionId()
   {
      return UUID.randomUUID().toString();
   }

   /*
    * Sends an email saying the alarm was triggered
    */
   public static void sendAlarmTriggeredEmail() throws MessagingException
   {
      // Email data
      String host = "smtp.aol.com";
      String user = "alarm_system539";
      String pass = "ZcCaXa8rRbsHpH7";
      String to = "naphidmc@gmail.com";
      String from = "alarm_system539@aol.com";
      String subject = "Alarm Alert";
      String messageText = "Alarm has been triggered at " + new Date();
      boolean sessionDebug = false;

      // SMTPS properties
      Properties props = System.getProperties();
      props.put("mail.host", host);
      props.put("mail.transport.protocol", "smtps");
      props.put("mail.smtps.auth", "true");
      props.put("mail.smtps.port", "465");
      props.put("mail.smtps.ssl.trust", host);
      javax.mail.Session mailSession = javax.mail.Session.getDefaultInstance(props, null);
      mailSession.setDebug(sessionDebug);
      Message msg = new MimeMessage(mailSession);
      msg.setFrom(new InternetAddress(from));
      InternetAddress[] address = {new InternetAddress(to)};
      msg.setRecipients(Message.RecipientType.TO, address);
      msg.setSubject(subject);
      msg.setSentDate(new Date());
      msg.setText(messageText);
      Transport transport = mailSession.getTransport("smtps");
      transport.connect(host, user, pass);
      transport.sendMessage(msg, msg.getAllRecipients());
      transport.close();
   }
}
