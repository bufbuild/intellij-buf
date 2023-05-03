package build.buf.intellij.icons

import com.intellij.openapi.util.IconLoader
import com.intellij.ui.AnimatedIcon
import com.intellij.util.IconUtil

object BufIcons {
    @JvmField
    val Logo = IconLoader.getIcon("/icons/logo.png", javaClass)

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
