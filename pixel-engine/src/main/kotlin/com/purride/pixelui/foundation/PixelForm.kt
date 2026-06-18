package com.purride.pixelui

public typealias Validator<T> = (T) -> String?
public typealias FormValidator = (Map<String, Any?>) -> Map<String, String>

/**
 * Callback-based async field validator.
 *
 * Return a cancellation lambda. Call [complete] with `null` for success or an error string for failure.
 */
public fun interface AsyncValidator<T> {
    public fun validate(value: T, complete: (String?) -> Unit): () -> Unit
}

/**
 * Callback-based async cross-field validator.
 *
 * Return a cancellation lambda. Call [complete] with `fieldId -> errorText`; an empty map means success.
 */
public fun interface AsyncFormValidator {
    public fun validate(values: Map<String, Any?>, complete: (Map<String, String>) -> Unit): () -> Unit
}

/**
 * Callback-based form submitter.
 *
 * Return a cancellation lambda. Call [complete] with `null` for success or a submit-level error string.
 */
public fun interface FormSubmitter {
    public fun submit(values: Map<String, Any?>, complete: (String?) -> Unit): () -> Unit
}

public fun interface FormAsyncOperation {
    public fun cancel()
}

public enum class FormSubmitState {
    IDLE,
    VALIDATING,
    SUBMITTING,
    SUCCEEDED,
    FAILED,
}

public fun interface FormFieldRegistration {
    public fun dispose()
}

