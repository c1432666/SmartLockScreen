package com.pvsagar.smartlockscreen.applogic_objects;

import android.app.Notification;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.service.notification.StatusBarNotification;
import android.support.v7.widget.CardView;

import com.pvsagar.smartlockscreen.services.NotificationService;

/**
 * Created by PV on 10/7/2014.
 */
public class LockScreenNotification {

    private int notification_id;
    private Notification mNotification;
    private String packageName;
    private String tag;
    private boolean isOngoing;
    private String key;
    private CardView cardView;
    private boolean isShown;

    private static String LOG_TAG = LockScreenNotification.class.getSimpleName();


    public LockScreenNotification(int notification_id, Notification mNotification,String packageName, boolean isOngoing, String tag, String key){
        this.notification_id = notification_id;
        this.mNotification = mNotification;
        this.packageName = packageName;
        this.isOngoing = isOngoing;
        this.tag = tag;
        this.key = key;
        this.isShown = false;
    }
    public LockScreenNotification(StatusBarNotification sbn){
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2){
            this.notification_id = sbn.getId();
            this.mNotification = sbn.getNotification();
            this.packageName = sbn.getPackageName();
            this.isOngoing = sbn.isOngoing();
            this.tag = sbn.getTag();
            this.isShown = false;
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP){
                this.key = sbn.getKey();
            }
        }
    }

    public String getKey(){
        return this.key;
    }

    public int getId(){
        return notification_id;
    }

    public Notification getNotification(){
        return mNotification;
    }

    public String getPackageName(){
        return packageName;
    }

    public boolean isOngoing(){
        return isOngoing;
    }

    public String getTag(){
        return tag;
    }

    public boolean containsCardView(){
        if(cardView != null){
            return true;
        }
        return false;
    }

    public void setCardView(CardView cardView){
        this.cardView = cardView;
    }

    public CardView getCardView(){
        return this.cardView;
    }

    public void setShown(boolean flag){
        this.isShown = flag;
    }

    public boolean isShown(){
        return this.isShown;
    }

    public void dismiss(Context context){
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2){
            /*for(int i=0; i< NotificationService.currentNotifications.size(); i++){
                if(this.packageName.equals(NotificationService.currentNotifications.get(i)) &&
                        this.notification_id == NotificationService.currentNotifications.get(i).getId()){
                    
                }
            }*/
            Intent intent = new Intent(context, NotificationService.class);
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                intent.putExtra(NotificationService.EXTRAS_CANCEL_NOTIFICATION_KEY, getKey());
//                Log.d(LOG_TAG,getKey());
            } else if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2 &&
                    Build.VERSION.SDK_INT <= Build.VERSION_CODES.KITKAT){
                Bundle bundle = new Bundle();
                bundle.putString(NotificationService.EXTRAS_CANCEL_NOTIFICATION_PACKAGE,packageName);
                bundle.putString(NotificationService.EXTRAS_CANCEL_NOTIFICATION_TAG,tag);
                bundle.putInt(NotificationService.EXTRAS_CANCEL_NOTIFICATION_ID,notification_id);
                intent.putExtra(NotificationService.EXTRAS_CANCEL_NOTIFICATION_BUNDLE,bundle);
            }
            intent.setAction(NotificationService.ACTION_CANCEL_NOTIFICATION);
//            Log.d(LOG_TAG,"Starting Service");
            context.startService(intent);
        }
    }
}
