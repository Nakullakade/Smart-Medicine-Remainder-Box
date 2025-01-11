package com.example.medicineboxapp;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {
    private MQTTService mService;
    private boolean mBound = false;

    private EditText med1name, med2name, med3name;
    private EditText med1Stock, med2Stock, med3Stock;

    private DatePicker datePicker;
    private TimePicker timePicker;

    private Button medsaveButton, medsyncButton, datetimesaveButton, datetimesyncButton;

    private Button medBox1, medBox2, medBox3;

    private Button historySync;

    private TextView history;

    private Button scheduleSyncButton, scheduleSaveButton;

    private TimePicker schedule1, schedule2, schedule3;

    private void setMedData(){
        try {
            String medjsonDataString = mService.getMedDataFromService();

            JSONObject medjsonData = new JSONObject(medjsonDataString);

            med1name.setText(medjsonData.getString("med1StockName"));
            med1Stock.setText(medjsonData.getString("med1Stock"));
            med2name.setText(medjsonData.getString("med2StockName"));
            med2Stock.setText(medjsonData.getString("med2Stock"));
            med3name.setText(medjsonData.getString("med3StockName"));
            med3Stock.setText(medjsonData.getString("med3Stock"));


        }
        catch (Exception e){
            e.printStackTrace();
        }
    }

    public static Date predict(List<Date> dates, List<Integer> counts) {
        if (dates.isEmpty() || counts.isEmpty() || dates.size() != counts.size() || dates.size() < 2) {
            Log.d("MQTT", "predict: failed");
            return new Date();
        }

        long[] x = dates.stream().mapToLong(Date::getTime).toArray();
        double[] y = counts.stream().mapToDouble(Integer::doubleValue).toArray();


        Log.d("MQTT", "predict: X is " + x.toString());
        Log.d("MQTT", "predict: y is " + y.toString());

        double sumX = 0;
        double sumY = 0;
        double sumXY = 0;
        double sumXX = 0;
        int n = dates.size();

        for (int i = 0; i < n; i++) {
            sumX += x[i];
            sumY += y[i];
            sumXY += x[i] * y[i];
            sumXX += x[i] * x[i];
        }

        // Calculate the slope and intercept
        double denominator = n * sumXX - sumX * sumX;
        if (Math.abs(denominator) < 1e-10) {
            // Slope is close to zero, return the last date
            return dates.get(n - 1);
        }

        double slope = (n * sumXY - sumX * sumY) / denominator;
        double intercept = (sumY - slope * sumX) / n;

        // Calculate the predicted date
        long startDate = dates.get(0).getTime();
        long predictedDate = (long) ((0 - intercept) / slope) + startDate;

        // Ensure predicted date is not before the start date
        predictedDate = Math.max(predictedDate, startDate);

        return new Date(predictedDate);
    }


    private void  setHistory(){
        String historyData = mService.getHistoryDataFromService();
        try {
            JSONArray data = new JSONArray(historyData);
            String s = "";
            SimpleDateFormat outputFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");

            List<Date> dates1 = new ArrayList<>();
            List<Date> dates2 = new ArrayList<>();
            List<Date> dates3 = new ArrayList<>();

            List<Integer> count1 = new ArrayList<>();
            List<Integer> count2 = new ArrayList<>();
            List<Integer> count3 = new ArrayList<>();


            for(int i= 0 ; i< data.length(); i++){
                JSONObject jsonObject = data.getJSONObject(i);

                int index = Integer.parseInt(jsonObject.getString("medIndex"));

                if(index ==1){
                    dates1.add(new Date(jsonObject.getString("time")));
                    count1.add(Integer.parseInt(jsonObject.getString("medCount")));
                }
                else if(index ==2){
                    dates2.add(new Date(jsonObject.getString("time")));
                    count2.add(Integer.parseInt(jsonObject.getString("medCount")));
                }
                else if(index ==3){
                    dates3.add(new Date(jsonObject.getString("time")));
                    count3.add(Integer.parseInt(jsonObject.getString("medCount")));
                }


                s += outputFormat.format(new Date(jsonObject.getString("time")));
                s += "  -  ";
                s += "Med Index "+ jsonObject.getString("medIndex");
                s += "  -  ";

                if(jsonObject.getBoolean("taken")){
                    s += "taken";
                }else{
                    s += "not taken";
                }

                s += " - Remaining "+ jsonObject.getString("medCount");

                s += "\n\n";

            }
            Log.d("MQTT", "setHistory: Remaining1 trying");

            Date remaining1 = predict(dates1,count1);
            Log.d("MQTT", "setHistory: Remaining1"+ remaining1);
            Date remaining2 = predict(dates2,count2);
            Date remaining3 = predict(dates3,count3);

            s += "Med 1 ends by - " + remaining1.toLocaleString() + "\n";
            s += "Med 2 ends by - " + remaining2.toLocaleString() + "\n";
            s += "Med 3 ends by - " + remaining3.toLocaleString();




            history.setText(s);
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    private void getSchedule(){

        try {
            Log.d("MQTT", "getSchedule: Trynig schedule");

            String scheduleData = mService.getScheduleDataFromService();
            Log.d("MQTT", "getSchedule: Trynig schedule 2");

            JSONObject sjsonData = new JSONObject(scheduleData);

            Log.d("MQTT", "getSchedule: Trynig schedule 3");

            String date1 = sjsonData.getString("s1");
            String date2 = sjsonData.getString("s2");
            String date3 = sjsonData.getString("s3");

            Log.d("MQTT", "getSchedule: " + date1.toString() + " " + date2.toString() + " " + date3.toString());

            //1
            Calendar calendar1 = Calendar.getInstance();
            calendar1.setTime(new Date(date1));


            // Set time to TimePicker
            schedule1.setCurrentHour(calendar1.get(Calendar.HOUR_OF_DAY));
            schedule1.setCurrentMinute(calendar1.get(Calendar.MINUTE));

            //2
            Calendar calendar2 = Calendar.getInstance();
            calendar2.setTime(new Date(date2));


            // Set time to TimePicker
            schedule2.setCurrentHour(calendar2.get(Calendar.HOUR_OF_DAY));
            schedule2.setCurrentMinute(calendar2.get(Calendar.MINUTE));

            //3
            Calendar calendar3 = Calendar.getInstance();
            calendar3.setTime(new Date(date3));


            // Set time to TimePicker
            schedule3.setCurrentHour(calendar3.get(Calendar.HOUR_OF_DAY));
            schedule3.setCurrentMinute(calendar3.get(Calendar.MINUTE));


        }catch (Exception e){
            e.printStackTrace();
        }
    }



    private void setDateTimeData(){
        try {
            Log.d("MQTT", "setDateTimeData: Trying to set Time");

            String datetimejsonDataString = mService.getDatetimeDataFromService();


            JSONObject datetimejsonData = new JSONObject(datetimejsonDataString);

            String datetimeString = datetimejsonData.getString("datetime");

            Log.d("MQTT", "setDateTimeData: Data "+ datetimeString);


            Date date = new Date(datetimeString);
            Log.d("MQTT", "setDateTimeData: " + date.toString());





            Calendar calendar = Calendar.getInstance();
            calendar.setTime(date);

            // Set date to DatePicker
            datePicker.updateDate(calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH));

            // Set time to TimePicker
            timePicker.setCurrentHour(calendar.get(Calendar.HOUR_OF_DAY));
            timePicker.setCurrentMinute(calendar.get(Calendar.MINUTE));


        }
        catch (Exception e){
            e.printStackTrace();
        }
    }

    private ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            Log.d("MQTT", "Service connected");

            MQTTService.MQTTServiceBinder binder = (MQTTService.MQTTServiceBinder) service;
            mService = binder.getService();

            setMedData();
            setDateTimeData();

            mBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            mBound = false;
        }
    };

    public void publishMessage(String topic, String message){
        if (mBound) {
            mService.publishMessage(topic, message);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        Log.d("MQTT", "onCreate: Trying to create intent");

        Intent intent = new Intent(this, MQTTService.class);
        startService(intent); // This will ensure the service is started if not already running
        bindService(intent, mServiceConnection, Context.BIND_AUTO_CREATE);

        med1name = findViewById(R.id.med1name);
        med2name = findViewById(R.id.med2name);
        med3name = findViewById(R.id.med3name);

        med1Stock = findViewById(R.id.med1Stock);
        med2Stock = findViewById(R.id.med2Stock);
        med3Stock = findViewById(R.id.med3Stock);

        datePicker = findViewById(R.id.datepicker);
        timePicker = findViewById(R.id.timepicker);

        medsaveButton = findViewById(R.id.medsaveButton);
        medsyncButton = findViewById(R.id.medsyncButton);

        datetimesaveButton = findViewById(R.id.datetimesaveButton);
        datetimesyncButton = findViewById(R.id.datetimesyncButton);
        timePicker.setIs24HourView(true); // For 24-hour mode


        medBox1 = findViewById(R.id.medBox1);
        medBox2 = findViewById(R.id.medBox2);
        medBox3 = findViewById(R.id.medBox3);

        historySync = findViewById(R.id.historySync);
        history = findViewById(R.id.history);
//        history.setSingleLine(false);

        scheduleSyncButton = findViewById(R.id.scheduleSyncButton);
        scheduleSaveButton = findViewById(R.id.saveScheduleButton);

        schedule1 = findViewById(R.id.schedule1picker);
        schedule2 = findViewById(R.id.schedule2picker);
        schedule3 = findViewById(R.id.schedule3picker);

        medsaveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                try {
                    JSONObject jsonData = new JSONObject();
                    jsonData.put("med1StockName", med1name.getText());
                    jsonData.put("med1Stock", med1Stock.getText());
                    jsonData.put("med2StockName", med2name.getText());
                    jsonData.put("med2Stock", med2Stock.getText());
                    jsonData.put("med3StockName", med3name.getText());
                    jsonData.put("med3Stock", med3Stock.getText());
                    mService.publishMessage("medication/setStock", jsonData.toString());
                    Toast.makeText(getApplicationContext(), "Published Medicine Data", Toast.LENGTH_SHORT).show();

                }
                catch (Exception e){
                    e.printStackTrace();
                }
            }
        });

        medsyncButton.setOnClickListener(new View.OnClickListener(){

            @Override
            public void onClick(View view) {
                setMedData();
            }
        });

        datetimesaveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                try {
                    Calendar calendar = Calendar.getInstance();

                    // Update the calendar with the date from the DatePicker
                    calendar.set(Calendar.YEAR, datePicker.getYear());
                    calendar.set(Calendar.MONTH, datePicker.getMonth());
                    calendar.set(Calendar.DAY_OF_MONTH, datePicker.getDayOfMonth());

                    // Update the calendar with the time from the TimePicker
                    calendar.set(Calendar.HOUR_OF_DAY, timePicker.getCurrentHour());
                    calendar.set(Calendar.MINUTE, timePicker.getCurrentMinute());

                    // Now you have a Date object with the selected date and time
                    Date date = calendar.getTime();

                    mService.publishMessage("medication/setTime", date.toString());
                    Toast.makeText(getApplicationContext(), "Published Date Time", Toast.LENGTH_SHORT).show();
                }
                catch (Exception e){
                    e.printStackTrace();
                }
            }
        });

        datetimesyncButton.setOnClickListener(new View.OnClickListener(){

            @Override
            public void onClick(View view) {
                setDateTimeData();
            }
        });

        medBox1.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mService.publishMessage("medication/open" , "1");
            }
        });
        medBox2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mService.publishMessage("medication/open" , "2");
            }
        });
        medBox3.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mService.publishMessage("medication/open" , "3");
            }
        });

        historySync.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mService.publishMessage("medication/givehistory", "1");
                setHistory();
            }
        });

        scheduleSyncButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mService.publishMessage("medication/getSchedule","1");
                getSchedule();
            }
        });

        scheduleSaveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                try {
                    Calendar calendar1 = Calendar.getInstance();

                    // Update the calendar with the time from the TimePicker
                    calendar1.set(Calendar.HOUR_OF_DAY, schedule1.getCurrentHour());
                    calendar1.set(Calendar.MINUTE, schedule1.getCurrentMinute());

                    // Now you have a Date object with the selected date and time
                    Date date1 = calendar1.getTime();

                    Calendar calendar2 = Calendar.getInstance();

                    // Update the calendar with the time from the TimePicker
                    calendar2.set(Calendar.HOUR_OF_DAY, schedule2.getCurrentHour());
                    calendar2.set(Calendar.MINUTE, schedule2.getCurrentMinute());

                    // Now you have a Date object with the selected date and time
                    Date date2 = calendar2.getTime();

                    Calendar calendar3 = Calendar.getInstance();

                    // Update the calendar with the time from the TimePicker
                    calendar3.set(Calendar.HOUR_OF_DAY, schedule3.getCurrentHour());
                    calendar3.set(Calendar.MINUTE, schedule3.getCurrentMinute());

                    // Now you have a Date object with the selected date and time
                    Date date3 = calendar3.getTime();

                    JSONObject data = new JSONObject();
                    data.put("s1", date1.toString());
                    data.put("s2", date2.toString());
                    data.put("s3", date3.toString());

                    mService.publishMessage("medication/schedule", data.toString());



                }catch (Exception e){
                    e.printStackTrace();
                }
            }
        });

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Unbind from the service
        if (mBound) {
            unbindService(mServiceConnection);
            mBound = false;
        }
    }



}