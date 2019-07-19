import java.util.ArrayList;
import java.util.Optional;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

public class ServerGUI extends Application implements Runnable
{
   private static final int DEFAULT_RESOLUTION_WIDTH = 800, DEFAULT_RESOLUTION_HEIGHT = 600;
   
   private static Scene scene;
   private static BorderPane homePane, manageUsersPane, manageCompaniesPane, manageDoorsPane, viewLogsPane;
   private static GridPane newUserPane, newCompanyPane, newDoorPane;
   
   private static TextArea serverLog;
   private static TextField createUserUsername, createCompanyName, createDoorMac,
                            createDoorName, createDoorLocation, createDoorBuilding;
   private static PasswordField createUserPassword, createUserConfirmPassword;
   private static Label createUserErrMessage, createCompanyErrMessage, createDoorErrMessage;
   private static ComboBox<Company> userCompanyChoices, doorCompanyChoices;
   private static TableView<User> usersTable;
   private static TableView<Company> companiesTable;
   private static TableView<Door> doorsTable;
   private static TableView<Log> logsTable;
   
   @Override
   public void run()
   {
      launch();
   }
   
   @Override
   public void stop()
   {
      System.exit(0); // Stop the server when the GUI application is closed
   }
   
   /**
    * Initializes all GUI elements and starts the GUI application
    */
   @SuppressWarnings({ "rawtypes", "unchecked" })
   @Override
   public void start(Stage primaryStage)
   {
      primaryStage.setTitle("Screamer Server");

      homePane = new BorderPane();
      manageUsersPane = new BorderPane();
      manageCompaniesPane = new BorderPane();
      manageDoorsPane = new BorderPane();
      viewLogsPane = new BorderPane();
      newCompanyPane = getNewGridPane();
      newUserPane = getNewGridPane();
      newDoorPane = getNewGridPane();
      
      scene = new Scene(homePane, DEFAULT_RESOLUTION_WIDTH, DEFAULT_RESOLUTION_HEIGHT);
      
      createTableViews();
      
      VBox logsButtons = getVBox();
      logsButtons.getChildren().add(getNewBackHomeButton());
      viewLogsPane.setCenter(logsTable);
      viewLogsPane.setLeft(logsButtons);

      Button viewLogs = new Button();
      viewLogs.setText("View Logs");
      viewLogs.setOnAction(new EventHandler<ActionEvent>() {
         @Override
         public void handle(ActionEvent event) {
            populateLogs();
            scene.setRoot(viewLogsPane);
         }
     });
      
      Button manageUsers = new Button();
      manageUsers.setText("Manage Users");
      manageUsers.setOnAction(new EventHandler<ActionEvent>() {
          @Override
          public void handle(ActionEvent event) {
             populateUserTable();
             scene.setRoot(manageUsersPane);
          }
      });

      Button manageCompanies = new Button();
      manageCompanies.setText("Manage Companies");
      manageCompanies.setOnAction(new EventHandler<ActionEvent>() {
         @Override
         public void handle(ActionEvent event) {
            populateCompaniesTable();
            scene.setRoot(manageCompaniesPane);
         }
      });
      
      Button manageDoors = new Button();
      manageDoors.setText("Manage Doors");
      manageDoors.setOnAction(new EventHandler<ActionEvent>() {
         @Override
         public void handle(ActionEvent event) {
            populateDoorsTable();
            scene.setRoot(manageDoorsPane);
         }
      });

      serverLog = new TextArea(); // Creates a text area to display server log
      
      VBox homeButtons = getVBox();
      homeButtons.getChildren().addAll(manageUsers, manageCompanies, manageDoors, viewLogs);
      
      homePane.setLeft(homeButtons);  
      homePane.setCenter(serverLog);
      
      // Cancel button for create new user page
      Button cancelCreateUser = getNewCancelButton(manageUsersPane);
      
      // New user fields and labels
      newUserPane.add(new Label("Username"), 0, 0);
      createUserUsername = new TextField();
      createUserUsername.setPromptText("Username");
      newUserPane.add(createUserUsername, 1, 0);
      
      newUserPane.add(new Label("Password"), 0, 1);
      createUserPassword = new PasswordField();
      createUserPassword.setPromptText("Password");
      newUserPane.add(createUserPassword, 1, 1);
      
      newUserPane.add(new Label("Confirm Password"), 0, 2);
      createUserConfirmPassword = new PasswordField();
      createUserConfirmPassword.setPromptText("Password");
      newUserPane.add(createUserConfirmPassword, 1, 2);
      
      userCompanyChoices = new ComboBox();
      doorCompanyChoices = new ComboBox();
      
      newUserPane.add(new Label("Company"), 0, 3);
      newUserPane.add(userCompanyChoices, 1, 3);
      
      // Create new user button
      Button finishCreateNewUserButton = new Button();
      finishCreateNewUserButton.setAlignment(Pos.CENTER);
      finishCreateNewUserButton.setText("Create");
      finishCreateNewUserButton.setOnAction(new EventHandler<ActionEvent>() {
         @Override
         public void handle(ActionEvent event) {
            // Validate input and create new user if input is good
            
            // Password and confirm password do not match
            if(!createUserPassword.getText().equals(createUserConfirmPassword.getText()))
            {
               createUserErrMessage.setText("Passwords don't match.");
            }
            else if(MySqlClient.userExists(createUserUsername.getText())) // Username taken :(
            {
               createUserErrMessage.setText("User already exists.");
            }
            else if(!MySqlClient.userNameValid(createUserUsername.getText())) // Username not valid!
            {
               createUserErrMessage.setText("Username invalid. Must be between 6 and 28 characters\n" +
                                            "and must only contains letters numbers and _'s");
            }
            else
            {
               // Everything is fine yay! Create a new user in the database
               MySqlClient.createUser(createUserUsername.getText(), createUserPassword.getText(), 
                                      userCompanyChoices.getSelectionModel().getSelectedItem().getName());
               populateUserTable();
               scene.setRoot(manageUsersPane);
            }
         }
      });
      
      HBox errMsgBox = getHBox(); 
      errMsgBox.setAlignment(Pos.CENTER);
      createUserErrMessage = new Label();
      createUserErrMessage.setAlignment(Pos.CENTER);
      createUserErrMessage.setTextFill(Color.RED);
      errMsgBox.getChildren().add(createUserErrMessage);
      
      newUserPane.add(errMsgBox, 0, 4);
      newUserPane.add(finishCreateNewUserButton, 0, 5);
      newUserPane.add(cancelCreateUser, 1, 5);
      
      createCompanyName = new TextField();
      createCompanyName.setPromptText("Company Name");
      
      createCompanyErrMessage = new Label();
      createCompanyErrMessage.setTextFill(Color.RED);
      
      Button finishCreateCompany = new Button("Create");
      finishCreateCompany.setOnAction(new EventHandler<ActionEvent>()
      {
         @Override
         public void handle(ActionEvent e)
         {
            if(createCompanyName.getLength() == 0)
            {
               createCompanyErrMessage.setText("Company name required.");
            }
            else if(MySqlClient.companyExists(createCompanyName.getText()))
            {
               createCompanyErrMessage.setText("Company with that name exists.");
            }
            else
            {
               MySqlClient.createCompany(createCompanyName.getText());
               populateCompaniesTable();
               scene.setRoot(manageCompaniesPane);
            }
         }
      });
      
      newCompanyPane.add(new Label("Company Name"), 0, 0);
      newCompanyPane.add(createCompanyName, 1, 0);
      newCompanyPane.add(createCompanyErrMessage, 0, 1);
      newCompanyPane.add(finishCreateCompany, 0, 2);
      newCompanyPane.add(getNewCancelButton(manageCompanies), 1, 2);

      // A button that navigates to the create user screen
      Button createNewUserButton = new Button("Create User");
      createNewUserButton.setOnAction(new EventHandler<ActionEvent>()
      {
         @Override 
         public void handle(ActionEvent e)
         {
            // Resets fields
            createUserUsername.setText("");
            createUserPassword.setText("");
            createUserConfirmPassword.setText("");
            populateCompanyChoices();
            scene.setRoot(newUserPane);
         }
      });
      
      Button removeUserButton = getNewRemoveButton("Remove User", usersTable, "userdata");

      VBox manageUsersButtonBox = getVBox();
      manageUsersButtonBox.getChildren().add(getNewBackHomeButton());
      manageUsersButtonBox.getChildren().add(createNewUserButton);
      manageUsersButtonBox.getChildren().add(removeUserButton);
      manageUsersButtonBox.setAlignment(Pos.TOP_LEFT);
      
      manageUsersPane.setCenter(usersTable);
      manageUsersPane.setLeft(manageUsersButtonBox);

      Button createNewCompanyButton = new Button("Create Company");
      createNewCompanyButton.setOnAction(new EventHandler<ActionEvent>()
      {
         @Override 
         public void handle(ActionEvent e)
         {
            createCompanyName.setText("");
            createCompanyErrMessage.setText("");
            scene.setRoot(newCompanyPane);
         }
      });
      
      Button removeCompanyButton = getNewRemoveButton("Remove Company", companiesTable, "companies");
      
      VBox manageCompaniesButtonBox = getVBox();
      manageCompaniesButtonBox.getChildren().addAll(getNewBackHomeButton(), createNewCompanyButton,
                                                    removeCompanyButton);
      
      manageCompaniesPane.setCenter(companiesTable);
      manageCompaniesPane.setLeft(manageCompaniesButtonBox);
      
      Button removeDoorButton = getNewRemoveButton("Remove Door", doorsTable, "doors");
      
      createDoorErrMessage = new Label();
      
      Button createDoorButton = new Button("Create Door");
      createDoorButton.setOnAction(new EventHandler<ActionEvent>()
      {
         @Override 
         public void handle(ActionEvent e)
         {
            populateCompanyChoices();
            createDoorMac.setText("");
            createDoorBuilding.setText("");
            createDoorLocation.setText("");
            createDoorName.setText("");
            createDoorErrMessage.setText("");
            scene.setRoot(newDoorPane);
         }
      });
      
      manageDoorsPane.setCenter(doorsTable);
      
      VBox manageDoorsButtons = getVBox();
      manageDoorsButtons.getChildren().addAll(getNewBackHomeButton(), createDoorButton, removeDoorButton);
      
      manageDoorsPane.setLeft(manageDoorsButtons);
      
      
      // New door pane setup
      newDoorPane.add(new Label("MAC Address"), 0, 0);
      newDoorPane.add(new Label("Company"), 0, 1);
      newDoorPane.add(new Label("Name"), 0, 2);
      newDoorPane.add(new Label("Location"), 0, 3);
      newDoorPane.add(new Label("Building"), 0, 4);
      
      
      createDoorMac = new TextField();
      createDoorName = new TextField();
      createDoorLocation = new TextField();
      createDoorBuilding = new TextField();
      
      createDoorErrMessage.setTextFill(Color.RED);
      
      newDoorPane.add(createDoorMac, 1, 0);
      newDoorPane.add(createDoorName, 1, 2);
      newDoorPane.add(createDoorLocation, 1, 3);
      newDoorPane.add(createDoorBuilding, 1, 4);
      newDoorPane.add(doorCompanyChoices, 1, 1);
      
      Button finishCreateDoor = new Button("Create Door");
      finishCreateDoor.setOnAction(new EventHandler<ActionEvent>()
      {
         @Override 
         public void handle(ActionEvent e)
         {
            if(MySqlClient.doorExists(createDoorMac.getText()))
            {
               createDoorErrMessage.setText("A door with that MAC address is already registered");
            }
            else
            {
               // No problems, create a door!
               MySqlClient.createDoor(createDoorMac.getText(), createDoorName.getText(), 
                                      createDoorBuilding.getText(), createDoorLocation.getText(), 
                                      doorCompanyChoices.getSelectionModel().getSelectedItem().toString());
               populateDoorsTable();
               scene.setRoot(manageDoorsPane);
            }
         }
      });
      
      newDoorPane.add(finishCreateDoor, 0, 6);
      newDoorPane.add(getNewCancelButton(manageDoorsPane), 1, 6);
      
      primaryStage.setScene(scene);
      primaryStage.show();
   }
   
