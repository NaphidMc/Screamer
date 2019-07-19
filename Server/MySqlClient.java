import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Date;

public class MySqlClient
{
   private static Connection connection;
   private static Statement statement;
   
   public static void startClient()
   {
      try
      {
         Class.forName("com.mysql.cj.jdbc.Driver");
         connection = DriverManager.getConnection("jdbc:mysql://localhost:3306/screamer","root","mojomojo1");
      }
      catch(SQLException e)
      {
         Server.log("MySql error: " + e.getMessage(), Server.MessageType.ERROR);
      }
      catch(ClassNotFoundException e1)
      {
         Server.log("Class not found: " + e1.getMessage(), Server.MessageType.ERROR);
      }
   }
   
   /**
    * Sends queries to the MySQL server
    * @param query
    */
   public static ResultSet executeQuery(String query) 
   {
      if(connection != null)
      {
         try
         {
            statement = connection.createStatement();
            
            ResultSet rs = null;
            if(query.toUpperCase().contains("INSERT") || query.toUpperCase().contains("DELETE"))
               statement.executeUpdate(query);
            else
               rs = statement.executeQuery(query);
            return rs;
         }
         catch(SQLException e)
         {
            Server.log("SQL Exception: " + e.getMessage(), Server.MessageType.ERROR);
         }
      }
      else
      {
         Server.log("MySql connection not available!", Server.MessageType.ERROR);
      }
      return null; // Failed to connect or other sql error, return null
   }
   
   /**
    * Returns a hashed version of str using the SHA-1 algorithm with a salt
    */
   private static String hash(String str, byte[] salt)
   {
      try
      {
         MessageDigest md = MessageDigest.getInstance("SHA-1");
         md.update(salt);
         byte[] bytes = md.digest(str.getBytes());
         StringBuilder strBlder = new StringBuilder();
         for(int i = 0; i < bytes.length; i++)
         {
            strBlder.append(Integer.toString((bytes[i] & 0xff) + 0x100, 16).substring(1));
         }
         return strBlder.toString();
      }
      catch(NoSuchAlgorithmException e)
      {
         Server.log("Could not create hash for password: " + e.getMessage(), Server.MessageType.ERROR);
      }
      return "ERROR";
   }
   
   /**
    * Gets all users from the database
    * @return
    */
   public static ArrayList<User> getAllUsers()
   {
       ResultSet rs = executeQuery("SELECT * FROM userdata;");
       ArrayList<User> result = new ArrayList<User>();
       if(rs != null)
       {
          try
          {
             // While there are more users to get
             while(rs.next())
             {
                String name = rs.getString("username"), 
                       pass = rs.getString("password"),
                       company = rs.getString("company"),
                       salt = rs.getString("salt");
                result.add(new User(name, pass, salt, company));
             }
          }
          catch(SQLException e)
          {
             System.out.println("SQL exception while getting all users: " + e.getMessage());
          }
       }
       
       return result;
   }
   
   /**
    * Creates a new log entry at the current time
    */
   public static void createLogEntry(String eventType, String doorMac, String code, String employee)
   {
      executeQuery("INSERT INTO doorlogs (`eventType`, `timestamp`, `code`, `employee`, `doorMacAddress`)"
                   + " VALUES ('" + eventType +"','" + new Date() + "','" + code + "','" 
                   + employee + "','" + doorMac + "');");
   }
   
   public static ArrayList<Company> getAllCompanies()
   {
      ResultSet rs = executeQuery("SELECT * FROM companies;");
      ArrayList<Company> result = new ArrayList<Company>();
      if(rs != null)
      {
         try
         {
            // While there are more companies to get
            while(rs.next())
            {
               String name = rs.getString("name");
               result.add(new Company(name));
            }
         }
         catch(SQLException e)
         {
            System.out.println("SQL exception while getting all companies: " + e.getMessage());
         }
      }
      
      return result;
   }
   
   /**
    * Gets a random salt
    * @return salt
    * @throws NoSuchAlgorithmException
    */
   private static byte[] getSalt() throws NoSuchAlgorithmException
   {
       SecureRandom sr = SecureRandom.getInstance("SHA1PRNG");
       byte[] salt = new byte[16];
       sr.nextBytes(salt);
       return salt;
   }
   
   /**
    * Checks if a door with this mac address exists
    * @param mac
    * @return
    */
   public static boolean doorExists(String mac)
   {
      return rowExists("doors", "macAddress", mac);
   }
   
   /**
    * Adds a new user to the database
    * @param username
    * @param password
    */
   public static void createUser(String username, String password, String company)
   {  
      try
      {
         System.out.println("USER CREATION");
         System.out.println("PASSWORD: " + password);
         byte[] salt = getSalt(); // Random salt is obtained
         System.out.println("SALT: " + new String(salt));
         String passwordHash = hash(password, salt); // Gets a hash for the password
         System.out.println("PASSWORD " + password + " saved as hash: " + passwordHash);
         executeQuery("INSERT INTO userdata (`username`, `password`, `company`, `salt`) VALUES ('" + username + "','" 
                      + passwordHash + "','" + company + "','" + new String(salt) + "');");
         Server.log("Created new user: " + username, Server.MessageType.INFO);
      }
      catch(NoSuchAlgorithmException e)
      {
         Server.log("Could not create user: " + e.getMessage(), Server.MessageType.ERROR);
      }
   }
   
