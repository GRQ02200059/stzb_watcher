package hev.sockstun;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ServiceInfo;
import android.net.VpnService;
import android.os.Build;
import android.os.ParcelFileDescriptor;

import androidx.core.app.NotificationCompat;

import com.example.myapplication.MainActivity;
import com.example.myapplication.R;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class TProxyService extends VpnService {
    private static native void TProxyStartService(String configPath, int fd);
    private static native void TProxyStopService();
    private static native long[] TProxyGetStats();

    public static final String ACTION_CONNECT = "hev.sockstun.CONNECT";
    public static final String ACTION_DISCONNECT = "hev.sockstun.DISCONNECT";

    private static boolean nativeReady = false;

    static {
        try {
            System.loadLibrary("hev-socks5-tunnel");
            nativeReady = true;
        } catch (Throwable ignored) {
            nativeReady = false;
        }
    }

    public static boolean isNativeReady() {
        return nativeReady;
    }

    private ParcelFileDescriptor tunFd = null;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_DISCONNECT.equals(intent.getAction())) {
            stopTunnel();
            return START_NOT_STICKY;
        }
        startTunnel();
        return START_STICKY;
    }

    @Override
    public void onRevoke() {
        stopTunnel();
        super.onRevoke();
    }

    private void startTunnel() {
        if (tunFd != null) {
            return;
        }
        if (!nativeReady) {
            stopSelf();
            return;
        }

        Preferences prefs = new Preferences(this);
        VpnService.Builder builder = new VpnService.Builder();
        builder.setBlocking(false);
        builder.setMtu(prefs.getTunnelMtu());

        String session = "";
        if (prefs.getIpv4()) {
            builder.addAddress(prefs.getTunnelIpv4Address(), prefs.getTunnelIpv4Prefix());
            builder.addRoute("0.0.0.0", 0);
            if (!prefs.getRemoteDns() && !prefs.getDnsIpv4().isEmpty()) {
                builder.addDnsServer(prefs.getDnsIpv4());
            }
            session = "IPv4";
        }
        if (prefs.getIpv6()) {
            builder.addAddress(prefs.getTunnelIpv6Address(), prefs.getTunnelIpv6Prefix());
            builder.addRoute("::", 0);
            if (!prefs.getRemoteDns() && !prefs.getDnsIpv6().isEmpty()) {
                builder.addDnsServer(prefs.getDnsIpv6());
            }
            session = session.isEmpty() ? "IPv6" : session + " + IPv6";
        }
        if (prefs.getRemoteDns()) {
            builder.addDnsServer(prefs.getMappedDns());
        }

        boolean disallowSelf = true;
        if (prefs.getGlobal()) {
            session += "/Global";
        } else {
            for (String appName : prefs.getApps()) {
                try {
                    builder.addAllowedApplication(appName);
                    disallowSelf = false;
                } catch (NameNotFoundException ignored) {
                }
            }
            session += "/per-App";
        }
        if (disallowSelf) {
            try {
                builder.addDisallowedApplication(getPackageName());
            } catch (NameNotFoundException ignored) {
            }
        }

        builder.setSession(session);
        tunFd = builder.establish();
        if (tunFd == null) {
            stopSelf();
            return;
        }

        File confFile = new File(getCacheDir(), "tproxy.conf");
        try {
            writeConfig(confFile, prefs);
        } catch (IOException e) {
            stopTunnel();
            return;
        }

        TProxyStartService(confFile.getAbsolutePath(), tunFd.getFd());
        prefs.setEnable(true);
        startForegroundCompat();
    }

    private void stopTunnel() {
        if (tunFd == null) {
            stopSelf();
            return;
        }

        new Preferences(this).setEnable(false);
        stopForeground(STOP_FOREGROUND_REMOVE);
        TProxyStopService();

        try {
            tunFd.close();
        } catch (IOException ignored) {
        }
        tunFd = null;
        stopSelf();
    }

    private void writeConfig(File file, Preferences prefs) throws IOException {
        if (!file.exists()) {
            file.createNewFile();
        }

        StringBuilder conf = new StringBuilder();
        conf.append("misc:\n");
        conf.append("  task-stack-size: ").append(prefs.getTaskStackSize()).append('\n');
        conf.append("tunnel:\n");
        conf.append("  mtu: ").append(prefs.getTunnelMtu()).append('\n');
        conf.append("socks5:\n");
        conf.append("  port: ").append(prefs.getSocksPort()).append('\n');
        conf.append("  address: '").append(prefs.getSocksAddress()).append("'\n");
        conf.append("  udp: '").append(prefs.getUdpInTcp() ? "tcp" : "udp").append("'\n");

        if (!prefs.getSocksUdpAddress().isEmpty()) {
            conf.append("  udp-address: '").append(prefs.getSocksUdpAddress()).append("'\n");
        }
        if (!prefs.getSocksUsername().isEmpty() && !prefs.getSocksPassword().isEmpty()) {
            conf.append("  username: '").append(prefs.getSocksUsername()).append("'\n");
            conf.append("  password: '").append(prefs.getSocksPassword()).append("'\n");
        }
        if (prefs.getRemoteDns()) {
            conf.append("mapdns:\n");
            conf.append("  address: ").append(prefs.getMappedDns()).append('\n');
            conf.append("  port: 53\n");
            conf.append("  network: 240.0.0.0\n");
            conf.append("  netmask: 240.0.0.0\n");
            conf.append("  cache-size: 10000\n");
        }

        FileOutputStream fos = new FileOutputStream(file, false);
        fos.write(conf.toString().getBytes());
        fos.close();
    }

    private void startForegroundCompat() {
        String channelId = "sockstun";
        NotificationManager manager =
            (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                channelId,
                "SOCKS5 Tunnel",
                NotificationManager.IMPORTANCE_LOW
            );
            manager.createNotificationChannel(channel);
        }

        Intent openIntent = new Intent(this, MainActivity.class);
        openIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );

        Notification notification = new NotificationCompat.Builder(this, channelId)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("开源桥接运行中")
            .setSmallIcon(android.R.drawable.sym_def_app_icon)
            .setContentIntent(pendingIntent)
            .build();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(2, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(2, notification);
        }
    }
}
