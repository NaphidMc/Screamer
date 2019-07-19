import javafx.beans.property.SimpleStringProperty;

public class User implements DatabaseItem
{
   // SimpleStringProperty is used so that the JavaFX table view class can read data from the User class
   private SimpleStringProperty username, password, salt, company;
   
   public User(String name, String pass, String salt, String company)
   {
      username = new SimpleStringProperty(name);
      password = new SimpleStringProperty(pass);
      this.salt = new SimpleStringProperty(salt);
      this.company = new SimpleStringProperty(company);
   }
   
   public String getUsername()
   {
      return username.get();
   }
   
   public String getPassword()
   {
      return password.get();
   }
   
   public String getSalt()
   {
      return salt.get();
   }
   
   public String getCompany()
   {
      return company.get();
   }
   
   @Override
   public String getKey()
   {
      return getUsername();
   }
   
   @Override
   public String getKeyColumnName()
   {
      return "username";
   }
}
