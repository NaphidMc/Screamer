import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Date;
import java.util.Properties;
import java.util.StringTokenizer;

import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

public class Server implements Runnable
{
   public static final int PORT = 8080, CODE_CHANGE = 55, CODE_ARM = 65, CODE_DISARM = 75, CODE_SOUND_ALARM = 45, CODE_MUTE_ALARM = 35;
   public static final String ROOT = ".", DEFAULT_FILE = "index.html", INVALID_CODE_MESSAGE = "Code must be 5 digits.",
                       CODE_SET_MESSAGE = "Code set.";
   private static Socket esp;
   private static BufferedOutputStream espDataOut;
   private Socket client;
   private static boolean ledOn = false;
   
   public static void main(String[] args)
   {
      System.out.println("Starting Server...");
      // Creates a ServerSocket to listen for clients
      try(ServerSocket serverSocket = new ServerSocket(PORT))  
      {
         System.out.println("Listening on port " + serverSocket.getLocalPort());
         while (true) 
         {
            Server s = new Server(serverSocket.accept()); // Hangs here until a connection is made
            Thread t = new Thread(s); // Creates a new thread for the server to comminicate with the client on
            t.start();
         }
      }
      catch (Exception e)
      {
         System.out.println("Server connection error: " + e.getMessage());
      }
   }
   
   public Server(Socket client)
   {
      this.client = client;
   }
   
   public void start()
   {

   }

