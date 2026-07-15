package com.purride.pixelcompat.routeentry.negative.argument

import com.purride.pixelui.PixelRouteRequest
import com.purride.pixelui.Text
import com.purride.pixelui.pixelRouteDestination

/** Destination whose request contract accepts only String arguments. */
private val stringArgumentDestination = pixelRouteDestination<String, Unit>(
    id = "string-argument-only",
) { _, scope ->
    Text(scope.arguments)
}

/**
 * Intentional compile failure: an Int must not satisfy the destination's String argument type.
 */
public val wrongArgumentRequest = PixelRouteRequest(stringArgumentDestination, 42)
