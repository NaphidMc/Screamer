#include <Keypad.h>
#include <LiquidCrystal.h>
#include <EEPROM.h>

// Keypad setup
const byte KEYPAD_ROWS = 4; 
const byte KEYPAD_COLS = 4; 

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
const int rs = 9, en = 8, d4 = 10, d5 = 11, d6 = 12, d7 = 13;
LiquidCrystal lcd(rs, en, d4, d5, d6, d7);

const int alarmTriggerPin = 51, sirenPin = 50;

const int CODE_LENGTH = 5, CURRENT_MESSAGE_DURATION = 3000;
bool armed = false, currTextFull = false, messageActive = false, passwordChangeInProgress = false, validateNewCode = false,
     alarmSounding = false;
char code[CODE_LENGTH], currText[CODE_LENGTH], newCode[CODE_LENGTH],
     currentMessage[16];
unsigned long currentMessageStart = 0;

// Checks if the text entered matches the current code
bool validateCode(char c[])
{   
  for(int i = 0; i < CODE_LENGTH; i++)
  {
    if(c[i] != code[i])
    {
      return false;
    }
  }

  return true;
}

// Sets a message to display on the bottom row for 5 seconds
void setCurrentMessage(const char message[])
{
  lcd.clear();
  currentMessageStart = millis();
  messageActive = true;
  
  // Resets current message
  for(int i = 0; i < 16; i++)
    currentMessage[i] = '\0';
  for(int i = 0; i < strlen(message); i++)
  {
    currentMessage[i] = message[i];
  }
}

void setup(){
  Serial.begin(9600);
  Serial1.begin(115200); // Baud rate for wifi chip which is connected to serial1
  pinMode(alarmTriggerPin, INPUT_PULLUP); // Controls door switch
  pinMode(sirenPin, OUTPUT); // Flashing light and siren
  // set up the LCD's number of columns and rows:
  lcd.begin(16, 2);
  
  bool readPasscode = true;
  // If EEPROM has never been written to, tell the program to not read the passcode from memory
  // Checked by seeing if memory address 0 contains the default value (255)
  if(EEPROM.read(0) == 255)
    readPasscode = false;

  for(int i = 0; i < CODE_LENGTH; i++)
  {
    newCode[i] = '\0';
    currText[i] = '\0';

    // If passcode has never been saved, load default (5 0's)
    if(!readPasscode)
      code[i] = '0';
    else
      code[i] = (char) EEPROM.read(i);
  }

  Serial.println(code); // Prints the active passcode on startup to the computer
}

void setNewCode(const char c[])
{
  for(int i = 0; i < CODE_LENGTH; i++)
  {
    code[i] = c[i];
    // Writes the new passcode to memory
    EEPROM.write(i, (byte) c[i]);
  }
  setCurrentMessage("PASSCODE SET");
}

// Handles all keypad input
void checkInput()
{
  char keyPressed = customKeypad.getKey();

  // Keypad input is disabled while a message is being displayed
  if (keyPressed && !messageActive){
    if(!currTextFull)
    {
      for(int i = 0; i < CODE_LENGTH; i++)
      {
        if(currText[i] == '\0')
        {
          currText[i] = keyPressed;
          currTextFull = (i == CODE_LENGTH - 1);
          break;
        }
      }
    }
    else
    {
      currTextFull = false;
      // If password change is in progress
      if(passwordChangeInProgress)
      {
        // If code has NOT been entered once already
        if(newCode[0] == '\0')
        {
          // Set the new code equal to current text entered
          for(int i = 0; i < CODE_LENGTH; i++)
          {
            newCode[i] = currText[i];
          }
          // User should be prompted to confirm new code
          validateNewCode = true;
        }
        else
        {
          // Checks if the codes match
          bool codesMatch = true;
          for(int i = 0; i < CODE_LENGTH; i++)
          {
            if(newCode[i] != currText[i])
            {
              codesMatch = false;
              break;
            }
          }

          // Codes match, set new passcode
          if(codesMatch)
          {
            setNewCode(newCode);
          }
          else
          {
            setCurrentMessage("CODES DONT MATCH");
          }
          
          // Reset passcode change variables
          validateNewCode = false;
          passwordChangeInProgress = false;
          for(int i = 0; i < CODE_LENGTH; i++)
            newCode[i] = '\0';
        }
      }
      else
      {
        if(validateCode(currText)) // Checks if the code entered is correct before proceeding to alarm functions
        {
          // If the alarm is sounding, disable the alarm
          if(alarmSounding)
          {
            alarmSounding = false;
            armed = false;
          }
          else // Otherwise do normal alarm functions depending on what key is pressed 
          {
            // 'A' is pressed - Arm or Disarm
            if(keyPressed == 'A')
            {
              armed = !armed;
            }
            // '*' is pressed - initiate code change
            else if(keyPressed == '*')
            {
              passwordChangeInProgress = true;
            }
          }
        }
        else
        {
          // Let the user know that the code entered is invalid
          Serial.println("Hello, invalid");
          setCurrentMessage("INVALID CODE");
          Serial.println(millis() + ' ');
          Serial.println(currentMessageStart + ' ');
        }
      }

      // Resets currText
      for(int i = 0; i < CODE_LENGTH; i++)
      {
        currText[i] = '\0';
        lcd.clear();
      }
    }
  }

}

