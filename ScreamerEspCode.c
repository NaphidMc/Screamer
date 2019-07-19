  /*
     Wifi Module Code
  */  
  #include <ESP8266WiFi.h>
  #include <WiFiClient.h>
  
  WiFiClient espClient; // Wifi client used to connect to servers
  
  #define CONNECT_TIMEOUT 10000
    
  char ssid[33], pass[63];
  bool tryConnect = false, attemptingCredentials = false;
  
  unsigned long lastOnCommand = -1, lastServerPing = 0;
  
  void setup()
  {
    Serial.begin(74880);
    WiFi.mode(WIFI_STA);
    WiFi.setAutoConnect(false);
    delay(10);
    
    pinMode(2, OUTPUT);
    Serial.println("ON");
  }
  
  void loop()
  {
    yield();
    char inputBuffer[33];
    int charactersReceived = 0;
    
    if(!WiFi.isConnected() && millis() - lastOnCommand > 5000 && !attemptingCredentials)
    {
      Serial.println("ON");
      lastOnCommand = millis();
    }
    
    if(Serial.available())
    {
      yield();
      char c = '\n';
      do
      {
        if(Serial.available())
        {
          yield();
          c = (char) Serial.read();
          inputBuffer[charactersReceived] = c;
          charactersReceived++;
        }
        yield();
      } while(c != '\n' && c != '\r' && charactersReceived <= 33);
      inputBuffer[charactersReceived - 1] = NULL; // Set last character to null
      Serial.println(inputBuffer);
    }
  
  if (charactersReceived > 0 && inputBuffer[0] == '#')
  {
    Serial.println("Esp read for SSID: ");
    for(int i = 0; i < charactersReceived; i++)
    {
      ssid[i] = inputBuffer[i + 1];
    }
    Serial.println(ssid);
    yield();
    return;
  }
  else if(charactersReceived > 0 && inputBuffer[0] == '$')
  {
    Serial.println("Esp read for password: ");
    for(int i = 0; i < charactersReceived; i++)
    {
      pass[i] = inputBuffer[i + 1];
    }
    Serial.println(pass);
    yield();
    tryConnect = true;
    attemptingCredentials = true;
    return;
  }
  
  if(tryConnect)
  {
    Serial.println("Conecting to wifi...");
    unsigned long startTime = millis();
    WiFi.begin(ssid, pass); // Tries to connect with credentials
    while (WiFi.waitForConnectResult() != WL_CONNECTED && millis() - startTime < CONNECT_TIMEOUT)
    {
      delay(500);
    }

    tryConnect = false;
    attemptingCredentials = false;
    if(WiFi.status() == WL_CONNECTED)
    {
      Serial.println("Connected");
    }
    else
    {
      // Reset variables so credentials can be reentered
      Serial.print("Failed ");
      tryConnect = false;
      Serial.println(WiFi.status());
      WiFi.disconnect();
    }
  }
  
  // If not connected to server, try to connect
  if (WiFi.status() == WL_CONNECTED && !espClient.connected())
  {
    Serial.println("Connecting to alarm server...");
    if (!espClient.connect("192.168.1.10", 8080))
    {
      Serial.println("Failed to connect to alarm server");
      yield();
      return;
    }
    Serial.println("Success!");
    espClient.print("ESP ");
    espClient.println(WiFi.macAddress());
  }
  else if(WiFi.status() == WL_CONNECTED && espClient.connected())
  {
    if(millis() - lastServerPing > 5000)
    {
      espClient.print("ESP ");
      espClient.println(WiFi.macAddress());
      lastServerPing = millis();
    }
    
    // While there client is receiving info over wifi
    char wifiMessage[12] = {'\0', '\0', '\0', '\0', '\0', '\0', '\0', '\0', '\0', '\0', '\0', '\0'};
    charactersReceived = 0;

    // Reads data from the server
    if(espClient.available())
    {
      yield();
      char c = '\n';
      do
      {
        if(espClient.available())
        {
          yield();
          c = (char) espClient.read();
          wifiMessage[charactersReceived] = c;
          charactersReceived++;
        }
        yield();
      } while(c != '\n' && c != '\r' && charactersReceived <= 12);
      wifiMessage[charactersReceived - 1] = NULL; // Set last character to null
    }

    // Send wifi message recieved from the server to arduino
    for (int i = 0; i < 12; i++)
      if (wifiMessage[i] != '\0')
        Serial.print(wifiMessage[i]);
    if(wifiMessage[0] != '\0')
      Serial.println();

    if(wifiMessage[1] == '\0')
    {
      if(wifiMessage[0] == 'H')
        digitalWrite(2, HIGH);
      else if(wifiMessage[0] == 'L')
        digitalWrite(2, LOW);
    }
    
    // Checks for input from arduino and sends it to the server
    if (inputBuffer[0] == '>')
    {
      espClient.print("ESP ");
      espClient.print(WiFi.macAddress());
      espClient.print(" ");
      espClient.println(inputBuffer);
      // espClient.println("Hello World");
    }
  }
} 
