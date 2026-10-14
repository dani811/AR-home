package io.arhome.localizer.depth

/**
 * ARCore reprojects the latest raw-depth estimate between depth updates. The
 * Image timestamp identifies the underlying estimate, so freshness is defined
 * by a change from the previously observed raw-depth timestamp, not by equality
 * with the current RGB/ARCore frame timestamp.
 */
object RawDepthFreshness {
    fun isNew(previousTimestampNs: Long?, observedTimestampNs: Long): Boolean =
        previousTimestampNs == null || previousTimestampNs != observedTimestampNs
}
