package com.purride.pixelui

import android.os.Bundle
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.CRC32

/** [PixelNavigatorSnapshotCodec] 写入并接受的唯一持久化 Navigator 快照 schema。
 *
 * Only persistent Navigator snapshot schema written and accepted by [PixelNavigatorSnapshotCodec].
 */
public const val PixelNavigatorPersistentSnapshotSchemaVersion: Int = 1

/** 公开 `PixelNavigatorSnapshotCodec` 的 `PixelNavigatorPersistentSnapshotBundleKey` 配置或运行值。
 *
 * Default Android [Bundle] key for versioned persistent Navigator snapshot bytes.
 */
public const val PixelNavigatorPersistentSnapshotBundleKey: String =
    "com.purride.pixelui.navigator.persistentSnapshot"

/**
 * 定义 `PixelRoutePayloadCodec` 在 `PixelNavigatorSnapshotCodec` 中的可替换调用契约。
 *
 * Versioned codec for one destination argument value.
 *
 * [schemaVersion] 会与每份参数 payload 一起写入；[decode] 只能显式接受自己能理解的版本，返回
 * [PixelRoutePayloadDecodeResult.Rejected] 会在改动任何活跃 Navigator 状态前整体拒绝该栈。
 *
 * [schemaVersion] is written next to every argument payload. [decode] must explicitly accept only
 * the versions it understands. Returning [PixelRoutePayloadDecodeResult.Rejected] safely rejects the
 * complete stack before any live Navigator state is changed.
 *
 * @param T Non-null argument value represented by this codec.
 */
public interface PixelRoutePayloadCodec<T : Any> {
    /** 公开 `PixelNavigatorSnapshotCodec` 的 `schemaVersion` 配置或运行值。
 *
 * Current payload schema emitted by [encode].
 */
    public val schemaVersion: Int

    /** 执行 `PixelNavigatorSnapshotCodec` 的 `encode` 公开行为；具体参数、返回和副作用见下文。
 *
 * Encodes [value] into a destination-owned, platform-neutral payload.
 */
    public fun encode(value: T): ByteArray

    /** 执行 `PixelNavigatorSnapshotCodec` 的 `decode` 公开行为；具体参数、返回和副作用见下文。
 *
 * 解码由 [schemaVersion] 写入的 [payload]。
 *
 * Decodes [payload] written with [schemaVersion].
 */
    public fun decode(
        schemaVersion: Int,
        payload: ByteArray,
    ): PixelRoutePayloadDecodeResult<T>
}

/** 定义 `PixelRoutePayloadDecodeResult` 在 `PixelNavigatorSnapshotCodec` 中的可替换调用契约。
 *
 * Result of decoding one destination-owned argument payload.
 */
public sealed interface PixelRoutePayloadDecodeResult<out T : Any> {
    /**
 * 定义 `Decoded` 在 `PixelNavigatorSnapshotCodec` 中承担的数据与行为边界。
 *
     * 解码成功的参数值。
     *
     * Successfully decoded argument value.
     *
     * @property value Validated value that may be used to create a restored route request.
     */
    public data class Decoded<T : Any>(public val value: T) : PixelRoutePayloadDecodeResult<T>

    /**
 * 定义 `Rejected` 在 `PixelNavigatorSnapshotCodec` 中承担的数据与行为边界。
 *
     * Rejected argument payload.
     *
     * @property message Consumer-readable reason that contains no raw payload data.
     */
    public data class Rejected(public val message: String) : PixelRoutePayloadDecodeResult<Nothing>
}

/** 定义 `PixelRouteStateRestorer` 在 `PixelNavigatorSnapshotCodec` 中的可替换调用契约。
 *
 * Applies previously validated route-local state to a newly allocated state bucket.
 */
public fun interface PixelRouteStateRestorer {
    /** 执行 `PixelNavigatorSnapshotCodec` 的 `restore` 公开行为；具体参数、返回和副作用见下文。
 *
 * Restores only destination-approved values into [stateBucket].
 */
    public fun restore(stateBucket: PixelRouteStateBucket)
}

/** 定义 `PixelRouteStateDecodeResult` 在 `PixelNavigatorSnapshotCodec` 中的可替换调用契约。
 *
 * Result of validating and migrating one destination's route-local state payload.
 */
public sealed interface PixelRouteStateDecodeResult {
    /**
 * 定义 `Decoded` 在 `PixelNavigatorSnapshotCodec` 中承担的数据与行为边界。
 *
     * Valid route-local state ready to apply to a fresh entry bucket.
     *
     * @property restorer Destination-owned operation that writes only approved state values.
     */
    public data class Decoded(
        public val restorer: PixelRouteStateRestorer,
    ) : PixelRouteStateDecodeResult

    /**
 * 定义 `Rejected` 在 `PixelNavigatorSnapshotCodec` 中承担的数据与行为边界。
 *
     * Rejected route-local state.
     *
     * @property message Consumer-readable reason that contains no raw payload data.
     */
    public data class Rejected(public val message: String) : PixelRouteStateDecodeResult
}

/**
 * 定义 `PixelRouteSnapshotAdapter` 在 `PixelNavigatorSnapshotCodec` 中承担的数据与行为边界。
 *
 * Persistence adapter for one typed [destination].
 *
 * The adapter is the allowlist boundary for process restoration. Arguments and route-local state
 * are never serialized reflectively. Implementations choose stable payloads and validate their size
 * and contents before any live Navigator state changes。实现必须自行选择稳定 payload，并在改动
 * 活跃 Navigator 状态前完成尺寸与内容校验。
 *
 * @param A Non-null argument type accepted by [destination].
 * @param R Successful result type produced by [destination].
 * @property destination Destination restored by this adapter.
 * @property argumentCodec Versioned argument codec owned by the destination.
 */
public abstract class PixelRouteSnapshotAdapter<A : Any, R>(
    public val destination: PixelRouteDestination<A, R>,
    public val argumentCodec: PixelRoutePayloadCodec<A>,
) {
    /** 公开 `PixelNavigatorSnapshotCodec` 当前的 `stateSchemaVersion` 状态维度。
 *
 * Current route-local state schema emitted by [encodeRouteState].
 */
    public open val stateSchemaVersion: Int = 1

    /**
 * 执行 `PixelNavigatorSnapshotCodec` 的 `encodeRouteState` 公开行为；具体参数、返回和副作用见下文。
 *
     * Captures destination-approved route-local values from [entry].
     *
     * Map keys are stable identifiers within this destination. The default persists no local
     * state, so ordinary in-memory bucket values are not accidentally written to disk.
     */
    public open fun encodeRouteState(entry: PixelRouteEntry<A, R>): Map<String, ByteArray> {
        return emptyMap()
    }

    /**
 * 执行 `PixelNavigatorSnapshotCodec` 的 `decodeRouteState` 公开行为；具体参数、返回和副作用见下文。
 *
     * 校验由 [schemaVersion] 写入的 route-local [payloads]。
     *
     * Validates route-local [payloads] written with [schemaVersion].
     *
     * The default accepts only an empty payload. Destinations that persist state must override
     * this method and return a restorer containing already validated values.
     */
    public open fun decodeRouteState(
        schemaVersion: Int,
        payloads: Map<String, ByteArray>,
    ): PixelRouteStateDecodeResult {
        return if (payloads.isEmpty()) {
            PixelRouteStateDecodeResult.Decoded(PixelRouteStateRestorer { })
        } else {
            PixelRouteStateDecodeResult.Rejected(
                "Destination '${destination.id}' does not accept persisted route-local state",
            )
        }
    }
}

