const mqtt = require("mqtt");

// MQTT broker settings
const mqttBroker = "mqtt://43.204.177.85:1883";
const mqttOptions = {
  clientId: "mqtt-client",
  keepAlive: 60,
};

// MQTT topics to subscribe
const topics = [
  "medication/setStock",
  "medication/setTime",
  "medication/schedule",
  "medication/noSchedule",
  "medication/getSchedule",
  "medication/taken",
  "medication/notTaken",
  "medication/givehistory",
];

var med1StockName = "med1";
var med2StockName = "med2";
var med3StockName = "med3";

var med1Stock = 0;
var med2Stock = 0;
var med3Stock = 0;

var reftime = new Date();
var time = new Date();

var schedule1 = null;
var schedule2 = null;
var schedule3 = null;
var history = [];
var scheduleHistory = [];

// Create MQTT client
const client = mqtt.connect(mqttBroker, mqttOptions);

const medTaken = (i) => {
  i = parseInt(i);
  if (i === 1) {
    med1Stock--;
  } else if (i === 2) {
    med2Stock--;
  } else if (i === 3) {
    med3Stock--;
  }
};

// Handle MQTT connection
client.on("connect", () => {
  console.log("Connected to MQTT broker");

  // Subscribe to topics
  topics.forEach((topic) => {
    client.subscribe(topic, (err) => {
      if (err) {
        console.error("Error subscribing to topic:", err);
      } else {
        console.log("Subscribed to topic:", topic);
      }
    });
  });

  // Publish message at regular intervals
  setInterval(() => {
    // calculate newTime based on reftime and time
    const now = new Date();
    const diff = now - reftime;
    const newtime = new Date(time.getTime() + diff);
    // console.log("reftime is " + reftime.toString());
    // console.log("now is " + now.toString());
    // console.log("time is " + time.toString());
    console.log("newtime is " + newtime.toString());
    if (schedule1 != null) {
      console.log("schedule1 is " + schedule1.toString());
    }else{
      console.log("schedule1 is null");
    }
    if (schedule2 != null) {
      console.log("schedule2 is " + schedule2.toString());
    }else{
      console.log("schedule2 is null");
    }
    if (schedule3 != null) {
      console.log("schedule3 is " + schedule3.toString());
    }else{
      console.log("schedule3 is null");
    }
    

    // in scheduleHistory check medIndex and todays date
    const date = newtime.getDate().toString();
    var m1exists = false,
      m2exists = false,
      m3exists = false;
    for (var i = 0; i < scheduleHistory.length; i++) {
      if (
        scheduleHistory[i].medIndex === 1 &&
        scheduleHistory[i].date === date
      ) {
        m1exists = true;
      }
      if (
        scheduleHistory[i].medIndex === 2 &&
        scheduleHistory[i].date === date
      ) {
        m2exists = true;
      }
      if (
        scheduleHistory[i].medIndex === 3 &&
        scheduleHistory[i].date === date
      ) {
        m3exists = true;
      }
    }

    var nowHours = newtime.getHours();
    var nowMinutes = newtime.getMinutes();

    var s1Hours, s1Minutes, s2Hours, s2Minutes, s3Hours, s3Minutes;

    if (schedule1 != null) {
      s1Hours = schedule1.getHours();
      s1Minutes = schedule1.getMinutes();
    }
    if (schedule2 != null) {
      s2Hours = schedule2.getHours();
      s2Minutes = schedule2.getMinutes();
    }
    if (schedule3 != null) {
      s3Hours = schedule3.getHours();
      s3Minutes = schedule3.getMinutes();
    }

    //  define a function to check if s1 is less than now
    function compareTime(s1Hours, s1Minutes, nowHours, nowMinutes) {
      if (
        s1Hours < nowHours ||
        (s1Hours === nowHours && s1Minutes <= nowMinutes)
      ) {
        return true;
      } else {
        return false;
      }
    }

    if (
      schedule1 != null &&
      !m1exists &&
      compareTime(s1Hours, s1Minutes, nowHours, nowMinutes)
    ) {
      console.log("Opening box 1")
      client.publish("medication/open", "1");
      scheduleHistory.push({
        date: new Date().getDate().toString(),
        medIndex: 1,
      });
    }
    if (
      schedule2 != null &&
      !m2exists &&
      compareTime(s2Hours, s2Minutes, nowHours, nowMinutes)
    ) {
      console.log("Opening box 2")
      client.publish("medication/open", "2");
      scheduleHistory.push({
        date: new Date().getDate().toString(),
        medIndex: 2,
      })
    }
    if (
      schedule3 != null &&
      !m3exists &&
      compareTime(s3Hours, s3Minutes, nowHours, nowMinutes)
    ) {
      console.log("Opening box 3")
      client.publish("medication/open", "3");
      scheduleHistory.push({
        date: new Date().getDate().toString(),
        medIndex: 3,
      })
    }

    client.publish("medication/time", newtime.toString());
    client.publish(
      "medication/stock",
      med1StockName +
        "," +
        med1Stock +
        "," +
        med2StockName +
        "," +
        med2Stock +
        "," +
        med3StockName +
        "," +
        med3Stock
    );
    console.log("Published message loop");
  }, 10000); // Publish every 10 seconds
});

