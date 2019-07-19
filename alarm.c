#include <Keypad.h>
#include <LiquidCrystal.h>
#include <EEPROM.h>

#define INPUT_MODE_NORMAL 0
#define INPUT_MODE_PASSCODE_CHANGE 1
#define INPUT_MODE_WIFI_SETUP 2
#define MESSAGE_INCORRECT_CODE "INVALID CODE"
#define MESSAGE_PASSCODE_SET "PASSCODE SET"
#define MESSAGE_PASSCODES_DONT_MATCH "CODES DONT MATCH"
#define MESSAGE_INVALID_INPUT "INVALID INPUT"
#define CONNECTION_TIMEOUT 12000 

// Keypad setup
const byte KEYPAD_ROWS = 4, KEYPAD_COLS = 4;

char keys[KEYPAD_ROWS][KEYPAD_COLS] = {
  {'1', '2', '3', 'A'},
  {'4', '5', '6', 'B'},
  {'7', '8', '9', 'C'},
  {'*', '0', '#', 'D'}
};

byte rowPins[KEYPAD_ROWS] = {2, 14, 15, 16};
byte colPins[KEYPAD_COLS] = {17, 4, 5, 20};
Keypad customKeypad = Keypad(makeKeymap(keys), rowPins, colPins, KEYPAD_ROWS, KEYPAD_COLS);

// LCD Setup
const byte rs = 9, en = 8, d4 = 10, d5 = 11, d6 = 12, d7 = 13; // Pin numbers
LiquidCrystal lcd(rs, en, d4, d5, d6, d7); // LCD constructor

const byte DOOR_SENSOR_PIN = 51, SIREN_PIN = 50;

const byte CODE_LENGTH = 5;
const int LCD_MESSAGE_DURATION = 3000; // Duration in ms that a inputBuffer on the LCD lasts
bool armed = false, inputBufferActive = false, alarmSounding = false, caretOn = false,
     lastDoorState;
int inputStage = 0, inputMode = 0, caretPosition = 0, pressedNum = 1;
char code[CODE_LENGTH], currText[63], newCode[CODE_LENGTH],
     currentMessage[16], ssid[32], wifiPass[63], lastCodeUsed[CODE_LENGTH];
unsigned long currentMessageStart = 0, lastSentWifiCreds = -1;
char lastPressed = '\0';

// Checks if the text entered matches the current code
bool validateCode(char c[])
{
  for (int i = 0; i < CODE_LENGTH; i++)
  {
    if (c[i] != code[i])
    {
      return false;
    }
  }
  
  return true;
}

void sendEspWifiCredentials()
{
  lastSentWifiCreds = millis();
  Serial.print("Sending SSID: ");
  for(int i = 0; i < 32; i++)
  {
    char rom = (char) EEPROM.read(i + CODE_LENGTH);
    if(rom != 255)
      ssid[i] = rom;
    else
    {
      ssid[i] = NULL;
      break;
    }
  }
  Serial.println(ssid);
  Serial1.print("#");
  Serial1.print(ssid);
  Serial1.print("\n");
  Serial1.flush();
  
  Serial.print("Sending password: ");
  for(int i = 0; i < 63; i++)
  {
    char rom = (char) EEPROM.read(i + CODE_LENGTH + 32);
    if(rom != 255)
      wifiPass[i] = rom;
    else
    {
      wifiPass[i] = NULL;
      break;
    }
  }
  Serial.print("$");
  Serial.println(wifiPass);
  Serial1.print("$");
  Serial1.print(wifiPass);
  Serial1.print("\n");
  Serial1.flush();
}

// Sets a inputBuffer to display on the bottom row for 5 seconds
void setCurrentMessage(const char inputBuffer[])
{
  lcd.clear();
  currentMessageStart = millis();
  inputBufferActive = true;

  // Resets current inputBuffer
  for (int i = 0; i < 16; i++)
    currentMessage[i] = '\0';
  for (int i = 0; i < strlen(inputBuffer); i++)
  {
    currentMessage[i] = inputBuffer[i];
  }
}

