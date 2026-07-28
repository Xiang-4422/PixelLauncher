package com.purride.pixelui

import kotlin.math.roundToInt

/** 定义 `PixelIntegerFormatter` 在 `PixelLocalizations` 中的可替换调用契约。
 *
 * Formats a signed integer without exposing a platform locale type in public API.
 */
public fun interface PixelIntegerFormatter {
    /** 执行 `PixelLocalizations` 的 `format` 公开行为；具体参数、返回和副作用见下文。
 *
 * Returns the complete localized representation of [value].
 */
    public fun format(value: Long): String

    /** 集中提供 `PixelLocalizations` 的 `<companion>` 共享入口。
 *
 * Provides the deterministic formatter shared by built-in bundles.
 */
    public companion object {
        /** 公开 `PixelLocalizations` 的 `Default` 配置或运行值。
 *
 * Locale-neutral decimal digits without grouping separators.
 */
        @JvmField
        public val Default: PixelIntegerFormatter = PixelIntegerFormatter { value -> value.toString() }
    }
}

/** 定义 `PixelPercentFormatter` 在 `PixelLocalizations` 中的可替换调用契约。
 *
 * Formats a normalized fraction as localized percentage text.
 */
public fun interface PixelPercentFormatter {
    /** 执行 `PixelLocalizations` 的 `format` 公开行为；具体参数、返回和副作用见下文。
 *
 * Returns percentage text for a finite [fraction] in the inclusive `0..1` range.
 */
    public fun format(fraction: Float): String

    /** 集中提供 `PixelLocalizations` 的 `<companion>` 共享入口。
 *
 * Provides the deterministic formatter shared by built-in bundles.
 */
    public companion object {
        /** 公开 `PixelLocalizations` 的 `Default` 配置或运行值。
 *
 * Whole-percent formatter using deterministic half-up rounding and an ASCII percent sign.
 */
        @JvmField
        public val Default: PixelPercentFormatter = PixelPercentFormatter { fraction ->
            "${(fraction * 100f).roundToInt()}%"
        }
    }
}

/**
 * 定义 `PixelLocalizationBundle` 在 `PixelLocalizations` 中承担的数据与行为边界。
 *
 * Immutable localized strings and numeric formatters for one exact [locale].
 *
 * Existing standard component names remain in [PixelLabelTokens], preserving that data class's
 * 构造器与 copy 契约。导航容器名称和数字格式化同样定义在这里。
 * Every static label and every formatter result must be non-blank.
 *
 * @property locale Exact locale key represented by this bundle.
 * @property labels Localized standard component and state labels.
 * @property navigationBar Localized accessibility name for a bottom navigation bar.
 * @property navigationRail Localized accessibility name for a navigation rail.
 * @param integerFormatter Consumer-overridable signed integer formatter.
 * @param percentFormatter Consumer-overridable normalized percentage formatter.
 * @throws IllegalArgumentException when either navigation label is blank.
 */
