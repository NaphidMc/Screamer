import javafx.beans.property.SimpleStringProperty;

public class Company implements DatabaseItem
{
   private SimpleStringProperty name;
   
   public Company(String name)
   {
      this.name = new SimpleStringProperty(name);
   }
   
   public String getName()
   {
      return name.get();
   }
   
   @Override
   public String getKey()
   {
      return getName();
   }
   
   @Override
   public String getKeyColumnName()
   {
      return "name";
   }
   
   @Override
   public String toString()
   {
      return getName();
   }
}