void resetCharArray(char *arr, int len)
{
  for (int i = 0; i < len; i++)
  {
    arr[i] = '\0';
  }
}

void setup() {
  Serial.begin(9600);    // Baud rate for the arduino
  Serial1.begin(74880);  // Baud rate for wifi chip which is connected to serial1
  pinMode(DOOR_SENSOR_PIN, INPUT_PULLUP); // Controls door switch
  pinMode(SIREN_PIN, OUTPUT); // Flashing light and siren
  lcd.begin(16, 2);   // set up the LCD's number of columns and rows:

  bool readPasscode = true;
  // If EEPROM has never been written to, tell the program to not read the passcode from memory
  // Checked by seeing if memory address 0 contains the default value (255)
  if (EEPROM.read(0) == 255)
    readPasscode = false;

  for (int i = 0; i < CODE_LENGTH; i++)
  {
    newCode[i] = '\0';

    // If passcode has never been saved, load default (5 0's)
    if (!readPasscode)
      code[i] = '0';
    else
      code[i] = (char) EEPROM.read(i);
  }
  
  resetCharArray(currText, 63);
  Serial.println(code); // Prints the active passcode on startup to the computer
  lastDoorState = digitalRead(DOOR_SENSOR_PIN);
}

// Sets a new passcode
void setNewCode(const char c[])
{
  for (int i = 0; i < CODE_LENGTH; i++)
  {
    code[i] = c[i];
    // Writes the new passcode to memory
    EEPROM.write(i, (byte) c[i]);
  }
  setCurrentMessage(MESSAGE_PASSCODE_SET);
}

void setInputMode(int mode)
{
  // Only change it if it's changing to something different
  if (inputMode != mode)
  {
    // Reset variables and set mode
    inputMode = mode;
    inputStage = 0;
    caretPosition = 0;
    resetCharArray(newCode, 5);
    resetCharArray(currText, sizeof(currText) / sizeof(char));
    pressedNum = 1;
    lastPressed = '\0';
    lcd.clear();
  }
}

// Based on the character you pressed and how many times, a different character is returned
/*
   (1ABCabc) (2DEFdef) (3GHIghi)
   (4JKLjkl) (5MNOmno) (6PQRpqr)
   (7STUstu) (8VWXvwx) (9YZyz)
   (   *   ) (   0   ) (#_!@$ '`"\&^*().,/[]{}-+=~|)
*/
char getKeypadChar(char c, int num)
{
  if (c == '0')
    return '0';

  if (c == '*')
    return '*';

  char result = '\0';
  if (c >= '1' && c <= '8')
  {
    if (num == 1) // First press on digits 1 - 9
      result = c;
    else if (num < 5) // 2nd 3rd and 4th characters on 1 - 8
      result = (char) (c + 16 + num - 2 + (c - '1') * 2);
    else if (num < 8) // 5th 6th and 7th characters on 1 - 8
      result = (char) (c + 49 + num - 6 + (c - '1') * 2);
    return result;
  }
  else if (c == '9')
  {
    if (pressedNum > 5)
      pressedNum = 1;
    num %= 6;
    if (num < 1)
      num = 1;
    switch (num)
    {
      case 1:
        return '9';
        break;
      case 2:
        return 'Y';
        break;
      case 3:
        return 'Z';
        break;
      case 4:
        return 'y';
        break;
      case 5:
        return 'z';
        break;
      default:
        return '?';
    }
  }
  return '\0'; // Default return value
}

