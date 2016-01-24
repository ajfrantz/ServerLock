package com.ajfrantz.serverlock;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.content.IntentFilter;
import android.support.v4.content.LocalBroadcastManager;


public class MainActivity extends Activity {
    private TextView statusText;
    private Button actionButton;
    private Intent nextAction;

    private class ServerStatusReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            System.out.println("ServerStatusReceiver::onReceive");

            statusText.setVisibility(View.VISIBLE);
            actionButton.setVisibility(View.VISIBLE);

            if(intent.getAction().equals(Constants.ROAMING_INTENT)) {
                System.out.println("ServerStatusReceiver::onReceive --> roaming");
                statusText.setText("You need to be on our wifi :-(");
                actionButton.setVisibility(View.INVISIBLE);
            } else if(intent.getAction().equals(Constants.SERVER_SLEEPING_INTENT)) {
                System.out.println("ServerStatusReceiver::onReceive --> sleeping");
                statusText.setText("Server (maybe) sleeping.");
                actionButton.setText("Wake it up!");

                nextAction.setAction(Constants.WAKE_UP_INTENT);
            } else if(intent.getAction().equals(Constants.SERVER_WAKING_INTENT)) {
                System.out.println("ServerStatusReceiver::onReceive --> waking");
                statusText.setText("Trying to wake up the server...");
                actionButton.setText("Give up");

                nextAction.setAction(Constants.SLEEP_INTENT);
            } else if(intent.getAction().equals(Constants.SERVER_AWAKE_INTENT)) {
                System.out.println("ServerStatusReceiver::onReceive --> awake");
                statusText.setText("Keeping server up!");
                actionButton.setText("Let it rest...");

                nextAction.setAction(Constants.SLEEP_INTENT);
            } else {
                System.out.println("ServerStatusReceiver::onReceive --> unknown intent");
                statusText.setVisibility(View.INVISIBLE);
                actionButton.setVisibility(View.INVISIBLE);
            }
        }
    }

    private ServerStatusReceiver server_receiver;

    @Override
    protected void onCreate(Bundle inState) {
        System.out.println("MainActivity::onCreate");

        super.onCreate(inState);
        setContentView(R.layout.activity_main);
        statusText = (TextView)findViewById(R.id.statusText);
        actionButton = (Button)findViewById(R.id.actionButton);
        nextAction = new Intent(this, ServerWakeService.class);
    }

    @Override
    protected void onResume() {
        super.onResume();

        System.out.println("MainActivity::onResume");
        statusText.setVisibility(View.INVISIBLE);
        actionButton.setVisibility(View.INVISIBLE);

        if(server_receiver == null) server_receiver = new ServerStatusReceiver();

        IntentFilter filt = new IntentFilter(Constants.ROAMING_INTENT);
        filt.addAction(Constants.SERVER_SLEEPING_INTENT);
        filt.addAction(Constants.SERVER_WAKING_INTENT);
        filt.addAction(Constants.SERVER_AWAKE_INTENT);
        LocalBroadcastManager.getInstance(this).registerReceiver(server_receiver, filt);

        Intent query = new Intent(this, ServerWakeService.class);
        query.setAction(Constants.QUERY_STATE_INTENT);
        System.out.println("Sending query!");
        startService(query);
    }

    @Override
    protected void onPause() {
        super.onPause();

        System.out.println("MainActivity::onPause");
        if(server_receiver != null) {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(server_receiver);
        }
    }

    public void onClick(View view) {
        System.out.println("MainActivity::onClick");
        startService(nextAction);
    }

}