   /**
    * Creates all table views here
    */
   @SuppressWarnings({ "rawtypes", "unchecked" })
   public void createTableViews()
   {
      doorsTable = new TableView<Door>();
      
      TableColumn macAddress = new TableColumn("MAC Address"),
                  location = new TableColumn("Location"),
                  building = new TableColumn("Building"),
                  company = new TableColumn("Company"),
                  name = new TableColumn("Name");
      macAddress.setCellValueFactory(new PropertyValueFactory<Log, String>("macAddress"));
      location.setCellValueFactory(new PropertyValueFactory<Log, String>("location"));
      building.setCellValueFactory(new PropertyValueFactory<Log, String>("building"));
      company.setCellValueFactory(new PropertyValueFactory<Log, String>("company"));
      name.setCellValueFactory(new PropertyValueFactory<Log, String>("name"));
      doorsTable.getColumns().addAll(macAddress, name, company, building, location);            
      
      logsTable = new TableView<Log>();
      
      TableColumn logsDateCol = new TableColumn("Date"), 
                  logsEventTypeCol = new TableColumn("Event Name"),
                  logsCodeCol = new TableColumn("Code"),
                  logsEmployeeCol = new TableColumn("Employee");
      logsDateCol.setCellValueFactory(new PropertyValueFactory<Log, String>("timeStamp"));
      logsEventTypeCol.setCellValueFactory(new PropertyValueFactory<Log, String>("eventType"));
      logsCodeCol.setCellValueFactory(new PropertyValueFactory<Log, String>("employee"));
      logsEmployeeCol.setCellValueFactory(new PropertyValueFactory<Log, String>("codeUsed"));
      logsTable.getColumns().addAll(logsDateCol, logsEventTypeCol, logsCodeCol, logsEmployeeCol);
      
      // Columns for company table
      TableColumn companyNameCol = new TableColumn("Name");
      companyNameCol.setCellValueFactory(new PropertyValueFactory<Company, String>("name"));
            
      companiesTable = new TableView<Company>();
      companiesTable.setEditable(false);
      companiesTable.getColumns().addAll(companyNameCol);
      
      usersTable = new TableView<User>();
      usersTable.setEditable(false);
      usersTable.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);

