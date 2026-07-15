package com.purride.pixelui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import java.util.Collections;
import org.junit.Test;

/** Verifies the localization core exposes natural platform-neutral Java descriptors. */
public final class PixelLocalizationsJavaInteropTest {
    /** Java can construct custom formatters, a delegate, and the complete fallback resolver. */
    @Test
    public void javaCanBuildAndResolveCustomLocalizationBundle() {
        /** Exact consumer locale represented only by the SDK's platform-neutral value. */
        final PixelLocale locale = new PixelLocale("fr-CA");
        /** Consumer bundle proving both Kotlin fun interfaces compile as Java SAM types. */
        final PixelLocalizationBundle bundle = new PixelLocalizationBundle(
                locale,
                PixelLabelTokens.Companion.getDefault(),
                "BARRE DE NAVIGATION",
                "RAIL DE NAVIGATION",
                value -> "N{" + value + "}",
                fraction -> "P{" + fraction + "}"
        );
        /** Exact map delegate created through the static Java entry point. */
        final PixelLocalizationDelegate delegate = PixelLocalizationDelegate.fromBundles(
                Collections.singletonList(bundle)
        );
        /** Resolver exercising the public full constructor without Android locale types. */
        final PixelLocalizationResolver resolver = new PixelLocalizationResolver(
                delegate,
                PixelLocale.Companion.getEnglish()
        );

        assertSame(bundle, resolver.resolveBundle(locale));
        assertEquals("N{-7}", bundle.formatInteger(-7));
        assertEquals("P{0.5}", bundle.formatPercent(0.5f));
        assertEquals(
                "PROVIDER",
                PixelLocalizationResolver.resolveText(null, "PROVIDER", "THEME", "ENGLISH")
        );
        assertSame(PixelLocalizationBundle.English, PixelLocalizationBundle.Default);
    }
}
