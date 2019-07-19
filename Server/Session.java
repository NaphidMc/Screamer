import java.util.Date;
import java.util.HashMap;

public class Session
{
   private String id = "";
   private boolean authenticated = false;
   private Date lastActive;
   public User user;
   public HashMap<String, String> parameters = new HashMap<String, String>();
   
   public Session(String id)
   {
      this.id = id;
      lastActive = new Date();
   }
   
   public void setUser(User u)
   {
      user = u;
   }
   
   public User getUser()
   {
      return user;
   }
   
   public String getId()
   {
      return id;
   }
   
   public void setAuthenticated(boolean b)
   {
      authenticated = b;
      resetLastActiveDate();
   }
   
   public boolean isAuthenticated()
   {
      return authenticated && !expired();
   }
   
   public void resetLastActiveDate()
   {
      lastActive = new Date();
   }
   
   public boolean expired()
   {
      if(new Date().getTime() - lastActive.getTime() > 15000)
         return true;
      return false;
   }
}
