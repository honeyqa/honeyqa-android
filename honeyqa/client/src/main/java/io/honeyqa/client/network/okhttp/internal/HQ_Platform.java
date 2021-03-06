/*
 * Copyright (C) 2012 Square, Inc.
 * Copyright (C) 2012 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.honeyqa.client.network.okhttp.internal;

import android.annotation.TargetApi;
import android.os.Build;

import io.honeyqa.client.network.okhttp.HQ_Protocol;

import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

import javax.net.ssl.SSLSocket;

import io.honeyqa.client.network.okio.Buffer;

import static io.honeyqa.client.network.okhttp.internal.HQ_Internal.logger;

/**
 * Access to platform-specific features.
 * <p/>
 * <h3>Server name indication (SNI)</h3>
 * Supported on Android 2.3+.
 * <p/>
 * <h3>Session Tickets</h3>
 * Supported on Android 2.3+.
 * <p/>
 * <h3>Android Traffic Stats (Socket Tagging)</h3>
 * Supported on Android 4.0+.
 * <p/>
 * <h3>ALPN (Application Layer HQ_Protocol Negotiation)</h3>
 * Supported on Android 5.0+. The APIs were present in Android 4.4, but that implementation was
 * unstable.
 * <p/>
 * Supported on OpenJDK 7 and 8 (via the JettyALPN-boot library).
 */
public class HQ_Platform {
    private static final HQ_Platform PLATFORM = findPlatform();

    public static HQ_Platform get() {
        return PLATFORM;
    }

    /**
     * Prefix used on custom headers.
     */
    public String getPrefix() {
        return "OkHttp";
    }

    public void logW(String warning) {
        System.out.println(warning);
    }

    public void tagSocket(Socket socket) throws SocketException {
    }

    public void untagSocket(Socket socket) throws SocketException {
    }

    /**
     * Configure TLS extensions on {@code sslSocket} for {@code route}.
     *
     * @param hostname non-null for client-side handshakes; null for
     *                 server-side handshakes.
     */
    public void configureTlsExtensions(SSLSocket sslSocket, String hostname,
                                       List<HQ_Protocol> protocols) {
    }

    /**
     * Called after the TLS handshake to release resources allocated by {@link
     * #configureTlsExtensions}.
     */
    public void afterHandshake(SSLSocket sslSocket) {
    }

    /**
     * Returns the negotiated protocol, or null if no protocol was negotiated.
     */
    public String getSelectedProtocol(SSLSocket socket) {
        return null;
    }

    public void connectSocket(Socket socket, InetSocketAddress address,
                              int connectTimeout) throws IOException {
        socket.connect(address, connectTimeout);
    }

    /**
     * Attempt to match the host runtime to a capable HQ_Platform implementation.
     */
    @TargetApi(Build.VERSION_CODES.KITKAT)
    private static HQ_Platform findPlatform() {
        // Attempt to find Android 2.3+ APIs.
        try {
            try {
                Class.forName("com.android.org.conscrypt.OpenSSLSocketImpl");
            } catch (ClassNotFoundException e) {
                // Older platform before being unbundled.
                Class.forName("org.apache.harmony.xnet.provider.jsse.OpenSSLSocketImpl");
            }

            HQ_OptionalMethod<Socket> setUseSessionTickets
                    = new HQ_OptionalMethod<>(null, "setUseSessionTickets", boolean.class);
            HQ_OptionalMethod<Socket> setHostname
                    = new HQ_OptionalMethod<>(null, "setHostname", String.class);
            Method trafficStatsTagSocket = null;
            Method trafficStatsUntagSocket = null;
            HQ_OptionalMethod<Socket> getAlpnSelectedProtocol = null;
            HQ_OptionalMethod<Socket> setAlpnProtocols = null;

            // Attempt to find Android 4.0+ APIs.
            try {
                Class<?> trafficStats = Class.forName("android.net.TrafficStats");
                trafficStatsTagSocket = trafficStats.getMethod("tagSocket", Socket.class);
                trafficStatsUntagSocket = trafficStats.getMethod("untagSocket", Socket.class);

                // Attempt to find Android 5.0+ APIs.
                try {
                    Class.forName("android.net.HQ_Network"); // Arbitrary class added in Android 5.0.
                    getAlpnSelectedProtocol = new HQ_OptionalMethod<>(byte[].class, "getAlpnSelectedProtocol");
                    setAlpnProtocols = new HQ_OptionalMethod<>(null, "setAlpnProtocols", byte[].class);
                } catch (ClassNotFoundException ignored) {
                }
            } catch (ClassNotFoundException | NoSuchMethodException ignored) {
            }

            return new Android(setUseSessionTickets, setHostname, trafficStatsTagSocket,
                    trafficStatsUntagSocket, getAlpnSelectedProtocol, setAlpnProtocols);
        } catch (ClassNotFoundException ignored) {
            // This isn't an Android runtime.
        }

        // Find Jetty's ALPN extension for OpenJDK.
        try {
            String negoClassName = "org.eclipse.jetty.alpn.ALPN";
            Class<?> negoClass = Class.forName(negoClassName);
            Class<?> providerClass = Class.forName(negoClassName + "$Provider");
            Class<?> clientProviderClass = Class.forName(negoClassName + "$ClientProvider");
            Class<?> serverProviderClass = Class.forName(negoClassName + "$ServerProvider");
            Method putMethod = negoClass.getMethod("put", SSLSocket.class, providerClass);
            Method getMethod = negoClass.getMethod("get", SSLSocket.class);
            Method removeMethod = negoClass.getMethod("remove", SSLSocket.class);
            return new JdkWithJettyBootPlatform(
                    putMethod, getMethod, removeMethod, clientProviderClass, serverProviderClass);
        } catch (ClassNotFoundException | NoSuchMethodException ignored) {
        }

        return new HQ_Platform();
    }

