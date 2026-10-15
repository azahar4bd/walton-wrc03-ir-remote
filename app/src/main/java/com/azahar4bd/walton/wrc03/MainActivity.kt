package com.azahar4bd.walton.wrc03

import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.GridLayout
import android.widget.Space
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.azahar4bd.walton.wrc03.data.RemoteProfiles
import com.azahar4bd.walton.wrc03.data.ProfileSelectionStore
import com.azahar4bd.walton.wrc03.data.ScannedCodeStore
import com.azahar4bd.walton.wrc03.databinding.ActivityMainBinding
import com.azahar4bd.walton.wrc03.ir.ConsumerIrHardware
import com.azahar4bd.walton.wrc03.ir.IrAvailability
import com.azahar4bd.walton.wrc03.ir.IrRemoteController
import com.azahar4bd.walton.wrc03.ir.IrSendResult
import com.azahar4bd.walton.wrc03.model.CodeConfidence
import com.azahar4bd.walton.wrc03.model.CommandGroup
import com.azahar4bd.walton.wrc03.model.RemoteCommand
import com.azahar4bd.walton.wrc03.model.RemoteProfile
import com.azahar4bd.walton.wrc03.model.ScannedCode
import com.azahar4bd.walton.wrc03.ui.RemoteKeypadLayout
import com.azahar4bd.walton.wrc03.ui.RemoteText
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar

private const val REPEAT_DELAY_MS = 400L
private const val REPEAT_INTERVAL_MS = 250L

/**
 * Single screen remote for the Walton WRC-03 handset.
 *
 * The UI is plain views (no Compose) so the app stays tiny and the whole IR stack can
 * be unit tested on a JVM.
 */
