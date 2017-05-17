package com.indeed.imhotep.shardmaster;

import com.indeed.imhotep.client.Host;
import com.indeed.imhotep.shardmaster.protobuf.AssignedShard;

import java.io.IOException;

/**
 * @author kenh
 */

public interface ShardMaster {
    Iterable<AssignedShard> getAssignments(final Host node) throws IOException;
}