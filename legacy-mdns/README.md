# Legacy Android multicast compatibility

This is the Java source of org.jmdns:jmdns:3.6.3, built locally with its existing Apache-2.0 license. It replaces the Maven binary, so the APK contains one mDNS implementation. No protocol, record, TXT, discovery or probing behavior is changed.

Source artifact: https://repo.maven.apache.org/maven2/org/jmdns/jmdns/3.6.3/jmdns-3.6.3-sources.jar

Original artifact SHA256: cad6d4c88381bcc2a70da44b8d2818b0e300e7d56ceb5263df7cee96e1a08cfb

The only code modification is in JmDNSImpl.openMulticastSocket: create an unbound multicast socket, explicitly enable SO_REUSEADDR, then bind it. Release the candidate socket if setup fails. Android 4.2.2's MulticastSocket(SocketAddress) binds in its DatagramSocket superclass before enabling address reuse, which can fail when the system already uses the mDNS port. Preserve JmDNS's interface selection, multicast group and hop limit.

Reference Android platform source: https://android.googlesource.com/platform/libcore/+/android-4.2.2_r1/luni/src/main/java/java/net/MulticastSocket.java

The shared module's LegacyMdnsPortReuseTest loads the real implementation, substitutes only the Android 4.2 constructor ordering, and exercises a real occupied UDP port. It failed with BindException before the patch. This test does not emulate a complete Android head unit or establish actual iPhone connectivity.