/**
 * 定义 `PixelRouteSnapshotRegistry` 在 `PixelNavigatorSnapshotCodec` 中承担的数据与行为边界。
 *
 * Immutable allowlist of destinations that may participate in persistent restoration.
 *
 * Unknown destination IDs are rejected atomically. Duplicate IDs are rejected during registry
 * construction so a snapshot can never select an ambiguous destination implementation.
 *
 * @property adapters Registered destination adapters keyed by stable destination ID.
 */
public class PixelRouteSnapshotRegistry(
    adapters: Iterable<PixelRouteSnapshotAdapter<*, *>>,
) {
    /** Destination adapters retained in deterministic registration order. */
    private val adapters: Map<String, PixelRouteSnapshotAdapter<*, *>> = buildMap {
        adapters.forEach { adapter ->
            val destinationId = adapter.destination.id
            require(destinationId !in this) {
                "Duplicate PixelRouteSnapshotAdapter destination ID '$destinationId'"
            }
            require(adapter.argumentCodec.schemaVersion > 0) {
                "Argument schema version for '$destinationId' must be greater than zero"
            }
            require(adapter.stateSchemaVersion > 0) {
                "State schema version for '$destinationId' must be greater than zero"
            }
            put(destinationId, adapter)
        }
    }

    /** 公开 `PixelNavigatorSnapshotCodec` 的 `destinationIds` 配置或运行值。
 *
 * Stable destination IDs accepted by this registry.
 */
    public val destinationIds: Set<String>
        get() = adapters.keys

    /** 判断 `PixelNavigatorSnapshotCodec` 是否满足 `contains` 条件，不修改现有状态。
 *
 * Returns whether [destinationId] is explicitly registered for restoration.
 */
    public fun contains(destinationId: String): Boolean = destinationId in adapters

    /** Returns the registered adapter for [destinationId], or `null` when it is unknown. */
    internal fun adapterFor(destinationId: String): PixelRouteSnapshotAdapter<*, *>? {
        return adapters[destinationId]
    }
}

/** 定义 `PixelNavigatorSnapshotFailureReason` 在 `PixelNavigatorSnapshotCodec` 中承担的数据与行为边界。
 *
 * Machine-readable reason why persistent snapshot encoding or decoding was rejected.
 */
public enum class PixelNavigatorSnapshotFailureReason {
    /** No route entries were supplied or decoded. */
    EmptyStack,

    /** The envelope magic, checksum, lengths, or encoded value structure was invalid. */
    CorruptData,

    /** envelope schema 与本 SDK 唯一写入并接受的 schema 不一致。 */
    UnsupportedSchema,

    /** Entry IDs, current-entry identity, or stack shape violated Navigator invariants. */
    InvalidStack,

    /** A destination was not present in the explicit restoration registry. */
    UnknownDestination,

    /** A destination rejected or failed to encode/decode its argument payload. */
    ArgumentPayloadRejected,

    /** A destination rejected or failed to encode/decode its route-local state. */
    RouteStateRejected,

    /** The encoded snapshot exceeded a bounded count or byte-size limit. */
    LimitExceeded,
}

/**
 * 表示 `PixelNavigatorSnapshotCodec` 的 `PixelNavigatorSnapshotFailure` 稳定结果或事件分支。
 *
 * Structured persistent snapshot failure.
 *
 * @property reason Stable machine-readable failure category.
 * @property message Diagnostic explanation that never includes raw argument or state payloads.
 * @property entryIndex Root-to-foreground entry index related to the failure, when known.
 * @property destinationId Stable destination ID related to the failure, when known.
 */
public data class PixelNavigatorSnapshotFailure(
    public val reason: PixelNavigatorSnapshotFailureReason,
    public val message: String,
    public val entryIndex: Int? = null,
    public val destinationId: String? = null,
)

/** 定义 `PixelNavigatorSnapshotEncodeResult` 在 `PixelNavigatorSnapshotCodec` 中的可替换调用契约。
 *
 * Result of encoding a live typed route stack.
 */
public sealed interface PixelNavigatorSnapshotEncodeResult {
    /**
 * 定义 `Encoded` 在 `PixelNavigatorSnapshotCodec` 中承担的数据与行为边界。
 *
     * Successfully encoded persistent snapshot.
     *
     * @property bytes Defensive copy of the complete checksummed snapshot envelope.
     * @property schemaVersion Envelope schema written into [bytes].
     * @property entryCount Number of root-to-foreground entries encoded.
     */
    public class Encoded internal constructor(
        bytes: ByteArray,
        public val schemaVersion: Int,
        public val entryCount: Int,
    ) : PixelNavigatorSnapshotEncodeResult {
        /** Immutable encoded bytes returned as a defensive copy. */
        private val encodedBytes: ByteArray = bytes.copyOf()

        /** 公开 `PixelNavigatorSnapshotCodec` 的 `bytes` 配置或运行值。
 *
 * Returns a defensive copy suitable for Android saved state or durable storage.
 */
        public val bytes: ByteArray
            get() = encodedBytes.copyOf()
    }

    /**
 * 定义 `Rejected` 在 `PixelNavigatorSnapshotCodec` 中承担的数据与行为边界。
 *
     * Snapshot encoding was rejected without producing partial bytes.
     *
     * @property failure Structured reason for the rejection.
     */
    public data class Rejected(
        public val failure: PixelNavigatorSnapshotFailure,
    ) : PixelNavigatorSnapshotEncodeResult
}

/** 定义 `PixelNavigatorSnapshotDecodeResult` 在 `PixelNavigatorSnapshotCodec` 中的可替换调用契约。
 *
 * Result of validating and decoding an untrusted persistent snapshot.
 */
public sealed interface PixelNavigatorSnapshotDecodeResult {
    /**
 * 定义 `Decoded` 在 `PixelNavigatorSnapshotCodec` 中承担的数据与行为边界。
 *
     * Complete stack was decoded and validated atomically.
     *
     * @property plan One-shot restoration plan ready for Navigator integration.
     */
    public data class Decoded(
        public val plan: PixelNavigatorRestorePlan,
    ) : PixelNavigatorSnapshotDecodeResult

