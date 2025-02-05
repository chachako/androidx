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

package androidx.tracing.driver

import android.content.Context
import androidx.benchmark.ExperimentalBenchmarkConfigApi
import androidx.benchmark.MicrobenchmarkConfig
import androidx.benchmark.TimeCapture
import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.tracing.Trace
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

internal const val PROCESS_NAME = "process"
internal const val TRACE_TAG = "work"

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalBenchmarkConfigApi::class)
@LargeTest
class TracingOverheadBenchmarkTest {

    val config =
        MicrobenchmarkConfig(
            traceAppTagEnabled = true,
            metrics = listOf(TimeCapture()),
        )

    @get:Rule val benchmarkRule = BenchmarkRule(config)

    private fun buildTraceContext(sink: TraceSink): TraceContext {
        return TraceContext(sequenceId = 1, sink = sink, isEnabled = true)
    }

    @Test
    fun reference() {
        benchmarkRule.measureRepeated {}
    }

    @Test
    fun platformTracing() {
        benchmarkRule.measureRepeated {
            Trace.beginSection(TRACE_TAG)
            Trace.endSection()
        }
    }

    @Test
    fun customTracingNoSink() {
        val traceContext = buildTraceContext(NoOpSink())
        val process = traceContext.ProcessTrack(id = 10, name = PROCESS_NAME)
        traceContext.use { benchmarkRule.measureRepeated { process.trace(TRACE_TAG) {} } }
    }

    @Test
    fun customTracingDisabled() {
        val context = EmptyTraceContext
        val process = context.ProcessTrack(id = 10, name = PROCESS_NAME)
        context.use { benchmarkRule.measureRepeated { process.trace(TRACE_TAG) {} } }
    }

    @Test
    fun customTracingInMemorySink() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val sink = buildInMemorySink(context)
        val traceContext = buildTraceContext(sink)
        traceContext.use {
            val process = traceContext.ProcessTrack(id = 10, name = PROCESS_NAME)
            var count = 0
            benchmarkRule.measureRepeated {
                process.trace(TRACE_TAG) {}
                // The Benchmark measurement loop creates ~250k packets when measuring the cost
                // of this loop. So we essentially give our sink a chance to catch up every 1000
                // packets.
                runWithMeasurementDisabled {
                    count += 1
                    if (count >= 1000) {
                        @SuppressWarnings("BanThreadSleep")
                        Thread.sleep(500L) // Just slow down the producer a bit
                        count = 0
                    }
                }
            }
        }
    }
}