class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var controller: IrRemoteController
    private lateinit var scannedCodeStore: ScannedCodeStore

    private lateinit var selectionStore: ProfileSelectionStore
    private val profiles: List<RemoteProfile> get() = RemoteProfiles.includingDiscovered(scannedCodes)

    private var profile: RemoteProfile = RemoteProfiles.DEFAULT

    /** Codes the user found with the scanner, newest last; they override the shipped table. */
    private var scannedCodes: List<ScannedCode> = emptyList()
    private var lastScannedCount: Int = 0
    private val repeatCancellers = mutableListOf<() -> Unit>()

    /** What actually gets beamed: the selected profile with the scanned codes layered on. */
    private val activeProfile: RemoteProfile
        get() = profile.withScannedCodes(scannedCodes)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        controller = IrRemoteController(ConsumerIrHardware.create(this))
        scannedCodeStore = ScannedCodeStore(this)
        scannedCodes = scannedCodeStore.all()
        lastScannedCount = scannedCodes.size
        selectionStore = ProfileSelectionStore(this)
        profile = profiles.firstOrNull { it.id == selectionStore.selectedId } ?: RemoteProfiles.DEFAULT

        setupProfilePicker()
        renderStatus()
        buildKeypad()

        binding.buttonScan.setOnClickListener {
            startActivity(CodeScanActivity.intent(this, profile.id))
        }
        binding.buttonCodeList.setOnClickListener { showCodeSheet() }
        binding.buttonHelp.setOnClickListener { showHelp() }
    }

    override fun onResume() {
        super.onResume()
        // Returning from code scan mode: pick up whatever was confirmed on the TV.
        val stored = scannedCodeStore.all()
        if (stored != scannedCodes) {
            val added = stored.size - lastScannedCount
            scannedCodes = stored
            lastScannedCount = stored.size
            profile = profiles.firstOrNull { it.id == selectionStore.selectedId } ?: profile
            setupProfilePicker()
            renderStatus()
            buildKeypad()
            if (added > 0) {
                Snackbar
                    .make(binding.root, getString(R.string.scan_saved_message, added), Snackbar.LENGTH_LONG)
                    .show()
            }
        }
    }

    override fun onPause() {
        repeatCancellers.forEach { it() }
        super.onPause()
    }

    // --- screen set up -------------------------------------------------------

    private fun setupProfilePicker() {
        val names = profiles.map { it.nameBn }
        val adapter =
            ArrayAdapter(this, android.R.layout.simple_spinner_item, names).also {
                it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
        binding.profileSpinner.adapter = adapter
        binding.profileSpinner.setSelection(profiles.indexOfFirst { it.id == profile.id }.coerceAtLeast(0))
        binding.profileSpinner.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>,
                    view: View?,
                    position: Int,
                    id: Long,
                ) {
                    profile = profiles[position]
                    selectionStore.selectedId = profile.id
                    updateProfileSummary()
                    renderStatus()
                    buildKeypad()
                }

                override fun onNothingSelected(parent: AdapterView<*>) = Unit
            }
        updateProfileSummary()
    }

    private fun updateProfileSummary() {
        binding.profileSummary.text = "${RemoteText.profileSummary(profile)}\n${profile.sourceNoteBn}"
        binding.scannedCodesLine.text = RemoteText.scannedCodesSummary(activeProfile.scannedCount())
    }

    private fun renderStatus() {
        val state = controller.availability(profile.carrierHz)
        binding.statusIcon.text = RemoteText.statusIcon(state)
        binding.statusTitle.text = RemoteText.statusTitle(state)
        binding.statusDetail.text = RemoteText.statusDetail(state)

        val colour =
            ContextCompat.getColor(
                this,
                if (state is IrAvailability.Ready) R.color.status_ok else R.color.status_error,
            )
        binding.statusIcon.setTextColor(colour)
        binding.statusTitle.setTextColor(colour)
    }

    private fun buildKeypad() {
        repeatCancellers.forEach { it() }
        repeatCancellers.clear()

        sections().forEach { (group, views) ->
            val (title, grid) = views
            title.text = group.titleBn
            grid.removeAllViews()
            RemoteKeypadLayout.cells(group).forEach { command ->
                grid.addView(if (command == null) createSpacer() else createButton(command))
            }
        }
    }

    private fun sections(): Map<CommandGroup, Pair<TextView, GridLayout>> =
        mapOf(
            CommandGroup.POWER_AND_INPUT to (binding.sectionTitlePower to binding.gridPower),
            CommandGroup.NAVIGATION to (binding.sectionTitleDpad to binding.gridDpad),
            CommandGroup.VOLUME_CHANNEL to (binding.sectionTitleVolumeChannel to binding.gridVolumeChannel),
            CommandGroup.NUMBER_PAD to (binding.sectionTitleNumberPad to binding.gridNumberPad),
            CommandGroup.MEDIA to (binding.sectionTitleMedia to binding.gridMedia),
        )

    private fun createButton(command: RemoteCommand): MaterialButton {
        val button =
            MaterialButton(
                this,
                null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle,
            )
        val current = activeProfile
        button.text = command.buttonTextBn + confidenceBadge(current.codeFor(command)?.confidence)
        button.isAllCaps = false
        button.contentDescription = command.buttonTextBn
        button.textSize = 13f
        button.layoutParams = gridCellParams()

        button.setOnClickListener { press(command) }
        // Long press jumps straight into code scan mode for this very button.
        button.setOnLongClickListener {
            startActivity(CodeScanActivity.intent(this, profile.id, command))
            true
        }
        if (command.repeatable) attachRepeat(button, command)
        return button
    }

    /** "?" for a guess, "✓" once the scanner has confirmed the code on the real TV. */
    private fun confidenceBadge(confidence: CodeConfidence?): String =
        when (confidence) {
            CodeConfidence.GUESS -> " ?"
            CodeConfidence.SCANNED -> " ✓"
            CodeConfidence.DOCUMENTED -> ""
            null -> " —"
        }

    private fun createSpacer(): Space =
        Space(this).apply { layoutParams = gridCellParams() }

    private fun gridCellParams(): GridLayout.LayoutParams =
        GridLayout.LayoutParams().apply {
            width = 0
            height = GridLayout.LayoutParams.WRAP_CONTENT
            columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
            val gap = (6 * resources.displayMetrics.density).toInt()
            setMargins(gap, gap, gap, gap)
        }

    /**
     * Volume / channel / seek keys keep firing NEC repeat frames while held, the way a
     * real handset does. The first press still goes out as a full frame.
     */
    private fun attachRepeat(
        button: MaterialButton,
        command: RemoteCommand,
    ) {
        var task: Runnable? = null
        val cancel = {
            task?.let { button.removeCallbacks(it) }
            task = null
            button.isPressed = false
        }
        repeatCancellers.add(cancel)

        button.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    cancel()
                    view.isPressed = true
                    press(command)
                    val repeat =
                        object : Runnable {
                            override fun run() {
                                press(command, isRepeat = true)
                                view.postDelayed(this, REPEAT_INTERVAL_MS)
                            }
                        }
                    task = repeat
                    view.postDelayed(repeat, REPEAT_DELAY_MS)
                    true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    cancel()
                    true
                }

                else -> false
            }
        }
    }

    // --- transmitting --------------------------------------------------------

    private fun press(
        command: RemoteCommand,
        isRepeat: Boolean = false,
    ) {
        val result =
            if (isRepeat) {
                controller.sendRepeat(command, activeProfile)
            } else {
                controller.send(command, activeProfile)
            }

        when (result) {
            is IrSendResult.Sent -> {
                binding.lastSent.text = RemoteText.sentSummary(result)
                if (!isRepeat) {
                    binding.root.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                }
            }

            is IrSendResult.Failed -> {
                binding.lastSent.text = result.reason.messageBn
                if (!isRepeat) {
                    Snackbar.make(binding.root, result.reason.messageBn, Snackbar.LENGTH_LONG).show()
                }
            }
        }
    }

    // --- dialogs -------------------------------------------------------------

    private fun showCodeSheet() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.code_list_title)
            .setMessage(RemoteText.codeSheet(activeProfile))
            .setPositiveButton(R.string.action_close, null)
            .show()
    }

    private fun showHelp() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.help_title)
            .setMessage(R.string.help_body)
            .setPositiveButton(R.string.action_close, null)
            .show()
    }
}
