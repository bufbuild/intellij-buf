// Copyright 2022-2025 Buf Technologies, Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package build.buf.intellij.icons

import com.intellij.openapi.util.IconLoader
import com.intellij.ui.AnimatedIcon
import com.intellij.util.IconUtil

object BufIcons {
    @JvmField
    val Logo = IconLoader.getIcon("/icons/logo.svg", javaClass)

    @JvmField
    val LogoGrayscale = IconUtil.desaturate(Logo)

    @JvmField
    val LogoAnimated = AnimatedIcon(
        AnimatedIcon.Default.DELAY,
        IconUtil.brighter(Logo, 3),
        IconUtil.brighter(Logo, 2),
        IconUtil.brighter(Logo, 1),
        Logo,
        IconUtil.darker(Logo, 1),
        IconUtil.darker(Logo, 2),
        IconUtil.darker(Logo, 3),
        IconUtil.darker(Logo, 2),
        IconUtil.darker(Logo, 1),
        Logo,
        IconUtil.brighter(Logo, 1),
        IconUtil.brighter(Logo, 2),
    )
}