public class FormController(
    public var validators: List<FormValidator> = emptyList(),
    public val focusScopeNode: FocusScopeNode = FocusScopeNode(),
    public var asyncValidators: List<AsyncFormValidator> = emptyList(),
) : ChangeNotifier() {
    private val fields = linkedSetOf<RegisteredFormField<*>>()
    private var activeOperationId = 0
    private var activeCancels: MutableList<() -> Unit> = mutableListOf()

    public var submitState: FormSubmitState = FormSubmitState.IDLE
        private set

    public var submitErrorText: String? = null
        private set

    public val fieldCount: Int
        get() = fields.size

    public val isValid: Boolean
        get() = fields.none { it.hasError }

    public val isValidating: Boolean
        get() = submitState == FormSubmitState.VALIDATING

    public val isSubmitting: Boolean
        get() = submitState == FormSubmitState.SUBMITTING

    public fun <T> registerField(
        state: FormFieldState<T>,
        validator: Validator<T>? = null,
        fieldId: String? = null,
        asyncValidator: AsyncValidator<T>? = null,
    ): FormFieldRegistration {
        require(fieldId == null || fieldId.isNotBlank()) { "fieldId must not be blank" }
        require(fieldId == null || fields.none { it.fieldId == fieldId }) {
            "FormController already contains fieldId '$fieldId'"
        }
        val field = RegisteredFormField(
            fieldId = fieldId,
            state = state,
            validator = validator,
            asyncValidator = asyncValidator,
        )
        fields += field
        return FormFieldRegistration {
            if (fields.remove(field)) {
                notifyListeners()
            }
        }
    }

    public fun validate(): Boolean {
        cancelActiveOperation()
        setSubmitState(FormSubmitState.IDLE, null, notify = false)
        val valid = validateSyncFields()
        notifyListeners()
        return valid
    }

    public fun validateAsync(onComplete: (Boolean) -> Unit): FormAsyncOperation {
        cancelActiveOperation()
        val operationId = nextOperationId()
        setSubmitState(FormSubmitState.VALIDATING, null)
        val syncValid = validateSyncFields()
        if (!syncValid) {
            finishValidation(operationId, finalState = FormSubmitState.IDLE) {
                onComplete(false)
            }
            return FormAsyncOperation { }
        }

        val asyncTaskCount = fields.count { it.hasAsyncValidator } + asyncValidators.size
        if (asyncTaskCount == 0) {
            finishValidation(operationId, finalState = FormSubmitState.IDLE) {
                onComplete(true)
            }
            return FormAsyncOperation { }
        }

        val cancels = mutableListOf<() -> Unit>()
        activeCancels = cancels
        var remaining = asyncTaskCount
        var asyncValid = true

        fun completeOne(valid: Boolean) {
            if (operationId != activeOperationId) return
            if (!valid) {
                asyncValid = false
            }
            remaining -= 1
            if (remaining == 0) {
                finishValidation(operationId, finalState = FormSubmitState.IDLE) {
                    onComplete(asyncValid)
                }
            }
        }

        fields.toList().forEach { field ->
            if (!field.hasAsyncValidator) return@forEach
            val cancel = field.validateAsync { valid -> completeOne(valid) }
            if (operationId == activeOperationId && submitState == FormSubmitState.VALIDATING) {
                cancels += cancel
            }
        }

        val values = fieldValuesSnapshot()
        asyncValidators.forEach { validator ->
            val cancel = validator.validate(values) { errors ->
                if (operationId != activeOperationId) return@validate
                applyCrossFieldErrors(errors, "AsyncFormValidator")
                completeOne(errors.isEmpty())
            }
            if (operationId == activeOperationId && submitState == FormSubmitState.VALIDATING) {
                cancels += cancel
            }
        }

        return FormAsyncOperation {
            if (operationId == activeOperationId) {
                cancelActiveOperation()
                setSubmitState(FormSubmitState.IDLE, null)
            }
        }
    }

    public fun submit(
        submitter: FormSubmitter,
        onComplete: (Boolean) -> Unit = {},
    ): FormAsyncOperation {
        var cancelled = false
        var validationHandle: FormAsyncOperation? = null
        validationHandle = validateAsync { valid ->
            if (cancelled) return@validateAsync
            if (!valid) {
                setSubmitState(FormSubmitState.FAILED, null)
                onComplete(false)
                return@validateAsync
            }
            val submitOperationId = nextOperationId()
            setSubmitState(FormSubmitState.SUBMITTING, null)
            var completed = false
            val cancel = submitter.submit(fieldValuesSnapshot()) { errorText ->
                if (submitOperationId != activeOperationId || completed) return@submit
                completed = true
                activeCancels.clear()
                val state = if (errorText == null) FormSubmitState.SUCCEEDED else FormSubmitState.FAILED
                setSubmitState(state, errorText)
                onComplete(errorText == null)
            }
            activeCancels = mutableListOf(cancel)
        }
        return FormAsyncOperation {
            cancelled = true
            validationHandle?.cancel()
            cancelActiveOperation()
            setSubmitState(FormSubmitState.IDLE, null)
        }
    }

    public fun clearSubmitState() {
        cancelActiveOperation()
        setSubmitState(FormSubmitState.IDLE, null)
    }

    public fun reset() {
        cancelActiveOperation()
        fields.toList().forEach { field -> field.reset() }
        setSubmitState(FormSubmitState.IDLE, null, notify = false)
        notifyListeners()
    }

    private fun validateSyncFields(): Boolean {
        var valid = true
        fields.toList().forEach { field ->
            if (!field.validate()) {
                valid = false
            }
        }
        if (validators.isNotEmpty()) {
            val crossFieldErrors = buildMap {
                validators.forEach { validator -> putAll(validator(fieldValuesSnapshot())) }
            }
            applyCrossFieldErrors(crossFieldErrors, "FormValidator")
            if (crossFieldErrors.isNotEmpty()) {
                valid = false
            }
        }
        return valid
    }

    private fun fieldValuesSnapshot(): Map<String, Any?> {
        return fields.mapNotNull { field ->
            field.fieldId?.let { id -> id to field.value }
        }.toMap()
    }

    private fun applyCrossFieldErrors(errors: Map<String, String>, sourceName: String) {
        val registeredIds = fields.mapNotNull { it.fieldId }.toSet()
        val unknownIds = errors.keys - registeredIds
        require(unknownIds.isEmpty()) {
            "$sourceName returned errors for unregistered fieldIds: $unknownIds"
        }
        fields.forEach { field ->
            val fieldId = field.fieldId
            if (fieldId != null) {
                errors[fieldId]?.let { error -> field.setError(error) }
            }
        }
    }

    private fun finishValidation(
        operationId: Int,
        finalState: FormSubmitState,
        onFinished: () -> Unit,
    ) {
        if (operationId != activeOperationId) return
        activeCancels.clear()
        setSubmitState(finalState, null)
        onFinished()
    }

    private fun setSubmitState(
        state: FormSubmitState,
        errorText: String?,
        notify: Boolean = true,
    ) {
        if (submitState == state && submitErrorText == errorText) return
        submitState = state
        submitErrorText = errorText
        if (notify) {
            notifyListeners()
        }
    }

    private fun nextOperationId(): Int {
        activeOperationId += 1
        activeCancels.clear()
        return activeOperationId
    }

    private fun cancelActiveOperation() {
        activeOperationId += 1
        activeCancels.toList().forEach { cancel -> cancel() }
        activeCancels.clear()
    }

    private class RegisteredFormField<T>(
        val fieldId: String?,
        val state: FormFieldState<T>,
        val validator: Validator<T>?,
        val asyncValidator: AsyncValidator<T>?,
    ) {
        val value: Any?
            get() = state.value

        val hasError: Boolean
            get() = state.hasError

        val hasAsyncValidator: Boolean
            get() = asyncValidator != null

        fun validate(): Boolean {
            val error = validator?.invoke(state.value)
            state.setError(error)
            return error == null
        }

        fun validateAsync(complete: (Boolean) -> Unit): () -> Unit {
            val validator = asyncValidator ?: return {}
            var completed = false
            val cancel = validator.validate(state.value) { error ->
                if (completed) return@validate
                completed = true
                state.setError(error)
                complete(error == null)
            }
            return {
                if (!completed) {
                    completed = true
                    cancel()
                }
            }
        }

        fun reset() {
            state.reset()
        }

        fun setError(error: String?) {
            state.setError(error)
        }
    }
}