public class PixelLocalizationBundle @JvmOverloads constructor(
    public val locale: PixelLocale,
    public val labels: PixelLabelTokens,
    public val navigationBar: String,
    public val navigationRail: String,
    integerFormatter: PixelIntegerFormatter = PixelIntegerFormatter.Default,
    percentFormatter: PixelPercentFormatter = PixelPercentFormatter.Default,
) {
    /** Formatter retained by this immutable bundle for signed integer output. */
    private val retainedIntegerFormatter: PixelIntegerFormatter = integerFormatter

    /** Formatter retained by this immutable bundle for normalized percentage output. */
    private val retainedPercentFormatter: PixelPercentFormatter = percentFormatter

    init {
        require(navigationBar.isNotBlank()) {
            "PixelLocalizationBundle.navigationBar must not be blank"
        }
        require(navigationRail.isNotBlank()) {
            "PixelLocalizationBundle.navigationRail must not be blank"
        }
    }

    /** 执行 `PixelLocalizations` 的 `formatInteger` 公开行为；具体参数、返回和副作用见下文。
 *
 * Formats an [Int] through the same complete signed-integer contract as the Long overload.
 */
    public fun formatInteger(value: Int): String = formatInteger(value.toLong())

    /**
 * 执行 `PixelLocalizations` 的 `formatInteger` 公开行为；具体参数、返回和副作用见下文。
 *
     * Formats a signed [Long] and rejects a blank result from a consumer formatter.
     *
     * @throws IllegalArgumentException when the installed formatter returns null or blank text.
     */
    public fun formatInteger(value: Long): String {
        /** Nullable view protects Kotlin callers from a Java formatter returning an invalid null. */
        val formatted: String? = retainedIntegerFormatter.format(value)
        require(!formatted.isNullOrBlank()) {
            "PixelLocalizationBundle integer formatter must return non-blank text"
        }
        return formatted
    }

    /**
 * 执行 `PixelLocalizations` 的 `formatPercent` 公开行为；具体参数、返回和副作用见下文。
 *
     * Formats a normalized [fraction] and rejects invalid input or a blank formatter result.
     *
     * @throws IllegalArgumentException when [fraction] is outside `0..1`, is not finite, or the
     * installed formatter returns null or blank text.
     */
    public fun formatPercent(fraction: Float): String {
        require(fraction.isFinite() && fraction in 0f..1f) {
            "PixelLocalizationBundle percent fraction must be finite and in 0..1, got $fraction"
        }
        /** Nullable view protects Kotlin callers from a Java formatter returning an invalid null. */
        val formatted: String? = retainedPercentFormatter.format(fraction)
        require(!formatted.isNullOrBlank()) {
            "PixelLocalizationBundle percent formatter must return non-blank text"
        }
        return formatted
    }

    /** Compares localized values and the installed formatter contracts. */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PixelLocalizationBundle) return false
        return locale == other.locale &&
            labels == other.labels &&
            navigationBar == other.navigationBar &&
            navigationRail == other.navigationRail &&
            retainedIntegerFormatter == other.retainedIntegerFormatter &&
            retainedPercentFormatter == other.retainedPercentFormatter
    }

    /** Returns a hash covering every value observed by [equals]. */
    override fun hashCode(): Int {
        var result = locale.hashCode()
        result = 31 * result + labels.hashCode()
        result = 31 * result + navigationBar.hashCode()
        result = 31 * result + navigationRail.hashCode()
        result = 31 * result + retainedIntegerFormatter.hashCode()
        result = 31 * result + retainedPercentFormatter.hashCode()
        return result
    }

    /** Returns stable localized values while intentionally omitting formatter implementation names. */
    override fun toString(): String {
        return "PixelLocalizationBundle(" +
            "locale=$locale, " +
            "labels=$labels, " +
            "navigationBar=$navigationBar, " +
            "navigationRail=$navigationRail)"
    }

    /** 集中提供 `PixelLocalizations` 的 `<companion>` 共享入口。
 *
 * Provides immutable English and Chinese bundles without installing an inherited provider.
 */
    public companion object {
        /** 公开 `PixelLocalizations` 的 `English` 配置或运行值。
 *
 * Built-in English bundle and terminal fallback for every resolver.
 */
        @JvmField
        public val English: PixelLocalizationBundle = PixelLocalizationBundle(
            locale = PixelLocale.English,
            labels = PixelLabelTokens.Default,
            navigationBar = "Navigation bar",
            navigationRail = "Navigation rail",
        )

        /** 公开 `PixelLocalizations` 的 `Chinese` 配置或运行值。
 *
 * Built-in language-level Chinese bundle using simplified Chinese component labels.
 */
        @JvmField
        public val Chinese: PixelLocalizationBundle = PixelLocalizationBundle(
            locale = PixelLocale("zh"),
            labels = PixelLabelTokens(
                confirm = "确定",
                cancel = "取消",
                dismiss = "关闭",
                empty = "暂无内容",
                error = "错误",
                loading = "加载中",
                button = "按钮",
                textButton = "文本按钮",
                textField = "文本框",
                listTile = "列表项",
                checkbox = "复选框",
                switch = "开关",
                slider = "滑块",
                tabs = "标签页",
                segmentedControl = "分段控件",
                valueAdjuster = "数值调节器",
                decrease = "减少",
                increase = "增加",
                menu = "菜单",
                dropdown = "下拉菜单",
                dialog = "对话框",
                bottomSheet = "底部面板",
                toast = "提示",
                snackbar = "消息提示",
                tooltip = "工具提示",
                progress = "进度",
                refresh = "刷新",
                scrollbar = "滚动条",
                slidable = "滑动操作",
            ),
            navigationBar = "导航栏",
            navigationRail = "导航侧栏",
        )

        /** 公开 `PixelLocalizations` 的 `Default` 配置或运行值。
 *
 * Default bundle equals [English] but does not implicitly install [PixelLocalizations].
 */
        @JvmField
        public val Default: PixelLocalizationBundle = English
    }
}

/**
 * 定义 `PixelLocalizationDelegate` 在 `PixelLocalizations` 中的可替换调用契约。
 *
 * Synchronous exact-locale lookup supplied to [PixelLocalizationResolver].
 *
 * A delegate must return a bundle only when [PixelLocalizationBundle.locale] equals the requested
 * locale. Language and default fallbacks belong to the resolver, keeping delegate behavior exact.
 */