    /**
     * Android 2.3 or better.
     */
    private static class Android extends HQ_Platform {
        private final HQ_OptionalMethod<Socket> setUseSessionTickets;
        private final HQ_OptionalMethod<Socket> setHostname;

        // Non-null on Android 4.0+.
        private final Method trafficStatsTagSocket;
        private final Method trafficStatsUntagSocket;

        // Non-null on Android 5.0+.
        private final HQ_OptionalMethod<Socket> getAlpnSelectedProtocol;
        private final HQ_OptionalMethod<Socket> setAlpnProtocols;

        public Android(HQ_OptionalMethod<Socket> setUseSessionTickets, HQ_OptionalMethod<Socket> setHostname,
                       Method trafficStatsTagSocket, Method trafficStatsUntagSocket,
                       HQ_OptionalMethod<Socket> getAlpnSelectedProtocol, HQ_OptionalMethod<Socket> setAlpnProtocols) {
            this.setUseSessionTickets = setUseSessionTickets;
            this.setHostname = setHostname;
            this.trafficStatsTagSocket = trafficStatsTagSocket;
            this.trafficStatsUntagSocket = trafficStatsUntagSocket;
            this.getAlpnSelectedProtocol = getAlpnSelectedProtocol;
            this.setAlpnProtocols = setAlpnProtocols;
        }

        @Override
        public void connectSocket(Socket socket, InetSocketAddress address,
                                  int connectTimeout) throws IOException {
            try {
                socket.connect(address, connectTimeout);
            } catch (SecurityException se) {
                // Before android 4.3, socket.connect could throw a SecurityException
                // if opening a socket resulted in an EACCES error.
                IOException ioException = new IOException("Exception in connect");
                ioException.initCause(se);
                throw ioException;
            }
        }

        @Override
        public void configureTlsExtensions(
                SSLSocket sslSocket, String hostname, List<HQ_Protocol> protocols) {
            // Enable SNI and session tickets.
            if (hostname != null) {
                setUseSessionTickets.invokeOptionalWithoutCheckedException(sslSocket, true);
                setHostname.invokeOptionalWithoutCheckedException(sslSocket, hostname);
            }

            // Enable ALPN.
            if (setAlpnProtocols != null && setAlpnProtocols.isSupported(sslSocket)) {
                Object[] parameters = {concatLengthPrefixed(protocols)};
                setAlpnProtocols.invokeWithoutCheckedException(sslSocket, parameters);
            }
        }

        @Override
        public String getSelectedProtocol(SSLSocket socket) {
            if (getAlpnSelectedProtocol == null) return null;
            if (!getAlpnSelectedProtocol.isSupported(socket)) return null;

            byte[] alpnResult = (byte[]) getAlpnSelectedProtocol.invokeWithoutCheckedException(socket);
            return alpnResult != null ? new String(alpnResult, HQ_Util.UTF_8) : null;
        }

        @Override
        public void tagSocket(Socket socket) throws SocketException {
            if (trafficStatsTagSocket == null) return;

            try {
                trafficStatsTagSocket.invoke(null, socket);
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            } catch (InvocationTargetException e) {
                throw new RuntimeException(e.getCause());
            }
        }

        @Override
        public void untagSocket(Socket socket) throws SocketException {
            if (trafficStatsUntagSocket == null) return;

            try {
                trafficStatsUntagSocket.invoke(null, socket);
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            } catch (InvocationTargetException e) {
                throw new RuntimeException(e.getCause());
            }
        }
    }

    /**
     * OpenJDK 7+ with {@code org.mortbay.jetty.alpn/alpn-boot} in the boot class path.
     */
    private static class JdkWithJettyBootPlatform extends HQ_Platform {
        private final Method putMethod;
        private final Method getMethod;
        private final Method removeMethod;
        private final Class<?> clientProviderClass;
        private final Class<?> serverProviderClass;

