package com.purride.pixelui

public typealias Validator<T> = (T) -> String?

public fun interface FormFieldRegistration {
    public fun dispose()
}

public class FormController : ChangeNotifier() {
    private val fields = linkedSetOf<RegisteredFormField<*>>()

    public val fieldCount: Int
        get() = fields.size

    public val isValid: Boolean
        get() = fields.none { it.hasError }

    public fun <T> registerField(
        state: FormFieldState<T>,
        validator: Validator<T>? = null,
    ): FormFieldRegistration {
        val field = RegisteredFormField(state = state, validator = validator)
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
        notifyListeners()
        return valid
    }

    public fun reset() {
        fields.toList().forEach { field -> field.reset() }
        notifyListeners()
    }

    private class RegisteredFormField<T>(
        val state: FormFieldState<T>,
        val validator: Validator<T>?,
    ) {
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
    return FormScopeWidget(controller = controller, child = child, key = key)
}

public fun <T> FormField(
    state: FormFieldState<T>,
    validator: Validator<T>? = null,
    key: Any? = null,
    builder: (BuildContext, FormFieldState<T>) -> Widget,
): Widget {
    return FormFieldWidget(
        state = state,
        validator = validator,
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
        if (oldWidget.state !== widget.state || oldWidget.validator !== widget.validator) {
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
        return widget.builder(context, widget.state)
    }

    private fun attachToForm() {
        val form = context.getInheritedWidgetOfExactType<FormScopeWidget>()?.controller
        if (attachedForm === form && registration != null) return
        registration?.dispose()
        attachedForm = form
        registration = form?.registerField(widget.state, widget.validator)
    }
}
