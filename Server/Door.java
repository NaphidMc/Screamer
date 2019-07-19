import javafx.beans.property.SimpleStringProperty;

public class Door implements DatabaseItem
{
   private SimpleStringProperty macAddress, company, name, location, building;
   
   public Door(String mac, String comp, String name, String loc, String building)
   {
      macAddress = new SimpleStringProperty(mac);
      company = new SimpleStringProperty(comp);
      this.name = new SimpleStringProperty(name);
      location = new SimpleStringProperty(loc);
      this.building = new SimpleStringProperty(building);
   }
   
   public String getMacAddress()
   {
      return macAddress.get();
   }
   
   public String getCompany()
   {
      return company.get();
   }
   
   public String getName()
   {
      return name.get();
   }
   
   public String getLocation()
   {
      return location.get();
   }
   
   public String getBuilding()
   {
      return building.get();
   }
   
   @Override
   public String getKey()
   {
      return getMacAddress();
   }
   
   @Override
   public String getKeyColumnName()
   {
      return "macAddress";
   }
}
