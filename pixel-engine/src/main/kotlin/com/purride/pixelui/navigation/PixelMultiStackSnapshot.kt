package com.purride.pixelui

import android.os.Bundle

/** 公开 `PixelMultiStackSnapshot` 的 `PixelMultiStackSnapshotSchemaVersion` 配置或运行值。
 *
 * Current outer schema used to group independently versioned child Navigator snapshots.
 */
public const val PixelMultiStackSnapshotSchemaVersion: Int = 1

/** 公开 `PixelMultiStackSnapshot` 的 `PixelMultiStackSnapshotBundleKey` 配置或运行值。
 *
 * Default Android Bundle key for one complete multi-stack Navigator snapshot.
 */
public const val PixelMultiStackSnapshotBundleKey: String =
    "com.purride.pixelui.navigator.multiStackSnapshot"

/** 定义 `PixelMultiStackSnapshotFailureReason` 在 `PixelMultiStackSnapshot` 中承担的数据与行为边界。
 *
 * Machine-readable reason a multi-stack snapshot operation was rejected atomically.
 */
public enum class PixelMultiStackSnapshotFailureReason {
    /** The controller or one of its child Navigators has not mounted. */
    NotAttached,

    /** The Bundle envelope is missing required fields or contains invalid structure. */
    InvalidEnvelope,

    /** The outer multi-stack schema is not supported by this SDK. */
    UnsupportedSchema,

    /** Saved stack identifiers do not exactly match the mounted stack definitions. */
    StackMismatch,

    /** No typed child-destination registry was supplied for a stack. */
    MissingRegistry,

    /** One child Navigator rejected its versioned arguments, state, or bytes. */
    ChildSnapshotRejected,

    /** A fully decoded child restoration plan could not be installed. */
    RestoreRejected,

    /** The outer stack count or aggregate byte size exceeded defensive limits. */
    LimitExceeded,
}

/**
 * 表示 `PixelMultiStackSnapshot` 的 `PixelMultiStackSnapshotFailure` 稳定结果或事件分支。
 *
 * Structured failure for multi-stack capture, Bundle decoding, or restoration.
 *
 * @property reason Stable rejection category.
 * @property message Consumer-readable explanation without raw route payloads.
 * @property stackId Related stack identifier when one child caused the failure.
 * @property childFailure Structured child-codec failure when available.
 */
public data class PixelMultiStackSnapshotFailure(
    public val reason: PixelMultiStackSnapshotFailureReason,
    public val message: String,
    public val stackId: String? = null,
    public val childFailure: PixelNavigatorSnapshotFailure? = null,
) {
    init {
        require(message.isNotBlank()) { "PixelMultiStackSnapshotFailure message must not be blank" }
    }
}

/**
 * 保存 `PixelMultiStackSnapshot` 的 `PixelMultiStackSnapshot` 可观察或可恢复状态。
 *
 * Defensive process-restoration snapshot for every child Navigator and the selected stack.
 *
 * Child byte arrays are copied on construction and access. Each child remains encoded by
 * [PixelNavigatorSnapshotCodec], so destination argument/state schemas migrate independently.
 *
 * @property schemaVersion Outer grouping schema.
 * @property activeStackId Stack selected when the snapshot was captured.
 */
