package com.tejeet.ft311gpio.utils.accessories;

import static android.content.Context.RECEIVER_EXPORTED;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbAccessory;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Arrays;
import java.util.Locale;

public abstract class AccessoryInterface {
    private static final String TAG = "mytag";
    public static final int MSG_WHAT_ACCESSORY_ROW_DATA = -1;
    public static final int MSG_WHAT_ACCESSORY_NOT_CONNECTED = -2;
    public static final int MSG_WHAT_ACCESSORY_DETACHED = -3;
    public static final int MSG_WHAT_ACCESSORY_PERMISSION_NOT_GRANTED = -4;
    public static final int MSG_WHAT_ACCESSORY_INEQUALITY = -5;
    private static final String ACTION_USB_PERMISSION = "com.AccessoryInterface.USB_PERMISSION";
    private boolean mIsPermissionRequestPending = false;
    private UsbManager mUsbManager = null;
    private ParcelFileDescriptor mFileDescriptor = null;
    private FileInputStream mInputStream = null;
    private FileOutputStream mOutputStream = null;
    private FileChannel mInChanel = null;
    private FileChannel mOutChanel = null;
    private final ByteBuffer mInBuffer;
    private final Handler mWorkerHandler;
    private final Handler mCommunicationHandler;
    private final BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ACTION_USB_PERMISSION.equals(action)) {
                UsbAccessory accessory = (UsbAccessory)intent.getParcelableExtra("accessory");
                if (intent.getBooleanExtra("permission", false)) {
                    Log.d("mytag", "Permission Granted");
                    Message.obtain(mCommunicationHandler, MSG_WHAT_ACCESSORY_NOT_CONNECTED).sendToTarget();
                    open(accessory);
                } else {
                    Log.d("mytag", "Permission Denied");
                    Message.obtain(mCommunicationHandler, MSG_WHAT_ACCESSORY_PERMISSION_NOT_GRANTED).sendToTarget();
                }
                mIsPermissionRequestPending = false;
            } else if ("android.hardware.usb.action.USB_ACCESSORY_DETACHED".equals(action)) {
                close();
                Message.obtain(mCommunicationHandler, MSG_WHAT_ACCESSORY_DETACHED).sendToTarget();
            } else {
                Log.d("mytag", "Unknown Action ");
            }
        }
    };

    protected AccessoryInterface(Handler communicationHandler, int bufferSize) {
        HandlerThread workerHandlerThread = new HandlerThread("AccessoryInterface.WorkerHandlerThread");
        workerHandlerThread.start();
        this.mWorkerHandler = new Handler(workerHandlerThread.getLooper(), new Handler.Callback() {
            public boolean handleMessage(Message msg) {
                callback(msg);
                return true;
            }
        });
        this.mCommunicationHandler = communicationHandler != null ? communicationHandler : this.mWorkerHandler;
        this.mInBuffer = ByteBuffer.allocate(bufferSize);
    }

    public final void create(Context context) {
        this.mUsbManager = (UsbManager)context.getSystemService(Context.USB_SERVICE);
        PendingIntent permissionIntent = PendingIntent.getBroadcast(context, 0, new Intent("com.AccessoryInterface.USB_PERMISSION"), PendingIntent.FLAG_IMMUTABLE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(this.mUsbReceiver, new IntentFilter() {
                {
                    this.addAction(ACTION_USB_PERMISSION);
                    this.addAction("android.hardware.usb.action.USB_ACCESSORY_DETACHED");
                }
            }, RECEIVER_EXPORTED);
        }else {
            context.registerReceiver(this.mUsbReceiver, new IntentFilter() {
                {
                    this.addAction(ACTION_USB_PERMISSION);
                    this.addAction("android.hardware.usb.action.USB_ACCESSORY_DETACHED");
                }
            });
        }
        if (this.mFileDescriptor == null) {
            UsbAccessory[] accessories = this.mUsbManager.getAccessoryList();
            if (accessories != null && accessories[0] != null) {
                UsbAccessory accessory = accessories[0];
                if (!this.getManufacturer().equals(accessory.getManufacturer())) {
                    Message.obtain(this.mCommunicationHandler, -5, "Manufacturer is not matched!").sendToTarget();
                    return;
                }

                if (!this.getModel().equals(accessory.getModel())) {
                    Message.obtain(this.mCommunicationHandler, -5, "Model is not matched!").sendToTarget();
                    return;
                }

                if (!this.getVersion().equals(accessory.getVersion())) {
                    Message.obtain(this.mCommunicationHandler, -5, "Version is not matched!").sendToTarget();
                    return;
                }

                if (this.mUsbManager.hasPermission(accessory)) {
                    this.open(accessory);
                } else if (!this.mIsPermissionRequestPending) {
                    this.mUsbManager.requestPermission(accessory, permissionIntent);
                    this.mIsPermissionRequestPending = true;
                }
            } else {
                Message.obtain(this.mCommunicationHandler, -2).sendToTarget();
            }
        }

    }

    public final void destroy(Context context) {
        context.unregisterReceiver(this.mUsbReceiver);
        this.close();
    }

    protected void callback(Message msg) {
        String logMsg = String.format(Locale.ENGLISH, "default callback; override it; message code: %d", msg.what);
        if (msg.what == -1) {
            logMsg = String.format(Locale.ENGLISH, "%s; row data: %s", logMsg, Arrays.toString((byte[])((byte[])msg.obj)));
        }

        Log.w(TAG, logMsg);
    }

    public abstract String getManufacturer();

    public abstract String getModel();

    public abstract String getVersion();

    private void open(UsbAccessory accessory) {
        this.mFileDescriptor = this.mUsbManager.openAccessory(accessory);
        if (this.mFileDescriptor != null) {
            FileDescriptor fd = this.mFileDescriptor.getFileDescriptor();
            this.mInputStream = new FileInputStream(fd);
            this.mInChanel = this.mInputStream.getChannel();
            this.mOutputStream = new FileOutputStream(fd);
            this.mOutChanel = this.mOutputStream.getChannel();
            this.mWorkerHandler.post(new Runnable() {
                public void run() {
                    try {
                        if (mInChanel != null) {
                            int actuallyRead = mInChanel.read(mInBuffer);
                            Message.obtain(mCommunicationHandler, -1, actuallyRead, 0, mInBuffer.array()).sendToTarget();
                            mInBuffer.clear();
                            mWorkerHandler.post(this);
                        }
                    } catch (IOException e) {
                        mWorkerHandler.removeCallbacks(this);
                    }

                }
            });
        }

    }

    protected final void write(byte[] data) {
        try {
            if (this.mOutChanel != null) {
                this.mOutChanel.write(ByteBuffer.wrap(data));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    protected final void directWrite(byte[] data, int byteOffset, int byteCount) {
        try {
            if (this.mOutputStream != null) {
                this.mOutputStream.write(data, byteOffset, byteCount);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    private void close() {
        this.mWorkerHandler.removeCallbacksAndMessages((Object)null);

        try {
            if (this.mFileDescriptor != null) {
                this.mFileDescriptor.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            this.mFileDescriptor = null;
        }

        try {
            if (this.mInputStream != null) {
                this.mInputStream.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            this.mInputStream = null;
        }

        try {
            if (this.mInChanel != null) {
                this.mInChanel.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            this.mInChanel = null;
        }

        try {
            if (this.mOutputStream != null) {
                this.mOutputStream.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            this.mOutputStream = null;
        }

        try {
            if (this.mOutChanel != null) {
                this.mOutChanel.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            this.mOutChanel = null;
        }

    }
}