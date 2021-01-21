package com.example.bluetoothchatapp;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.ParcelUuid;
import android.util.Log;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.UUID;

public class ChatUtils {

    private Context context;
    private final Handler handler;
    private ConnectThread connectThread;
    private AcceptThread acceptThread;
    private BluetoothAdapter bluetoothAdapter;

    private final String APP_NAME = "BluetoothChatApp";
    public UUID APP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    public static final int STATE_NONE = 0;
    public static final int STATE_LISTEN = 1;
    public static final int STATE_CONNECTING = 2;
    public static final int STATE_CONNECTED = 3;

    private int state;

    public ChatUtils(Context context, Handler handler){
        this.context = context;
        this.handler = handler;

        state = STATE_NONE;
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    }


    public int getState() {
        return state;
    }

    public synchronized void setState(int state) {
        this.state = state;
        handler.obtainMessage(MainActivity.MESSAGE_STATE_CHANGED, state, -1).sendToTarget();
    }

    private synchronized void start(){
        if(connectThread != null){
            connectThread.cancel();
            connectThread = null;
        }

        if(acceptThread == null){
            acceptThread = new AcceptThread();
            acceptThread.start();
        }

        setState(STATE_LISTEN);
    }

    public synchronized void stop(){
        if(connectThread != null){
            connectThread.cancel();
            connectThread = null;
        }

        if(acceptThread != null){
            acceptThread.cancel();
            acceptThread = null;
        }

        setState(STATE_NONE);
    }

    public void connect(BluetoothDevice device){
        if(state == STATE_CONNECTING){
            connectThread.cancel();
            connectThread = null;
        }

        if(bluetoothAdapter.isDiscovering()){
            bluetoothAdapter.cancelDiscovery();
        }

        connectThread = new ConnectThread(device);
        connectThread.start();

        setState(STATE_CONNECTING);
    }

    private class AcceptThread extends Thread{
        private final BluetoothServerSocket serverSocket;

        public AcceptThread(){
            BluetoothServerSocket tmp = null;
            try{
                tmp = bluetoothAdapter.listenUsingInsecureRfcommWithServiceRecord(APP_NAME, APP_UUID);
            }catch (IOException e){
                Log.e("Accept->Constructor", e.toString());
            }

            serverSocket = tmp;
        }

        public void run(){
            BluetoothSocket socket = null;
            try{
                serverSocket.accept();
            }catch (IOException e){
                Log.e("Accept->Run", e.toString());
                try{
                    serverSocket.close();
                }catch (IOException e1){
                    Log.e("Accept->Close", e1.toString());
                }
            }

            if(serverSocket != null){
                switch (state){
                    case STATE_LISTEN:
                    case STATE_CONNECTING:
                        connected(socket.getRemoteDevice());
                        break;
                    case STATE_NONE:
                    case STATE_CONNECTED:
                        try{
                            socket.close();
                        }catch (IOException e){
                            Log.e("Accept->CloseSocket", e.toString());
                        }
                        break;
                }
            }
        }

        public void cancel(){
            try{
                serverSocket.close();
            }catch (IOException e){
                Log.e("Accept->CloseServer", e.toString());
            }
        }
    }



    private class ConnectThread extends Thread{
        private final BluetoothSocket socket;
        private final BluetoothDevice device;


        public ConnectThread(BluetoothDevice device){
            this.device = device;
            BluetoothSocket tmp = null;
            try{
                tmp = device.createInsecureRfcommSocketToServiceRecord(APP_UUID);
            } catch (IOException e) {
                Log.e("Connect->Constructor", e.toString());
            }
            socket = tmp;
        }

        public void run(){

            bluetoothAdapter.cancelDiscovery();

            try{
                socket.connect();
            }catch (IOException e){
                Log.e("Connect->Run", e.toString());
                try{
                    socket.close();
                }catch (IOException e1){
                    Log.e("Connect->CloseSocket", e1.toString());
                }
                connectionFailed();
                return;
            }

            synchronized (ChatUtils.this){
                connectThread = null;
            }

            connected(device);
        }

        public void cancel(){
            try{
                socket.close();
            }catch (IOException e){
                Log.e("Connect->Cancel", e.toString());
            }
        }

    }

    private synchronized void connectionFailed(){
        Message message = handler.obtainMessage(MainActivity.MESSAGE_TOAST);
        Bundle bundle = new Bundle();
        bundle.putString(MainActivity.TOAST, "Não conseguiu conectar ao dispositivo!");
        message.setData(bundle);
        handler.sendMessage(message);

        ChatUtils.this.start();
    }

    private synchronized void connected(BluetoothDevice device){
        if(connectThread != null){
            connectThread.cancel();
            connectThread = null;
        }

        Message message = handler.obtainMessage(MainActivity.MESSAGE_DEVICE_NAME);
        Bundle bundle = new Bundle();
        bundle.putString(MainActivity.DEVICE_NAME, device.getName());
        message.setData(bundle);
        handler.sendMessage(message);

        setState(STATE_CONNECTED);
    }


}
