package com.purride.pixelcompat.routeentry.negative.replace

import com.purride.pixelui.PixelRouteRequest
import com.purride.pixelui.Text
import com.purride.pixelui.pixelRouteDestination

/** Replacement destination that produces an Int result. */
private val integerResultReplacement = pixelRouteDestination<String, Int>(
    id = "integer-result-replacement",
) { _, scope ->
    Text(scope.arguments)
}

/**
 * Intentional compile failure: a String-result scope may only replace itself with String-result requests.
 */
public val wrongReplaceDestination = pixelRouteDestination<String, String>(
    id = "string-result-origin",
) { _, scope ->
    scope.replaceWith(PixelRouteRequest(integerResultReplacement, "replacement"))
    Text(scope.arguments)
}
