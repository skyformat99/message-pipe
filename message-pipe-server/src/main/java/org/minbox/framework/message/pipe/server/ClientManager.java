package org.minbox.framework.message.pipe.server;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.minbox.framework.message.pipe.core.ClientStatus;
import org.minbox.framework.message.pipe.core.information.ClientInformation;
import org.minbox.framework.message.pipe.core.exception.MessagePipeException;
import org.springframework.util.ObjectUtils;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * client collection
 * <p>
 * Provides operating methods for client sets stored in memory
 *
 * @author 恒宇少年
 */
public class ClientManager {

    private static final String CLIENT_ID_PATTERN = "%s::%d";
    /**
     * There is a list of all clients
     */
    private static final ConcurrentMap<String, ClientInformation> CLIENTS = new ConcurrentHashMap();
    /**
     * The channel corresponding to each client
     */
    private static final ConcurrentMap<String, ManagedChannel> CLIENT_CHANNEL = new ConcurrentHashMap();
    /**
     * List of clients bound to the message pipeline
     * <p>
     * The key of this set is the name of the message channel bound to the client
     */
    private static final ConcurrentMap<String, Set<String>> PIPE_CLIENTS = new ConcurrentHashMap();

    /**
     * If it does not exist, add it to the client collection
     *
     * @param address client address
     * @param port    client port
     * @return the client id
     */
    public static String putIfNotPresent(String address, int port) {
        String clientId = getClientId(address, port);
        ClientInformation clientInformation = ClientInformation.valueOf(address, port);
        if (!CLIENTS.containsKey(clientId)) {
            CLIENTS.put(clientId, clientInformation);
        }
        return clientId;
    }

    /**
     * get formatted clientId
     *
     * @param address the client address
     * @param port    the client port
     * @return clientId
     */
    public static String getClientId(String address, int port) {
        return String.format(CLIENT_ID_PATTERN, address, port);
    }

    /**
     * Check if the client already exists
     *
     * @param clientId client id
     * @return Return "true" means it already exists
     */
    public static boolean containsClient(String clientId) {
        return CLIENTS.containsKey(clientId);
    }

    /**
     * Get client information from {@link #CLIENTS}
     *
     * @param clientId client id
     * @return The client information {@link ClientInformation}
     */
    public static ClientInformation getClient(String clientId) {
        return CLIENTS.get(clientId);
    }

    /**
     * Get all client from {@link #CLIENTS}
     *
     * @return all clients {@link ClientInformation}
     */
    public static List<ClientInformation> getAllClient() {
        return CLIENTS.values().stream().collect(Collectors.toList());
    }

    /**
     * Update client information
     *
     * @param information The {@link ClientInformation} instance
     */
    public static void updateClientInformation(ClientInformation information) {
        String clientId = getClientId(information.getAddress(), information.getPort());
        CLIENTS.put(clientId, information);
    }

    /**
     * Bind client to message pipe {@link #PIPE_CLIENTS}
     *
     * @param pipeName message pipe name
     * @param clientId client id
     */
    public static void bindClientToPipe(String pipeName, String clientId) {
        Set<String> pipeClients = Optional.ofNullable(PIPE_CLIENTS.get(pipeName)).orElse(new HashSet<>());
        pipeClients.add(clientId);
        PIPE_CLIENTS.put(pipeName, pipeClients);
    }

    /**
     * Get message pipe bind clients information {@link ClientInformation}
     *
     * @param pipeName message pipe name
     * @return The message pipe bind clients
     */
    public static List<ClientInformation> getPipeBindOnLineClients(String pipeName) {
        List<ClientInformation> clientInformationList = new ArrayList<>();
        Set<String> clientIds = regexGetClientIds(pipeName);
        if (!ObjectUtils.isEmpty(clientIds)) {
            clientIds.stream().forEach(clientId -> {
                ClientInformation client = CLIENTS.get(clientId);
                if (ClientStatus.ON_LINE == client.getStatus()) {
                    clientInformationList.add(client);
                }
            });
        }
        return clientInformationList;
    }

    /**
     * Use regular expressions to obtain ClientIds
     *
     * @param pipeName The {@link MessagePipe} specific name
     * @return The {@link MessagePipe} binding clientIds
     */
    private static Set<String> regexGetClientIds(String pipeName) {
        Iterator<String> iterator = PIPE_CLIENTS.keySet().iterator();
        while (iterator.hasNext()) {
            // PipeName when the client is registered，May be a regular expression
            String pipeNamePattern = iterator.next();
            boolean isMatch = Pattern.compile(pipeNamePattern).matcher(pipeName).matches();
            if (isMatch) {
                return PIPE_CLIENTS.get(pipeNamePattern);
            }
        }
        return null;
    }

    /**
     * Establish a client channel
     *
     * @param clientId The {@link ClientInformation} id
     * @return {@link ManagedChannel} instance
     */
    public static synchronized ManagedChannel establishChannel(String clientId) {
        ClientInformation information = CLIENTS.get(clientId);
        if (ObjectUtils.isEmpty(information)) {
            throw new MessagePipeException("Client: " + clientId + " is not registered");
        }
        ManagedChannel channel = CLIENT_CHANNEL.get(clientId);
        if (ObjectUtils.isEmpty(channel) || channel.isShutdown()) {
            channel = ManagedChannelBuilder.forAddress(information.getAddress(), information.getPort())
                    .usePlaintext()
                    .build();
            CLIENT_CHANNEL.put(clientId, channel);
        }
        return channel;
    }

    /**
     * Remove client {@link ManagedChannel}
     *
     * @param clientId The client id
     */
    public static void removeChannel(String clientId) {
        CLIENT_CHANNEL.remove(clientId);
    }
}
