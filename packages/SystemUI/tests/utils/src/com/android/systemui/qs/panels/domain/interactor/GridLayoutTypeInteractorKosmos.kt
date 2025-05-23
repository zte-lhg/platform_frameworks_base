/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.systemui.qs.panels.domain.interactor

import com.android.systemui.kosmos.Kosmos
import com.android.systemui.qs.panels.data.repository.gridLayoutTypeRepository
import com.android.systemui.qs.panels.shared.model.GridLayoutType
import com.android.systemui.qs.panels.shared.model.InfiniteGridLayoutType
import com.android.systemui.qs.panels.shared.model.PaginatedGridLayoutType
import com.android.systemui.qs.panels.ui.compose.GridLayout
import com.android.systemui.qs.panels.ui.compose.infinitegrid.infiniteGridLayout
import com.android.systemui.qs.panels.ui.compose.paginatedGridLayout
import com.android.systemui.shade.domain.interactor.shadeModeInteractor

val Kosmos.gridLayoutTypeInteractor by
    Kosmos.Fixture { GridLayoutTypeInteractor(gridLayoutTypeRepository, shadeModeInteractor) }

val Kosmos.gridLayoutMap: Map<GridLayoutType, GridLayout> by
    Kosmos.Fixture {
        mapOf(
            Pair(InfiniteGridLayoutType, infiniteGridLayout),
            Pair(PaginatedGridLayoutType, paginatedGridLayout),
        )
    }
