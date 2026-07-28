package hev.sockstun;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashSet;
import java.util.Set;

public class Preferences {
    public static final String PREFS_NAME = "SocksPrefs";
    public static final String SOCKS_ADDR = "SocksAddr";
    public static final String SOCKS_UDP_ADDR = "SocksUdpAddr";
    public static final String SOCKS_PORT = "SocksPort";
    public static final String SOCKS_USER = "SocksUser";
    public static final String SOCKS_PASS = "SocksPass";
    public static final String DNS_IPV4 = "DnsIpv4";
    public static final String DNS_IPV6 = "DnsIpv6";
    public static final String IPV4 = "Ipv4";
    public static final String IPV6 = "Ipv6";
    public static final String GLOBAL = "Global";
    public static final String UDP_IN_TCP = "UdpInTcp";
    public static final String REMOTE_DNS = "RemoteDNS";
    public static final String APPS = "Apps";
    public static final String ENABLE = "Enable";

    private final SharedPreferences prefs;

    public Preferences(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public String getSocksAddress() {
        return prefs.getString(SOCKS_ADDR, "127.0.0.1");
    }

    public void setSocksAddress(String addr) {
        prefs.edit().putString(SOCKS_ADDR, addr).apply();
    }

    public String getSocksUdpAddress() {
        return prefs.getString(SOCKS_UDP_ADDR, "");
    }

    public void setSocksUdpAddress(String addr) {
        prefs.edit().putString(SOCKS_UDP_ADDR, addr).apply();
    }

    public int getSocksPort() {
        return prefs.getInt(SOCKS_PORT, 1080);
    }

    public void setSocksPort(int port) {
        prefs.edit().putInt(SOCKS_PORT, port).apply();
    }

    public String getSocksUsername() {
        return prefs.getString(SOCKS_USER, "");
    }

    public void setSocksUsername(String user) {
        prefs.edit().putString(SOCKS_USER, user).apply();
    }

    public String getSocksPassword() {
        return prefs.getString(SOCKS_PASS, "");
    }

    public void setSocksPassword(String pass) {
        prefs.edit().putString(SOCKS_PASS, pass).apply();
    }

    public String getDnsIpv4() {
        return prefs.getString(DNS_IPV4, "1.1.1.1");
    }

    public void setDnsIpv4(String addr) {
        prefs.edit().putString(DNS_IPV4, addr).apply();
    }

    public String getDnsIpv6() {
        return prefs.getString(DNS_IPV6, "2606:4700:4700::1111");
    }

    public void setDnsIpv6(String addr) {
        prefs.edit().putString(DNS_IPV6, addr).apply();
    }

    public String getMappedDns() {
        return "198.18.0.2";
    }

    public boolean getUdpInTcp() {
        return prefs.getBoolean(UDP_IN_TCP, false);
    }

    public void setUdpInTcp(boolean enable) {
        prefs.edit().putBoolean(UDP_IN_TCP, enable).apply();
    }

    public boolean getRemoteDns() {
        return prefs.getBoolean(REMOTE_DNS, true);
    }

    public void setRemoteDns(boolean enable) {
        prefs.edit().putBoolean(REMOTE_DNS, enable).apply();
    }

    public boolean getIpv4() {
        return prefs.getBoolean(IPV4, true);
    }

    public void setIpv4(boolean enable) {
        prefs.edit().putBoolean(IPV4, enable).apply();
    }

    public boolean getIpv6() {
        return prefs.getBoolean(IPV6, true);
    }

    public void setIpv6(boolean enable) {
        prefs.edit().putBoolean(IPV6, enable).apply();
    }

    public boolean getGlobal() {
        return prefs.getBoolean(GLOBAL, false);
    }

    public void setGlobal(boolean enable) {
        prefs.edit().putBoolean(GLOBAL, enable).apply();
    }

    public Set<String> getApps() {
        return prefs.getStringSet(APPS, new HashSet<>());
    }

    public void setApps(Set<String> apps) {
        prefs.edit().putStringSet(APPS, apps).apply();
    }

    public boolean getEnable() {
        return prefs.getBoolean(ENABLE, false);
    }

    public void setEnable(boolean enable) {
        prefs.edit().putBoolean(ENABLE, enable).apply();
    }

    public int getTunnelMtu() {
        return 8500;
    }

    public String getTunnelIpv4Address() {
        return "198.18.0.1";
    }

    public int getTunnelIpv4Prefix() {
        return 32;
    }

    public String getTunnelIpv6Address() {
        return "fc00::1";
    }

    public int getTunnelIpv6Prefix() {
        return 128;
    }

    public int getTaskStackSize() {
        return 81920;
    }
}
