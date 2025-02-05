package com.testdata

import androidx.appfunctions.AppFunction
import androidx.appfunctions.AppFunctionContext

class FakeAllPrimitiveReturnsImpl : FakeAllPrimitiveReturns {

    @AppFunction override fun unitReturn(appFunctionContext: AppFunctionContext) {}

    @AppFunction
    override fun intReturn(appFunctionContext: AppFunctionContext): Int {
        return 0
    }

    @AppFunction
    override fun longReturn(appFunctionContext: AppFunctionContext): Long {
        return 0L
    }

    @AppFunction
    override fun floatReturn(appFunctionContext: AppFunctionContext): Float {
        return 0.0f
    }

    @AppFunction
    override fun doubleReturn(appFunctionContext: AppFunctionContext): Double {
        return 0.0
    }

    @AppFunction
    override fun stringReturn(appFunctionContext: AppFunctionContext): String {
        return ""
    }

    @AppFunction
    override fun booleanReturn(appFunctionContext: AppFunctionContext): Boolean {
        return false
    }

    @AppFunction
    override fun nullableIntReturn(appFunctionContext: AppFunctionContext): Int? {
        return null
    }

    @AppFunction
    override fun nullableLongReturn(appFunctionContext: AppFunctionContext): Long? {
        return null
    }

    @AppFunction
    override fun nullableFloatReturn(appFunctionContext: AppFunctionContext): Float? {
        return null
    }

    @AppFunction
    override fun nullableDoubleReturn(appFunctionContext: AppFunctionContext): Double? {
        return null
    }

    @AppFunction
    override fun nullableStringReturn(appFunctionContext: AppFunctionContext): String? {
        return null
    }

    @AppFunction
    override fun nullableBooleanReturn(appFunctionContext: AppFunctionContext): Boolean? {
        return null
    }
}