void doWifiSetup(char pressed)
{
  // 'C' cancels code change
  if (pressed == 'C')
  {
    setInputMode(INPUT_MODE_NORMAL);
    setCurrentMessage("CANCELLED");
    return;
  }
  else if (pressed == 'B' && caretPosition > 0) // Backspace
  {
    currText[caretPosition] = '\0';
    caretPosition--;
  }

  // Stage 0, inputting the SSID
  if ((pressed >= '0' && pressed <= '9') || pressed == '*' || pressed == '#')
  {
    if (pressedNum > 7)
      pressedNum = 1;

    if (pressed != lastPressed)
      pressedNum = 1;
    currText[caretPosition] = getKeypadChar(pressed, pressedNum);
    pressedNum++;
  }
  else if (pressed == 'A' && currText[caretPosition] != '\0') // Sumbit character
  {
    caretPosition++; // Move caret forward
    pressedNum = 1;
  }
  else if (pressed == 'D') // Done
  {
    if(inputStage == 0) // SSID entry
    {
      inputStage = 1;
      for(int i = 0; i < 32; i++)
      {
        EEPROM.write(CODE_LENGTH + i, currText[i]); // Save network name
      }
      resetCharArray(currText, 63);
      caretPosition = 0;
    }
    else if(inputStage == 1) // PASSWORD Entry
    {
      for(int i = 0; i < 63; i++)
        EEPROM.write(CODE_LENGTH + 32 + i, currText[i]); // Save wifi password
      resetCharArray(currText, 63);
      caretPosition = 0;
      inputStage = 2;
      sendEspWifiCredentials();
    }
  }


  lastPressed = pressed;
}

// Handles input during a passcode change
void doPasscodeChange(char pressed)
{
  // 'C' cancels code change
  if (pressed == 'C')
  {
    setInputMode(INPUT_MODE_NORMAL);
    setCurrentMessage("CANCELLED");
    return;
  }
  // 'B' is backspace
  else if (pressed == 'B')
  {
    caretPosition--;
    currText[caretPosition] = '\0';
  }

  // New passcode has not been entered for the first time (the last character of newCode hasn't been set yet)
  if (newCode[CODE_LENGTH - 1] == '\0' && pressed >= '0' && pressed <= '9')
  {
    newCode[caretPosition] = pressed; // The code character at the caret is set to pressed
    currText[caretPosition] = pressed;
    caretPosition++;
  }
  else if (newCode[CODE_LENGTH - 1] != '\0' && inputStage != 1 && pressed == '*')
  {
    inputStage = 1;
    caretPosition = 0;
    resetCharArray(currText, sizeof(currText) / sizeof(char));
    lcd.clear();
  }
  else if (inputStage == 1)
  {
    // Checks if full code hasn't been entered yet
    if (currText[CODE_LENGTH - 1] == '\0')
    {
      currText[caretPosition] = pressed;
      caretPosition++;
    }
    else if (currText[CODE_LENGTH - 1] != '\0' && pressed == 'A') // If you have entered for a second time and hit 'A'
    {
      // Checks if the codes match
      bool codesMatch = true;
      for (int i = 0; i < CODE_LENGTH; i++)
      {
        if (newCode[i] != currText[i])
        {
          codesMatch = false;
          break;
        }
      }

      // If codes match
      if (codesMatch)
      {
        Serial1.print(">Code_Change");
        for(int i = 0; i < CODE_LENGTH; i++)
          Serial1.print(lastCodeUsed[i]);
        Serial1.println();
        setNewCode(newCode); // Set new code
      }
      else
        setCurrentMessage(MESSAGE_PASSCODES_DONT_MATCH); // Tell the user the codes don't match
      setInputMode(INPUT_MODE_NORMAL); // Either way return to home
    }
  }
}