    /**
 * 定义 `Rejected` 在 `PixelNavigatorSnapshotCodec` 中承担的数据与行为边界。
 *
     * Snapshot was rejected before any live Navigator state changed.
     *
     * @property failure Structured reason for the rejection.
     */
    public data class Rejected(
        public val failure: PixelNavigatorSnapshotFailure,
    ) : PixelNavigatorSnapshotDecodeResult
}

/**
 * 定义 `PixelNavigatorRestoredEntryInspection` 在 `PixelNavigatorSnapshotCodec` 中承担的数据与行为边界。
 *
 * Public, argument-free description of one entry in a validated restoration plan.
 *
 * @property id Preserved entry identity from the snapshot.
 * @property destinationId Stable registered destination identifier.
 * @property argumentSchemaVersion Destination argument schema found in the snapshot.
 * @property stateSchemaVersion Destination state schema found in the snapshot.
 * @property stateKeys Stable local-state keys accepted by the destination adapter.
 */
public data class PixelNavigatorRestoredEntryInspection(
    public val id: PixelRouteEntryId,
    public val destinationId: String,
    public val argumentSchemaVersion: Int,
    public val stateSchemaVersion: Int,
    public val stateKeys: Set<String>,
)

/**
 * 定义 `PixelNavigatorRestorePlan` 在 `PixelNavigatorSnapshotCodec` 中承担的数据与行为边界。
 *
 * Fully validated, one-shot route-stack restoration plan.
 *
 * Plans retain decoded typed arguments and fresh state buckets internally. Public inspection does
 * not expose those potentially sensitive values. A Navigator must consume the whole plan or keep
 * its existing stack; partial entry restoration is deliberately unsupported.
 *
 * @property schemaVersion 产生该计划的快照 schema。
 * @property currentEntryId Foreground entry identity preserved by the snapshot.
 * @property entries Argument-free root-to-foreground entry inspections.
 */
public class PixelNavigatorRestorePlan internal constructor(
    public val schemaVersion: Int,
    public val currentEntryId: PixelRouteEntryId,
    public val entries: List<PixelNavigatorRestoredEntryInspection>,
    internal val resolvedEntries: List<ResolvedPersistentRouteEntry>,
) {
    /** Whether the detached decoded entries have already been claimed by a Navigator. */
    private var consumed: Boolean = false

    /** Claims every detached entry exactly once for atomic Navigator installation. */
    @Synchronized
    internal fun claimEntries(owner: PixelRouteEntryOwner): List<PixelRouteEntry<*, *>>? {
        if (consumed) return null
        consumed = true
        return resolvedEntries.map { entry -> entry.createEntry(owner) }
    }
}

/** 执行 `PixelNavigatorSnapshotCodec` 的 `saveToBundle` 公开行为；具体参数、返回和副作用见下文。
 *
 * Saves this successful encoded snapshot to [outState] under [key].
 */
public fun PixelNavigatorSnapshotEncodeResult.Encoded.saveToBundle(
    outState: Bundle,
    key: String = PixelNavigatorPersistentSnapshotBundleKey,
) {
    require(key.isNotBlank()) { "Persistent Navigator snapshot Bundle key must not be blank" }
    outState.putByteArray(key, bytes)
}

/** 查询 `PixelNavigatorSnapshotCodec` 的 `getPixelNavigatorPersistentSnapshotBytes` 结果，不产生额外状态变更。
 *
 * Returns a defensive copy of persistent snapshot bytes stored under [key], when present.
 */
public fun Bundle.getPixelNavigatorPersistentSnapshotBytes(
    key: String = PixelNavigatorPersistentSnapshotBundleKey,
): ByteArray? {
    require(key.isNotBlank()) { "Persistent Navigator snapshot Bundle key must not be blank" }
    return getByteArray(key)?.copyOf()
}

/**
 * 定义 `PixelNavigatorSnapshotCodec` 在 `PixelNavigatorSnapshotCodec` 中承担的数据与行为边界。
 *
 * Bounded, checksummed codec for versioned typed Navigator snapshots.
 *
 * 该 schema 保存 entry 身份、destination ID、版本化参数字节、destination 批准的 route-local
 * state 以及前台 entry ID。解码对任意字节数组都是全函数：畸形、未知、超限或被 adapter 拒绝的输入
 * 一律返回 [PixelNavigatorSnapshotDecodeResult.Rejected]，既不抛出异常也不半途恢复栈。
 *
 * The schema stores entry identity, destination ID, versioned argument bytes, versioned
 * destination-approved local state, and the foreground entry ID. Decoding is total for arbitrary
 * byte arrays: malformed, unknown, oversized, or adapter-rejected input returns [PixelNavigatorSnapshotDecodeResult.Rejected]
 * instead of throwing or partially restoring a stack.
 */
public class PixelNavigatorSnapshotCodec {
    /** 执行 `PixelNavigatorSnapshotCodec` 的 `encode` 公开行为；具体参数、返回和副作用见下文。
 *
 * Encodes [entries] in root-to-foreground order using the explicit [registry] allowlist.
 */
    public fun encode(
        entries: List<PixelRouteEntry<*, *>>,
        registry: PixelRouteSnapshotRegistry,
    ): PixelNavigatorSnapshotEncodeResult {
        val rawEntriesResult = captureEntries(entries, registry)
        if (rawEntriesResult is CaptureEntriesResult.Rejected) {
            return PixelNavigatorSnapshotEncodeResult.Rejected(rawEntriesResult.failure)
        }
        val rawEntries = (rawEntriesResult as CaptureEntriesResult.Captured).entries
        return try {
            val payload = encodePayload(
                currentEntryId = rawEntries.last().entryId,
                entries = rawEntries,
            )
            val envelope = encodeEnvelope(
                schemaVersion = PixelNavigatorPersistentSnapshotSchemaVersion,
                payload = payload,
            )
            if (envelope.size > SnapshotLimits.MaxEncodedBytes) {
                PixelNavigatorSnapshotEncodeResult.Rejected(
                    failure(
                        PixelNavigatorSnapshotFailureReason.LimitExceeded,
                        "Encoded Navigator snapshot exceeds ${SnapshotLimits.MaxEncodedBytes} bytes",
                    ),
                )
            } else {
                PixelNavigatorSnapshotEncodeResult.Encoded(
                    bytes = envelope,
                    schemaVersion = PixelNavigatorPersistentSnapshotSchemaVersion,
                    entryCount = rawEntries.size,
                )
            }
        } catch (error: SnapshotLimitException) {
            PixelNavigatorSnapshotEncodeResult.Rejected(
                failure(PixelNavigatorSnapshotFailureReason.LimitExceeded, error.message.orEmpty()),
            )
        } catch (error: Exception) {
            PixelNavigatorSnapshotEncodeResult.Rejected(
                failure(
                    PixelNavigatorSnapshotFailureReason.CorruptData,
                    "Failed to encode Navigator snapshot: ${safeErrorMessage(error)}",
                ),
            )
        }
    }

