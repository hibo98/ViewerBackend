/*
 * The MIT License
 *
 * Copyright 2019 Niklas Merkelt.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package de.freifunkdresden.viewerbackend.stats;

import de.freifunkdresden.viewerbackend.Community;
import de.freifunkdresden.viewerbackend.DataGen;
import de.freifunkdresden.viewerbackend.Node;
import de.freifunkdresden.viewerbackend.VPN;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.influxdb.dto.Point;

public class StatsSQL {

    private static final Map<GeneralStatType, Double> generalStats = new EnumMap<>(GeneralStatType.class);
    private static final Set<Node> nodes = Collections.synchronizedSet(new LinkedHashSet<>());
    private static final Map<String, Integer> versions = new LinkedHashMap<>();
    private static final Map<String, Integer> communities = new LinkedHashMap<>();
    private static final Map<VPN, Integer> vpnUsage = new EnumMap<>(VPN.class);
    private static final Map<Node, Integer> gatewayUsage = new LinkedHashMap<>();
    private static final Map<Node, Integer> gatewayClients = new LinkedHashMap<>();

    public static void addToStats(Node n) {
        nodes.add(n);
        addVersion(n.getFirmwareVersion());
        addCommunity(n.getCommunity());
        addGatewayUsage(n.getGateway());
        addGatewayUsageClients(n.getGateway(), n.getClients());
    }

    public static void addGeneralStats(GeneralStatType type, double value) {
        generalStats.put(type, value);
    }

    public static void addVersion(String version) {
        if (!version.isEmpty()) {
            synchronized (versions) {
                versions.put(version, versions.getOrDefault(version, 0) + 1);
            }
        }
    }

    public static void addCommunity(Community c) {
        synchronized (communities) {
            String c_name = c.getName();
            communities.put(c_name, communities.getOrDefault(c_name, 0) + 1);
        }
    }

    public static void addVpnUsage(VPN vpn, int usage) {
        vpnUsage.put(vpn, usage);
    }

    public static void addGatewayUsage(Node gw) {
        if (gw == null || gw.getId() < 0) {
            return;
        }
        synchronized (gatewayUsage) {
            gatewayUsage.put(gw, gatewayUsage.getOrDefault(gw, 0) + 1);
        }
    }

    public static void addGatewayUsageClients(Node gw, int cl) {
        if (gw.getId() < 0) {
            return;
        }
        synchronized (gatewayClients) {
            gatewayClients.put(gw, gatewayClients.getOrDefault(gw, 0) + cl);
        }
    }

    public static void processStats() {
        List<Point> general = new ArrayList<>();
        generalStats.forEach((key, value) -> {
            general.add(Point.measurement(key.name().toLowerCase())
                    .addField("value", value)
                    .build());
        });
        DataGen.getInflux().write(general);
        List<Point> vpn = new ArrayList<>();
        vpnUsage.forEach((key, value) -> {
            vpn.add(Point.measurement("vpn_usage")
                    .tag("vpn", key.getVpnId())
                    .addField("usage", value)
                    .build());
        });
        DataGen.getInflux().write(vpn);
        List<Point> node_clients = new ArrayList<>();
        List<Point> node_load = new ArrayList<>();
        List<Point> node_memory = new ArrayList<>();
        nodes.forEach((e) -> {
            node_clients.add(Point.measurement("node_clients")
                    .tag("node", String.valueOf(e.getId()))
                    .addField("value", e.getClients())
                    .build());
            node_load.add(Point.measurement("node_load")
                    .tag("node", String.valueOf(e.getId()))
                    .addField("value", e.getLoadAvg())
                    .build());
            node_memory.add(Point.measurement("node_memory")
                    .tag("node", String.valueOf(e.getId()))
                    .addField("value", e.getMemoryUsage())
                    .build());
        });
        DataGen.getInflux().write(node_clients);
        DataGen.getInflux().write(node_load);
        DataGen.getInflux().write(node_memory);
        List<Point> nodes_versions = new ArrayList<>();
        versions.forEach((v, c) -> {
            nodes_versions.add(Point.measurement("nodes_versions")
                    .tag("version", v)
                    .addField("value", c)
                    .build());
        });
        DataGen.getInflux().write(nodes_versions);
        List<Point> nodes_communities = new ArrayList<>();
        communities.forEach((c, v) -> {
            nodes_communities.add(Point.measurement("nodes_communities")
                    .tag("community", c)
                    .addField("value", v)
                    .build());
        });
        DataGen.getInflux().write(nodes_communities);
        // gateway usage
        List<Point> nodes_gateway = new ArrayList<>();
        gatewayUsage.forEach((gw, v) -> {
            nodes_gateway.add(Point.measurement("nodes_gateway")
                    .tag("gateway", String.valueOf(gw.getId()))
                    .addField("value", v)
                    .build());
        });
        DataGen.getInflux().write(nodes_gateway);
        // gateway usage clients
        List<Point> nodes_gateway_clients = new ArrayList<>();
        gatewayClients.forEach((gw, v) -> {
            nodes_gateway_clients.add(Point.measurement("nodes_gateway_clients")
                    .tag("gateway", String.valueOf(gw.getId()))
                    .addField("value", v)
                    .build());
        });
        DataGen.getInflux().write(nodes_gateway_clients);
    }
}
