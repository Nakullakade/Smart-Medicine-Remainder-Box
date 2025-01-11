#include <ESP8266WiFi.h>
#include <PubSubClient.h>
#include <Servo.h>
#include <LiquidCrystal_I2C.h>

// WiFi and MQTT settings
const char* ssid = "finalproject";
const char* password = "finalproject";
const char* mqttServer = "43.204.177.85";
const int mqttPort = 1883;

WiFiClient espClient;
PubSubClient client(espClient);

// Pins and servo setup
#define Buzzer D6
#define IR1 D0
#define IR2 D3
#define IR3 D8
#define S1pin 13
#define S2pin 2
#define S3pin 14

Servo S1A, S2, S3;

LiquidCrystal_I2C lcd(0x27, 20, 4);
String Med1StockName = "M1", Med2StockName = "M2", Med3StockName = "M3";
int Med1Stock = 0, Med2Stock = 0, Med3Stock = 0;

int Closing_angle = 10;
int Opening_angle = 180;
int CurrentAngle = Closing_angle;

bool boxOpen = false;

bool m1Open = false;
bool m2Open = false;
bool m3Open = false;

bool m1NotTaken = false;
bool m2NotTaken = false;
bool m3NotTaken = false;

unsigned long m1OpenTimer = 0, m2OpenTimer = 0, m3OpenTimer = 0;
unsigned long m1NotTakenTimer = 0, m2NotTakenTimer = 0, m3NotTakenTimer = 0;

unsigned long openTimer = 30000;
unsigned long MedOpenTimer = 10000;
unsigned long MedNotTakenTimer = 10000;

String dtime = "00:00";
String ddate = "00/00/00";

String show = "time";

// Structure to hold the date and time components
struct DateTimeComponents {
    int year, month, day, hour, minute, second;
};

unsigned long previousMillis = 0;  // will store last time the function was called
const long interval = 5000;        // interval at which to call function (milliseconds)

// Function prototypes
void callback(char* topic, byte* payload, unsigned int length);
void reconnect();
void setup_wifi();
void publishData(String topic, String message);

// Function to parse the datetime string
bool parseDateTime(const char* dateTimeStr, DateTimeComponents &dtc) {
    char monthStr[4];
    // Parse the string; Example format: "Sat May 04 2024 02:45:16 GMT+0530"
    if (sscanf(dateTimeStr, "%*s %s %d %d %d:%d:%d",
               monthStr, &dtc.day, &dtc.year, &dtc.hour, &dtc.minute, &dtc.second) == 6) {
        dtc.month = monthStrToInt(monthStr);
        dtc.year = dtc.year % 100; // Convert yyyy to yy
        // Debug outputs to verify correct parsing
        Serial.print("Parsed Date: ");
        Serial.print(dtc.day);
        Serial.print("/");
        Serial.print(dtc.month);
        Serial.print("/");
        Serial.print(dtc.year);
        Serial.print(" Time: ");
        Serial.print(dtc.hour);
        Serial.print(":");
        Serial.print(dtc.minute);
        Serial.println();

        return true;
    }
    return false;
}