// Handles input when the system is at the 'home' screen
void doNormalInput(char pressed)
{
  // In the home screen a passcode must be entered in order to do anything
  if (caretPosition < CODE_LENGTH) // If the passcode has not been entered yet
  {
    // This loop sets the next character to whatever key was pressed in the input array
    currText[caretPosition] = pressed;
    caretPosition++;
  }
  else // Full passcode length of characters has been entered
  {
    if (validateCode(currText)) // Checks if the code entered is correct before proceeding to alarm functions
    {
      // Code was accepted, save it as last used code
      for(int i = 0; i < CODE_LENGTH; i++)
        lastCodeUsed[i] = currText[i];
      
      // If the alarm is sounding, disable the alarm
      if (alarmSounding)
      {
        alarmSounding = false;
        armed = false;
      }
      else // Code is correct; perform alarm functions depending on what key is pressed
      {
        // 'A' is pressed - Arm or Disarm
        if (pressed == 'A')
        {
          armed = !armed;
          // Tell the esp to tell the server that the alarm was armed / disarmed
          Serial1.print(">");
          if(armed)
            Serial1.print("armed");
          else
            Serial1.print("disarmed");
          for(int i = 0; i < CODE_LENGTH; i++)
            Serial1.print(lastCodeUsed[i]);
          Serial1.println();
          lcd.clear(); // Refresh the display to indicate that the system is armed
        }
        // '*' is pressed - initiate code change
        else if (pressed == '*')
        {
          setInputMode(INPUT_MODE_PASSCODE_CHANGE);
        }
        else if (pressed == 'C')
        {
          setInputMode(INPUT_MODE_WIFI_SETUP);
        }
        else
        {
          setCurrentMessage(MESSAGE_INVALID_INPUT);
        }
      }
    }
    else // Passcode was wrong
    {
      // Let the user know that the code entered is invalid
      setCurrentMessage(MESSAGE_INCORRECT_CODE);
    }
    
    caretPosition = 0;
    resetCharArray(currText, sizeof(currText) / sizeof(char));
  }
}

// Handles all keypad input
void checkInput()
{
  char keyPressed = customKeypad.getKey(); // Key pressed

  caretOn = (millis() % 1000) > 500; // Code to make the caret blink on / off every 500ms

  // Keypad input is disabled while a inputBuffer is being displayed
  if (keyPressed && !inputBufferActive) {
    if (inputMode == INPUT_MODE_NORMAL)
    {
      doNormalInput(keyPressed);
    }
    else if (inputMode == INPUT_MODE_PASSCODE_CHANGE)
    {
      doPasscodeChange(keyPressed);
    }
    else if (inputMode == INPUT_MODE_WIFI_SETUP)
    {
      doWifiSetup(keyPressed);
    }
  }

}

void updateLCD()
{
  lcd.setCursor(0, 0);
  // Displays password change instructions on the first row
  if (inputMode == INPUT_MODE_PASSCODE_CHANGE)
  {
    // If the new code was entered once already, prompt the user to validate it
    if (inputStage == 1)
      lcd.print("Confirm new code:");
    // Prompt the user to enter a new code
    else
      lcd.print("Enter new code: ");
  }
  else if (inputMode == INPUT_MODE_WIFI_SETUP)
  {
    if (inputStage == 0)
      lcd.print("SSID:");
    else if(inputStage == 1)
      lcd.print("PASSWORD:");
    else if(inputStage == 2)
      lcd.print("Connecting...");
  }
  else
  {
    // Displays armed status on the top row
    if (!armed)
      lcd.print("NOT ");
    lcd.print("ARMED");

    if (alarmSounding)
      lcd.print(" (!)");
  }

  lcd.setCursor(0, 1);
  if (inputBufferActive)
  {
    if (millis() - currentMessageStart >= LCD_MESSAGE_DURATION) // Message has expired, get rid of it
    {
      inputBufferActive = false;
      for (int i = 0; i < 16; i++)
        currentMessage[i] = '\0';
      lcd.clear();
    }
    lcd.print(currentMessage);
  }
  else if (inputMode == INPUT_MODE_WIFI_SETUP)
  {
    for (int i = 0; i < 16 + 15; i++)
    {
      if (currText[i] == '\0' && i == caretPosition)
      {
        if (caretOn)
          lcd.print('_');
        else
          lcd.print(' ');
      }
      else if (currText[i] != '\0')
      {
        lcd.print(currText[i]);
      }
      else
        lcd.print(' ');
    }
  }
  else
  {
    // Draws stars for every character of the code entered
    for (int i = 0; i < CODE_LENGTH; i++)
    {
      if (i == caretPosition && caretOn)      // Displays an underscore when caret is active
        lcd.print('_');
      else if ((i == caretPosition && !caretOn) || currText[i] == '\0') // Draws a space where caret should be if inactive
        lcd.print(' ');
      else
        lcd.print('*');                       // Draws a star otherwise
    }
  }
}

