/*
 * Copyright 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.testdata

import androidx.appfunctions.AppFunctionSerializable

@AppFunctionSerializable
class EntityWithValidProperties(
    val longParam: Long,
    val doubleParam: Double,
    val boolParam: Boolean,
    val stringParam: String,
    val longArray: LongArray,
    val doubleArray: DoubleArray,
    val boolArray: BooleanArray,
    val stringList: List<String>
)
