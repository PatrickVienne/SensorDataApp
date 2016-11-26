package com.example.polyun.sensor2pc;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedWriter;
import java.io.DataOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.sql.Array;
import java.util.Date;

public class MainActivity extends AppCompatActivity {

    public int PORT = 9999;
    private Button connectPhones;
    private String serverIpAddress = "192.168.0.12";
    private boolean connected = false;
    TextView text;
    EditText port;
    EditText ipAdr;

    private float timestamp;
    private float gyrolasttimestamp;

    private static final int FROM_RADS_TO_DEGS = -57;
    private float Mag_x,Mag_y,Mag_z;
    private float Acc_x,Acc_y,Acc_z;
    private float Gx,Gy,Gz;
    private float Gyro_x,Gyro_y,Gyro_z;
    private float LinAcc_x,LinAcc_y,LinAcc_z;
    private float Orient_x,Orient_y,Orient_z;
    private float corr_yaw,corr_pitch,corr_roll;
    private float[] rotationMatrix = new float[9];
    float[] adjustedRotationMatrix = new float[9];
    float[] orientation = new float[3];
    private static final float NS2S = 1.0f / 1000000000.0f;
    private final float[] deltaRotationVector = new float[4];
    float[] truncatedRotationVector = new float[4];

    private SensorManager sensorManager;
    private Sensor accelerometer,magnetometer,lin_acc_meter,gyrometer,orient_meter, gravimeter, rotation_meter, game_rot_meter;

