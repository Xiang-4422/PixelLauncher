package com.purride.pixelcompat.routeentry.negative.complete

import com.purride.pixelui.Text
import com.purride.pixelui.pixelRouteDestination

/**
 * Intentional compile failure: this destination promises Int results but completes with String.
 */
public val wrongCompleteDestination = pixelRouteDestination<String, Int>(
    id = "integer-result-only",
) { _, scope ->
    scope.complete("not-an-integer")
    Text(scope.arguments)
}
