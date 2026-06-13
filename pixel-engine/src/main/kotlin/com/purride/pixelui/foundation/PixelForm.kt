package com.purride.pixelui

public typealias Validator<T> = (T) -> String?
public typealias FormValidator = (Map<String, Any?>) -> Map<String, String>

public fun interface FormFieldRegistration {
    public fun dispose()
}

public class FormController(
    public var validators: List<FormValidator> = emptyList(),
    public val focusScopeNode: FocusScopeNode = FocusScopeNode(),
) : ChangeNotifier() {
    private val fields = linkedSetOf<RegisteredFormField<*>>()

    public val fieldCount: Int
        get() = fields.size

    public val isValid: Boolean
        get() = fields.none { it.hasError }

    public fun <T> registerField(
        state: FormFieldState<T>,
        validator: Validator<T>? = null,
        fieldId: String? = null,
    ): FormFieldRegistration {
        require(fieldId == null || fieldId.isNotBlank()) { "fieldId must not be blank" }
        require(fieldId == null || fields.none { it.fieldId == fieldId }) {
            "FormController already contains fieldId '$fieldId'"
        }
        val field = RegisteredFormField(fieldId = fieldId, state = state, validator = validator)
        fields += field
        return FormFieldRegistration {
            if (fields.remove(field)) {
                notifyListeners()
            }
        }
    }

    public fun validate(): Boolean {
        var valid = true
        fields.toList().forEach { field ->
            if (!field.validate()) {
                valid = false
            }
        }
        if (validators.isNotEmpty()) {
            val values = fields.mapNotNull { field ->
                field.fieldId?.let { id -> id to field.value }
            }.toMap()
            val crossFieldErrors = buildMap {
                validators.forEach { validator -> putAll(validator(values)) }
            }
            val registeredIds = fields.mapNotNull { it.fieldId }.toSet()
            val unknownIds = crossFieldErrors.keys - registeredIds
            require(unknownIds.isEmpty()) {
                "FormValidator returned errors for unregistered fieldIds: $unknownIds"
            }
            fields.forEach { field ->
                val fieldId = field.fieldId
                if (fieldId != null) {
                    crossFieldErrors[fieldId]?.let { error ->
                        field.setError(error)
                        valid = false
                    }
                }
            }
        }
        notifyListeners()
        return valid
    }

    public fun reset() {
        fields.toList().forEach { field -> field.reset() }
        notifyListeners()
    }

    private class RegisteredFormField<T>(
        val fieldId: String?,
        val state: FormFieldState<T>,
        val validator: Validator<T>?,
    ) {
        val value: Any?
            get() = state.value

        val hasError: Boolean
            get() = state.hasError

        fun validate(): Boolean {
            val error = validator?.invoke(state.value)
            state.setError(error)
            return error == null
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
    builder: (BuildContext, FormFieldState<T>) -> Widget,
): Widget {
    return FormFieldWidget(
        state = state,
        validator = validator,
        fieldId = fieldId,
        focusNode = focusNode,
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
        )
    }
}