    boolean acc_disp = false;
    PrintWriter out;
    boolean post2socket = false;
    boolean post2REST = true;
    private ToggleButton BtnRest, BtnSocket;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        connectPhones = (Button)findViewById(R.id.send);
        connectPhones.setOnClickListener(connectListener);
        text=(TextView)findViewById(R.id.textin);
        port=(EditText)findViewById(R.id.port);
        ipAdr=(EditText)findViewById(R.id.ipadr);
        text.setText("Press send to stream magnetometer measurement");
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        lin_acc_meter = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
        gyrometer = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        orient_meter = sensorManager.getDefaultSensor(Sensor.TYPE_ORIENTATION);
        gravimeter = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY);
        rotation_meter = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        game_rot_meter = sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR);
        port.setText("9999");
        ipAdr.setText(serverIpAddress);
        acc_disp =false;
        addListenerOnButton();

    }

    public void addListenerOnButton() {

        BtnRest = (ToggleButton) findViewById(R.id.btnrest);
        BtnSocket = (ToggleButton) findViewById(R.id.btnsocket);
        BtnRest.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    Toast.makeText(MainActivity.this, "Streaming to Rest",  Toast.LENGTH_SHORT).show();
                    post2REST = true;
                } else {
                    Toast.makeText(MainActivity.this, "Stopped the Stream to Rest",  Toast.LENGTH_SHORT).show();
                    post2REST = false;
                }
            }
            }
        );
        BtnSocket.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
               public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                   if (isChecked) {
                       Toast.makeText(MainActivity.this, "Streaming to Socket",  Toast.LENGTH_SHORT).show();
                       post2socket = true;
                   } else {
                       Toast.makeText(MainActivity.this, "Stopped the Stream to Socket",  Toast.LENGTH_SHORT).show();
                       post2socket = false;
                   }
               }
           }
        );
    }

    private Button.OnClickListener connectListener = new Button.OnClickListener() {
        @Override
        public void onClick(View v) {

            if (!connected) {
                if (!serverIpAddress.equals("")) {
                    connectPhones.setText("Stop Streaming");
                    Thread cThread = new Thread(new ClientThread());
                    cThread.start();
                }
            }
            else{
                connectPhones.setText("Start Streaming");
                connected=false;
                acc_disp=false;
            }
        }
    };

    public class ClientThread implements Runnable {
        Socket socket;
        public void run() {
            if (post2socket == true){
                try {
                    acc_disp=true;
                    PORT = Integer.parseInt(port.getText().toString());
                    serverIpAddress=ipAdr.getText().toString();
                    InetAddress serverAddr = InetAddress.getByName(serverIpAddress);
                    //InetAddress serverAddr = InetAddress.getByName("TURBOBEAVER");
                    socket = new Socket(serverAddr, PORT);
                    connected = true;
                    out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(socket.getOutputStream())), true);

                    while (connected && post2socket) {
                        long date =  new Date().getTime();

                        String send_text = create_sendjsontext();
                        //String send_text = array.toString();
                        //String send_text = String.format("{'x':%3.2f,'y':%3.2f,'z':%3.2f}", x, y, z);
                        //sendtext.setText(send_text);
                        //out.write(send_text,0,120);
                        out.printf(send_text+'*');
                        out.flush();
                        Thread.sleep(100);
                    }


                }
                catch (Exception e) {
                }
                finally{
                    try{
                        JSONObject json = new JSONObject();
                        long date =  new Date().getTime();
                        json.put("date", date);
                        json.put("FINISHED", 1);
                        String send_text = json.toString();
                        out.printf(send_text+'*');
                        out.flush();
                        acc_disp=false;
                        connected=false;
                        connectPhones.setText("Start Streaming");
                        //out.close();
                        out.printf(null);
                        socket.shutdownInput();
                        out.close();
                        socket.close();
                    }catch(Exception a){
                    }
                }
            }
            else if(post2REST = true) {
                try{
                    acc_disp=true;
                    String url = "http://"+ipAdr.getText().toString() +":"+port.getText().toString()+"/streamdata";

                    // 1. create HttpClient
                    HttpClient httpclient = new DefaultHttpClient();

                    // 2. make POST request to the given URL
                    HttpPost httpPost = new HttpPost(url);
                    connected = true;
                    // 3. build jsonObject
                    while (connected && post2REST) {

                        String send_text = create_sendjsontext();

                        // ** Alternative way to convert Person object to JSON string usin Jackson Lib
                        // ObjectMapper mapper = new ObjectMapper();
                        // json = mapper.writeValueAsString(person);

                        // 5. set json to StringEntity
                        StringEntity se = new StringEntity(send_text);

                        // 6. set httpPost Entity
                        httpPost.setEntity(se);

                        // 7. Set some headers to inform server about the type of the content
                        httpPost.setHeader("Accept", "application/json");
                        httpPost.setHeader("Content-type", "application/json");
                        // 8. Execute POST request to the given URL
                        HttpResponse httpResponse = httpclient.execute(httpPost);
                        Log.i("HTTP_RESPONSE",httpResponse.toString());
                        Thread.sleep(100);
                    }
                }catch (Exception e) {
                }
                finally{
                    try{
                        acc_disp=false;
                        connected=false;
                        connectPhones.setText("Start Streaming");
                    }catch(Exception a){
                    }
                }
            }

        }

        public void disconnect(){
            try {
                acc_disp = false;
                connected = false;
                connectPhones.setText("Start Streaming");
                //out.close();
                out.printf(null);
                socket.shutdownInput();
                out.close();
                socket.close();
            }catch (Exception e){

            }
        }
    }

    private String create_sendjsontext() {
        JSONObject json = new JSONObject();
        try {
            json.put("Orient_x", Orient_x);
            json.put("Orient_y", Orient_y);
            json.put("Orient_z", Orient_z);
            json.put("corr_yaw", corr_yaw);
            json.put("corr_pitch", corr_pitch);
            json.put("corr_roll", corr_roll);
            json.put("Mag_x", Mag_x);
            json.put("Mag_y", Mag_y);
            json.put("Mag_z", Mag_z);
            json.put("Gx", Gx);
            json.put("Gy", Gy);
            json.put("Gz", Gz);
            json.put("Gyro_x", Gyro_x);
            json.put("Gyro_y", Gyro_y);
            json.put("Gyro_z", Gyro_z);
            json.put("Acc_x", Acc_x);
            json.put("Acc_y", Acc_y);
            json.put("Acc_z", Acc_z);
            json.put("LinAcc_x", LinAcc_x);
            json.put("LinAcc_y", LinAcc_y);
            json.put("LinAcc_z", LinAcc_z);
            json.put("FINISHED", 0);
            json.put("date", timestamp);
            // 4. convert JSONObject to JSON to String
            String send_text = json.toString();
            return send_text;
        } catch (JSONException e) {
            e.printStackTrace();
            return "";
        }
    }

    ;

    private void init_perif(){
        // smthing
    }

    @Override
    protected void onResume() {
        super.onResume();
        sensorManager.registerListener(sensorlistener, accelerometer, SensorManager.SENSOR_DELAY_FASTEST);
        sensorManager.registerListener(sensorlistener, gravimeter, SensorManager.SENSOR_DELAY_FASTEST);
        sensorManager.registerListener(sensorlistener, magnetometer, SensorManager.SENSOR_DELAY_FASTEST);
        sensorManager.registerListener(sensorlistener, lin_acc_meter, SensorManager.SENSOR_DELAY_FASTEST);
        sensorManager.registerListener(sensorlistener, gyrometer, SensorManager.SENSOR_DELAY_FASTEST);
        sensorManager.registerListener(sensorlistener, orient_meter, SensorManager.SENSOR_DELAY_FASTEST);
        sensorManager.registerListener(sensorlistener, rotation_meter, SensorManager.SENSOR_DELAY_FASTEST);
        sensorManager.registerListener(sensorlistener, game_rot_meter, SensorManager.SENSOR_DELAY_FASTEST);
    }

    @Override
    protected void onStop() {
        //sensorManager.unregisterListener(magnetometerListener);
        super.onStop();
    }


    private void refreshDisplay() {
        if(acc_disp == true){
            String output = String.format("Mag: X:%3.2f μT  |  Y:%3.2f μT  |   Z:%3.2f μT \n " +
                    "Grav: X:%3.2f  |  Y:%3.2f  |   Z:%3.2f \n" +
                    "Orient: Yaw:%3.2f  |  Roll:%3.2f  |   Pitch:%3.2f \n" +
                    "cOrient: Az:%3.2f  |  Roll:%3.2f  |   Pitch:%3.2f \n"
                    , Mag_x, Mag_y, Mag_z,Gx,Gy,Gz,Orient_x,Orient_y,Orient_z, corr_yaw, corr_roll, corr_pitch);
            text.setText(output);
        }
    };

    private SensorEventListener sensorlistener = new SensorEventListener() {

        @Override
        public void onAccuracyChanged(Sensor sensor, int acc) {
        }
        @Override
        public void onSensorChanged(SensorEvent event) {

            switch(event.sensor.getType()) {
                //https://developer.android.com/reference/android/hardware/SensorEvent.html

                case Sensor.TYPE_LINEAR_ACCELERATION:
                    LinAcc_x = event.values[0];
                    LinAcc_y = event.values[1];
                    LinAcc_z = event.values[2];
                    break;

                case Sensor.TYPE_ORIENTATION:
                    /*
                    values[0]: Azimuth, angle between the magnetic north direction and the y-axis, around the z-axis (0 to 359). 0=North, 90=East, 180=South, 270=West
                    values[1]: Pitch, rotation around x-axis (-180 to 180), with positive values when the z-axis moves toward the y-axis.
                    values[2]: Roll, rotation around the y-axis (-90 to 90) increasing as the device moves clockwise.
                     */
                    Orient_x = event.values[0];
                    Orient_y = event.values[1];
                    Orient_z = event.values[2];
                    break;

                case Sensor.TYPE_GYROSCOPE:
                    Gyro_x = event.values[0];
                    Gyro_y = event.values[1];
                    Gyro_z = event.values[2];
                    break;

                case Sensor.TYPE_GRAVITY:
                    Gx = event.values[0];
                    Gy = event.values[1];
                    Gz = event.values[2];
                    break;

                case Sensor.TYPE_ACCELEROMETER:
                    Acc_x = event.values[0];
                    Acc_y = event.values[1];
                    Acc_z = event.values[2];
                    break;

                case Sensor.TYPE_MAGNETIC_FIELD:
                    Mag_x = event.values[0];
                    Mag_y = event.values[1];
                    Mag_z = event.values[2];
                    break;
                /*
                Identical to TYPE_ROTATION_VECTOR except that it doesn't use the geomagnetic field. Therefore the Y axis doesn't point north, but instead to some other reference, that reference is allowed to drift by the same order of magnitude as the gyroscope drift around the Z axis.
In the ideal case, a phone rotated and returning to the same real-world orientation will report the same game rotation vector (without using the earth's geomagnetic field). However, the orientation may drift somewhat over time. See TYPE_ROTATION_VECTOR for a detailed description of the values. This sensor will not have the estimated heading accuracy value.
                 */

                case Sensor.TYPE_GAME_ROTATION_VECTOR:
                    Mag_x = event.values[0];
                    Mag_y = event.values[1];
                    Mag_z = event.values[2];
                    break;

                case Sensor.TYPE_ROTATION_VECTOR:
                    /*
                    values[0]: x*sin(θ/2)
                    values[1]: y*sin(θ/2)
                    values[2]: z*sin(θ/2)
                    values[3]: cos(θ/2)
                    values[4]: estimated heading Accuracy (in radians) (-1 if unavailable)
                     */
                    if (event.values.length > 4) {
                        System.arraycopy(event.values, 0, truncatedRotationVector, 0, 4);
                        update(truncatedRotationVector);
                    } else {
                        update(event.values);
                    }
                    break;

                default:
                    {}
            }
            timestamp = event.timestamp;
            refreshDisplay();
        }
    };

    private void update(float[] vectors) {

        SensorManager.getRotationMatrixFromVector(rotationMatrix, vectors);
        SensorManager.remapCoordinateSystem(rotationMatrix, SensorManager.AXIS_X, SensorManager.AXIS_Z, adjustedRotationMatrix);
        SensorManager.getOrientation(adjustedRotationMatrix, orientation);
        corr_yaw = orientation[0] * FROM_RADS_TO_DEGS;
        corr_pitch = orientation[1] * FROM_RADS_TO_DEGS;
        corr_roll = orientation[2] * FROM_RADS_TO_DEGS;
    }


}