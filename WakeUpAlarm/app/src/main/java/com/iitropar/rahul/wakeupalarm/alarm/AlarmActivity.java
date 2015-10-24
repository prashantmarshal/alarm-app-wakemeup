package com.iitropar.rahul.wakeupalarm.alarm;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;
import android.widget.Toast;

import com.iitropar.rahul.wakeupalarm.R;
import com.iitropar.rahul.wakeupalarm.gcm.MultiSendMessage;
import com.iitropar.rahul.wakeupalarm.gcm.SendMessage;
import com.iitropar.rahul.wakeupalarm.gps.GPSTracker;
import com.iitropar.rahul.wakeupalarm.utility.JSONParser;
import com.iitropar.rahul.wakeupalarm.utility.WifiScanner;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;


/**
 * Created by Rahul on 21/4/15.
 */

//Change what you want to do on alarm receive in this class
public class AlarmActivity extends BroadcastReceiver{
    private static GPSTracker gps ;
    private static WifiScanner ws;
    private Context ctx;
    int uniqueid;
    double lat,lon;


    String TAG = "MyDebug: ";
    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "OnReceive");

        String type = intent.getStringExtra("type");
        if(type.equals("alarm")) {
            uniqueid = intent.getIntExtra("uniqueid", -1);
        }
        ctx = context;
        Thread t=null;
        if(type.equals("alarm")) {
            Log.d(TAG, "UniqueID: " + String.valueOf(uniqueid));
            ringAlarm(context);
            t = new Thread(requestFriendsLocation);
            t.start();
        }
        else if(type.equals("stopalarm")) {
            if(t!=null) {
                Log.i(TAG,"stopping");
//                t.stop();
            }
        }
        else if(type.equals(context.getString(R.string.Return_Distance))) {
            returnDistance(intent);
        }
        else if(type.equals(context.getString(R.string.Receive_Distance))) {
            String msg = intent.getStringExtra("distance");
            String from = intent.getStringExtra("from");
//            new Thread(requestFriendsLocation).start();
            Log.d("MyDebug","Received in alarm: "+msg);
            Toast.makeText(ctx,"Received | "+from+" | Distance: "+msg,Toast.LENGTH_LONG).show();
        }
        else {
        }

    }

    void ringAlarm(Context context) {
        Log.d("MyDebug","Ringing Alarm");

       Intent intent=new Intent(context,alarmDialog.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);

    }

    void returnDistance(Intent intent) {
        try {
            JSONObject jo = new JSONObject(intent.getStringExtra("message"));
            double latitude = Double.parseDouble(jo.getString("latitude"));
            Log.d(TAG, "lat: " + latitude);
            double longitude = Double.parseDouble(jo.getString("longitude"));
            uniqueid = jo.getInt("randCheck");
            Log.d(TAG, "uniq: " + uniqueid);
            String sender = jo.getString("from");
            Log.d(TAG, "lan: " + latitude + " long: " + longitude + " from: " + sender);
            SharedPreferences preferences = ctx.getSharedPreferences(ctx.getString(R.string.Saved_Phone_Number), Context.MODE_PRIVATE);
            String mobile = preferences.getString(ctx.getString(R.string.mobile), "");
            Log.d(TAG, "MOBILE: " + mobile);
            JSONObject res = new JSONObject();
            res.put("randCheck", uniqueid);
            res.put("type",ctx.getString(R.string.Receive_Distance));
            res.put("from", String.valueOf(mobile));
            gps = new GPSTracker(ctx);
            ws = new WifiScanner(ctx);
            Thread.sleep(5000);
            Log.d(TAG,"location value: "+jo.getString("location"));
            Log.d(TAG,"gps value: "+gps.canGetLocation());
            if(jo.getString("location").equals("true")) {
                if (gps.canGetLocation()) {
                    double myLatitude = gps.getLatitude();
                    double myLongitude = gps.getLongitude();
                    Log.d(TAG, "mylat: " + myLatitude + " myLong: " + myLongitude);
                    double distance = (latitude - myLatitude) * (latitude - myLatitude) + (longitude - myLongitude) * (longitude - myLongitude);
                    distance = Math.sqrt(distance);
                    Toast.makeText(ctx, "from: " + sender + " | Score: " + String.valueOf(distance) + " | lat/lon: " + latitude + "/" + longitude, Toast.LENGTH_LONG).show();
                    res.put("location","true");
                    res.put("distance", String.valueOf(distance));
                }
                else {
                    res.put("location","false");
                }
            }
            else if(jo.getString("wifi").equals("true")) {
                int count = 5;
                while(!ws.canGetWifi&&count>0) {
                    count--;
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                if(ws.canGetWifi) {
                    double score = 0, match = 0;
                    ArrayList<String> mywlist = ws.WiFiNetworks;
                    ArrayList<String> wlist = (ArrayList<String>) jo.get("wifilist");
                    for(String w: wlist) {
                        if(mywlist.contains(w))
                            match++;
                    }
                    score = match/(mywlist.size()+wlist.size()-match);
                    res.put("wifi","true");
                    res.put("wifiscore",String.valueOf(score));
                }
                else {
                    res.put("wifi","false");
                }
            }
            else {
                Log.d(TAG,"NO LOCATION AVAILABLE");
                return;
            }
            SendMessage sendMessage = new SendMessage(ctx, sender, res.toString());
            sendMessage.execute();
        } catch (Exception e) {
            Log.d(TAG,"JSONERROR");
            e.printStackTrace();
        }
    }

    Runnable requestFriendsLocation = new Runnable(){
        @Override
        public void run(){
            JSONParser json = new JSONParser();
            ws = new WifiScanner(ctx);
            String url = ctx.getString(R.string.SERVER_IP) + ctx.getString(R.string.Find_Nearest_Page);
            SharedPreferences sharedpreferences = ctx.getSharedPreferences(ctx.getString(R.string.Saved_Phone_Number),Context.MODE_PRIVATE) ;
            String mobileNumber = sharedpreferences.getString(ctx.getString(R.string.mobile),"") ;
            Log.d(TAG,"mobile: "+mobileNumber);
            if (mobileNumber != null && !mobileNumber.toLowerCase().equals("")) {
                try {
                    Log.d(TAG,"Mobile found: " + mobileNumber) ;
                    FriendsDatabase fd = new FriendsDatabase(ctx);
                    fd.open();
                    ArrayList<String> arr = fd.getNumbers();
                    fd.close();
                    JSONObject jo = new JSONObject();
                    jo.put("from", mobileNumber);
                    jo.put("randCheck", String.valueOf(uniqueid));
                    gps = new GPSTracker(ctx);
                    if (gps.canGetLocation()) {
                        jo.put("location","true");
                        jo.put("latitude", gps.getLatitude());
                        jo.put("longitude", gps.getLongitude());
                    }
                    else {
                        jo.put("location","false");
                    }
                    int count = 5;
                    while(!ws.canGetWifi&&count>0) {
                        count--;
                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                    if(ws.canGetWifi) {
                        jo.put("wifi","true");
                        jo.put("wifilist",ws.WiFiNetworks);
                    }
                    else {
                        jo.put("wifi","false");
                    }
                    Log.d("MyDebug", "message: " + jo.toString());
                    MultiSendMessage msm = new MultiSendMessage(ctx,arr,jo.toString());
                    msm.execute();
                    Log.d(TAG, "SENT LOCATION");
                } catch(Exception e) {
                    Log.e(TAG,"JSON parsing error");
                    e.printStackTrace();
                }

            }
            else {

            }
        }
    };



}