    /** 执行 `PixelNavigatorSnapshotCodec` 的 `decode` 公开行为；具体参数、返回和副作用见下文。
 *
 * Decodes and atomically validates untrusted [bytes] against [registry].
 */
    public fun decode(
        bytes: ByteArray,
        registry: PixelRouteSnapshotRegistry,
    ): PixelNavigatorSnapshotDecodeResult {
        if (bytes.size > SnapshotLimits.MaxEncodedBytes) {
            return rejectedDecode(
                PixelNavigatorSnapshotFailureReason.LimitExceeded,
                "Navigator snapshot exceeds ${SnapshotLimits.MaxEncodedBytes} bytes",
            )
        }
        val envelope = try {
            decodeEnvelope(bytes)
        } catch (error: SnapshotLimitException) {
            return rejectedDecode(
                PixelNavigatorSnapshotFailureReason.LimitExceeded,
                error.message.orEmpty(),
            )
        } catch (error: Exception) {
            return rejectedDecode(
                PixelNavigatorSnapshotFailureReason.CorruptData,
                "Invalid Navigator snapshot envelope: ${safeErrorMessage(error)}",
            )
        }
        val rawStack = try {
            if (envelope.schemaVersion != PixelNavigatorPersistentSnapshotSchemaVersion) {
                return rejectedDecode(
                    PixelNavigatorSnapshotFailureReason.UnsupportedSchema,
                    "Unsupported Navigator snapshot schema ${envelope.schemaVersion}",
                )
            }
            decodePayload(envelope.payload)
        } catch (error: SnapshotLimitException) {
            return rejectedDecode(
                PixelNavigatorSnapshotFailureReason.LimitExceeded,
                error.message.orEmpty(),
            )
        } catch (error: Exception) {
            return rejectedDecode(
                PixelNavigatorSnapshotFailureReason.CorruptData,
                "Invalid Navigator snapshot payload: ${safeErrorMessage(error)}",
            )
        }
        validateRawStack(rawStack)?.let { failure ->
            return PixelNavigatorSnapshotDecodeResult.Rejected(failure)
        }
        return resolveRawStack(rawStack = rawStack, registry = registry)
    }

    /** Captures and validates each live entry before any bytes are emitted. */
    private fun captureEntries(
        entries: List<PixelRouteEntry<*, *>>,
        registry: PixelRouteSnapshotRegistry,
    ): CaptureEntriesResult {
        if (entries.isEmpty()) {
            return CaptureEntriesResult.Rejected(
                failure(PixelNavigatorSnapshotFailureReason.EmptyStack, "Navigator stack is empty"),
            )
        }
        if (entries.size > SnapshotLimits.MaxEntries) {
            return CaptureEntriesResult.Rejected(
                failure(
                    PixelNavigatorSnapshotFailureReason.LimitExceeded,
                    "Navigator stack exceeds ${SnapshotLimits.MaxEntries} entries",
                ),
            )
        }
        val seenIds = linkedSetOf<PixelRouteEntryId>()
        val captured = mutableListOf<RawPersistentRouteEntry>()
        entries.forEachIndexed { index, entry ->
            if (!seenIds.add(entry.id)) {
                return CaptureEntriesResult.Rejected(
                    failure(
                        reason = PixelNavigatorSnapshotFailureReason.InvalidStack,
                        message = "Duplicate route entry ID ${entry.id.value}",
                        entryIndex = index,
                        destinationId = entry.destination.id,
                    ),
                )
            }
            if (entry.id.value == Long.MAX_VALUE) {
                return CaptureEntriesResult.Rejected(
                    failure(
                        reason = PixelNavigatorSnapshotFailureReason.InvalidStack,
                        message = "Route entry ID ${entry.id.value} leaves no identity for a future push",
                        entryIndex = index,
                        destinationId = entry.destination.id,
                    ),
                )
            }
            val adapter = registry.adapterFor(entry.destination.id)
                ?: return CaptureEntriesResult.Rejected(
                    failure(
                        reason = PixelNavigatorSnapshotFailureReason.UnknownDestination,
                        message = "Destination '${entry.destination.id}' is not registered for restoration",
                        entryIndex = index,
                        destinationId = entry.destination.id,
                    ),
                )
            if (adapter.destination !== entry.destination) {
                return CaptureEntriesResult.Rejected(
                    failure(
                        reason = PixelNavigatorSnapshotFailureReason.UnknownDestination,
                        message = "Destination '${entry.destination.id}' does not match its registered adapter instance",
                        entryIndex = index,
                        destinationId = entry.destination.id,
                    ),
                )
            }
            val rawEntry = captureEntry(index, entry, adapter)
            if (rawEntry is CaptureEntryResult.Rejected) {
                return CaptureEntriesResult.Rejected(rawEntry.failure)
            }
            captured += (rawEntry as CaptureEntryResult.Captured).entry
        }
        return CaptureEntriesResult.Captured(captured)
    }

    /** Captures one type-erased entry through its matching typed adapter. */
    @Suppress("UNCHECKED_CAST")
    private fun captureEntry(
        index: Int,
        entry: PixelRouteEntry<*, *>,
        adapter: PixelRouteSnapshotAdapter<*, *>,
    ): CaptureEntryResult {
        return captureTypedEntry(
            index = index,
            entry = entry as PixelRouteEntry<Any, Any?>,
            adapter = adapter as PixelRouteSnapshotAdapter<Any, Any?>,
        )
    }