public class PixelMultiStackSnapshot internal constructor(
    public val schemaVersion: Int,
    public val activeStackId: String,
    snapshots: Map<String, ByteArray>,
) {
    /** Immutable child bytes retained in deterministic stack order. */
    private val childSnapshots: Map<String, ByteArray> =
        snapshots.mapValuesTo(linkedMapOf()) { (_, bytes) -> bytes.copyOf() }

    /** 公开 `PixelMultiStackSnapshot` 的 `stackIds` 配置或运行值。
 *
 * Ordered stack identifiers included in this snapshot.
 */
    public val stackIds: Set<String>
        get() = childSnapshots.keys.toCollection(linkedSetOf())

    init {
        require(schemaVersion > 0) { "Multi-stack snapshot schema must be greater than zero" }
        require(activeStackId.isNotBlank()) { "Multi-stack activeStackId must not be blank" }
        require(childSnapshots.isNotEmpty()) { "Multi-stack snapshot must contain a child stack" }
        require(childSnapshots.keys.none(String::isBlank)) {
            "Multi-stack snapshot child IDs must not be blank"
        }
        require(childSnapshots.values.none(ByteArray::isEmpty)) {
            "Multi-stack snapshot child bytes must not be empty"
        }
        require(activeStackId in childSnapshots) {
            "Multi-stack activeStackId must identify one saved child stack"
        }
    }

    /** 执行 `PixelMultiStackSnapshot` 的 `snapshotBytes` 公开行为；具体参数、返回和副作用见下文。
 *
 * Returns a defensive copy of encoded child bytes for [stackId], when present.
 */
    public fun snapshotBytes(stackId: String): ByteArray? = childSnapshots[stackId]?.copyOf()

    /** Returns all child bytes as defensive copies for internal restore and Bundle encoding. */
    internal fun snapshotCopies(): Map<String, ByteArray> {
        return childSnapshots.mapValuesTo(linkedMapOf()) { (_, bytes) -> bytes.copyOf() }
    }
}

/** 定义 `PixelMultiStackSnapshotEncodeResult` 在 `PixelMultiStackSnapshot` 中的可替换调用契约。
 *
 * Result of capturing every mounted child Navigator.
 */
public sealed interface PixelMultiStackSnapshotEncodeResult {
    /** 定义 `Encoded` 在 `PixelMultiStackSnapshot` 中承担的数据与行为边界。
 *
 * Successfully captured all child stacks without partial output.
 */
    public data class Encoded(
        /** 记录 `PixelMultiStackSnapshot` 的 `snapshot` 配置或运行值，读取与更新均遵守所属类型约束。 */
        public val snapshot: PixelMultiStackSnapshot,
    ) : PixelMultiStackSnapshotEncodeResult

    /** 定义 `Rejected` 在 `PixelMultiStackSnapshot` 中承担的数据与行为边界。
 *
 * At least one child or controller invariant rejected the complete capture.
 */
    public data class Rejected(
        /** 保存 `PixelMultiStackSnapshot` 的 `failure` 结果或失败信息。 */
        public val failure: PixelMultiStackSnapshotFailure,
    ) : PixelMultiStackSnapshotEncodeResult
}

/** 定义 `PixelMultiStackSnapshotDecodeResult` 在 `PixelMultiStackSnapshot` 中的可替换调用契约。
 *
 * Result of reading a multi-stack snapshot envelope from an Android Bundle.
 */
public sealed interface PixelMultiStackSnapshotDecodeResult {
    /** 定义 `Decoded` 在 `PixelMultiStackSnapshot` 中承担的数据与行为边界。
 *
 * Bundle fields formed one bounded, supported snapshot.
 */
    public data class Decoded(
        /** 记录 `PixelMultiStackSnapshot` 的 `snapshot` 配置或运行值，读取与更新均遵守所属类型约束。 */
        public val snapshot: PixelMultiStackSnapshot,
    ) : PixelMultiStackSnapshotDecodeResult

    /** 定义 `Rejected` 在 `PixelMultiStackSnapshot` 中承担的数据与行为边界。
 *
 * Bundle fields were unsupported, malformed, or outside defensive limits.
 */
    public data class Rejected(
        /** 保存 `PixelMultiStackSnapshot` 的 `failure` 结果或失败信息。 */
        public val failure: PixelMultiStackSnapshotFailure,
    ) : PixelMultiStackSnapshotDecodeResult
}

/** 定义 `PixelMultiStackRestoreResult` 在 `PixelMultiStackSnapshot` 中的可替换调用契约。
 *
 * Result of atomically restoring all child plans and the selected stack.
 */
