package yuan.family.com.socketconnectnative;

import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import com.google.gson.Gson;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;

import yuan.family.com.socketconnectnative.data.CpuInfo;

public class SocketConnNativeService extends Service {
    final String TAG = SocketConnNativeService.class.getSimpleName();
    private WindowManager mWindowManager;
    private WindowManager.LayoutParams wmParams;
    private LocalSocket mSocket;
    private InputStream mIn;
    private OutputStream mOut;
    private Handler mHandler;
    private Handler mThreadHandler;
    private HandlerThread mHandlerThread;
    private final int CONN_SOCKET = 101;
    private final int SOCKET_RESULT = 101;

    private TextView mCpuFreqTV;

    public SocketConnNativeService() {
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }

    private void initWindowParams() {
        mWindowManager = (WindowManager) getApplication().getSystemService(getApplication().WINDOW_SERVICE);
        wmParams = new WindowManager.LayoutParams();
        wmParams.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        wmParams.format = PixelFormat.TRANSLUCENT;
        wmParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        wmParams.gravity = Gravity.LEFT | Gravity.TOP;
        wmParams.width = WindowManager.LayoutParams.MATCH_PARENT;
        wmParams.height = WindowManager.LayoutParams.WRAP_CONTENT;
    }

    private void initView() {
        initWindowParams();
        View dialogView = LayoutInflater.from(this).inflate(R.layout.cpu_freq_view, null);
        mCpuFreqTV = dialogView.findViewById(R.id.cpu_freq);
        dialogView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mWindowManager.removeViewImmediate(v);
                stopSelf();
            }
        });
        mWindowManager.addView(dialogView, wmParams);
    }

    private Notification getNotification(){
        NotificationManager mNotiManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationChannel mChannel = new NotificationChannel("socket_native_conn", "socket_native_conn", NotificationManager.IMPORTANCE_DEFAULT);
        mNotiManager.createNotificationChannel(mChannel);
        Notification.Builder mBuilder = new Notification.Builder(this);
        mBuilder.setShowWhen(false);
        mBuilder.setAutoCancel(false);
        mBuilder.setSmallIcon(R.mipmap.ic_launcher);
        mBuilder.setContentText("SocketConnNative keep");
        mBuilder.setContentTitle("SocketConnNative keep");
        mBuilder.setChannelId("socket_native_conn");

        return mBuilder.build();
    }

    private boolean connect() {
        if (mSocket != null && mSocket.isConnected()) {
            return true;
        }
        try {
            // a non-server socket
            mSocket = new LocalSocket();
            // LocalSocketAddress.Namespace.RESERVED keep namespace
            LocalSocketAddress address = new LocalSocketAddress("nativeservice", LocalSocketAddress.Namespace.RESERVED);
            mSocket.connect(address);
            mIn = mSocket.getInputStream();
            mOut = mSocket.getOutputStream();
        } catch (Exception ex) {
            ex.printStackTrace();
            //disconnect();
            return false;
        }
        return true;
    }

    public void disconnect() {
        try {
            if (mSocket != null) {
                mSocket.shutdownInput();
                mSocket.shutdownOutput();
                mSocket.close();
            }

        } catch (Exception ex) {
            ex.printStackTrace();
        }
        try {
            if (mIn != null) {
                mIn.close();
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
        try {
            if (mOut != null) {
                mOut.close();
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
        mSocket = null;
        mIn = null;
        mOut = null;
    }

    protected boolean sendCommand(byte[] cmd) {
        try {
            String prefixCmd = "0 traceability ";
            byte fullCmd[] = new byte[prefixCmd.length() + cmd.length];
            System.arraycopy(prefixCmd.getBytes(), 0, fullCmd, 0, prefixCmd.length());
            System.arraycopy(cmd, 0, fullCmd, prefixCmd.length(), cmd.length);
            if (mOut != null) {
                mOut.write(fullCmd, 0, fullCmd.length);
            }
        } catch (Exception ex) {
            Log.e(TAG, "write error");
            return false;
        }
        return true;
    }

    public String connSocketNative(byte[] cmd) {
        byte[] result = new byte[128];
        StringBuilder stringBuilder = new StringBuilder();
        if (!connect()) {
            Log.d(TAG, "Connecting nativeservice proxy fail!");
            mThreadHandler.sendEmptyMessage(CONN_SOCKET);
        } else if (!sendCommand(cmd)) {
            Log.d(TAG, "Send command to nativeservice proxy fail!");
            mThreadHandler.sendEmptyMessage(CONN_SOCKET);
        } else {
            BufferedReader br = null;
            try {
                br = new BufferedReader(new InputStreamReader(mIn, "UTF-8"));
                String resultStr = br.readLine();
                while (!TextUtils.isEmpty(resultStr)) {
                    stringBuilder.append(resultStr);
                    resultStr = br.readLine();
                }
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }

        disconnect();
        return stringBuilder.toString().replace("[?]", "").trim();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        startForeground(1, getNotification());

        mHandler = new MainHandler(Looper.getMainLooper());
        mHandlerThread = new HandlerThread("thread", Thread.MAX_PRIORITY);
        mHandlerThread.start();
        mThreadHandler = new ThreadHandler(mHandlerThread.getLooper());
        initView();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        mThreadHandler.sendEmptyMessage(CONN_SOCKET);
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopForeground(true);
    }

    private class ThreadHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case CONN_SOCKET:
                    String result = connSocketNative(new byte[]{'A'});
                    Gson gson = new Gson();
                    CpuInfo cpuInfo = gson.fromJson(result, CpuInfo.class);
                    if (cpuInfo != null && cpuInfo.getCode() == 200) {
                        mHandler.sendMessage(Message.obtain(mHandler, SOCKET_RESULT, cpuInfo.getCpu()));
                    }
                    break;
                default:
                    break;
            }
        }

        public ThreadHandler(Looper looper) {
            super(looper);
        }
    }

    private class MainHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case SOCKET_RESULT:
                    mCpuFreqTV.setText(msg.obj.toString());
                    mThreadHandler.sendEmptyMessageDelayed(CONN_SOCKET, 1000);
                    break;
                default:
                    break;
            }
        }

        public MainHandler(Looper looper) {
            super(looper);
        }
    }
}