void checkAlarmTriggered()
{
  bool doorPin = digitalRead(DOOR_SENSOR_PIN);
  // If the door switch detects a change, let the server know
  if(lastDoorState != doorPin)
  {
    if(doorPin)
      Serial1.println(">OPENED");
    else 
      Serial1.println(">CLOSED");
    lastDoorState = doorPin;
  }
  
  // Checks if alarm trigger mechanism is triggered (when DOOR_SENSOR_PIN reads high)
  if (doorPin && armed && !alarmSounding)
  {
    Serial1.println(">trig"); // Arduino tells the esp which tells the server that the alarm went off
    alarmSounding = true;
  }

  // If the alarm is going off make an annoying buzzing sound
  if (alarmSounding)
    digitalWrite(SIREN_PIN, LOW); // Siren on
  else
    digitalWrite(SIREN_PIN, HIGH); // Siren is off
}

// Checks if the wifi module is trying to communicate
void checkEspInput()
{
  char inputBuffer[64];
  int charsRead = 0;
  // While there is input available read it into a char array
  if (Serial1.available() > 0)
  {
    charsRead = Serial1.readBytesUntil('\n', inputBuffer, sizeof(inputBuffer) - 1);
    inputBuffer[charsRead] = NULL;
  }
  
  // Parse input here
  if (charsRead > 0) // Something was read
  {
    Serial.print("ESP said: ");
    Serial.println(inputBuffer);
    if(inputBuffer[0] == 'O' && inputBuffer[1] == 'N' && (millis() - lastSentWifiCreds > 20000 || lastSentWifiCreds == -1))
    {
      sendEspWifiCredentials();
    }
    
    if(inputMode == INPUT_MODE_WIFI_SETUP && inputStage == 2)
    { 
      bool match = true;
      char connectMessage[] = "Connected";
      for(int i = 0; i < 9; i++)
        if(inputBuffer[i] != connectMessage[i])
          match = false;
      if(match) // Esp confirms wifi is connected! Yay
      {
        setCurrentMessage("Wifi Connected!");
        setInputMode(INPUT_MODE_NORMAL);
      }
      else if(millis() - lastSentWifiCreds >= CONNECTION_TIMEOUT) // Connection timed out
      {
        setCurrentMessage("Failed.");
        setInputMode(INPUT_MODE_NORMAL);
      }
    }
    
    // Check codes here
    // TODO: Why are these codes 2 digits anyways? there wont be more than 10 commands?
    if (inputBuffer[0] == '6' && inputBuffer[1] == '5') // Code to arm
    {
      armed = true;
      setCurrentMessage("R: Armed");
    }
    else if (inputBuffer[0] == '7' && inputBuffer[1] == '5') // Code to disarm
    {
      armed = false;
      setCurrentMessage("R: Disarmed");
    }
    else if (inputBuffer[0] == '5' && inputBuffer[1] == '5') // Code to initiate passcode change
    {
      char cd[CODE_LENGTH];
      for (int i = 0; i < CODE_LENGTH; i++)
        cd[i] = inputBuffer[3 + i];
      Serial1.println(">R:Code_Change");
      setNewCode(cd);
    }
    else if (inputBuffer[0] == '4' && inputBuffer[1] == '5') // Sound alarm
    {
      alarmSounding = true;
      setCurrentMessage("R: Sounding");
    }
    else if (inputBuffer[0] == '3' && inputBuffer[1] == '5') // Mute alarm
    {
      alarmSounding = false;
      armed = false;
      setCurrentMessage("R: Muted");
    }
  }
}

void loop() {
  checkEspInput();
  checkInput();
  updateLCD();
  checkAlarmTriggered();
}