    /** Encodes one entry after the destination-instance check makes the erased cast safe. */
    private fun <A : Any, R> captureTypedEntry(
        index: Int,
        entry: PixelRouteEntry<A, R>,
        adapter: PixelRouteSnapshotAdapter<A, R>,
    ): CaptureEntryResult {
        val destinationId = entry.destination.id
        val argumentPayload = try {
            adapter.argumentCodec.encode(entry.arguments).copyOf()
        } catch (error: Exception) {
            return CaptureEntryResult.Rejected(
                failure(
                    reason = PixelNavigatorSnapshotFailureReason.ArgumentPayloadRejected,
                    message = "Destination '$destinationId' argument encoding failed: ${safeErrorMessage(error)}",
                    entryIndex = index,
                    destinationId = destinationId,
                ),
            )
        }
        if (argumentPayload.size > SnapshotLimits.MaxArgumentBytes) {
            return CaptureEntryResult.Rejected(
                failure(
                    reason = PixelNavigatorSnapshotFailureReason.LimitExceeded,
                    message = "Destination '$destinationId' argument payload exceeds ${SnapshotLimits.MaxArgumentBytes} bytes",
                    entryIndex = index,
                    destinationId = destinationId,
                ),
            )
        }
        val routeState = try {
            adapter.encodeRouteState(entry).mapValues { (_, payload) -> payload.copyOf() }
        } catch (error: Exception) {
            return CaptureEntryResult.Rejected(
                failure(
                    reason = PixelNavigatorSnapshotFailureReason.RouteStateRejected,
                    message = "Destination '$destinationId' route-state encoding failed: ${safeErrorMessage(error)}",
                    entryIndex = index,
                    destinationId = destinationId,
                ),
            )
        }
        validateStatePayloads(routeState)?.let { message ->
            return CaptureEntryResult.Rejected(
                failure(
                    reason = PixelNavigatorSnapshotFailureReason.LimitExceeded,
                    message = "Destination '$destinationId' $message",
                    entryIndex = index,
                    destinationId = destinationId,
                ),
            )
        }
        return CaptureEntryResult.Captured(
            RawPersistentRouteEntry(
                entryId = entry.id,
                destinationId = destinationId,
                argumentSchemaVersion = adapter.argumentCodec.schemaVersion,
                argumentPayload = argumentPayload,
                stateSchemaVersion = adapter.stateSchemaVersion,
                statePayloads = routeState,
            ),
        )
    }

    /** Resolves every raw entry or rejects the complete stack on the first unsafe value. */
    private fun resolveRawStack(
        rawStack: RawPersistentRouteStack,
        registry: PixelRouteSnapshotRegistry,
    ): PixelNavigatorSnapshotDecodeResult {
        rawStack.entries.forEachIndexed { index, rawEntry ->
            if (registry.adapterFor(rawEntry.destinationId) == null) {
                return rejectedDecode(
                    reason = PixelNavigatorSnapshotFailureReason.UnknownDestination,
                    message = "Destination '${rawEntry.destinationId}' is not registered for restoration",
                    entryIndex = index,
                    destinationId = rawEntry.destinationId,
                )
            }
        }
        val resolvedEntries = mutableListOf<ResolvedPersistentRouteEntry>()
        val inspections = mutableListOf<PixelNavigatorRestoredEntryInspection>()
        rawStack.entries.forEachIndexed { index, rawEntry ->
            val adapter = registry.adapterFor(rawEntry.destinationId)
                ?: return rejectedDecode(
                    reason = PixelNavigatorSnapshotFailureReason.UnknownDestination,
                    message = "Destination '${rawEntry.destinationId}' is not registered for restoration",
                    entryIndex = index,
                    destinationId = rawEntry.destinationId,
                )
            val resolved = resolveEntry(index, rawEntry, adapter)
            if (resolved is ResolveEntryResult.Rejected) {
                return PixelNavigatorSnapshotDecodeResult.Rejected(resolved.failure)
            }
            val decodedEntry = (resolved as ResolveEntryResult.Resolved).entry
            resolvedEntries += decodedEntry
            inspections += PixelNavigatorRestoredEntryInspection(
                id = rawEntry.entryId,
                destinationId = rawEntry.destinationId,
                argumentSchemaVersion = rawEntry.argumentSchemaVersion,
                stateSchemaVersion = rawEntry.stateSchemaVersion,
                stateKeys = rawEntry.statePayloads.keys,
            )
        }
        return PixelNavigatorSnapshotDecodeResult.Decoded(
            PixelNavigatorRestorePlan(
                schemaVersion = PixelNavigatorPersistentSnapshotSchemaVersion,
                currentEntryId = rawStack.currentEntryId,
                entries = inspections,
                resolvedEntries = resolvedEntries,
            ),
        )
    }

    /** Resolves one type-erased raw entry through its registered typed adapter. */
    @Suppress("UNCHECKED_CAST")
    private fun resolveEntry(
        index: Int,
        rawEntry: RawPersistentRouteEntry,
        adapter: PixelRouteSnapshotAdapter<*, *>,
    ): ResolveEntryResult {
        return resolveTypedEntry(
            index = index,
            rawEntry = rawEntry,
            adapter = adapter as PixelRouteSnapshotAdapter<Any, Any?>,
        )
    }

    /** Decodes arguments and validates local state into a fresh, detached bucket. */
    private fun <A : Any, R> resolveTypedEntry(
        index: Int,
        rawEntry: RawPersistentRouteEntry,
        adapter: PixelRouteSnapshotAdapter<A, R>,
    ): ResolveEntryResult {
        val arguments = try {
            when (
                val decoded = adapter.argumentCodec.decode(
                    schemaVersion = rawEntry.argumentSchemaVersion,
                    payload = rawEntry.argumentPayload.copyOf(),
                )
            ) {
                is PixelRoutePayloadDecodeResult.Decoded -> decoded.value
                is PixelRoutePayloadDecodeResult.Rejected -> return ResolveEntryResult.Rejected(
                    failure(
                        reason = PixelNavigatorSnapshotFailureReason.ArgumentPayloadRejected,
                        message = "Destination '${rawEntry.destinationId}' rejected arguments: ${decoded.message}",
                        entryIndex = index,
                        destinationId = rawEntry.destinationId,
                    ),
                )
            }
        } catch (error: Exception) {
            return ResolveEntryResult.Rejected(
                failure(
                    reason = PixelNavigatorSnapshotFailureReason.ArgumentPayloadRejected,
                    message = "Destination '${rawEntry.destinationId}' argument decoding failed: ${safeErrorMessage(error)}",
                    entryIndex = index,
                    destinationId = rawEntry.destinationId,
                ),
            )
        }
        val stateResult = try {
            adapter.decodeRouteState(
                schemaVersion = rawEntry.stateSchemaVersion,
                payloads = rawEntry.statePayloads.mapValues { (_, payload) -> payload.copyOf() },
            )
        } catch (error: Exception) {
            return ResolveEntryResult.Rejected(
                failure(
                    reason = PixelNavigatorSnapshotFailureReason.RouteStateRejected,
                    message = "Destination '${rawEntry.destinationId}' route-state decoding failed: ${safeErrorMessage(error)}",
                    entryIndex = index,
                    destinationId = rawEntry.destinationId,
                ),
            )
        }
        val restorer = when (stateResult) {
            is PixelRouteStateDecodeResult.Decoded -> stateResult.restorer
            is PixelRouteStateDecodeResult.Rejected -> return ResolveEntryResult.Rejected(
                failure(
                    reason = PixelNavigatorSnapshotFailureReason.RouteStateRejected,
                    message = "Destination '${rawEntry.destinationId}' rejected route state: ${stateResult.message}",
                    entryIndex = index,
                    destinationId = rawEntry.destinationId,
                ),
            )
        }
        val restoredBucket = PixelRouteStateBucket.create()
        try {
            restorer.restore(restoredBucket)
        } catch (error: Exception) {
            restoredBucket.disposeRetention()
            return ResolveEntryResult.Rejected(
                failure(
                    reason = PixelNavigatorSnapshotFailureReason.RouteStateRejected,
                    message = "Destination '${rawEntry.destinationId}' route-state restoration failed: ${safeErrorMessage(error)}",
                    entryIndex = index,
                    destinationId = rawEntry.destinationId,
                ),
            )
        }
        return ResolveEntryResult.Resolved(
            ResolvedPersistentRouteEntry.Typed(
                id = rawEntry.entryId,
                destination = adapter.destination,
                arguments = arguments,
                stateBucket = restoredBucket,
            ),
        )
    }

