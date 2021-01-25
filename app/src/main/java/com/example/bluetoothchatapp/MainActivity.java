package com.example.bluetoothchatapp;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.ActionBarContainer;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Objects;

public class MainActivity extends AppCompatActivity {

    private BluetoothAdapter bluetoothAdapter;
    private Context context;
    private ChatUtils chatUtils;

    private ListView listMainChat;
    private EditText edCreateMessage;
    private Button btnSendMessage;
    private ArrayAdapter adapterMainChat;

    private Button recordButton = null;
    private MediaRecorder recorder = null;

    private RecordButton btnRecord = null;
    private MediaPlayer player = null;
    private static String fileName = null;


    private final int LOCATION_PERMISSION_REQUEST = 101;
    private final int SELECT_DEVICE = 102;
    private final int RECORD_AUDIO = 103;

    public static final String DEVICE_NAME = "deviceName";
    private String connectedDevice;

    public static final String TOAST = "toast";

    public static final int MESSAGE_STATE_CHANGED = 0;
    public static final int MESSAGE_READ = 1;
    public static final int MESSAGE_WRITE = 2;
    public static final int MESSAGE_DEVICE_NAME = 3;
    public static final int MESSAGE_TOAST = 4;


    private Handler handler = new Handler(new Handler.Callback() {
        @Override
        public boolean handleMessage(@NonNull Message msg) {

            switch (msg.what) {
                case MESSAGE_STATE_CHANGED:
                    switch (msg.arg1) {
                        case ChatUtils.STATE_NONE:
                            setState("Não Conectado!");
                            break;
                        case ChatUtils.STATE_LISTEN:
                            setState("Não Conectado!");
                            break;
                        case ChatUtils.STATE_CONNECTING:
                            setState("Conectando...");
                            break;
                        case ChatUtils.STATE_CONNECTED:
                            setState("Conectado a " + connectedDevice);
                            break;
                    }
                    break;
                case MESSAGE_READ:
                    byte[] buffer = (byte[]) msg.obj;
                        String inputBuffer = new String(buffer, 0, msg.arg1);
                        if (inputBuffer.length() > 100){
                            convertBytesToFile(buffer);
                        }else {
                            adapterMainChat.add(connectedDevice + ": " + inputBuffer);
                        }
                        break;

                case MESSAGE_WRITE:
                    byte[] buffer1 = (byte[]) msg.obj;
                    String outputBuffer = new String(buffer1);
                    adapterMainChat.add("Eu: " + outputBuffer);
                    break;
                case MESSAGE_DEVICE_NAME:
                    connectedDevice = msg.getData().getString(DEVICE_NAME);
                    Toast.makeText(context, connectedDevice, Toast.LENGTH_SHORT).show();
                    break;
                case MESSAGE_TOAST:
                    Toast.makeText(context, msg.getData().getString(TOAST), Toast.LENGTH_SHORT).show();
                    break;
            }

            return false;
        }
    });