public sealed interface PixelMultiStackRestoreResult {
    /**
 * 定义 `Restored` 在 `PixelMultiStackSnapshot` 中承担的数据与行为边界。
 *
     * Every child stack was restored and [activeStackId] became selected.
     *
     * @property activeStackId Restored selected stack.
     * @property stackIds Complete restored stack identifiers.
     */
    public data class Restored(
        public val activeStackId: String,
        public val stackIds: Set<String>,
    ) : PixelMultiStackRestoreResult

    /** 定义 `Rejected` 在 `PixelMultiStackSnapshot` 中承担的数据与行为边界。
 *
 * No child plan was applied when validation or decoding failed.
 */
    public data class Rejected(
        /** 保存 `PixelMultiStackSnapshot` 的 `failure` 结果或失败信息。 */
        public val failure: PixelMultiStackSnapshotFailure,
    ) : PixelMultiStackRestoreResult
}

/**
 * 执行 `PixelMultiStackSnapshot` 的 `persistentSnapshot` 公开行为；具体参数、返回和副作用见下文。
 *
 * Captures every mounted child stack through its explicit destination [registries].
 *
 * The operation produces no snapshot if any child rejects persistence.
 *
 * 只要任一子栈拒绝持久化，本次操作就不会产出任何快照。
 */
public fun PixelMultiStackNavigatorController.persistentSnapshot(
    registries: Map<String, PixelRouteSnapshotRegistry>,
    codec: PixelNavigatorSnapshotCodec = PixelNavigatorSnapshotCodec(),
): PixelMultiStackSnapshotEncodeResult {
    if (!isAttached || stackIds.isEmpty()) {
        return rejectedMultiEncode(
            PixelMultiStackSnapshotFailureReason.NotAttached,
            "PixelMultiStackNavigatorController is not attached to a complete host",
        )
    }
    if (stackIds.size > MultiStackSnapshotLimits.MaxStacks) {
        return rejectedMultiEncode(
            PixelMultiStackSnapshotFailureReason.LimitExceeded,
            "Multi-stack snapshot exceeds ${MultiStackSnapshotLimits.MaxStacks} child stacks",
        )
    }
    val snapshots = linkedMapOf<String, ByteArray>()
    var aggregateBytes = 0L
    stackIds.forEach { stackId ->
        val registry = registries[stackId] ?: return rejectedMultiEncode(
            PixelMultiStackSnapshotFailureReason.MissingRegistry,
            "No persistent destination registry was supplied for stack '$stackId'",
            stackId,
        )
        val navigator = navigatorState(stackId) ?: return rejectedMultiEncode(
            PixelMultiStackSnapshotFailureReason.NotAttached,
            "Navigator stack '$stackId' has not mounted",
            stackId,
        )
        when (val encoded = navigator.persistentSnapshot(registry, codec)) {
            is PixelNavigatorSnapshotEncodeResult.Encoded -> {
                val bytes = encoded.bytes
                aggregateBytes += bytes.size.toLong()
                if (aggregateBytes > MultiStackSnapshotLimits.MaxAggregateBytes) {
                    return rejectedMultiEncode(
                        PixelMultiStackSnapshotFailureReason.LimitExceeded,
                        "Multi-stack child snapshots exceed ${MultiStackSnapshotLimits.MaxAggregateBytes} bytes",
                        stackId,
                    )
                }
                snapshots[stackId] = bytes
            }
            is PixelNavigatorSnapshotEncodeResult.Rejected -> return rejectedMultiEncode(
                PixelMultiStackSnapshotFailureReason.ChildSnapshotRejected,
                "Navigator stack '$stackId' rejected persistent capture",
                stackId,
                encoded.failure,
            )
        }
    }
    return PixelMultiStackSnapshotEncodeResult.Encoded(
        PixelMultiStackSnapshot(
            schemaVersion = PixelMultiStackSnapshotSchemaVersion,
            activeStackId = activeStackId,
            snapshots = snapshots,
        ),
    )
}

