// Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.

package net.whiteravens.dietapp.testing

/**
 * A test of something the application must do and does not do yet. [check] says it the way an
 * ordinary test would. While the defect is there, [check] fails and this passes, so that the
 * defect stays in the suite without turning it red. Once the code does what [check] asks, this
 * fails: the change that fixed the defect makes it an ordinary test.
 */
fun knownDefect(defect: String, check: () -> Unit) {
    val failure = runCatching(check).exceptionOrNull()
        ?: throw AssertionError("No longer a defect: $defect. Make this an ordinary test.")
    // What the defect looked like this time, in the test's report.
    val seen = failure.message.orEmpty().lines().joinToString("\n    ")
    println("Known defect, still there: $defect\n  seen as: ${failure::class.simpleName}: $seen")
}
