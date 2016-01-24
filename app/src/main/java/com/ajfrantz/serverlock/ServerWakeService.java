package com.ajfrantz.serverlock;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.net.wifi.WifiInfo;
import android.os.IBinder;
import android.os.Message;
import android.support.v4.content.LocalBroadcastManager;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiManager.WifiLock;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.DatagramSocket;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;

public class ServerWakeService extends Service {
    private enum State {
        ROAMING, SLEEPING, WAKING, AWAKE
    }
    private State state = State.ROAMING;
    private Socket keepalive_sock;
    private Thread worker;

    private WifiLock wifi_lock;

    public ServerWakeService() {
    }

    private void keep_server_up() {
        byte[] wol_magic = {
                (byte)0xff, (byte)0xff, (byte)0xff, (byte)0xff, (byte)0xff, (byte)0xff,
                (byte)0x00, (byte)0x25, (byte)0x22, (byte)0x36, (byte)0xc2, (byte)0xff,
                (byte)0x00, (byte)0x25, (byte)0x22, (byte)0x36, (byte)0xc2, (byte)0xff,
                (byte)0x00, (byte)0x25, (byte)0x22, (byte)0x36, (byte)0xc2, (byte)0xff,
                (byte)0x00, (byte)0x25, (byte)0x22, (byte)0x36, (byte)0xc2, (byte)0xff,
                (byte)0x00, (byte)0x25, (byte)0x22, (byte)0x36, (byte)0xc2, (byte)0xff,
                (byte)0x00, (byte)0x25, (byte)0x22, (byte)0x36, (byte)0xc2, (byte)0xff,
                (byte)0x00, (byte)0x25, (byte)0x22, (byte)0x36, (byte)0xc2, (byte)0xff,
                (byte)0x00, (byte)0x25, (byte)0x22, (byte)0x36, (byte)0xc2, (byte)0xff,
                (byte)0x00, (byte)0x25, (byte)0x22, (byte)0x36, (byte)0xc2, (byte)0xff,
                (byte)0x00, (byte)0x25, (byte)0x22, (byte)0x36, (byte)0xc2, (byte)0xff,
                (byte)0x00, (byte)0x25, (byte)0x22, (byte)0x36, (byte)0xc2, (byte)0xff,
                (byte)0x00, (byte)0x25, (byte)0x22, (byte)0x36, (byte)0xc2, (byte)0xff,
                (byte)0x00, (byte)0x25, (byte)0x22, (byte)0x36, (byte)0xc2, (byte)0xff,
                (byte)0x00, (byte)0x25, (byte)0x22, (byte)0x36, (byte)0xc2, (byte)0xff,
                (byte)0x00, (byte)0x25, (byte)0x22, (byte)0x36, (byte)0xc2, (byte)0xff,
                (byte)0x00, (byte)0x25, (byte)0x22, (byte)0x36, (byte)0xc2, (byte)0xff,
        };

        byte [] buf = new byte[2048];

        while(state == State.WAKING || state == State.AWAKE) {
            if(!check_wifi()) {
                break;
            }

            System.out.println("Starting fresh keepalive cycle.");
            try {
                DatagramPacket wol_packet = new DatagramPacket(wol_magic, 102, InetAddress.getByName("192.168.200.255"), 7);
                DatagramSocket sock = new DatagramSocket();
                sock.setBroadcast(true);
                sock.send(wol_packet);
            } catch (IOException e) {
                System.out.println("WOL packet failed, that's odd...");
            }

            try {
                InetAddress server = InetAddress.getByName("server");
                keepalive_sock = new Socket(server, 5005);

                InputStream is = keepalive_sock.getInputStream();
                OutputStream os = keepalive_sock.getOutputStream();

                while(state == State.WAKING || state == State.AWAKE) {
                    if(!check_wifi()) {
                        break;
                    }

                    int nread = is.read(buf);

                    System.out.println("Server is alive.");

                    Intent server_up_intent = new Intent(this, ServerWakeService.class);
                    server_up_intent.setAction(Constants.SERVER_UP_INTENT);
                    startService(server_up_intent);

                    os.write(buf, 0, nread);
                }
            } catch (IOException e) {
                System.out.println("Keepalive loop failed :-(");

                Intent lost_contact_intent = new Intent(this, ServerWakeService.class);
                lost_contact_intent.setAction(Constants.LOST_CONTACT_INTENT);
                startService(lost_contact_intent);
            }
        }

        System.out.println("Keepalive thread shutting down...");
        Intent sleep_intent = new Intent(this, ServerWakeService.class);
        sleep_intent.setAction(Constants.SLEEP_INTENT);
        startService(sleep_intent);
    }