    /** Validates stack-level invariants before destination code is invoked. */
    private fun validateRawStack(rawStack: RawPersistentRouteStack): PixelNavigatorSnapshotFailure? {
        if (rawStack.entries.isEmpty()) {
            return failure(PixelNavigatorSnapshotFailureReason.EmptyStack, "Navigator stack is empty")
        }
        if (rawStack.entries.size > SnapshotLimits.MaxEntries) {
            return failure(
                PixelNavigatorSnapshotFailureReason.LimitExceeded,
                "Navigator stack exceeds ${SnapshotLimits.MaxEntries} entries",
            )
        }
        val ids = linkedSetOf<PixelRouteEntryId>()
        rawStack.entries.forEachIndexed { index, entry ->
            if (entry.entryId.value == Long.MAX_VALUE) {
                return failure(
                    reason = PixelNavigatorSnapshotFailureReason.InvalidStack,
                    message = "Route entry ID ${entry.entryId.value} leaves no identity for a future push",
                    entryIndex = index,
                    destinationId = entry.destinationId,
                )
            }
            if (!ids.add(entry.entryId)) {
                return failure(
                    reason = PixelNavigatorSnapshotFailureReason.InvalidStack,
                    message = "Duplicate route entry ID ${entry.entryId.value}",
                    entryIndex = index,
                    destinationId = entry.destinationId,
                )
            }
        }
        if (rawStack.currentEntryId != rawStack.entries.last().entryId) {
            return failure(
                PixelNavigatorSnapshotFailureReason.InvalidStack,
                "Foreground entry ID must match the final stack entry",
            )
        }
        return null
    }

    /** 编码不含 envelope 头与校验和的 schema payload。 */
    private fun encodePayload(
        currentEntryId: PixelRouteEntryId,
        entries: List<RawPersistentRouteEntry>,
    ): ByteArray {
        return ByteArrayOutputStream().use { byteOutput ->
            DataOutputStream(byteOutput).use { output ->
                output.writeLong(currentEntryId.value)
                output.writeInt(entries.size)
                entries.forEach { entry -> writeRawEntry(output, entry) }
            }
            byteOutput.toByteArray()
        }
    }

    /** 解码 schema payload，并拒绝任何尾部多余字节。 */
    private fun decodePayload(payload: ByteArray): RawPersistentRouteStack {
        return DataInputStream(ByteArrayInputStream(payload)).use { input ->
            val currentEntryId = PixelRouteEntryId(readPositiveLong(input, "current entry ID"))
            val entryCount = readBoundedCount(input, "entry", SnapshotLimits.MaxEntries)
            val entries = List(entryCount) { readRawEntry(input) }
            require(input.available() == 0) { "Trailing bytes after snapshot payload" }
            RawPersistentRouteStack(currentEntryId = currentEntryId, entries = entries)
        }
    }

    /** 按当前唯一 payload 布局写出一条原始 entry。 */
    private fun writeRawEntry(
        output: DataOutputStream,
        entry: RawPersistentRouteEntry,
    ) {
        output.writeLong(entry.entryId.value)
        writeString(output, entry.destinationId)
        output.writeInt(entry.argumentSchemaVersion)
        writeByteArray(output, entry.argumentPayload, SnapshotLimits.MaxArgumentBytes)
        output.writeInt(entry.stateSchemaVersion)
        output.writeInt(entry.statePayloads.size)
        entry.statePayloads.toSortedMap().forEach { (key, payload) ->
            writeString(output, key)
            writeByteArray(output, payload, SnapshotLimits.MaxStateValueBytes)
        }
    }

    /** 按当前唯一 payload 布局读取一条原始 entry。 */
    private fun readRawEntry(input: DataInputStream): RawPersistentRouteEntry {
        val entryId = PixelRouteEntryId(readPositiveLong(input, "entry ID"))
        val destinationId = readString(input)
        require(destinationId.isNotBlank()) { "Destination ID must not be blank" }
        val argumentSchemaVersion = readPositiveInt(input, "argument schema version")
        val argumentPayload = readByteArray(input, SnapshotLimits.MaxArgumentBytes)
        /** 当前 schema 始终显式写入 state schema 与已批准的 state 键值。 */
        val stateSchemaVersion = readPositiveInt(input, "state schema version")
        /** 有界读取的 route-local state 键值，键名重复即整体拒绝。 */
        val stateCount = readBoundedCount(input, "state value", SnapshotLimits.MaxStateValues)
        /** 保持读取顺序的已校验 state 值。 */
        val statePayloads = linkedMapOf<String, ByteArray>()
        repeat(stateCount) {
            val key = readString(input)
            require(key.isNotBlank()) { "Route-state key must not be blank" }
            require(key !in statePayloads) { "Duplicate route-state key '$key'" }
            statePayloads[key] = readByteArray(input, SnapshotLimits.MaxStateValueBytes)
        }
        return RawPersistentRouteEntry(
            entryId = entryId,
            destinationId = destinationId,
            argumentSchemaVersion = argumentSchemaVersion,
            argumentPayload = argumentPayload,
            stateSchemaVersion = stateSchemaVersion,
            statePayloads = statePayloads,
        )
    }

    /** Wraps one schema payload in a magic, length, and CRC32 envelope. */
    private fun encodeEnvelope(schemaVersion: Int, payload: ByteArray): ByteArray {
        require(schemaVersion > 0) { "Snapshot schema version must be greater than zero" }
        if (payload.size > SnapshotLimits.MaxPayloadBytes) {
            throw SnapshotLimitException(
                "Snapshot payload exceeds ${SnapshotLimits.MaxPayloadBytes} bytes",
            )
        }
        val checksum = CRC32().apply { update(payload) }.value.toInt()
        return ByteArrayOutputStream().use { byteOutput ->
            DataOutputStream(byteOutput).use { output ->
                output.writeInt(SnapshotMagic)
                output.writeInt(schemaVersion)
                output.writeInt(payload.size)
                output.write(payload)
                output.writeInt(checksum)
            }
            byteOutput.toByteArray()
        }
    }

