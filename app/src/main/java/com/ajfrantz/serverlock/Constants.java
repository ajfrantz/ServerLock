package com.ajfrantz.serverlock;

/**
 * Created by aj on 4/5/15.
 */
public class Constants {
    public static String QUERY_STATE_INTENT = "com.ajfrantz.ServerLock.query_state";
    public static String WAKE_UP_INTENT = "com.ajfrantz.ServerLock.wake_up";
    public static String SLEEP_INTENT = "com.ajfrantz.ServerLock.sleep";
    public static String LOST_CONTACT_INTENT = "com.ajfrantz.ServerLock.lost_contact";
    public static String SERVER_UP_INTENT = "com.ajfrantz.ServerLock.server_up";

    // These are sent by the service on changes (or in response to a query) in order to make the UI
    // state consistent.
    public static String ROAMING_INTENT = "com.ajfrantz.ServerLock.roaming";
    public static String SERVER_SLEEPING_INTENT = "com.ajfrantz.ServerLock.server_sleeping";
    public static String SERVER_WAKING_INTENT = "com.ajfrantz.ServerLock.server_waking";
    public static String SERVER_AWAKE_INTENT = "com.ajfrantz.ServerLock.server_awake";

    public interface NOTIFICATION_ID {
        public static int FOREGROUND_SERVICE = 1337;
    }
}