        public JdkWithJettyBootPlatform(Method putMethod, Method getMethod, Method removeMethod,
                                        Class<?> clientProviderClass, Class<?> serverProviderClass) {
            this.putMethod = putMethod;
            this.getMethod = getMethod;
            this.removeMethod = removeMethod;
            this.clientProviderClass = clientProviderClass;
            this.serverProviderClass = serverProviderClass;
        }

        @TargetApi(Build.VERSION_CODES.KITKAT)
        @Override
        public void configureTlsExtensions(
                SSLSocket sslSocket, String hostname, List<HQ_Protocol> protocols) {
            List<String> names = new ArrayList<>(protocols.size());
            for (int i = 0, size = protocols.size(); i < size; i++) {
                HQ_Protocol protocol = protocols.get(i);
                if (protocol == HQ_Protocol.HTTP_1_0) continue; // No HTTP/1.0 for ALPN.
                names.add(protocol.toString());
            }
            try {
                Object provider = Proxy.newProxyInstance(HQ_Platform.class.getClassLoader(),
                        new Class[]{clientProviderClass, serverProviderClass}, new JettyNegoProvider(names));
                putMethod.invoke(null, sslSocket, provider);
            } catch (InvocationTargetException | IllegalAccessException e) {
                throw new AssertionError(e);
            }
        }

        @TargetApi(Build.VERSION_CODES.KITKAT)
        @Override
        public void afterHandshake(SSLSocket sslSocket) {
            try {
                removeMethod.invoke(null, sslSocket);
            } catch (IllegalAccessException | InvocationTargetException ignored) {
                throw new AssertionError();
            }
        }

        @TargetApi(Build.VERSION_CODES.KITKAT)
        @Override
        public String getSelectedProtocol(SSLSocket socket) {
            try {
                JettyNegoProvider provider =
                        (JettyNegoProvider) Proxy.getInvocationHandler(getMethod.invoke(null, socket));
                if (!provider.unsupported && provider.selected == null) {
                    logger.log(Level.INFO, "ALPN callback dropped: SPDY and HTTP/2 are disabled. "
                            + "Is alpn-boot on the boot class path?");
                    return null;
                }
                return provider.unsupported ? null : provider.selected;
            } catch (InvocationTargetException | IllegalAccessException e) {
                throw new AssertionError();
            }
        }
    }

    /**
     * Handle the methods of ALPN's ClientProvider and ServerProvider
     * without a compile-time dependency on those interfaces.
     */
    private static class JettyNegoProvider implements InvocationHandler {
        /**
         * This peer's supported protocols.
         */
        private final List<String> protocols;
        /**
         * Set when remote peer notifies ALPN is unsupported.
         */
        private boolean unsupported;
        /**
         * The protocol the server selected.
         */
        private String selected;

        public JettyNegoProvider(List<String> protocols) {
            this.protocols = protocols;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            String methodName = method.getName();
            Class<?> returnType = method.getReturnType();
            if (args == null) {
                args = HQ_Util.EMPTY_STRING_ARRAY;
            }
            if (methodName.equals("supports") && boolean.class == returnType) {
                return true; // ALPN is supported.
            } else if (methodName.equals("unsupported") && void.class == returnType) {
                this.unsupported = true; // Peer doesn't support ALPN.
                return null;
            } else if (methodName.equals("protocols") && args.length == 0) {
                return protocols; // Client advertises these protocols.
            } else if ((methodName.equals("selectProtocol") || methodName.equals("select"))
                    && String.class == returnType && args.length == 1 && args[0] instanceof List) {
                List<String> peerProtocols = (List) args[0];
                // Pick the first known protocol the peer advertises.
                for (int i = 0, size = peerProtocols.size(); i < size; i++) {
                    if (protocols.contains(peerProtocols.get(i))) {
                        return selected = peerProtocols.get(i);
                    }
                }
                return selected = protocols.get(0); // On no intersection, try peer's first protocol.
            } else if ((methodName.equals("protocolSelected") || methodName.equals("selected"))
                    && args.length == 1) {
                this.selected = (String) args[0]; // Server selected this protocol.
                return null;
            } else {
                return method.invoke(this, args);
            }
        }
    }

    /**
     * Returns the concatenation of 8-bit, length prefixed protocol names.
     * http://tools.ietf.org/html/draft-agl-tls-nextprotoneg-04#page-4
     */
    static byte[] concatLengthPrefixed(List<HQ_Protocol> protocols) {
        Buffer result = new Buffer();
        for (int i = 0, size = protocols.size(); i < size; i++) {
            HQ_Protocol protocol = protocols.get(i);
            if (protocol == HQ_Protocol.HTTP_1_0) continue; // No HTTP/1.0 for ALPN.
            result.writeByte(protocol.toString().length());
            result.writeUtf8(protocol.toString());
        }
        return result.readByteArray();
    }
}