   /**
    * Adds a new door to the database
    * @param mac
    * @param name
    * @param building
    * @param location
    * @param company
    */
   public static void createDoor(String mac, String name, String building, String location, String company)
   {
      executeQuery("INSERT INTO doors (`macAddress`, `company`, `location`, `name`, `building`) VALUES ('" + mac + "','" 
            + company + "','" + location + "','" + name + "','" + building + "');");
      Server.log("Registered new door: " + mac, Server.MessageType.INFO);
   }
   
   public static ArrayList<Door> getAllDoors(String where)
   {
      String query = "SELECT * FROM doors";
      if(where.length() > 0)
         query += " WHERE " + where;
      query += ";";
      System.out.println(query);
      ResultSet rs = executeQuery(query);
      ArrayList<Door> result = new ArrayList<Door>();
      if(rs != null)
      {
         try
         {
            // While there are more companies to get
            while(rs.next())
            {
               result.add(new Door(rs.getString("macAddress"), rs.getString("company"), 
                     rs.getString("name"), rs.getString("location"), rs.getString("building")));
            }
         }
         catch(SQLException e)
         {
            System.out.println("SQL exception while getting all doors: " + e.getMessage());
         }
      }
      
      return result;
   }
   
   public static ArrayList<Door> getAllDoors()
   {
      return getAllDoors("");
   }
   
   public static ArrayList<Log> getAllLogs(String where)
   {
      String query = "SELECT * FROM doorlogs";
      if(!where.equals(""))
         query += " WHERE " + where;
      query += ";";
      ResultSet rs = executeQuery(query);
      ArrayList<Log> result = new ArrayList<Log>();
      if(rs != null)
      {
         try
         {
            // While there are more companies to get
            while(rs.next())
            {
               String eventType = rs.getString("eventType"),
                      timeStamp = rs.getString("timeStamp"),
                      code = rs.getString("code"),
                      employee = rs.getString("employee"),
                      mac = rs.getString("doorMacAddress");
               result.add(new Log(timeStamp, eventType, code, employee, mac));
            }
         }
         catch(SQLException e)
         {
            System.out.println("SQL exception while getting logs: " + e.getMessage());
         }
      }
      
      return result;
   }
   
   public static ArrayList<Log> getLogs()
   {
      return getAllLogs("");
   }
   
   /**
    * Creates a new company
    * @param name
    */
   public static void createCompany(String name)
   {
      executeQuery("INSERT INTO companies (`name`) VALUES ('" + name + "');");
      Server.log("Created new company: " + name, Server.MessageType.INFO);
   }

   /**
    * Tries to get a user by username, null if user does not exist
    * @param username
    * @return
    */
   public static User getUser(String username)
   {
      try
      {
         ResultSet set = executeQuery("SELECT * FROM userdata WHERE `username`='" + username + "';");
         if(set == null || !set.next())
            return null;
         else
         {
            String u = set.getString("username"),
                   p = set.getString("password"),
                   s = set.getString("salt"),
                   c = set.getString("company");
            return new User(u, p, s, c);
         }
      }
      catch(SQLException e)
      {
         Server.log("Could not get user: " + e.getMessage(), Server.MessageType.ERROR);
      }
      return null;
   }
   
   /**
    * Tries to log in with specified credentials.
    * Returns null if unsuccessful
    * @param username
    * @param pass
    * @return
    */
   public static User login(String username, String pass)
   {
      User user = getUser(username);
      if(user == null)
         return null;
      
      System.out.println(username + "'s salt: " + user.getSalt());
      System.out.println(username + "'s hashed pass: " + user.getPassword());
      System.out.println("Pass atempted: " + pass);
      System.out.println("Salt used: " + user.getSalt());
      System.out.println("Hash attempted: " + hash(pass, user.getSalt().getBytes()));
      if(user.getPassword().equals(hash(pass, user.getSalt().getBytes())))
         return user;
      else
         return null;
   }
   
   /**
    * Determines if a user with specified username exists
    * @param username
    * @return
    */
   public static boolean userExists(String username)
   {
      return rowExists("userdata", "username", username);
   }
   
   /**
    * Determines if a company with specified name exists
    * @param username
    * @return
    */
   public static boolean companyExists(String name)
   {
      return rowExists("companies", "name", name);
   }
   
   public static boolean rowExists(String table, String col, String value)
   {
      ResultSet set = executeQuery("SELECT * FROM `" + table +"` WHERE `" + col + "`='" + value + "';");
      
      try
      {
         if(set != null)
         {
            return set.next(); // ResultSet.next() returns false if there is no next element
         }
         else 
            return true;
      }
      catch(SQLException e)
      {
         Server.log("Exception while checking if element exists", Server.MessageType.ERROR);
         return false;
      }
   }
   
   /**
    * Checks if a username is valid (only contains accepted characters and is within length limits)
    * @param name
    * @return
    */
   public static boolean userNameValid(String name)
   {
      // TODO change 1 to 6
      if(name.length() < 1)
         return false;
      else if(name.length() > 28)
         return false;
      
      for(int i = 0; i < name.length(); i++)
      {
         char ch = name.charAt(i);
         if(!Character.isLetter(ch) && !Character.isDigit(ch) && !(ch == '_'))
            return false;
      }
      return true;
   }
   
   /*
    * Closes all MySql resources
    */
   public static void close() throws SQLException  
   {
      if(connection != null)
         connection.close();
      
      if(statement != null)
         statement.close();
   }
}