/**
 * 执行 `PixelMultiStackSnapshot` 的 `restorePersistentSnapshot` 公开行为；具体参数、返回和副作用见下文。
 *
 * Pre-decodes every child before installing any plan, then restores the selected stack.
 *
 * Stack definitions and registries must exactly match the captured identifiers. Under the engine's
 * single-threaded UI mutation contract, the preflight guarantees plan installation cannot become
 * partially valid between decoding and commit.
 */
public fun PixelMultiStackNavigatorController.restorePersistentSnapshot(
    snapshot: PixelMultiStackSnapshot,
    registries: Map<String, PixelRouteSnapshotRegistry>,
    codec: PixelNavigatorSnapshotCodec = PixelNavigatorSnapshotCodec(),
): PixelMultiStackRestoreResult {
    if (snapshot.schemaVersion != PixelMultiStackSnapshotSchemaVersion) {
        return rejectedMultiRestore(
            PixelMultiStackSnapshotFailureReason.UnsupportedSchema,
            "Unsupported multi-stack snapshot schema ${snapshot.schemaVersion}",
        )
    }
    if (!isAttached || stackIds.isEmpty()) {
        return rejectedMultiRestore(
            PixelMultiStackSnapshotFailureReason.NotAttached,
            "PixelMultiStackNavigatorController is not attached to a complete host",
        )
    }
    if (snapshot.stackIds != stackIds || snapshot.activeStackId !in stackIds) {
        return rejectedMultiRestore(
            PixelMultiStackSnapshotFailureReason.StackMismatch,
            "Saved stack identifiers do not match the mounted multi-stack host",
        )
    }
    val plans = linkedMapOf<String, PixelNavigatorRestorePlan>()
    stackIds.forEach { stackId ->
        val registry = registries[stackId] ?: return rejectedMultiRestore(
            PixelMultiStackSnapshotFailureReason.MissingRegistry,
            "No persistent destination registry was supplied for stack '$stackId'",
            stackId,
        )
        val navigator = navigatorState(stackId)
        if (navigator == null || navigator.inspectionSnapshot().isDisposed) {
            return rejectedMultiRestore(
                PixelMultiStackSnapshotFailureReason.NotAttached,
                "Navigator stack '$stackId' is not available for restoration",
                stackId,
            )
        }
        val bytes = snapshot.snapshotBytes(stackId) ?: return rejectedMultiRestore(
            PixelMultiStackSnapshotFailureReason.InvalidEnvelope,
            "Multi-stack snapshot is missing child bytes for '$stackId'",
            stackId,
        )
        when (val decoded = codec.decode(bytes, registry)) {
            is PixelNavigatorSnapshotDecodeResult.Decoded -> plans[stackId] = decoded.plan
            is PixelNavigatorSnapshotDecodeResult.Rejected -> return rejectedMultiRestore(
                PixelMultiStackSnapshotFailureReason.ChildSnapshotRejected,
                "Navigator stack '$stackId' rejected persistent restoration",
                stackId,
                decoded.failure,
            )
        }
    }
    stackIds.forEach { stackId ->
        val navigator = checkNotNull(navigatorState(stackId))
        val plan = checkNotNull(plans[stackId])
        if (!navigator.restore(plan)) {
            return rejectedMultiRestore(
                PixelMultiStackSnapshotFailureReason.RestoreRejected,
                "Navigator stack '$stackId' could not install its validated restoration plan",
                stackId,
            )
        }
    }
    selectStack(snapshot.activeStackId)
    return PixelMultiStackRestoreResult.Restored(
        activeStackId = snapshot.activeStackId,
        stackIds = snapshot.stackIds,
    )
}

/** 执行 `PixelMultiStackSnapshot` 的 `saveToBundle` 公开行为；具体参数、返回和副作用见下文。
 *
 * Writes one bounded multi-stack envelope below [key] without exposing child payloads as keys.
 */