public class FormFieldState<T>(
    initialValue: T,
) : ChangeNotifier() {
    private val initialValue: T = initialValue

    public var value: T = initialValue
        private set

    public var errorText: String? = null
        private set

    public val hasError: Boolean
        get() = errorText != null

    public fun setValue(value: T) {
        if (this.value == value) return
        this.value = value
        notifyListeners()
    }

    internal fun setError(errorText: String?) {
        if (this.errorText == errorText) return
        this.errorText = errorText
        notifyListeners()
    }

    internal fun reset() {
        value = initialValue
        errorText = null
        notifyListeners()
    }
}

public fun Form(
    child: Widget,
    controller: FormController = FormController(),
    key: Any? = null,
): Widget {
    return FormScopeWidget(
        controller = controller,
        child = FocusScope(
            node = controller.focusScopeNode,
            child = child,
            key = key?.let { "$it-focus-scope" },
        ),
        key = key,
    )
}

public fun <T> FormField(
    state: FormFieldState<T>,
    validator: Validator<T>? = null,
    fieldId: String? = null,
    focusNode: FocusNode? = null,
    key: Any? = null,
    asyncValidator: AsyncValidator<T>? = null,
    builder: (BuildContext, FormFieldState<T>) -> Widget,
): Widget {
    return FormFieldWidget(
        state = state,
        validator = validator,
        fieldId = fieldId,
        focusNode = focusNode,
        asyncValidator = asyncValidator,
        builder = builder,
        key = key,
    )
}

private class FormScopeWidget(
    val controller: FormController,
    override val child: Widget,
    override val key: Any?,
) : InheritedNotifier<FormController>(notifier = controller, child = child, key = key)

private class FormFieldWidget<T>(
    val state: FormFieldState<T>,
    val validator: Validator<T>?,
    val fieldId: String?,
    val focusNode: FocusNode?,
    val asyncValidator: AsyncValidator<T>?,
    val builder: (BuildContext, FormFieldState<T>) -> Widget,
    override val key: Any?,
) : StatefulWidget(key = key) {
    override fun createState(): State<out StatefulWidget> = FormFieldWidgetState<T>()
}

private class FormFieldWidgetState<T> : State<FormFieldWidget<T>>() {
    private var attachedForm: FormController? = null
    private var registration: FormFieldRegistration? = null

    override fun initState() {
        attachToForm()
    }

    override fun didChangeDependencies() {
        attachToForm()
    }

    override fun didUpdateWidget(oldWidget: FormFieldWidget<T>) {
        if (
            oldWidget.state !== widget.state ||
            oldWidget.validator !== widget.validator ||
            oldWidget.asyncValidator !== widget.asyncValidator ||
            oldWidget.fieldId != widget.fieldId
        ) {
            registration?.dispose()
            registration = null
            oldWidget.state.setError(null)
        }
        attachToForm()
    }

    override fun dispose() {
        registration?.dispose()
    }

    override fun build(context: BuildContext): Widget {
        context.watch(widget.state)
        val child = widget.builder(context, widget.state)
        return widget.focusNode?.let { node ->
            Focus(
                node = node,
                child = child,
                key = widget.key?.let { "$it-focus" },
            )
        } ?: child
    }

    private fun attachToForm() {
        val form = context.getInheritedWidgetOfExactType<FormScopeWidget>()?.controller
        if (attachedForm === form && registration != null) return
        registration?.dispose()
        attachedForm = form
        registration = form?.registerField(
            state = widget.state,
            validator = widget.validator,
            fieldId = widget.fieldId,
            asyncValidator = widget.asyncValidator,
        )
    }
}