    private void wake_up() {

        // Check if we need to run the code below at all.
        switch(state) {
            case ROAMING:
            case WAKING:
            case AWAKE:
                return;
        }

        state = State.WAKING;

        // Lock wifi so we don't lose contact with the server -- we can only talk to it via our
        // wifi network, the fallback to cell service is not ok.
        if(wifi_lock == null) {
            WifiManager wifiManager = (WifiManager) getSystemService(WIFI_SERVICE);
            wifi_lock = wifiManager.createWifiLock("ServerLock");
            wifi_lock.setReferenceCounted(false);
        }
        wifi_lock.acquire();

        // Create an 'ongoing' intent so we're considered a 'foreground' service and not killed.
        // Plus, it's kind of nice to have the notification anyway.
        Intent app_intent = new Intent(this, MainActivity.class);
        app_intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent app_pi = PendingIntent.getActivity(this, 0, app_intent, 0);

        Intent sleep_intent = new Intent(this, ServerWakeService.class);
        sleep_intent.setAction(Constants.SLEEP_INTENT);
        PendingIntent sleep_pi = PendingIntent.getService(this, 0, sleep_intent, 0);

        Notification note = new Notification.Builder(this)
                .setSmallIcon(R.drawable.sleep_icon)
                .setContentTitle("ServerLock")
                .setTicker("ServerLock")
                .setContentText("Keeping the server awake...")
                .setContentIntent(app_pi)
                .setOngoing(true)
                .addAction(android.R.drawable.ic_lock_power_off, "Allow sleep", sleep_pi)
                .build();

        startForeground(Constants.NOTIFICATION_ID.FOREGROUND_SERVICE, note);

        // Start a worker thread that will connect to the server and do the actual work.
        if(worker == null) {
            worker = new Thread(new Runnable() {
                @Override
                public void run() {
                    keep_server_up();
                }
            });
            worker.start();
        }

        // Update the UI, if it's open.
        send_status_update();
    }

    private void go_to_bed() {

        // Force into sleeping mode, but allow ROAMING to override it.
        if (state != State.ROAMING) {
            state = State.SLEEPING;
        }

        // Clean up our wifi locks, notifications, and worker thread.
        if(wifi_lock != null) {
            wifi_lock.release();
        }
        stopForeground(true);
        stopSelf();

        if(worker != null) {
            worker.interrupt();
            worker = null;
        }

        // The worker thread might be stuck on I/O with the server.  In a minor violation of thread
        // isolation principles, we can force the thread to die faster by just yanking its socket
        // out from under it.
        if(keepalive_sock != null) {
            try {
                keepalive_sock.close();
            } catch (IOException e) {
                System.out.println("Socket shutdown error... ignored.");
            }
        }

        // Finally, update the UI if it's awake.
        send_status_update();
    }

    private void send_status_update() {
        Intent response = new Intent(this, MainActivity.class);

        switch(state) {
            case ROAMING:
                response.setAction(Constants.ROAMING_INTENT);
                break;

            case SLEEPING:
                response.setAction(Constants.SERVER_SLEEPING_INTENT);
                break;

            case WAKING:
                response.setAction(Constants.SERVER_WAKING_INTENT);
                break;

            case AWAKE:
                response.setAction(Constants.SERVER_AWAKE_INTENT);
                break;
        }

        System.out.println("Sending status (" + state.toString() + ")");
        LocalBroadcastManager.getInstance(this).sendBroadcast(response);
    }

    private class WifiChangeReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            System.out.println("WifiChangeReceiver::onReceive");

            check_wifi();
        }
    }

    private WifiChangeReceiver wifi_change_receiver = null;

    private boolean check_wifi() {
        WifiManager wifiManager = (WifiManager) getSystemService(WIFI_SERVICE);
        WifiInfo wifiInfo = wifiManager.getConnectionInfo();

        if(!wifiInfo.getSSID().equals("\"Loading...\"")) {
            System.out.println("Not on our wifi; things won't work :(");

            if(state != State.ROAMING) {
                go_to_bed();

                state = State.ROAMING;
                send_status_update();
            }

            return false;
        } else if(state == State.ROAMING) {
            // We can do better now!
            state = State.SLEEPING;
            send_status_update();
        }

        System.out.println("We're on the home network.");
        return true;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        System.out.println("ServerWakeService::onStartCommand");

        if(wifi_change_receiver == null) {
            System.out.println("Making WifiChangeReceiver");

            wifi_change_receiver = new WifiChangeReceiver();
            registerReceiver(wifi_change_receiver, new IntentFilter(WifiManager.NETWORK_STATE_CHANGED_ACTION));
        }

        check_wifi();

        if(intent == null) {
            System.out.println("ServerWakeService::onStartCommand --> null intent?");
        } else if(intent.getAction().equals(Constants.QUERY_STATE_INTENT)) {
            System.out.println("ServerWakeService::onStartCommand --> query state");
            send_status_update();
        } else if(state != State.ROAMING) {
            if(intent.getAction().equals(Constants.WAKE_UP_INTENT)) {
                System.out.println("ServerWakeService::onStartCommand --> wake up");
                wake_up();
            } else if(intent.getAction().equals(Constants.SLEEP_INTENT)) {
                System.out.println("ServerWakeService::onStartCommand --> go to bed");
                go_to_bed();
            } else if(intent.getAction().equals(Constants.SERVER_UP_INTENT) && state == State.WAKING) {
                System.out.println("ServerWakeService::onStartCommand --> server is up");
                state = State.AWAKE;
                send_status_update();
            } else if(intent.getAction().equals(Constants.LOST_CONTACT_INTENT) && state == State.AWAKE) {
                System.out.println("ServerWakeService::onStartCommand --> lost the server :-(");
                state = State.AWAKE;
                state = State.WAKING;
                send_status_update();
            } else {
                System.out.println("ServerWakeService::onStartCommand --> ignoring intent " + intent.getAction());
            }
        } else {
            System.out.println("ServerWakeService::onStartCommand --> rejected due to roaming");
        }

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if(wifi_change_receiver != null) {
            unregisterReceiver(wifi_change_receiver);
            wifi_change_receiver = null;
        }

        if(wifi_lock != null) {
            wifi_lock.release();
            wifi_lock = null;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        throw new UnsupportedOperationException("This is not a bindable service.");
    }
}