public fun PixelMultiStackSnapshot.saveToBundle(
    outState: Bundle,
    key: String = PixelMultiStackSnapshotBundleKey,
) {
    require(key.isNotBlank()) { "Multi-stack snapshot Bundle key must not be blank" }
    val snapshots = snapshotCopies()
    val childIds = ArrayList(snapshots.keys)
    val envelope = Bundle().apply {
        putInt(MultiStackSchemaField, schemaVersion)
        putString(MultiStackActiveField, activeStackId)
        putStringArrayList(MultiStackIdsField, childIds)
        childIds.forEachIndexed { index, stackId ->
            putByteArray(MultiStackChildFieldPrefix + index, checkNotNull(snapshots[stackId]))
        }
    }
    outState.putBundle(key, envelope)
}

/**
 * 查询 `PixelMultiStackSnapshot` 的 `getPixelMultiStackSnapshot` 结果，不产生额外状态变更。
 *
 * Reads and validates a bounded multi-stack envelope stored below [key].
 *
 * `null` means no envelope exists; malformed or unsupported envelopes return structured rejection.
 */
public fun Bundle.getPixelMultiStackSnapshot(
    key: String = PixelMultiStackSnapshotBundleKey,
): PixelMultiStackSnapshotDecodeResult? {
    require(key.isNotBlank()) { "Multi-stack snapshot Bundle key must not be blank" }
    if (!containsKey(key)) return null
    val envelope = try {
        getBundle(key)
    } catch (error: RuntimeException) {
        return rejectedMultiDecode(
            PixelMultiStackSnapshotFailureReason.InvalidEnvelope,
            "Multi-stack Bundle value has the wrong type",
        )
    } ?: return rejectedMultiDecode(
        PixelMultiStackSnapshotFailureReason.InvalidEnvelope,
        "Multi-stack Bundle value is not a nested Bundle",
    )
    val schemaVersion = envelope.getInt(MultiStackSchemaField, -1)
    if (schemaVersion != PixelMultiStackSnapshotSchemaVersion) {
        return rejectedMultiDecode(
            PixelMultiStackSnapshotFailureReason.UnsupportedSchema,
            "Unsupported multi-stack snapshot schema $schemaVersion",
        )
    }
    val activeStackId = envelope.getString(MultiStackActiveField)
    val childIds = envelope.getStringArrayList(MultiStackIdsField)
    if (
        activeStackId.isNullOrBlank() ||
        childIds.isNullOrEmpty() ||
        childIds.any(String::isBlank) ||
        childIds.distinct().size != childIds.size ||
        activeStackId !in childIds
    ) {
        return rejectedMultiDecode(
            PixelMultiStackSnapshotFailureReason.InvalidEnvelope,
            "Multi-stack Bundle fields do not describe one valid selected stack set",
        )
    }
    if (childIds.size > MultiStackSnapshotLimits.MaxStacks) {
        return rejectedMultiDecode(
            PixelMultiStackSnapshotFailureReason.LimitExceeded,
            "Multi-stack Bundle exceeds ${MultiStackSnapshotLimits.MaxStacks} child stacks",
        )
    }
    val snapshots = linkedMapOf<String, ByteArray>()
    var aggregateBytes = 0L
    childIds.forEachIndexed { index, stackId ->
        val bytes = envelope.getByteArray(MultiStackChildFieldPrefix + index)
        if (bytes == null || bytes.isEmpty()) {
            return rejectedMultiDecode(
                PixelMultiStackSnapshotFailureReason.InvalidEnvelope,
                "Multi-stack Bundle is missing child bytes for '$stackId'",
                stackId,
            )
        }
        aggregateBytes += bytes.size.toLong()
        if (aggregateBytes > MultiStackSnapshotLimits.MaxAggregateBytes) {
            return rejectedMultiDecode(
                PixelMultiStackSnapshotFailureReason.LimitExceeded,
                "Multi-stack child snapshots exceed ${MultiStackSnapshotLimits.MaxAggregateBytes} bytes",
                stackId,
            )
        }
        snapshots[stackId] = bytes.copyOf()
    }
    return PixelMultiStackSnapshotDecodeResult.Decoded(
        PixelMultiStackSnapshot(
            schemaVersion = schemaVersion,
            activeStackId = activeStackId,
            snapshots = snapshots,
        ),
    )
}