   @Override
   public void run()
   {
      System.out.println("Client connection made");
      BufferedReader in = null; // To receive data from client
      PrintWriter out = null;          // To send characters to the client
      BufferedOutputStream dataOut = null;    // To send data to the client     
      boolean ranAlready = false;
      while((client == esp && client.isConnected()) || (client != esp) && !ranAlready)
      {
         ranAlready = true;
      try 
      {
         in = new BufferedReader(new InputStreamReader(client.getInputStream())); // To receive data from client
         out = new PrintWriter(client.getOutputStream());                            // To send characters to the client
         dataOut = new BufferedOutputStream(client.getOutputStream());      // To send data to the client  
         
         String input = in.readLine(); // Reads line from client
         System.out.println("INPUT: " + input);
         if(input == null)
            continue;
         StringTokenizer parse = new StringTokenizer(input);
         String cmd = "DEFAULT";
         if(parse.hasMoreTokens())
            cmd = parse.nextToken().toUpperCase(); // Get command

         // If a get command is sent, send a file
         if(cmd.equals("GET"))
         {
            String fileRequested = parse.nextToken().toLowerCase(); // Next token is file requested
            if(fileRequested.endsWith("/"))
            {
               fileRequested = File.separator + "" + DEFAULT_FILE;
            }
            
            File f = new File(ROOT, fileRequested);
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
               System.out.println("Error converting file to byte array.");
               System.out.println("File name:  " + f.getName());
               System.out.println("File exists: " + f.exists());
               System.out.println(e1.getMessage());
            }
            
            // Sends the header
            out.println("HTTP/1.1 200 OK"); // OK header
            out.println("Date: " + new Date());
            out.println("Content-type: text/html");
            out.println("Content-length: " + length);
            out.println();
            out.flush();
            
            // Sends the actual file
            dataOut.write(data, 0, length);
            dataOut.flush();
         }
         else if(cmd.equals("POST"))
         {
            System.out.println("POST Received.");
            
            // Send OK header
            out.println("HTTP/1.1 200 OK");
            out.println("Date: " + new Date());
            out.println();
            out.flush();
            
            int r = 0;
            String message = "", body = "";
            // Reads the post
            while(in.ready() && (r = in.read()) != -1)
            {
               char c = (char) r;
               message += c;
            }
            
            // Parses the post
            String[] lines = message.split("\n");
            int messageLength = 0;
            for(String s : lines)
            {
               if(s.contains("Content-Length"))
               {
                  messageLength = Integer.parseInt(s.split(" ")[1].trim());
                  break;
               }
            }
            body = message.substring(message.length() - messageLength, message.length());
            System.out.println("POST MESSAGE: " + body);
            handleInput(body, out, dataOut);
         }
         else if(cmd.equals("ESP")) // If it is the esp connecting
         {
            // Save this socket as esp
            System.out.println("esp connected.");
            esp = this.client;
            espDataOut = new BufferedOutputStream(esp.getOutputStream());
            this.client.setKeepAlive(true);
         }
         else if(cmd.equals("TRIG"))
         {
            System.out.println("Alarm Triggered");
            try
            {
               System.out.println("Sending email alert...");
               sendEmail();
               System.out.println("Done.");
            }
            catch(Exception e)
            {
               System.out.println("Could not send email: " + e.getMessage());
            }
         }
         else
         {
            System.out.println("Unsupported command: " + cmd);
         }
      }
      catch(IOException e)
      {
         System.out.println("IO exception during client connection.\n" + e.getMessage());
      }
      finally 
      {
         // Close web resources only and not esp one's as those should be saved
         if(esp != this.client)
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
         System.out.println("Client connection closed.");
      }
      }
   }
   
   public void handleInput(String input, PrintWriter out, BufferedOutputStream dataOut) throws IOException
   {
      // toggle led
      if(input.equals("toggle"))
      {
         if(!esp.isConnected())
         {
            System.out.println("Warning: Esp not connected!");
            return;
         }
         
         // Send signal to ep8266
         if(!ledOn)
            sendMessageToEsp("H");
         else
            sendMessageToEsp("L");
         ledOn = !ledOn;
      }
      else if(input.split(" ").length == 2 && input.split(" ")[0].equals(CODE_CHANGE + ""))
      {
         System.out.println("Attempting pass code change...");
         // Makes sure the code is within guidelines (5 numbers only, no letters)
         // The webpage makes sure there are no non-numbers
         String newCode = input.split(" ")[1];
         if(newCode.length() != 5)
         {
            System.out.println("Code length must be 5");
            
            // Tell the website that the code length is wrong
            out.println("HTTP/1.1 400 Bad Request"); // Bad request header
            out.println("Date: " + new Date());
            out.println("Content-type: text/plain");
            out.println("Content-length: " + INVALID_CODE_MESSAGE.length());
            out.println();
            out.flush();
            
            dataOut.write(INVALID_CODE_MESSAGE.getBytes());
            dataOut.flush();
         }
         else if(esp != null && esp.isConnected() && espDataOut != null)
         {
            // Tell the website the code was set
            out.println("HTTP/1.1 200 OK"); 
            out.println("Date: " + new Date());
            out.println("Content-type: text/plain");
            out.println("Content-length: " + CODE_SET_MESSAGE.length());
            out.println();
            out.flush();
            
            dataOut.write(CODE_SET_MESSAGE.getBytes());
            dataOut.flush();
         }
      }
      
      // Sends input to esp
      sendMessageToEsp(input);
   }
   
   // This method attempts to send a message to the wifi module
   public void sendMessageToEsp(String message)
   {
      try
      {
         if(espDataOut != null && esp.isConnected())
         {
            espDataOut.write(message.getBytes());
            espDataOut.flush();
            System.out.println("Message sent to esp: " + message);
         }
         else
            System.out.println("ERROR: Esp output stream is null or esp not connected!");
      }
      catch(IOException e)
      {
         System.out.println("ERROR: Failed to send signal to esp!\n" + e.getMessage());
      }
   }
   
   public static void sendEmail() throws MessagingException
   {
      
      String host = "smtp.aol.com";
      String user = "alarm_system539";
      String pass = "ZcCaXa8rRbsHpH7";
      String to = "naphidmc@gmail.com";
      String from = "alarm_system539@aol.com";
      String subject = "Alarm Alert";
      String messageText = "Alarm has been triggered at " + new Date();
      boolean sessionDebug = false;
      
      Properties props = System.getProperties();
      props.put("mail.host", host);
      // props.put("mail.transport.protocol", "smtp");
      // props.put("mail.smtp.auth", "true");
      // props.put("mail.smtp.port", 26);
      // Uncomment 5 SMTPS-related lines below and comment out 2 SMTP-related lines above and 1 below if you prefer to use SSL
      props.put("mail.transport.protocol", "smtps");
      props.put("mail.smtps.auth", "true");
      props.put("mail.smtps.port", "465");
      props.put("mail.smtps.ssl.trust", host);
      Session mailSession = Session.getDefaultInstance(props, null);
      mailSession.setDebug(sessionDebug);
      Message msg = new MimeMessage(mailSession);
      msg.setFrom(new InternetAddress(from));
      InternetAddress[] address = {new InternetAddress(to)};
      msg.setRecipients(Message.RecipientType.TO, address);
      msg.setSubject(subject);
      msg.setSentDate(new Date());
      msg.setText(messageText);
      // Transport transport = mailSession.getTransport("smtp");
      Transport transport = mailSession.getTransport("smtps");
      transport.connect(host, user, pass);
      transport.sendMessage(msg, msg.getAllRecipients());
      transport.close();
   }
}
