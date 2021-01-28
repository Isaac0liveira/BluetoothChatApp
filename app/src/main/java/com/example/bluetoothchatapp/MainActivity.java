package com.example.bluetoothchatapp;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.Toast;

import org.xiph.vorbis.player.VorbisPlayer;
import org.xiph.vorbis.recorder.VorbisRecorder;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
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
    private Button playButton = null;
    private VorbisRecorder vorbisRecorder;
    private VorbisPlayer vorbisPlayer;


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
    public boolean recording;

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
                    if (inputBuffer.length() > 100) {
                        convertBytesToFile(buffer, "ogg");
                    } else {
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

    private Handler recordingHandler = new Handler(new Handler.Callback() {
        @Override
        public boolean handleMessage(Message msg) {
            switch (msg.what) {
                case VorbisRecorder.START_ENCODING:
                    Log.d("Gravação: ", "Gravação Iniciada");
                    break;
                case VorbisRecorder.STOP_ENCODING:
                    Log.d("Gravação: ", "Parando a Gravação");
                    break;
                case VorbisRecorder.UNSUPPORTED_AUDIO_TRACK_RECORD_PARAMETERS:
                    Log.d("Gravação: ", "Seu dispositivo não suporta os parâmetros de gravação");
                    break;
                case VorbisRecorder.ERROR_INITIALIZING:
                    Log.d("Gravação: ", "Erro ao iniciar, tente alterar as configurações.");
                    break;
                case VorbisRecorder.FAILED_FOR_UNKNOWN_REASON:
                    Log.d("Gravação: ", "Gravação falhou por um motivo desconhecido!");
                    break;
                case VorbisRecorder.FINISHED_SUCCESSFULLY:
                    Log.d("Gravação: ", "Gravação salva com sucesso");
                    break;
            }
            return false;
        }
    });

    private Handler playerHandler = new Handler(new Handler.Callback() {
        @Override
        public boolean handleMessage(Message msg) {
            switch (msg.what) {
                case VorbisPlayer.PLAYING_FAILED:
                    Log.d("Player: ", "Falha em reproduzir o audio!");
                    break;
                case VorbisPlayer.PLAYING_FINISHED:
                    Log.d("Player: ", "Audio reproduzido com sucesso!");
                    break;
                case VorbisPlayer.PLAYING_STARTED:
                    Log.d("Player: ", "Começando a reproduzir o audio!");
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
        fileName += "/audiorecordtest.ogg";
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
        byte[] bytearray = new byte[2048];

        for (int readNum; (readNum = file.read(bytearray)) != -1; ) {
            bos.write(bytearray, 0, readNum);
        }

        byte[] bytes = bos.toByteArray();

        return bytes;
    }

    private void convertBytesToFile(byte[] bytearray, String suffix) {
        try {
            File outputFile = File.createTempFile("file", suffix, getCacheDir());
            outputFile.deleteOnExit();
            FileOutputStream fileoutputstream = new FileOutputStream(getExternalCacheDir().getAbsolutePath() + "/audio.ogg");
            fileoutputstream.write(bytearray);
            fileoutputstream.close();

        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    @SuppressLint("SetTextI18n")
    private void startRecording() {
        if (vorbisRecorder == null || vorbisRecorder.isStopped()) {
            //Get location to save to
            File fileToSaveTo = new File(getExternalCacheDir().getAbsolutePath(), "saveTo.ogg");
            //Create our recorder if necessary
            if (vorbisRecorder == null) {
                vorbisRecorder = new VorbisRecorder(fileToSaveTo, recordingHandler);
            }

            vorbisRecorder.start(8000, 2, 32000);
            recordButton.setText("Stop");
        }
    }

    private void stopRecording() throws IOException {
        vorbisRecorder.stop();
        recordButton.setText("Start");
        chatUtils.write(convert(getExternalCacheDir().getAbsolutePath() + "/saveTo.ogg"), -1);
    }


    private void initBluetooth() {
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth não detectado!", Toast.LENGTH_SHORT).show();
        }
    }


    private void init() {
        listMainChat = findViewById(R.id.list_conversation);
        recording = false;
        edCreateMessage = findViewById(R.id.ed_enter_message);
        btnSendMessage = findViewById(R.id.btn_send_message);
        playButton = findViewById(R.id.btn_play);
        adapterMainChat = new ArrayAdapter(context, android.R.layout.simple_list_item_1);
        listMainChat.setAdapter(adapterMainChat);
        recordButton = new Button(context);
        recordButton.setText("Start");
        recordButton.setOnClickListener(v -> {
                    if(!recording){
                        startRecording();
                        recording = true;
                        return;
                    }else{
                        try {
                            stopRecording();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        recording = false;
                        return;
                    }
        });
        playButton.setOnClickListener(v -> {
            if (vorbisPlayer == null) {
                try {
                    File fileToSaveTo = new File(getExternalCacheDir().getAbsolutePath(), "saveTo.ogg");
                    if(!fileToSaveTo.exists()){
                        return;
                    }
                    vorbisPlayer = new VorbisPlayer(fileToSaveTo, playerHandler);
                    vorbisPlayer.start();
                    vorbisPlayer.stop();
                } catch (FileNotFoundException e) {
                    Log.e("erroPlayer", "Failed to find saveTo.ogg", e);
                    Toast.makeText(context, "Failed to find file to play!", Toast.LENGTH_SHORT).show();
                }
            } else if (vorbisPlayer.isPlaying()) {
                vorbisPlayer.stop();
                playButton.setText("Audio");
            } else {
                vorbisPlayer.start();
                playButton.setText("Parar");
            }
        });
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