    /** Validates and extracts an encoded snapshot envelope. */
    private fun decodeEnvelope(bytes: ByteArray): SnapshotEnvelope {
        require(bytes.size >= SnapshotEnvelopeOverhead) { "Snapshot envelope is truncated" }
        return DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            require(input.readInt() == SnapshotMagic) { "Snapshot magic does not match" }
            val schemaVersion = readPositiveInt(input, "snapshot schema version")
            val payloadLength = input.readInt()
            if (payloadLength < 0 || payloadLength > SnapshotLimits.MaxPayloadBytes) {
                throw SnapshotLimitException("Snapshot payload length $payloadLength is outside limits")
            }
            require(input.available() == payloadLength + Int.SIZE_BYTES) {
                "Snapshot envelope length does not match its payload"
            }
            val payload = ByteArray(payloadLength)
            input.readFully(payload)
            val expectedChecksum = input.readInt()
            val actualChecksum = CRC32().apply { update(payload) }.value.toInt()
            require(expectedChecksum == actualChecksum) { "Snapshot checksum does not match" }
            require(input.available() == 0) { "Trailing bytes after snapshot envelope" }
            SnapshotEnvelope(schemaVersion = schemaVersion, payload = payload)
        }
    }

    /** Writes one bounded UTF-8 string with an explicit byte length. */
    private fun writeString(output: DataOutputStream, value: String) {
        val encoded = value.toByteArray(StandardCharsets.UTF_8)
        if (encoded.size > SnapshotLimits.MaxStringBytes) {
            throw SnapshotLimitException(
                "Encoded string exceeds ${SnapshotLimits.MaxStringBytes} bytes",
            )
        }
        output.writeInt(encoded.size)
        output.write(encoded)
    }

    /** Reads one bounded UTF-8 string with strict length validation. */
    private fun readString(input: DataInputStream): String {
        val length = input.readInt()
        if (length < 0 || length > SnapshotLimits.MaxStringBytes) {
            throw SnapshotLimitException("Encoded string length $length is outside limits")
        }
        val encoded = ByteArray(length)
        input.readFully(encoded)
        return String(encoded, StandardCharsets.UTF_8)
    }

    /** Writes one bounded byte array with an explicit length. */
    private fun writeByteArray(
        output: DataOutputStream,
        value: ByteArray,
        maximumBytes: Int,
    ) {
        if (value.size > maximumBytes) {
            throw SnapshotLimitException("Encoded byte array exceeds $maximumBytes bytes")
        }
        output.writeInt(value.size)
        output.write(value)
    }

    /** Reads one byte array whose declared length cannot exceed [maximumBytes]. */
    private fun readByteArray(input: DataInputStream, maximumBytes: Int): ByteArray {
        val length = input.readInt()
        if (length < 0 || length > maximumBytes) {
            throw SnapshotLimitException("Encoded byte array length $length is outside limits")
        }
        val value = ByteArray(length)
        input.readFully(value)
        return value
    }

    /** Reads one positive integer or rejects the payload. */
    private fun readPositiveInt(input: DataInputStream, label: String): Int {
        val value = input.readInt()
        require(value > 0) { "$label must be greater than zero" }
        return value
    }

    /** Reads one positive long or rejects the payload. */
    private fun readPositiveLong(input: DataInputStream, label: String): Long {
        val value = input.readLong()
        require(value > 0L) { "$label must be greater than zero" }
        return value
    }

    /** Reads one non-negative count bounded by [maximum]. */
    private fun readBoundedCount(
        input: DataInputStream,
        label: String,
        maximum: Int,
    ): Int {
        val value = input.readInt()
        if (value < 0 || value > maximum) {
            throw SnapshotLimitException("$label count $value is outside 0..$maximum")
        }
        return value
    }

    /** Validates route-local state counts, keys, individual values, and aggregate bytes. */
    private fun validateStatePayloads(payloads: Map<String, ByteArray>): String? {
        if (payloads.size > SnapshotLimits.MaxStateValues) {
            return "route-state value count exceeds ${SnapshotLimits.MaxStateValues}"
        }
        var aggregateBytes = 0L
        payloads.forEach { (key, payload) ->
            if (key.isBlank()) return "contains a blank route-state key"
            if (key.toByteArray(StandardCharsets.UTF_8).size > SnapshotLimits.MaxStringBytes) {
                return "route-state key '$key' exceeds ${SnapshotLimits.MaxStringBytes} UTF-8 bytes"
            }
            if (payload.size > SnapshotLimits.MaxStateValueBytes) {
                return "route-state value '$key' exceeds ${SnapshotLimits.MaxStateValueBytes} bytes"
            }
            aggregateBytes += payload.size.toLong()
            if (aggregateBytes > SnapshotLimits.MaxStateBytesPerEntry) {
                return "route-state payload exceeds ${SnapshotLimits.MaxStateBytesPerEntry} bytes"
            }
        }
        return null
    }

    /** Creates a structured failure without retaining an exception or raw payload. */
    private fun failure(
        reason: PixelNavigatorSnapshotFailureReason,
        message: String,
        entryIndex: Int? = null,
        destinationId: String? = null,
    ): PixelNavigatorSnapshotFailure {
        return PixelNavigatorSnapshotFailure(
            reason = reason,
            message = message,
            entryIndex = entryIndex,
            destinationId = destinationId,
        )
    }

    /** Creates one rejected decode result with optional entry context. */
    private fun rejectedDecode(
        reason: PixelNavigatorSnapshotFailureReason,
        message: String,
        entryIndex: Int? = null,
        destinationId: String? = null,
    ): PixelNavigatorSnapshotDecodeResult.Rejected {
        return PixelNavigatorSnapshotDecodeResult.Rejected(
            failure(reason, message, entryIndex, destinationId),
        )
    }

    /** Returns a short exception diagnostic without exposing encoded data. */
    private fun safeErrorMessage(error: Exception): String {
        return error.message?.takeIf(String::isNotBlank) ?: error::class.java.simpleName
    }

    /** 仅供测试使用的内部入口：把原始 entry 编码为当前 payload 与 envelope。 */
    internal fun encodePayloadForTest(
        currentEntryId: PixelRouteEntryId,
        entries: List<RawPersistentRouteEntry>,
    ): ByteArray {
        return encodeEnvelope(
            PixelNavigatorPersistentSnapshotSchemaVersion,
            encodePayload(currentEntryId, entries),
        )
    }

    /** Internal test hook for producing a valid envelope around adversarial payload bytes. */
    internal fun encodeEnvelopeForTest(schemaVersion: Int, payload: ByteArray): ByteArray {
        return encodeEnvelope(schemaVersion, payload)
    }
}