public fun interface PixelLocalizationDelegate {
    /** 执行 `PixelLocalizations` 的 `load` 公开行为；具体参数、返回和副作用见下文。
 *
 * Returns an exact bundle for [locale], or null when this delegate does not support it.
 */
    public fun load(locale: PixelLocale): PixelLocalizationBundle?

    /** 集中提供 `PixelLocalizations` 的 `<companion>` 共享入口。
 *
 * Creates exact immutable map delegates and exposes the built-in English/Chinese delegate.
 */
    public companion object {
        /** 公开 `PixelLocalizations` 的 `BuiltIn` 配置或运行值。
 *
 * Exact delegate containing the built-in English and Chinese bundles.
 */
        @JvmField
        public val BuiltIn: PixelLocalizationDelegate = BundleMapLocalizationDelegate(
            bundles = listOf(PixelLocalizationBundle.English, PixelLocalizationBundle.Chinese),
        )

        /**
 * 创建或解析 `PixelLocalizations` 的 `fromBundles` 结果，并在返回前校验输入。
 *
         * Creates an exact delegate from [bundles] after rejecting duplicate canonical locales.
         *
         * The list is defensively copied into private immutable lookup storage.
         */
        @JvmStatic
        public fun fromBundles(bundles: List<PixelLocalizationBundle>): PixelLocalizationDelegate {
            return BundleMapLocalizationDelegate(bundles)
        }

        /** 创建或解析 `PixelLocalizations` 的 `fromBundles` 结果，并在返回前校验输入。
 *
 * Java- and Kotlin-friendly vararg overload of [fromBundles].
 */
        @JvmStatic
        public fun fromBundles(vararg bundles: PixelLocalizationBundle): PixelLocalizationDelegate {
            return fromBundles(bundles.toList())
        }
    }
}

/** Exact private delegate backed by an immutable canonical-locale map. */
private class BundleMapLocalizationDelegate(
    bundles: List<PixelLocalizationBundle>,
) : PixelLocalizationDelegate {
    /** Owned exact-locale lookup after duplicate canonical keys have been rejected. */
    private val bundlesByLocale: Map<PixelLocale, PixelLocalizationBundle>

    init {
        /** Canonical locale keys used to reject ambiguous consumer registrations. */
        val locales = bundles.map(PixelLocalizationBundle::locale)
        require(locales.distinct().size == locales.size) {
            "PixelLocalizationDelegate bundles must use unique canonical locales"
        }
        bundlesByLocale = LinkedHashMap<PixelLocale, PixelLocalizationBundle>().apply {
            bundles.forEach { bundle -> put(bundle.locale, bundle) }
        }
    }

    /** Returns only the bundle registered for the exact canonical locale. */
    override fun load(locale: PixelLocale): PixelLocalizationBundle? = bundlesByLocale[locale]
}

/**
 * 定义 `PixelLocalizationResolver` 在 `PixelLocalizations` 中承担的数据与行为边界。
 *
 * Deterministically resolves locale bundles and component text precedence.
 *
 * For each requested locale, bundle lookup tries its exact canonical tag and then its language
 * subtag. It next tries the configured default locale and its language, then English. A custom
 * [delegate] wins over built-ins for every exact candidate; built-in English remains terminal.
 * 构造该 resolver 本身不会安装 [PixelLocalizations]。
 *
 * @property delegate Optional consumer exact-locale delegate evaluated before built-ins.
 * @property defaultLocale Configured fallback tried after requested exact/language candidates.
 */
