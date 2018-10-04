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
package com.indeed.imhotep.client;

import com.indeed.imhotep.DynamicIndexSubshardDirnameUtil;
import org.apache.log4j.Logger;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.Interval;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

/**
 * @author jplaisance
 */
public final class ShardTimeUtils {
    private ShardTimeUtils() {
    }

    private static final Logger log = Logger.getLogger(ShardTimeUtils.class);
    private static final DateTimeZone ZONE = DateTimeZone.forOffsetHours(-6);
    private static final String SHARD_PREFIX = "index";
    private static final String DYNAMIC_SHARD_PREFIX = "dindex";
    private static final DateTimeFormatter yyyymmdd =
            DateTimeFormat.forPattern("yyyyMMdd").withZone(ZONE);
    private static final DateTimeFormatter yyyymmddhh =
            DateTimeFormat.forPattern("yyyyMMdd.HH").withZone(ZONE);
    private static final DateTimeFormatter yyyymmddhhmmss =
            DateTimeFormat.forPattern("yyyyMMddHHmmss").withZone(ZONE);

    public static DateTime parseStart(final String shardId) {
        if (shardId.startsWith(DYNAMIC_SHARD_PREFIX)) {
            return DynamicIndexSubshardDirnameUtil.parseStartTimeFromShardId(shardId);
        } else {
            if (shardId.length() > 16) {
                return yyyymmddhh.parseDateTime(shardId.substring(5, 16));
            } else if (shardId.length() > 13) {
                return yyyymmddhh.parseDateTime(shardId.substring(5, 16));
            } else {
                return yyyymmdd.parseDateTime(shardId.substring(5, 13));
            }
        }
    }

    public static String toDailyShardPrefix(final DateTime dateTime) {
        return SHARD_PREFIX + dateTime.toString(yyyymmdd);
    }

    public static String toHourlyShardPrefix(final DateTime dateTime) {
        return SHARD_PREFIX + dateTime.toString(yyyymmddhh);
    }

    public static String toTimeRangeShardPrefix(final DateTime start, final DateTime end) {
        return SHARD_PREFIX + start.toString(yyyymmddhh) + "-" + end.toString(yyyymmddhh);
    }

    public static String versionizeShardId(final String shardId) {
        return shardId + "." + DateTime.now().toString(yyyymmddhhmmss);
    }

    public static Interval parseInterval(final String shardId) {
        if (shardId.startsWith(DYNAMIC_SHARD_PREFIX)) {
            return DynamicIndexSubshardDirnameUtil.parseTimeRangeFromShardId(shardId);
        } else {
            if (shardId.length() > 16) {
                final DateTime start = yyyymmddhh.parseDateTime(shardId.substring(5, 16));
                final DateTime end = yyyymmddhh.parseDateTime(shardId.substring(17, 28));
                return new Interval(start, end);
            } else if (shardId.length() > 13) {
                final DateTime start = yyyymmddhh.parseDateTime(shardId.substring(5, 16));
                final DateTime end = start.plusHours(1);
                return new Interval(start, end);
            } else {
                final DateTime start = yyyymmdd.parseDateTime(shardId.substring(5, 13));
                final DateTime end = start.plusDays(1);
                return new Interval(start, end);
            }
        }
    }

    public static boolean isValidShardId(final String shardId) {
        try {
            ShardTimeUtils.parseInterval(shardId);
            return true;
        } catch (final Throwable e) {
            return false;
        }
    }
}
