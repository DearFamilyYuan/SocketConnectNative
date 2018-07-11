package yuan.family.com.socketconnectnative;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;

public class SocketConnectNativeActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_socket_connect_native);
    }

    private void startSocketService() {
        Intent intent = new Intent(this, SocketConnNativeService.class);
        startForegroundService(intent);
    }

    public void startSocketConn(View v) {
        startSocketService();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }
}
