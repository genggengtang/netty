/**
 * Copyright 2012 Nikita Koksharov
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.corundumstudio.socketio.transport;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.util.concurrent.Future;

import java.net.SocketAddress;
import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;

import com.corundumstudio.socketio.DisconnectableHub;
import com.corundumstudio.socketio.HandshakeData;
import com.corundumstudio.socketio.SocketIOClient;
import com.corundumstudio.socketio.Transport;
import com.corundumstudio.socketio.ack.AckManager;
import com.corundumstudio.socketio.namespace.Namespace;
import com.corundumstudio.socketio.parser.Packet;
import com.corundumstudio.socketio.parser.PacketType;
import com.corundumstudio.socketio.store.Store;
import com.corundumstudio.socketio.store.StoreFactory;

/**
 * Base class for main client.
 *
 * Each main client can have multiple namespace clients, when all namespace
 * clients has disconnected then main client disconnects too.
 *
 *
 */
public abstract class MainBaseClient {

    private final ConcurrentMap<Namespace, SocketIOClient> namespaceClients = new ConcurrentHashMap<Namespace, SocketIOClient>();
    private final Store store;

    private final AtomicBoolean disconnected = new AtomicBoolean();
    private final DisconnectableHub disconnectableHub;
    private final AckManager ackManager;
    private final UUID sessionId;
    private final Transport transport;
    private Channel channel;
    private final HandshakeData handshakeData;

    public MainBaseClient(UUID sessionId, AckManager ackManager, DisconnectableHub disconnectable,
            Transport transport, StoreFactory storeFactory, HandshakeData handshakeData) {
        this.sessionId = sessionId;
        this.ackManager = ackManager;
        this.disconnectableHub = disconnectable;
        this.transport = transport;
        this.store = storeFactory.createStore(sessionId);
        this.handshakeData = handshakeData;
    }

    public Transport getTransport() {
        return transport;
    }

    public abstract Future send(Packet packet);

    public void removeChildClient(SocketIOClient client) {
        namespaceClients.remove((Namespace) client.getNamespace());
        if (namespaceClients.isEmpty()) {
            disconnectableHub.onDisconnect(this);
        }
    }

    public SocketIOClient getChildClient(Namespace namespace) {
        return namespaceClients.get(namespace);
    }

    public SocketIOClient addChildClient(Namespace namespace) {
        SocketIOClient client = new NamespaceClient(this, namespace);
        namespaceClients.put(namespace, client);
        return client;
    }

    public Collection<SocketIOClient> getAllChildClients() {
        return namespaceClients.values();
    }

    public boolean isConnected() {
        return !disconnected.get();
    }

    public void onChannelDisconnect() {
        disconnected.set(true);
        for (SocketIOClient client : getAllChildClients()) {
            ((NamespaceClient) client).onDisconnect();
        }
    }

    public HandshakeData getHandshakeData() {
        return handshakeData;
    }

    public AckManager getAckManager() {
        return ackManager;
    }

    public UUID getSessionId() {
        return sessionId;
    }

    public SocketAddress getRemoteAddress() {
        return channel.remoteAddress();
    }

    public void disconnect() {
        Future future = send(new Packet(PacketType.DISCONNECT));
        future.addListener(ChannelFutureListener.CLOSE);

        onChannelDisconnect();
    }

    Channel getChannel() {
        return channel;
    }

    void setChannel(Channel channel) {
        this.channel = channel;
    }

    public Store getStore() {
        return store;
    }

}