// Handle incoming MQTT messages
client.on("message", (topic, message) => {
  console.log(
    "Received message from topic:",
    topic,
    "Message:",
    message.toString()
  );

  if (topic === "medication/setStock") {
    var newStock = JSON.parse(message);
    med1StockName = newStock["med1StockName"];
    med2StockName = newStock["med2StockName"];
    med3StockName = newStock["med3StockName"];
    med1Stock = newStock["med1Stock"];
    med2Stock = newStock["med2Stock"];
    med3Stock = newStock["med3Stock"];
  } else if (topic === "medication/setTime") {
    time = new Date(message);
    console.log(time);
  } else if (topic === "medication/schedule") {
    var newSchedule = JSON.parse(message);

    schedule1 = new Date(newSchedule["s1"]);
    schedule2 = new Date(newSchedule["s2"]);
    schedule3 = new Date(newSchedule["s3"]);
  } else if (topic === "medication/noSchedule") {
    var deleteScheduleIndex = message;

    if (deleteScheduleIndex === 1) {
      schedule1 = null;
    } else if (deleteScheduleIndex === 2) {
      schedule2 = null;
    } else if (deleteScheduleIndex === 3) {
      schedule3 = null;
    }
  } else if (topic === "medication/getSchedule") {
    var data = {};
    if (schedule1 != null) {
      data["s1"] = schedule1.toString();
    } else {
      data["s1"] = "";
    }
    if (schedule2 != null) {
      data["s2"] = schedule2.toString();
    } else {
      data["s2"] = "";
    }
    if (schedule3 != null) {
      data["s3"] = schedule3.toString();
    } else {
      data["s3"] = "";
    }
    console.log("Sending schedule", data);
    client.publish("medication/allSchedule", JSON.stringify(data));
  } else if (topic === "medication/taken") {
    var takenMedIndex = message;
    medTaken(takenMedIndex);
    history.push({
      time: new Date().toString(),
      taken: true,
      medIndex: parseInt(takenMedIndex), // takenMedIndex,
      medCount: med1Stock
    });

    // in scheduleHistory, put date without time, taken, medIndex
   
  } else if (topic === "medication/notTaken") {
    var notTakenMedIndex = message;
    history.push({
      time: new Date().toString(),
      taken: false,
      medIndex: parseInt(notTakenMedIndex), // notTakenMedIndex,
      medCount: med1Stock
    });

    //  in scheduleHistory, put date without time, taken, medIndex
    
  } else if (topic === "medication/givehistory") {
    client.publish("medication/allHistory", JSON.stringify(history));
  }
});

// Handle MQTT error
client.on("error", (error) => {
  console.error("MQTT error:", error);
});
