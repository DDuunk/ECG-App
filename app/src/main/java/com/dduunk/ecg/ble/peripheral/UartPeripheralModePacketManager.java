package com.dduunk.ecg.ble.peripheral;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import com.dduunk.ecg.ble.UartPacket;
import com.dduunk.ecg.ble.UartPacketManagerBase;
import com.dduunk.ecg.mqtt.MqttManager;
import com.dduunk.ecg.mqtt.MqttSettings;

import java.nio.charset.Charset;

public class UartPeripheralModePacketManager extends UartPacketManagerBase {

    // Log
    private final static String TAG = UartPeripheralModePacketManager.class.getSimpleName();

    public UartPeripheralModePacketManager(@NonNull Context context, @Nullable Listener listener, boolean isPacketCacheEnabled, @Nullable MqttManager mqttManager) {
        super(context, listener, isPacketCacheEnabled, mqttManager);
    }

    public void send(@NonNull UartPeripheralService uartPeripheralService, @NonNull byte[] data/*, BlePeripheral.UpdateDatabaseCompletionHandler completionHandler*/) {
        mSentBytes += data.length;
        uartPeripheralService.setRx(data);
    }

    public void send(@NonNull UartPeripheralService uartPeripheralService, @NonNull String text, boolean wasReceivedFromMqtt) {
        if (mMqttManager != null) {
            // Mqtt publish to TX
            if (MqttSettings.isPublishEnabled(mContext)) {
                final String topic = MqttSettings.getPublishTopic(mContext, MqttSettings.kPublishFeed_TX);
                if (topic != null) {
                    final int qos = MqttSettings.getPublishQos(mContext, MqttSettings.kPublishFeed_TX);
                    mMqttManager.publish(topic, text, qos);
                }
            }
        }

        // Create data and send to Uart
        byte[] data = text.getBytes(Charset.forName("UTF-8"));
        UartPacket uartPacket = new UartPacket(null, UartPacket.TRANSFERMODE_TX, data);

        try {
            mPacketsSemaphore.acquire();        // don't append more data, till the delegate has finished processing it
        } catch (InterruptedException e) {
            Log.w(TAG, "InterruptedException: " + e.toString());
        }
        mPackets.add(uartPacket);
        mPacketsSemaphore.release();

        Listener listener = mWeakListener.get();
        if (listener != null) {
            mMainHandler.post(() -> listener.onUartPacket(uartPacket));
        }

        final boolean isMqttEnabled = mMqttManager != null;
        final boolean shouldBeSent = !wasReceivedFromMqtt || (isMqttEnabled && MqttSettings.getSubscribeBehaviour(mContext) == MqttSettings.kSubscribeBehaviour_Transmit);

        if (shouldBeSent) {
            send(uartPeripheralService, data);
        }
    }
}