/*
 * Copyright (C) 2018 Indeed Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */
 package com.indeed.imhotep.api;

import com.indeed.imhotep.GroupMultiRemapRule;
import com.indeed.imhotep.GroupRemapRule;
import com.indeed.imhotep.ImhotepStatusDump;
import com.indeed.imhotep.QueryRemapRule;
import com.indeed.imhotep.RegroupCondition;
import com.indeed.imhotep.TermCount;
import com.indeed.imhotep.protobuf.HostAndPort;
import com.indeed.imhotep.protobuf.MultiFTGSRequest;
import com.indeed.imhotep.protobuf.ShardNameNumDocsPair;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author jsgroth
 */
public interface ImhotepServiceCore {
    // session-based methods
    int handleRegroup(String sessionId, GroupRemapRule[] remapRules) throws ImhotepOutOfMemoryException;
    int handleRegroup(String sessionId, int numRemapRules, Iterator<GroupRemapRule> remapRules) throws ImhotepOutOfMemoryException;
    int handleQueryRegroup(String sessionId, QueryRemapRule remapRule) throws ImhotepOutOfMemoryException;
    int handleMultisplitRegroup(String sessionId, GroupMultiRemapRule[] remapRules, boolean errorOnCollisions) throws ImhotepOutOfMemoryException;
    int handleMultisplitRegroup(String sessionId, int numRemapRules, Iterator<GroupMultiRemapRule> remapRules, boolean errorOnCollisions) throws ImhotepOutOfMemoryException;
    void handleIntOrRegroup(String sessionId, String field, long[] terms, int targetGroup, int negativeGroup, int positiveGroup) throws ImhotepOutOfMemoryException;
    void handleStringOrRegroup(String sessionId, String field, String[] terms, int targetGroup, int negativeGroup, int positiveGroup) throws ImhotepOutOfMemoryException;
    void handleRandomRegroup(String sessionId, String field, boolean isIntField, String salt, double p, int targetGroup, int negativeGroup, int positiveGroup) throws ImhotepOutOfMemoryException;
    void handleRandomMultiRegroup(String sessionId, String field, boolean isIntField, String salt, int targetGroup, double[] percentages, int[] resultGroups) throws ImhotepOutOfMemoryException;
    void handleRandomMetricRegroup(String sessionId, List<String> stat, String salt, double p, int targetGroup, int negativeGroup, int positiveGroup) throws ImhotepOutOfMemoryException;
    void handleRandomMetricMultiRegroup(String sessionId, List<String> stat, String salt, int targetGroup, double[] percentages, int[] resultGroups) throws ImhotepOutOfMemoryException;
    void handleRegexRegroup(String sessionId, String field, String regex, int targetGroup, int negativeGroup, int positiveGroup) throws ImhotepOutOfMemoryException;
    int handleMetricRegroup(String sessionId, List<String> stat, long min, long max, long intervalSize, boolean noGutters) throws ImhotepOutOfMemoryException;
    int handleRegroup(String sessionId, int[] fromGroups, int[] toGroups, boolean filterOutNotTargeted) throws ImhotepOutOfMemoryException;
    int handleMetricFilter(String sessionId, List<String> stat, long min, long max, boolean negate) throws ImhotepOutOfMemoryException;
    int handleMetricFilter(String sessionId, List<String> stat, long min, long max, int targetGroup, int negativeGroup, int positiveGroup) throws ImhotepOutOfMemoryException;
    List<TermCount> handleApproximateTopTerms(String sessionId, String field, boolean isIntField, int k);
    int handlePushStat(String sessionId, String metric) throws ImhotepOutOfMemoryException;
    int handlePopStat(String sessionId);
    void handleGetFTGSIterator(String sessionId, FTGSParams params, OutputStream os) throws IOException, ImhotepOutOfMemoryException;
    void handleGetSubsetFTGSIterator(String sessionId, Map<String, long[]> intFields, Map<String, String[]> stringFields, final List<List<String>> stats, OutputStream os) throws IOException, ImhotepOutOfMemoryException;
    void handleGetFTGSIteratorSplit(String sessionId, String[] intFields, String[] stringFields, OutputStream os, int splitIndex, int numSplits, long termLimit, final List<List<String>> stats) throws IOException, ImhotepOutOfMemoryException;
    void handleGetSubsetFTGSIteratorSplit(String sessionId, Map<String, long[]> intFields, Map<String, String[]> stringFields, final List<List<String>> stats, OutputStream os, int splitIndex, int numSplits) throws IOException, ImhotepOutOfMemoryException;
    void handleMergeFTGSIteratorSplit(String sessionId, FTGSParams params, OutputStream os, HostAndPort[] nodes, int splitIndex) throws IOException, ImhotepOutOfMemoryException;
    void handleMergeSubsetFTGSIteratorSplit(String sessionId, Map<String, long[]> intFields, Map<String, String[]> stringFields, final List<List<String>> stats, OutputStream os, HostAndPort[] nodes, int splitIndex) throws IOException, ImhotepOutOfMemoryException;
    void handleMergeMultiFTGSSplit(MultiFTGSRequest request, String validLocalSessionId, OutputStream os, HostAndPort[] nodes) throws IOException, ImhotepOutOfMemoryException;
    long handleGetTotalDocFreq(String sessionId, String[] intFields, String[] stringFields);
    GroupStatsIterator handleGetGroupStats(String sessionId, List<String> stat) throws ImhotepOutOfMemoryException;
    GroupStatsIterator handleGetDistinct(String sessionId, String field, boolean isIntField);
    GroupStatsIterator handleMergeDistinctSplit(String sessionId, String field, boolean isIntField, HostAndPort[] nodes, int splitIndex);
    GroupStatsIterator handleMergeMultiDistinctSplit(final MultiFTGSRequest request, final String validLocalSessionId, final HostAndPort[] nodes) throws ImhotepOutOfMemoryException;
    List<String> getShardsForSession(String sessionId);
    boolean sessionIsValid(String sessionId);
    void handleCloseSession(String sessionId);
    void handleCloseSession(String sessionId, Exception e);
    void handleCreateDynamicMetric(String sessionId, String dynamicMetricName) throws ImhotepOutOfMemoryException;
    void handleUpdateDynamicMetric(String sessionId, String dynamicMetricName, int[] deltas) throws ImhotepOutOfMemoryException;
    void handleConditionalUpdateDynamicMetric(String sessionId, String dynamicMetricName, RegroupCondition[] conditions, int[] deltas);
    void handleGroupConditionalUpdateDynamicMetric(String sessionId, String dynamicMetricName, int[] groups, RegroupCondition[] conditions, int[] deltas);
    void handleRebuildAndFilterIndexes(String sessionId, String[] intFields, String[] stringFields) throws ImhotepOutOfMemoryException;
    void handleResetGroups(String sessionId) throws ImhotepOutOfMemoryException;
    int handleGetNumGroups(String sessionId);
    PerformanceStats handleGetPerformanceStats(String sessionId, boolean reset);
    PerformanceStats handleCloseAndGetPerformanceStats(String sessionId);
    // open session methods return session id
    String handleOpenSession(String dataset, List<ShardNameNumDocsPair> shardRequestList, String username, String clientName, String ipAddress, int clientVersion, int mergeThreadLimit, boolean optimizeGroupZeroLookups, String sessionId, AtomicLong tempFileSizeBytesLeft, long sessionTimeout) throws ImhotepOutOfMemoryException;

    // non-session-based methods
    ImhotepStatusDump handleGetStatusDump(boolean includeShardList);

    void close();
}
