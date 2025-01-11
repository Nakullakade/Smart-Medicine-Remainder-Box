package com.example.medicineboxapp;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import org.eclipse.paho.android.service.MqttAndroidClient;
import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttMessageListener;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.json.JSONException;
import org.json.JSONObject;

public class MQTTService extends Service  implements MqttCallback {

    private final IBinder mBinder = new MQTTServiceBinder();

    int NOTIFICATION_ID = 1;

    // Your existing MQTTService code here


    private MqttClient mqttClient;
    private final String serverUri = "tcp://43.204.177.85:1883";
//    private final String serverUri = "tcp://test.mosquitto.org:1883";

    private final String clientId = "AndroidClient";


    private final String[] topics = {"medication/stock", "medication/time", "medication/allHistory", "medication/allSchedule"};

    private String history;

    private String med1StockName, med2StockName, med3StockName;
    private int med1Stock, med2Stock, med3Stock;

    private String datetime;

    private String schedule;

    @Override
    public void connectionLost(Throwable cause) {
        Log.d("MQTT", "connectionLost: MQTT Connection Lost");
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) throws Exception {
        // Handle incoming messages
        Log.d("MQTT", "messageArrived: Topic"+topic);
        if(topic.equals(topics[0])){
            String s = message.toString();
            String[] tokens = s.split(",");

            med1StockName = tokens[0];
            med1Stock = Integer.parseInt(tokens[1]);
            med2StockName = tokens[2];
            med2Stock = Integer.parseInt(tokens[3]);
            med3StockName = tokens[4];
            med3Stock = Integer.parseInt(tokens[5]);
        }
        else if(topic.equals(topics[1])){
            Log.d("MQTT", "messageArrived: "+ message.toString());
            datetime = message.toString();

        }
        else if(topic.equals(topics[2])){
            Log.d("MQTT", "messageArrived: "+ message.toString());
            history = message.toString();
        }
        else if(topic.equals(topics[3])){
            Log.d("MQTT", "messageArrived: "+ message.toString());
            schedule = message.toString();
        }
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {

    }


    public class MQTTServiceBinder extends Binder {
        MQTTService getService() {
            return MQTTService.this;
        }
    }


    // Additional methods to handle publish and other functionalities
    public void publishMessage(String topic, String message) {
        if (!mqttClient.isConnected()) {
            Log.d("MQTT", "Client is not connected.");
            return;
        }
        MqttMessage mqttMessage = new MqttMessage();
        mqttMessage.setPayload(message.getBytes());
        try {
            mqttClient.publish(topic, mqttMessage);
            Log.d("MQTT", "Message published");
        } catch (MqttException e) {
            Log.e("MQTT", "Error Publishing: " + e.getMessage());
            e.printStackTrace();
        }
    }


    // Method to retrieve data from service
    public String getMedDataFromService() {
        // Example method to retrieve data
        JSONObject jsonData = new JSONObject();

        try {
            // Adding data to JSON object
            jsonData.put("med1StockName", med1StockName);
            jsonData.put("med1Stock", med1Stock);
            jsonData.put("med2StockName", med2StockName);
            jsonData.put("med2Stock", med2Stock);
            jsonData.put("med3StockName", med3StockName);
            jsonData.put("med3Stock", med3Stock);

        } catch (JSONException e) {
            e.printStackTrace();
        }

        // Returning JSON string
        return jsonData.toString();
    }

    public String getScheduleDataFromService(){
        return schedule;
    }

    public String getHistoryDataFromService(){
        return history;
    }

    public String getDatetimeDataFromService() {
        // Example method to retrieve data
        JSONObject jsonData = new JSONObject();

        try {
            // Adding data to JSON object
            jsonData.put("datetime", datetime);

        } catch (JSONException e) {
            e.printStackTrace();
        }

        // Returning JSON string
        return jsonData.toString();
    }

    private Notification getNotification() {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "MQTT Service";
            String description = "MQTT Service Channel";
            int importance = NotificationManager.IMPORTANCE_DEFAULT;
            NotificationChannel channel = new NotificationChannel("MQTTServiceChannel", name, importance);
            channel.setDescription(description);
            // Register the channel with the system; you can't change the importance
            // or other notification behaviors after this
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, "MQTTServiceChannel")
                .setContentTitle("MQTT Service")
                .setContentText("Running...")
                .setSmallIcon(R.drawable.ic_notification)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);

        return builder.build();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForeground(NOTIFICATION_ID, getNotification());
        }
        connect();
        return START_STICKY;
    }

    private void connect() {
        try {
            Log.d("MQTT", "connect: Calling Connect on Service");
            MemoryPersistence persistence = new MemoryPersistence();
            mqttClient = new MqttClient( serverUri, clientId, persistence);

            MqttConnectOptions options = new MqttConnectOptions();
            options.setCleanSession(true); // Depends on your use case
            options.setKeepAliveInterval(5);

            Log.d("MQTT", "connect: Trying to connect to mqtt");
            mqttClient.connect(options);
            mqttClient.setCallback(this);
            for(String topic : topics){
                subscribeToTopic(topic);
            }
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    private void subscribeToTopic(String topic) {
        try {
            Log.d("MQTT", "subscribeToTopic: Subscribing to topic" + topic);
            mqttClient.subscribe(topic);
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        Log.d("MQTT", "Service onBind called");
        return mBinder;
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        super.onTaskRemoved(rootIntent);
        stopSelf(); // Stops the service when the app is removed from the task manager
    }

}