    private void setState(CharSequence subtitle) {
        getSupportActionBar().setSubtitle(subtitle);
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        context = this;
        fileName = getExternalCacheDir().getAbsolutePath();
        fileName += "/audiorecordtest.3gp";
        initBluetooth();
        init();
        chatUtils = new ChatUtils(context, handler);
        chatUtils.start();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main_activity, menu);
        return super.onCreateOptionsMenu(menu);
    }

    private void checkPermission() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.RECORD_AUDIO}, RECORD_AUDIO);
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_PERMISSION_REQUEST);
        } else {
            Intent intent = new Intent(context, DeviceListActivity.class);
            startActivityForResult(intent, SELECT_DEVICE);
        }
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == LOCATION_PERMISSION_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Intent intent = new Intent(context, DeviceListActivity.class);
                startActivityForResult(intent, SELECT_DEVICE);
            } else {
                new AlertDialog.Builder(this).setCancelable(false).setMessage("A Localização é necessária, por favor conceda a permissão!")
                        .setPositiveButton("Conceder", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                checkPermission();
                            }
                        }).setNegativeButton("Negar", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        MainActivity.this.finish();
                    }
                }).show();
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    public byte[] convert(String path) throws IOException {

        FileInputStream file = new FileInputStream(path);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] bytearray = new byte[1024];

        for (int readNum; (readNum = file.read(bytearray)) != -1; ) {
            bos.write(bytearray, 0, readNum);
        }

        byte[] bytes = bos.toByteArray();

        return bytes;
    }

    private void convertBytesToFile(byte[] bytearray) {
        try {

            File outputFile = File.createTempFile("file", "mp3", getCacheDir());
            outputFile.deleteOnExit();
            FileOutputStream fileoutputstream = new FileOutputStream(getExternalCacheDir().getAbsolutePath() + "/audio.3gp");
            fileoutputstream.write(bytearray);
            fileoutputstream.flush();
            fileoutputstream.close();

        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private void startRecording() {
        recorder = new MediaRecorder();
        recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        recorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
        recorder.setOutputFile(fileName);
        recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);

        try {
            recorder.prepare();
        } catch (IOException e) {
            Log.e("Audio->Record", "Método prepare() falhou!");
        }

        recorder.start();
    }

    private void stopRecording() throws IOException {
        recorder.stop();
        recorder.release();
        chatUtils.write(convert(fileName), 2);
        recorder = null;
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private void onRecord(boolean start) throws IOException {
        if (start) {
            startRecording();
        } else {
            stopRecording();
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    class RecordButton extends androidx.appcompat.widget.AppCompatButton {
        boolean mStartRecording = true;

        OnClickListener clicker = v -> {
            try {
                onRecord(mStartRecording);
            } catch (IOException e) {
                e.printStackTrace();
            }
            if (mStartRecording) {
                setText("Stop recording");
            } else {
                setText("Start recording");
            }
            mStartRecording = !mStartRecording;
        };

        public RecordButton(Context ctx) {
            super(ctx);
            setText("Start recording");
            setOnClickListener(clicker);
        }
    }


    private void initBluetooth() {
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth não detectado!", Toast.LENGTH_SHORT).show();
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private void init() {
        listMainChat = findViewById(R.id.list_conversation);
        edCreateMessage = findViewById(R.id.ed_enter_message);
        btnSendMessage = findViewById(R.id.btn_send_message);
        adapterMainChat = new ArrayAdapter(context, android.R.layout.simple_list_item_1);
        listMainChat.setAdapter(adapterMainChat);
        recordButton = new RecordButton(context);
        RelativeLayout rl = findViewById(R.id.relative);
        rl.addView(recordButton, new RelativeLayout.LayoutParams(150, 150));
        btnSendMessage.setOnClickListener(v -> {
            String message = edCreateMessage.getText().toString();
            if (!message.isEmpty()) {
                edCreateMessage.setText("");
                chatUtils.write(message.getBytes(), -1);
            }
        });
    }

    @SuppressLint("NonConstantResourceId")
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_search_devices:
                checkPermission();
                return true;
            case R.id.menu_enable_bluetooth:
                enableBluetooth();
                return true;
            default:
                return super.onOptionsItemSelected(item);

        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if (requestCode == SELECT_DEVICE && resultCode == RESULT_OK) {
            String address = Objects.requireNonNull(data).getStringExtra("deviceAddress");
            if (BluetoothAdapter.checkBluetoothAddress(address)) {
                Log.d("endereço", "valido");
            } else {
                Log.d("endereço", "invalido");
            }
            chatUtils.connect(bluetoothAdapter.getRemoteDevice(address));
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    private void enableBluetooth() {
        if (bluetoothAdapter.isEnabled()) {
            Toast.makeText(this, "O Bluetooth já está ligado!", Toast.LENGTH_SHORT).show();
        } else {
            bluetoothAdapter.enable();
        }

        if (bluetoothAdapter.getScanMode() != BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
            Intent discoveryIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300);
            startActivityForResult(discoveryIntent, 0);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (chatUtils != null) {
            chatUtils.stop();
        }
    }
}