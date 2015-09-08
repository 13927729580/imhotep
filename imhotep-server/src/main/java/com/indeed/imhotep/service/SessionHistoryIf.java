/*
 * Copyright (C) 2015 Indeed Inc.
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

package com.indeed.imhotep.service;

import com.indeed.flamdex.api.FlamdexReader;
import com.indeed.flamdex.api.IntValueLookup;

/**
   I accumulate information about the use of an ImhotepSession in order to log
   it when the session is closed.

   More precisely, I capture history of a group of local sessions associated
   with a particular session id, managed by a SessionManager.
 */
public interface SessionHistoryIf {

    String getSessionId();

    void onCreate(FlamdexReader reader);

    void onGetFTGSIterator(String[] intFields, String[] stringFields);

    void onWriteFTGSIteratorSplit(String[] intFields, String[] stringFields);

    void onPushStat(String stat, IntValueLookup lookup);

    FlamdexReader getFlamdexReader(FlamdexReader reader);
}
