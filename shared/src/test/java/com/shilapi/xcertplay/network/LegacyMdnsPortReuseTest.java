package com.shilapi.xcertplay.network;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.SocketAddress;
import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

/** Runs the real mDNS implementation with API17's multicast constructor ordering. */
public class LegacyMdnsPortReuseTest {
    @Test public void android42CanStartDiscoveryWhenAnotherReusableSocketOwnsTheMdnsPort() throws Exception {
        String previousPort = System.getProperty("net.mdns.port");
        MulticastSocket existing = new MulticastSocket((SocketAddress) null);
        Object dns = null;
        try {
            existing.setReuseAddress(true);
            existing.bind(new InetSocketAddress(0));
            System.setProperty("net.mdns.port", Integer.toString(existing.getLocalPort()));
            ClassLoader loader = new Android42MdnsLoader(getClass().getClassLoader());
            Class<?> implementation = loader.loadClass("javax.jmdns.impl.JmDNSImpl");
            try {
                dns = implementation.getConstructor(InetAddress.class, String.class, long.class)
                        .newInstance(InetAddress.getByName("127.0.0.1"), "legacy-port-reuse-test", 0L);
            } catch (InvocationTargetException error) {
                throw new AssertionError("mDNS must enable port reuse before binding on Android 4.2", error.getCause());
            }
            MulticastSocket socket = (MulticastSocket) implementation.getMethod("getSocket").invoke(dns);
            assertEquals(existing.getLocalPort(), socket.getLocalPort());
            assertTrue(socket.getReuseAddress());
        } finally {
            if (dns != null) dns.getClass().getMethod("close").invoke(dns);
            existing.close();
            if (previousPort == null) System.clearProperty("net.mdns.port");
            else System.setProperty("net.mdns.port", previousPort);
        }
    }

    @Test public void failedMulticastJoinClosesTheNewSocketBeforeAnotherConnectionAttempt() throws Exception {
        String previousPort = System.getProperty("net.mdns.port");
        MulticastSocket existing = new MulticastSocket((SocketAddress) null);
        try {
            existing.setReuseAddress(true);
            existing.bind(new InetSocketAddress(0));
            System.setProperty("net.mdns.port", Integer.toString(existing.getLocalPort()));
            Android42MulticastSocket.lastOpened = null;
            Android42MulticastSocket.rejectJoin = true;
            Class<?> implementation = new Android42MdnsLoader(getClass().getClassLoader())
                    .loadClass("javax.jmdns.impl.JmDNSImpl");
            try {
                implementation.getConstructor(InetAddress.class, String.class, long.class)
                        .newInstance(InetAddress.getByName("127.0.0.1"), "legacy-join-failure-test", 0L);
                fail("The injected multicast join failure must be reported");
            } catch (InvocationTargetException expected) {
                assertTrue(expected.getCause() instanceof IOException);
            }
            assertNotNull(Android42MulticastSocket.lastOpened);
            assertTrue("Failed setup must release its port", Android42MulticastSocket.lastOpened.isClosed());
        } finally {
            Android42MulticastSocket.rejectJoin = false;
            if (Android42MulticastSocket.lastOpened != null) Android42MulticastSocket.lastOpened.close();
            existing.close();
            if (previousPort == null) System.clearProperty("net.mdns.port");
            else System.setProperty("net.mdns.port", previousPort);
        }
    }

    /**
     * API17 MulticastSocket(SocketAddress) calls DatagramSocket(address), which binds first,
     * then setReuseAddress(true). The host JVM uses a different order, so recreate only that
     * constructor behavior; all actual binds and port conflicts still use real OS sockets.
     */
    public static class Android42MulticastSocket extends MulticastSocket {
        static MulticastSocket lastOpened;
        static boolean rejectJoin;

        public Android42MulticastSocket(SocketAddress address) throws IOException {
            super((SocketAddress) null);
            setReuseAddress(false);
            if (address != null) bind(address);
            setReuseAddress(true);
            lastOpened = this;
        }

        @Override public void joinGroup(SocketAddress group, java.net.NetworkInterface net) throws IOException {
            if (rejectJoin) throw new IOException("Injected multicast join failure");
            super.joinGroup(group, net);
        }
    }

    private static class Android42MdnsLoader extends ClassLoader {
        Android42MdnsLoader(ClassLoader parent) { super(parent); }

        @Override protected synchronized Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (!name.startsWith("javax.jmdns.")) return super.loadClass(name, resolve);
            Class<?> loaded = findLoadedClass(name);
            if (loaded == null) {
                String path = name.replace('.', '/') + ".class";
                try (InputStream input = getParent().getResourceAsStream(path)) {
                    if (input == null) throw new ClassNotFoundException(name);
                    byte[] bytes = input.readAllBytes();
                    if (name.equals("javax.jmdns.impl.JmDNSImpl")) bytes = withAndroid42Constructor(bytes);
                    loaded = defineClass(name, bytes, 0, bytes.length);
                } catch (IOException error) { throw new ClassNotFoundException(name, error); }
            }
            if (resolve) resolveClass(loaded);
            return loaded;
        }

        private byte[] withAndroid42Constructor(byte[] original) {
            String oldType = "java/net/MulticastSocket";
            String replacement = Android42MulticastSocket.class.getName().replace('.', '/');
            ClassWriter writer = new ClassWriter(0);
            new ClassReader(original).accept(new ClassVisitor(Opcodes.ASM9, writer) {
                @Override public MethodVisitor visitMethod(int access, String name, String descriptor,
                        String signature, String[] exceptions) {
                    MethodVisitor target = super.visitMethod(access, name, descriptor, signature, exceptions);
                    return new MethodVisitor(Opcodes.ASM9, target) {
                        @Override public void visitTypeInsn(int opcode, String type) {
                            super.visitTypeInsn(opcode, opcode == Opcodes.NEW && type.equals(oldType) ? replacement : type);
                        }
                        @Override public void visitMethodInsn(int opcode, String owner, String method,
                                String desc, boolean isInterface) {
                            super.visitMethodInsn(opcode,
                                    opcode == Opcodes.INVOKESPECIAL && owner.equals(oldType) && method.equals("<init>")
                                            ? replacement : owner, method, desc, isInterface);
                        }
                    };
                }
            }, 0);
            return writer.toByteArray();
        }
    }
}
