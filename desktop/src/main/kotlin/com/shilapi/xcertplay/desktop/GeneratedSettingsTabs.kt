package com.shilapi.xcertplay.desktop

import java.awt.Color
import java.awt.FlowLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import javax.swing.BorderFactory
import javax.swing.BoxLayout
import javax.swing.JCheckBox
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JSpinner
import javax.swing.JTextField
import javax.swing.SpinnerNumberModel

/**
 * Dashboard tabs generated from [SettingsSchema]: one control per granular setting, grouped
 * into titled sections, with its help text underneath.
 */
class GeneratedSettingsTabs {
    private val editors = SettingsSchema.ALL.associateWith { editorFor(it) }

    /** The content for one schema tab. */
    fun tab(name: String): JComponent = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        border = BorderFactory.createEmptyBorder(PAD, PAD, PAD, PAD)
        SettingsSchema.ALL.filter { it.tab == name }.groupBy { it.section }.forEach { (section, settings) ->
            add(section(section, settings))
        }
        components.forEach { (it as JComponent).alignmentX = JComponent.LEFT_ALIGNMENT }
    }

    fun load(values: SettingsValues) = editors.forEach { (setting, editor) -> editor.load(values, setting) }

    fun read(): SettingsValues = editors.entries.fold(SettingsValues.DEFAULTS) { values, (setting, editor) ->
        editor.store(values, setting)
    }

    private fun section(title: String, settings: List<Setting<*>>): JComponent = JPanel(GridBagLayout()).apply {
        border = BorderFactory.createCompoundBorder(
            BorderFactory.createTitledBorder(title),
            BorderFactory.createEmptyBorder(GAP, GAP, GAP, GAP),
        )
        var row = 0
        for (setting in settings) {
            val editor = editors.getValue(setting)
            add(editor.row(setting), constraints(row++))
            if (setting.help.isNotEmpty()) add(JLabel(setting.help).apply { foreground = HINT_COLOR }, constraints(row++))
        }
        maximumSize = java.awt.Dimension(Int.MAX_VALUE, preferredSize.height)
    }

    private fun constraints(row: Int) = GridBagConstraints().apply {
        gridy = row
        anchor = GridBagConstraints.WEST
        fill = GridBagConstraints.HORIZONTAL
        weightx = 1.0
        insets = Insets(2, 0, 2, 0)
    }

    /** One setting's control; typed so values round-trip without casts at the call sites. */
    private abstract class Editor<T>(val component: JComponent, val unit: String = "") {
        abstract fun show(value: T)
        abstract fun value(): T

        fun row(setting: Setting<*>): JComponent = JPanel(FlowLayout(FlowLayout.LEFT, GAP, 0)).apply {
            if (component !is JCheckBox) add(JLabel(setting.label))
            add(component)
            if (unit.isNotEmpty()) add(JLabel(unit))
        }

        @Suppress("UNCHECKED_CAST")
        fun load(values: SettingsValues, setting: Setting<*>) = show(values[setting as Setting<T>])

        @Suppress("UNCHECKED_CAST")
        fun store(values: SettingsValues, setting: Setting<*>): SettingsValues = values.with(setting as Setting<T>, value())
    }

    private fun editorFor(setting: Setting<*>): Editor<*> = when (val type = setting.type) {
        is SettingType.Bool -> object : Editor<Boolean>(JCheckBox(setting.label)) {
            override fun show(value: Boolean) { (component as JCheckBox).isSelected = value }
            override fun value() = (component as JCheckBox).isSelected
        }
        is SettingType.Whole -> object : Editor<Int>(JSpinner(SpinnerNumberModel(type.min, type.min, type.max, type.step)), type.unit) {
            override fun show(value: Int) { (component as JSpinner).value = value }
            override fun value() = (component as JSpinner).value as Int
        }
        is SettingType.Decimal -> object : Editor<Double>(
            JSpinner(SpinnerNumberModel(type.min, type.min, type.max, type.step)).apply {
                editor = JSpinner.NumberEditor(this, "0.#####")
            },
            type.unit,
        ) {
            override fun show(value: Double) { (component as JSpinner).value = value }
            override fun value() = ((component as JSpinner).value as Number).toDouble()
        }
        is SettingType.Choice -> object : Editor<String>(JComboBox(type.options.map { it.second }.toTypedArray())) {
            override fun show(value: String) {
                (component as JComboBox<*>).selectedIndex = type.options.indexOfFirst { it.first == value }.coerceAtLeast(0)
            }
            override fun value() = type.options[(component as JComboBox<*>).selectedIndex].first
        }
        is SettingType.Text -> object : Editor<String>(JTextField(TEXT_COLUMNS)) {
            override fun show(value: String) { (component as JTextField).text = value }
            override fun value() = (component as JTextField).text.trim().take(type.maxLength)
                .ifEmpty { setting.default as String }
        }
    }

    private companion object {
        const val PAD = 12
        const val GAP = 8
        const val TEXT_COLUMNS = 20
        val HINT_COLOR = Color(0x8B, 0x94, 0x9E)
    }
}
