import java.io.BufferedOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketException;
import java.util.Date;

public class Esp
{
   private String mac;
   private Socket socket;
   private Date lastOnline;
   private BufferedOutputStream out;
   
   public Esp(String mac, Socket s)
   {
      socket = s;
      try
      {
         socket.setKeepAlive(true);
      } 
      catch (SocketException e1)
      {
         Server.log("Esp constructor: " + e1.getMessage(), Server.MessageType.ERROR);
      }
      this.mac = mac;
      lastOnline = new Date();
      try
      {
         out =  new BufferedOutputStream(socket.getOutputStream());
      }
      catch(IOException e)
      {  
         Server.log("Could not open output stream for esp", Server.MessageType.ERROR);
      }
   }
   
   public Socket getSocket()
   {
      return socket;
   }
   
   public BufferedOutputStream getOutputStream()
   {
      return out;
   }
   
   public String getMacAddress()
   {
      return mac;
   }
   
   public void resetLastOnline()
   {
      lastOnline = new Date();
   }
   
   /**
    * @return A boolean; true if the esp is online false if not
    */
   public boolean online()
   {
      return new Date().getTime() - lastOnline.getTime() < 10000;
   }
}