// Helper function to convert month string to integer
int monthStrToInt(const char* monthStr) {
    const char* months[] = {"Jan", "Feb", "Mar", "Apr", "May", "Jun",
                            "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};
    for (int i = 0; i < 12; i++) {
        if (strcmp(monthStr, months[i]) == 0) {
            return i + 1;
        }
    }
    return 0;
}

// Function to get formatted date string
String getDate(const char* dateTimeStr) {
    DateTimeComponents dtc;
    if (parseDateTime(dateTimeStr, dtc)) {
        char dateStr[9]; // Enough for "dd/mm/yy"
        sprintf(dateStr, "%02d/%02d/%02d", dtc.day, dtc.month, dtc.year);
        return String(dateStr);
    }
    return String("Invalid Date");
}

// Function to get formatted time string
String getTime(const char* dateTimeStr) {
    DateTimeComponents dtc;
    if (parseDateTime(dateTimeStr, dtc)) {
        char timeStr[6]; // Enough for "hh:mm"
        sprintf(timeStr, "%02d:%02d", dtc.hour, dtc.minute);
        Serial.println("Time to show is "+String(timeStr));
        return String(timeStr);
    }
    return String("Invalid Time");
}

void publishData(String topic, String message) {
  // Check if connected to MQTT broker
  if (client.connected()) {
    // Publish the message
    client.publish(topic.c_str(), message.c_str());
    Serial.println("Published to topic: " + topic + ", message: " + message);
  } else {
    Serial.println("Failed to publish. Not connected to MQTT broker.");
  }
}

void OpenBox(int no) {
  ControlServo(Opening_angle, no);
  delay(3000);
}
void CloseBox(int no) {
  ControlServo(Closing_angle, no);
  delay(3000);
}

void showTimeDate() {
  if (show == "time") {
    lcd.clear();
    lcd.setCursor(0, 0);
    lcd.print("Time: " + dtime);
    lcd.setCursor(0, 2);
    lcd.print("Date: " + ddate);
  }
}

void showMedStock() {
  if (show == "med") {
    lcd.clear();
    lcd.setCursor(0, 0);
    lcd.print("   Medicine Stock");
    lcd.setCursor(0, 1);
    lcd.print(Med1StockName +" - "+ Med1Stock);
    lcd.setCursor(0, 2);
    lcd.print(Med2StockName +" - "+ Med2Stock);
    lcd.setCursor(0, 3);
    lcd.print(Med3StockName +" - "+ Med3Stock);
  }
}

void showMsg() {
  if (m1Open) {
    lcd.clear();
    lcd.setCursor(0, 0);
    lcd.print("Take Medicine!");
    lcd.setCursor(0, 2);
    lcd.print("Medicine Box 1 is Open!");
    delay(2000);
  }
  if (m2Open) {
    lcd.clear();
    lcd.setCursor(0, 0);
    lcd.print("   Take Medicine!");
    lcd.setCursor(0, 2);
    lcd.print("Medicine Box 2 is Open!");
    delay(2000);
  }
  if (m3Open) {
    lcd.clear();
    lcd.setCursor(0, 0);
    lcd.print("   Take Medicine!");
    lcd.setCursor(0, 2);
    lcd.print("Medicine Box 3 is Open!");
    delay(2000);
  }
}

void showAlert() {
  if (show == "alert") {
    if (m1NotTaken) {
      lcd.clear();
      lcd.setCursor(0,0);
      lcd.print("       Alert!");
      lcd.setCursor(0, 2);
      lcd.print(Med1StockName + " not taken!");
      delay(2000);
    }
    if (m2NotTaken) {
      lcd.clear();
      lcd.setCursor(0,0);
      lcd.print("       Alert!");
      lcd.setCursor(0, 2);
      lcd.print(Med2StockName + " not taken!");
      delay(2000);
    }
    if (m3NotTaken) {
      lcd.clear();
      lcd.setCursor(0,0);
      lcd.print("       Alert!");
      lcd.setCursor(0, 2);
      lcd.print(Med3StockName + " not taken!");
      delay(2000);
    }
  }
}

bool isAlert() {
  return m1NotTaken || m2NotTaken || m3NotTaken;
}

void publishMedicineTaken(int medicineNumber) {
  // Create a topic for medicine taken
  String topic = "medication/taken";

  // Create a payload with medicine number
  String payload = String(medicineNumber);

  // Publish the message
  publishData(topic, payload);
}

void publishMedicineNotTaken(int medicineNumber) {
  // Create a topic for medicine not taken
  String topic = "medication/notTaken";

  // Create a payload with medicine number
  String payload = String(medicineNumber);

  // Publish the message
  publishData(topic, payload);
}

bool isMsg() {
  return m1Open || m2Open || m3Open;
}

void handleShowLoop() {
  unsigned long currentMillis = millis();

  if (currentMillis - previousMillis > interval) {
    Serial.println("Show : "+ show);
    previousMillis = currentMillis;
    if (show == "time") {
      if (isMsg()) {
        show = "msg";
        showMsg();
      } else if (isAlert()) {
        show = "alert";
        showAlert();
      } else {
        show = "med";
        showMedStock();
      }
    } else if (show == "msg") {
      if (isAlert()) {
        show = "alert";
        showAlert();
      } else {
        show = "med";
        showMedStock();
      }
    } else if ( show == "alert"){
      show = "med";
      showMedStock();
    }
     else {
      show = "time";
      showTimeDate();
    }
  }
}

void handleIR() {
  if (m1Open && digitalRead(IR1)==0) {
    // int value = digitalRead(IR1);
    m1Open = false;
    CloseBox(1);
    publishMedicineTaken(1);
    digitalWrite(Buzzer, LOW);

  }
  if (m2Open && digitalRead(IR2)==0) {
    // int value = digitalRead(IR2);
    m2Open = false;
    CloseBox(2);
    publishMedicineTaken(2);
    digitalWrite(Buzzer, LOW);

  }
  if (m3Open && digitalRead(IR3)==0) {
    // int value = digitalRead(IR3);
    m3Open = false;
    CloseBox(3);
    publishMedicineTaken(3);
    digitalWrite(Buzzer, LOW);

  }
}

void handleBoxStatus() {
  unsigned long currentMillis = millis();
  if (m1Open && (currentMillis - m1OpenTimer) > MedOpenTimer) {
    //too long box is open. close box and set alert flag
    m1Open = false;
    m1NotTaken = true;
    m1NotTakenTimer = currentMillis;
    digitalWrite(Buzzer, LOW);
    publishMedicineNotTaken(1);
    Serial.println("Med 1 not taken");
    CloseBox(1);
  }
  if (m2Open && (currentMillis - m2OpenTimer) > MedOpenTimer) {
    //too long box is open. close box and set alert flag
    m2Open = false;
    m2NotTaken = true;
    m2NotTakenTimer = currentMillis;
    digitalWrite(Buzzer, LOW);
    publishMedicineNotTaken(2);
    Serial.println("Med 2 not taken");
    CloseBox(2);

  }
  if (m3Open && (currentMillis - m3OpenTimer) > MedOpenTimer) {
    //too long box is open. close box and set alert flag
    m3Open = false;
    m3NotTaken = true;
    m3NotTakenTimer = currentMillis;
    digitalWrite(Buzzer, LOW);
    publishMedicineNotTaken(3);
    Serial.println("Med 3 not taken");
    CloseBox(3);

  }

  if (m1NotTaken && (currentMillis - m1NotTakenTimer) > MedNotTakenTimer) {
    m1NotTaken = false;
  }
  if (m2NotTaken && (currentMillis - m2NotTakenTimer) > MedNotTakenTimer) {
    m2NotTaken = false;
  }
  if (m3NotTaken && (currentMillis - m3NotTakenTimer) > MedNotTakenTimer) {
    m3NotTaken = false;
  }
}

void ControlServo(int a, int S) {

  // S1A.write(a);

  Serial.println("SERVO :" + String(S));
  Serial.println("Target = " + String(a));
  Serial.println("CurrentAngle = " + String(CurrentAngle));
  if (a > CurrentAngle)  //(180 > 90)
  {
    // Serial.println("a > currA");     // 90 <180
    for (; CurrentAngle <= a; CurrentAngle = CurrentAngle + 5) {
      // Serial.println("c angle :"+ String(CurrentAngle));

      if (S == 1) {
        S1A.write(CurrentAngle);
      }
      if (S == 2)
        S2.write(CurrentAngle);
      if (S == 3)
        S3.write(CurrentAngle);

      delay(50);
    }
    if (S == 1) {

      S1A.write(a);
      // Serial.println("--------------------S1A Contrlling");
    }
    if (S == 2)
      S2.write(a);
    if (S == 3)
      S3.write(a);

  } else {
    for (; CurrentAngle >= a; CurrentAngle = CurrentAngle - 5) {
      if (S == 1) {

        S1A.write(CurrentAngle);
        // Serial.println("--------------------S1A Contrlling");
      }
      if (S == 2)
        S2.write(CurrentAngle);
      if (S == 3)
        S3.write(CurrentAngle);

      delay(50);
    }
    if (S == 1) {

      // Serial.println("--------------------S1A Contrlling");
      S1A.write(a);
    }
    if (S == 2)
      S2.write(a);
    if (S == 3)
      S3.write(a);
  }
  Serial.println("CurrentAngle = " + String(CurrentAngle));
}

void setup_wifi() {
  delay(10);
  // We start by connecting to a WiFi network
  Serial.println();
  Serial.print("Connecting to ");
  Serial.println(ssid);

  WiFi.begin(ssid, password);

  while (WiFi.status() != WL_CONNECTED) {
    delay(500);
    Serial.print(".");
  }

  Serial.println("");
  Serial.println("WiFi connected");
  Serial.println("IP address: ");
  Serial.println(WiFi.localIP());
}

void setup() {
  Serial.begin(115200);
  setup_wifi();
  client.setServer(mqttServer, mqttPort);
  client.setCallback(callback);

  S1A.attach(S1pin);
  S2.attach(S2pin);
  S3.attach(S3pin);

  S1A.write(10);
  S2.write(10);
  S3.write(10);

  pinMode(Buzzer, OUTPUT);
  digitalWrite(Buzzer, LOW);

  // Set IR sensor pins as inputs
  pinMode(IR1, INPUT);
  pinMode(IR2, INPUT);
  pinMode(IR3, INPUT);

  lcd.init();
  lcd.backlight();
  lcd.print("Connecting to MQTT");
}

void loop() {
  if (!client.connected()) {
    reconnect();
  }
  client.loop();
  handleShowLoop();
  // showAlert();
  handleIR();
  handleBoxStatus();
}

void reconnect() {
  while (!client.connected()) {
    Serial.print("Attempting MQTT connection...");
    if (client.connect("ESP8266Client")) {
      Serial.println("connected");
      lcd.setCursor(0, 0);
      lcd.print("Connected.Subing...");

      // Subscribe to topics
      client.subscribe("medication/stock");
      client.subscribe("medication/time");
      client.subscribe("medication/open");
    } else {
      Serial.print("failed, rc=");
      Serial.print(client.state());
      Serial.println(" try again in 5 seconds");
      delay(5000);
    }
  }
}

void callback(char* topic, byte* payload, unsigned int length) {
  Serial.print("Message arrived [");
  Serial.print(topic);
  Serial.print("] ");

  // Convert payload to a string
  String message;
  for (int i = 0; i < length; i++) {
    message += (char)payload[i];
  }
  Serial.println(message);

  // Check the topic and take appropriate actions
  if (String(topic) == "medication/stock") {
    // Assuming message format is "med1Name,val1,med2Name,val2,med3Name,val3"
    int commaIndex1 = message.indexOf(',');
    int commaIndex2 = message.indexOf(',', commaIndex1 + 1);
    int commaIndex3 = message.indexOf(',', commaIndex2 + 1);
    int commaIndex4 = message.indexOf(',', commaIndex3 + 1);
    int commaIndex5 = message.indexOf(',', commaIndex4 + 1);

    // Extract stock names and values for each medicine from the message
    String med1Name = message.substring(0, commaIndex1);
    int val1 = message.substring(commaIndex1 + 1, commaIndex2).toInt();
    String med2Name = message.substring(commaIndex2 + 1, commaIndex3);
    int val2 = message.substring(commaIndex3 + 1, commaIndex4).toInt();
    String med3Name = message.substring(commaIndex4 + 1, commaIndex5);
    int val3 = message.substring(commaIndex5 + 1).toInt();

    // Assign values to respective variables
    Med1StockName = med1Name;
    Med1Stock = val1;
    Med2StockName = med2Name;
    Med2Stock = val2;
    Med3StockName = med3Name;
    Med3Stock = val3;
  } else if (String(topic) == "medication/time") {
    // Extract time and date from the message
    dtime = getTime(message.c_str());
    ddate = getDate(message.c_str());
    // Here you can update variables or do something with these times
    // Example: Set an alarm, update display, etc.
  } else if (String(topic) == "medication/open") {
    Serial.println("Med Open "+ message);
    if (message == "1") {
      OpenBox(1);
      digitalWrite(Buzzer, HIGH);
      m1Open = true;
      m1OpenTimer = millis();
    } else if (message == "2") {
      OpenBox(2);
      digitalWrite(Buzzer, HIGH);
      m2Open = true;
      m2OpenTimer = millis();
    } else if (message == "3") {
      OpenBox(3);
      digitalWrite(Buzzer, HIGH);
      m3Open = true;
      m3OpenTimer = millis();
    }
  }
}