void updateLCD()
{
  lcd.setCursor(0, 0);
  // Displays password change instructions on the first row
  if(passwordChangeInProgress)
  {
    // If the new code was entered once already, prompt the user to validate it
    if(validateNewCode)
      lcd.print("Confirm new code:");
    // Prompt the user to enter a new code
    else
      lcd.print("Enter new code: ");
  }
  else
  {
    // Displays armed status on the top row
    if(!armed)
      lcd.print("NOT ");
    lcd.print("ARMED");

    if(alarmSounding)
      lcd.print(" (SOUNDING)");
  }
  
  lcd.setCursor(0, 1);
  if(messageActive)
  {
    if(millis() - currentMessageStart >= CURRENT_MESSAGE_DURATION) // Message has expired, get rid of it
    {
      messageActive = false;
      for(int i = 0; i < 16; i++)
        currentMessage[i] = '\0';
      lcd.clear();
    }
    lcd.print(currentMessage);
  }
  else
  {   
    // Draws stars for every character of the code entered
    for(int i = 0; i < CODE_LENGTH; i++)
    {
      if(currText[i] == '\0')
        break;
      else 
        lcd.print('*');
    }
  }

}

void checkAlarmTriggered()
{
    
  // Checks if alarm trigger mechanism is triggered
  if(digitalRead(alarmTriggerPin) && armed && !alarmSounding)
  {
    Serial1.println("trig"); // Arduino tells the esp which tells the server that the alarm went off
    alarmSounding = true;
  }

  // If the alarm is going off make an annoying buzzing sound
  if(alarmSounding)
  {
    digitalWrite(sirenPin, LOW); // Siren on
  }
  else
    digitalWrite(sirenPin, HIGH); // Siren is off
}

// Checks if the wifi module is trying to communicate
void checkEspInput()
{
  char message[12] = {'\0', '\0', '\0', '\0', '\0', '\0', '\0', '\0', '\0', '\0', '\0', '\0'};
  int index = 0;
  // While there is input available read it into a char array
  while(Serial1.available() > 0)
  {
    message[index] = (char) Serial1.read();
    index++;
  }
  
  // Parse input here
  if(message[0] != '\0') // Something was read
  {
    // Check codes here
    // TODO: Why are these codes 2 digits anyways? there wont be more than 10 commands? 
    if(message[0] == '6' && message[1] == '5') // Code to arm
    {
      armed = true;
      setCurrentMessage("R: Armed");
    }
    else if(message[0] == '7' && message[1] == '5') // Code to disarm
    {
      armed = false;
      setCurrentMessage("R: Disarmed");
    }
    else if(message[0] == '5' && message[1] == '5') // Code to initiate passcode change
    {
       passwordChangeInProgress = false;
       char cd[CODE_LENGTH];
       for(int i = 0; i < CODE_LENGTH; i++)
         cd[i] = message[3 + i];
       setNewCode(cd);
    }
    else if(message[0] == '4' && message[1] == '5') // Sound alarm
    {
      alarmSounding = true;
      setCurrentMessage("R: Sounding");
    }
    else if(message[0] == '3' && message[1] == '5') // Mute alarm 
    {
      alarmSounding = false;
      armed = false;
      setCurrentMessage("R: Muted");
    }
  }
}

void loop(){
  checkEspInput();
  checkInput();
  updateLCD();
  checkAlarmTriggered();
}
