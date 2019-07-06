/*
 * Wifi Module Code
 */
#include <ESP8266WiFi.h>
#include <WiFiClient.h>

WiFiClient espClient; // Wifi client used to connect to servers

void setup()
{
  Serial.begin(115200);
  delay(10);
  
  pinMode(2, OUTPUT);

  // Serial.println("Starting");
  WiFi.begin("Cottage", "mojomojo11");

  // Serial.print("Connecting");
  while (WiFi.status() != WL_CONNECTED)
  {
    delay(500);
    // Serial.print(".");
  }
  // Serial.println();

  // Serial.print("Connected, IP address: ");
  // Serial.println(WiFi.localIP());
}

void loop() 
{
  // Flash pin 2 on the wifi module to indicate the program is running
  /*digitalWrite(2, HIGH);
  delay(500); 
  digitalWrite(2, LOW);
  delay(500);*/ 

  // If not connected to server, try to connect
  if(!espClient.connected())
  {
    // Serial.println("Connecting to alarm server...");
    if(!espClient.connect("192.168.1.10", 8080))
    {
      // Serial.println("Failed to connect to alarm server");
      return;
    }
    // Serial.println("Success!");
    espClient.println("esp");
  }
  else
  {
    // While there client is receiving info over wifi
    char wifiMessage[12] = {'\0', '\0', '\0', '\0', '\0', '\0', '\0', '\0', '\0', '\0', '\0', '\0'};
    int index = 0;
    // If there is data to be read from the server
    while(espClient.available() > 0)
    {
      char c = (char) espClient.read();
      wifiMessage[index] = c;
      index++;
      // Toggle pin 2 upon receiving L or H, this is useful to test if esp is connected on it's own
      if(c == 'H')
        digitalWrite(2, HIGH);
      else if(c == 'L')
        digitalWrite(2, LOW);
    }

    // Send wifi message recieved from the server to arduino
    for(int i = 0; i < 12; i++)
      if(wifiMessage[i] != '\0')
        Serial.print(wifiMessage[i]);

    // Checks for input from arduino and sends it to the server
    char arduinoMessage[12] = {'\0', '\0', '\0', '\0', '\0', '\0', '\0', '\0', '\0', '\0', '\0', '\0'};
    if(Serial.available() > 4)
    {
      index = 0;
      while(Serial.available() > 0)
      {
        char c = (char) Serial.read();
        arduinoMessage[index] = c;
        Serial.print(c);
        index++;
      }
    }
    
    if(arduinoMessage[0] != '\0')
    {
      Serial.println();
      espClient.println(arduinoMessage);
      // espClient.println("Hello World");
    }
  }
}
