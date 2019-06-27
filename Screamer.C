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
byte colPins[KEYPAD_COLS] = {17, 18, 19, 20}; 
Keypad customKeypad = Keypad(makeKeymap(keys), rowPins, colPins, KEYPAD_ROWS, KEYPAD_COLS); 

// LCD Setup
const int rs = 9, en = 8, d4 = 10, d5 = 11, d6 = 12, d7 = 13;
LiquidCrystal lcd(rs, en, d4, d5, d6, d7);

const int alarmTriggerPin = 51, buzzerPin = 50;

const int CODE_LENGTH = 5, CURRENT_MESSAGE_DURATION = 2000;
bool armed = true, currTextFull = false, messageActive = false, passwordChangeInProgress = false, validateNewCode = false,
     alarmSounding = false;
char code[CODE_LENGTH], currText[CODE_LENGTH], newCode[CODE_LENGTH],
     currentMessage[16];
int currentMessageStart = 0;

// Checks if the text entered matches the current code
bool validateCode(char c[])
{
  if(strlen(c) < CODE_LENGTH)
    return false;
    
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
void setCurrentMessage(char message[])
{
  if(messageActive)
    return;
    
  lcd.clear();
  currentMessageStart = millis();
  messageActive = true;
  
  // Resets current message
  for(int i = 0; i < 16; i++)
    currentMessage[i] = '\0';
  for(int i = 0; i < strlen(message) && i < 16; i++)
  {
    currentMessage[i] = message[i];
  }
}

void setup(){
  Serial.begin(9600);
  pinMode(alarmTriggerPin, INPUT);
  pinMode(buzzerPin, OUTPUT);
   // set up the LCD's number of columns and rows:
  lcd.begin(16, 2);
  
  bool readPasscode = true;
  // If EEPROM has never been written to, tell the program to not read the passcode from memory
  if(EEPROM.read(0) == 255)
    readPasscode = false;

  for(int i = 0; i < CODE_LENGTH; i++)
  {
    newCode[i] = '\0';
    currText[i] = '\0';

    // If passcode has never been saved, load defaul (all 0's)
    if(!readPasscode)
      code[i] = '0';
    else
      code[i] = (char) EEPROM.read(i);
  }
}
  
void loop(){
  char keyPressed = customKeypad.getKey();

  if (keyPressed && !messageActive){
    if(!currTextFull)
    {
      for(int i = 0; i < CODE_LENGTH; i++)
      {
        if(currText[i] == '\0')
        {
          currText[i] = keyPressed;
          currTextFull = i == CODE_LENGTH - 1;
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
            for(int i = 0; i < CODE_LENGTH; i++)
            {
              code[i] = newCode[i];
              // Writes the new passcode to memory
              EEPROM.write(i, (byte) newCode[i]);
            }
            setCurrentMessage("PASSCODE SET");
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
          alarmSounding = false;
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
        else
        {
          // Let the user know that the code entered is invalid
          setCurrentMessage("INVALID CODE");
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
  }
  
  lcd.setCursor(0, 1);
  if(messageActive)
  {
    if(millis() - currentMessageStart >= CURRENT_MESSAGE_DURATION)
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

  // Checks if alarm trigger mechanism is triggered
  if(digitalRead(alarmTriggerPin) && armed)
    alarmSounding = true;

  if(alarmSounding)
  {
    tone(buzzerPin, 500, 500);
  }
}
