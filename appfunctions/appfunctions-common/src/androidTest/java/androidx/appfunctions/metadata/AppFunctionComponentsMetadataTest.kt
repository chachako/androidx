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

package androidx.appfunctions.metadata

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AppFunctionComponentsMetadataTest {

    @Test
    fun appFunctionComponentsMetadata_equalsAndHashCode() {
        val dataType1 =
            AppFunctionPrimitiveTypeMetadata(AppFunctionDataTypeMetadata.TYPE_INT, false)
        val dataType2 =
            AppFunctionPrimitiveTypeMetadata(AppFunctionDataTypeMetadata.TYPE_STRING, true)

        val components1 = AppFunctionComponentsMetadata(listOf(dataType1))
        val components2 = AppFunctionComponentsMetadata(listOf(dataType1))
        val components3 = AppFunctionComponentsMetadata(listOf(dataType2))
        val components4 = AppFunctionComponentsMetadata(listOf(dataType1, dataType2))

        assertThat(components1).isEqualTo(components2)
        assertThat(components1.hashCode()).isEqualTo(components2.hashCode())

        assertThat(components1).isNotEqualTo(components3)
        assertThat(components1.hashCode()).isNotEqualTo(components3.hashCode())

        assertThat(components1).isNotEqualTo(components4)
        assertThat(components1.hashCode()).isNotEqualTo(components4.hashCode())
    }

    @Test
    fun toAppFunctionComponentsMetadataDocument_returnsCorrectDocument() {
        val primitiveType1 =
            AppFunctionPrimitiveTypeMetadata(AppFunctionDataTypeMetadata.TYPE_INT, false)
        val primitiveType2 =
            AppFunctionPrimitiveTypeMetadata(AppFunctionDataTypeMetadata.TYPE_STRING, true)
        val components = AppFunctionComponentsMetadata(listOf(primitiveType1, primitiveType2))

        val appFunctionComponentsMetadataDocument =
            components.toAppFunctionComponentsMetadataDocument()

        assertThat(appFunctionComponentsMetadataDocument.dataTypes)
            .containsExactly(primitiveType1, primitiveType2)
    }
}