      // Makes columns for user data table
      TableColumn usernameCol = new TableColumn("Username");
      usernameCol.setCellValueFactory(new PropertyValueFactory<User, String>("username"));
      TableColumn passwordCol = new TableColumn("Password");
      passwordCol.setCellValueFactory(new PropertyValueFactory<User, String>("password"));
      TableColumn companyCol = new TableColumn("Company");
      companyCol.setCellValueFactory(new PropertyValueFactory<User, String>("company"));
      TableColumn saltCol = new TableColumn("Salt");
      saltCol.setCellValueFactory(new PropertyValueFactory<User, String>("salt"));
      
      usersTable.getColumns().addAll(usernameCol, passwordCol, companyCol, saltCol); // Add columns to table
   }
   
   public GridPane getNewGridPane()
   {
      GridPane pane = new GridPane();
      pane.setAlignment(Pos.CENTER);
      pane.setHgap(10);
      pane.setVgap(10);
      pane.setPadding(new Insets(25, 25, 25, 25));
      return pane;
   }
   
   /**
    * Gets a new button which returns to the Parent returnTo
    * @param returnTo
    * @return
    */
   public Button getNewCancelButton(Parent returnTo)
   {
      Button cancel = new Button();
      cancel.setAlignment(Pos.CENTER);
      cancel.setText("Cancel");
      cancel.setOnAction(new EventHandler<ActionEvent>() {
         @Override
         public void handle(ActionEvent event) {
            // Go back
            scene.setRoot(returnTo);
         }
      });
      return cancel;
   }
   
   public Button getNewBackHomeButton()
   {
      // A button that navigates back home
      Button backHomeButton = new Button("<-- Back");
      backHomeButton.setOnAction(new EventHandler<ActionEvent>()
      {
         @Override
         public void handle(ActionEvent event)
         {
            scene.setRoot(homePane);
         }
      });
      return backHomeButton;
   }
   
   public void populateLogs()
   {
      logsTable.setItems(FXCollections.observableArrayList(MySqlClient.getLogs()));
   }
   
   public void populateCompanyChoices()
   {
      ArrayList<Company> choices = new ArrayList<Company>();
      choices.add(new Company("N/A"));
      choices.addAll(MySqlClient.getAllCompanies());
      userCompanyChoices.setItems(FXCollections.observableArrayList(choices));
      userCompanyChoices.getSelectionModel().selectFirst();
      doorCompanyChoices.setItems(FXCollections.observableArrayList(choices));
      doorCompanyChoices.getSelectionModel().selectFirst();
   }
   
   /**
    * Returns a button that removes selected element from tableview on click
    * @param text
    * @param tableView
    * @return
    */
   public Button getNewRemoveButton(String text, TableView<?> tableView, String table)
   {
      Alert removeAlert = new Alert(AlertType.CONFIRMATION);
      removeAlert.setTitle("Confirm Deletion");
      removeAlert.setContentText("This action cannot be undone.");
      
      Button removeButton = new Button("Remove Selected");
      removeButton.setOnAction(new EventHandler<ActionEvent>()
      {  
         TableView<?> t = tableView;
         String tab = table;
         
         @Override 
         public void handle(ActionEvent e)
         {
            DatabaseItem item = (DatabaseItem) t.getSelectionModel().getSelectedItem();
            if(item == null)
               return;
            
            // Create a popup to prompt user to confirm deletion
            removeAlert.setHeaderText("Remove element where " + item.getKeyColumnName() + " = '" + item.getKey() + "'");
            Optional<ButtonType> op = removeAlert.showAndWait();
            if(op.get() == ButtonType.OK) // Deletion confirmed
            {
               MySqlClient.executeQuery("DELETE FROM `" + tab + "` WHERE `" + item.getKeyColumnName() + "` = '" + item.getKey() + "';");
            }
            populateTable(t);
         }
      });
      
      return removeButton;
   }
   
   public void populateTable(TableView<?> t)
   {
      if(t == usersTable)
         populateUserTable();
      else if(t == companiesTable)
         populateCompaniesTable();
      else if(t == doorsTable)
         populateDoorsTable();
   }
   
   public void populateDoorsTable()
   {
      doorsTable.setItems(FXCollections.observableArrayList(MySqlClient.getAllDoors()));
   }
   
   /**
    * Refreshes element in the userTable table by making a SQL query
    */
   public void populateUserTable()
   {
      // Creates a list of the ObservableList type so the TableView can use the data
      ObservableList<User> users = FXCollections.observableArrayList(MySqlClient.getAllUsers());
      usersTable.setItems(users); // Sets the table data
   }
   
   /**
    * Refreshes companies in companies table
    */
   public void populateCompaniesTable()
   {
      companiesTable.setItems(FXCollections.observableArrayList(MySqlClient.getAllCompanies()));
   }
   
   /**
    * Gets a new HBox with default spacing and padding
    * @return
    */
   public HBox getHBox()
   {
      HBox hb = new HBox();
      hb.setPadding(new Insets(10));
      hb.setSpacing(8);
      return hb;
   }
   
   /**
    * Gets a new VBox with default spacing and padding
    * @return
    */
   public VBox getVBox()
   {
      VBox vb = new VBox();
      vb.setPadding(new Insets(10));
      vb.setSpacing(8);
      return vb;
   }
   
   public synchronized void updateLog(String append)
   {
      Platform.runLater(new Runnable()
      {
         @Override
         public void run()
         {
            if(serverLog != null)
               serverLog.appendText(append + "\n"); 
         }
      });
   }
}