/** Detached, typed entry ready to be installed by Navigator restoration integration. */
internal sealed interface ResolvedPersistentRouteEntry {
    /** Preserved entry identity. */
    val id: PixelRouteEntryId

    /** Stable destination identifier. */
    val destinationId: String

    /** Creates one Navigator-owned entry with restored arguments and local state. */
    fun createEntry(owner: PixelRouteEntryOwner): PixelRouteEntry<*, *>

    /** Typed resolved entry retained behind the erased restoration-plan boundary. */
    data class Typed<A : Any, R>(
        override val id: PixelRouteEntryId,
        val destination: PixelRouteDestination<A, R>,
        val arguments: A,
        val stateBucket: PixelRouteStateBucket,
    ) : ResolvedPersistentRouteEntry {
        /** Stable destination identifier derived from [destination]. */
        override val destinationId: String
            get() = destination.id

        /** Creates one entry without exposing the internal owner capability publicly. */
        override fun createEntry(owner: PixelRouteEntryOwner): PixelRouteEntry<A, R> {
            return PixelRouteEntry.create(
                id = id,
                destination = destination,
                arguments = arguments,
                owner = owner,
                stateBucket = stateBucket,
            )
        }
    }
}

/** Raw entry used between bounded envelope parsing and destination validation. */
internal data class RawPersistentRouteEntry(
    /** Preserved route-entry identity. */
    val entryId: PixelRouteEntryId,
    /** Stable destination identifier. */
    val destinationId: String,
    /** Destination argument payload schema. */
    val argumentSchemaVersion: Int,
    /** Opaque destination argument bytes. */
    val argumentPayload: ByteArray,
    /** Destination route-local state schema. */
    val stateSchemaVersion: Int,
    /** Opaque destination-approved local-state values. */
    val statePayloads: Map<String, ByteArray>,
)

/** Parsed raw stack that has not yet invoked destination codecs. */
private data class RawPersistentRouteStack(
    /** Foreground entry identity. */
    val currentEntryId: PixelRouteEntryId,
    /** Root-to-foreground raw entries. */
    val entries: List<RawPersistentRouteEntry>,
)

/** Parsed and checksummed outer envelope. */
private data class SnapshotEnvelope(
    /** Outer Navigator snapshot schema. */
    val schemaVersion: Int,
    /** Schema-specific payload bytes. */
    val payload: ByteArray,
)

/** Result of capturing all live entries before envelope encoding. */
private sealed interface CaptureEntriesResult {
    /** Complete validated raw entry list. */
    data class Captured(val entries: List<RawPersistentRouteEntry>) : CaptureEntriesResult

    /** Atomic capture failure. */
    data class Rejected(val failure: PixelNavigatorSnapshotFailure) : CaptureEntriesResult
}

/** Result of capturing one live entry. */
private sealed interface CaptureEntryResult {
    /** Validated raw entry. */
    data class Captured(val entry: RawPersistentRouteEntry) : CaptureEntryResult

    /** Entry-specific capture failure. */
    data class Rejected(val failure: PixelNavigatorSnapshotFailure) : CaptureEntryResult
}

/** Result of decoding one raw entry through its typed adapter. */
private sealed interface ResolveEntryResult {
    /** Validated typed entry. */
    data class Resolved(val entry: ResolvedPersistentRouteEntry) : ResolveEntryResult

    /** Entry-specific decode or migration failure. */
    data class Rejected(val failure: PixelNavigatorSnapshotFailure) : ResolveEntryResult
}

/** Internal exception used to distinguish bounded-input rejection from general corruption. */
private class SnapshotLimitException(message: String) : IllegalArgumentException(message)

/** Fixed defensive limits applied before allocating untrusted snapshot fields. */
private object SnapshotLimits {
    /** Maximum complete envelope size accepted from saved state. */
    const val MaxEncodedBytes: Int = 1_048_576
    /** Maximum schema payload size excluding the envelope. */
    const val MaxPayloadBytes: Int = MaxEncodedBytes - SnapshotEnvelopeOverhead
    /** Maximum number of entries in one Navigator stack. */
    const val MaxEntries: Int = 128
    /** Maximum UTF-8 byte size of a destination ID or route-state key. */
    const val MaxStringBytes: Int = 1_024
    /** Maximum destination argument payload size per entry. */
    const val MaxArgumentBytes: Int = 262_144
    /** Maximum number of approved local-state values per entry. */
    const val MaxStateValues: Int = 64
    /** Maximum local-state value size. */
    const val MaxStateValueBytes: Int = 65_536
    /** Maximum aggregate local-state payload size per entry. */
    const val MaxStateBytesPerEntry: Int = 262_144
}

/** Four-byte `PXNV` snapshot magic. */
private const val SnapshotMagic: Int = 0x50584E56

/** Magic, schema, payload length, and CRC32 fields outside the schema payload. */
private const val SnapshotEnvelopeOverhead: Int = Int.SIZE_BYTES * 4

/** 为 `PixelRoutePayloadDecodeResult.Decoded` 提供可直接构造与匹配的顶层短名。 */
public typealias PixelRoutePayloadDecoded<T> = PixelRoutePayloadDecodeResult.Decoded<T>

/** 为 `PixelRoutePayloadDecodeResult.Rejected` 提供可直接构造与匹配的顶层短名。 */
public typealias PixelRoutePayloadRejected = PixelRoutePayloadDecodeResult.Rejected

/** 为 `PixelRouteStateDecodeResult.Decoded` 提供可直接构造与匹配的顶层短名。 */
public typealias PixelRouteStateDecoded = PixelRouteStateDecodeResult.Decoded

/** 为 `PixelRouteStateDecodeResult.Rejected` 提供可直接构造与匹配的顶层短名。 */
public typealias PixelRouteStateRejected = PixelRouteStateDecodeResult.Rejected

/** 为 `PixelNavigatorSnapshotEncodeResult.Encoded` 提供可直接构造与匹配的顶层短名。 */
public typealias PixelNavigatorSnapshotEncoded = PixelNavigatorSnapshotEncodeResult.Encoded

/** 为 `PixelNavigatorSnapshotEncodeResult.Rejected` 提供可直接构造与匹配的顶层短名。 */
public typealias PixelNavigatorSnapshotEncodeRejected = PixelNavigatorSnapshotEncodeResult.Rejected

/** 为 `PixelNavigatorSnapshotDecodeResult.Decoded` 提供可直接构造与匹配的顶层短名。 */
public typealias PixelNavigatorSnapshotDecoded = PixelNavigatorSnapshotDecodeResult.Decoded

/** 为 `PixelNavigatorSnapshotDecodeResult.Rejected` 提供可直接构造与匹配的顶层短名。 */
public typealias PixelNavigatorSnapshotDecodeRejected = PixelNavigatorSnapshotDecodeResult.Rejected