public class PixelLocalizationResolver @JvmOverloads constructor(
    public val delegate: PixelLocalizationDelegate? = null,
    public val defaultLocale: PixelLocale = PixelLocale.English,
) {
    /** 查询 `PixelLocalizations` 的 `resolveBundle` 结果，不产生额外状态变更。
 *
 * Resolves one requested locale through exact, language, default, then English candidates.
 */
    public fun resolveBundle(requestedLocale: PixelLocale): PixelLocalizationBundle {
        return resolveBundle(listOf(requestedLocale))
    }

    /**
 * 查询 `PixelLocalizations` 的 `resolveBundle` 结果，不产生额外状态变更。
 *
     * Resolves ordered locale preferences without exposing a platform LocaleList type.
     *
     * An empty list starts directly at the configured default. For a non-empty list, each locale's
     * exact and language candidates are tried before advancing to the next preference.
     */
    public fun resolveBundle(requestedLocales: List<PixelLocale>): PixelLocalizationBundle {
        /** Insertion-ordered set prevents repeated delegate calls for equivalent fallback keys. */
        val candidates = linkedSetOf<PixelLocale>()
        requestedLocales.forEach { locale ->
            candidates += locale
            locale.languageLocaleOrNull()?.let(candidates::add)
        }
        candidates += defaultLocale
        defaultLocale.languageLocaleOrNull()?.let(candidates::add)
        candidates += PixelLocale.English

        candidates.forEach { candidate ->
            loadExactCandidate(candidate)?.let { bundle -> return bundle }
        }
        return PixelLocalizationBundle.English
    }

    /** Loads one exact custom bundle first, validates its key, then consults built-ins. */
    private fun loadExactCandidate(locale: PixelLocale): PixelLocalizationBundle? {
        /** Consumer result retained separately so an incoherent locale key fails immediately. */
        val customBundle = delegate?.load(locale)
        if (customBundle != null) {
            require(customBundle.locale == locale) {
                "PixelLocalizationDelegate returned ${customBundle.locale} for exact request $locale"
            }
            return customBundle
        }
        return PixelLocalizationDelegate.BuiltIn.load(locale)
    }

    /** 集中提供 `PixelLocalizations` 的 `<companion>` 共享入口。
 *
 * Provides a shared resolver and the component-facing text precedence helper.
 */
    public companion object {
        /** 公开 `PixelLocalizations` 的 `Default` 配置或运行值。
 *
 * Built-in resolver with English configured as the default locale.
 */
        @JvmField
        public val Default: PixelLocalizationResolver = PixelLocalizationResolver()

        /**
 * 查询 `PixelLocalizations` 的 `resolveText` 结果，不产生额外状态变更。
 *
         * Resolves user-visible text in the fixed order explicit → provider → theme → English.
         *
         * Null means a layer is absent. A present blank value is rejected rather than silently
         * changing precedence or removing an accessibility name.
         *
         * @throws IllegalArgumentException when any present candidate is blank.
         */
        @JvmStatic
        public fun resolveText(
            explicitText: String?,
            providerText: String?,
            themeText: String?,
            englishFallback: String,
        ): String {
            requireNonBlankWhenPresent(name = "explicitText", value = explicitText)
            requireNonBlankWhenPresent(name = "providerText", value = providerText)
            requireNonBlankWhenPresent(name = "themeText", value = themeText)
            require(englishFallback.isNotBlank()) {
                "PixelLocalizationResolver.englishFallback must not be blank"
            }
            return explicitText ?: providerText ?: themeText ?: englishFallback
        }

        /** Rejects a blank optional precedence layer while preserving null as absence. */
        private fun requireNonBlankWhenPresent(name: String, value: String?) {
            require(value == null || value.isNotBlank()) {
                "PixelLocalizationResolver.$name must be null or non-blank"
            }
        }
    }
}

/**
 * 定义 `PixelLocalizationProvider` 在 `PixelLocalizations` 中承担的数据与行为边界。
 *
 * Explicit opt-in widget that resolves and installs [PixelLocalizations] for its [child].
 *
 * When [localeOverride] is null, this provider subscribes to the complete ordered locale list in
 * the nearest [HostCapabilities] snapshot. The first requested locale remains the published active
 * locale while bundle lookup may reach a later supported preference. An absent or empty Host list
 * falls back to [PixelLocalizationResolver.defaultLocale]. A non-null override avoids the Host
 * locale dependency. Hosts never add this provider automatically: until an application opts in,
 * components resolve their text from theme label tokens。Host 不会自动安装该 provider，未接入前
 * 组件从 theme label token 解析文本。
 *
 * @property child Descendant subtree receiving the resolved localization bundle.
 * @property resolver Deterministic custom/built-in bundle resolver.
 * @property localeOverride Optional requested locale that takes precedence over Host capabilities.
 * @property key Optional retained identity for this provider widget.
 */
public class PixelLocalizationProvider @JvmOverloads constructor(
    public val child: Widget,
    public val resolver: PixelLocalizationResolver = PixelLocalizationResolver.Default,
    public val localeOverride: PixelLocale? = null,
    override val key: Any? = null,
) : StatelessWidget(key = key) {
    /** Resolves ordered locale preferences and publishes one immutable inherited snapshot. */
    override fun build(context: BuildContext): Widget {
        /** Ordered requests selected without touching Host capabilities for an explicit override. */
        val requestedLocales = if (localeOverride != null) {
            listOf(localeOverride)
        } else {
            /** Complete Host preference order observed only by the Host-aware provider branch. */
            val hostLocales = HostCapabilities.maybeOf(context)?.locales.orEmpty()
            hostLocales.ifEmpty { listOf(resolver.defaultLocale) }
        }
        /** First requested locale retained even when a later preference supplies the bundle. */
        val activeLocale = requestedLocales.first()
        return PixelLocalizations(
            locale = activeLocale,
            bundle = resolver.resolveBundle(requestedLocales),
            child = child,
        )
    }
}

