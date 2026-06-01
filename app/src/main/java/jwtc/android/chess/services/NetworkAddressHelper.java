package jwtc.android.chess.services;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiManager;
import android.os.Build;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

public final class NetworkAddressHelper {
    private NetworkAddressHelper() {
    }

    public static String getLikelyWifiIpv4Address(Context context) {
        String activeNetworkAddress = getActiveWifiIpv4Address(context);
        if (activeNetworkAddress != null) {
            return activeNetworkAddress;
        }

        List<String> candidates = getLikelyWifiIpv4Addresses();
        return candidates.isEmpty() ? null : candidates.get(0);
    }

    public static List<String> getLikelyWifiIpv4Addresses() {
        LinkedHashSet<String> addresses = new LinkedHashSet<>();
        List<String> preferred = new ArrayList<>();
        List<String> secondary = new ArrayList<>();

        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            if (interfaces == null) {
                return Collections.emptyList();
            }

            while (interfaces.hasMoreElements()) {
                NetworkInterface networkInterface = interfaces.nextElement();
                if (!networkInterface.isUp() || networkInterface.isLoopback()) {
                    continue;
                }

                String interfaceName = networkInterface.getName() == null ? "" : networkInterface.getName().toLowerCase(Locale.US);
                Enumeration<InetAddress> inetAddresses = networkInterface.getInetAddresses();
                while (inetAddresses.hasMoreElements()) {
                    InetAddress inetAddress = inetAddresses.nextElement();
                    if (!(inetAddress instanceof Inet4Address) || inetAddress.isLoopbackAddress()) {
                        continue;
                    }

                    String hostAddress = inetAddress.getHostAddress();
                    if (hostAddress == null || hostAddress.startsWith("169.254.")) {
                        continue;
                    }

                    if (interfaceName.startsWith("wlan") || interfaceName.startsWith("wifi")) {
                        preferred.add(hostAddress);
                    } else {
                        secondary.add(hostAddress);
                    }
                }
            }
        } catch (SocketException ignored) {
        }

        addresses.addAll(preferred);
        addresses.addAll(secondary);
        return new ArrayList<>(addresses);
    }

    public static String getActiveWifiIpv4Address(Context context) {
        ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager == null) {
            return null;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Network activeNetwork = connectivityManager.getActiveNetwork();
            if (activeNetwork == null) {
                return null;
            }

            NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(activeNetwork);
            if (capabilities == null || !capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                return null;
            }

            LinkProperties linkProperties = connectivityManager.getLinkProperties(activeNetwork);
            if (linkProperties == null) {
                return null;
            }

            for (LinkAddress linkAddress : linkProperties.getLinkAddresses()) {
                InetAddress inetAddress = linkAddress.getAddress();
                if (inetAddress instanceof Inet4Address && !inetAddress.isLoopbackAddress()) {
                    String hostAddress = inetAddress.getHostAddress();
                    if (hostAddress != null && !hostAddress.startsWith("169.254.")) {
                        return hostAddress;
                    }
                }
            }
        }

        return null;
    }

    public static String getWifiGatewayIp(Context context) {
        WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        if (wifiManager == null || wifiManager.getDhcpInfo() == null) {
            return null;
        }

        int gatewayAddress = wifiManager.getDhcpInfo().gateway;
        return String.format(Locale.US, "%d.%d.%d.%d",
            (gatewayAddress & 0xff),
            (gatewayAddress >> 8 & 0xff),
            (gatewayAddress >> 16 & 0xff),
            (gatewayAddress >> 24 & 0xff));
    }
}