/** Creates a structured capture rejection. */
private fun rejectedMultiEncode(
    reason: PixelMultiStackSnapshotFailureReason,
    message: String,
    stackId: String? = null,
    childFailure: PixelNavigatorSnapshotFailure? = null,
): PixelMultiStackSnapshotEncodeResult.Rejected {
    return PixelMultiStackSnapshotEncodeResult.Rejected(
        PixelMultiStackSnapshotFailure(reason, message, stackId, childFailure),
    )
}

/** Creates a structured Bundle decode rejection. */
private fun rejectedMultiDecode(
    reason: PixelMultiStackSnapshotFailureReason,
    message: String,
    stackId: String? = null,
): PixelMultiStackSnapshotDecodeResult.Rejected {
    return PixelMultiStackSnapshotDecodeResult.Rejected(
        PixelMultiStackSnapshotFailure(reason, message, stackId),
    )
}

/** Creates a structured restoration rejection. */
private fun rejectedMultiRestore(
    reason: PixelMultiStackSnapshotFailureReason,
    message: String,
    stackId: String? = null,
    childFailure: PixelNavigatorSnapshotFailure? = null,
): PixelMultiStackRestoreResult.Rejected {
    return PixelMultiStackRestoreResult.Rejected(
        PixelMultiStackSnapshotFailure(reason, message, stackId, childFailure),
    )
}

/** Defensive bounds for one outer multi-stack Bundle envelope. */
private object MultiStackSnapshotLimits {
    /** Maximum independently retained stacks accepted in one host. */
    const val MaxStacks: Int = 32

    /** Maximum aggregate child snapshot bytes accepted from saved state. */
    const val MaxAggregateBytes: Long = 8L * 1_048_576L
}

/** Private Bundle field containing the outer schema version. */
private const val MultiStackSchemaField: String = "schema"

/** Private Bundle field containing the selected stack identifier. */
private const val MultiStackActiveField: String = "active"

/** Private Bundle field containing child stack identifiers in stable order. */
private const val MultiStackIdsField: String = "ids"

/** Prefix for index-addressed child snapshot byte arrays. */
private const val MultiStackChildFieldPrefix: String = "child."

/** 为 `PixelMultiStackSnapshotEncodeResult.Encoded` 提供可直接构造与匹配的顶层短名。 */
public typealias PixelMultiStackSnapshotEncoded = PixelMultiStackSnapshotEncodeResult.Encoded

/** 为 `PixelMultiStackSnapshotEncodeResult.Rejected` 提供可直接构造与匹配的顶层短名。 */
public typealias PixelMultiStackSnapshotEncodeRejected = PixelMultiStackSnapshotEncodeResult.Rejected

/** 为 `PixelMultiStackSnapshotDecodeResult.Decoded` 提供可直接构造与匹配的顶层短名。 */
public typealias PixelMultiStackSnapshotDecoded = PixelMultiStackSnapshotDecodeResult.Decoded

/** 为 `PixelMultiStackSnapshotDecodeResult.Rejected` 提供可直接构造与匹配的顶层短名。 */
public typealias PixelMultiStackSnapshotDecodeRejected = PixelMultiStackSnapshotDecodeResult.Rejected

/** 为 `PixelMultiStackRestoreResult.Restored` 提供可直接构造与匹配的顶层短名。 */
public typealias PixelMultiStackRestored = PixelMultiStackRestoreResult.Restored

/** 为 `PixelMultiStackRestoreResult.Rejected` 提供可直接构造与匹配的顶层短名。 */
public typealias PixelMultiStackRestoreRejected = PixelMultiStackRestoreResult.Rejected