/**
 * 定义 `PixelLocalizations` 在 `PixelLocalizations` 中承担的数据与行为边界。
 *
 * Explicit opt-in inherited localization provider for one active [locale] and resolved [bundle].
 *
 * Host capability 中的 locale 不会自动安装该 provider。组件使用 [maybeOf]，因此缺少 provider 时
 * 会回落到 theme label token；[of] 在显式 provider 之外会故意失败。嵌套 provider 解析到最近的
 * retained 边界。
 *
 * A Host capability locale does not install this provider automatically. Components use [maybeOf]
 * so an absent provider falls through to theme label tokens; [of] deliberately fails outside an
 * explicit provider. Nested providers resolve to the nearest retained boundary.
 *
 * @property locale Active requested locale, which may differ from [bundle]'s fallback locale.
 * @property bundle Resolved immutable strings and formatters inherited by descendants.
 * @property child Descendant widget subtree receiving localization.
 * @property key Optional retained identity for this provider boundary.
 */
public class PixelLocalizations @JvmOverloads constructor(
    public val locale: PixelLocale,
    public val bundle: PixelLocalizationBundle,
    override val child: Widget,
    override val key: Any? = null,
) : InheritedWidget(child = child, key = key) {
    /** 创建 `PixelLocalizations` 实例并建立初始不变量。
 *
 * Resolves [locale] immediately through [resolver] for explicit provider construction.
 */
    @JvmOverloads
    public constructor(
        locale: PixelLocale,
        resolver: PixelLocalizationResolver,
        child: Widget,
        key: Any? = null,
    ) : this(
        locale = locale,
        bundle = resolver.resolveBundle(locale),
        child = child,
        key = key,
    )

    /** Notifies dependents when either the active locale or resolved bundle changes by value. */
    override fun updateShouldNotify(oldWidget: InheritedWidget): Boolean {
        val oldLocalizations = oldWidget as? PixelLocalizations ?: return true
        return locale != oldLocalizations.locale || bundle != oldLocalizations.bundle
    }

    /** 集中提供 `PixelLocalizations` 的 `<companion>` 共享入口。
 *
 * Reads only explicitly installed localization providers.
 */
    public companion object {
        /** 执行 `PixelLocalizations` 的 `maybeOf` 公开行为；具体参数、返回和副作用见下文。
 *
 * Returns the nearest explicitly inherited bundle, or null when localization is absent.
 */
        @JvmStatic
        public fun maybeOf(context: BuildContext): PixelLocalizationBundle? {
            return context.dependOnInheritedWidgetOfExactType<PixelLocalizations>()?.bundle
        }

        /** 执行 `PixelLocalizations` 的 `of` 公开行为；具体参数、返回和副作用见下文。
 *
 * Returns the nearest bundle or fails to preserve the provider's explicit opt-in contract.
 */
        @JvmStatic
        public fun of(context: BuildContext): PixelLocalizationBundle {
            return checkNotNull(maybeOf(context)) {
                "PixelLocalizations.of() requires an explicit PixelLocalizations ancestor"
            }
        }

        /** 执行 `PixelLocalizations` 的 `maybeLocaleOf` 公开行为；具体参数、返回和副作用见下文。
 *
 * Returns the nearest active requested locale, or null when localization is absent.
 */
        @JvmStatic
        public fun maybeLocaleOf(context: BuildContext): PixelLocale? {
            return context.dependOnInheritedWidgetOfExactType<PixelLocalizations>()?.locale
        }

        /** 执行 `PixelLocalizations` 的 `localeOf` 公开行为；具体参数、返回和副作用见下文。
 *
 * Returns the nearest active requested locale or fails outside an explicit provider.
 */
        @JvmStatic
        public fun localeOf(context: BuildContext): PixelLocale {
            return checkNotNull(maybeLocaleOf(context)) {
                "PixelLocalizations.localeOf() requires an explicit PixelLocalizations ancestor"
            }
        }
    }
}

/** Returns this canonical locale's language-only fallback, excluding unknown and private-use tags. */
private fun PixelLocale.languageLocaleOrNull(): PixelLocale? {
    /** First canonical subtag, which is the language except for private-use-only tags. */
    val language = languageTag.substringBefore('-')
    if (language == "und" || language == "x") return null
    if (language.length !in 2..8 || !language.all(Char::isLetter)) return null
    return PixelLocale(language)
}
