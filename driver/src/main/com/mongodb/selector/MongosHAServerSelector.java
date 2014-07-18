/*
 * Copyright (c) 2008-2014 MongoDB, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.mongodb.selector;

import com.mongodb.ServerAddress;
import com.mongodb.connection.ClusterDescription;
import com.mongodb.connection.ServerDescription;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.mongodb.connection.ClusterConnectionMode.MULTIPLE;
import static com.mongodb.connection.ClusterType.SHARDED;

/**
 * A server selector that "sticks" to the fastest mongos in the cluster, so long as it's still available.
 *
 * @since 3.0
 */
public class MongosHAServerSelector implements ServerSelector {
    private ServerAddress stickTo;
    private Set<ServerAddress> consideredServers = new HashSet<ServerAddress>();

    @Override
    public List<ServerDescription> select(final ClusterDescription clusterDescription) {
        if (clusterDescription.getConnectionMode() != MULTIPLE || clusterDescription.getType() != SHARDED) {
            return clusterDescription.getAny();
        }

        Set<ServerAddress> okServers = getOkServers(clusterDescription);

        synchronized (this) {
            if (!consideredServers.containsAll(okServers) || !okServers.contains(stickTo)) {
                if (stickTo != null && !okServers.contains(stickTo)) {
                    stickTo = null;
                    consideredServers.clear();
                }
                ServerDescription fastestServer = null;
                for (ServerDescription cur : clusterDescription.getAny()) {
                    if (fastestServer == null || cur.getRoundTripTimeNanos() < fastestServer.getRoundTripTimeNanos()) {
                        fastestServer = cur;
                    }
                }
                if (fastestServer != null) {
                    stickTo = fastestServer.getAddress();
                    consideredServers.addAll(okServers);
                }
            }
            if (stickTo == null) {
                return Collections.emptyList();
            } else {
                return Arrays.asList(clusterDescription.getByServerAddress(stickTo));
            }
        }
    }

    @Override
    public String toString() {
        return "MongosHAServerSelector{"
               + (stickTo == null ? "" : "stickTo=" + stickTo)
               + '}';
    }

    private Set<ServerAddress> getOkServers(final ClusterDescription clusterDescription) {
        Set<ServerAddress> okServers = new HashSet<ServerAddress>();
        for (ServerDescription cur : clusterDescription.getAny()) {
            okServers.add(cur.getAddress());
        }
        return okServers;
    }
}
